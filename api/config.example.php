<?php
// Copie este arquivo para config.php e preencha com suas credenciais do Hostgator
// NÃO envie config.php para o GitHub (já está no .gitignore)

define('DB_HOST', 'localhost');
define('DB_NAME', 'seu_banco');        // ex: usuario_futsegunda
define('DB_USER', 'seu_usuario');      // ex: usuario_futsegunda
define('DB_PASS', 'sua_senha');

// Chave secreta — coloque a mesma no frontend (index.html → SERVER_CONFIG.apiKey)
define('API_KEY', 'troque-por-uma-chave-secreta-qualquer');

// ── WhatsApp (Evolution API) ─────────────────────────────────────────────
// A Evolution roda FORA do Hostgator (ex.: notebook + Cloudflare Tunnel).
// Deixe EVOLUTION_URL vazio para desligar o envio (o app não quebra).
define('EVOLUTION_URL', '');        // ex: https://evo.seu-tunel.exemplo
define('EVOLUTION_INSTANCE', '');   // ex: futsegunda
define('EVOLUTION_APIKEY', '');     // AUTHENTICATION_API_KEY da Evolution
define('EVOLUTION_GROUP_JID', '');  // ex: [email protected] (use wa_list_groups p/ achar)
define('WA_QUIET_SECONDS', 90);     // janela de silêncio p/ consolidar o envio
define('WA_CRON_KEY', '');          // segredo p/ chamar o cron via HTTP (?key=...)
