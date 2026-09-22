from pathlib import Path
import shutil
import sys

root = Path(__file__).resolve().parent
out = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "_push_package"
out.mkdir(parents=True, exist_ok=True)

src = (root / "mobile-push.php").read_text(encoding="utf-8")

# FCM teslim raporunun Android istemcisi tarafından doğrulanabilmesi için
# top-level notification ve Android auto-notification seçeneklerini çıkar.
old = "'notification'=>array('title'=>$row['title'],'body'=>$row['body']),'data'=>array("
if old not in src:
    raise SystemExit("FCM top-level notification anchor not found; package not created")
src = src.replace(old, "'data'=>array(")

old_android = "'android'=>array('priority'=>'high','notification'=>array('channel_id'=>'elak_school_updates','sound'=>'default'))"
if old_android not in src:
    raise SystemExit("FCM Android notification anchor not found; package not created")
src = src.replace(old_android, "'android'=>array('priority'=>'high')")

# Firebase servis hesabı özel anahtarının public_html / document root altında
# kullanılmasını yazılım seviyesinde de engelle. Anahtar webden erişilebilen
# bir dizine yanlışlıkla konursa push yapılandırılmamış kabul edilir.
old_cred = "if(!is_file($path)||!is_readable($path))return null;$raw=file_get_contents($path);$j=json_decode($raw,true);"
new_cred = """$real=realpath($path);if($real===false||!is_file($real)||!is_readable($real))return null;$norm=str_replace('\\\\','/',$real);if(strpos('/'.$norm,'/public_html/')!==false)return null;$doc=isset($_SERVER['DOCUMENT_ROOT'])?realpath($_SERVER['DOCUMENT_ROOT']):false;if($doc!==false){$docNorm=rtrim(str_replace('\\\\','/',$doc),'/').'/';if(strpos($norm,$docNorm)===0)return null;}$path=$real;$raw=file_get_contents($path);$j=json_decode($raw,true);"""
if old_cred not in src:
    raise SystemExit("Firebase credential path anchor not found; package not created")
src = src.replace(old_cred, new_cred)

(out / "mobile-push.php").write_text(src, encoding="utf-8")
shutil.copy2(root / "push-admin.php", out / "push-admin.php")
shutil.copy2(root / "PUSH_0_9_3_1_KURULUM.md", out / "OKU-BENI.md")
print("ELAK push package prepared: data-only FCM + public web root credential rejection")
