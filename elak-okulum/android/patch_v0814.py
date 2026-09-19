from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

s = activity.read_text(encoding="utf-8")

s = s.replace(
    'box.addView(detailRow("Veli",s.optString("parent_name","-").ifBlank{"-"}))\n        box.addView(phoneRow(s.optString("parent_phone")))',
    'box.addView(contactRows(s))'
)

old_sel = '''    private fun selectedStudentCard(raw:JSONObject):View{ val s=enrichStudent(raw) ?: raw; val box=card(accent=green); box.addView(TextView(this).apply{text="✓  Seçilen Öğrenci  •  kaldırmak için dokunun";textSize=12f;setTextColor(green);setTypeface(typeface,Typeface.BOLD)}); box.addView(cardHeading(s.optString("full_name"))); box.addView(bodyText("${s.optString("class_name")} • No: ${s.optString("student_no")}")); box.addView(detailRow("Veli",s.optString("parent_name","-"))); box.addView(phoneRow(s.optString("parent_phone"))); box.addView(detailRow("Teslim Yetkilisi",s.optString("authorized_person","-").ifBlank{"-"})); return box }'''
new_sel = '''    private fun selectedStudentCard(raw:JSONObject):View{
        val s=enrichStudent(raw) ?: raw
        val box=card(accent=green)
        box.addView(TextView(this).apply{text="✓  Seçilen Öğrenci  •  kaldırmak için dokunun";textSize=12f;setTextColor(green);setTypeface(typeface,Typeface.BOLD)})
        box.addView(cardHeading(s.optString("full_name")))
        box.addView(bodyText("${s.optString("class_name")} • No: ${s.optString("student_no")}"))
        box.addView(contactRows(s))
        box.addView(detailRow("Teslim Yetkilisi",s.optString("authorized_person","-").ifBlank{"-"}))
        return box
    }'''
if old_sel not in s:
    raise SystemExit("selectedStudentCard marker missing")
s = s.replace(old_sel, new_sel, 1)

s = s.replace(
    'box.addView(divider()); box.addView(detailRow("Neden",p.optString("reason","-"))); box.addView(detailRow("Veli",p.optString("parent_name","-"))); box.addView(phoneRow(p.optString("parent_phone")));',
    'box.addView(divider()); box.addView(detailRow("Neden",p.optString("reason","-"))); box.addView(contactRows(p));'
)
s = s.replace(
    'if(p.optString("parent_phone").isNotBlank())box.addView(phoneRow(p.optString("parent_phone"))); return box',
    'if(hasContacts(p))box.addView(contactRows(p)); return box'
)
s = s.replace(
    'box.addView(detailRow("Veli",p.optString("parent_name","-").ifBlank{"-"})); box.addView(phoneRow(p.optString("parent_phone"))); box.addView(detailRow("Veli Bildirimi",p.optString("notification_status","-")))',
    'box.addView(contactRows(p)); box.addView(detailRow("Veli Bildirimi",p.optString("notification_status","-")))'
)
s = s.replace(
    'val phone=s.optString("parent_phone"); if(phone.isNotBlank()){box.addView(divider());box.addView(detailRow("Veli",s.optString("parent_name","-")));box.addView(phoneRow(phone))}',
    'if(hasContacts(s)){box.addView(divider());box.addView(contactRows(s))}'
)

marker = '    private fun phoneRow(phone:String):View{'
helper = '''    private fun hasContacts(s:JSONObject):Boolean {
        val arr=s.optJSONArray("rehber_contacts")
        return (arr!=null && arr.length()>0) || s.optString("parent_phone").isNotBlank()
    }

    private fun contactRows(s:JSONObject):View {
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val arr=s.optJSONArray("rehber_contacts")
        if(arr==null || arr.length()==0){
            val phone=s.optString("parent_phone")
            if(phone.isNotBlank()) outer.addView(contactItem(s.optString("parent_name").ifBlank{"İletişim"},"Veli",phone))
            else outer.addView(detailRow("İletişim","Kayıtlı telefon bulunamadı"))
            return outer
        }
        outer.addView(TextView(this).apply{
            text="İletişim Numaraları (${arr.length()})";textSize=11.5f;setTextColor(muted);setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(3),0,dp(4))
        })
        for(i in 0 until arr.length()){
            val c=arr.optJSONObject(i)?:continue
            outer.addView(contactItem(c.optString("name"),c.optString("relationship").ifBlank{"İletişim"},c.optString("phone")))
        }
        return outer
    }

    private fun contactItem(name:String,relationship:String,phone:String):View {
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(9),dp(7),dp(7),dp(7));background=rounded(Color.rgb(248,250,253),10,border)}
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val label=listOf(relationship,name).filter{it.isNotBlank()}.joinToString(" • ").ifBlank{"İletişim"}
        info.addView(TextView(this).apply{text=label;textSize=11f;setTextColor(muted);setTypeface(typeface,Typeface.BOLD)})
        info.addView(TextView(this).apply{text=phone.ifBlank{"-"};textSize=13f;setTextColor(if(phone.isBlank())ink else blue);setTypeface(typeface,Typeface.BOLD);if(phone.isNotBlank())setOnClickListener{dial(phone)}})
        row.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        if(phone.isNotBlank()) row.addView(TextView(this).apply{text="WhatsApp";textSize=11f;setTextColor(green);setTypeface(typeface,Typeface.BOLD);setPadding(dp(8),dp(5),dp(4),dp(5));setOnClickListener{whatsapp(phone)}})
        row.layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(5)}
        return row
    }

'''
if marker not in s:
    raise SystemExit("phoneRow marker missing")
s = s.replace(marker, helper + marker, 1)

pat = re.compile(r'''    private fun enrichStudent\(raw:JSONObject\?\):JSONObject\?\{.*?\n    \}\n\n    private fun isToday''', re.S)
new_enrich = '''    private fun enrichStudent(raw:JSONObject?):JSONObject?{
        if(raw==null)return null
        val s=JSONObject(raw.toString())
        val no=s.optString("student_no").trim(); if(no.isBlank())return s
        try{
            val local=rehberCache.listStudents(no).firstOrNull{it.schoolNo.trim()==no}
            if(local!=null){
                if(s.optString("full_name").isBlank())s.put("full_name",local.name)
                if(s.optString("class_name").isBlank())s.put("class_name",local.className)
            }

            val gs=rehberCache.listGuardians(no).filter{it.schoolNo.trim()==no}
            val ordered=gs.sortedWith(compareBy<CallerCache.Guardian>{
                val r=it.relationship.lowercase(java.util.Locale.forLanguageTag("tr-TR"))
                when { r.contains("veli") -> 0; r.contains("anne") -> 1; r.contains("baba") -> 2; else -> 3 }
            }.thenBy{it.name})

            val contacts=JSONArray(); val seen=linkedSetOf<String>()
            fun addContact(name:String,relationship:String,phoneRaw:String){
                val phone=phoneRaw.trim(); if(phone.isBlank())return
                val key=phone.filter{it.isDigit()}.takeLast(10).ifBlank{phone}
                if(!seen.add(key))return
                contacts.put(JSONObject().put("name",name).put("relationship",relationship.ifBlank{"İletişim"}).put("phone",phone))
            }
            ordered.forEach{addContact(it.name,it.relationship,it.phone)}
            if(local!=null && local.phone.isNotBlank()) addContact(local.name,"Öğrenci",local.phone)

            val primary=ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("veli",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("anne",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("baba",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank()}

            if(primary!=null){
                if(primary.name.isNotBlank())s.put("parent_name",primary.name)
                s.put("parent_phone",primary.phone)
                if(s.optString("authorized_person").isBlank() && primary.name.isNotBlank())s.put("authorized_person",primary.name)
            } else if(local!=null && local.phone.isNotBlank() && s.optString("parent_phone").isBlank()) {
                s.put("parent_name","Öğrenci")
                s.put("parent_phone",local.phone)
            }

            if(contacts.length()>0){
                s.put("rehber_contacts",contacts)
                s.put("contact_phones",(0 until contacts.length()).mapNotNull{contacts.optJSONObject(it)?.optString("phone")?.takeIf{p->p.isNotBlank()}}.joinToString(";"))
                s.put("rehber_source",1)
            }
        }catch(_:Exception){}
        return s
    }

    private fun isToday'''
s, n = pat.subn(new_enrich, s, count=1)
if n != 1:
    raise SystemExit("enrichStudent marker missing")

activity.write_text(s, encoding="utf-8")

a = api.read_text(encoding="utf-8")
a = re.sub(r'private const val UA = "ELAK-Okulum/0\.8\.\d+ Android"', 'private const val UA = "ELAK-Okulum/0.8.14 Android"', a)
api.write_text(a, encoding="utf-8")

print("v0.8.14 all Rehber contacts patch applied")
