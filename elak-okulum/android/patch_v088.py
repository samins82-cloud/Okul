from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Legacy izin compatibility fixes.
p = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivity.kt"
if p.exists():
    s = p.read_text(encoding="utf-8")
    s = s.replace('error?.description.orEmpty()', 'error?.description?.toString().orEmpty()')
    s = s.replace('        webView.webViewClient = null\n', '')
    p.write_text(s, encoding="utf-8")

# Modern native izin screen: TextView/EditText receivers also have a `text` property.
# Qualify the activity color field so Kotlin does not resolve the CharSequence receiver property.
modern = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
if modern.exists():
    m = modern.read_text(encoding="utf-8")
    m = m.replace('setTextColor(text)', 'setTextColor(this@IzinActivityModern.text)')
    m = m.replace('if(phone.isBlank())text else blue', 'if(phone.isBlank())this@IzinActivityModern.text else blue')
    modern.write_text(m, encoding="utf-8")

print("v0.8.12 Izin Kotlin compatibility patch applied")
