from pathlib import Path
import shutil
import sys

root = Path(__file__).resolve().parent
out = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "_push_package"
out.mkdir(parents=True, exist_ok=True)

src = (root / "mobile-push.php").read_text(encoding="utf-8")
old = "'notification'=>array('title'=>$row['title'],'body'=>$row['body']),'data'=>array("
new = "'data'=>array("
if old not in src:
    raise SystemExit("FCM payload anchor not found; package not created")
src = src.replace(old, new)
(out / "mobile-push.php").write_text(src, encoding="utf-8")
shutil.copy2(root / "push-admin.php", out / "push-admin.php")
shutil.copy2(root / "PUSH_0_9_3_1_KURULUM.md", out / "OKU-BENI.md")
print("ELAK push package prepared with data-only high-priority FCM payload")
