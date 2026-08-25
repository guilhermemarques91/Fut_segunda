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
define('SITE_URL', '');             // ex: https://fut.barleiseca.com.br (sem barra final)

// ── IA local (Ollama) que lê a lista repostada no grupo ──────────────────
// Roda na mesma máquina da Evolution, exposta pelo mesmo Cloudflare Tunnel.
// Deixe OLLAMA_URL vazio para desligar a leitura por IA (nada quebra).
define('OLLAMA_URL',   '');                    // ex: https://ollama.guimarques.dev.br
define('OLLAMA_MODEL', 'gemma4:12b');          // testado: 21/21 na lista real do grupo
define('OLLAMA_TIMEOUT', 600);                 // seg. — medido ~45s no gemma4:12b
// Desligar o "thinking" do modelo: medido 204s -> 44s, com resultado idêntico.
// Só ligue se trocar por um modelo que dependa de raciocínio para ler a lista.
define('AI_THINK', false);
// O Ollama está atrás do Cloudflare Access. Crie um Service Token em
// Zero Trust → Access controls → Service auth e libere-o na policy do app.
define('CF_ACCESS_CLIENT_ID',     '');         // ....access
define('CF_ACCESS_CLIENT_SECRET', '');
define('WA_WEBHOOK_KEY', '');                  // ?key=... que a Evolution manda no webhook
define('AI_CONFIRM_ENABLED', true);            // desliga a leitura por IA sem mexer em código
