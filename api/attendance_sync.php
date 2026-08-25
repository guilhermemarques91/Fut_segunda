<?php
// Importa as confirmações de uma rodada para o app_data (lista de presença + janta).
// Extraído de confirmar.php para poder ser reusado por quem mais grava em
// presence_confirmations (hoje: o link público e a aprovação da leitura por IA).
// Espelha _applyConfirmations() do frontend — se mudar aqui, mudar lá.

if (!function_exists('syncAttendanceFromConfirmations')) {

// Espelha _applyConfirmations() do frontend: só mexe em quem respondeu (não-pending),
// respeita os ajustes manuais do admin (attendances[date].imported) e não toca em janta
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

}
