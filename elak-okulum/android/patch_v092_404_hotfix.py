from pathlib import Path

ROOT = Path(__file__).resolve().parent
login = ROOT / "app/src/main/java/com/elak/okulum/LoginActivity.kt"

s = login.read_text(encoding="utf-8")
s = s.replace('schoolCode.setText(session.schoolCode.ifBlank { "MCOAIHL" })', 'schoolCode.setText(session.schoolCode.ifBlank { "759975" })')
s = s.replace('schoolCode = field("Okul kodu (örn. MCOAIHL)")', 'schoolCode = field("Okul kodu (örn. 759975)")')
s = s.replace('authenticate(session.schoolCode.ifBlank { "MCOAIHL" }, session.username, session.password, true)', 'authenticate(session.schoolCode.ifBlank { "759975" }, session.username, session.password, true)')
s = s.replace('val legacyMco = code.equals("MCOAIHL", ignoreCase = true)', 'val legacyMco = code.equals("MCOAIHL", ignoreCase = true) || code == "759975"')
s = s.replace(
    'val message = centralError?.takeIf { it.isNotBlank() }\n                    ?: listOfNotNull(rehberError, izinError).firstOrNull { it.isNotBlank() }',
    'val message = if (legacyMco) {\n                    centralError?.takeIf { it.isNotBlank() }\n                        ?: listOfNotNull(rehberError, izinError).firstOrNull { it.isNotBlank() }\n                } else {\n                    centralError?.takeIf { it.isNotBlank() }\n                }'
)
login.write_text(s, encoding="utf-8")
print("0.9.2 759975 login compatibility patch applied")
