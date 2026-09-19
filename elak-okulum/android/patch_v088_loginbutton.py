from pathlib import Path

p = Path(__file__).resolve().parent / "app/src/main/java/com/elak/okulum/MainActivity.kt"
s = p.read_text(encoding="utf-8")

# Portalın güncel okul giriş butonunu kullan.
s = s.replace("document.getElementById('loginBtn')", "document.getElementById('schoolLoginBtn')")

# Kişisel kullanıcı bilgilerini sadece buton tıklamasında değil form submit olayında da
# native kasaya aktar. Böylece İzin Takip modülü ikinci kez kullanıcı adı/şifre istemez.
old = """var b=document.getElementById('schoolLoginBtn'); if(b) b.addEventListener('click',send,true);
              var p=document.getElementById('schoolPass'); if(p) p.addEventListener('keydown',function(e){if(e.key==='Enter')send();},true);"""
new = """var f=document.getElementById('schoolLoginForm'); if(f) f.addEventListener('submit',send,true);
              var b=document.getElementById('schoolLoginBtn'); if(b) b.addEventListener('click',send,true);
              var p=document.getElementById('schoolPass'); if(p) p.addEventListener('keydown',function(e){if(e.key==='Enter')send();},true);"""
if old in s:
    s = s.replace(old, new, 1)
elif "document.getElementById('schoolLoginForm')" not in s:
    needle = "var b=document.getElementById('schoolLoginBtn');"
    s = s.replace(needle, "var f=document.getElementById('schoolLoginForm'); if(f) f.addEventListener('submit',send,true);\n              " + needle, 1)

p.write_text(s, encoding="utf-8")
print("v0.8.12 school login SSO/form hook applied")
