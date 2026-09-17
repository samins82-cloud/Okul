package com.elak.okulum.rehber

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

class RehberActivity : AppCompatActivity() {
    private val session by lazy { RehberSession(this) }
    private val cache by lazy { CallerCache(this) }
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var classSpinner: Spinner
    private lateinit var list: ListView
    private lateinit var studentBtn: Button
    private lateinit var guardianBtn: Button
    private var guardianMode = false
    private var entries: List<CallerCache.DirectoryEntry> = emptyList()

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 26, 56)
        buildUi()
        refreshStatus()
        refreshDirectory()
        if (!session.token.isNullOrBlank()) syncNow(false)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245, 247, 250)) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 10, 10); setBackgroundColor(Color.rgb(8, 26, 56))
        }
        top.addView(Button(this).apply { text = "‹"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(56, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(TextView(this).apply {
            text = "ELAK Akıllı Rehber"; textSize = 18f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Button(this).apply { text = "↻"; setOnClickListener { syncNow(true) } })
        top.addView(Button(this).apply { text = "⚙"; setOnClickListener { showSetup() } })
        root.addView(top)

        status = TextView(this).apply { textSize = 11f; setPadding(14, 7, 14, 7); setTextColor(Color.DKGRAY) }
        root.addView(status)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10, 6, 10, 4) }
        studentBtn = Button(this).apply { text = "Öğrenci Rehberi"; setOnClickListener { guardianMode = false; updateTabs(); refreshDirectory() } }
        guardianBtn = Button(this).apply { text = "Veli Rehberi"; setOnClickListener { guardianMode = true; updateTabs(); refreshDirectory() } }
        tabs.addView(studentBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(guardianBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs)

        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10, 2, 10, 6) }
        search = EditText(this).apply {
            hint = "Ad, no, sınıf veya telefon ara"; isSingleLine = true
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) { refreshDirectory(false) }
            })
        }
        classSpinner = Spinner(this).apply {
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { refreshDirectory(false) }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        filters.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        filters.addView(classSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(filters)

        list = ListView(this).apply { dividerHeight = 1 }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8, 6, 8, 8); setBackgroundColor(Color.WHITE) }
        bottom.addView(Button(this).apply { text = "☎ Rehber"; isEnabled = false }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(Button(this).apply { text = "⚙ Arayan Kimliği"; setOnClickListener { showSetup() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(Button(this).apply { text = "🌐 Yönetim"; setOnClickListener { openWebAdmin() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bottom)
        setContentView(root)
        updateTabs()
    }

    private fun updateTabs() {
        studentBtn.alpha = if (!guardianMode) 1f else .55f
        guardianBtn.alpha = if (guardianMode) 1f else .55f
    }

    private fun refreshDirectory(rebuildClasses: Boolean = true) {
        if (rebuildClasses) {
            val current = classSpinner.selectedItem?.toString().orEmpty()
            val classes = listOf("Tüm Sınıflar") + cache.listClasses()
            classSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classes)
            val idx = classes.indexOf(current).takeIf { it >= 0 } ?: 0
            classSpinner.setSelection(idx, false)
        }
        val cls = classSpinner.selectedItem?.toString().orEmpty().let { if (it == "Tüm Sınıflar") "" else it }
        entries = cache.listEntries(guardianMode, search.text?.toString().orEmpty(), cls)
        list.adapter = DirectoryAdapter(entries)
    }

    private inner class DirectoryAdapter(private val data: List<CallerCache.DirectoryEntry>) : BaseAdapter() {
        override fun getCount() = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val e = data[position]
            val row = LinearLayout(this@RehberActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(16, 12, 12, 10); setBackgroundColor(Color.WHITE)
            }
            row.addView(TextView(this@RehberActivity).apply {
                text = if (guardianMode) e.guardianName.ifBlank { "Veli" } else e.studentName.ifBlank { "Öğrenci" }
                textSize = 16f; setTextColor(Color.rgb(8, 26, 56)); setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(this@RehberActivity).apply {
                text = if (guardianMode) {
                    "${e.relationship.ifBlank { "Veli" }} · ${e.studentName} · ${e.className} · No: ${e.schoolNo}"
                } else {
                    "${e.className} · No: ${e.schoolNo}" + if (e.guardianName.isNotBlank()) " · ${e.guardianName} (${e.relationship.ifBlank { "Veli" }})" else ""
                }
                textSize = 12f; setTextColor(Color.DKGRAY)
            })
            val actions = LinearLayout(this@RehberActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            actions.addView(Button(this@RehberActivity).apply { text = "☎ Ara"; setOnClickListener { dial(e.phoneKey) } })
            actions.addView(Button(this@RehberActivity).apply { text = "WhatsApp"; setOnClickListener { whatsapp(e.phoneKey) } })
            row.addView(actions)
            return row
        }
    }

    private fun dial(phone: String) {
        if (phone.isBlank()) return
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$phone")))
    }

    private fun whatsapp(phone: String) {
        if (phone.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))) }
        catch (_: Exception) { Toast.makeText(this, "WhatsApp açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    private fun openWebAdmin() {
        val token = session.token
        if (token.isNullOrBlank()) { showSetup(); return }
        thread {
            val sso = RehberApi.webSso(token)
            runOnUiThread {
                val url = if (!sso.isNullOrBlank()) "https://elak.mcoaihl.com/rehber/index.php?mobile_sso=${Uri.encode(sso)}" else "https://elak.mcoaihl.com/rehber/"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun syncNow(showToast: Boolean) {
        val token = session.token
        if (token.isNullOrBlank()) { if (showToast) showSetup(); return }
        status.text = "Rehber senkronize ediliyor…"
        thread {
            try {
                val count = cache.replaceFromSync(RehberApi.sync(token))
                session.lastSync = System.currentTimeMillis()
                runOnUiThread {
                    refreshStatus(); refreshDirectory()
                    if (showToast) Toast.makeText(this, "$count kayıt güncellendi.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshStatus()
                    if (showToast) Toast.makeText(this, "Senkronizasyon: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSetup() {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 20, 36, 8) }
        wrap.addView(TextView(this).apply {
            text = if (session.token.isNullOrBlank()) "Okulum hesabıyla otomatik eşleşme yapılamadıysa Rehber hesabını yalnız bir kez tanımlayın." else "Rehber oturumu etkin. Arayan kimliği izinlerini buradan yönetebilirsiniz."
            setPadding(0, 0, 0, 10)
        })
        val user = EditText(this).apply { hint = "Akıllı Rehber kullanıcı adı"; setText(session.username) }
        val pass = EditText(this).apply { hint = "Akıllı Rehber şifresi"; inputType = 0x00000081 }
        wrap.addView(user); wrap.addView(pass)
        wrap.addView(Button(this).apply { text = "Arayan Kimliği Yetkisi"; setOnClickListener { requestCallScreeningRole() } })
        wrap.addView(Button(this).apply { text = "Arayan Kartını Ekran Üstünde Göster"; setOnClickListener { requestOverlay() } })
        if (Build.VERSION.SDK_INT >= 33) wrap.addView(Button(this).apply { text = "Bildirim İzni"; setOnClickListener { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) } })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Akıllı Rehber Ayarları")
            .setView(wrap)
            .setNegativeButton("Kapat", null)
            .setNeutralButton("Oturumu Temizle") { _, _ ->
                session.clear(); cache.writableDatabase.delete("callers", null, null); refreshStatus(); refreshDirectory()
            }
            .setPositiveButton("Giriş + Senkronize", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = user.text.toString().trim(); val p = pass.text.toString()
                if (u.isBlank() || p.isBlank()) { Toast.makeText(this, "Kullanıcı adı ve şifre gerekli.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                thread {
                    try {
                        val login = RehberApi.login(u, p); session.token = login.token; session.username = u
                        val count = cache.replaceFromSync(RehberApi.sync(login.token)); session.lastSync = System.currentTimeMillis()
                        runOnUiThread { dialog.dismiss(); refreshStatus(); refreshDirectory(); Toast.makeText(this, "Akıllı Rehber etkin. $count kayıt eşleştirildi.", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) {
                        runOnUiThread { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; Toast.makeText(this, e.message ?: "Giriş başarısız.", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            else Toast.makeText(this, "Arayan kimliği rolü zaten etkin veya kullanılamıyor.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        else Toast.makeText(this, "Ekran üstü arayan kartı etkin.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatus() {
        val role = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) else false
        val synced = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "henüz yok"
        status.text = "Arayan kimliği: ${if (role) "Açık" else "Kapalı"} · Kayıt: ${cache.count()} · Son eşitleme: $synced"
    }

    override fun onResume() { super.onResume(); refreshStatus() }
}
