<?php
// Execute este arquivo UMA VEZ após o setup.php para criar as tabelas de autenticação.
// Acesse: https://seusite.com/api/auth_setup.php
// Depois pode deletar ou proteger este arquivo.

require __DIR__ . '/config.php';
?>
<!DOCTYPE html>
<html lang="pt-br">
<head>
  <meta charset="UTF-8">
  <title>Fut Segunda — Auth Setup</title>
  <style>
    body { font-family: sans-serif; max-width: 600px; margin: 40px auto; padding: 20px;
           background: #060d1c; color: #e2e8f0; }
    h1   { color: #4ade80; margin-bottom: 24px; }
    p    { line-height: 1.7; margin: 8px 0; }
    code { background: rgba(255,255,255,0.08); padding: 2px 8px; border-radius: 4px;
           font-family: monospace; color: #fbbf24; }
    .ok   { color: #4ade80; }
    .warn { color: #fbbf24; }
    .err  { color: #f87171; }
    hr    { border: none; border-top: 1px solid rgba(255,255,255,0.1); margin: 20px 0; }
  </style>
</head>
<body>
<h1>⚽ Fut Segunda — Auth Setup</h1>
<?php
// Garante que o usuário existe com a senha e role corretas (cria ou faz reset completo)
function ensureUser($pdo, $username, $password, $role) {
    $hash = password_hash($password, PASSWORD_BCRYPT);
    $stmt = $pdo->prepare('SELECT id FROM users WHERE username = ?');
    $stmt->execute([$username]);
    $existing = $stmt->fetch(PDO::FETCH_ASSOC);
    if ($existing) {
        $pdo->prepare('UPDATE users SET password = ?, role = ? WHERE id = ?')
            ->execute([$hash, $role, $existing['id']]);
        echo '<p class="ok">✅ Usuário <code>' . htmlspecialchars($username) . '</code> atualizado → role <strong>' . $role . '</strong>, senha redefinida.</p>';
    } else {
        $pdo->prepare('INSERT INTO users (username, password, role) VALUES (?, ?, ?)')
            ->execute([$username, $hash, $role]);
        echo '<p class="ok">✅ Usuário <code>' . htmlspecialchars($username) . '</code> criado (' . $role . ').</p>';
    }
}

try {
    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    // Tabela de usuários
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS users (
            id         INT AUTO_INCREMENT PRIMARY KEY,
            username   VARCHAR(64)  NOT NULL UNIQUE,
            password   VARCHAR(255) NOT NULL,
            role       ENUM('admin','viewer') NOT NULL DEFAULT 'viewer',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>users</code> criada/verificada.</p>';

    // Garante coluna role em users (caso tabela existia antes desta versão)
    try {
        $pdo->exec("ALTER TABLE users ADD COLUMN role ENUM('admin','viewer') NOT NULL DEFAULT 'viewer'");
        echo '<p class="ok">✅ Coluna <code>role</code> adicionada à tabela <code>users</code>.</p>';
    } catch (PDOException $e) {
        echo '<p style="color:#94a3b8">ℹ️ Coluna <code>role</code> já existe em <code>users</code>.</p>';
    }

    // Tabela de sessões
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS sessions (
            token      CHAR(64)  NOT NULL,
            user_id    INT       NOT NULL,
            role       ENUM('admin','viewer') NOT NULL DEFAULT 'viewer',
            expires_at DATETIME  NOT NULL,
            PRIMARY KEY (token),
            INDEX idx_user (user_id),
            CONSTRAINT fk_session_user
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>sessions</code> criada/verificada.</p>';

    // Garante coluna role em sessions
    try {
        $pdo->exec("ALTER TABLE sessions ADD COLUMN role ENUM('admin','viewer') NOT NULL DEFAULT 'viewer'");
        echo '<p class="ok">✅ Coluna <code>role</code> adicionada à tabela <code>sessions</code>.</p>';
    } catch (PDOException $e) {
        echo '<p style="color:#94a3b8">ℹ️ Coluna <code>role</code> já existe em <code>sessions</code>.</p>';
    }

    // Tabela de controle de envio ao WhatsApp (debounce/anti-spam por rodada)
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS whatsapp_sent (
            rodada_date    VARCHAR(10) NOT NULL PRIMARY KEY,
            last_sent_at   DATETIME    NULL,
            last_sent_hash CHAR(40)    NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>whatsapp_sent</code> criada/verificada.</p>';

    // ── Leitura por IA da lista do grupo ──────────────────────────────
    // Mensagens cruas recebidas pelo webhook da Evolution (rastro/depuração).
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS wa_inbox (
            id          INT AUTO_INCREMENT PRIMARY KEY,
            wa_msg_id   VARCHAR(96)  NOT NULL,
            chat_jid    VARCHAR(64)  NOT NULL,
            sender_name VARCHAR(100) NULL,
            body        TEXT         NOT NULL,
            status      ENUM('new','skipped','parsed','error') NOT NULL DEFAULT 'new',
            parse_error VARCHAR(255) NULL,
            received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            parsed_at   DATETIME NULL,
            UNIQUE KEY uq_msg (wa_msg_id),
            INDEX idx_status (status, received_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>wa_inbox</code> criada/verificada.</p>';

    // Propostas de mudança que a IA montou, aguardando aprovação do admin.
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS ai_confirm_proposals (
            id          INT AUTO_INCREMENT PRIMARY KEY,
            inbox_id    INT  NOT NULL,
            rodada_date DATE NOT NULL,
            status      ENUM('pending','approved','rejected','superseded') NOT NULL DEFAULT 'pending',
            model       VARCHAR(64)  NULL,
            items       LONGTEXT NOT NULL,
            unmatched   LONGTEXT NULL,
            created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            decided_at  DATETIME NULL,
            decided_by  VARCHAR(64) NULL,
            INDEX idx_status (status, rodada_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>ai_confirm_proposals</code> criada/verificada.</p>';

    // A IA precisa confirmar quem NÃO tem WhatsApp cadastrado (suplente que entra no
    // lugar de alguém). Esses não recebem link, então a linha nasce sem telefone.
    // No MySQL o índice UNIQUE aceita vários NULL, então uq_rodada_phone continua valendo.
    try {
        $pdo->exec("ALTER TABLE presence_confirmations MODIFY phone CHAR(11) NULL");
        echo '<p class="ok">✅ Coluna <code>phone</code> de <code>presence_confirmations</code> agora aceita NULL.</p>';
    } catch (PDOException $e) {
        echo '<p class="warn">⚠️ Não consegui alterar <code>presence_confirmations.phone</code>: ' . htmlspecialchars($e->getMessage()) . '</p>';
    }

    echo '<hr>';

    // ── Usuários (reset completo: força role admin + senha correta) ──
    ensureUser($pdo, 'gui',   '198200', 'admin');
    ensureUser($pdo, 'admin', 'admin',  'admin');

    // Invalidar todas as sessões antigas (forçar re-login com nova role)
    $pdo->exec("DELETE FROM sessions");
    echo '<p class="ok">✅ Sessões antigas invalidadas — faça login novamente.</p>';

    echo '<hr>';
    echo '<p class="ok">✅ Setup concluído!</p>';
    echo '<p class="warn">⚠️ <strong>Delete este arquivo do servidor após rodar.</strong></p>';

} catch (PDOException $e) {
    echo '<p class="err">❌ Erro: ' . htmlspecialchars($e->getMessage()) . '</p>';
}
?>
</body>
</html>
