<?php

declare(strict_types=1);

session_name('elak_okulum_central_admin');
session_start();

function h(string $v): string { return htmlspecialchars($v, ENT_QUOTES, 'UTF-8'); }
function root_dir(): string { return dirname(__DIR__, 2); }
function env_values(): array {
    static $v = null;
    if ($v !== null) return $v;
    $v = [];
    $f = root_dir() . '/.env';
    if (!is_file($f)) return $v;
    foreach (file($f, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) continue;
        [$k,$x] = explode('=', $line, 2);
        $x = trim($x);
        if (strlen($x) >= 2 && (($x[0] === '"' && $x[-1] === '"') || ($x[0] === "'" && $x[-1] === "'"))) $x = substr($x,1,-1);
        $v[trim($k)] = stripcslashes($x);
    }
    return $v;
}
function db(): PDO {
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $e = env_values();
    $dsn = 'mysql:host=' . ($e['DB_HOST'] ?? 'localhost') . ';port=' . ($e['DB_PORT'] ?? '3306') . ';dbname=' . ($e['DB_NAME'] ?? 'elak_okulum') . ';charset=utf8mb4';
    return $pdo = new PDO($dsn, $e['DB_USER'] ?? '', $e['DB_PASS'] ?? '', [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]);
}
function csrf(): string {
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(24));
    return (string)$_SESSION['csrf'];
}
function check_csrf(): void {
    $x = (string)($_POST['csrf'] ?? '');
    if ($x === '' || !hash_equals(csrf(), $x)) throw new RuntimeException('Güvenlik doğrulaması başarısız.');
}
function logged_in(): bool { return !empty($_SESSION['system_admin_id']); }

$pdo = db();
$error = '';
$success = '';

if (isset($_GET['logout'])) {
    $_SESSION = [];
    session_destroy();
    header('Location: ./');
    exit;
}

if (!logged_in() && $_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'login') {
    try {
        check_csrf();
        $u = trim((string)($_POST['username'] ?? ''));
        $p = (string)($_POST['password'] ?? '');
        $st = $pdo->prepare('SELECT * FROM system_admins WHERE username=? AND status=1 LIMIT 1');
        $st->execute([$u]);
        $a = $st->fetch();
        if (!$a || !password_verify($p, (string)$a['password_hash'])) throw new RuntimeException('Kullanıcı adı veya şifre hatalı.');
        session_regenerate_id(true);
        $_SESSION['system_admin_id'] = (int)$a['id'];
        $_SESSION['system_admin_name'] = (string)$a['name'];
        $pdo->prepare('UPDATE system_admins SET last_login_at=NOW() WHERE id=?')->execute([(int)$a['id']]);
        header('Location: ./');
        exit;
    } catch (Throwable $e) { $error = $e->getMessage(); }
}

if (logged_in() && $_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'create_school') {
    try {
        check_csrf();
        $code = strtoupper(trim((string)($_POST['school_code'] ?? '')));
        $name = trim((string)($_POST['school_name'] ?? ''));
        $short = trim((string)($_POST['short_name'] ?? ''));
        $primary = trim((string)($_POST['primary_color'] ?? '#183B66'));
        $secondary = trim((string)($_POST['secondary_color'] ?? '#FFFFFF'));
        $logo = trim((string)($_POST['logo_url'] ?? ''));
        $adminName = trim((string)($_POST['admin_name'] ?? ''));
        $adminUser = trim((string)($_POST['admin_username'] ?? ''));
        $adminPass = (string)($_POST['admin_password'] ?? '');
        if (!preg_match('/^[A-Z0-9_-]{3,32}$/', $code)) throw new RuntimeException('Okul kodu 3-32 karakter olmalı; A-Z, 0-9, - ve _ kullanılabilir.');
        if ($name === '' || $adminName === '' || $adminUser === '') throw new RuntimeException('Okul adı ve ilk yönetici bilgileri zorunludur.');
        if (strlen($adminPass) < 8) throw new RuntimeException('İlk yönetici şifresi en az 8 karakter olmalıdır.');
        if (!preg_match('/^#[0-9A-Fa-f]{6}$/', $primary) || !preg_match('/^#[0-9A-Fa-f]{6}$/', $secondary)) throw new RuntimeException('Renkler #RRGGBB biçiminde olmalıdır.');

        $pdo->beginTransaction();
        $st = $pdo->prepare('INSERT INTO schools(school_code,school_name,short_name,logo_url,primary_color,secondary_color,status) VALUES(?,?,?,?,?,?,1)');
        $st->execute([$code,$name,$short !== '' ? $short : null,$logo !== '' ? $logo : null,$primary,$secondary]);
        $schoolId = (int)$pdo->lastInsertId();

        $st = $pdo->prepare("INSERT INTO users(school_id,name,username,password_hash,role,school_scope,status) VALUES(?,?,?,?, 'school_admin','both',1)");
        $st->execute([$schoolId,$adminName,$adminUser,password_hash($adminPass,PASSWORD_DEFAULT)]);
        $userId = (int)$pdo->lastInsertId();
        $pdo->prepare("INSERT IGNORE INTO user_roles(user_id,role_key,is_primary) VALUES(?, 'school_admin',1)")->execute([$userId]);

        $pdo->prepare('INSERT INTO audit_logs(actor_type,actor_id,school_id,action,entity_type,entity_id,details,ip_address) VALUES(?,?,?,?,?,?,?,?)')
            ->execute(['system_admin',(int)$_SESSION['system_admin_id'],$schoolId,'school.create','school',$schoolId,json_encode(['code'=>$code,'name'=>$name],JSON_UNESCAPED_UNICODE),$_SERVER['REMOTE_ADDR'] ?? null]);
        $pdo->commit();
        $success = $code . ' kodlu okul ve ilk yönetici hesabı oluşturuldu.';
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        $error = $e->getMessage();
    }
}

$schools = [];
if (logged_in()) {
    $schools = $pdo->query("SELECT s.*, (SELECT COUNT(*) FROM users u WHERE u.school_id=s.id AND u.status=1) AS user_count FROM schools s ORDER BY s.school_name")->fetchAll();
}
?><!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ELAK Merkezi Yönetim</title><style>
:root{--n:#0b2342;--p:#2563eb;--bg:#f3f6fb;--tx:#16253d;--m:#6b7b91;--line:#dce5f0;--ok:#13764d;--bad:#a72d2d}*{box-sizing:border-box}body{margin:0;background:var(--bg);font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial;color:var(--tx)}.wrap{max-width:1050px;margin:28px auto;padding:16px}.top{background:linear-gradient(135deg,#081f43,#174c89);color:#fff;padding:22px 24px;border-radius:20px;display:flex;justify-content:space-between;align-items:center}.top h1{font-size:23px;margin:0}.top p{margin:5px 0 0;opacity:.78;font-size:13px}.top a{color:#fff;text-decoration:none;border:1px solid #ffffff55;padding:8px 11px;border-radius:10px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:16px}.card{background:#fff;border:1px solid var(--line);border-radius:17px;padding:20px;box-shadow:0 8px 25px #183b6610}h2{font-size:17px;margin:0 0 14px}.formgrid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.full{grid-column:1/-1}label{font-size:11px;font-weight:800;display:block;margin:0 0 4px;color:#53657c}input{width:100%;padding:10px 11px;border:1px solid #cad6e5;border-radius:10px;font:inherit}.btn{border:0;background:var(--p);color:#fff;padding:11px 15px;border-radius:10px;font-weight:800;cursor:pointer;width:100%}.msg{padding:11px 13px;border-radius:10px;margin-top:14px;font-size:13px}.err{background:#fdecec;color:var(--bad)}.ok{background:#e9f8ef;color:var(--ok)}table{width:100%;border-collapse:collapse;font-size:12.5px}th,td{text-align:left;padding:10px 7px;border-bottom:1px solid #edf1f6}th{color:#607089;font-size:11px}.code{font-family:ui-monospace,monospace;font-weight:800;background:#edf3ff;padding:3px 7px;border-radius:7px}.login{max-width:430px;margin:60px auto}.muted{color:var(--m);font-size:12px;line-height:1.5}@media(max-width:800px){.grid,.formgrid{grid-template-columns:1fr}.full{grid-column:auto}.wrap{margin:8px auto;padding:10px}.top{align-items:flex-start;gap:10px;flex-direction:column}}
</style></head><body><div class="wrap">
<?php if(!logged_in()): ?>
<div class="login"><div class="top"><div><h1>ELAK Merkezi Yönetim</h1><p>Çok okullu yönetim paneli</p></div></div><div class="card" style="margin-top:14px"><h2>Merkezi yönetici girişi</h2><?php if($error):?><div class="msg err"><?=h($error)?></div><?php endif;?><form method="post"><input type="hidden" name="action" value="login"><input type="hidden" name="csrf" value="<?=h(csrf())?>"><p><label>Kullanıcı adı</label><input name="username" autocomplete="username" required></p><p><label>Şifre</label><input name="password" type="password" autocomplete="current-password" required></p><button class="btn">Giriş Yap</button></form></div></div>
<?php else: ?>
<div class="top"><div><h1>ELAK Merkezi Yönetim</h1><p><?=h((string)($_SESSION['system_admin_name'] ?? 'Yönetici'))?> · <?=count($schools)?> okul</p></div><a href="?logout=1">Çıkış</a></div>
<?php if($error):?><div class="msg err"><?=h($error)?></div><?php endif;?><?php if($success):?><div class="msg ok"><?=h($success)?></div><?php endif;?>
<div class="grid"><div class="card"><h2>Yeni Okul Ekle</h2><form method="post"><input type="hidden" name="action" value="create_school"><input type="hidden" name="csrf" value="<?=h(csrf())?>"><div class="formgrid"><div><label>Okul Kodu</label><input name="school_code" placeholder="TRB001" required></div><div><label>Kısa Ad</label><input name="short_name" placeholder="Örnek AİHL"></div><div class="full"><label>Okul Adı</label><input name="school_name" required></div><div><label>Ana Renk</label><input name="primary_color" value="#183B66"></div><div><label>İkincil Renk</label><input name="secondary_color" value="#FFFFFF"></div><div class="full"><label>Logo URL (isteğe bağlı)</label><input name="logo_url" placeholder="https://..."></div><div class="full"><hr style="border:0;border-top:1px solid var(--line);margin:7px 0"></div><div><label>İlk Yönetici Ad Soyad</label><input name="admin_name" required></div><div><label>İlk Yönetici Kullanıcı Adı</label><input name="admin_username" required></div><div class="full"><label>İlk Yönetici Şifresi</label><input name="admin_password" type="password" minlength="8" required></div><div class="full"><button class="btn">Okulu ve Yöneticiyi Oluştur</button></div></div></form><p class="muted">Her okulun verisi aynı merkezi veritabanında school_id ile ayrılır. Aynı kullanıcı adı farklı okullarda kullanılabilir.</p></div>
<div class="card"><h2>Kayıtlı Okullar</h2><div style="overflow:auto"><table><thead><tr><th>Kod</th><th>Okul</th><th>Kullanıcı</th><th>Durum</th></tr></thead><tbody><?php foreach($schools as $s):?><tr><td><span class="code"><?=h((string)$s['school_code'])?></span></td><td><b><?=h((string)$s['school_name'])?></b><br><span class="muted"><?=h((string)($s['short_name'] ?? ''))?></span></td><td><?=h((string)$s['user_count'])?></td><td><?=((int)$s['status']===1?'Aktif':'Pasif')?></td></tr><?php endforeach;?><?php if(!$schools):?><tr><td colspan="4" class="muted">Henüz okul eklenmedi.</td></tr><?php endif;?></tbody></table></div></div></div>
<?php endif; ?></div></body></html>
