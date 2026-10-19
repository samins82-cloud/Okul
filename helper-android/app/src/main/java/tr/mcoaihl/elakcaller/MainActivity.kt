package tr.mcoaihl.elakcaller

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var connectionBadge: TextView
    private lateinit var status: TextView
    private lateinit var roleState: TextView
    private lateinit var overlayState: TextView
    private lateinit var notificationState: TextView
    private lateinit var contactsState: TextView
    private lateinit var contactsButton: Button
    private lateinit var callDiagnostics: TextView
    private lateinit var codeInput: EditText
    private lateinit var pairButton: Button
    private lateinit var roleButton: Button
    private lateinit var overlayButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var refreshButton: Button

    private val prefs by lazy {
        getSharedPreferences("elak_caller", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        requestNeededPermissions()
        handleIntent(intent)
        refreshStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radiusDp: Int = 14,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(
            Color.WHITE,
            18,
            Color.rgb(219, 231, 241)
        )
        elevation = dp(3).toFloat()
    }

    private fun actionButton(
        label: String,
        backgroundColor: Int = Color.rgb(239, 246, 252),
        textColor: Int = Color.rgb(37, 91, 137)
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(textColor)
        background = rounded(backgroundColor, 13)
        minHeight = dp(46)
        minimumHeight = dp(46)
    }

    private fun stateChip(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(Color.rgb(240, 244, 248), 12)
        setTextColor(Color.rgb(95, 113, 130))
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(26))
            setBackgroundColor(Color.rgb(245, 249, 252))
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "E"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(33, 108, 174), 15)
        }
        val brandCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "ELAK Arayan Tanıma"
            textSize = 23f
            setTextColor(Color.rgb(24, 54, 84))
            setTypeface(typeface, Typeface.BOLD)
        }
        val version = TextView(this).apply {
            text = "v${packageManager.getPackageInfo(packageName, 0).versionName} · Android 10+"
            textSize = 11f
            setTextColor(Color.rgb(105, 124, 143))
        }
        brandCopy.addView(title)
        brandCopy.addView(version)
        brand.addView(mark, LinearLayout.LayoutParams(dp(50), dp(50)))
        brand.addView(
            brandCopy,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val desc = TextView(this).apply {
            text = "Gelen arama numarasını güvenli cihaz anahtarıyla ELAK Akıllı Rehber'e sorar; çağrıyı geciktirmeden arayan kişi ve ilgili öğrenci bilgilerini ekranda gösterir."
            textSize = 13f
            setTextColor(Color.rgb(88, 107, 126))
            setPadding(0, dp(12), 0, dp(14))
        }

        connectionBadge = TextView(this).apply {
            text = "BAĞLI DEĞİL"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(130, 145, 158), 13)
        }

        val connectionCard = card()
        val connectionTitle = TextView(this).apply {
            text = "Bağlantı Durumu"
            textSize = 14f
            setTextColor(Color.rgb(30, 62, 92))
            setTypeface(typeface, Typeface.BOLD)
        }
        status = TextView(this).apply {
            textSize = 12f
            setPadding(0, dp(9), 0, 0)
            setTextColor(Color.rgb(69, 91, 111))
        }
        connectionCard.addView(connectionTitle)
        connectionCard.addView(status)

        val diagnosticCard = card()
        val diagnosticTitle = TextView(this).apply {
            text = "Son Gelen Arama Tanılaması"
            textSize = 14f
            setTextColor(Color.rgb(30, 62, 92))
        }
        callDiagnostics = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(69, 91, 111))
            setPadding(0, dp(8), 0, 0)
            text = "Henüz Android arama olayı alınmadı."
        }
        diagnosticCard.addView(diagnosticTitle)
        diagnosticCard.addView(callDiagnostics)

        val permissionCard = card()
        val permissionTitle = TextView(this).apply {
            text = "Gerekli Yetkiler"
            textSize = 14f
            setTextColor(Color.rgb(30, 62, 92))
            setTypeface(typeface, Typeface.BOLD)
        }
        permissionCard.addView(permissionTitle)

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        roleState = stateChip("Arayan kimliği kontrol ediliyor")
        overlayState = stateChip("Ekran üstü kontrol ediliyor")
        notificationState = stateChip("Bildirim kontrol ediliyor")
        contactsState = stateChip("Telefon kişilerinin izni kontrol ediliyor")
        listOf(roleState, overlayState, notificationState, contactsState).forEach {
            chips.addView(
                it,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            )
        }
        permissionCard.addView(chips)

        roleButton = actionButton("Arayan Kimliği Yetkisini Aç").apply {
            setOnClickListener { requestCallScreeningRole() }
        }
        overlayButton = actionButton("Ekranın Üzerinde Gösterme İznini Aç").apply {
            setOnClickListener { requestOverlayPermission() }
        }
        permissionCard.addView(
            roleButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) }
        )
        permissionCard.addView(
            overlayButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        contactsButton = actionButton("Kayıtlı Kişilerden Gelen Aramaları Etkinleştir").apply {
            setOnClickListener { requestContactPermission() }
        }
        permissionCard.addView(
            contactsButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
        permissionCard.addView(TextView(this).apply {
            text = "Bu izin, Android'in telefona kayıtlı numaraların çağrılarını da ELAK'a bildirmesi içindir. " +
                "Telefon kişileri sunucuya aktarılmaz; yalnız gelen numara yetkili KVM rehberinde sorgulanır."
            textSize = 11f
            setTextColor(Color.rgb(94, 114, 132))
            setPadding(0, dp(6), 0, 0)
        })

        val pairCard = card()
        val pairTitle = TextView(this).apply {
            text = "Telefonu Eşleştir"
            textSize = 14f
            setTextColor(Color.rgb(30, 62, 92))
            setTypeface(typeface, Typeface.BOLD)
        }
        val pairInfo = TextView(this).apply {
            text = "Akıllı Rehber PWA > Ayarlar > Bu Telefonu Bağla bölümündeki 8 haneli kodu kullanın."
            textSize = 11f
            setTextColor(Color.rgb(92, 112, 132))
            setPadding(0, dp(6), 0, dp(10))
        }
        codeInput = EditText(this).apply {
            hint = "8 haneli eşleştirme kodu"
            isSingleLine = true
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(
                Color.rgb(249, 252, 254),
                12,
                Color.rgb(211, 224, 235)
            )
        }
        pairButton = actionButton(
            "Bu Telefonu Eşleştir",
            Color.rgb(33, 108, 174),
            Color.WHITE
        ).apply {
            setOnClickListener {
                claim(
                    codeInput.text.toString(),
                    BASE_URL
                )
            }
        }
        pairCard.addView(pairTitle)
        pairCard.addView(pairInfo)
        pairCard.addView(codeInput)
        pairCard.addView(
            pairButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        refreshButton = actionButton("Bağlantıyı Yeniden Kontrol Et").apply {
            setOnClickListener { refreshStatus() }
        }
        val openPwa = actionButton("Akıllı Rehberi Aç").apply {
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL)))
            }
        }
        disconnectButton = actionButton(
            "Bu Telefonun Bağlantısını Kaldır",
            Color.rgb(255, 241, 241),
            Color.rgb(173, 61, 61)
        ).apply {
            setOnClickListener { disconnectDevice() }
        }

        val footerCard = card().apply {
            addView(refreshButton)
            addView(
                openPwa,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
            )
            addView(
                disconnectButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
            )
        }

        listOf(
            brand,
            desc,
            connectionBadge,
            connectionCard,
            diagnosticCard,
            permissionCard,
            pairCard,
            footerCard
        ).forEachIndexed { index, view ->
            root.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = if (index == 7) 0 else dp(12)
                }
            )
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
    }

    private fun setBadge(
        connected: Boolean,
        label: String = if (connected) "BAĞLI" else "BAĞLI DEĞİL"
    ) {
        connectionBadge.text = label
        connectionBadge.background = rounded(
            if (connected) Color.rgb(28, 143, 102) else Color.rgb(130, 145, 158),
            13
        )
    }

    private fun updatePermissionUi() {
        val roleManager = getSystemService(RoleManager::class.java)
        val roleOk =
            roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val overlayOk = Settings.canDrawOverlays(this)
        val notificationOk =
            Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val contactsOk = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

        applyState(roleState, "Arayan kimliği", roleOk)
        applyState(overlayState, "Ekran üstü gösterim", overlayOk)
        applyState(notificationState, "Bildirim", notificationOk)
        applyState(contactsState, "Kayıtlı kişilerden aramalar", contactsOk)
        contactsButton.text =
            if (contactsOk) "Kayıtlı Kişiler İzni Açık ✓"
            else "Kayıtlı Kişilerden Gelen Aramaları Etkinleştir"
        contactsButton.isEnabled = !contactsOk

        roleButton.text =
            if (roleOk) "Arayan Kimliği Yetkisi Açık ✓"
            else "Arayan Kimliği Yetkisini Aç"
        roleButton.isEnabled = !roleOk

        overlayButton.text =
            if (overlayOk) "Ekran Üstü Gösterim Açık ✓"
            else "Ekranın Üzerinde Gösterme İznini Aç"
        overlayButton.isEnabled = !overlayOk
    }

    private fun applyState(view: TextView, label: String, enabled: Boolean) {
        view.text = "$label · ${if (enabled) "Açık" else "Kapalı"}"
        view.setTextColor(
            if (enabled) Color.rgb(28, 126, 91)
            else Color.rgb(166, 95, 26)
        )
        view.background = rounded(
            if (enabled) Color.rgb(235, 248, 242)
            else Color.rgb(255, 247, 232),
            12
        )
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "elakcaller" && data.host == "pair") {
            val code = data.getQueryParameter("code").orEmpty()
            if (code.length == 8) {
                codeInput.setText(code)
                claim(code, BASE_URL)
            }
        }
    }

    private fun claim(codeRaw: String, baseRaw: String) {
        val code = codeRaw
            .trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
        if (code.length != 8) {
            Toast.makeText(
                this,
                "8 haneli eşleştirme kodunu girin.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        pairButton.isEnabled = false
        status.text = "Eşleştiriliyor…"

        thread(name = "elak-pair") {
            try {
                val base = safeBase(baseRaw)
                val url = URL(base + "api.php?action=native_pair_claim")
                val body = JSONObject()
                    .put("code", code)
                    .put(
                        "device_name",
                        "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                    .put("app_version", packageManager.getPackageInfo(packageName, 0).versionName ?: "1.2.4")
                    .toString()

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "ELAK-Caller/1.2.4")
                }

                try {
                    connection.outputStream.use {
                        it.write(body.toByteArray(Charsets.UTF_8))
                    }
                    val stream =
                        if (connection.responseCode in 200..299)
                            connection.inputStream
                        else
                            connection.errorStream
                    val json = JSONObject(
                        stream.bufferedReader().use { it.readText() }
                    )
                    if (!json.optBoolean("ok")) {
                        throw Exception(
                            json.optString(
                                "error",
                                "Eşleştirme başarısız."
                            )
                        )
                    }

                    prefs.edit()
                        .putString("token", json.getString("token"))
                        .putString("base_url", base)
                        .apply()

                    runOnUiThread {
                        setBadge(true, "BAĞLI")
                        status.text =
                            "✓ Telefon ELAK Akıllı Rehber'e bağlandı."
                        pairButton.isEnabled = true
                        codeInput.setText("")
                        updatePermissionUi()
                        requestCallScreeningRole()
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBadge(false)
                    status.text =
                        "Bağlantı kurulamadı: ${e.message ?: "Hata"}"
                    pairButton.isEnabled = true
                }
            }
        }
    }

    private fun refreshStatus() {
        updatePermissionUi()
        refreshCallDiagnostics()
        val token = prefs.getString("token", "").orEmpty()
        disconnectButton.isEnabled = token.isNotBlank()

        if (token.isBlank()) {
            setBadge(false)
            status.text =
                "1) Akıllı Rehber PWA'dan eşleştirme kodu oluşturun.\n" +
                "2) Kodu bu uygulamaya aktarın.\n" +
                "3) Arayan kimliği ve ekran üstü gösterim yetkilerini açın."
            return
        }

        setBadge(true, "KONTROL EDİLİYOR")
        status.text = "Sunucu bağlantısı doğrulanıyor…"
        checkServerStatus(token)
    }

    private fun checkServerStatus(token: String) {
        thread(name = "elak-status") {
            val base = safeBase(
                prefs.getString("base_url", BASE_URL).orEmpty()
            )
            val url = URL(base + "api.php?action=native_status")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2200
                readTimeout = 2200
                useCaches = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ELAK-Caller/1.2.4")
            }

            try {
                val code = connection.responseCode
                if (code == 401 || code == 403) {
                    prefs.edit().remove("token").apply()
                    runOnUiThread {
                        setBadge(false, "BAĞLANTI KALDIRILDI")
                        status.text =
                            "Bu telefonun sunucu bağlantısı geçersiz. Yeniden eşleştirin."
                        disconnectButton.isEnabled = false
                    }
                    return@thread
                }
                if (code !in 200..299) {
                    throw Exception("HTTP $code")
                }

                val json = JSONObject(
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                )
                val org = json.optString("org_name", "ELAK Kurumu")
                runOnUiThread {
                    setBadge(true, "BAĞLI")
                    status.text =
                        "✓ $org\n" +
                        "Cihaz: ${json.optString("device_name", Build.MODEL)}\n" +
                        "Gelen aramalar için hazır."
                    disconnectButton.isEnabled = true
                    updatePermissionUi()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    setBadge(true, "BAĞLI · ÇEVRİMDIŞI")
                    status.text =
                        "Sunucuya şu an ulaşılamadı. Eşleştirme korunuyor; bağlantı geldiğinde otomatik devam eder."
                    disconnectButton.isEnabled = true
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun disconnectDevice() {
        val token = prefs.getString("token", "").orEmpty()
        if (token.isBlank()) {
            clearLocalConnection()
            return
        }

        disconnectButton.isEnabled = false
        status.text = "Bağlantı kaldırılıyor…"

        thread(name = "elak-disconnect") {
            val base = safeBase(
                prefs.getString("base_url", BASE_URL).orEmpty()
            )
            val url = URL(base + "api.php?action=native_disconnect")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2200
                readTimeout = 2200
                doOutput = true
                useCaches = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ELAK-Caller/1.2.4")
            }

            var serverRevoked = false
            try {
                connection.outputStream.use {
                    it.write("{}".toByteArray(Charsets.UTF_8))
                }
                serverRevoked = connection.responseCode in 200..299
            } catch (_: Exception) {
            } finally {
                connection.disconnect()
            }

            prefs.edit()
                .remove("token")
                .remove("base_url")
                .apply()

            runOnUiThread {
                setBadge(false)
                status.text =
                    if (serverRevoked)
                        "Bu telefonun ELAK bağlantısı kaldırıldı."
                    else
                        "Yerel bağlantı kaldırıldı. Sunucuya ulaşılamadığı için cihaz kaydı daha sonra yönetici ekranından kapatılabilir."
                disconnectButton.isEnabled = false
            }
        }
    }

    private fun clearLocalConnection() {
        prefs.edit()
            .remove("token")
            .remove("base_url")
            .apply()
        setBadge(false)
        status.text = "Telefon eşleştirilmemiş."
        disconnectButton.isEnabled = false
    }

    private fun requestNeededPermissions() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun refreshCallDiagnostics() {
        val message = prefs.getString("last_call_result", "").orEmpty()
        val at = prefs.getLong("last_call_at", 0L)
        callDiagnostics.text = if (at == 0L || message.isBlank()) {
            "Henüz Android arama olayı alınmadı. Gerçek GSM aramasıyla sınayın."
        } else {
            val stamp = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM
            ).format(java.util.Date(at))
            "$stamp\n$message"
        }
    }

    private fun requestContactPermission() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
        } else {
            updatePermissionUi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS || requestCode == REQUEST_NOTIFICATIONS) {
            updatePermissionUi()
        }
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (
            roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            startActivityForResult(
                roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_CALL_SCREENING
                ),
                REQUEST_CALL_SCREENING
            )
        } else {
            refreshStatus()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            Toast.makeText(
                this,
                "Ekran üstü gösterim izni zaten açık.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun safeBase(raw: String): String {
        return try {
            val uri = Uri.parse(raw)
            if (
                uri.scheme == "https" &&
                uri.host.equals("elak.mcoaihl.com", true) &&
                uri.path.orEmpty().startsWith("/rehber")
            ) {
                if (raw.endsWith("/")) raw else "$raw/"
            } else {
                BASE_URL
            }
        } catch (_: Exception) {
            BASE_URL
        }
    }

    companion object {
        private const val BASE_URL =
            "https://elak.mcoaihl.com/rehber/"
        private const val REQUEST_CALL_SCREENING = 3001
        private const val REQUEST_NOTIFICATIONS = 3002
        private const val REQUEST_CONTACTS = 3003
    }
}
