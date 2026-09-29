from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent
activity=ROOT/'app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt'
s=activity.read_text(encoding='utf-8')

old='val c=CheckBox(this).apply{text=label;isChecked=true;textSize=12f;setTextColor(text)}'
new='val c=CheckBox(this).apply{text=label;isChecked=true;textSize=12f;setTextColor(Color.rgb(15,34,62))}'
if old not in s:
    raise SystemExit('lunch checkbox marker missing')
s=s.replace(old,new,1)

old='val noSms=infoBox("Bu izin türünde çıkış sırasında veliye SMS veya WhatsApp gönderilmez.",green)'
new='val noSms=card(accent=green).apply{addView(bodyText("Bu izin türünde çıkış sırasında veliye SMS veya WhatsApp gönderilmez."))}'
if old not in s:
    raise SystemExit('lunch no-sms marker missing')
s=s.replace(old,new,1)
activity.write_text(s,encoding='utf-8')

# Rehber: liste görünümünde convertView dış sarmalayıcıyı döndürüyordu ve tag
# StudentHolder olmadığı için arama/list-grid geçişinden sonra ClassCastException oluşuyordu.
rehber=ROOT/'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'
r=rehber.read_text(encoding='utf-8')

if 'import android.provider.Settings' not in r:
    r=r.replace('import android.os.Looper\n','import android.os.Looper\nimport android.provider.Settings\n',1)

adapter_pat=re.compile(r'''    private inner class StudentListAdapter\(private val rows: List<CallerCache\.Student>\) : BaseAdapter\(\) \{.*?\n    \}\n\n    private data class StudentHolder\(val avatar: AvatarHolder, val name: TextView, val meta: TextView, val phone: TextView\)''',re.S)
adapter_new='''    private inner class StudentListAdapter(private val rows: List<CallerCache.Student>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val student = rows[position]
            var row = convertView as? LinearLayout
            var holder = row?.tag as? StudentHolder
            if (row == null || holder == null) {
                row = LinearLayout(this@RehberActivity84).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                }
                val card = LinearLayout(this@RehberActivity84).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(Color.WHITE, dp(17).toFloat(), soft)
                }
                val avatarPair = createAvatarFrame()
                card.addView(avatarPair.first, LinearLayout.LayoutParams(dp(52), dp(52)))
                val name = TextView(this@RehberActivity84).apply { textSize = 15.5f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) }
                val meta = TextView(this@RehberActivity84).apply { textSize = 10.8f; setTextColor(muted) }
                val phone = TextView(this@RehberActivity84).apply { textSize = 10.8f; setTextColor(muted) }
                val info = LinearLayout(this@RehberActivity84).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                    addView(name); addView(meta); addView(phone)
                }
                card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86)))
                holder = StudentHolder(avatarPair.second, name, meta, phone)
                row.tag = holder
                row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(94))
            }
            val h = holder ?: return row
            bindAvatar(h.avatar, student)
            h.name.text = student.name
            h.meta.text = student.className + "  ·  No: " + student.schoolNo
            h.phone.text = "Öğrenci Tel: " + if (student.phone.isBlank()) "Eklenmemiş" else PhoneUtil.display(student.phone)
            return row
        }
    }

    private data class StudentHolder(val avatar: AvatarHolder, val name: TextView, val meta: TextView, val phone: TextView)'''
r,n=adapter_pat.subn(adapter_new,r,count=1)
if n!=1:
    raise SystemExit('Rehber StudentListAdapter marker missing')

# Ayarlardan dönünce ekran kartı izin durumunu yenile.
if 'override fun onResume()' not in r:
    marker='''    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val studentId = intent.getLongExtra("open_student_id", 0L)
        if (studentId > 0) showStudentDetail(studentId)
    }
'''
    replacement=marker+'''\n    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && active == "settings") {
            main.post { if (!isFinishing && !isDestroyed) showSettings() }
        }
    }
'''
    if marker not in r:
        raise SystemExit('Rehber onNewIntent marker missing')
    r=r.replace(marker,replacement,1)

# İlk kullanımda ekran üstü kart iznini bir kez açıkla.
call_marker='''        requestNotificationPermissionIfNeeded()
        val openStudent = intent.getLongExtra("open_student_id", 0L)
'''
call_repl='''        requestNotificationPermissionIfNeeded()
        maybePromptCallerOverlayPermission()
        val openStudent = intent.getLongExtra("open_student_id", 0L)
'''
if call_marker in r:
    r=r.replace(call_marker,call_repl,1)

# Ana sayfadaki durum satırı iki ayrı izni gösterir.
r=r.replace(
'''        val caller = if (isCallScreeningActive()) "Açık" else "Kapalı"
        return TextView(this).apply {
            text = "Arayan kimliği: $caller  ·  Telefon kaydı: ${cache.count()}  ·  Son eşitleme: $last"''',
'''        val caller = if (isCallScreeningActive()) "Açık" else "Kapalı"
        val overlay = if (CallerOverlay.canDraw(this)) "Açık" else "İzin gerekli"
        return TextView(this).apply {
            text = "Arayan kimliği: $caller  ·  Ekran kartı: $overlay  ·  Telefon kaydı: ${cache.count()}  ·  Son eşitleme: $last"''',1)

settings_marker='''        setting(body, "Arayan Kimliği", if (isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
        setting(body, "Veri Senkronizasyonu", "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
'''
settings_repl='''        setting(body, "Arayan Kimliği", if (isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
        setting(body, "Arayan Kimliği Ekran Kartı", if (CallerOverlay.canDraw(this)) "Açık" else "İzin gerekli · Dokunun") { requestCallerOverlayPermission() }
        setting(body, "Veri Senkronizasyonu", "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
'''
if settings_marker not in r:
    raise SystemExit('Rehber settings marker missing')
r=r.replace(settings_marker,settings_repl,1)

helper_marker='''    private fun requestCallScreeningRole() {
'''
helpers='''    private fun maybePromptCallerOverlayPermission() {
        if (!isCallScreeningActive() || CallerOverlay.canDraw(this)) return
        val prefs = getSharedPreferences("rehber_ui", MODE_PRIVATE)
        if (prefs.getBoolean("caller_overlay_prompt_v098", false)) return
        prefs.edit().putBoolean("caller_overlay_prompt_v098", true).apply()
        main.postDelayed({
            if (isFinishing || isDestroyed || CallerOverlay.canDraw(this)) return@postDelayed
            AlertDialog.Builder(this)
                .setTitle("Arayan kimliği ekran kartı")
                .setMessage("Android 14 ve üzeri sürümlerde arayan veli/öğrenci kartının telefon ekranının üstünde görünmesi için ‘diğer uygulamaların üzerinde göster’ izni gerekir.")
                .setNegativeButton("Daha Sonra", null)
                .setPositiveButton("İzin Ver") { _, _ -> requestCallerOverlayPermission() }
                .show()
        }, 700L)
    }

    private fun requestCallerOverlayPermission() {
        if (CallerOverlay.canDraw(this)) {
            Toast.makeText(this, "Arayan kimliği ekran kartı izni açık.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
            catch (_: Exception) { Toast.makeText(this, "Ekran üstü izin ayarı açılamadı.", Toast.LENGTH_LONG).show() }
        }
    }

'''
if helper_marker not in r:
    raise SystemExit('Rehber request role marker missing')
r=r.replace(helper_marker,helpers+helper_marker,1)

r=re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.8 · Rehber Kararlılık")', r, count=1)
rehber.write_text(r,encoding='utf-8')

print('v0.9.8 Rehber list/search crash + caller overlay fix applied')
