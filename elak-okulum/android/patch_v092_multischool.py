from pathlib import Path

ROOT = Path(__file__).resolve().parent
p = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
s = p.read_text(encoding="utf-8")

s = s.replace(
    '    private var displayName = ""\n    private var currentTab = "home"',
    '    private var displayName = ""\n    private var schoolName = ""\n    private var schoolCode = ""\n    private var currentTab = "home"'
)

s = s.replace(
    '        displayName = account.displayName.ifBlank { account.username }\n\n        buildShell()',
    '        displayName = account.displayName.ifBlank { account.username }\n        schoolName = account.schoolName.ifBlank { "Mahmud Celaleddin Ökten Anadolu İmam Hatip Lisesi" }\n        schoolCode = account.schoolCode.ifBlank { "MCOAIHL" }\n\n        buildShell()'
)

s = s.replace(
    '            text = "Mahmud Celaleddin Ökten AİHL"',
    '            text = schoolName'
)

s = s.replace(
    '        box.addView(infoRow("Okul", "Mahmud Celaleddin Ökten AİHL"))',
    '        box.addView(infoRow("Okul", schoolName))\n        box.addView(divider())\n        box.addView(infoRow("Okul Kodu", schoolCode))'
)

p.write_text(s, encoding="utf-8")
print("0.9.2 multi-school branding patch applied")
