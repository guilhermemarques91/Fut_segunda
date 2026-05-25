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

    // Tabela de sessões
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS sessions (
            token      CHAR(64)  NOT NULL,
            user_id    INT       NOT NULL,
            role       ENUM('admin','viewer') NOT NULL,
            expires_at DATETIME  NOT NULL,
            PRIMARY KEY (token),
            INDEX idx_user (user_id),
            CONSTRAINT fk_session_user
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");
    echo '<p class="ok">✅ Tabela <code>sessions</code> criada/verificada.</p>';

    // Cria admin padrão se não existir
    $stmt = $pdo->prepare('SELECT id FROM users WHERE username = ?');
    $stmt->execute(['admin']);
    if (!$stmt->fetch()) {
        $hash = password_hash('fut@admin', PASSWORD_BCRYPT);
        $pdo->prepare('INSERT INTO users (username, password, role) VALUES (?, ?, ?)')
            ->execute(['admin', $hash, 'admin']);
        echo '<p class="ok">✅ Usuário <code>admin</code> criado.</p>';
        echo '<p class="warn">⚠️ Credenciais iniciais — troque a senha após o primeiro login!</p>';
        echo '<p>Usuário: <code>admin</code> &nbsp;|&nbsp; Senha: <code>fut@admin</code></p>';
    } else {
        echo '<p style="color:#94a3b8">ℹ️ Usuário <code>admin</code> já existe — senha não foi alterada.</p>';
    }

    echo '<hr>';
    echo '<p class="ok">✅ Setup concluído! <strong>Delete este arquivo do servidor agora.</strong></p>';

} catch (PDOException $e) {
    echo '<p class="err">❌ Erro: ' . htmlspecialchars($e->getMessage()) . '</p>';
}
?>
</body>
</html>
