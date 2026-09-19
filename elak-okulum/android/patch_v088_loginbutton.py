from pathlib import Path

p = Path(__file__).resolve().parent / "app/src/main/java/com/elak/okulum/MainActivity.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("document.getElementById('loginBtn')", "document.getElementById('schoolLoginBtn')")
p.write_text(s, encoding="utf-8")
print("v0.8.8 schoolLoginBtn SSO hook applied")
