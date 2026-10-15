package tr.mcoaihl.elakcaller

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var connectionBadge: TextView
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

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); if (intent != null) { setIntent(intent); handleIntent(intent) } }

    private fun rounded(color: Int, radius: Float = 28f) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(44,52,44,36); setBackgroundColor(Color.rgb(246,249,252)) }
        val title = TextView(this).apply { text="ELAK Arayan Tanıma"; textSize=24f; setTextColor(Color.rgb(24,54,84)); setTypeface(typeface,android.graphics.Typeface.BOLD) }
        val desc = TextView(this).apply { text="PWA Akıllı Rehber ile birlikte çalışan küçük yardımcı uygulama. Gelen numarayı kurum rehberiyle eşleştirir; öğrenci ve veli verisini telefonda saklamaz."; textSize=14f; setTextColor(Color.rgb(92,112,132)); setPadding(0,18,0,18) }
        connectionBadge = TextView(this).apply { text="BAĞLI DEĞİL"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface,android.graphics.Typeface.BOLD); setPadding(18,13,18,13); background=rounded(Color.rgb(131,145,159)) }
        status = TextView(this).apply { textSize=14f; setPadding(22,18,22,18); background=rounded(Color.WHITE,22f); setTextColor(Color.rgb(42,69,95)) }
        val perm = Button(this).apply { text="Arayan Kimliği Yetkisini Aç"; setOnClickListener { requestCallScreeningRole() } }
        val overlay = Button(this).apply { text="Ekranın Üzerinde Gösterme İznini Aç"; setOnClickListener { requestOverlayPermission() } }
        codeInput = EditText(this).apply { hint="8 haneli eşleştirme kodu"; isSingleLine=true; setPadding(22,18,22,18) }
        pairButton = Button(this).apply { text="Bu Telefonu Eşleştir"; setOnClickListener { claim(codeInput.text.toString(), prefs.getString("base_url","https://elak.mcoaihl.com/rehber/") ?: "https://elak.mcoaihl.com/rehber/") } }
        val openPwa = Button(this).apply { text="Akıllı Rehberi Aç"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://elak.mcoaihl.com/rehber/"))) } }
        listOf(title,desc,connectionBadge,status,perm,overlay,codeInput,pairButton,openPwa).forEach { root.addView(it,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin=14 }) }
        return ScrollView(this).apply { addView(root) }
    }

    private fun setBadge(connected: Boolean, label: String = if (connected) "BAĞLI" else "BAĞLI DEĞİL") {
        connectionBadge.text=label
        connectionBadge.background=rounded(if(connected) Color.rgb(25,146,103) else Color.rgb(131,145,159))
    }

    private fun handleIntent(i: Intent) { val data=i.data?:return; if(data.scheme=="elakcaller"&&data.host=="pair"){val code=data.getQueryParameter("code").orEmpty();val base=data.getQueryParameter("base").orEmpty().ifBlank{"https://elak.mcoaihl.com/rehber/"};if(code.length==8){codeInput.setText(code);claim(code,base)}} }

    private fun claim(codeRaw:String,baseRaw:String){
        val code=codeRaw.trim().uppercase().replace(Regex("[^A-Z0-9]"),"")
        if(code.length!=8){Toast.makeText(this,"8 haneli eşleştirme kodunu girin.",Toast.LENGTH_SHORT).show();return}
        pairButton.isEnabled=false;status.text="Eşleştiriliyor…"
        thread { try {
            val base=if(baseRaw.endsWith("/"))baseRaw else "$baseRaw/";val url=URL(base+"api.php?action=native_pair_claim")
            val body=JSONObject().put("code",code).put("device_name",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toString()
            val c=(url.openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=3500;readTimeout=3500;doOutput=true;setRequestProperty("Content-Type","application/json")}
            c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val j=JSONObject(stream.bufferedReader().use{it.readText()});if(!j.optBoolean("ok"))throw Exception(j.optString("error","Eşleştirme başarısız."))
            prefs.edit().putString("token",j.getString("token")).putString("base_url",base).apply()
            runOnUiThread{setBadge(true,"BAĞLI");status.text="✓ Telefon ELAK Akıllı Rehber'e bağlandı.";pairButton.isEnabled=true;requestCallScreeningRole()}
        }catch(e:Exception){runOnUiThread{setBadge(false);status.text="Bağlantı kurulamadı: ${e.message?:"Hata"}";pairButton.isEnabled=true}} }
    }

    private fun refreshStatus(){
        val token=prefs.getString("token","").orEmpty();val rm=getSystemService(RoleManager::class.java);val roleOk=rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)&&rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);val overlayOk=Settings.canDrawOverlays(this)
        if(token.isBlank()){setBadge(false);status.text="1) Akıllı Rehber PWA'dan eşleştirme kodu oluşturun.\n2) Kodu bu uygulamaya aktarın.\n3) Arayan kimliği ve ekran üstü gösterim yetkilerini açın.";return}
        setBadge(true,"BAĞLI");status.text="Bağlantı doğrulanıyor…\nArayan kimliği: ${if(roleOk)"Açık" else "Kapalı"} · Ekran üstü: ${if(overlayOk)"Açık" else "Kapalı"}"
        checkServerStatus(token,roleOk,overlayOk)
    }

    private fun checkServerStatus(token:String,roleOk:Boolean,overlayOk:Boolean){thread{try{val base=prefs.getString("base_url","https://elak.mcoaihl.com/rehber/")?:"https://elak.mcoaihl.com/rehber/";val url=URL((if(base.endsWith("/"))base else "$base/")+"api.php?action=native_status");val c=(url.openConnection() as HttpURLConnection).apply{requestMethod="GET";connectTimeout=2500;readTimeout=2500;setRequestProperty("Authorization","Bearer $token")};if(c.responseCode !in 200..299)throw Exception("Bağlantı kaldırılmış");val j=JSONObject(c.inputStream.bufferedReader().use{it.readText()});val org=j.optString("org_name","ELAK Kurumu");runOnUiThread{setBadge(true,"BAĞLI");status.text="✓ $org\nArayan kimliği: ${if(roleOk)"Açık" else "Kapalı"} · Ekran üstü: ${if(overlayOk)"Açık" else "Kapalı"}${if(roleOk&&overlayOk)"\nArayan bilgisi tüm uygulamaların üzerinde gösterilecek." else "\nEksik yetkiyi açın."}"}}catch(e:Exception){prefs.edit().remove("token").apply();runOnUiThread{setBadge(false,"BAĞLANTI YOK");status.text="Sunucu bağlantısı doğrulanamadı veya cihaz bağlantısı kaldırıldı. Yeniden eşleştirin."}}}}

    private fun requestNeededPermissions(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3002)}
    private fun requestCallScreeningRole(){val rm=getSystemService(RoleManager::class.java);if(rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)&&!rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),3001) else refreshStatus()}
    private fun requestOverlayPermission(){if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}else Toast.makeText(this,"Ekran üstü gösterim izni zaten açık.",Toast.LENGTH_SHORT).show()}
    override fun onResume(){super.onResume();if(::status.isInitialized)refreshStatus()}
}
