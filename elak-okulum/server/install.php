<?php

declare(strict_types=1);

session_name('elak_okulum_installer');
if (session_status() !== PHP_SESSION_ACTIVE) {
    session_start();
}

$appRoot = is_dir(__DIR__ . '/_app') ? (__DIR__ . '/_app') : dirname(__DIR__);
$schemaFile = $appRoot . '/database/schema.sql';
$envFile = $appRoot . '/.env';
$storageDir = $appRoot . '/storage';
$lockFile = $storageDir . '/installed.lock';

function h(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

function installer_base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    $scheme = $https ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $script = $_SERVER['SCRIPT_NAME'] ?? '/install.php';
    $dir = rtrim(str_replace('\\', '/', dirname($script)), '/.');
    return $scheme . '://' . $host . ($dir === '' ? '' : $dir);
}

function sql_statements(string $sql): array
{
    $sql = preg_replace('/^\s*CREATE\s+DATABASE.*?;\s*/mi', '', $sql) ?? $sql;
    $sql = preg_replace('/^\s*USE\s+[^;]+;\s*/mi', '', $sql) ?? $sql;
    $sql = preg_replace('/\bCREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)/i', 'CREATE TABLE IF NOT EXISTS ', $sql) ?? $sql;
    $sql = preg_replace('/\bINSERT\s+INTO\s+modules\b/i', 'INSERT IGNORE INTO modules', $sql) ?? $sql;

    $statements = [];
    $buffer = '';
    $quote = null;
    $escape = false;
    $len = strlen($sql);
    for ($i = 0; $i < $len; $i++) {
        $ch = $sql[$i];
        $buffer .= $ch;
        if ($escape) { $escape = false; continue; }
        if ($ch === '\\' && $quote !== null) { $escape = true; continue; }
        if ($ch === "'" || $ch === '"' || $ch === '`') {
            if ($quote === null) $quote = $ch;
            elseif ($quote === $ch) $quote = null;
            continue;
        }
        if ($ch === ';' && $quote === null) {
            $stmt = trim(substr($buffer, 0, -1));
            if ($stmt !== '') $statements[] = $stmt;
            $buffer = '';
        }
    }
    if (trim($buffer) !== '') $statements[] = trim($buffer);
    return $statements;
}

$checks = [
    'php' => version_compare(PHP_VERSION, '8.1.0', '>='),
    'pdo' => extension_loaded('pdo_mysql'),
    'schema' => is_file($schemaFile) && is_readable($schemaFile),
    'app_write' => is_dir($appRoot) && is_writable($appRoot),
];
$ready = !in_array(false, $checks, true);
$installed = is_file($lockFile);
$error = '';
$success = false;

if (empty($_SESSION['installer_csrf'])) {
    $_SESSION['installer_csrf'] = bin2hex(random_bytes(24));
}

$defaults = [
    'db_host' => 'localhost',
    'db_port' => '3306',
    'db_name' => 'elak_okulum',
    'db_user' => '',
    'app_url' => 'https://elak.mcoaihl.com/okulum',
    'admin_name' => '',
    'admin_username' => 'admin',
];

if (!$installed && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $csrf = (string)($_POST['csrf'] ?? '');
    if ($csrf === '' || !hash_equals((string)$_SESSION['installer_csrf'], $csrf)) {
        $error = 'Güvenlik doğrulaması başarısız. Sayfayı yenileyip tekrar deneyin.';
    } elseif (!$ready) {
        $error = 'Sunucu gereksinimleri tamamlanmadan kurulum başlatılamaz.';
    } else {
        $dbHost = trim((string)($_POST['db_host'] ?? 'localhost'));
        $dbPort = trim((string)($_POST['db_port'] ?? '3306'));
        $dbName = trim((string)($_POST['db_name'] ?? 'elak_okulum'));
        $dbUser = trim((string)($_POST['db_user'] ?? ''));
        $dbPass = (string)($_POST['db_pass'] ?? '');
        $appUrl = rtrim(trim((string)($_POST['app_url'] ?? $defaults['app_url'])), '/');
        $adminName = trim((string)($_POST['admin_name'] ?? ''));
        $adminUsername = trim((string)($_POST['admin_username'] ?? 'admin'));
        $adminPassword = (string)($_POST['admin_password'] ?? '');
        $createDb = isset($_POST['create_db']);

        if (!preg_match('/^[A-Za-z0-9_]+$/', $dbName)) {
            $error = 'Veritabanı adı yalnızca harf, rakam ve alt çizgi içerebilir.';
        } elseif ($dbHost === '' || $dbUser === '' || $adminName === '' || $adminUsername === '') {
            $error = 'Veritabanı ve yönetici alanlarını eksiksiz doldurun.';
        } elseif (!ctype_digit($dbPort) || (int)$dbPort < 1 || (int)$dbPort > 65535) {
            $error = 'Veritabanı portu geçersiz.';
        } elseif (strlen($adminPassword) < 8) {
            $error = 'Yönetici şifresi en az 8 karakter olmalıdır.';
        } else {
            try {
                $serverDsn = "mysql:host={$dbHost};port={$dbPort};charset=utf8mb4";
                $server = new PDO($serverDsn, $dbUser, $dbPass, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES => false,
                ]);

                if ($createDb) {
                    try {
                        $server->exec("CREATE DATABASE IF NOT EXISTS `{$dbName}` CHARACTER SET utf8mb4 COLLATE utf8mb4_turkish_ci");
                    } catch (Throwable $createError) {
                    }
                }

                $dsn = "mysql:host={$dbHost};port={$dbPort};dbname={$dbName};charset=utf8mb4";
                $pdo = new PDO($dsn, $dbUser, $dbPass, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES => false,
                ]);

                $schema = file_get_contents($schemaFile);
                if ($schema === false) throw new RuntimeException('schema.sql okunamadı.');
                foreach (sql_statements($schema) as $statement) $pdo->exec($statement);

                $stmt = $pdo->prepare('SELECT COUNT(*) FROM system_admins WHERE username=?');
                $stmt->execute([$adminUsername]);
                if ((int)$stmt->fetchColumn() === 0) {
                    $stmt = $pdo->prepare('INSERT INTO system_admins(name,username,password_hash) VALUES(?,?,?)');
                    $stmt->execute([$adminName, $adminUsername, password_hash($adminPassword, PASSWORD_DEFAULT)]);
                }

                $env = "DB_HOST={$dbHost}\n"
                     . "DB_PORT={$dbPort}\n"
                     . "DB_NAME={$dbName}\n"
                     . "DB_USER={$dbUser}\n"
                     . "DB_PASS=\"" . addcslashes($dbPass, "\\\"") . "\"\n"
                     . "APP_URL={$appUrl}\n";
                if (file_put_contents($envFile, $env, LOCK_EX) === false) {
                    throw new RuntimeException('.env dosyası oluşturulamadı. Uygulama klasörünün yazma iznini kontrol edin.');
                }

                if (!is_dir($storageDir) && !mkdir($storageDir, 0755, true) && !is_dir($storageDir)) {
                    throw new RuntimeException('storage klasörü oluşturulamadı.');
                }
                $uploadsDir = __DIR__ . '/uploads/logos';
                if (!is_dir($uploadsDir)) @mkdir($uploadsDir, 0755, true);

                if (file_put_contents($lockFile, 'ELAK Okulum installed at ' . date(DATE_ATOM) . "\n", LOCK_EX) === false) {
                    throw new RuntimeException('Kurulum kilit dosyası oluşturulamadı.');
                }
                $success = true;
                $installed = true;
            } catch (Throwable $e) {
                $message = $e->getMessage();
                if (str_contains(strtolower($message), 'unknown database')) {
                    $message = 'Veritabanı bulunamadı. Hosting panelinizden veritabanını oluşturun veya veritabanını oluşturmaya çalış seçeneğini işaretleyin.';
                } elseif (str_contains(strtolower($message), 'access denied')) {
                    $message = 'Veritabanı kullanıcı adı/şifre veya yetkileri hatalı. Hosting panelindeki MySQL bilgilerini kontrol edin.';
                }
                $error = 'Kurulum tamamlanamadı: ' . $message;
            }
        }
    }
}

?><!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ELAK Okulum Kurulum</title>
<style>
:root{--nav:#0f2743;--primary:#183b66;--bg:#f3f6fa;--text:#182236;--muted:#64748b;--line:#d9e1eb;--ok:#13764d;--bad:#a72d2d}*{box-sizing:border-box}body{margin:0;background:var(--bg);font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial;color:var(--text)}.wrap{max-width:760px;margin:4vh auto;padding:18px}.head{background:linear-gradient(135deg,#0f2743,#183b66);color:#fff;padding:24px 26px;border-radius:20px 20px 0 0}.head h1{margin:0;font-size:26px}.head p{margin:6px 0 0;opacity:.8}.card{background:#fff;padding:24px 26px;border-radius:0 0 20px 20px;box-shadow:0 14px 40px rgba(20,39,64,.1)}.checks{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:14px 0 20px}.check{padding:10px 12px;border:1px solid var(--line);border-radius:10px;font-size:13px}.yes{color:var(--ok)}.no{color:var(--bad)}h2{font-size:17px;margin:22px 0 8px;border-bottom:1px solid var(--line);padding-bottom:8px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:13px}.full{grid-column:1/-1}label{display:block;font-size:12px;font-weight:750;margin:0 0 5px}input{width:100%;padding:11px;border:1px solid #cfd9e5;border-radius:10px;font:inherit}.tick{display:flex;gap:8px;align-items:center;font-size:13px;color:var(--muted)}.tick input{width:auto}.btn{width:100%;border:0;border-radius:11px;background:var(--primary);color:#fff;padding:13px;font-size:14px;font-weight:800;margin-top:18px;cursor:pointer}.msg{padding:12px;border-radius:10px;margin-bottom:14px;font-size:13px}.err{background:#fdeaea;color:#922b2b}.ok{background:#e8f7ef;color:#116341}.links{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:15px}.links a{text-align:center;text-decoration:none;background:#eef3f8;color:#183b66;padding:11px;border-radius:10px;font-weight:750}.note{font-size:12px;color:var(--muted);line-height:1.55;margin-top:15px}@media(max-width:650px){.grid,.checks,.links{grid-template-columns:1fr}.full{grid-column:auto}.wrap{padding:10px;margin:1vh auto}.head,.card{padding:20px}}
</style></head><body><div class="wrap"><div class="head"><h1>ELAK Okulum</h1><p>Otomatik sunucu kurulumu · <?=h(installer_base_url())?></p></div><div class="card">
<div class="checks"><div class="check <?= $checks['php']?'yes':'no' ?>"><?= $checks['php']?'✓':'✕' ?> PHP 8.1+ <b><?=h(PHP_VERSION)?></b></div><div class="check <?= $checks['pdo']?'yes':'no' ?>"><?= $checks['pdo']?'✓':'✕' ?> PDO MySQL</div><div class="check <?= $checks['schema']?'yes':'no' ?>"><?= $checks['schema']?'✓':'✕' ?> schema.sql</div><div class="check <?= $checks['app_write']?'yes':'no' ?>"><?= $checks['app_write']?'✓':'✕' ?> Uygulama klasörü yazılabilir</div></div>
<?php if($error): ?><div class="msg err"><?=h($error)?></div><?php endif; ?>
<?php if($success): ?><div class="msg ok"><b>Kurulum tamamlandı.</b> Veritabanı, .env ve merkezi yönetici hesabı hazır.</div><?php endif; ?>
<?php if($installed): ?><div class="msg ok">ELAK Okulum bu sunucuda kurulu görünüyor. Güvenlik için <b>install.php</b> dosyasını sunucudan silmeniz önerilir.</div><div class="links"><a href="./">Okul Girişi</a><a href="admin/login.php">Yönetim Paneli</a></div>
<?php else: ?><form method="post" autocomplete="off"><input type="hidden" name="csrf" value="<?=h((string)$_SESSION['installer_csrf'])?>"><h2>Veritabanı</h2><div class="grid"><div><label>DB Host</label><input name="db_host" value="<?=h((string)($_POST['db_host']??$defaults['db_host']))?>" required></div><div><label>DB Port</label><input name="db_port" value="<?=h((string)($_POST['db_port']??$defaults['db_port']))?>" inputmode="numeric" required></div><div><label>DB Adı</label><input name="db_name" value="<?=h((string)($_POST['db_name']??$defaults['db_name']))?>" required></div><div><label>DB Kullanıcı</label><input name="db_user" value="<?=h((string)($_POST['db_user']??$defaults['db_user']))?>" required></div><div class="full"><label>DB Şifre</label><input name="db_pass" type="password" required></div><div class="full tick"><input id="create_db" type="checkbox" name="create_db"><label for="create_db" style="margin:0">Veritabanı yoksa oluşturmaya çalış (hosting izin veriyorsa)</label></div></div><h2>Uygulama</h2><div class="grid"><div class="full"><label>Uygulama Adresi</label><input name="app_url" value="<?=h((string)($_POST['app_url']??$defaults['app_url']))?>" required></div></div><h2>Merkezi Yönetici</h2><div class="grid"><div><label>Ad Soyad</label><input name="admin_name" value="<?=h((string)($_POST['admin_name']??$defaults['admin_name']))?>" required></div><div><label>Kullanıcı Adı</label><input name="admin_username" value="<?=h((string)($_POST['admin_username']??$defaults['admin_username']))?>" required></div><div class="full"><label>Şifre (en az 8 karakter)</label><input name="admin_password" type="password" minlength="8" required></div></div><button class="btn" <?=!$ready?'disabled':''?>>Kurulumu Başlat</button></form><div class="note">Kurulum bittiğinde <b>install.php</b> dosyasını silin. Veritabanı şifresi yalnızca sunucudaki <b>.env</b> dosyasına yazılır.</div><?php endif; ?>
</div></div></body></html>
