from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
overlay = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerOverlay.kt'
notifier = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCardNotifier.kt'
rehber = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# ------------------------------------------------------------------
# 1) Caller overlay: below status bar, one clean compact card
# ------------------------------------------------------------------
s = overlay.read_text(encoding='utf-8')

# v0.9.13 put the overlay at absolute y=0 with NO_LIMITS. That caused it to
# collide with the phone status bar. Keep it at the highest *usable* point.
s = s.replace(
'''                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,''',
'''                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,''',
1
)
s = s.replace('''                y = 0''',
              '''                y = statusBarHeight(app) + dp(app, 3)''', 1)

# Make the overlay visually tighter so it does not cover a large part of the
# screen while the native phone UI is also active.
s = s.replace(
'''            setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 12))''',
'''            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 9))''',
1
)
s = s.replace('''                cornerRadius = dp(context, 18).toFloat()
                setStroke(dp(context, 2), navy)''',
              '''                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), navy)''', 1)
s = s.replace('''            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)''',
              '''            textSize = 11.2f
            setTypeface(typeface, Typeface.BOLD)''', 1)
s = s.replace('''            textSize = 24f''', '''            textSize = 22f''', 1)
s = s.replace('''        }, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)))''',
              '''        }, LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)))''', 1)
s = s.replace('''            textSize = 19f''', '''            textSize = 17.2f''', 1)
s = s.replace('''            textSize = 12f
            setTextColor(muted)''',
              '''            textSize = 11.2f
            setTextColor(muted)''', 1)
s = s.replace('''            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)''',
              '''            textSize = 12.2f
            setTypeface(typeface, Typeface.BOLD)''', 1)
s = s.replace('''LinearLayout.LayoutParams(0, dp(context, 42), 1f)''',
              '''LinearLayout.LayoutParams(0, dp(context, 38), 1f)''')
s = s.replace('''            setPadding(dp(context, 10), 0, dp(context, 10), 0)''',
              '''            setPadding(dp(context, 8), 0, dp(context, 8), 0)''', 1)

# Helper for a device-safe top position.
marker = '''    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()
'''
helper = '''    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }

''' + marker
if marker not in s:
    raise SystemExit('CallerOverlay dp marker missing')
s = s.replace(marker, helper, 1)
overlay.write_text(s, encoding='utf-8')

# ------------------------------------------------------------------
# 2) Do NOT show the high-priority heads-up notification over a
#    successfully attached ELAK overlay. It was the duplicate panel
#    visible in the screenshot.
# ------------------------------------------------------------------
s = notifier.read_text(encoding='utf-8')

s = s.replace(
'''        if (canFullScreen) builder.setFullScreenIntent(pending, true)

        try { manager.notify(NOTIFICATION_ID, builder.build()) } catch (_: Exception) { }

        // If neither Android's full-screen route nor overlay permission is available,''',
'''        if (!overlayShown && canFullScreen) builder.setFullScreenIntent(pending, true)

        if (overlayShown) {
            // Overlay is already visible: remove any stale ELAK call notification.
            // This prevents the Android heads-up card from covering the ELAK card.
            try { manager.cancel(NOTIFICATION_ID) } catch (_: Exception) { }
        } else {
            try { manager.notify(NOTIFICATION_ID, builder.build()) } catch (_: Exception) { }
        }

        // If neither Android's full-screen route nor overlay permission is available,''',
1
)
notifier.write_text(s, encoding='utf-8')

# ------------------------------------------------------------------
# 3) Strong permission warning + cleaner Settings design
# ------------------------------------------------------------------
r = rehber.read_text(encoding='utf-8')

# A warning should appear once per RehberActivity session, not just once forever.
var_marker = '''    private var pendingPhotoStudent: CallerCache.Student? = null
'''
if var_marker not in r:
    raise SystemExit('Rehber variable marker missing')
r = r.replace(var_marker, var_marker + '''    private var overlayWarningShown = false
''', 1)

prompt_pat = re.compile(r'''    private fun maybePromptCallerOverlayPermission\(\) \{.*?\n    \}\n\n    private fun requestCallerOverlayPermission''', re.S)
prompt_new = '''    private fun maybePromptCallerOverlayPermission() {
        if (CallerOverlay.canDraw(this) || overlayWarningShown) return
        overlayWarningShown = true
        main.postDelayed({
            if (isFinishing || isDestroyed || CallerOverlay.canDraw(this)) return@postDelayed
            AlertDialog.Builder(this)
                .setTitle("Arayan Kimliği Ekran Kartı Kapalı")
                .setMessage("Ekran kartı izni kapalı olduğu için kayıtlı veli, öğretmen veya personel aradığında ELAK kimlik kartı ekranda otomatik açılamaz. Açmak için ‘Diğer uygulamaların üzerinde göster’ iznini etkinleştirin.")
                .setNegativeButton("SONRA", null)
                .setPositiveButton("ŞİMDİ AÇ") { _, _ -> requestCallerOverlayPermission() }
                .show()
        }, 550L)
    }

    private fun requestCallerOverlayPermission'''
r, n = prompt_pat.subn(lambda _m: prompt_new, r, count=1)
if n != 1:
    raise SystemExit('maybePromptCallerOverlayPermission marker missing')

# Add an always-visible orange warning panel at the top of Settings while the
# overlay permission is off.
settings_head = '''        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(18)) }
        setting(body, "Arayan Kimliği", if (isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
'''
settings_repl = '''        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(7), dp(12), dp(14)) }

        if (!CallerOverlay.canDraw(this)) {
            val warning = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = rounded(Color.rgb(255, 247, 237), dp(14).toFloat(), Color.rgb(251, 146, 60))
                setOnClickListener { requestCallerOverlayPermission() }
                addView(TextView(this@RehberActivity84).apply {
                    text = "⚠  Arayan Kimliği Ekran Kartı Kapalı"
                    textSize = 12.8f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(154, 52, 18))
                })
                addView(TextView(this@RehberActivity84).apply {
                    text = "Bu izin kapalıyken gelen aramada ELAK kartı otomatik açılamaz. Açmak için dokunun."
                    textSize = 9.8f
                    setTextColor(Color.rgb(154, 52, 18))
                    setPadding(0, dp(3), 0, 0)
                })
            }
            body.addView(warning, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(6)))
        }

        setting(body, "Arayan Kimliği", if (isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
'''
if settings_head not in r:
    raise SystemExit('Settings body marker missing')
r = r.replace(settings_head, settings_repl, 1)

# Make ordinary Settings rows less tall and visually denser.
r = r.replace(
'''            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.WHITE, dp(15).toFloat(), soft); setOnClickListener { action() }
            addView(TextView(this@RehberActivity84).apply { text = name; textSize = 14.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
            addView(TextView(this@RehberActivity84).apply { text = desc; textSize = 10.5f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })''',
'''            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(9), dp(12), dp(9)); background = rounded(Color.WHITE, dp(14).toFloat(), soft); setOnClickListener { action() }
            addView(TextView(this@RehberActivity84).apply { text = name; textSize = 13.3f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
            addView(TextView(this@RehberActivity84).apply { text = desc; textSize = 9.7f; setTextColor(muted); setPadding(0, dp(2), 0, 0) })''',
1
)
r = r.replace(
'''        parent.addView(v, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))''',
'''        parent.addView(v, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(3), 0, dp(3)))''',
1
)

r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)',
           'setting(body, "Sürüm", "v0.9.15 · Arayan Kartı & İzin Uyarısı")',
           r, count=1)

rehber.write_text(r, encoding='utf-8')

print('v0.9.15 caller overlay layout + duplicate notification + permission warning applied')
