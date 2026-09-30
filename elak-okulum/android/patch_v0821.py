from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
cache = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCache.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'
service = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerIdService.kt'
notifier = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCardNotifier.kt'
overlay = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerOverlay.kt'
incoming = ROOT / 'app/src/main/java/com/elak/okulum/rehber/IncomingCallerActivity.kt'

# ---------- CallerCache: teachers/personnel ----------
c = cache.read_text(encoding='utf-8')
c = c.replace('SQLiteOpenHelper(context.applicationContext, "elak_okulum_rehber.db", null, 4)',
              'SQLiteOpenHelper(context.applicationContext, "elak_okulum_rehber.db", null, 5)', 1)

# Extend Match without breaking existing constructor calls.
old = '''        val phone: String,\n        val extraCount: Int\n    )'''
new = '''        val phone: String,\n        val extraCount: Int,\n        val contactType: String = "student",\n        val roleTitle: String = ""\n    )'''
if old not in c:
    raise SystemExit('Match marker missing')
c = c.replace(old, new, 1)

# StaffContact model after Guardian.
guardian_end = '''    data class Guardian(\n        val id: Long,\n        val studentId: Long,\n        val name: String,\n        val relationship: String,\n        val phone: String,\n        val studentName: String,\n        val schoolNo: String,\n        val className: String\n    )\n'''
staff_model = guardian_end + '''\n    data class StaffContact(\n        val id: String,\n        val type: String,\n        val name: String,\n        val roleTitle: String,\n        val unit: String,\n        val phone: String\n    )\n'''
if guardian_end not in c:
    raise SystemExit('Guardian model marker missing')
c = c.replace(guardian_end, staff_model, 1)

# Add table before announcements.
ann_marker = '''        db.execSQL(\n            """CREATE TABLE announcements('''
staff_table = '''        db.execSQL(\n            """CREATE TABLE staff_contacts(\n                contact_id TEXT PRIMARY KEY,\n                contact_type TEXT NOT NULL DEFAULT 'staff',\n                name TEXT NOT NULL DEFAULT '',\n                role_title TEXT NOT NULL DEFAULT '',\n                unit_name TEXT NOT NULL DEFAULT '',\n                phone TEXT NOT NULL DEFAULT ''\n            )""".trimIndent()\n        )\n        db.execSQL("CREATE INDEX idx_staff_type_name ON staff_contacts(contact_type,name)")\n        db.execSQL("CREATE INDEX idx_staff_phone ON staff_contacts(phone)")\n\n'''
if ann_marker not in c:
    raise SystemExit('announcements table marker missing')
c = c.replace(ann_marker, staff_table + ann_marker, 1)

c = c.replace('listOf("students", "guardians", "callers", "classes", "announcements", "incoming_history", "meta")',
              'listOf("students", "guardians", "callers", "classes", "staff_contacts", "announcements", "incoming_history", "meta")', 1)

# Clear + parse during sync.
old = '''            db.delete("classes", null, null)\n            db.delete("announcements", null, null)\n\n            val studentMap = parseStudentSummaries(db, root)\n            parseClasses(db, root)\n            parseAnnouncements(db, root)'''
new = '''            db.delete("classes", null, null)\n            db.delete("staff_contacts", null, null)\n            db.delete("announcements", null, null)\n\n            val studentMap = parseStudentSummaries(db, root)\n            parseClasses(db, root)\n            parseStaffContacts(db, root)\n            parseAnnouncements(db, root)'''
if old not in c:
    raise SystemExit('replaceFromSync marker missing')
c = c.replace(old, new, 1)

# Parser + query methods before parseClasses.
parse_classes_marker = '    private fun parseClasses(db: SQLiteDatabase, root: JSONObject) {'
methods = r'''    private fun parseStaffContacts(db: SQLiteDatabase, root: JSONObject) {
        fun parseArray(arr: JSONArray?, type: String) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rawId = first(o, "id", "teacher_id", "staff_id", "personnel_id", "employee_id", "uuid")
                val id = type + ":" + rawId.ifBlank { (i + 1).toString() }
                val name = first(o, "name", "full_name", "display_name", "ad_soyad", "adsoyad")
                if (name.isBlank()) continue
                val roleTitle = if (type == "teacher") {
                    first(o, "branch", "branch_name", "subject", "lesson", "brans", "branş", "field", "title")
                } else {
                    first(o, "duty", "job", "position", "title", "role_title", "gorev", "görev", "unvan")
                }
                val unit = first(o, "unit", "department", "birim", "place", "location")
                val phone = PhoneUtil.normalize(first(o, "phone", "mobile", "phone_number", "tel", "telefon"))
                val cv = ContentValues().apply {
                    put("contact_id", id)
                    put("contact_type", type)
                    put("name", name)
                    put("role_title", roleTitle)
                    put("unit_name", unit)
                    put("phone", phone)
                }
                db.insertWithOnConflict("staff_contacts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        parseArray(firstArray(root, "teachers", "teacher_list", "ogretmenler", "öğretmenler"), "teacher")
        parseArray(firstArray(root, "staff", "personnel", "personel", "employees", "calisanlar", "çalışanlar"), "staff")
    }

    fun listStaffContacts(type: String, query: String = ""): List<StaffContact> {
        val out = ArrayList<StaffContact>()
        readableDatabase.rawQuery(
            "SELECT contact_id,contact_type,name,role_title,unit_name,phone FROM staff_contacts WHERE contact_type=? ORDER BY name",
            arrayOf(type)
        ).use { q ->
            while (q.moveToNext()) {
                out.add(StaffContact(q.getString(0).orEmpty(), q.getString(1).orEmpty(), q.getString(2).orEmpty(), q.getString(3).orEmpty(), q.getString(4).orEmpty(), q.getString(5).orEmpty()))
            }
        }
        val needle = query.trim()
        if (needle.isBlank()) return out
        return out.filter { matchesQuery(needle, listOf(it.name, it.roleTitle, it.unit), "", it.phone) }
    }

    fun teacherCount(): Int = countStaff("teacher")
    fun personnelCount(): Int = countStaff("staff")
    private fun countStaff(type: String): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM staff_contacts WHERE contact_type=?", arrayOf(type)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun lookupStaff(rawPhone: String): StaffContact? {
        val key = PhoneUtil.normalize(rawPhone)
        if (key.length < 10) return null
        return readableDatabase.rawQuery(
            "SELECT contact_id,contact_type,name,role_title,unit_name,phone FROM staff_contacts WHERE phone=? LIMIT 1",
            arrayOf(key)
        ).use { q ->
            if (!q.moveToFirst()) null else StaffContact(q.getString(0).orEmpty(), q.getString(1).orEmpty(), q.getString(2).orEmpty(), q.getString(3).orEmpty(), q.getString(4).orEmpty(), q.getString(5).orEmpty())
        }
    }

'''
if parse_classes_marker not in c:
    raise SystemExit('parseClasses marker missing')
c = c.replace(parse_classes_marker, methods + parse_classes_marker, 1)
cache.write_text(c, encoding='utf-8')

# ---------- Rehber home + teacher/personnel card lists ----------
r = activity.read_text(encoding='utf-8')
home_old = '''        tileRow(body,\n            tile(R.drawable.ic_rehber_people, "Öğrenci\\nRehberi", cache.studentCount().toString() + " öğrenci", brightBlue) { showDirectory(false) },\n            tile(R.drawable.ic_rehber_people, "Veli\\nRehberi", cache.guardianCount().toString() + " veli", red) { showDirectory(true) })\n        tileRow(body,\n            tile(R.drawable.ic_rehber_class, "Sınıf\\nListeleri", cache.classCount().toString() + " sınıf", teal) { showClasses() },'''
home_new = '''        tileRow(body,\n            tile(R.drawable.ic_rehber_people, "Öğrenci\\nRehberi", cache.studentCount().toString() + " öğrenci", brightBlue) { showDirectory(false) },\n            tile(R.drawable.ic_rehber_people, "Veli\\nRehberi", cache.guardianCount().toString() + " veli", red) { showDirectory(true) })\n        tileRow(body,\n            tile(R.drawable.ic_rehber_people, "Öğretmenler", cache.teacherCount().toString() + " öğretmen", purple) { showStaffDirectory("teacher") },\n            tile(R.drawable.ic_rehber_people, "Personel", cache.personnelCount().toString() + " personel", orange) { showStaffDirectory("staff") })\n        tileRow(body,\n            tile(R.drawable.ic_rehber_class, "Sınıf\\nListeleri", cache.classCount().toString() + " sınıf", teal) { showClasses() },'''
if home_old not in r:
    raise SystemExit('home tile marker missing')
r = r.replace(home_old, home_new, 1)

showdir_marker = '    private fun showDirectory(guardian: Boolean) {'
staff_ui = r'''    private fun showStaffDirectory(type: String) {
        selectNav("directory")
        val teacher = type == "teacher"
        setHeader(if (teacher) "Öğretmenler" else "Personel", true)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(8))
            setBackgroundColor(light)
        }
        val search = EditText(this).apply {
            hint = if (teacher) "Öğretmen adı veya branş ara…" else "Personel adı veya görev ara…"
            textSize = 13.5f
            isSingleLine = true
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(Color.WHITE, dp(15).toFloat(), soft)
        }
        page.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) })
        val list = ListView(this).apply { divider = null; clipToPadding = false; setPadding(0, 0, 0, dp(14)) }
        page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun render() {
            val rows = cache.listStaffContacts(type, search.text.toString())
            list.adapter = object : BaseAdapter() {
                override fun getCount() = rows.size
                override fun getItem(position: Int) = rows[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val item = rows[position]
                    val card = LinearLayout(this@RehberActivity84).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(11), dp(12), dp(11))
                        background = rounded(Color.WHITE, dp(17).toFloat(), soft)
                    }
                    val top = LinearLayout(this@RehberActivity84).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    val labels = LinearLayout(this@RehberActivity84).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(TextView(this@RehberActivity84).apply {
                        text = item.name
                        textSize = 15.5f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(ink)
                    })
                    labels.addView(TextView(this@RehberActivity84).apply {
                        val prefix = if (teacher) "BRANŞ" else "GÖREV"
                        text = prefix + " · " + item.roleTitle.ifBlank { if (teacher) "Belirtilmemiş" else "Belirtilmemiş" }
                        textSize = 11.7f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(if (teacher) purple else orange)
                        setPadding(0, dp(3), 0, 0)
                    })
                    if (item.unit.isNotBlank()) labels.addView(TextView(this@RehberActivity84).apply {
                        text = item.unit
                        textSize = 10.5f
                        setTextColor(muted)
                        setPadding(0, dp(2), 0, 0)
                    })
                    if (item.phone.isNotBlank()) labels.addView(TextView(this@RehberActivity84).apply {
                        text = PhoneUtil.display(item.phone)
                        textSize = 12f
                        setTextColor(muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                    top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (item.phone.isNotBlank()) {
                        top.addView(TextView(this@RehberActivity84).apply {
                            text = "☎"; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(blue, dp(12).toFloat())
                            setOnClickListener { dial(item.phone) }
                        }, LinearLayout.LayoutParams(dp(45), dp(45)).apply { marginStart = dp(5) })
                        top.addView(TextView(this@RehberActivity84).apply {
                            text = "W"; textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); background = rounded(green, dp(12).toFloat())
                            setOnClickListener { whatsapp(item.phone) }
                        }, LinearLayout.LayoutParams(dp(45), dp(45)).apply { marginStart = dp(5) })
                    }
                    card.addView(top)
                    return FrameLayout(this@RehberActivity84).apply {
                        setPadding(0, dp(4), 0, dp(4))
                        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                }
            }
        }
        var pending: Runnable? = null
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pending?.let { main.removeCallbacks(it) }
                pending = Runnable { render() }
                main.postDelayed(pending!!, 35L)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render()
        swap(page)
    }

'''
if showdir_marker not in r:
    raise SystemExit('showDirectory marker missing')
r = r.replace(showdir_marker, staff_ui + showdir_marker, 1)
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.12 · Öğretmen & Personel Rehberi")', r, count=1)
activity.write_text(r, encoding='utf-8')

# ---------- Incoming calls: resolve teacher/personnel first ----------
s = service.read_text(encoding='utf-8')
old = '''        val cache = CallerCache(this)\n        val matches = cache.lookupAll(phone)\n        val match = matches.firstOrNull()?.copy(extraCount = (matches.size - 1).coerceAtLeast(0))\n        cache.addHistory(phone, match, "Gelen Arama")\n        if (match != null) CallerCardNotifier.show(this, match, phone)'''
new = '''        val cache = CallerCache(this)\n        val staff = cache.lookupStaff(phone)\n        val matches = cache.lookupAll(phone)\n        val match = if (staff != null) {\n            CallerCache.Match(\n                guardianName = staff.name,\n                relationship = if (staff.type == "teacher") "Öğretmen" else "Personel",\n                studentName = "", schoolNo = "", className = "", studentId = 0L,\n                hasPhoto = false, photoVersion = 0L, phone = staff.phone, extraCount = 0,\n                contactType = staff.type, roleTitle = staff.roleTitle\n            )\n        } else matches.firstOrNull()?.copy(extraCount = (matches.size - 1).coerceAtLeast(0))\n        cache.addHistory(phone, match, "Gelen Arama")\n        if (match != null) CallerCardNotifier.show(this, match, phone)'''
if old not in s:
    raise SystemExit('CallerIdService marker missing')
s = s.replace(old, new, 1)
service.write_text(s, encoding='utf-8')

# Notifier: preserve staff match and pass type/title extras.
s = notifier.read_text(encoding='utf-8')
s = s.replace('''        val all = CallerCache(context).lookupAll(phone)\n        val guardianMatches = all.filter {''', '''        val all = if (match.contactType != "student") listOf(match) else CallerCache(context).lookupAll(phone)\n        val guardianMatches = all.filter {''', 1)
s = s.replace('''            putExtra("extra", displayMatch.extraCount)''', '''            putExtra("extra", displayMatch.extraCount)\n            putExtra("contact_type", displayMatch.contactType)\n            putExtra("role_title", displayMatch.roleTitle)''', 1)
s = s.replace('''        val title = if (related.size > 1) {\n            "Gelen Arama · ${related.size} öğrenci eşleşti"\n        } else {\n            "Gelen Arama · " + displayMatch.studentName\n        }''', '''        val title = when {\n            displayMatch.contactType == "teacher" -> "Gelen Arama · Öğretmen"\n            displayMatch.contactType == "staff" -> "Gelen Arama · Personel"\n            related.size > 1 -> "Gelen Arama · ${related.size} öğrenci eşleşti"\n            else -> "Gelen Arama · " + displayMatch.studentName\n        }''', 1)
s = s.replace('''        val content = if (related.size > 1) {''', '''        val content = if (displayMatch.contactType != "student") {\n            displayMatch.guardianName + if (displayMatch.roleTitle.isNotBlank()) " · " + displayMatch.roleTitle else ""\n        } else if (related.size > 1) {''', 1)
notifier.write_text(s, encoding='utf-8')

# Overlay: teacher/personnel labels and extras.
s = overlay.read_text(encoding='utf-8')
s = s.replace('''        val guardian = match.guardianName.ifBlank { match.relationship.ifBlank { "Kayıtlı Veli" } }''', '''        val guardian = match.guardianName.ifBlank { match.relationship.ifBlank { "Kayıtlı Kişi" } }''', 1)
s = s.replace('''            text = match.relationship.ifBlank { "Veli" } + "  ·  " + PhoneUtil.display(phone)''', '''            text = when (match.contactType) {\n                "teacher" -> "Öğretmen" + if (match.roleTitle.isNotBlank()) " · " + match.roleTitle else ""\n                "staff" -> "Personel" + if (match.roleTitle.isNotBlank()) " · " + match.roleTitle else ""\n                else -> match.relationship.ifBlank { "Veli" }\n            } + "  ·  " + PhoneUtil.display(phone)''', 1)
# Avoid a fake student line for staff.
s = s.replace('''        card.addView(TextView(context).apply {\n            text = studentText\n            textSize = 13f''', '''        if (match.contactType == "student") card.addView(TextView(context).apply {\n            text = studentText\n            textSize = 13f''', 1)
s = s.replace('''        })\n\n        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }''', '''        })\n\n        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }''', 1)
s = s.replace('''            putExtra("extra", match.extraCount)''', '''            putExtra("extra", match.extraCount)\n            putExtra("contact_type", match.contactType)\n            putExtra("role_title", match.roleTitle)''', 1)
overlay.write_text(s, encoding='utf-8')

# Incoming full-screen: simple dedicated staff/personnel card before student renderer.
s = incoming.read_text(encoding='utf-8')
render_marker = '''    private fun render() {\n        incomingPhone = intent.getStringExtra("phone").orEmpty()'''
render_repl = '''    private fun render() {\n        incomingPhone = intent.getStringExtra("phone").orEmpty()\n        val contactType = intent.getStringExtra("contact_type").orEmpty()\n        if (contactType == "teacher" || contactType == "staff") {\n            renderStaffCaller(contactType)\n            return\n        }'''
if render_marker not in s:
    raise SystemExit('Incoming render marker missing')
s = s.replace(render_marker, render_repl, 1)

student_row_marker = '    private fun studentMatchRow(match: CallerCache.Match): View {'
staff_render = r'''    private fun renderStaffCaller(type: String) {
        val name = intent.getStringExtra("guardian").orEmpty().ifBlank { if (type == "teacher") "Öğretmen" else "Personel" }
        val roleTitle = intent.getStringExtra("role_title").orEmpty()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(42), dp(22), dp(28))
            setBackgroundColor(navy)
        }
        root.addView(TextView(this).apply {
            text = if (type == "teacher") "👩‍🏫  ÖĞRETMEN ARIYOR" else "👤  PERSONEL ARIYOR"
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = name; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(28), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = (if (type == "teacher") "Branş" else "Görev") + " · " + roleTitle.ifBlank { "Belirtilmemiş" }
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.rgb(173, 220, 255)); setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = displayInternational(incomingPhone); textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, 0, 0, dp(26))
        })
        root.addView(TextView(this).apply {
            text = "WhatsApp'tan Yaz"; textSize = 14f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); background = rounded(green, dp(14).toFloat())
            setOnClickListener { val n = PhoneUtil.international(incomingPhone); if (n.isNotBlank()) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n"))) } catch (_: Exception) {} }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(TextView(this).apply {
            text = "KAPAT"; textSize = 14f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); background = rounded(red, dp(14).toFloat()); setOnClickListener { CallerCardNotifier.dismiss(this@IncomingCallerActivity); finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        setContentView(root)
    }

'''
if student_row_marker not in s:
    raise SystemExit('studentMatchRow marker missing')
s = s.replace(student_row_marker, staff_render + student_row_marker, 1)
incoming.write_text(s, encoding='utf-8')

print('v0.9.12 teacher/personnel directory cards + branch/duty + caller identity applied')
