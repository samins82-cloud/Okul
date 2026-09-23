from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"
home = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"

s = activity.read_text(encoding="utf-8")

pat = re.compile(r'''    private fun showStudents\(\) \{.*?\n    \}\n\n    private fun showNewPermission\(\) \{''', re.S)
new_block = r'''    private var selectedStudent: JSONObject? = null
    private var pendingDirectoryStudent: JSONObject? = null

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
                                        runOnUiThread {
                                            selectedStudent=synced
                                            pendingDirectoryStudent=synced
                                            showNewPermission()
                                        }
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

    private fun showNewPermission() {'''
s, n = pat.subn(lambda _m: new_block, s, count=1)
if n != 1:
    raise SystemExit("showStudents/showNewPermission marker missing")

start=s.find('    private fun showNewPermission() {')
if start<0:
    raise SystemExit("showNewPermission start missing")
end=s.find('\n    private fun ',start+10)
if end<0:end=len(s)
sub=s[start:end]

# Eski UI tabanında showNewPermission içinde yerel selectedStudent tanımı bulunuyor.
# Bu yerel değişken, Öğrenciler ekranından gelen seçimi gölgeliyordu. Yerel tanımı
# kaldırıp sınıf düzeyindeki ortak seçimi kullanıyoruz.
local_decl = re.compile(r'\bvar\s+selectedStudent\s*:\s*JSONObject\?\s*=\s*null\s*;?')
if local_decl.search(sub):
    sub=local_decl.sub('selectedStudent = pendingDirectoryStudent; pendingDirectoryStudent = null',sub,count=1)
elif re.search(r'selectedStudent\s*=\s*null',sub):
    sub=re.sub(r'selectedStudent\s*=\s*null','selectedStudent = pendingDirectoryStudent; pendingDirectoryStudent = null',sub,count=1)
else:
    brace=sub.find('{')
    sub=sub[:brace+1]+'\n        selectedStudent = pendingDirectoryStudent; pendingDirectoryStudent = null'+sub[brace+1:]

needle='        showContent(wrapScroll(page))\n'
prefill='''        selectedStudent?.let { chosen ->
            selectedBox.removeAllViews()
            selectedBox.addView(selectedStudentCard(chosen))
            receiver.setText(chosen.optString("authorized_person").ifBlank { chosen.optString("parent_name") })
        }
'''
pos=sub.find(needle)
if pos<0:
    raise SystemExit("showNewPermission showContent marker missing")
# Seçili öğrenci kartını ekran gösterilmeden önce doldur.
sub=sub[:pos]+prefill+sub[pos:]
s=s[:start]+sub+s[end:]

activity.write_text(s, encoding="utf-8")

a=api.read_text(encoding="utf-8")
a=re.sub(r'private const val UA = "ELAK-Okulum/[0-9.]+ Android"','private const val UA = "ELAK-Okulum/0.9.4 Android"',a)
api.write_text(a, encoding="utf-8")

# Ana ekranda pasif modüller tamamen nötr gri/soluk görünür; modülün kendi rengi
# pasif kartta hiç kullanılmaz. Emoji dahil bütün kartın opaklığı düşürülür.
h=home.read_text(encoding="utf-8")
h=h.replace(
    'val enabled=core.moduleEnabled(m.key);val accent=if(enabled)m.color else muted\n        val bg=if(enabled)tintOnWhite(m.color,.075f) else tintOnWhite(m.color,.025f)',
    'val enabled=core.moduleEnabled(m.key);val accent=if(enabled)m.color else Color.rgb(156,163,175)\n        val bg=if(enabled)tintOnWhite(m.color,.075f) else Color.rgb(246,247,249)'
)
h=h.replace(
    'background=moduleBackground(bg,if(enabled)tint(m.color,.34f) else Color.rgb(190,199,211),!enabled);elevation=if(enabled)dp(2).toFloat() else 0f',
    'background=moduleBackground(bg,if(enabled)tint(m.color,.34f) else Color.rgb(218,223,230),!enabled);elevation=if(enabled)dp(2).toFloat() else 0f;alpha=if(enabled)1f else .48f'
)
h=h.replace(
    'background=rounded(if(enabled)tintOnWhite(m.color,.16f) else Color.rgb(232,236,241),11)',
    'background=rounded(if(enabled)tintOnWhite(m.color,.16f) else Color.rgb(238,240,243),11)'
)
h=h.replace('stats.addView(mini("Sürüm","0.9.3",blue)', 'stats.addView(mini("Sürüm","0.9.4",blue)')
home.write_text(h,encoding="utf-8")

print("v0.9.4 student handoff + fully faded inactive modules patch applied")
