<?php
// Cron (1x/min): envia ao grupo a lista de confirmados de cada rodada ativa,
// UMA vez por janela de silêncio (debounce) e só quando a lista muda.
// Rodar via: cron CLI  ->  php api/dispatch_confirmados.php
//        ou  HTTP      ->  /api/dispatch_confirmados.php?key=WA_CRON_KEY

require __DIR__ . '/config.php';
require __DIR__ . '/whatsapp.php';

$isCli = (php_sapi_name() === 'cli');
if (!$isCli) {
    header('Content-Type: application/json; charset=utf-8');
    $key = $_GET['key'] ?? '';
    if (!wa_cfg('WA_CRON_KEY') || $key !== wa_cfg('WA_CRON_KEY')) {
        http_response_code(403);
        echo json_encode(['error' => 'forbidden']);
        exit;
    }
}

$out = ['processed' => [], 'sent' => [], 'skipped' => []];

try {
    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    $quiet = (int) wa_cfg('WA_QUIET_SECONDS', 90);

    // Garante a tabela de controle de envio (não depende de rodar o auth_setup.php).
    $pdo->exec("CREATE TABLE IF NOT EXISTS whatsapp_sent (
        rodada_date    VARCHAR(10) NOT NULL PRIMARY KEY,
        last_sent_at   DATETIME    NULL,
        last_sent_hash CHAR(40)    NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Rodadas com confirmações nas últimas 6h e alguém que respondeu (não pending)
    $stmt = $pdo->query("
        SELECT rodada_date,
               MAX(confirmed_at) AS last_resp,
               TIMESTAMPDIFF(SECOND, MAX(confirmed_at), NOW()) AS quiet_secs
        FROM presence_confirmations
        WHERE status <> 'pending' AND confirmed_at IS NOT NULL
          AND confirmed_at >= (NOW() - INTERVAL 6 HOUR)
        GROUP BY rodada_date
    ");
    $rodadas = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $selSent = $pdo->prepare('SELECT last_sent_hash FROM whatsapp_sent WHERE rodada_date = ?');
    $upSent  = $pdo->prepare('INSERT INTO whatsapp_sent (rodada_date, last_sent_at, last_sent_hash)
                              VALUES (?, NOW(), ?)
                              ON DUPLICATE KEY UPDATE last_sent_at = NOW(), last_sent_hash = VALUES(last_sent_hash)');

    foreach ($rodadas as $r) {
        $date = $r['rodada_date'];
        $out['processed'][] = $date;

        if ((int)$r['quiet_secs'] < $quiet) { $out['skipped'][] = "$date (silêncio insuficiente)"; continue; }

        $msg  = wa_build_confirmados_msg($pdo, $date);
        $hash = sha1($msg);

        $selSent->execute([$date]);
        $prev = $selSent->fetch(PDO::FETCH_ASSOC);
        if ($prev && $prev['last_sent_hash'] === $hash) { $out['skipped'][] = "$date (sem mudança)"; continue; }

        $send = wa_send_group_text($msg);
        if (!empty($send['ok'])) {
            $upSent->execute([$date, $hash]);
            $out['sent'][] = $date;
        } else {
            $out['skipped'][] = "$date (falha envio: " . ($send['err'] ?? $send['code'] ?? '?') . ')';
        }
    }
} catch (Throwable $e) {
    $out['error'] = $e->getMessage();
}

echo json_encode($out, JSON_UNESCAPED_UNICODE) . "\n";
