<?php
// Execute este arquivo UMA VEZ no navegador após o deploy para criar a tabela:
// https://seusite.com/api/setup.php
// Depois pode deletar ou proteger este arquivo.

require __DIR__ . '/config.php';

try {
    $pdo = new PDO(
        'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    $pdo->exec("
        CREATE TABLE IF NOT EXISTS app_data (
            id         INT          NOT NULL DEFAULT 1,
            data       LONGTEXT     NOT NULL,
            updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    ");

    echo '<p style="font-family:sans-serif;color:green;font-size:1.2rem">
            ✅ Tabela criada com sucesso! Pode deletar este arquivo agora.
          </p>';
} catch (PDOException $e) {
    echo '<p style="font-family:sans-serif;color:red">
            ❌ Erro: ' . htmlspecialchars($e->getMessage()) . '
          </p>';
}
