<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Api-Key, X-Auth-Token');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

require __DIR__ . '/config.php';

// Autenticação
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

// Valida token de usuário (header ou query string para sendBeacon)
$authToken = $_SERVER['HTTP_X_AUTH_TOKEN'] ?? $_GET['token'] ?? '';
if (!$authToken) {
    http_response_code(401);
    echo json_encode(['error' => 'Auth token required']);
    exit;
}
$stmtTok = $pdo->prepare(
    'SELECT user_id, role FROM sessions WHERE token = ? AND expires_at > NOW()'
);
$stmtTok->execute([$authToken]);
$session = $stmtTok->fetch(PDO::FETCH_ASSOC);
if (!$session) {
    http_response_code(401);
    echo json_encode(['error' => 'Token inválido ou expirado']);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];

// GET — carrega todos os dados
if ($method === 'GET') {
    $stmt = $pdo->query('SELECT data, updated_at FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    if ($row) {
        echo $row['data']; // já é JSON
    } else {
        echo '{}'; // banco vazio, primeira vez
    }
    exit;
}

// POST — salva todos os dados (somente admin)
if ($method === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['error' => 'Forbidden — somente admin pode salvar dados']);
        exit;
    }
    $body = file_get_contents('php://input');
    if (!$body || !json_decode($body)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid JSON']);
        exit;
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
