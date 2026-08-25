<?php
// Receptor do webhook da Evolution API (evento MESSAGES_UPSERT).
//
// Só GRAVA a mensagem e responde 200 na hora. A leitura pela IA é feita depois pelo
// cron ai_parse_inbox.php — o Ollama local pode levar minutos, e webhook lento faz a
// Evolution entrar em retry.
//
// Configurar na Evolution (ou pelo botão do app -> action=wa_set_webhook):
//   POST {EVOLUTION_URL}/webhook/set/{instance}
//   { "webhook": { "enabled": true, "events": ["MESSAGES_UPSERT"],
//                  "url": "https://SEUSITE/api/wa_webhook.php?key=WA_WEBHOOK_KEY" } }

require __DIR__ . '/config.php';
require_once __DIR__ . '/ai_parse.php';

header('Content-Type: application/json; charset=utf-8');

// Responde 200 sempre que possível: 5xx faz a Evolution reenviar em loop.
function wh_out($payload) {
    echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    exit;
}

$key = $_GET['key'] ?? '';
if (!ai_cfg('WA_WEBHOOK_KEY') || !hash_equals((string) ai_cfg('WA_WEBHOOK_KEY'), (string) $key)) {
    http_response_code(403);
    wh_out(['error' => 'forbidden']);
}

$raw  = file_get_contents('php://input');
$body = json_decode($raw, true);
if (!is_array($body)) wh_out(['ok' => true, 'skip' => 'payload vazio']);

// A Evolution manda o evento em maiúscula ou com ponto, dependendo da versão.
$event = strtolower(str_replace('_', '.', (string)($body['event'] ?? '')));
if ($event !== '' && $event !== 'messages.upsert') wh_out(['ok' => true, 'skip' => 'evento ' . $event]);

// Em alguns modos data vem como lista de mensagens; normaliza para uma só.
$data = $body['data'] ?? [];
if (isset($data[0]) && is_array($data[0])) $data = $data[0];

$msgId  = (string)($data['key']['id'] ?? '');
$jid    = (string)($data['key']['remoteJid'] ?? '');
$fromMe = !empty($data['key']['fromMe']);
$sender = (string)($data['pushName'] ?? '');

$msg  = $data['message'] ?? [];
$text = $msg['conversation']
     ?? ($msg['extendedTextMessage']['text']
     ?? ($msg['imageMessage']['caption']
     ?? ($msg['videoMessage']['caption'] ?? '')));
$text = trim((string)$text);

if (!$msgId || $text === '')                     wh_out(['ok' => true, 'skip' => 'sem texto']);
if ($jid !== ai_cfg('EVOLUTION_GROUP_JID'))      wh_out(['ok' => true, 'skip' => 'outro chat']);
// ANTI-LOOP: a lista consolidada que o dispatch_confirmados posta é fromMe.
if ($fromMe)                                     wh_out(['ok' => true, 'skip' => 'mensagem do bot']);

try {
    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    $status = ai_looks_like_roster($text) ? 'new' : 'skipped';

    // Guarda extra: alguém reencaminhando a própria mensagem do bot (aí não é fromMe).
    if ($status === 'new') {
        $h = $pdo->prepare('SELECT COUNT(*) FROM whatsapp_sent WHERE last_sent_hash = ?');
        $h->execute([sha1($text)]);
        if ((int)$h->fetchColumn() > 0) $status = 'skipped';
    }

    // INSERT IGNORE: a Evolution reenvia o mesmo messageId em retry/reconexão.
    $pdo->prepare('INSERT IGNORE INTO wa_inbox (wa_msg_id, chat_jid, sender_name, body, status)
                   VALUES (?, ?, ?, ?, ?)')
        ->execute([$msgId, $jid, $sender !== '' ? $sender : null, $text, $status]);

    wh_out(['ok' => true, 'status' => $status]);
} catch (Throwable $e) {
    // Erro nosso não pode virar retry infinito da Evolution.
    error_log('wa_webhook: ' . $e->getMessage());
    wh_out(['ok' => true, 'skip' => 'erro interno']);
}
