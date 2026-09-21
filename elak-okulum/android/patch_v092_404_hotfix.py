from pathlib import Path

ROOT = Path(__file__).resolve().parent
login = ROOT / "app/src/main/java/com/elak/okulum/LoginActivity.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/auth/CentralApi.kt"

s = login.read_text(encoding="utf-8")
s = s.replace('schoolCode.setText(session.schoolCode.ifBlank { "MCOAIHL" })', 'schoolCode.setText(session.schoolCode.ifBlank { "759975" })')
s = s.replace('schoolCode = field("Okul kodu (örn. MCOAIHL)")', 'schoolCode = field("Okul kodu (örn. 759975)")')
s = s.replace('authenticate(session.schoolCode.ifBlank { "MCOAIHL" }, session.username, session.password, true)', 'authenticate(session.schoolCode.ifBlank { "759975" }, session.username, session.password, true)')
s = s.replace('val legacyMco = code.equals("MCOAIHL", ignoreCase = true)', 'val legacyMco = code.equals("MCOAIHL", ignoreCase = true) || code == "759975"')
s = s.replace('val message = centralError?.takeIf { it.isNotBlank() }\n                    ?: listOfNotNull(rehberError, izinError).firstOrNull { it.isNotBlank() }', 'val message = if (legacyMco) {\n                    listOfNotNull(rehberError, izinError).firstOrNull { it.isNotBlank() }\n                        ?: centralError?.takeIf { it.isNotBlank() }\n                } else {\n                    centralError?.takeIf { it.isNotBlank() }\n                }')
login.write_text(s, encoding="utf-8")

s = api.read_text(encoding="utf-8")
old = '''        val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) { JSONObject().put("error", text) }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            throw IllegalStateException(json.optString("error", "Merkezi ELAK servisine bağlanılamadı."))
        }
        return json'''
new = '''        val looksHtml = text.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
            text.trimStart().startsWith("<html", ignoreCase = true)
        if (looksHtml) {
            val message = if (code == 404) {
                "Merkezi ELAK servisi bu sunucuda henüz etkin değil."
            } else {
                "Merkezi ELAK servisine bağlanılamadı (HTTP $code)."
            }
            throw IllegalStateException(message)
        }
        val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            val message = json.optString("error").takeIf { it.isNotBlank() }
                ?: "Merkezi ELAK servisine bağlanılamadı (HTTP $code)."
            throw IllegalStateException(message)
        }
        return json'''
if old not in s:
    raise SystemExit("CentralApi request block not found")
s = s.replace(old, new)
api.write_text(s, encoding="utf-8")
print("0.9.2 404/759975 hotfix applied")
