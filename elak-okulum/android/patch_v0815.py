from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

s = activity.read_text(encoding="utf-8")

# Ensure Rehber data is actually available before student results are enriched.
s = s.replace(
    '                    val arr = IzinApi.students(session, query)',
    '                    ensureRehberData()\n                    val arr = IzinApi.students(session, query)',
    1
)
s = s.replace(
    '                    val arr=IzinApi.students(session,query)',
    '                    ensureRehberData()\n                    val arr=IzinApi.students(session,query)',
    1
)

# Add a synchronous, throttled Rehber refresh for background search threads.
marker = '    private fun syncRehberSilently(){\n'
helper = '''    @Volatile private var rehberEnsureAt:Long=0L

    private fun ensureRehberData(){
        val now=System.currentTimeMillis()
        val rs=RehberSession(this)
        val cacheReady=try{rehberCache.studentCount()>0}catch(_:Exception){false}
        if(cacheReady && now-rs.lastSync<120000L)return
        if(now-rehberEnsureAt<15000L && cacheReady)return
        rehberEnsureAt=now
        try{
            var token=rs.token.orEmpty()
            var payload:JSONObject?=null
            if(token.isNotBlank()){
                try{payload=RehberApi.sync(token)}catch(_:Exception){token=""}
            }
            if(payload==null){
                val ok=OkulumSession(this)
                if(ok.username.isNotBlank()&&ok.password.isNotBlank()){
                    val login=RehberApi.login(ok.username,ok.password)
                    token=login.token; rs.token=token; rs.username=ok.username
                    payload=RehberApi.sync(token)
                }
            }
            payload?.let{fresh->rehberCache.replaceFromSync(fresh);rs.lastSync=System.currentTimeMillis()}
        }catch(_:Exception){}
    }

'''
if marker not in s:
    raise SystemExit('syncRehberSilently marker missing')
s = s.replace(marker, helper + marker, 1)

# Replace phone/contact matching. First resolve the actual Rehber student, then use its internal ID.
pat = re.compile(r'''    private fun enrichStudent\(raw:JSONObject\?\):JSONObject\?\{.*?\n    \}\n\n    private fun isToday''', re.S)
new_enrich = '''    private fun enrichStudent(raw:JSONObject?):JSONObject?{
        if(raw==null)return null
        val s=JSONObject(raw.toString())
        val no=s.optString("student_no").trim()
        val fullName=s.optString("full_name").trim()
        val className=s.optString("class_name").trim()
        try{
            fun noKey(v:String):String{
                val digits=v.filter{it.isDigit()}
                if(digits.isBlank())return v.trim().lowercase(java.util.Locale.forLanguageTag("tr-TR"))
                return digits.trimStart('0').ifBlank{"0"}
            }
            val allStudents=rehberCache.listStudents()
            val local=when{
                no.isNotBlank()->allStudents.firstOrNull{it.schoolNo.trim()==no}
                    ?: allStudents.firstOrNull{noKey(it.schoolNo)==noKey(no)}
                else->null
            } ?: allStudents.firstOrNull{
                fullName.isNotBlank() && it.name.equals(fullName,true) &&
                    (className.isBlank() || it.className.equals(className,true))
            }

            if(local!=null){
                if(s.optString("full_name").isBlank())s.put("full_name",local.name)
                if(s.optString("class_name").isBlank())s.put("class_name",local.className)
            }

            val gs=if(local!=null) rehberCache.guardiansForStudent(local.id) else emptyList()
            val ordered=gs.sortedWith(compareBy<CallerCache.Guardian>{
                val r=it.relationship.lowercase(java.util.Locale.forLanguageTag("tr-TR"))
                when { r.contains("veli") -> 0; r.contains("anne") -> 1; r.contains("baba") -> 2; else -> 3 }
            }.thenBy{it.name})

            val contacts=JSONArray(); val seen=linkedSetOf<String>()
            fun addContact(name:String,relationship:String,phoneRaw:String){
                val phone=phoneRaw.trim(); if(phone.isBlank())return
                val digits=phone.filter{it.isDigit()}
                val key=if(digits.length>=10)digits.takeLast(10) else digits.ifBlank{phone}
                if(!seen.add(key))return
                contacts.put(JSONObject().put("name",name).put("relationship",relationship.ifBlank{"İletişim"}).put("phone",phone))
            }
            ordered.forEach{addContact(it.name,it.relationship,it.phone)}
            if(local!=null && local.phone.isNotBlank())addContact(local.name,"Öğrenci",local.phone)

            // Fallback: keep any phone already present in Izin Takip even when Rehber match is absent.
            val existing=s.optString("parent_phone")
            if(existing.isNotBlank())addContact(s.optString("parent_name"),"Veli",existing)

            val primary=ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("veli",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("anne",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank() && it.relationship.contains("baba",true)}
                ?: ordered.firstOrNull{it.phone.isNotBlank()}

            if(primary!=null){
                if(primary.name.isNotBlank())s.put("parent_name",primary.name)
                s.put("parent_phone",primary.phone)
                if(s.optString("authorized_person").isBlank()&&primary.name.isNotBlank())s.put("authorized_person",primary.name)
            }else if(local!=null && local.phone.isNotBlank() && s.optString("parent_phone").isBlank()){
                s.put("parent_name",local.name.ifBlank{"Öğrenci"})
                s.put("parent_phone",local.phone)
            }

            if(contacts.length()>0){
                s.put("rehber_contacts",contacts)
                s.put("contact_phones",(0 until contacts.length()).mapNotNull{contacts.optJSONObject(it)?.optString("phone")?.takeIf{p->p.isNotBlank()}}.joinToString(";"))
                if(local!=null || ordered.isNotEmpty())s.put("rehber_source",1)
            }
            if(local!=null)s.put("rehber_student_id",local.id)
        }catch(_:Exception){}
        return s
    }

    private fun isToday'''
s, n = pat.subn(new_enrich, s, count=1)
if n != 1:
    raise SystemExit('enrichStudent marker missing')

activity.write_text(s, encoding='utf-8')

a = api.read_text(encoding='utf-8')
a = re.sub(r'private const val UA = "ELAK-Okulum/0\.8\.\d+ Android"', 'private const val UA = "ELAK-Okulum/0.8.15 Android"', a)
api.write_text(a, encoding='utf-8')

print('v0.8.15 Rehber phone matching fix applied')
