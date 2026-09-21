from pathlib import Path
import os
import xml.etree.ElementTree as ET

path = Path(__file__).resolve().parent / "app/src/main/res/values/fcm_config.xml"
values = {
    "google_app_id": os.getenv("ELAK_FIREBASE_APP_ID", "").strip(),
    "google_api_key": os.getenv("ELAK_FIREBASE_API_KEY", "").strip(),
    "project_id": os.getenv("ELAK_FIREBASE_PROJECT_ID", "").strip(),
    "gcm_defaultSenderId": os.getenv("ELAK_FIREBASE_SENDER_ID", "").strip(),
}
if not all(values.values()):
    print("Firebase client config not supplied; polling fallback remains active.")
    raise SystemExit(0)

tree = ET.parse(path)
root = tree.getroot()
for node in root.findall("string"):
    name = node.attrib.get("name", "")
    if name in values:
        node.text = values[name]
tree.write(path, encoding="utf-8", xml_declaration=True)
print("Firebase client resources configured for this build.")
