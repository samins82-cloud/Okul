<?php

declare(strict_types=1);

session_name('elak_okulum_central_admin');
session_start();
if (empty($_SESSION['system_admin_id'])) { header('Location: ./'); exit; }

function h(string $v): string { return htmlspecialchars($v, ENT_QUOTES, 'UTF-8'); }
function root_dir(): string { return dirname(__DIR__, 2); }
function env_values(): array {
    static $v = null; if ($v !== null) return $v; $v=[];
    $f=root_dir().'/.env';
    foreach (is_file($f) ? (file($f, FILE_IGNORE_NEW_LINES|FILE_SKIP_EMPTY_LINES) ?: []) : [] as $line) {
        $line=trim($line); if($line===''||str_starts_with($line,'#')||!str_contains($line,'=')) continue;
        [$k,$x]=explode('=',$line,2); $x=trim($x);
        if(strlen($x)>=2 && (($x[0]==='"'&&$x[-1]==='"')||($x[0]==="'"&&$x[-1]==="'"))) $x=substr($x,1,-1);
        $v[trim($k)]=stripcslashes($x);
    } return $v;
}
function db():PDO { static $p=null; if($p instanceof PDO)return $p; $e=env_values(); $dsn='mysql:host='.($e['DB_HOST']??'localhost').';port='.($e['DB_PORT']??'3306').';dbname='.($e['DB_NAME']??'elak_okulum').';charset=utf8mb4'; return $p=new PDO($dsn,$e['DB_USER']??'',$e['DB_PASS']??'',[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]); }
function csrf():string { if(empty($_SESSION['csrf']))$_SESSION['csrf']=bin2hex(random_bytes(24)); return (string)$_SESSION['csrf']; }
function check_csrf():void { $x=(string)($_POST['csrf']??''); if($x===''||!hash_equals(csrf(),$x))throw new RuntimeException('Güvenlik doğrulaması başarısız.'); }

$pdo=db(); $error=''; $success='';
$schools=$pdo->query('SELECT id,school_code,school_name FROM schools WHERE status=1 ORDER BY school_name')->fetchAll();
$schoolId=(int)($_GET['school_id']??$_POST['school_id']??($schools[0]['id']??0));

if($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['action']??'')==='save' && $schoolId>0){
    try{
        check_csrf();
        $modules=$pdo->query('SELECT id,module_key FROM modules ORDER BY sort_order,id')->fetchAll();
        $pdo->beginTransaction();
        $st=$pdo->prepare('INSERT INTO school_modules(school_id,module_id,custom_title,custom_url,is_enabled,sort_order) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE custom_title=VALUES(custom_title),custom_url=VALUES(custom_url),is_enabled=VALUES(is_enabled),sort_order=VALUES(sort_order)');
        $n=0;
        foreach($modules as $m){
            $id=(int)$m['id']; $key=(string)$m['module_key'];
            $title=trim((string)($_POST['title'][$key]??''));
            $url=trim((string)($_POST['url'][$key]??''));
            $enabled=isset($_POST['enabled'][$key])?1:0;
            $sort=(int)($_POST['sort'][$key]??100);
            if($url!=='' && !preg_match('#^https://#i',$url)) throw new RuntimeException($key.' modül adresi HTTPS ile başlamalıdır.');
            $st->execute([$schoolId,$id,$title!==''?$title:null,$url!==''?$url:null,$enabled,$sort]); $n++;
        }
        $pdo->prepare('INSERT INTO audit_logs(actor_type,actor_id,school_id,action,entity_type,entity_id,details,ip_address) VALUES(?,?,?,?,?,?,?,?)')->execute(['system_admin',(int)$_SESSION['system_admin_id'],$schoolId,'school.modules.update','school',$schoolId,json_encode(['module_count'=>$n]),$_SERVER['REMOTE_ADDR']??null]);
        $pdo->commit(); $success='Okul modülleri güncellendi.';
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();$error=$e->getMessage();}
}

$selected=null; $rows=[];
if($schoolId>0){
    $st=$pdo->prepare('SELECT id,school_code,school_name FROM schools WHERE id=?');$st->execute([$schoolId]);$selected=$st->fetch();
    $st=$pdo->prepare('SELECT m.id,m.module_key,m.title,m.description,m.category,m.module_type,m.web_url,m.sort_order,sm.custom_title,sm.custom_url,sm.is_enabled,sm.sort_order AS school_sort FROM modules m LEFT JOIN school_modules sm ON sm.module_id=m.id AND sm.school_id=? WHERE m.status=1 ORDER BY COALESCE(sm.sort_order,m.sort_order),m.id');
    $st->execute([$schoolId]);$rows=$st->fetchAll();
}
?><!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ELAK Okul Modülleri</title><style>
:root{--n:#0b2342;--p:#2563eb;--bg:#f3f6fb;--tx:#16253d;--m:#6b7b91;--line:#dce5f0;--ok:#13764d;--bad:#a72d2d}*{box-sizing:border-box}body{margin:0;background:var(--bg);font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial;color:var(--tx)}.wrap{max-width:1150px;margin:24px auto;padding:14px}.top{background:linear-gradient(135deg,#081f43,#174c89);color:#fff;padding:20px 22px;border-radius:18px;display:flex;justify-content:space-between;gap:12px;align-items:center}.top h1{font-size:21px;margin:0}.top p{font-size:12px;opacity:.8;margin:5px 0 0}.top a{color:#fff;text-decoration:none;border:1px solid #ffffff55;padding:8px 11px;border-radius:9px}.card{background:#fff;border:1px solid var(--line);border-radius:16px;padding:18px;margin-top:14px}.schoolbar{display:flex;gap:10px;align-items:end}.schoolbar select{flex:1;padding:10px;border:1px solid #cbd7e5;border-radius:10px;background:#fff}.btn{border:0;background:var(--p);color:#fff;padding:10px 15px;border-radius:10px;font-weight:800;cursor:pointer}.msg{padding:11px 13px;border-radius:10px;margin-top:12px;font-size:13px}.err{background:#fdecec;color:var(--bad)}.ok{background:#e9f8ef;color:var(--ok)}table{width:100%;border-collapse:collapse;font-size:12px}th,td{padding:9px 7px;border-bottom:1px solid #edf1f6;vertical-align:top}th{text-align:left;color:#65768e;font-size:10.5px}.key{font-family:ui-monospace,monospace;background:#edf3ff;padding:3px 6px;border-radius:6px}.url,.title,.sort{width:100%;padding:8px;border:1px solid #cad6e5;border-radius:8px}.sort{width:72px}.desc{color:var(--m);font-size:10.5px;margin-top:3px}.save{margin-top:14px;width:100%}.note{color:var(--m);font-size:11.5px;line-height:1.5}@media(max-width:760px){.wrap{margin:5px auto;padding:8px}.top{align-items:flex-start;flex-direction:column}.schoolbar{align-items:stretch;flex-direction:column}table{min-width:860px}.tablewrap{overflow:auto}}
</style></head><body><div class="wrap"><div class="top"><div><h1>Okula Özel Modüller</h1><p><?=h($selected?(string)$selected['school_name']:'Okul seçin')?> · Merkezi çok-okullu yapı</p></div><a href="./">← Okullar</a></div>
<?php if($error):?><div class="msg err"><?=h($error)?></div><?php endif;?><?php if($success):?><div class="msg ok"><?=h($success)?></div><?php endif;?>
<div class="card"><form method="get" class="schoolbar"><div style="flex:1"><label style="font-size:11px;font-weight:800">Okul</label><select name="school_id"><?php foreach($schools as $s):?><option value="<?=h((string)$s['id'])?>" <?=((int)$s['id']===$schoolId?'selected':'')?>><?=h((string)$s['school_code'])?> · <?=h((string)$s['school_name'])?></option><?php endforeach;?></select></div><button class="btn">Okulu Aç</button></form></div>
<?php if($selected):?><div class="card"><form method="post"><input type="hidden" name="action" value="save"><input type="hidden" name="csrf" value="<?=h(csrf())?>"><input type="hidden" name="school_id" value="<?=h((string)$schoolId)?>"><div class="tablewrap"><table><thead><tr><th>Aktif</th><th>Modül</th><th>Okulda Görünecek Ad</th><th>Okula Özel HTTPS Adresi</th><th>Sıra</th></tr></thead><tbody><?php foreach($rows as $r): $enabled=$r['is_enabled']===null?1:(int)$r['is_enabled'];?><tr><td><input type="checkbox" name="enabled[<?=h((string)$r['module_key'])?>]" <?=$enabled?'checked':''?>></td><td><span class="key"><?=h((string)$r['module_key'])?></span><div><b><?=h((string)$r['title'])?></b></div><div class="desc"><?=h((string)($r['description']??''))?></div></td><td><input class="title" name="title[<?=h((string)$r['module_key'])?>]" value="<?=h((string)($r['custom_title']??''))?>" placeholder="<?=h((string)$r['title'])?>"></td><td><input class="url" name="url[<?=h((string)$r['module_key'])?>]" value="<?=h((string)($r['custom_url']??$r['web_url']??''))?>" placeholder="https://okul.../"><div class="desc"><?=h((string)$r['module_type'])?></div></td><td><input class="sort" type="number" name="sort[<?=h((string)$r['module_key'])?>]" value="<?=h((string)($r['school_sort']??$r['sort_order']))?>"></td></tr><?php endforeach;?></tbody></table></div><button class="btn save">Modül Ayarlarını Kaydet</button><p class="note">MCOAIHL için Akıllı Rehber ve İzin Takip Android içinde native açılır. Diğer okullarda her modüle o okulun kendi HTTPS adresi verilebilir; böylece hiçbir okul başka okulun web modülüne yönlenmez.</p></form></div><?php endif;?></div></body></html>
