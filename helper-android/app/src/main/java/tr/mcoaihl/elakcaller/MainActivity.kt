package tr.mcoaihl.elakcaller

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var codeInput: EditText
    private lateinit var pairButton: Button
    private val prefs by lazy { getSharedPreferences("elak_caller", Context.MODE_PRIVATE) }

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

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 52, 44, 36)
            setBackgroundColor(Color.rgb(246,249,252))
        }
        val title = TextView(this).apply {
            text = "ELAK Arayan Tanıma"
            textSize = 24f
            setTextColor(Color.rgb(24,54,84))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val desc = TextView(this).apply {
            text = "Bu küçük yardımcı uygulama yalnız gelen telefon numarasını ELAK Akıllı Rehber ile eşleştirir. Rehber ve öğrenci verisi telefonda tutulmaz."
            textSize = 14f
            setTextColor(Color.rgb(92,112,132))
            setPadding(0,18,0,24)
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(24,20,24,20)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.rgb(30,79,121))
        }
        val perm = Button(this).apply {
            text = "Arayan Kimliği Yetkisini Aç"
            setOnClickListener { requestCallScreeningRole() }
        }
        codeInput = EditText(this).apply {
            hint = "8 haneli eşleştirme kodu"
            isSingleLine = true
            setPadding(22,18,22,18)
        }
        pairButton = Button(this).apply {
            text = "Bu Telefonu Eşleştir"
            setOnClickListener { claim(codeInput.text.toString(), prefs.getString("base_url","https://elak.mcoaihl.com/rehber/") ?: "https://elak.mcoaihl.com/rehber/") }
        }
        val openPwa = Button(this).apply {
            text = "Akıllı Rehberi Aç"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://elak.mcoaihl.com/rehber/")))
            }
        }
        listOf(title,desc,status,perm,codeInput,pairButton,openPwa).forEach {
            root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = if (it === desc) 4 else 14
            })
        }
        return ScrollView(this).apply { addView(root) }
    }

    private fun handleIntent(i: Intent) {
        val data = i.data ?: return
        if (data.scheme == "elakcaller" && data.host == "pair") {
            val code = data.getQueryParameter("code").orEmpty()
            val base = data.getQueryParameter("base").orEmpty().ifBlank { "https://elak.mcoaihl.com/rehber/" }
            if (code.length == 8) {
                codeInput.setText(code)
                claim(code, base)
            }
        }
    }

    private fun claim(codeRaw: String, baseRaw: String) {
        val code = codeRaw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (code.length != 8) {
            Toast.makeText(this, "8 haneli eşleştirme kodunu girin.", Toast.LENGTH_SHORT).show()
            return
        }
        pairButton.isEnabled = false
        status.text = "Eşleştiriliyor…"
        thread {
            try {
                val base = if (baseRaw.endsWith("/")) baseRaw else "$baseRaw/"
                val url = URL(base + "api.php?action=native_pair_claim")
                val body = JSONObject().put("code", code).put("device_name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toString()
                val c = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3500
                    readTimeout = 3500
                    doOutput = true
                    setRequestProperty("Content-Type","application/json")
                }
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                val j = JSONObject(text)
                if (!j.optBoolean("ok")) throw Exception(j.optString("error","Eşleştirme başarısız."))
                val token = j.getString("token")
                prefs.edit().putString("token",token).putString("base_url",base).apply()
                runOnUiThread {
                    status.text = "✓ Bu telefon ELAK Akıllı Rehber'e bağlandı."
                    pairButton.isEnabled = true
                    requestCallScreeningRole()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Bağlantı kurulamadı: ${e.message ?: "Hata"}"
                    pairButton.isEnabled = true
                }
            }
        }
    }

    private fun refreshStatus() {
        val token = prefs.getString("token","").orEmpty()
        val roleManager = getSystemService(RoleManager::class.java)
        val roleOk = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        status.text = when {
            token.isBlank() -> "1) Akıllı Rehber PWA'dan eşleştirme kodu oluşturun.\n2) Bu uygulamada kodu girin.\n3) Arayan kimliği yetkisini açın."
            roleOk -> "✓ Telefon bağlı ve arayan tanıma yetkisi aktif."
            else -> "✓ Telefon bağlı.\nArayan tanıma için sistem yetkisini açın."
        }
    }

    private fun requestNeededPermissions() {
        val req = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.READ_CONTACTS)
        if (req.isNotEmpty()) requestPermissions(req.toTypedArray(), 3002)
    }

    private fun requestCallScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 3001)
        } else refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }
}
