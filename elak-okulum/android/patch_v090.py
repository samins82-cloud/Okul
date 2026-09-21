from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
files = [
    ROOT / "app/src/main/java/com/elak/okulum/MainActivity.kt",
    ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberApi.kt",
    ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt",
    ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt",
]
for p in files:
    if not p.exists():
        continue
    s = p.read_text(encoding="utf-8")
    s = re.sub(r'ELAK-Okulum/0\.\d+\.\d+', 'ELAK-Okulum/0.9.0', s)
    s = re.sub(r'v0\.\d+\.\d+', 'v0.9.0', s)
    p.write_text(s, encoding="utf-8")
print("ELAK Okulum 0.9.0 version alignment applied")
