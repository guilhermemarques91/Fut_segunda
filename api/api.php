<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Api-Key, X-Auth-Token');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

set_exception_handler(function($e) {
    if (!headers_sent()) http_response_code(500);
    echo json_encode(['error' => 'Erro interno: ' . $e->getMessage()]);
    exit;
});

require __DIR__ . '/config.php';

// Valida API key
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

$action = $_GET['action'] ?? '';

// ── HELPER ───────────────────────────────────────────────
function getSession($pdo, $token) {
    if (!$token) return null;
    $stmt = $pdo->prepare(
        'SELECT s.user_id, s.role, u.username
         FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token = ? AND s.expires_at > NOW()'
    );
    $stmt->execute([$token]);
    return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
}

// ── LOGIN (não exige token) ──────────────────────────────
if ($action === 'login') {
    $body     = json_decode(file_get_contents('php://input'), true) ?? [];
    $username = trim($body['username'] ?? '');
    $password = $body['password'] ?? '';

    if (!$username || !$password) {
        http_response_code(400);
        echo json_encode(['error' => 'Usuário e senha obrigatórios']);
        exit;
    }

    $stmt = $pdo->prepare('SELECT id, password, role FROM users WHERE username = ?');
    $stmt->execute([$username]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Usuário ou senha incorretos']);
        exit;
    }

    $pdo->prepare('DELETE FROM sessions WHERE user_id = ? AND expires_at < NOW()')
        ->execute([$user['id']]);

    $token   = bin2hex(random_bytes(32));
    $expires = date('Y-m-d H:i:s', strtotime('+30 days'));
    $pdo->prepare('INSERT INTO sessions (token, user_id, role, expires_at) VALUES (?, ?, ?, ?)')
        ->execute([$token, $user['id'], $user['role'], $expires]);

    echo json_encode(['token' => $token, 'role' => $user['role'], 'username' => $username]);
    exit;
}

// ── Valida token para todas as outras ações ──────────────
$authToken = $_SERVER['HTTP_X_AUTH_TOKEN'] ?? $_GET['token'] ?? '';
if (!$authToken) {
    http_response_code(401);
    echo json_encode(['error' => 'Auth token required']);
    exit;
}
$session = getSession($pdo, $authToken);
if (!$session) {
    http_response_code(401);
    echo json_encode(['error' => 'Token inválido ou expirado']);
    exit;
}

// ── VALIDATE ────────────────────────────────────────────
if ($action === 'validate') {
    echo json_encode(['ok' => true, 'role' => $session['role'], 'username' => $session['username']]);
    exit;
}

// ── LOGOUT ──────────────────────────────────────────────
if ($action === 'logout') {
    $pdo->prepare('DELETE FROM sessions WHERE token = ?')->execute([$authToken]);
    echo json_encode(['ok' => true]);
    exit;
}

// ── CHANGE PASSWORD ──────────────────────────────────────
if ($action === 'change_password') {
    $body        = json_decode(file_get_contents('php://input'), true) ?? [];
    $oldPassword = $body['old_password'] ?? '';
    $newPassword = $body['new_password'] ?? '';

    if (strlen($newPassword) < 6) {
        http_response_code(400);
        echo json_encode(['error' => 'Nova senha deve ter pelo menos 6 caracteres']);
        exit;
    }

    $stmt = $pdo->prepare('SELECT password FROM users WHERE id = ?');
    $stmt->execute([$session['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!password_verify($oldPassword, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Senha atual incorreta']);
        exit;
    }

    $hash = password_hash($newPassword, PASSWORD_BCRYPT);
    $pdo->prepare('UPDATE users SET password = ? WHERE id = ?')
        ->execute([$hash, $session['user_id']]);

    echo json_encode(['ok' => true]);
    exit;
}

// ── Actions somente para admin ───────────────────────────
if (in_array($action, ['list_users', 'create_user', 'delete_user'], true)) {
    if ($session['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['error' => 'Acesso negado']);
        exit;
    }

    if ($action === 'list_users') {
        $stmt = $pdo->query('SELECT id, username, role, created_at FROM users ORDER BY id');
        echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
        exit;
    }

    if ($action === 'create_user') {
        $body     = json_decode(file_get_contents('php://input'), true) ?? [];
        $username = trim($body['username'] ?? '');
        $password = $body['password'] ?? '';
        $role     = $body['role'] ?? 'viewer';

        if (!$username || strlen($password) < 6) {
            http_response_code(400);
            echo json_encode(['error' => 'Usuário obrigatório e senha mínima de 6 caracteres']);
            exit;
        }
        if (!in_array($role, ['admin', 'viewer'], true)) {
            http_response_code(400);
            echo json_encode(['error' => 'Role inválido']);
            exit;
        }

        $chk = $pdo->prepare('SELECT id FROM users WHERE username = ?');
        $chk->execute([$username]);
        if ($chk->fetch()) {
            http_response_code(409);
            echo json_encode(['error' => 'Usuário já existe']);
            exit;
        }

        $hash = password_hash($password, PASSWORD_BCRYPT);
        $pdo->prepare('INSERT INTO users (username, password, role) VALUES (?, ?, ?)')
            ->execute([$username, $hash, $role]);

        echo json_encode(['ok' => true, 'id' => (int) $pdo->lastInsertId()]);
        exit;
    }

    if ($action === 'delete_user') {
        $body = json_decode(file_get_contents('php://input'), true) ?? [];
        $id   = (int) ($body['id'] ?? 0);

        if (!$id) {
            http_response_code(400);
            echo json_encode(['error' => 'ID inválido']);
            exit;
        }
        if ($id === (int) $session['user_id']) {
            http_response_code(400);
            echo json_encode(['error' => 'Você não pode remover sua própria conta']);
            exit;
        }

        $pdo->prepare('DELETE FROM users WHERE id = ?')->execute([$id]);
        echo json_encode(['ok' => true]);
        exit;
    }
}

// ── GET — carrega todos os dados ─────────────────────────
$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $stmt = $pdo->query('SELECT data, updated_at FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    echo $row ? $row['data'] : '{}';
    exit;
}

// ── POST — salva todos os dados (somente admin) ──────────
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
