from pathlib import Path

main_path = Path('app/src/main/java/tr/mcoaihl/elakcaller/MainActivity.kt')
main_text = main_path.read_text(encoding='utf-8')
main_replacements = {
    'v1.2.0 · Android 10+': 'v1.2.3 · Android 10+',
    '.put("app_version", "1.2.0")': '.put("app_version", "1.2.3")',
    'ELAK-Caller/1.2.0': 'ELAK-Caller/1.2.3',
}
for old, new in main_replacements.items():
    if old not in main_text:
        raise SystemExit(f'MainActivity beklenen metin bulunamadı: {old}')
    main_text = main_text.replace(old, new)
main_path.write_text(main_text, encoding='utf-8')

caller_path = Path('app/src/main/java/tr/mcoaihl/elakcaller/CallerIdServiceV121.kt')
caller_text = caller_path.read_text(encoding='utf-8')
if 'ELAK-Caller/1.2.1' not in caller_text:
    raise SystemExit('CallerIdServiceV121 sürüm metni bulunamadı.')
caller_text = caller_text.replace('ELAK-Caller/1.2.1', 'ELAK-Caller/1.2.3')
caller_path.write_text(caller_text, encoding='utf-8')

print('ELAK Arayan Tanıma v1.2.3 sürüm metinleri güncellendi.')
