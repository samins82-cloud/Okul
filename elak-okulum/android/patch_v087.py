from pathlib import Path

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivity.kt"

text = activity.read_text(encoding="utf-8")
old = 'handleMainFrameFailure("WebView hata kodu ${error.errorCode}: ${error.description}", request.url?.toString())'
new = 'handleMainFrameFailure("WebView hata kodu ${error?.errorCode ?: -1}: ${error?.description ?: \"Bilinmeyen hata\"}", request.url?.toString())'
if old not in text:
    raise SystemExit("Izin null-safety target not found")
text = text.replace(old, new, 1)
activity.write_text(text, encoding="utf-8")
print("v0.8.7 Izin null-safety patch applied")
