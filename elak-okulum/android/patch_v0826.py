from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
overlay = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerOverlay.kt'
rehber = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# ------------------------------------------------------------------
# 1) Caller overlay: re-assert z-order after the system Phone/InCallUI
#    has had time to open. Some OEM call UIs are raised a few hundred
#    milliseconds after CallScreeningService returns.
# ------------------------------------------------------------------
s = overlay.read_text(encoding='utf-8')

if 'private var currentParams: WindowManager.LayoutParams? = null' not in s:
    s = s.replace(
        '    private var currentView: View? = null\n',
        '    private var currentView: View? = null\n    private var currentParams: WindowManager.LayoutParams? = null\n',
        1
    )

show_pat = re.compile(r'''    private fun showOnMain\(app: Context, match: CallerCache\.Match, phone: String\): Boolean \{.*?\n    \}\n\n    fun dismiss''', re.S)
show_new = '''    private fun showOnMain(app: Context, match: CallerCache.Match, phone: String): Boolean {
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = statusBarHeight(app) + dp(app, 3)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            wm.addView(view, params)
            windowManager = wm
            currentView = view
            currentParams = params
            val myGeneration = ++generation

            // The stock/OEM Phone activity is commonly raised just after screening.
            // Re-add the same TYPE_APPLICATION_OVERLAY window after those transitions
            // so it remains above ordinary application windows.
            handler.postDelayed({ raiseOverlay(myGeneration) }, 280L)
            handler.postDelayed({ raiseOverlay(myGeneration) }, 900L)
            handler.postDelayed({ raiseOverlay(myGeneration) }, 1800L)
            handler.postDelayed({ if (myGeneration == generation) dismiss(app) }, 45_000L)
            true
        } catch (_: Exception) {
            currentView = null
            currentParams = null
            windowManager = null
            false
        }
    }

    private fun raiseOverlay(expectedGeneration: Int) {
        if (expectedGeneration != generation) return
        val wm = windowManager ?: return
        val view = currentView ?: return
        val params = currentParams ?: return
        try {
            if (view.isAttachedToWindow) wm.removeViewImmediate(view)
            wm.addView(view, params)
        } catch (_: Exception) {
            // If an OEM blocks re-attaching, keep the already attached window when possible.
            try {
                if (!view.isAttachedToWindow) {
                    currentView = null
                    currentParams = null
                    windowManager = null
                }
            } catch (_: Exception) { }
        }
    }

    fun isShowing(): Boolean = try { currentView?.isAttachedToWindow == true } catch (_: Exception) { false }

    fun dismiss'''
s, n = show_pat.subn(lambda _m: show_new, s, count=1)
if n != 1:
    raise SystemExit('CallerOverlay showOnMain marker missing')

# Clear the params reference when the overlay is dismissed.
s = s.replace(
'''        currentView = null
        windowManager = null
    }
''',
'''        currentView = null
        currentParams = null
        windowManager = null
    }
''',
1
)

overlay.write_text(s, encoding='utf-8')

# ------------------------------------------------------------------
# 2) Rehber settings: Xiaomi/Redmi/POCO/HyperOS has additional OEM
#    permissions beyond Settings.canDrawOverlays(). Also expose Android
#    14+ full-screen-intent permission as a secondary fallback.
# ------------------------------------------------------------------
r = rehber.read_text(encoding='utf-8')

if 'import android.app.NotificationManager\n' not in r:
    r = r.replace('import android.app.Dialog\n', 'import android.app.Dialog\nimport android.app.NotificationManager\n', 1)

# After the standard overlay prompt, also warn Xiaomi-family devices once for v0.9.17.
call_marker = '''        maybePromptCallerOverlayPermission()
        val openStudent = intent.getLongExtra("open_student_id", 0L)
'''
call_repl = '''        maybePromptCallerOverlayPermission()
        maybePromptOemCallerPermission()
        val openStudent = intent.getLongExtra("open_student_id", 0L)
'''
if call_marker not in r:
    raise SystemExit('Rehber onCreate overlay prompt marker missing')
r = r.replace(call_marker, call_repl, 1)

# Insert OEM + full-screen rows immediately after the normal overlay setting.
settings_marker = '''        setting(body, "Arayan Kimliği Ekran Kartı", if (CallerOverlay.canDraw(this)) "Açık" else "İzin gerekli · Dokunun") { requestCallerOverlayPermission() }
        setting(body, "Veri Senkronizasyonu", "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
'''
settings_repl = '''        setting(body, "Arayan Kimliği Ekran Kartı", if (CallerOverlay.canDraw(this)) "Açık" else "İzin gerekli · Dokunun") { requestCallerOverlayPermission() }
        if (isXiaomiFamilyDevice()) {
            setting(body, "Xiaomi / HyperOS Üstte Gösterme", "Arka planda açılır pencere + Kilit ekranında göster izinlerini kontrol edin") { openOemPermissionEditor() }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            setting(body, "Tam Ekran Arama Yedeği", if (canUseFullScreenCaller()) "Açık" else "İzin gerekli · Dokunun") { requestFullScreenCallerPermission() }
        }
        setting(body, "Veri Senkronizasyonu", "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
'''
if settings_marker not in r:
    raise SystemExit('Rehber settings overlay marker missing')
r = r.replace(settings_marker, settings_repl, 1)

helper_marker = '''    private fun requestCallerOverlayPermission() {
'''
helpers = '''    private fun isXiaomiFamilyDevice(): Boolean {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase(Locale.ROOT)
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")
    }

    private fun maybePromptOemCallerPermission() {
        if (!isXiaomiFamilyDevice() || !CallerOverlay.canDraw(this)) return
        val prefs = getSharedPreferences("rehber_ui", MODE_PRIVATE)
        if (prefs.getBoolean("oem_overlay_prompt_v0917", false)) return
        prefs.edit().putBoolean("oem_overlay_prompt_v0917", true).apply()
        main.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            AlertDialog.Builder(this)
                .setTitle("Xiaomi / HyperOS ek izin")
                .setMessage("Standart ‘Ekran kartı’ izni Açık görünse bile Xiaomi/Redmi/POCO cihazlarda Telefon ekranının üstünde kalabilmek için ayrıca ‘Arka planda açılır pencereleri göster’ ve ‘Kilit ekranında göster’ izinlerinin açık olması gerekir. ELAK kartı görünmüyor veya Telefon uygulamasının altında kalıyorsa bu iki izni kontrol edin.")
                .setNegativeButton("SONRA", null)
                .setPositiveButton("İZİNLERİ AÇ") { _, _ -> openOemPermissionEditor() }
                .show()
        }, 900L)
    }

    private fun openOemPermissionEditor() {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                putExtra("extra_pkgname", packageName)
            }
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) { }
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            Toast.makeText(this, "Uygulama izinleri ekranı açılamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun canUseFullScreenCaller(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            (getSystemService(NotificationManager::class.java)).canUseFullScreenIntent()
        } catch (_: Exception) { false }
    }

    private fun requestFullScreenCallerPermission() {
        if (Build.VERSION.SDK_INT < 34 || canUseFullScreenCaller()) {
            Toast.makeText(this, "Tam ekran arama yedeği açık.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { Toast.makeText(this, "Tam ekran izin ayarı açılamadı.", Toast.LENGTH_LONG).show() }
        }
    }

'''
if helper_marker not in r:
    raise SystemExit('Rehber requestCallerOverlayPermission marker missing')
r = r.replace(helper_marker, helpers + helper_marker, 1)

r = re.sub(
    r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)',
    'setting(body, "Sürüm", "v0.9.17 · Arayan Kartı Üstte Kalma")',
    r,
    count=1
)

rehber.write_text(r, encoding='utf-8')

print('v0.9.17 caller overlay z-order + Xiaomi/HyperOS permission guidance applied')
