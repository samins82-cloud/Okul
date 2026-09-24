from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"
home = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"

s = activity.read_text(encoding="utf-8")

pat = re.compile(r'''    private fun showStudents\(\) \{.*?\n    \}\n\n    private fun showNewPermission\(\) \{''', re.S)
new_block = r'''    private var selectedStudent: JSONObject? = null

    private fun showStudents() {
        currentNav = "students"; selectNav(currentNav); titleText.text = "İzin Takip • Öğrenciler"
        val page = pageColumn()
        page.addView(hero("Öğrenciler", "Güncel öğrenci kaynağı Akıllı Rehber'dir. Sınıf seçin; öğrenciyi seçip doğrudan izin oluşturun."))
        val search = input("Ad, soyad veya okul no")
        val classFilter = modernSpinner(listOf("Sınıf Seçin"))
        val filterInfo = TextView(this).apply {
            text = "Sınıf seçildiğinde güncel öğrenciler listelenir."
            textSize = 11.5f; setTextColor(muted); setPadding(dp(4), dp(2), dp(4), dp(5))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(search); page.addView(space(8)); page.addView(classFilter); page.addView(filterInfo); page.addView(space(5)); page.addView(list)
        showContent(wrapScroll(page))

        var selectedClass = ""
        var serial = 0

        fun classSig(value:String):String {
            val v=value.trim().uppercase(java.util.Locale.forLanguageTag("tr-TR"))
            val grade=Regex("""(?:^|[^0-9])(5|6|7|8|9|10|11|12)(?:[^0-9]|$)""").find(v)?.groupValues?.getOrNull(1).orEmpty()
            val section=Regex("""[/\-\s]([A-ZÇĞİÖŞÜ])(?:\s*ŞUBESİ)?\s*$""").find(v)?.groupValues?.getOrNull(1).orEmpty()
            return if(grade.isNotBlank() && section.isNotBlank()) "$grade/$section" else v.replace(" ","")
        }

        fun gradeOf(value:String):Int = Regex("""(?:^|[^0-9])(5|6|7|8|9|10|11|12)(?:[^0-9]|$)""")
            .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

        fun scopeOk(clazz:String):Boolean {
            val g=gradeOf(clazz)
            return when(session.schoolScope){
                "middle" -> g in 5..8
                "high" -> g in 9..12
                else -> true
            }
        }

        fun toJson(no:String,name:String,clazz:String,phone:String):JSONObject {
            val base=JSONObject()
                .put("student_no",no)
                .put("full_name",name)
                .put("class_name",clazz)
                .put("school_level",if(gradeOf(clazz)>=9)"high" else "middle")
            if(phone.isNotBlank())base.put("student_phone",phone)
            return enrichStudent(base) ?: base
        }

        fun load() {
            val mySerial=++serial
            val q=search.text.toString().trim().lowercase(java.util.Locale.forLanguageTag("tr-TR"))
            if(selectedClass.isBlank() && q.isBlank()){
                list.removeAllViews()
                list.addView(emptyCard("👥","Sınıf seçin","Yukarıdaki sınıf filtresinden bir sınıf seçerek güncel öğrencileri görüntüleyin."))
                filterInfo.text="Sınıf seçildiğinde güncel öğrenciler listelenir."
                return
            }
            list.removeAllViews(); list.addView(emptyText("Akıllı Rehber yükleniyor…"))
            thread {
                try {
                    ensureRehberData()
                    val rows=rehberCache.listStudents()
                        .filter{scopeOk(it.className)}
                        .filter{selectedClass.isBlank() || classSig(it.className)==classSig(selectedClass)}
                        .filter{
                            q.isBlank() || it.name.lowercase(java.util.Locale.forLanguageTag("tr-TR")).contains(q) ||
                                it.schoolNo.lowercase(java.util.Locale.forLanguageTag("tr-TR")).contains(q)
                        }
                        .sortedWith(compareBy({it.name},{it.schoolNo}))
                        .map{toJson(it.schoolNo,it.name,it.className,it.phone)}
                    runOnUiThread {
                        if(mySerial!=serial)return@runOnUiThread
                        list.removeAllViews()
                        filterInfo.text=(if(selectedClass.isBlank())"Arama" else selectedClass)+" • ${rows.size} öğrenci"
                        if(rows.isEmpty())list.addView(emptyCard("🔎","Öğrenci bulunamadı","Sınıfı veya arama bilgisini değiştirin."))
                        rows.forEach { student ->
                            val item=studentCard(student,true)
                            item.setOnClickListener {
                                filterInfo.text="${student.optString("full_name")} izin için hazırlanıyor…"
                                thread {
                                    try {
                                        val synced=IzinApi.syncStudentFromDirectory(session,student)
                                        runOnUiThread { showNewPermission(synced) }
                                    } catch(e:Exception) {
                                        runOnUiThread {
                                            filterInfo.text=(if(selectedClass.isBlank())"Arama" else selectedClass)+" • ${rows.size} öğrenci"
                                            toast(e.message ?: "Öğrenci izin kaydına bağlanamadı.")
                                        }
                                    }
                                }
                            }
                            list.addView(item)
                        }
                    }
                }catch(e:Exception){
                    runOnUiThread{if(mySerial==serial){list.removeAllViews();list.addView(emptyText(e.message?:"Rehber öğrencileri yüklenemedi."))}}
                }
            }
        }

        classFilter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){
                selectedClass=if(position<=0)"" else classFilter.getItemAtPosition(position).toString();load()
            }
            override fun onNothingSelected(parent:AdapterView<*>?){ }
        }

        thread {
            try {
                ensureRehberData()
                val classes=rehberCache.listStudents().map{it.className.trim()}.filter{it.isNotBlank()&&scopeOk(it)}.distinct()
                    .sortedWith(compareBy<String>({gradeOf(it)},{classSig(it).substringAfter("/","")},{it}))
                runOnUiThread {
                    val options=ArrayList<String>();options.add("Sınıf Seçin");options.addAll(classes)
                    classFilter.adapter=ArrayAdapter(this@IzinActivityModern,android.R.layout.simple_spinner_dropdown_item,options)
                    classFilter.setSelection(0,false)
                }
            }catch(_:Exception){ }
        }
        addDebouncedSearch(search){load()}
        list.addView(emptyCard("👥","Sınıf seçin","Öğrenci listesi artık İzin Takip'in eski tablosundan değil Akıllı Rehber'den gelir."))
    }

    private fun showNewPermission(preselected: JSONObject? = null) {'''
s, n = pat.subn(lambda _m: new_block, s, count=1)
if n != 1:
    raise SystemExit("showStudents/showNewPermission marker missing")

start=s.find('    private fun showNewPermission(preselected: JSONObject? = null) {')
if start<0:
    raise SystemExit("showNewPermission start missing")
end=s.find('\n    private fun ',start+10)
if end<0:
    raise SystemExit("showNewPermission end missing")

permission_method = r'''    private fun showNewPermission(preselected: JSONObject? = null) {
        currentNav = "new"; selectNav(currentNav); titleText.text = "İzin Takip • Yeni İzin"
        val page = pageColumn()
        page.addView(hero("Yeni İzin", "Bir veya birden fazla öğrenci seçin; seçilen öğrenciler aynı izin bilgileriyle kaydedilir."))

        val search = input("Öğrenci adı veya okul no")
        val searchButton = primaryButton("ÖĞRENCİ ARA / EKLE", blue)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selectedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val reason = input("Örn. Hastane, ailevi neden")
        val receiver = input("Teslim alan kişi veya veli adı")
        val approval = modernSpinner(listOf("Veli onay şeklini seçin", "Telefon", "SMS", "WhatsApp", "Dilekçe", "Yüz Yüze"))
        val sameDay = modernSpinner(listOf("Dönüş durumunu seçin", "Evet, aynı gün dönecek", "Hayır, dönüş yapmayacak"))
        val note = input("Açıklama / not").apply { minLines = 3; gravity = Gravity.TOP }
        val save = primaryButton("SEÇİLEN ÖĞRENCİLERE İZİN VER", green)
        val selected = linkedMapOf<Long, JSONObject>()

        fun renderSelected() {
            selectedBox.removeAllViews()
            selectedBox.addView(formLabel("Seçilen Öğrenciler (${selected.size})"))
            if(selected.isEmpty()) {
                selectedBox.addView(emptyCard("👥", "Henüz öğrenci seçilmedi", "Yukarıdaki arama alanından öğrenci ekleyebilirsiniz."))
                return
            }
            selected.values.forEach { s ->
                val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                wrap.addView(selectedStudentCard(s))
                val remove = secondaryButton("SEÇİMDEN ÇIKAR", red)
                remove.setOnClickListener {
                    selected.remove(s.optLong("id"))
                    renderSelected()
                }
                wrap.addView(remove, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5); bottomMargin = dp(8) })
                selectedBox.addView(wrap)
            }
        }

        fun addStudent(s: JSONObject) {
            val id=s.optLong("id")
            if(id<=0L) { toast("Öğrenci izin kaydına bağlanamadı."); return }
            selected[id]=s
            if(receiver.text.toString().trim().isBlank() && selected.size==1) {
                receiver.setText(s.optString("authorized_person").ifBlank { s.optString("parent_name") })
            }
            renderSelected()
        }

        page.addView(formLabel("Öğrenci Arama / Çoklu Seçim")); page.addView(search); page.addView(space(8)); page.addView(searchButton)
        page.addView(space(8)); page.addView(results); page.addView(space(8)); page.addView(selectedBox)
        page.addView(space(12)); page.addView(formLabel("İzin Nedeni")); page.addView(reason)
        page.addView(space(8)); page.addView(formLabel("Teslim Alan Kişi / Veli Adı")); page.addView(receiver)
        page.addView(space(8)); page.addView(formLabel("Veli Onay Şekli")); page.addView(approval)
        page.addView(space(8)); page.addView(formLabel("Dönüş Durumu")); page.addView(sameDay)
        page.addView(space(8)); page.addView(formLabel("Açıklama / Not")); page.addView(note)
        page.addView(space(14)); page.addView(save)

        preselected?.let { addStudent(it) } ?: renderSelected()
        showContent(wrapScroll(page))

        searchButton.setOnClickListener {
            val q=search.text.toString().trim()
            if(q.isBlank()) { toast("Öğrenci adı veya okul numarası girin."); return@setOnClickListener }
            results.removeAllViews(); results.addView(emptyText("Akıllı Rehber aranıyor…"))
            thread {
                try {
                    ensureRehberData()
                    val loc=java.util.Locale.forLanguageTag("tr-TR")
                    val qq=q.lowercase(loc)
                    val rows=rehberCache.listStudents()
                        .filter { it.name.lowercase(loc).contains(qq) || it.schoolNo.lowercase(loc).contains(qq) }
                        .take(40)
                    runOnUiThread {
                        results.removeAllViews()
                        if(rows.isEmpty()) results.addView(emptyText("Öğrenci bulunamadı."))
                        rows.forEach { r ->
                            val base=JSONObject()
                                .put("student_no",r.schoolNo)
                                .put("full_name",r.name)
                                .put("class_name",r.className)
                                .put("student_phone",r.phone)
                            val enriched=enrichStudent(base) ?: base
                            val item=studentCard(enriched,true)
                            item.setOnClickListener {
                                thread {
                                    try {
                                        val synced=IzinApi.syncStudentFromDirectory(session,enriched)
                                        runOnUiThread {
                                            addStudent(synced)
                                            results.removeAllViews()
                                            search.setText("")
                                            search.requestFocus()
                                        }
                                    } catch(e:Exception) {
                                        runOnUiThread { toast(e.message ?: "Öğrenci eklenemedi.") }
                                    }
                                }
                            }
                            results.addView(item)
                        }
                    }
                } catch(e:Exception) {
                    runOnUiThread { results.removeAllViews(); results.addView(emptyText(e.message ?: "Arama yapılamadı.")) }
                }
            }
        }

        save.setOnClickListener {
            if(selected.isEmpty()) { toast("En az bir öğrenci seçilmelidir."); return@setOnClickListener }
            if(reason.text.toString().trim().isBlank()) { toast("İzin nedenini yazın."); return@setOnClickListener }
            if(approval.selectedItemPosition==0) { toast("Veli onay şeklini seçin."); return@setOnClickListener }
            if(sameDay.selectedItemPosition==0) { toast("Dönüş durumunu seçin."); return@setOnClickListener }
            save.isEnabled=false
            thread {
                try {
                    val r=IzinApi.createPermissions(
                        session,
                        selected.keys.toList(),
                        reason.text.toString().trim(),
                        receiver.text.toString().trim(),
                        approval.selectedItem.toString(),
                        sameDay.selectedItemPosition==1,
                        note.text.toString().trim()
                    )
                    runOnUiThread { save.isEnabled=true; toast(r.optString("message","İzin oluşturuldu.")); showDashboard() }
                } catch(e:Exception) {
                    runOnUiThread { save.isEnabled=true; toast(e.message ?: "İzin oluşturulamadı.") }
                }
            }
        }
    }
'''

s=s[:start]+permission_method+s[end:]
activity.write_text(s, encoding="utf-8")

a=api.read_text(encoding="utf-8")
a=re.sub(r'private const val UA = "ELAK-Okulum/[0-9.]+ Android"','private const val UA = "ELAK-Okulum/0.9.6 Android"',a)
api.write_text(a, encoding="utf-8")

h=home.read_text(encoding="utf-8")
h=h.replace(
    'val enabled=core.moduleEnabled(m.key);val accent=if(enabled)m.color else muted\n        val bg=if(enabled)tintOnWhite(m.color,.075f) else tintOnWhite(m.color,.025f)',
    'val enabled=core.moduleEnabled(m.key);val accent=if(enabled)m.color else Color.rgb(156,163,175)\n        val bg=if(enabled)tintOnWhite(m.color,.075f) else Color.rgb(246,247,249)'
)
h=h.replace(
    'background=moduleBackground(bg,if(enabled)tint(m.color,.34f) else Color.rgb(190,199,211),!enabled);elevation=if(enabled)dp(2).toFloat() else 0f',
    'background=moduleBackground(bg,if(enabled)tint(m.color,.34f) else Color.rgb(218,223,230),!enabled);elevation=if(enabled)dp(2).toFloat() else 0f;alpha=if(enabled)1f else .42f'
)
h=h.replace(
    'background=rounded(if(enabled)tintOnWhite(m.color,.16f) else Color.rgb(232,236,241),11)',
    'background=rounded(if(enabled)tintOnWhite(m.color,.16f) else Color.rgb(238,240,243),11)'
)
h=re.sub(r'stats\.addView\(mini\("Sürüm","[0-9.]+",blue\)', 'stats.addView(mini("Sürüm","0.9.6",blue)', h)
home.write_text(h,encoding="utf-8")

print("v0.9.6 multi-student permission flow + labeled form patch applied")
