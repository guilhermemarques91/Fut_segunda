<?php
// Cron (1x/min): pega UMA mensagem nova da wa_inbox, manda pro Ollama ler e grava uma
// proposta de mudanças para o admin aprovar no app. Nada é escrito em
// presence_confirmations aqui — só depois da aprovação (ai_apply_proposal).
//
// Rodar via: cron CLI  ->  php api/ai_parse_inbox.php
//        ou  HTTP      ->  /api/ai_parse_inbox.php?key=WA_CRON_KEY
//
// Teste sem WhatsApp:  php api/ai_parse_inbox.php --dry-run --text=lista.txt

require __DIR__ . '/config.php';
require_once __DIR__ . '/ai_parse.php';

$isCli = (php_sapi_name() === 'cli');
if (!$isCli) {
    header('Content-Type: application/json; charset=utf-8');
    $key = $_GET['key'] ?? '';
    if (!ai_cfg('WA_CRON_KEY') || !hash_equals((string) ai_cfg('WA_CRON_KEY'), (string) $key)) {
        http_response_code(403);
        echo json_encode(['error' => 'forbidden']);
        exit;
    }
} else {
    set_time_limit(0);
}

// ── flags de CLI ─────────────────────────────────────────
$dryRun  = false;
$textArg = null;
if ($isCli) {
    foreach (array_slice($argv, 1) as $a) {
        if ($a === '--dry-run')                     $dryRun = true;
        elseif (strpos($a, '--text=') === 0)        $textArg = substr($a, 7);
    }
}

$out = ['processed' => null, 'proposal' => null, 'skipped' => [], 'items' => [], 'unmatched' => []];

try {
    if (!ai_configured()) throw new RuntimeException('IA desligada (OLLAMA_URL/AI_CONFIRM_ENABLED)');

    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    // ── modo teste: lê de um arquivo, não grava nada ──────
    if ($textArg !== null) {
        if (!is_file($textArg)) throw new RuntimeException("arquivo não encontrado: $textArg");
        $text = file_get_contents($textArg);
        $date = ai_target_rodada($pdo);

        echo "Rodada alvo: $date\n";
        echo 'Passa no filtro: ' . (ai_looks_like_roster($text) ? 'sim' : 'NÃO') . "\n";
        echo 'Modelo: ' . ai_cfg('OLLAMA_MODEL') . "\n\n";

        $read = ai_ollama_extract($text);
        if (empty($read['ok'])) { echo "ERRO: {$read['err']}\n"; exit(1); }

        echo "── o que a IA leu ──\n";
        foreach ($read['entries'] as $e) {
            printf("  %-24s %s%s%s\n", $e['name'] ?? '?',
                !empty($e['check']) ? '✅' : '  ',
                !empty($e['meat'])  ? '🥩' : '  ',
                !empty($e['cross']) ? '❌' : '');
        }

        $prop = ai_build_proposal($pdo, $read['entries'], $date);
        echo "\n── mudanças propostas ──\n";
        foreach ($prop['items'] as $it) {
            printf("  #%-4d %-24s %s -> %s\n", $it['player_id'], $it['player_name'],
                   $it['from_status'], $it['to_status']);
        }
        if (!$prop['items']) echo "  (nenhuma)\n";
        if ($prop['unmatched']) {
            echo "\n── não reconhecidos ──\n";
            foreach ($prop['unmatched'] as $u) {
                printf("  %-24s %-16s (%s)\n", $u['raw'], $u['status'], $u['reason']);
            }
        }
        echo "\n(dry-run: nada foi gravado)\n";
        exit(0);
    }

    // Execução anterior que morreu no meio deixaria a linha presa em 'processing'.
    $pdo->exec("UPDATE wa_inbox SET status = 'new'
                WHERE status = 'processing' AND parsed_at < (NOW() - INTERVAL 30 MINUTE)");

    // ── 1 mensagem por execução: se a inferência estiver lenta, não empilha ──
    // A inferência local passa de 40s e o cron roda de minuto em minuto, então duas
    // execuções se cruzam. Reserva antes de trabalhar: quem perder o UPDATE desiste.
    $row = $pdo->query("SELECT id, body, sender_name FROM wa_inbox
                        WHERE status = 'new' ORDER BY id ASC LIMIT 1")
               ->fetch(PDO::FETCH_ASSOC);

    if ($row && !$dryRun) {
        $claim = $pdo->prepare("UPDATE wa_inbox SET status = 'processing', parsed_at = NOW()
                                WHERE id = ? AND status = 'new'");
        $claim->execute([(int)$row['id']]);
        if ($claim->rowCount() !== 1) {
            $out['skipped'][] = 'outra execução já pegou a mensagem';
            $row = null;
        }
    }

    if (!$row) {
        if (!$out['skipped']) $out['skipped'][] = 'nada novo na inbox';
    } else {
        $inboxId = (int)$row['id'];
        $out['processed'] = $inboxId;

        $read = ai_ollama_extract($row['body']);
        if (empty($read['ok'])) {
            $pdo->prepare("UPDATE wa_inbox SET status = 'error', parse_error = ?, parsed_at = NOW() WHERE id = ?")
                ->execute([substr($read['err'] ?? 'erro', 0, 255), $inboxId]);
            $out['skipped'][] = 'falha na IA: ' . ($read['err'] ?? '?');
        } else {
            $date = ai_target_rodada($pdo);
            $prop = ai_build_proposal($pdo, $read['entries'], $date);
            $out['items']     = $prop['items'];
            $out['unmatched'] = $prop['unmatched'];

            if (!$dryRun) {
                if ($prop['items'] || $prop['unmatched']) {
                    // proposta antiga da mesma rodada vira histórico — vale a mais recente
                    $pdo->prepare("UPDATE ai_confirm_proposals SET status = 'superseded'
                                   WHERE status = 'pending' AND rodada_date = ?")
                        ->execute([$date]);

                    $ins = $pdo->prepare('INSERT INTO ai_confirm_proposals
                        (inbox_id, rodada_date, model, items, unmatched) VALUES (?, ?, ?, ?, ?)');
                    $ins->execute([
                        $inboxId, $date, ai_cfg('OLLAMA_MODEL'),
                        json_encode($prop['items'],     JSON_UNESCAPED_UNICODE),
                        json_encode($prop['unmatched'], JSON_UNESCAPED_UNICODE),
                    ]);
                    $out['proposal'] = (int)$pdo->lastInsertId();
                } else {
                    $out['skipped'][] = 'lista lida, nada mudou';
                }
                $pdo->prepare("UPDATE wa_inbox SET status = 'parsed', parsed_at = NOW() WHERE id = ?")
                    ->execute([$inboxId]);
            }
        }
    }

    // poda: a inbox é só rastro de depuração
    if (!$dryRun) {
        $pdo->exec('DELETE FROM wa_inbox WHERE received_at < (NOW() - INTERVAL 7 DAY)');
    }
} catch (Throwable $e) {
    $out['error'] = $e->getMessage();
}

echo json_encode($out, JSON_UNESCAPED_UNICODE) . "\n";
