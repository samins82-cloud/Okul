from pathlib import Path
import base64, gzip

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
parts_dir = ROOT / "patches"

# Bu paket yalnız modern İzin Takip arayüz tabanını üretir.
# IzinApi.kt artık güncel kaynak dosyada yönetiliyor; eski 0.8.13 API dönüşümleri
# merkezi Rehber/CORE senkronizasyonunu geri almaması için burada uygulanmaz.
ACTIVITY_GZ_B64 = "".join(
    (parts_dir / ("izin13_%d.b64" % i)).read_text(encoding="utf-8").strip()
    for i in range(1, 6)
)
activity.write_bytes(gzip.decompress(base64.b64decode(ACTIVITY_GZ_B64)))

print("Izin modern UI base applied; current API preserved")
