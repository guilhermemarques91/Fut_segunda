<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Api-Key, X-Auth-Token');
// Nunca cachear respostas da API (evita lista de usuários/dados desatualizados
// servidos pelo cache do navegador ou do LiteSpeed/Hostgator).
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Expires: 0');
// Desliga o cache público do LiteSpeed para as respostas da API
header('X-LiteSpeed-Cache-Control: no-cache');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

set_exception_handler(function($e) {
    if (!headers_sent()) http_response_code(500);
    echo json_encode(['error' => 'Erro interno: ' . $e->getMessage()]);
    exit;
});

require __DIR__ . '/config.php';

// Valida API key
$key = $_SERVER['HTTP_X_API_KEY'] ?? '';
if ($key !== API_KEY) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit;
}

// Conexão com o banco
try {
    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'DB connection failed']);
    exit;
}

$action = $_GET['action'] ?? '';

// ── HELPERS ──────────────────────────────────────────────
function getSession($pdo, $token) {
    if (!$token) return null;
    $stmt = $pdo->prepare(
        'SELECT s.user_id, s.role, u.username
         FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token = ? AND s.expires_at > NOW()'
    );
    $stmt->execute([$token]);
    return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
}

function normalizePhone($raw) {
    $d = preg_replace('/\D/', '', (string)$raw);
    if (strlen($d) >= 12 && substr($d, 0, 2) === '55') $d = substr($d, 2);
    if (strlen($d) === 10) $d = substr($d, 0, 2) . '9' . substr($d, 2);
    return strlen($d) === 11 ? $d : null;
}

// ── HISTÓRICO PÚBLICO (sem token) ───────────────────────
if ($action === 'public') {
    $stmt = $pdo->query('SELECT data FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) { echo json_encode(['players'=>[],'attendances'=>[],'results'=>[],'teamHistory'=>[]]); exit; }
    $d = json_decode($row['data'], true) ?? [];
    $players = array_map(fn($p) => [
        'id'       => $p['id'],
        'name'     => $p['name'],
        'position' => $p['position'] ?? '',
        'overall'  => $p['overall']  ?? 60,
    ], $d['players'] ?? []);
    // Mapa id -> nome para resolver participantes e responsável pela louça
    $nameById = [];
    foreach ($d['players'] ?? [] as $p) { $nameById[$p['id']] = $p['name']; }
    // Tira-gosto público: SEM valores/cobranças/pendência — só janta, participantes e quem lavou
    $dinner = array_map(function($h) use ($nameById) {
        $partIds   = $h['participants'] ?? [];
        $partNames = array_values(array_filter(array_map(fn($id) => $nameById[$id] ?? null, $partIds)));
        $loucaId   = $h['loucaResponsavel'] ?? null;
        return [
            'date'         => $h['date'],
            'meal'         => $h['meal'] ?? '',
            'count'        => count($partIds),
            'participants' => $partNames,
            'louca'        => ($loucaId !== null && isset($nameById[$loucaId])) ? $nameById[$loucaId] : null,
        ];
    }, $d['dinnerHistory'] ?? []);
    echo json_encode([
        'players'       => $players,
        'attendances'   => $d['attendances']  ?? [],
        'results'       => $d['results']      ?? [],
        'teamHistory'   => $d['teamHistory']  ?? [],
        'dinnerHistory' => $dinner,
        'liveState'     => $d['liveState']    ?? null,
    ]);
    exit;
}

// ── LOGIN (não exige token) ──────────────────────────────
if ($action === 'login') {
    $body     = json_decode(file_get_contents('php://input'), true) ?? [];
    $username = trim($body['username'] ?? '');
    $password = $body['password'] ?? '';

    if (!$username || !$password) {
        http_response_code(400);
        echo json_encode(['error' => 'Usuário e senha obrigatórios']);
        exit;
    }

    $stmt = $pdo->prepare('SELECT id, password, role FROM users WHERE username = ?');
    $stmt->execute([$username]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Usuário ou senha incorretos']);
        exit;
    }

    $pdo->prepare('DELETE FROM sessions WHERE user_id = ? AND expires_at < NOW()')
        ->execute([$user['id']]);

    $token   = bin2hex(random_bytes(32));
    $expires = date('Y-m-d H:i:s', strtotime('+30 days'));
    $pdo->prepare('INSERT INTO sessions (token, user_id, role, expires_at) VALUES (?, ?, ?, ?)')
        ->execute([$token, $user['id'], $user['role'], $expires]);

    echo json_encode(['token' => $token, 'role' => $user['role'], 'username' => $username]);
    exit;
}

// ── LIVE UPDATE (público — só API key) ──────────────────
if ($action === 'live_update' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body)) { http_response_code(400); echo json_encode(['error' => 'Invalid JSON']); exit; }
    $stmt = $pdo->query('SELECT data FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    $d    = $row ? (json_decode($row['data'], true) ?? []) : [];
    $d['liveState'] = $body;
    $newJson = json_encode($d, JSON_UNESCAPED_UNICODE);
    $pdo->prepare('INSERT INTO app_data (id, data) VALUES (1, ?) ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP')
        ->execute([$newJson]);
    echo json_encode(['ok' => true]);
    exit;
}

// ── Valida token para todas as outras ações ──────────────
$authToken = $_SERVER['HTTP_X_AUTH_TOKEN'] ?? $_GET['token'] ?? '';
if (!$authToken) {
    http_response_code(401);
    echo json_encode(['error' => 'Auth token required']);
    exit;
}
$session = getSession($pdo, $authToken);
if (!$session) {
    http_response_code(401);
    echo json_encode(['error' => 'Token inválido ou expirado']);
    exit;
}

// ── VALIDATE ────────────────────────────────────────────
if ($action === 'validate') {
    echo json_encode(['ok' => true, 'role' => $session['role'], 'username' => $session['username']]);
    exit;
}

// ── LOGOUT ──────────────────────────────────────────────
if ($action === 'logout') {
    $pdo->prepare('DELETE FROM sessions WHERE token = ?')->execute([$authToken]);
    echo json_encode(['ok' => true]);
    exit;
}

// ── CHANGE PASSWORD ──────────────────────────────────────
if ($action === 'change_password') {
    $body        = json_decode(file_get_contents('php://input'), true) ?? [];
    $oldPassword = $body['old_password'] ?? '';
    $newPassword = $body['new_password'] ?? '';

    if (strlen($newPassword) < 6) {
        http_response_code(400);
        echo json_encode(['error' => 'Nova senha deve ter pelo menos 6 caracteres']);
        exit;
    }

    $stmt = $pdo->prepare('SELECT password FROM users WHERE id = ?');
    $stmt->execute([$session['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!password_verify($oldPassword, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Senha atual incorreta']);
        exit;
    }

    $hash = password_hash($newPassword, PASSWORD_BCRYPT);
    $pdo->prepare('UPDATE users SET password = ? WHERE id = ?')
        ->execute([$hash, $session['user_id']]);

    echo json_encode(['ok' => true]);
    exit;
}

// ── VERIFY PASSWORD ──────────────────────────────────────
if ($action === 'verify_password') {
    $body     = json_decode(file_get_contents('php://input'), true) ?? [];
    $password = $body['password'] ?? '';

    $stmt = $pdo->prepare('SELECT password FROM users WHERE id = ?');
    $stmt->execute([$session['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Senha incorreta']);
        exit;
    }

    echo json_encode(['ok' => true]);
    exit;
}

// ── GENERATE TOKENS (admin) ──────────────────────────────
if ($action === 'generate_tokens') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body    = json_decode(file_get_contents('php://input'), true) ?? [];
    $date    = $body['date'] ?? '';
    $players = $body['players'] ?? [];
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    $token = '';
    $bytes = random_bytes(12);
    for ($i = 0; $i < 12; $i++) $token .= $alphabet[ord($bytes[$i]) % strlen($alphabet)];
    $pdo->prepare('DELETE FROM presence_confirmations WHERE rodada_date = ?')->execute([$date]);
    $stmt = $pdo->prepare(
        'INSERT INTO presence_confirmations (rodada_date, token, phone, player_id, player_name) VALUES (?, ?, ?, ?, ?)'
    );
    $inserted = 0;
    foreach ($players as $p) {
        $phone = normalizePhone($p['phone'] ?? '');
        if (!$phone) continue;
        $stmt->execute([$date, $token, $phone, (string)($p['id'] ?? ''), (string)($p['name'] ?? '')]);
        $inserted++;
    }
    echo json_encode(['ok' => true, 'token' => $token, 'inserted' => $inserted]);
    exit;
}

// ── GET CONFIRMATIONS ────────────────────────────────────
if ($action === 'get_confirmations') {
    $date = $_GET['date'] ?? '';
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    $stmt = $pdo->prepare(
        'SELECT player_id, player_name, status, confirmed_at
         FROM presence_confirmations WHERE rodada_date = ? ORDER BY player_name'
    );
    $stmt->execute([$date]);
    echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
    exit;
}

// ── Actions somente para admin ───────────────────────────
if (in_array($action, ['list_users', 'create_user', 'delete_user', 'update_user_role'], true)) {
    if ($session['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['error' => 'Acesso negado']);
        exit;
    }

    if ($action === 'list_users') {
        $stmt = $pdo->query('SELECT id, username, role, created_at FROM users ORDER BY id');
        echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
        exit;
    }

    if ($action === 'create_user') {
        $body     = json_decode(file_get_contents('php://input'), true) ?? [];
        $username = trim($body['username'] ?? '');
        $password = $body['password'] ?? '';
        $role     = $body['role'] ?? 'viewer';

        if (!$username || strlen($password) < 6) {
            http_response_code(400);
            echo json_encode(['error' => 'Usuário obrigatório e senha mínima de 6 caracteres']);
            exit;
        }
        if (!in_array($role, ['admin', 'viewer'], true)) {
            http_response_code(400);
            echo json_encode(['error' => 'Role inválido']);
            exit;
        }

        $chk = $pdo->prepare('SELECT id FROM users WHERE username = ?');
        $chk->execute([$username]);
        if ($chk->fetch()) {
            http_response_code(409);
            echo json_encode(['error' => 'Usuário já existe']);
            exit;
        }

        $hash = password_hash($password, PASSWORD_BCRYPT);
        $pdo->prepare('INSERT INTO users (username, password, role) VALUES (?, ?, ?)')
            ->execute([$username, $hash, $role]);

        echo json_encode(['ok' => true, 'id' => (int) $pdo->lastInsertId()]);
        exit;
    }

    if ($action === 'update_user_role') {
        $body = json_decode(file_get_contents('php://input'), true) ?? [];
        $id   = (int) ($body['id'] ?? 0);
        $role = $body['role'] ?? '';

        if (!$id || !in_array($role, ['admin', 'viewer'], true)) {
            http_response_code(400);
            echo json_encode(['error' => 'ID ou role inválido']);
            exit;
        }
        if ($id === (int) $session['user_id']) {
            http_response_code(400);
            echo json_encode(['error' => 'Você não pode alterar sua própria role']);
            exit;
        }

        $pdo->prepare('UPDATE users SET role = ? WHERE id = ?')->execute([$role, $id]);
        echo json_encode(['ok' => true]);
        exit;
    }

    if ($action === 'delete_user') {
        $body = json_decode(file_get_contents('php://input'), true) ?? [];
        $id   = (int) ($body['id'] ?? 0);

        if (!$id) {
            http_response_code(400);
            echo json_encode(['error' => 'ID inválido']);
            exit;
        }
        if ($id === (int) $session['user_id']) {
            http_response_code(400);
            echo json_encode(['error' => 'Você não pode remover sua própria conta']);
            exit;
        }

        $pdo->prepare('DELETE FROM users WHERE id = ?')->execute([$id]);
        echo json_encode(['ok' => true]);
        exit;
    }
}

// ── WHATSAPP: enviar confirmados ao grupo agora (admin) ──
if ($action === 'send_confirmados') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/whatsapp.php';
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $date = $body['date'] ?? '';
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    // Texto vindo do app (idêntico ao que o admin vê) tem prioridade; senão monta no servidor.
    $text = isset($body['text']) && trim($body['text']) !== '' ? $body['text'] : wa_build_confirmados_msg($pdo, $date);
    $send = wa_send_group_text($text);
    echo json_encode(['ok' => !empty($send['ok']), 'code' => $send['code'] ?? null, 'err' => $send['err'] ?? null, 'preview' => $text]);
    exit;
}

// ── WHATSAPP: listar grupos da instância (admin) — p/ achar o JID ──
if ($action === 'wa_list_groups') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/whatsapp.php';
    echo json_encode(wa_list_groups());
    exit;
}

// ── GET — carrega todos os dados ─────────────────────────
$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $stmt = $pdo->query('SELECT data, updated_at FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    echo $row ? $row['data'] : '{}';
    exit;
}

// ── POST — salva todos os dados (somente admin) ──────────
if ($method === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['error' => 'Forbidden — somente admin pode salvar dados']);
        exit;
    }
    $body = file_get_contents('php://input');
    $newData = json_decode($body, true);
    if (!$body || !is_array($newData)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid JSON']);
        exit;
    }
    // Preserve liveState — it is managed separately via live_update and must not be wiped on every save
    $existing = $pdo->query('SELECT data FROM app_data WHERE id = 1')->fetch(PDO::FETCH_ASSOC);
    if ($existing) {
        $existingData = json_decode($existing['data'], true) ?? [];
        if (array_key_exists('liveState', $existingData)) {
            $newData['liveState'] = $existingData['liveState'];
            $body = json_encode($newData, JSON_UNESCAPED_UNICODE);
        }
    }
    $stmt = $pdo->prepare(
        'INSERT INTO app_data (id, data) VALUES (1, ?)
         ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP'
    );
    $stmt->execute([$body]);
    echo json_encode(['ok' => true, 'saved_at' => date('c')]);
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'Method not allowed']);
