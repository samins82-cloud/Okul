from pathlib import Path

ROOT = Path(__file__).resolve().parent
main = ROOT / "app/src/main/java/com/elak/okulum/MainActivity.kt"

s = main.read_text(encoding="utf-8")

s = s.replace(
'''    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/okulum/"
        private val INTERNAL_HOSTS = setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com", "dp01.ni.net.tr")
    }''',
'''    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/okulum/"
        const val EXTRA_FORCE_WEB = "elak_force_web"
        const val EXTRA_START_URL = "elak_start_url"
        private val INTERNAL_HOSTS = setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com", "dp01.ni.net.tr")
    }''',1)

s = s.replace(
'''    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var pendingDownload: PendingDownload? = null''',
'''    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var pendingDownload: PendingDownload? = null
    private var forceWebMode = false
    private var requestedStartUrl = BASE_URL''',1)

s = s.replace(
'''        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(8, 26, 56)
        window.navigationBarColor = Color.WHITE
        buildUi(); configureInsets(); configureWebView(); loadHome()''',
'''        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        forceWebMode = intent.getBooleanExtra(EXTRA_FORCE_WEB, false)
        requestedStartUrl = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") } ?: BASE_URL
        if (!forceWebMode) {
            val saved = OkulumSession(this)
            if (saved.username.isNotBlank() && saved.password.isNotBlank()) {
                startActivity(Intent(this, ElakHomeActivity::class.java))
                finish()
                return
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(8, 26, 56)
        window.navigationBarColor = Color.WHITE
        buildUi(); configureInsets(); configureWebView(); loadHome()''',1)

s = s.replace(
'''                    runOnUiThread { Toast.makeText(this@MainActivity, "Akıllı Rehber otomatik bağlandı: $count telefon", Toast.LENGTH_SHORT).show() }''',
'''                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "ELAK Okulum hazır: $count telefon eşitlendi", Toast.LENGTH_SHORT).show()
                        if (!forceWebMode && !isFinishing) {
                            startActivity(Intent(this@MainActivity, ElakHomeActivity::class.java))
                            finish()
                        }
                    }''',1)

s = s.replace(
'''    private fun loadHome() { webView.loadUrl(BASE_URL, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6")) }''',
'''    private fun loadHome() { webView.loadUrl(requestedStartUrl, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6")) }''',1)

main.write_text(s, encoding="utf-8")
print("v0.9.0 native hub launcher bridge applied")
