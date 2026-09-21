<?php

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');

const ACCESS_TTL = 900;          // 15 dakika
const REFRESH_TTL = 2592000;     // 30 gün

function respond(array $payload, int $status = 200): never
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function app_root(): string
{
    return dirname(__DIR__, 3);
}

function env_values(): array
{
    static $values = null;
    if ($values !== null) return $values;
    $values = [];
    $file = app_root() . '/.env';
    if (!is_file($file)) return $values;
    foreach (file($file, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) continue;
        [$key, $value] = explode('=', $line, 2);
        $value = trim($value);
        if (strlen($value) >= 2 && (($value[0] === '"' && $value[-1] === '"') || ($value[0] === "'" && $value[-1] === "'"))) {
            $value = substr($value, 1, -1);
        }
        $values[trim($key)] = stripcslashes($value);
    }
    return $values;
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $e = env_values();
    $host = $e['DB_HOST'] ?? 'localhost';
    $port = $e['DB_PORT'] ?? '3306';
    $name = $e['DB_NAME'] ?? 'elak_okulum';
    $user = $e['DB_USER'] ?? '';
    $pass = $e['DB_PASS'] ?? '';
    $dsn = "mysql:host={$host};port={$port};dbname={$name};charset=utf8mb4";
    $pdo = new PDO($dsn, $user, $pass, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    return $pdo;
}

function json_body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') return [];
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

function route(): string
{
    $r = trim((string)($_GET['r'] ?? ''), '/');
    if ($r !== '') return $r;
    $path = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH) ?: '';
    $marker = '/api/v1/';
    $pos = strpos($path, $marker);
    if ($pos !== false) return trim(substr($path, $pos + strlen($marker)), '/');
    return '';
}

function bearer(): string
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if ($header === '' && function_exists('getallheaders')) {
        $headers = getallheaders();
        $header = (string)($headers['Authorization'] ?? $headers['authorization'] ?? '');
    }
    if (preg_match('/^Bearer\s+(.+)$/i', trim($header), $m)) return trim($m[1]);
    return '';
}

function token_hash(string $token): string
{
    return hash('sha256', $token);
}

function new_token(): string
{
    return rtrim(strtr(base64_encode(random_bytes(48)), '+/', '-_'), '=');
}

function school_for_code(PDO $pdo, string $code): ?array
{
    $stmt = $pdo->prepare('SELECT id,school_code,school_name,short_name,logo_url,primary_color,secondary_color FROM schools WHERE school_code=? AND status=1 LIMIT 1');
    $stmt->execute([$code]);
    $row = $stmt->fetch();
    return $row ?: null;
}

function roles_for_user(PDO $pdo, array $user): array
{
    $roles = [];
    try {
        $stmt = $pdo->prepare('SELECT role_key,is_primary FROM user_roles WHERE user_id=? ORDER BY is_primary DESC,id ASC');
        $stmt->execute([(int)$user['id']]);
        foreach ($stmt->fetchAll() as $row) {
            $key = trim((string)$row['role_key']);
            if ($key !== '' && !in_array($key, $roles, true)) $roles[] = $key;
        }
    } catch (Throwable $e) {
    }
    $legacy = trim((string)($user['role'] ?? ''));
    if ($legacy !== '' && !in_array($legacy, $roles, true)) array_unshift($roles, $legacy);
    if (!$roles) $roles[] = 'teacher';
    return $roles;
}

function default_permissions(array $roles): array
{
    $map = [
        'school_admin' => ['*'],
        'manager' => ['module.izin_takip','module.akilli_rehber','module.online_yoklama','module.veli_randevu','module.duyurular','module.ders_programi','module.akilli_tahta','module.lgs_yks','module.anketler','module.etkinlik_gezi','module.belgeler','module.ogretmen_islemleri'],
        'teacher' => ['module.akilli_rehber','module.online_yoklama','module.veli_randevu','module.duyurular','module.ders_programi','module.akilli_tahta','module.belgeler'],
        'security' => ['module.izin_takip','module.akilli_rehber'],
        'dormitory' => ['module.izin_takip','module.akilli_rehber','module.duyurular','module.belgeler'],
        'guardian' => ['module.veli_randevu','module.duyurular','module.ders_programi','module.lgs_yks','module.anketler','module.etkinlik_gezi','module.belgeler'],
        'student' => ['module.duyurular','module.ders_programi','module.lgs_yks','module.belgeler'],
        'staff' => ['module.duyurular','module.belgeler'],
    ];
    $permissions = [];
    foreach ($roles as $role) {
        foreach ($map[$role] ?? [] as $p) $permissions[$p] = true;
    }
    return array_keys($permissions);
}

function permissions_for_user(PDO $pdo, int $userId, array $roles): array
{
    $permissions = array_fill_keys(default_permissions($roles), true);
    try {
        $stmt = $pdo->prepare('SELECT permission_key,is_allowed FROM user_permissions WHERE user_id=?');
        $stmt->execute([$userId]);
        foreach ($stmt->fetchAll() as $row) {
            $key = trim((string)$row['permission_key']);
            if ($key === '') continue;
            if ((int)$row['is_allowed'] === 1) $permissions[$key] = true;
            else unset($permissions[$key]);
        }
    } catch (Throwable $e) {
    }
    try {
        $stmt = $pdo->prepare('SELECT m.module_key,ump.can_view,ump.can_manage FROM user_module_permissions ump JOIN modules m ON m.id=ump.module_id WHERE ump.user_id=?');
        $stmt->execute([$userId]);
        foreach ($stmt->fetchAll() as $row) {
            $viewKey = 'module.' . $row['module_key'];
            if ((int)$row['can_view'] === 1) $permissions[$viewKey] = true;
            else unset($permissions[$viewKey]);
            if ((int)$row['can_manage'] === 1) $permissions[$viewKey . '.manage'] = true;
        }
    } catch (Throwable $e) {
    }
    return array_values(array_keys($permissions));
}

function links_for_user(PDO $pdo, int $userId): array
{
    try {
        $stmt = $pdo->prepare('SELECT link_type,external_id,label,school_scope,meta_json FROM user_links WHERE user_id=? ORDER BY link_type,label,external_id');
        $stmt->execute([$userId]);
        $rows = [];
        foreach ($stmt->fetchAll() as $row) {
            $meta = [];
            if (!empty($row['meta_json'])) {
                $decoded = json_decode((string)$row['meta_json'], true);
                if (is_array($decoded)) $meta = $decoded;
            }
            $rows[] = [
                'type' => (string)$row['link_type'],
                'external_id' => (string)$row['external_id'],
                'label' => (string)($row['label'] ?? ''),
                'school_scope' => (string)($row['school_scope'] ?? 'both'),
                'meta' => $meta,
            ];
        }
        return $rows;
    } catch (Throwable $e) {
        return [];
    }
}

function modules_for_user(PDO $pdo, int $schoolId, array $permissions): array
{
    $all = in_array('*', $permissions, true);
    $allowed = array_fill_keys($permissions, true);
    $sql = 'SELECT m.module_key,m.title,m.description,m.category,m.icon,m.default_color,m.module_type,m.web_url,m.sso_enabled,COALESCE(sm.custom_title,m.title) AS effective_title,COALESCE(sm.custom_color,m.default_color) AS effective_color,COALESCE(sm.custom_url,m.web_url) AS effective_url,COALESCE(sm.sort_order,m.sort_order) AS effective_sort FROM modules m LEFT JOIN school_modules sm ON sm.module_id=m.id AND sm.school_id=? WHERE m.status=1 AND COALESCE(sm.is_enabled,1)=1 ORDER BY effective_sort,m.id';
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$schoolId]);
    $rows = [];
    foreach ($stmt->fetchAll() as $row) {
        $key = (string)$row['module_key'];
        if (!$all && !isset($allowed['module.' . $key])) continue;
        $rows[] = [
            'key' => $key,
            'title' => (string)$row['effective_title'],
            'description' => (string)($row['description'] ?? ''),
            'category' => (string)($row['category'] ?? ''),
            'icon' => (string)($row['icon'] ?? ''),
            'color' => (string)($row['effective_color'] ?? ''),
            'type' => (string)$row['module_type'],
            'url' => (string)($row['effective_url'] ?? ''),
            'sso' => (int)$row['sso_enabled'] === 1,
            'can_manage' => $all || isset($allowed['module.' . $key . '.manage']),
        ];
    }
    return $rows;
}

function profile_payload(PDO $pdo, array $user): array
{
    $roles = roles_for_user($pdo, $user);
    $permissions = permissions_for_user($pdo, (int)$user['id'], $roles);
    $primary = $roles[0] ?? (string)$user['role'];
    $schoolScope = (string)($user['school_scope'] ?? 'both');
    $school = [
        'id' => (int)$user['school_id'],
        'code' => (string)$user['school_code'],
        'name' => (string)$user['school_name'],
        'short_name' => (string)($user['short_name'] ?? ''),
        'logo_url' => (string)($user['logo_url'] ?? ''),
    ];
    return [
        'id' => (int)$user['id'],
        'username' => (string)$user['username'],
        'name' => (string)$user['name'],
        'primary_role' => $primary,
        'roles' => $roles,
        'school_scope' => $schoolScope,
        'permissions' => $permissions,
        'links' => links_for_user($pdo, (int)$user['id']),
        'modules' => modules_for_user($pdo, (int)$user['school_id'], $permissions),
        'school' => $school,
    ];
}

function user_by_access_token(PDO $pdo, string $accessToken): ?array
{
    if ($accessToken === '') return null;
    $sql = 'SELECT u.*,s.school_code,s.school_name,s.short_name,s.logo_url,axs.id AS session_id FROM auth_sessions axs JOIN users u ON u.id=axs.user_id JOIN schools s ON s.id=u.school_id WHERE axs.access_token_hash=? AND axs.revoked_at IS NULL AND axs.access_expires_at>NOW() AND u.status=1 AND s.status=1 LIMIT 1';
    $stmt = $pdo->prepare($sql);
    $stmt->execute([token_hash($accessToken)]);
    $user = $stmt->fetch();
    if ($user) {
        $pdo->prepare('UPDATE auth_sessions SET last_used_at=NOW() WHERE id=?')->execute([(int)$user['session_id']]);
        return $user;
    }
    return null;
}

function issue_session(PDO $pdo, int $userId, string $device, string $version): array
{
    $access = new_token();
    $refresh = new_token();
    $stmt = $pdo->prepare('INSERT INTO auth_sessions(user_id,access_token_hash,refresh_token_hash,access_expires_at,refresh_expires_at,device_name,app_version,last_used_at) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL ? SECOND),DATE_ADD(NOW(),INTERVAL ? SECOND),?,?,NOW())');
    $stmt->execute([$userId, token_hash($access), token_hash($refresh), ACCESS_TTL, REFRESH_TTL, $device, $version]);
    return ['access_token'=>$access,'refresh_token'=>$refresh,'expires_in'=>ACCESS_TTL,'refresh_expires_in'=>REFRESH_TTL];
}

try {
    $pdo = db();
    $method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
    $route = route();

    if ($method === 'GET' && ($route === '' || $route === 'health')) {
        respond(['ok'=>true,'service'=>'ELAK Okulum API','version'=>'0.9.2','time'=>date(DATE_ATOM)]);
    }

    if ($method === 'POST' && $route === 'auth/login') {
        $body = json_body();
        $username = trim((string)($body['username'] ?? ''));
        $password = (string)($body['password'] ?? '');
        $schoolCode = strtoupper(trim((string)($body['school_code'] ?? 'MCOAIHL')));
        if ($username === '' || $password === '') respond(['ok'=>false,'error'=>'Kullanıcı adı ve şifre gerekli.'], 422);
        $school = school_for_code($pdo, $schoolCode);
        if (!$school) respond(['ok'=>false,'error'=>'Okul kaydı bulunamadı.'], 404);
        $stmt = $pdo->prepare('SELECT u.*,s.school_code,s.school_name,s.short_name,s.logo_url FROM users u JOIN schools s ON s.id=u.school_id WHERE u.school_id=? AND u.username=? AND u.status=1 LIMIT 1');
        $stmt->execute([(int)$school['id'], $username]);
        $user = $stmt->fetch();
        if (!$user || !password_verify($password, (string)$user['password_hash'])) respond(['ok'=>false,'error'=>'Kullanıcı adı veya şifre hatalı.'], 401);
        $tokens = issue_session($pdo, (int)$user['id'], trim((string)($body['device_name'] ?? 'Android')), trim((string)($body['app_version'] ?? '0.9.2')));
        respond(['ok'=>true,'data'=>array_merge($tokens,['user'=>profile_payload($pdo,$user)])]);
    }

    if ($method === 'POST' && $route === 'auth/refresh') {
        $body = json_body();
        $refresh = trim((string)($body['refresh_token'] ?? ''));
        if ($refresh === '') respond(['ok'=>false,'error'=>'Yenileme anahtarı gerekli.'], 422);
        $stmt = $pdo->prepare('SELECT axs.*,u.status FROM auth_sessions axs JOIN users u ON u.id=axs.user_id WHERE axs.refresh_token_hash=? AND axs.revoked_at IS NULL AND axs.refresh_expires_at>NOW() AND u.status=1 LIMIT 1');
        $stmt->execute([token_hash($refresh)]);
        $session = $stmt->fetch();
        if (!$session) respond(['ok'=>false,'error'=>'Oturum süresi doldu.'], 401);
        $pdo->prepare('UPDATE auth_sessions SET revoked_at=NOW() WHERE id=?')->execute([(int)$session['id']]);
        $tokens = issue_session($pdo, (int)$session['user_id'], trim((string)($body['device_name'] ?? 'Android')), trim((string)($body['app_version'] ?? '0.9.2')));
        respond(['ok'=>true,'data'=>$tokens]);
    }

    if ($method === 'GET' && ($route === 'me' || $route === 'auth/me')) {
        $user = user_by_access_token($pdo, bearer());
        if (!$user) respond(['ok'=>false,'error'=>'Yetkisiz veya süresi dolmuş oturum.'], 401);
        respond(['ok'=>true,'data'=>['user'=>profile_payload($pdo,$user)]]);
    }

    if ($method === 'POST' && $route === 'auth/logout') {
        $token = bearer();
        if ($token !== '') $pdo->prepare('UPDATE auth_sessions SET revoked_at=NOW() WHERE access_token_hash=? AND revoked_at IS NULL')->execute([token_hash($token)]);
        respond(['ok'=>true]);
    }

    respond(['ok'=>false,'error'=>'API yolu bulunamadı.','route'=>$route], 404);
} catch (Throwable $e) {
    error_log('[ELAK API] ' . $e->getMessage());
    respond(['ok'=>false,'error'=>'Sunucu işlemi tamamlanamadı.'], 500);
}
