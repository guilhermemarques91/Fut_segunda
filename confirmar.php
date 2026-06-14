<?php
require __DIR__ . '/api/config.php';

function normalizePhone($raw) {
    $d = preg_replace('/\D/', '', (string)$raw);
    if (strlen($d) >= 12 && substr($d, 0, 2) === '55') $d = substr($d, 2);
    if (strlen($d) === 10) $d = substr($d, 0, 2) . '9' . substr($d, 2);
    return strlen($d) === 11 ? $d : null;
}

// Importa as confirmações desta rodada para o app_data (lista de presença + janta),
// server-side — assim a lista atualiza mesmo sem nenhum admin com o app aberto.
// Espelha _applyConfirmations() do frontend: só mexe em quem respondeu (não-pending),
// respeita os ajustes manuais do admin (attendances[date].manual) e não toca em janta
// já fechada. Nunca lança erro pra cima — uma falha aqui não pode quebrar a confirmação.
function syncAttendanceFromConfirmations($pdo, $date) {
    try {
        $stmt = $pdo->prepare('SELECT player_id, status FROM presence_confirmations WHERE rodada_date = ?');
        $stmt->execute([$date]);
        $confs = $stmt->fetchAll(PDO::FETCH_ASSOC);

        $row = $pdo->query('SELECT data FROM app_data WHERE id = 1')->fetch(PDO::FETCH_ASSOC);
        if (!$row) return;
        $data = json_decode($row['data'], true);
        if (!is_array($data)) return;

        // rodada travada (encerrada): não mexe — igual ao guard do frontend
        if (in_array($date, $data['lockedRodadas'] ?? [], true)) return;

        $players   = $data['players'] ?? [];
        $playerSet = [];
        foreach ($players as $p) { $playerSet[(int)$p['id']] = true; }

        // ── attendances[date] ──────────────────────────────
        $data['attendances'] = $data['attendances'] ?? [];
        $attIdx = null;
        foreach ($data['attendances'] as $i => $a) {
            if (($a['date'] ?? '') === $date) { $attIdx = $i; break; }
        }
        if ($attIdx === null) {
            // default igual ao frontend: todos os mensalistas (não isentos)
            $def = [];
            foreach ($players as $p) {
                if (!empty($p['isRegular']) && empty($p['isIsento'])) $def[] = (int)$p['id'];
            }
            $data['attendances'][] = ['date' => $date, 'opponent' => '', 'players' => $def, 'noShow' => [], 'manual' => []];
            $attIdx = count($data['attendances']) - 1;
        }
        $att =& $data['attendances'][$attIdx];
        $att['players'] = array_values(array_map('intval', $att['players'] ?? []));
        $att['noShow']  = array_values(array_map('intval', $att['noShow'] ?? []));
        // mapa playerId(string) -> último status já importado; só reagimos a mudanças
        $imported = (isset($att['imported']) && is_array($att['imported'])) ? $att['imported'] : [];

        // ── dinnerHistory[date] ────────────────────────────
        $data['dinnerHistory'] = $data['dinnerHistory'] ?? [];
        $dinIdx = null;
        foreach ($data['dinnerHistory'] as $i => $d) {
            if (($d['date'] ?? '') === $date) { $dinIdx = $i; break; }
        }
        if ($dinIdx === null) {
            // default igual ao frontend: participantes = quem está na presença
            $data['dinnerHistory'][] = ['date' => $date, 'meal' => '', 'total' => 0, 'share' => 0,
                'realShare' => 0, 'participants' => $att['players'], 'paidBy' => [], 'closed' => false, 'loucaResponsavel' => null];
            $dinIdx = count($data['dinnerHistory']) - 1;
        }
        $din =& $data['dinnerHistory'][$dinIdx];
        $dinnerClosed = !empty($din['closed']);
        $din['participants'] = array_values(array_map('intval', $din['participants'] ?? []));

        $setMember = function (&$arr, $id, $want) {
            $pos = array_search($id, $arr, true);
            if ($want && $pos === false) { $arr[] = $id; return true; }
            if (!$want && $pos !== false) { array_splice($arr, $pos, 1); return true; }
            return false;
        };

        $changed = false;
        foreach ($confs as $c) {
            if ($c['status'] === 'pending') continue;
            $id = (int)$c['player_id'];
            if (!$id || !isset($playerSet[$id])) continue;
            $st = $c['status'];
            // só reage quando a resposta é nova/mudou (preserva ajuste manual do admin)
            if (isset($imported[$id]) && $imported[$id] === $st) continue;
            $wantFootball = ($st === 'football' || $st === 'football_dinner');
            $wantDinner   = ($st === 'football_dinner' || $st === 'dinner_only');
            $setMember($att['players'], $id, $wantFootball);
            if (!$dinnerClosed) $setMember($din['participants'], $id, $wantDinner);
            $setMember($att['noShow'], $id, $st === 'no');
            $imported[$id] = $st;
            $changed = true;
        }
        $att['imported'] = $imported; // persiste o mapa de status já importados
        unset($att, $din);
        if (!$changed) return;

        $json = json_encode($data, JSON_UNESCAPED_UNICODE);
        if ($json === false) return;
        $pdo->prepare('INSERT INTO app_data (id, data) VALUES (1, ?)
                       ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP')
            ->execute([$json]);
    } catch (Throwable $e) {
        // silencioso de propósito — não quebra o fluxo de confirmação do usuário
    }
}

$token  = trim($_GET['t'] ?? '');
$step   = 1;
$player = null;
$status = null;
$rowId  = null;
$error  = '';
$success = false;

$statusLabels = [
    'pending'         => '⏳ Pendente',
    'football'        => '⚽ Vou jogar',
    'football_dinner' => '⚽🍖 Vou jogar + janta',
    'dinner_only'     => '🍖 Só a janta',
    'no'              => '❌ Não vou',
];
$statusColors = [
    'football'        => '#22c55e',
    'football_dinner' => '#f59e0b',
    'dinner_only'     => '#a855f7',
    'no'              => '#6b7280',
    'pending'         => '#94a3b8',
];

if ($token) {
    try {
        $pdo = new PDO(
            'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
            DB_USER, DB_PASS,
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
        );
    } catch (PDOException $e) {
        $error = 'Erro de conexão. Tente novamente mais tarde.';
    }

    // Valida se o token existe antes de mostrar o formulário
    if (!$error) {
        $tkStmt = $pdo->prepare('SELECT COUNT(*) FROM presence_confirmations WHERE token = ?');
        $tkStmt->execute([$token]);
        if ((int)$tkStmt->fetchColumn() === 0) {
            $token = ''; // força o bloco "Link inválido"
            $error = 'Link expirado ou inválido. Peça ao organizador para gerar um novo link.';
        }
    }

    if (!$error && $_SERVER['REQUEST_METHOD'] === 'POST') {
        $rawPhone = $_POST['phone'] ?? '';
        $phone    = normalizePhone($rawPhone);

        if (!$phone) {
            $error = 'Número inválido. Use o formato (11) 99999-9999.';
        } else {
            $stmt = $pdo->prepare(
                'SELECT id, player_name, status, rodada_date FROM presence_confirmations WHERE token = ? AND phone = ?'
            );
            $stmt->execute([$token, $phone]);
            $row = $stmt->fetch(PDO::FETCH_ASSOC);

            if (!$row) {
                $error = 'Número não encontrado nesta rodada. Verifique se digitou o número cadastrado ou fale com o organizador.';
            } else {
                $player = $row['player_name'];
                $rowId  = $row['id'];
                $status = $row['status'];

                $newStatus = $_POST['status'] ?? '';
                $valid     = ['football', 'football_dinner', 'dinner_only', 'no'];
                if ($newStatus && in_array($newStatus, $valid, true)) {
                    $pdo->prepare('UPDATE presence_confirmations SET status = ?, confirmed_at = NOW() WHERE id = ?')
                        ->execute([$newStatus, $rowId]);
                    $status  = $newStatus;
                    $success = true;
                    // importa server-side p/ a lista de presença/janta (não depende de admin com o app aberto)
                    syncAttendanceFromConfirmations($pdo, $row['rodada_date']);
                } else {
                    $step = 2;
                }
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Confirmação — Fut Segunda</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0f172a;color:#f1f5f9;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
.card{background:#1e293b;border-radius:20px;padding:32px 24px;width:100%;max-width:420px;text-align:center;box-shadow:0 25px 50px rgba(0,0,0,0.5)}
.logo{font-size:3rem;margin-bottom:6px}
.title{font-size:1rem;color:#64748b;margin-bottom:28px;font-weight:500}
.player-name{font-size:1.6rem;font-weight:800;color:#f1f5f9;margin-bottom:6px}
.subtitle{font-size:.9rem;color:#64748b;margin-bottom:24px}
.current-badge{display:inline-block;padding:4px 12px;border-radius:20px;font-size:.78rem;font-weight:600;margin-bottom:20px;background:rgba(255,255,255,0.06);color:#94a3b8}
input[type="tel"]{width:100%;padding:16px;font-size:1.15rem;text-align:center;background:#0f172a;border:2px solid #334155;border-radius:14px;color:#f1f5f9;outline:none;letter-spacing:.08em;margin-bottom:12px;-webkit-appearance:none}
input[type="tel"]:focus{border-color:#3b82f6}
.btn{display:block;width:100%;padding:17px;font-size:1rem;font-weight:700;border:none;border-radius:14px;cursor:pointer;margin-bottom:10px;transition:opacity .15s,transform .1s;-webkit-tap-highlight-color:transparent;font-family:inherit}
.btn:active{transform:scale(.97);opacity:.85}
.btn-primary{background:#3b82f6;color:#fff}
.btn-football{background:#16a34a;color:#fff}
.btn-football-dinner{background:#d97706;color:#fff}
.btn-dinner{background:#7c3aed;color:#fff}
.btn-no{background:#374151;color:#9ca3af}
.error{background:#450a0a;color:#fca5a5;border-radius:12px;padding:14px;margin-bottom:18px;font-size:.88rem;line-height:1.4}
.success-icon{font-size:4.5rem;margin-bottom:14px}
.success-text{font-size:1.2rem;font-weight:700;margin-bottom:6px}
.success-sub{font-size:.85rem;color:#64748b}
.hint{font-size:.82rem;color:#64748b;margin-bottom:20px;line-height:1.5}
</style>
</head>
<body>
<div class="card">
  <div class="logo">⚽</div>
  <div class="title">Fut Segunda — Confirmação de Presença</div>

<?php if (!$token): ?>
  <div class="error"><?= $error ?: 'Link inválido. Use o link enviado no grupo do WhatsApp.' ?></div>

<?php elseif ($success): ?>
  <div class="success-icon"><?= $status === 'no' ? '😢' : ($status === 'dinner_only' ? '🍖' : '🎉') ?></div>
  <div class="player-name"><?= htmlspecialchars($player) ?></div>
  <div class="success-text" style="color:<?= $statusColors[$status] ?>"><?= $statusLabels[$status] ?></div>
  <div class="success-sub" style="margin-top:12px">Confirmação registrada com sucesso!<br>Você pode alterar clicando no link novamente.</div>

<?php elseif ($step === 2 && $player): ?>
  <div class="player-name"><?= htmlspecialchars($player) ?></div>
  <div class="subtitle">Como vai ser na segunda?</div>
  <?php if ($status !== 'pending'): ?>
    <div class="current-badge">Atual: <?= $statusLabels[$status] ?></div>
  <?php endif; ?>
  <form method="POST">
    <input type="hidden" name="phone" value="<?= htmlspecialchars($_POST['phone'] ?? '') ?>">
    <button type="submit" name="status" value="football"        class="btn btn-football">⚽ Vou jogar</button>
    <button type="submit" name="status" value="football_dinner" class="btn btn-football-dinner">⚽🍖 Vou jogar + janta</button>
    <button type="submit" name="status" value="dinner_only"     class="btn btn-dinner">🍖 Só a janta</button>
    <button type="submit" name="status" value="no"              class="btn btn-no">❌ Não vou</button>
  </form>

<?php else: ?>
  <?php if ($error): ?>
    <div class="error"><?= htmlspecialchars($error) ?></div>
  <?php endif; ?>
  <div class="hint">Digite seu número de celular com DDD para confirmar sua presença</div>
  <form method="POST" autocomplete="off">
    <input type="tel" name="phone" placeholder="(11) 99999-9999" autofocus inputmode="numeric">
    <button type="submit" class="btn btn-primary">Continuar →</button>
  </form>

<?php endif; ?>
</div>
</body>
</html>
