from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt"
incoming = ROOT / "app/src/main/java/com/elak/okulum/rehber/IncomingCallerActivity.kt"
main = ROOT / "app/src/main/java/com/elak/okulum/MainActivity.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberApi.kt"

# Replace letter/text placeholders with the recognizable WhatsApp mark.
a = activity.read_text(encoding="utf-8")
a = a.replace('squareAction("W", green)', 'whatsappAction')
a = a.replace('squareAction("◉", green)', 'whatsappAction')
a = a.replace('squareAction("W",green)', 'whatsappAction')
a = a.replace('squareAction("◉",green)', 'whatsappAction')

if "private fun whatsappAction" not in a:
    marker = "    private fun squareAction"
    idx = a.find(marker)
    if idx < 0:
        raise SystemExit("squareAction helper not found")
    helper = '''    private fun whatsappAction(action: () -> Unit): View = FrameLayout(this).apply {
        background = rounded(Color.rgb(37, 211, 102), dp(11).toFloat())
        setOnClickListener { action() }
        contentDescription = "WhatsApp"
        addView(ImageView(this@RehberActivity84).apply {
            setImageResource(R.drawable.ic_whatsapp)
            contentDescription = "WhatsApp"
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }, FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(5) }
    }

'''
    a = a[:idx] + helper + a[idx:]

# If a class WhatsApp button still has a W placeholder, keep the label but add the brand icon.
a = a.replace(
    'text = "W Grup"',
    'text = "Grup"\n            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_whatsapp, 0, 0, 0)\n            compoundDrawablePadding = dp(5)'
)
a = a.replace(
    'text="W Grup"',
    'text="Grup"; setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_whatsapp,0,0,0); compoundDrawablePadding=dp(5)'
)
a = re.sub(r'setting\(body,\s*"Sürüm",\s*"v0\.8\.\d+"\)', 'setting(body,"Sürüm","v0.8.6")', a)
activity.write_text(a, encoding="utf-8")

# Put the WhatsApp icon beside the full-width incoming-call action as well.
i = incoming.read_text(encoding="utf-8")
old = '            text = "WhatsApp\'tan Yaz"\n            textSize = 14f'
new = '            text = "WhatsApp\'tan Yaz"\n            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_whatsapp, 0, 0, 0)\n            compoundDrawablePadding = dp(8)\n            textSize = 14f'
if old in i:
    i = i.replace(old, new, 1)
else:
    i = i.replace(
        'text = "WhatsApp\'tan Yaz"',
        'text = "WhatsApp\'tan Yaz"\n            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_whatsapp, 0, 0, 0)\n            compoundDrawablePadding = dp(8)',
        1
    )
incoming.write_text(i, encoding="utf-8")

# Keep network user-agents aligned with the app version.
m = main.read_text(encoding="utf-8")
m = re.sub(r'ELAK-Okulum/0\.8\.\d+ tr-TR', 'ELAK-Okulum/0.8.6 tr-TR', m)
main.write_text(m, encoding="utf-8")

r = api.read_text(encoding="utf-8")
r = re.sub(r'ELAK-Okulum/0\.8\.\d+ Android', 'ELAK-Okulum/0.8.6 Android', r)
api.write_text(r, encoding="utf-8")

print("v0.8.6 WhatsApp icon patch applied")
