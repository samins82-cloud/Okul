from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
overlay = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerOverlay.kt'
notifier = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCardNotifier.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/IncomingCallerActivity.kt'
rehber = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# CallerOverlay.show() previously returned true immediately after handler.post(),
# even when WindowManager.addView later failed. That suppressed the full-screen
# fallback and left the user with no automatic caller card.
s = overlay.read_text(encoding='utf-8')
if 'import java.util.concurrent.CountDownLatch' not in s:
    s = s.replace('import android.widget.TextView\n', 'import android.widget.TextView\nimport java.util.concurrent.CountDownLatch\nimport java.util.concurrent.TimeUnit\nimport java.util.concurrent.atomic.AtomicBoolean\n', 1)

pat = re.compile(r'''    fun show\(context: Context, match: CallerCache\.Match, phone: String\): Boolean \{.*?\n    \}\n\n    fun dismiss''', re.S)
new = '''    fun show(context: Context, match: CallerCache.Match, phone: String): Boolean {
        if (!canDraw(context)) return false
        val app = context.applicationContext

        // If already on main, add the overlay synchronously and return the real result.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showOnMain(app, match, phone)
        }

        // CallScreeningService can call us from a binder thread. Wait only briefly so
        // CallerCardNotifier knows whether the card was actually attached to WindowManager.
        val result = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        return try {
            handler.post {
                try { result.set(showOnMain(app, match, phone)) }
                finally { latch.countDown() }
            }
            latch.await(550L, TimeUnit.MILLISECONDS) && result.get()
        } catch (_: Exception) {
            false
        }
    }

    private fun showOnMain(app: Context, match: CallerCache.Match, phone: String): Boolean {
        return try {
            dismiss(app)
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = buildView(app, match, phone)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(app, 54)
            }
            wm.addView(view, params)
            windowManager = wm
            currentView = view
            val myGeneration = ++generation
            handler.postDelayed({ if (myGeneration == generation) dismiss(app) }, 45_000L)
            true
        } catch (_: Exception) {
            currentView = null
            windowManager = null
            false
        }
    }

    fun dismiss'''
s, n = pat.subn(new, s, count=1)
if n != 1:
    raise SystemExit('CallerOverlay show marker missing')
overlay.write_text(s, encoding='utf-8')

# Always arm the full-screen call PendingIntent when Android allows it. The overlay
# is still shown as a parallel fallback; IncomingCallerActivity dismisses it when
# the full-screen route actually opens.
s = notifier.read_text(encoding='utf-8')
old = '''        val canFullScreen = if (Build.VERSION.SDK_INT >= 34) {
            try { manager.canUseFullScreenIntent() } catch (_: Exception) { false }
        } else true
        if (!overlayShown && canFullScreen) builder.setFullScreenIntent(pending, true)

        try { manager.notify(NOTIFICATION_ID, builder.build()) } catch (_: Exception) { }

        if (!overlayShown && Build.VERSION.SDK_INT < 34) {
            try { context.startActivity(intent) } catch (_: Exception) { }
        }
'''
new = '''        val canFullScreen = if (Build.VERSION.SDK_INT >= 34) {
            try { manager.canUseFullScreenIntent() } catch (_: Exception) { false }
        } else true
        if (canFullScreen) builder.setFullScreenIntent(pending, true)

        try { manager.notify(NOTIFICATION_ID, builder.build()) } catch (_: Exception) { }

        // If neither Android's full-screen route nor overlay permission is available,
        // the high-priority call notification remains as the final visible fallback.
        if (!overlayShown && !canFullScreen && Build.VERSION.SDK_INT < 34) {
            try { context.startActivity(intent) } catch (_: Exception) { }
        }
'''
if old not in s:
    raise SystemExit('CallerCardNotifier fallback marker missing')
s = s.replace(old, new, 1)
notifier.write_text(s, encoding='utf-8')

# When the full-screen activity wins the race, remove the overlay so there is only
# one caller card on screen.
s = activity.read_text(encoding='utf-8')
old = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
'''
new = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallerOverlay.dismiss(this)
'''
if old not in s:
    raise SystemExit('IncomingCallerActivity onCreate marker missing')
s = s.replace(old, new, 1)
old = '''    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }
'''
new = '''    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CallerOverlay.dismiss(this)
        setIntent(intent)
        render()
    }
'''
if old not in s:
    raise SystemExit('IncomingCallerActivity onNewIntent marker missing')
s = s.replace(old, new, 1)
activity.write_text(s, encoding='utf-8')

# Visible version label in settings.
r = rehber.read_text(encoding='utf-8')
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.11 · Otomatik Arayan Kimliği")', r, count=1)
rehber.write_text(r, encoding='utf-8')

print('v0.9.11 automatic incoming caller full-screen + verified overlay fallback applied')
