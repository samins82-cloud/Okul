from pathlib import Path

path = Path('app/src/main/java/tr/mcoaihl/elakcaller/MainActivity.kt')
text = path.read_text(encoding='utf-8')

replacements = {
    'v1.2.0 · Android 10+': 'v1.2.2 · Android 10+',
    '.put("app_version", "1.2.0")': '.put("app_version", "1.2.2")',
    'ELAK-Caller/1.2.0': 'ELAK-Caller/1.2.2',
}

for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Beklenen metin bulunamadı: {old}')
    text = text.replace(old, new)

path.write_text(text, encoding='utf-8')
print('MainActivity v1.2.2 sürüm bilgileri güncellendi.')
