from pathlib import Path

p = Path(__file__).resolve().parent / "app/src/main/java/com/elak/okulum/izin/IzinActivity.kt"
s = p.read_text(encoding="utf-8")
s = s.replace('error?.description.orEmpty()', 'error?.description?.toString().orEmpty()')
s = s.replace('        webView.webViewClient = null\n', '')
p.write_text(s, encoding="utf-8")
print("v0.8.8 Izin Kotlin compatibility patch applied")
