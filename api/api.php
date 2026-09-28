<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Api-Key, X-Auth-Token');
// Nunca cachear respostas da API (evita lista de usuários/dados desatualizados
// servidos pelo cache do navegador ou do LiteSpeed/Hostgator).
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Expires: 0');
// Desliga o cache público do LiteSpeed para as respostas da API
header('X-LiteSpeed-Cache-Control: no-cache');

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

// ── HELPERS ──────────────────────────────────────────────
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

function normalizePhone($raw) {
    $d = preg_replace('/\D/', '', (string)$raw);
    if (strlen($d) >= 12 && substr($d, 0, 2) === '55') $d = substr($d, 2);
    if (strlen($d) === 10) $d = substr($d, 0, 2) . '9' . substr($d, 2);
    return strlen($d) === 11 ? $d : null;
}

// ── HISTÓRICO PÚBLICO (sem token) ───────────────────────
if ($action === 'public') {
    $stmt = $pdo->query('SELECT data FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) { echo json_encode(['players'=>[],'attendances'=>[],'results'=>[],'teamHistory'=>[]]); exit; }
    $d = json_decode($row['data'], true) ?? [];
    $players = array_map(fn($p) => [
        'id'       => $p['id'],
        'name'     => $p['name'],
        'position' => $p['position'] ?? '',
        'overall'  => $p['overall']  ?? 60,
        'photo'    => $p['photo']    ?? null,
        'video'    => $p['video']    ?? null,
    ], $d['players'] ?? []);
    // Mapa id -> nome para resolver participantes e responsável pela louça
    $nameById = [];
    foreach ($d['players'] ?? [] as $p) { $nameById[$p['id']] = $p['name']; }
    // Tira-gosto público: SEM valores/cobranças/pendência — só janta, participantes e quem lavou
    $dinner = array_map(function($h) use ($nameById) {
        $partIds   = $h['participants'] ?? [];
        $partNames = array_values(array_filter(array_map(fn($id) => $nameById[$id] ?? null, $partIds)));
        $loucaId   = $h['loucaResponsavel'] ?? null;
        return [
            'date'         => $h['date'],
            'meal'         => $h['meal'] ?? '',
            'count'        => count($partIds),
            'participants' => $partNames,
            'louca'        => ($loucaId !== null && isset($nameById[$loucaId])) ? $nameById[$loucaId] : null,
        ];
    }, $d['dinnerHistory'] ?? []);
    echo json_encode([
        'players'       => $players,
        'attendances'   => $d['attendances']  ?? [],
        'results'       => $d['results']      ?? [],
        'teamHistory'   => $d['teamHistory']  ?? [],
        'dinnerHistory' => $dinner,
        'liveState'     => $d['liveState']    ?? null,
    ]);
    exit;
}

// ── VERSÃO DOS APPS (Android/Watch) — sem token, só API key ──
// Os apps consultam isso pra saber se existe build mais nova que a instalada
// (BuildConfig.VERSION_CODE local vs o que foi publicado aqui pelo painel).
if ($action === 'app_version') {
    $metaFile = __DIR__ . '/releases/meta.json';
    $meta = is_file($metaFile) ? (json_decode(file_get_contents($metaFile), true) ?: new stdClass()) : new stdClass();
    echo json_encode($meta);
    exit;
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

// ── LIVE UPDATE (público — só API key) ──────────────────
if ($action === 'live_update' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body)) { http_response_code(400); echo json_encode(['error' => 'Invalid JSON']); exit; }
    $stmt = $pdo->query('SELECT data FROM app_data WHERE id = 1');
    $row  = $stmt->fetch(PDO::FETCH_ASSOC);
    $d    = $row ? (json_decode($row['data'], true) ?? []) : [];
    $d['liveState'] = $body;
    $newJson = json_encode($d, JSON_UNESCAPED_UNICODE);
    $pdo->prepare('INSERT INTO app_data (id, data) VALUES (1, ?) ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP')
        ->execute([$newJson]);
    echo json_encode(['ok' => true]);
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

// ── LIVE EVENTS (autenticado — evita 2 dispositivos se atropelarem) ──
// Ao contrário de `live_update` (que sobrescreve o liveState inteiro com o
// que o cliente tinha em cache), estas actions fazem um read-modify-write
// atômico no servidor, uma única mudança por vez, e são idempotentes via
// `clientEventId` — reenviar o mesmo evento (ex.: fila offline do relógio
// depois de recuperar conexão) nunca duplica o efeito.
function _liveEventLock($pdo) {
    $pdo->beginTransaction();
    $stmt = $pdo->prepare('SELECT data FROM app_data WHERE id = 1 FOR UPDATE');
    $stmt->execute();
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        // Garante que a linha exista antes de travar
        $pdo->prepare('INSERT INTO app_data (id, data) VALUES (1, ?) ON DUPLICATE KEY UPDATE id = id')
            ->execute([json_encode(new stdClass())]);
        $stmt->execute();
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
    }
    return json_decode($row['data'], true) ?? [];
}

function _liveEventSave($pdo, $d) {
    $pdo->prepare('UPDATE app_data SET data = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1')
        ->execute([json_encode($d, JSON_UNESCAPED_UNICODE)]);
    $pdo->commit();
}

function _liveEventNowMs() { return (int) round(microtime(true) * 1000); }

if ($action === 'live_goal_add' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $clientEventId = trim($body['clientEventId'] ?? '');
    $team = $body['team'] ?? '';
    if (!$clientEventId || !in_array($team, ['home', 'away'], true)) {
        http_response_code(400);
        echo json_encode(['error' => 'clientEventId e team (home/away) são obrigatórios']);
        exit;
    }
    $d = _liveEventLock($pdo);
    $live = $d['liveState'] ?? [];
    $goalLog = $live['goalLog'] ?? [];
    $already = null;
    foreach ($goalLog as $e) { if (($e['clientEventId'] ?? null) === $clientEventId) { $already = $e; break; } }
    if (!$already) {
        $entry = [
            'clientEventId' => $clientEventId,
            'scorerId'      => $body['scorerId'] ?? null,
            'assistId'      => (!empty($body['ownGoal'])) ? null : ($body['assistId'] ?? null),
            'team'          => $team,
            'ownGoal'       => !empty($body['ownGoal']),
            'minute'        => $body['minute'] ?? null,
            'source'        => $body['source'] ?? 'web',
            'at'            => $body['at'] ?? _liveEventNowMs(),
        ];
        $goalLog[] = $entry;
        $live['goalLog'] = $goalLog;
        $d['liveState'] = $live;
        _liveEventSave($pdo, $d);
        echo json_encode(['ok' => true, 'duplicate' => false, 'goalLog' => $goalLog]);
    } else {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => true, 'goalLog' => $goalLog]);
    }
    exit;
}

if ($action === 'live_goal_undo' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $clientEventId = trim($body['clientEventId'] ?? '');
    $team = $body['team'] ?? '';
    if (!$clientEventId || !in_array($team, ['home', 'away'], true)) {
        http_response_code(400);
        echo json_encode(['error' => 'clientEventId e team (home/away) são obrigatórios']);
        exit;
    }
    $d = _liveEventLock($pdo);
    $live = $d['liveState'] ?? [];
    $goalLog = $live['goalLog'] ?? [];
    // Idempotência: se este undo (mesmo clientEventId) já foi aplicado antes, não desfaz de novo.
    $undoneIds = $live['undoneEventIds'] ?? [];
    if (in_array($clientEventId, $undoneIds, true)) {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => true, 'goalLog' => $goalLog]);
        exit;
    }
    $idx = null;
    for ($i = count($goalLog) - 1; $i >= 0; $i--) {
        if (($goalLog[$i]['team'] ?? null) === $team) { $idx = $i; break; }
    }
    if ($idx === null) {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => false, 'goalLog' => $goalLog]);
        exit;
    }
    array_splice($goalLog, $idx, 1);
    $undoneIds[] = $clientEventId;
    $live['goalLog'] = $goalLog;
    $live['undoneEventIds'] = array_slice($undoneIds, -200); // não deixa crescer sem limite
    $d['liveState'] = $live;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'duplicate' => false, 'goalLog' => $goalLog]);
    exit;
}

if ($action === 'live_period' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $clientEventId = trim($body['clientEventId'] ?? '');
    $event = $body['event'] ?? '';
    $validEvents = ['start_t1', 'end_t1', 'start_t2', 'end_t2', 'reset'];
    if (!$clientEventId || !in_array($event, $validEvents, true)) {
        http_response_code(400);
        echo json_encode(['error' => 'clientEventId e event válido são obrigatórios']);
        exit;
    }
    $d = _liveEventLock($pdo);
    $live = $d['liveState'] ?? [];
    $appliedIds = $live['appliedPeriodEventIds'] ?? [];
    if (in_array($clientEventId, $appliedIds, true)) {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => true, 'liveState' => $live]);
        exit;
    }
    $at = $body['at'] ?? _liveEventNowMs();
    // Elapsed do tempo corrente até agora, considerando se estava rodando.
    $elapsedNow = (int) ($live['timerElapsed'] ?? 0);
    if (!empty($live['timerRunning']) && !empty($live['timerStartedAt'])) {
        $elapsedNow += max(0, $at - (int) $live['timerStartedAt']);
    }
    switch ($event) {
        case 'start_t1':
            $live['periodo'] = 1;
            $live['timerRunning'] = true;
            $live['timerStartedAt'] = $at;
            $live['timerElapsed'] = $live['timerElapsed'] ?? 0;
            break;
        case 'end_t1':
            $live['t1ms'] = $elapsedNow;
            $live['timerRunning'] = false;
            $live['timerStartedAt'] = null;
            $live['timerElapsed'] = $elapsedNow;
            $live['intervaloStart'] = $at;
            break;
        case 'start_t2':
            $live['periodo'] = 2;
            $live['intervaloMs'] = !empty($live['intervaloStart']) ? max(0, $at - (int) $live['intervaloStart']) : ($live['intervaloMs'] ?? 0);
            $live['intervaloStart'] = null;
            $live['timerElapsed'] = 0;
            $live['timerStartedAt'] = $at;
            $live['timerRunning'] = true;
            break;
        case 'end_t2':
            $live['timerRunning'] = false;
            $live['timerStartedAt'] = null;
            $live['timerElapsed'] = $elapsedNow;
            $live['t2ended'] = true;
            break;
        case 'reset':
            $live['periodo'] = null;
            $live['timerRunning'] = false;
            $live['timerStartedAt'] = null;
            $live['timerElapsed'] = 0;
            $live['t1ms'] = null;
            $live['intervaloStart'] = null;
            $live['intervaloMs'] = null;
            $live['t2ended'] = false;
            break;
    }
    $appliedIds[] = $clientEventId;
    $live['appliedPeriodEventIds'] = array_slice($appliedIds, -200);
    $d['liveState'] = $live;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'duplicate' => false, 'liveState' => $live]);
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

// ── VERIFY PASSWORD ──────────────────────────────────────
if ($action === 'verify_password') {
    $body     = json_decode(file_get_contents('php://input'), true) ?? [];
    $password = $body['password'] ?? '';

    $stmt = $pdo->prepare('SELECT password FROM users WHERE id = ?');
    $stmt->execute([$session['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password'])) {
        http_response_code(401);
        echo json_encode(['error' => 'Senha incorreta']);
        exit;
    }

    echo json_encode(['ok' => true]);
    exit;
}

// ── GENERATE TOKENS (admin) ──────────────────────────────
if ($action === 'generate_tokens') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body    = json_decode(file_get_contents('php://input'), true) ?? [];
    $date    = $body['date'] ?? '';
    $players = $body['players'] ?? [];
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    $token = '';
    $bytes = random_bytes(12);
    for ($i = 0; $i < 12; $i++) $token .= $alphabet[ord($bytes[$i]) % strlen($alphabet)];
    $pdo->prepare('DELETE FROM presence_confirmations WHERE rodada_date = ?')->execute([$date]);
    $stmt = $pdo->prepare(
        'INSERT INTO presence_confirmations (rodada_date, token, phone, player_id, player_name) VALUES (?, ?, ?, ?, ?)'
    );
    $inserted = 0;
    foreach ($players as $p) {
        $phone = normalizePhone($p['phone'] ?? '');
        if (!$phone) continue;
        $stmt->execute([$date, $token, $phone, (string)($p['id'] ?? ''), (string)($p['name'] ?? '')]);
        $inserted++;
    }
    echo json_encode(['ok' => true, 'token' => $token, 'inserted' => $inserted]);
    exit;
}

// ── GET CONFIRMATIONS ────────────────────────────────────
if ($action === 'get_confirmations') {
    $date = $_GET['date'] ?? '';
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    $stmt = $pdo->prepare(
        'SELECT player_id, player_name, status, confirmed_at
         FROM presence_confirmations WHERE rodada_date = ? ORDER BY player_name'
    );
    $stmt->execute([$date]);
    echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
    exit;
}

// ── UPLOAD PLAYER VIDEO (admin) ──────────────────────────
if ($action === 'upload_player_video' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    // $_FILES vem vazio quando o upload excede upload_max_filesize/post_max_size do PHP —
    // sem essa checagem específica, o erro genérico de "nenhum arquivo" confunde o usuário.
    if (empty($_FILES) && (int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) {
        http_response_code(413);
        echo json_encode(['error' => 'Arquivo excede o limite de upload do servidor']);
        exit;
    }
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['error' => 'Nenhum arquivo enviado ou erro no upload']);
        exit;
    }
    $file = $_FILES['file'];
    $maxBytes = 8 * 1024 * 1024;
    if ($file['size'] > $maxBytes) {
        http_response_code(400);
        echo json_encode(['error' => 'Vídeo maior que 8MB']);
        exit;
    }
    $allowedExt = ['mp4' => 'video/mp4', 'webm' => 'video/webm', 'mov' => 'video/quicktime'];
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!isset($allowedExt[$ext])) {
        http_response_code(400);
        echo json_encode(['error' => 'Formato inválido — use mp4, webm ou mov']);
        exit;
    }
    $mime = @finfo_file(finfo_open(FILEINFO_MIME_TYPE), $file['tmp_name']);
    if (!$mime || strpos($mime, 'video/') !== 0) {
        http_response_code(400);
        echo json_encode(['error' => 'O arquivo não parece ser um vídeo válido']);
        exit;
    }
    $playerId = preg_replace('/[^0-9]/', '', (string) ($_POST['playerId'] ?? ''));
    if (!$playerId) {
        http_response_code(400);
        echo json_encode(['error' => 'playerId obrigatório']);
        exit;
    }
    $dir = __DIR__ . '/uploads/players';
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        http_response_code(500);
        echo json_encode(['error' => 'Não foi possível criar o diretório de upload']);
        exit;
    }
    if (!is_writable($dir)) {
        http_response_code(500);
        echo json_encode(['error' => 'Diretório de upload sem permissão de escrita']);
        exit;
    }
    $filename = $playerId . '_' . time() . '.' . $ext;
    if (!move_uploaded_file($file['tmp_name'], $dir . '/' . $filename)) {
        http_response_code(500);
        echo json_encode(['error' => 'Falha ao salvar o arquivo']);
        exit;
    }
    echo json_encode(['ok' => true, 'url' => '/api/uploads/players/' . $filename]);
    exit;
}

// ── DELETE PLAYER VIDEO (admin) ──────────────────────────
if ($action === 'delete_player_video' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $url  = $body['url'] ?? '';
    if ($url) {
        $uploadsDir = realpath(__DIR__ . '/uploads/players');
        $filename   = basename(parse_url($url, PHP_URL_PATH) ?: '');
        if ($uploadsDir && $filename) {
            $target = $uploadsDir . '/' . $filename;
            if (file_exists($target) && strpos(realpath($target) ?: '', $uploadsDir) === 0) {
                @unlink($target);
            }
        }
    }
    // Idempotente: arquivo já ausente não é erro.
    echo json_encode(['ok' => true]);
    exit;
}

// ── PUBLICAR BUILD DO APP (admin) — Android ou Watch ─────
// Guarda o .apk em releases/<platform>-latest.apk (sobrescreve a anterior) e
// atualiza releases/meta.json com a versão publicada. O download em si é
// servido por download_release.php (sem token — precisa abrir direto do
// navegador do celular/relógio).
if ($action === 'upload_app_release' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    if (empty($_FILES) && (int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) {
        http_response_code(413);
        echo json_encode(['error' => 'Arquivo excede o limite de upload do servidor']);
        exit;
    }
    if (!isset($_FILES['apk']) || $_FILES['apk']['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['error' => 'Nenhum arquivo enviado ou erro no upload']);
        exit;
    }
    $platform = $_POST['platform'] ?? '';
    if (!in_array($platform, ['android', 'wear'], true)) {
        http_response_code(400);
        echo json_encode(['error' => 'platform deve ser "android" ou "wear"']);
        exit;
    }
    $file = $_FILES['apk'];
    $maxBytes = 150 * 1024 * 1024;
    if ($file['size'] > $maxBytes) {
        http_response_code(400);
        echo json_encode(['error' => 'Arquivo maior que 150MB']);
        exit;
    }
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if ($ext !== 'apk') {
        http_response_code(400);
        echo json_encode(['error' => 'Envie um arquivo .apk']);
        exit;
    }
    $versionCode = (int) ($_POST['versionCode'] ?? 0);
    $versionName = trim($_POST['versionName'] ?? '');
    $notes       = trim($_POST['notes'] ?? '');
    if ($versionCode <= 0 || !$versionName) {
        http_response_code(400);
        echo json_encode(['error' => 'versionCode e versionName são obrigatórios']);
        exit;
    }
    $dir = __DIR__ . '/releases';
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        http_response_code(500);
        echo json_encode(['error' => 'Não foi possível criar o diretório de releases']);
        exit;
    }
    $filename = $platform . '-latest.apk';
    if (!move_uploaded_file($file['tmp_name'], $dir . '/' . $filename)) {
        http_response_code(500);
        echo json_encode(['error' => 'Falha ao salvar o arquivo']);
        exit;
    }
    $metaFile = $dir . '/meta.json';
    $meta = is_file($metaFile) ? (json_decode(file_get_contents($metaFile), true) ?: []) : [];
    $meta[$platform] = [
        'versionCode' => $versionCode,
        'versionName' => $versionName,
        'notes'       => $notes,
        'fileName'    => $filename,
        'size'        => $file['size'],
        'uploadedAt'  => date('Y-m-d H:i:s'),
        'uploadedBy'  => $session['username'],
    ];
    file_put_contents($metaFile, json_encode($meta, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    echo json_encode(['ok' => true, 'meta' => $meta[$platform]]);
    exit;
}

// ── GRANULAR — helpers de idempotência genérica ──────────
// Usado pelas actions abaixo (player_save, player_rate, etc.) — igual em
// espírito ao clientEventId dos live_*, mas como agora são muitas actions
// diferentes (não só 3), a lista de ids já aplicados fica numa chave só
// (_appliedWriteIds) em vez de uma por action.
function _alreadyApplied($d, $clientEventId) {
    return in_array($clientEventId, $d['_appliedWriteIds'] ?? [], true);
}
function _markApplied(&$d, $clientEventId) {
    $ids = $d['_appliedWriteIds'] ?? [];
    $ids[] = $clientEventId;
    $d['_appliedWriteIds'] = array_slice($ids, -500);
}

// ── JOGADORES: salvar (criar ou atualizar) — admin ───────
// Mesma receita do live_goal_add: lock da linha, merge raso na sub-chave
// (não substitui o objeto inteiro — uma tela que só manda `name` não pode
// apagar `attributes`/`balance` de quem já existia), id novo calculado no
// servidor (nunca confia em id vindo do cliente, evita colisão se dois
// dispositivos criarem ao mesmo tempo).
if ($action === 'player_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $clientEventId = trim($body['clientEventId'] ?? '');
    $player = $body['player'] ?? null;
    if (!$clientEventId || !is_array($player) || trim($player['name'] ?? '') === '') {
        http_response_code(400);
        echo json_encode(['error' => 'clientEventId e player.name são obrigatórios']);
        exit;
    }
    $d = _liveEventLock($pdo);
    if (_alreadyApplied($d, $clientEventId)) {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => true, 'players' => $d['players'] ?? []]);
        exit;
    }
    $players = $d['players'] ?? [];
    $id = $player['id'] ?? null;
    if ($id === null) {
        $maxId = 0;
        foreach ($players as $p) $maxId = max($maxId, (int) ($p['id'] ?? 0));
        $player['id']         = $maxId + 1;
        $player['balance']    = $player['balance']    ?? 0;
        $player['payments']   = $player['payments']   ?? [];
        $player['dinnerDebt'] = $player['dinnerDebt'] ?? 0;
        $player['lastRating'] = $player['lastRating'] ?? null;
        $players[] = $player;
    } else {
        $found = false;
        foreach ($players as $i => $p) {
            if ((int) ($p['id'] ?? -1) === (int) $id) {
                $players[$i] = array_merge($p, $player);
                $found = true;
                break;
            }
        }
        if (!$found) {
            $pdo->rollBack();
            http_response_code(404);
            echo json_encode(['error' => 'Jogador não encontrado']);
            exit;
        }
    }
    $d['players'] = $players;
    _markApplied($d, $clientEventId);
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'duplicate' => false, 'players' => $players]);
    exit;
}

// ── JOGADORES: remover — admin ───────────────────────────
if ($action === 'player_delete' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $id = (int) ($body['id'] ?? 0);
    if (!$id) {
        http_response_code(400); echo json_encode(['error' => 'id obrigatório']); exit;
    }
    $d = _liveEventLock($pdo);
    $d['players'] = array_values(array_filter($d['players'] ?? [], fn($p) => (int) ($p['id'] ?? -1) !== $id));
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'players' => $d['players']]);
    exit;
}

// ── JOGADORES: rating rápido (endpoint fino) — admin ─────
// Separado do player_save geral de propósito: a tela de "avaliação rápida"
// só deve mexer nos 3 atributos + overall + data, nunca no resto da ficha.
if ($action === 'player_rate' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $clientEventId = trim($body['clientEventId'] ?? '');
    $id    = (int) ($body['id'] ?? 0);
    $attrs = $body['attributes'] ?? null;
    if (!$clientEventId || !$id || !is_array($attrs)) {
        http_response_code(400);
        echo json_encode(['error' => 'clientEventId, id e attributes são obrigatórios']);
        exit;
    }
    $d = _liveEventLock($pdo);
    if (_alreadyApplied($d, $clientEventId)) {
        $pdo->rollBack();
        echo json_encode(['ok' => true, 'duplicate' => true, 'players' => $d['players'] ?? []]);
        exit;
    }
    $players = $d['players'] ?? [];
    $phy = max(1, min(99, (int) ($attrs['physical']  ?? 60)));
    $tac = max(1, min(99, (int) ($attrs['tactical']  ?? 60)));
    $tec = max(1, min(99, (int) ($attrs['technical'] ?? 60)));
    // Mesma fórmula do calcOverall() do painel web (frontend/index.html) — precisa
    // bater exatamente, senão o overall diverge entre o app e o painel.
    $overall = (int) round($phy * 0.3 + $tac * 0.35 + $tec * 0.35);
    $found = false;
    foreach ($players as $i => $p) {
        if ((int) ($p['id'] ?? -1) === $id) {
            $players[$i]['attributes'] = ['physical' => $phy, 'tactical' => $tac, 'technical' => $tec];
            $players[$i]['overall']    = $overall;
            $players[$i]['lastRating'] = date('Y-m-d');
            $found = true;
            break;
        }
    }
    if (!$found) {
        $pdo->rollBack();
        http_response_code(404);
        echo json_encode(['error' => 'Jogador não encontrado']);
        exit;
    }
    $d['players'] = $players;
    _markApplied($d, $clientEventId);
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'duplicate' => false, 'players' => $players]);
    exit;
}

// ── UPLOAD PLAYER PHOTO (admin) ───────────────────────────
// Clone de upload_player_video trocando a whitelist pra imagem — substitui
// a foto em data-URI inline (usada pelo painel web) por um arquivo de
// verdade, pra não inflar ainda mais o JSON granular.
if ($action === 'upload_player_photo' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    if (empty($_FILES) && (int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) {
        http_response_code(413);
        echo json_encode(['error' => 'Arquivo excede o limite de upload do servidor']);
        exit;
    }
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['error' => 'Nenhum arquivo enviado ou erro no upload']);
        exit;
    }
    $file = $_FILES['file'];
    $maxBytes = 5 * 1024 * 1024;
    if ($file['size'] > $maxBytes) {
        http_response_code(400);
        echo json_encode(['error' => 'Imagem maior que 5MB']);
        exit;
    }
    $allowedExt = ['jpg' => 'image/jpeg', 'jpeg' => 'image/jpeg', 'png' => 'image/png', 'webp' => 'image/webp'];
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!isset($allowedExt[$ext])) {
        http_response_code(400);
        echo json_encode(['error' => 'Formato inválido — use jpg, png ou webp']);
        exit;
    }
    $mime = @finfo_file(finfo_open(FILEINFO_MIME_TYPE), $file['tmp_name']);
    if (!$mime || strpos($mime, 'image/') !== 0) {
        http_response_code(400);
        echo json_encode(['error' => 'O arquivo não parece ser uma imagem válida']);
        exit;
    }
    $playerId = preg_replace('/[^0-9]/', '', (string) ($_POST['playerId'] ?? ''));
    if (!$playerId) {
        http_response_code(400);
        echo json_encode(['error' => 'playerId obrigatório']);
        exit;
    }
    $dir = __DIR__ . '/uploads/players';
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        http_response_code(500);
        echo json_encode(['error' => 'Não foi possível criar o diretório de upload']);
        exit;
    }
    if (!is_writable($dir)) {
        http_response_code(500);
        echo json_encode(['error' => 'Diretório de upload sem permissão de escrita']);
        exit;
    }
    $filename = $playerId . '_photo_' . time() . '.' . $ext;
    if (!move_uploaded_file($file['tmp_name'], $dir . '/' . $filename)) {
        http_response_code(500);
        echo json_encode(['error' => 'Falha ao salvar o arquivo']);
        exit;
    }
    echo json_encode(['ok' => true, 'url' => '/api/uploads/players/' . $filename]);
    exit;
}

// ── DELETE PLAYER PHOTO (admin) — best-effort, idempotente ─
if ($action === 'delete_player_photo' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $url  = $body['url'] ?? '';
    if ($url) {
        $uploadsDir = realpath(__DIR__ . '/uploads/players');
        $filename   = basename(parse_url($url, PHP_URL_PATH) ?: '');
        if ($uploadsDir && $filename) {
            $target = $uploadsDir . '/' . $filename;
            if (file_exists($target) && strpos(realpath($target) ?: '', $uploadsDir) === 0) {
                @unlink($target);
            }
        }
    }
    echo json_encode(['ok' => true]);
    exit;
}

// ── TARIFAS (mensal/avulso + isenção de goleiro) — admin ──
if ($action === 'fees_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $fees = $body['fees'] ?? null;
    if (!is_array($fees)) {
        http_response_code(400); echo json_encode(['error' => 'fees obrigatório']); exit;
    }
    $d = _liveEventLock($pdo);
    $d['fees'] = array_merge($d['fees'] ?? [], $fees);
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'fees' => $d['fees']]);
    exit;
}

// ── RODADA: presença — admin ─────────────────────────────
// Upsert por data (mesma semântica do autoSavePresenca() do painel web).
if ($action === 'attendance_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $date = trim($body['date'] ?? '');
    if (!$date) {
        http_response_code(400); echo json_encode(['error' => 'date obrigatório']); exit;
    }
    $entry = [
        'date'     => $date,
        'opponent' => $body['opponent'] ?? '',
        'players'  => $body['players']  ?? [],
        'noShow'   => $body['noShow']   ?? [],
        'manual'   => $body['manual']   ?? [],
        'imported' => $body['imported'] ?? new stdClass(),
    ];
    $d = _liveEventLock($pdo);
    $list = $d['attendances'] ?? [];
    $idx = null;
    foreach ($list as $i => $a) { if (($a['date'] ?? null) === $date) { $idx = $i; break; } }
    if ($idx !== null) { $list[$idx] = array_merge($list[$idx], $entry); } else { $list[] = $entry; }
    $d['attendances'] = $list;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'attendances' => $list]);
    exit;
}

// ── RODADA: reordenar fila de avulsos — admin ────────────
if ($action === 'avulso_order_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body  = json_decode(file_get_contents('php://input'), true) ?? [];
    $order = $body['order'] ?? null;
    if (!is_array($order)) {
        http_response_code(400); echo json_encode(['error' => 'order obrigatório']); exit;
    }
    $d = _liveEventLock($pdo);
    $d['avulsoOrder'] = $order;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'avulsoOrder' => $order]);
    exit;
}

// ── RODADA: times — admin ────────────────────────────────
// Upsert por data em teamHistory[] e espelha em results[] (pendente até ter
// gols), igual ao autoSaveTeams() do painel web — os dois sempre andam
// juntos lá, então ficam atômicos aqui também.
if ($action === 'team_history_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $date = trim($body['date'] ?? '');
    $home = $body['home'] ?? [];
    $away = $body['away'] ?? [];
    if (!$date) {
        http_response_code(400); echo json_encode(['error' => 'date obrigatório']); exit;
    }
    $d = _liveEventLock($pdo);

    $teamHistory = $d['teamHistory'] ?? [];
    $idx = null;
    foreach ($teamHistory as $i => $t) { if (($t['date'] ?? null) === $date) { $idx = $i; break; } }
    $entry = [
        'date'        => $date,
        'opponent'    => $body['opponent'] ?? ($idx !== null ? ($teamHistory[$idx]['opponent'] ?? '') : ''),
        'home'        => $home,
        'away'        => $away,
        'homeReserve' => $body['homeReserve'] ?? null,
        'awayReserve' => $body['awayReserve'] ?? null,
    ];
    if ($idx !== null) {
        $teamHistory[$idx] = array_merge($teamHistory[$idx], $entry);
    } else {
        $maxId = 0; foreach ($teamHistory as $t) $maxId = max($maxId, (int) ($t['id'] ?? 0));
        $entry['id'] = $maxId + 1;
        $teamHistory[] = $entry;
    }
    $d['teamHistory'] = $teamHistory;

    $results = $d['results'] ?? [];
    $ridx = null;
    foreach ($results as $i => $r) { if (($r['date'] ?? null) === $date) { $ridx = $i; break; } }
    if ($ridx !== null) {
        $results[$ridx]['homePlayerIds'] = $home;
        $results[$ridx]['awayPlayerIds'] = $away;
    } else {
        $maxRid = 0; foreach ($results as $r) $maxRid = max($maxRid, (int) ($r['id'] ?? 0));
        $results[] = [
            'id' => $maxRid + 1, 'date' => $date,
            'homeTeam' => 'T. Preto e Amarelo', 'awayTeam' => 'T. Azul',
            'homeScore' => null, 'awayScore' => null, 'pending' => true,
            'goals' => [], 'goalLog' => [], 'motm' => null,
            'homePlayerIds' => $home, 'awayPlayerIds' => $away,
        ];
    }
    $d['results'] = $results;

    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'teamHistory' => $teamHistory, 'results' => $results]);
    exit;
}

// ── RODADA: tira-gosto — admin ───────────────────────────
// Upsert por data. NÃO reproduz a baixa de débito de fecharTiraGosto() do
// painel web (mexe em player.dinnerDebt + despesa vinculada, junto com o
// Financeiro que só chega na Fase 3) — "closed"/"paidBy" ficam como estavam;
// fechar de vez com o débito ainda é só pelo painel web por enquanto.
if ($action === 'dinner_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $date = trim($body['date'] ?? '');
    if (!$date) {
        http_response_code(400); echo json_encode(['error' => 'date obrigatório']); exit;
    }
    $participants = $body['participants'] ?? [];
    $total        = (float) ($body['total'] ?? 0);
    $cnt          = count($participants);
    $realShare    = ($cnt > 0 && $total > 0) ? $total / $cnt : 0;
    $share        = $total > 0 ? (ceil($realShare / 5) * 5) : 0;

    $d = _liveEventLock($pdo);
    $list = $d['dinnerHistory'] ?? [];
    $idx = null;
    foreach ($list as $i => $h) { if (($h['date'] ?? null) === $date) { $idx = $i; break; } }
    $entry = [
        'date'             => $date,
        'meal'             => $body['meal'] ?? '',
        'total'            => $total,
        'share'            => $share,
        'realShare'        => $realShare,
        'participants'     => $participants,
        'loucaResponsavel' => $body['loucaResponsavel'] ?? null,
    ];
    if ($idx !== null) {
        $entry['id']      = $list[$idx]['id'];
        $entry['paidBy']  = $list[$idx]['paidBy']  ?? [];
        $entry['closed']  = $list[$idx]['closed']  ?? false;
        $list[$idx] = array_merge($list[$idx], $entry);
    } else {
        $maxId = 0; foreach ($list as $h) $maxId = max($maxId, (int) ($h['id'] ?? 0));
        $entry['id']     = $maxId + 1;
        $entry['paidBy'] = [];
        $entry['closed'] = false;
        $list[] = $entry;
    }
    $d['dinnerHistory'] = $list;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'dinnerHistory' => $list]);
    exit;
}

// ── RODADA: bloquear/reabrir — admin ─────────────────────
if ($action === 'locked_rodadas_save' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body   = json_decode(file_get_contents('php://input'), true) ?? [];
    $date   = trim($body['date'] ?? '');
    $locked = !empty($body['locked']);
    if (!$date) {
        http_response_code(400); echo json_encode(['error' => 'date obrigatório']); exit;
    }
    $d = _liveEventLock($pdo);
    $list = $d['lockedRodadas'] ?? [];
    if ($locked) {
        if (!in_array($date, $list, true)) $list[] = $date;
    } else {
        $list = array_values(array_filter($list, fn($x) => $x !== $date));
    }
    $d['lockedRodadas'] = $list;
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true, 'lockedRodadas' => $list]);
    exit;
}

// ── RODADA: excluir (com senha) — admin ──────────────────
if ($action === 'rodada_delete' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') {
        http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit;
    }
    $body     = json_decode(file_get_contents('php://input'), true) ?? [];
    $date     = trim($body['date'] ?? '');
    $password = $body['password'] ?? '';
    if (!$date) {
        http_response_code(400); echo json_encode(['error' => 'date obrigatório']); exit;
    }
    // Confere a senha ANTES de travar a linha — mesma checagem de verify_password.
    $stmt = $pdo->prepare('SELECT password FROM users WHERE id = ?');
    $stmt->execute([$session['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$user || !password_verify($password, $user['password'])) {
        http_response_code(401); echo json_encode(['error' => 'Senha incorreta']); exit;
    }
    $d = _liveEventLock($pdo);
    $d['attendances']   = array_values(array_filter($d['attendances']   ?? [], fn($a) => ($a['date'] ?? null) !== $date));
    $d['teamHistory']   = array_values(array_filter($d['teamHistory']   ?? [], fn($t) => ($t['date'] ?? null) !== $date));
    $d['results']       = array_values(array_filter($d['results']       ?? [], fn($r) => ($r['date'] ?? null) !== $date));
    $d['dinnerHistory'] = array_values(array_filter($d['dinnerHistory'] ?? [], fn($h) => ($h['date'] ?? null) !== $date));
    $d['lockedRodadas'] = array_values(array_filter($d['lockedRodadas'] ?? [], fn($x) => $x !== $date));
    _liveEventSave($pdo, $d);
    echo json_encode(['ok' => true]);
    exit;
}

// ── Actions somente para admin ───────────────────────────
if (in_array($action, ['list_users', 'create_user', 'delete_user', 'update_user_role'], true)) {
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

    if ($action === 'update_user_role') {
        $body = json_decode(file_get_contents('php://input'), true) ?? [];
        $id   = (int) ($body['id'] ?? 0);
        $role = $body['role'] ?? '';

        if (!$id || !in_array($role, ['admin', 'viewer'], true)) {
            http_response_code(400);
            echo json_encode(['error' => 'ID ou role inválido']);
            exit;
        }
        if ($id === (int) $session['user_id']) {
            http_response_code(400);
            echo json_encode(['error' => 'Você não pode alterar sua própria role']);
            exit;
        }

        $pdo->prepare('UPDATE users SET role = ? WHERE id = ?')->execute([$role, $id]);
        echo json_encode(['ok' => true]);
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

// ── WHATSAPP: enviar confirmados ao grupo agora (admin) ──
if ($action === 'send_confirmados') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/whatsapp.php';
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $date = $body['date'] ?? '';
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    // Texto vindo do app (idêntico ao que o admin vê) tem prioridade; senão monta no servidor.
    $fromApp = isset($body['text']) && trim($body['text']) !== '';
    $text = $fromApp ? $body['text'] : wa_build_confirmados_msg($pdo, $date);
    // O texto do servidor já inclui o link; o do app não — anexa o rodapé com o link.
    if ($fromApp) {
        $linkLines = wa_confirm_link_lines($pdo, $date);
        if ($linkLines) $text .= "\n" . implode("\n", $linkLines);
    }
    $send = wa_send_group_text($text);
    echo json_encode(['ok' => !empty($send['ok']), 'code' => $send['code'] ?? null, 'err' => $send['err'] ?? null, 'preview' => $text]);
    exit;
}

// ── WHATSAPP: listar grupos da instância (admin) — p/ achar o JID ──
if ($action === 'wa_list_groups') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/whatsapp.php';
    echo json_encode(wa_list_groups());
    exit;
}

// ── WHATSAPP: registrar o webhook na Evolution (admin) ──
// Evita ter que configurar na mão — igual wa_list_groups facilita achar o JID.
if ($action === 'wa_set_webhook') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/whatsapp.php';
    if (!wa_cfg('SITE_URL') || !wa_cfg('WA_WEBHOOK_KEY')) {
        http_response_code(400);
        echo json_encode(['error' => 'Configure SITE_URL e WA_WEBHOOK_KEY no config.php do servidor']);
        exit;
    }
    $hook = rtrim(wa_cfg('SITE_URL'), '/') . '/api/wa_webhook.php?key=' . rawurlencode(wa_cfg('WA_WEBHOOK_KEY'));
    echo json_encode(wa_set_webhook($hook));
    exit;
}

// ── IA: propostas de confirmação lidas do grupo (admin) ──
if ($action === 'ai_proposals') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    $date = $_GET['date'] ?? '';
    if (!$date || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
        http_response_code(400); echo json_encode(['error' => 'Data inválida']); exit;
    }
    // A tabela pode não existir ainda (auth_setup.php não rodado) — não quebra o app.
    try {
        $stmt = $pdo->prepare(
            "SELECT p.id, p.rodada_date, p.model, p.items, p.unmatched, p.created_at, i.sender_name
             FROM ai_confirm_proposals p
             LEFT JOIN wa_inbox i ON i.id = p.inbox_id
             WHERE p.status = 'pending' AND p.rodada_date = ?
             ORDER BY p.id DESC LIMIT 1"
        );
        $stmt->execute([$date]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        echo json_encode(['proposal' => null]);
        exit;
    }
    if (!$row) { echo json_encode(['proposal' => null]); exit; }
    echo json_encode(['proposal' => [
        'id'          => (int)$row['id'],
        'rodada_date' => $row['rodada_date'],
        'model'       => $row['model'],
        'sender_name' => $row['sender_name'],
        'created_at'  => $row['created_at'],
        'items'       => json_decode($row['items'], true) ?: [],
        'unmatched'   => json_decode($row['unmatched'] ?? '[]', true) ?: [],
    ]]);
    exit;
}

// ── IA: aprovar proposta (admin) ──
if ($action === 'ai_approve' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    require_once __DIR__ . '/ai_parse.php';
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $id   = (int)($body['id'] ?? 0);
    if (!$id) { http_response_code(400); echo json_encode(['error' => 'Proposta inválida']); exit; }
    // items = subconjunto de player_id que o admin deixou marcado (ausente = todos)
    $only = isset($body['items']) && is_array($body['items']) ? $body['items'] : null;
    $res  = ai_apply_proposal($pdo, $id, $session['username'] ?? '', $only);
    if (empty($res['ok'])) { http_response_code(400); echo json_encode(['error' => $res['err'] ?? 'Falha ao aplicar']); exit; }
    echo json_encode($res);
    exit;
}

// ── IA: descartar proposta (admin) ──
if ($action === 'ai_reject' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($session['role'] !== 'admin') { http_response_code(403); echo json_encode(['error' => 'Acesso negado']); exit; }
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    $id   = (int)($body['id'] ?? 0);
    if (!$id) { http_response_code(400); echo json_encode(['error' => 'Proposta inválida']); exit; }
    $pdo->prepare("UPDATE ai_confirm_proposals SET status = 'rejected', decided_at = NOW(), decided_by = ?
                   WHERE id = ? AND status = 'pending'")
        ->execute([$session['username'] ?? '', $id]);
    echo json_encode(['ok' => true]);
    exit;
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
    $newData = json_decode($body, true);
    if (!$body || !is_array($newData)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid JSON']);
        exit;
    }
    // Preserve liveState — it is managed separately via live_update and must not be wiped on every save
    $existing = $pdo->query('SELECT data FROM app_data WHERE id = 1')->fetch(PDO::FETCH_ASSOC);
    if ($existing) {
        $existingData = json_decode($existing['data'], true) ?? [];
        if (array_key_exists('liveState', $existingData)) {
            $newData['liveState'] = $existingData['liveState'];
            $body = json_encode($newData, JSON_UNESCAPED_UNICODE);
        }
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
