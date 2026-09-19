from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

s = activity.read_text(encoding="utf-8")

pat = re.compile(r'''    private fun showStudents\(\) \{.*?\n    \}\n\n    private fun showNewPermission\(\) \{''', re.S)
new_block = r'''    private fun showStudents() {
        currentNav = "students"; selectNav(currentNav); titleText.text = "İzin Takip • Öğrenciler"
        val page = pageColumn(); page.addView(hero("Öğrenciler", "Ad, okul no veya sınıfla arayın; sınıf filtresi ile listeyi daraltın. Veli bilgileri Akıllı Rehber ile tamamlanır."))
        val search = input("Ad, soyad veya okul no")
        val classFilter = modernSpinner(listOf("Tüm Sınıflar"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val filterInfo = TextView(this).apply {
            text = "Tüm sınıflar"
            textSize = 11.5f
            setTextColor(muted)
            setPadding(dp(4), dp(2), dp(4), dp(5))
        }
        page.addView(search)
        page.addView(space(8))
        page.addView(classFilter)
        page.addView(filterInfo)
        page.addView(space(5))
        page.addView(list)
        showContent(wrapScroll(page))

        var selectedClass = ""
        var serial = 0

        fun classSig(value:String):String {
            val v=value.trim().uppercase(java.util.Locale.forLanguageTag("tr-TR"))
            val grade=Regex("""(?:^|[^0-9])(5|6|7|8|9|10|11|12)(?:[^0-9]|$)""").find(v)?.groupValues?.getOrNull(1).orEmpty()
            val section=Regex("""[/\-\s]([A-ZÇĞİÖŞÜ])(?:\s*ŞUBESİ)?\s*$""").find(v)?.groupValues?.getOrNull(1).orEmpty()
            return if(grade.isNotBlank() && section.isNotBlank()) "$grade/$section" else ""
        }

        fun classMatches(actual:String, wanted:String):Boolean {
            if(wanted.isBlank()) return true
            val a=actual.trim(); val w=wanted.trim()
            if(a.equals(w,true)) return true
            val asig=classSig(a); val wsig=classSig(w)
            if(asig.isNotBlank() && wsig.isNotBlank()) return asig==wsig
            val locale=java.util.Locale.forLanguageTag("tr-TR")
            val an=a.lowercase(locale).replace(Regex("\\s+")," ")
            val wn=w.lowercase(locale).replace(Regex("\\s+")," ")
            return an.contains(wn) || wn.contains(an)
        }

        fun load(query: String) {
            val mySerial = ++serial
            val q=query.trim()
            if (q.isBlank() && selectedClass.isBlank()) {
                list.removeAllViews()
                list.addView(emptyCard("🔎", "Aramaya başlayın veya sınıf seçin", "Öğrenci adı/numarası yazın ya da yukarıdan bir sınıf seçin."))
                filterInfo.text="Tüm sınıflar"
                return
            }
            list.removeAllViews(); list.addView(emptyText("Yükleniyor…"))
            val requestQuery=if(q.isBlank()) selectedClass else q
            thread {
                try {
                    ensureRehberData()
                    val arr = IzinApi.students(session, requestQuery)
                    val filtered=ArrayList<JSONObject>()
                    for(i in 0 until arr.length()) {
                        val student=enrichStudent(arr.optJSONObject(i)) ?: continue
                        if(classMatches(student.optString("class_name"), selectedClass)) filtered.add(student)
                    }
                    runOnUiThread {
                        if (mySerial != serial) return@runOnUiThread
                        list.removeAllViews()
                        filterInfo.text = if(selectedClass.isBlank()) {
                            "${filtered.size} sonuç • Tüm sınıflar"
                        } else {
                            "${filtered.size} sonuç • $selectedClass"
                        }
                        if (filtered.isEmpty()) list.addView(emptyCard("🔎", "Öğrenci bulunamadı", "Arama veya sınıf filtresini değiştirin."))
                        filtered.take(100).forEach { list.addView(studentCard(it, false)) }
                    }
                } catch(e:Exception) {
                    runOnUiThread {
                        if (mySerial == serial) {
                            list.removeAllViews()
                            list.addView(emptyText(e.message ?: "Öğrenciler yüklenemedi."))
                        }
                    }
                }
            }
        }

        classFilter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?, view:View?, position:Int, id:Long) {
                selectedClass=if(position<=0) "" else classFilter.getItemAtPosition(position).toString()
                load(search.text.toString())
            }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }

        thread {
            try {
                ensureRehberData()
                val classes=(rehberCache.listClasses().map{it.name} + rehberCache.listStudents().map{it.className})
                    .map{it.trim()}.filter{it.isNotBlank()}.distinct()
                    .sortedWith(compareBy<String>({ classSig(it).substringBefore('/').toIntOrNull() ?: 99 }, { classSig(it).substringAfter('/', "") }, { it }))
                runOnUiThread {
                    val options=ArrayList<String>(); options.add("Tüm Sınıflar"); options.addAll(classes)
                    classFilter.adapter=ArrayAdapter(this@IzinActivityModern, android.R.layout.simple_spinner_dropdown_item, options)
                    classFilter.setSelection(0, false)
                }
            } catch(_:Exception) { }
        }

        addDebouncedSearch(search) { load(it) }
        list.addView(emptyCard("🔎", "Aramaya başlayın veya sınıf seçin", "Yazdıkça öğrenci sonuçları otomatik görüntülenir."))
    }

    private fun showNewPermission() {'''
s, n = pat.subn(lambda _m: new_block, s, count=1)
if n != 1:
    raise SystemExit("showStudents marker missing")

activity.write_text(s, encoding="utf-8")

a = api.read_text(encoding="utf-8")
a = re.sub(r'private const val UA = "ELAK-Okulum/0\.8\.\d+ Android"', 'private const val UA = "ELAK-Okulum/0.8.16 Android"', a)
api.write_text(a, encoding="utf-8")

print("v0.8.16 class filter patch applied")
