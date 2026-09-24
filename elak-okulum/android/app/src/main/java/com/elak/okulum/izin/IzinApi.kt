package com.elak.okulum.izin

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object IzinApi {
    private const val BASE = "https://www.mcoaihl.com/izin/"
    private const val UA = "ELAK-Okulum/0.9.6 Android"

    data class LoginResult(
        val cookie:String,val csrf:String,val role:String,val fullName:String,val username:String,
        val adminAccess:String,val schoolScope:String,val permissions:List<String>
    )

    fun login(username:String,password:String):LoginResult{
        val body="login=1&username=${enc(username.trim())}&password=${enc(password)}"
        val conn=open(BASE+"index.php","POST",null).apply{instanceFollowRedirects=false;doOutput=true;setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");setRequestProperty("Referer",BASE+"index.php")}
        conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};val code=conn.responseCode;val postText=readText(conn);val cookie=extractCookie(conn);conn.disconnect()
        if(cookie.isBlank())throw IllegalStateException(extractLoginError(postText)?:"İzin Takip oturumu açılamadı.")
        if(code !in 200..399)throw IllegalStateException("İzin Takip giriş hatası (HTTP $code).")
        val result=bootstrap(cookie);if(result.username.isBlank())throw IllegalStateException("İzin Takip kullanıcı bilgisi alınamadı.");return result
    }

    fun bootstrap(cookie:String):LoginResult{
        val conn=open(BASE+"index.php","GET",cookie);val code=conn.responseCode;val html=readText(conn);val refreshed=extractCookie(conn).ifBlank{cookie};conn.disconnect()
        if(code !in 200..299)throw IllegalStateException("İzin Takip oturumu doğrulanamadı (HTTP $code).")
        if(html.contains("name=\"login\"",true)||html.contains("KULLANICI GİRİŞİ",true))throw IllegalStateException("İzin Takip oturumu sona erdi.")
        return parseAppBootstrap(html,refreshed)
    }

    fun dashboard(session:IzinSession):JSONObject{
        val data=requestJson(session,"dashboard")
        try{
            val outside=permissions(session,"","Dışarıda");var today=0
            for(i in 0 until outside.length()){val p=outside.optJSONObject(i)?:continue;if(isToday(p.optString("exited_at")))today++}
            data.optJSONObject("stats")?.put("outside",today)
        }catch(_:Exception){}
        return data
    }

    fun students(session:IzinSession,query:String=""):JSONArray=requestJson(session,"students","&q=${enc(query)}").optJSONArray("students")?:JSONArray()
    fun studentSearch(session:IzinSession,query:String):JSONArray=requestJson(session,"student_search","&q=${enc(query)}").optJSONArray("students")?:JSONArray()
    fun permissions(session:IzinSession,query:String="",status:String=""):JSONArray=requestJson(session,"permissions","&q=${enc(query)}&status=${enc(status)}").optJSONArray("permissions")?:JSONArray()

    fun securityQueue(session:IzinSession):JSONObject{
        val data=requestJson(session,"security_queue");val raw=data.optJSONArray("returning")?:JSONArray();val today=JSONArray()
        for(i in 0 until raw.length()){val row=raw.optJSONObject(i)?:continue;if(isToday(row.optString("exited_at")))today.put(row)}
        data.put("returning",today);return data
    }

    fun syncStudentFromDirectory(session:IzinSession,directoryStudent:JSONObject):JSONObject{
        val no=directoryStudent.optString("student_no").trim();val name=directoryStudent.optString("full_name").trim();val clazz=directoryStudent.optString("class_name").trim()
        if(no.isBlank()||name.isBlank()||clazz.isBlank())throw IllegalStateException("Öğrencinin okul no, ad veya sınıf bilgisi eksik.")
        val rows=students(session,no);var existing:JSONObject?=null
        for(i in 0 until rows.length()){val x=rows.optJSONObject(i)?:continue;if(normalizeNo(x.optString("student_no"))==normalizeNo(no)){existing=x;break}}
        val canManage=session.permissions.contains("students_manage")
        if(existing==null&&!canManage)throw IllegalStateException("Bu öğrenci İzin Takip kayıtlarında yok. Öğrenci eşitleme yetkisi olan bir idareciyle bir kez açılması gerekiyor.")
        var id=existing?.optLong("id")?:0L
        if(canManage){
            val saved=requestForm(session,"student_save",linkedMapOf(
                "csrf" to session.csrf,"id" to if(id>0)id.toString() else "","full_name" to name,"student_no" to no,"class_name" to clazz,
                "school_level" to directoryStudent.optString("school_level").ifBlank{inferLevel(clazz)},"gender" to directoryStudent.optString("gender"),
                "parent_name" to directoryStudent.optString("parent_name"),"parent_phone" to directoryStudent.optString("parent_phone"),
                "authorized_person" to directoryStudent.optString("authorized_person")
            ));id=saved.optLong("id",id)
        }
        if(id<=0L)throw IllegalStateException("Öğrenci İzin Takip kaydı oluşturulamadı.")
        return JSONObject(directoryStudent.toString()).put("id",id)
    }

    fun syncStudentContact(session:IzinSession,student:JSONObject):JSONObject=syncStudentFromDirectory(session,student)

    /**
     * notificationTargets anahtarları öğrenci ID'sidir.
     * Her hedef {name, relationship, phone} içerir. Sunucu bu numarayı izin kaydına
     * sabitler; çıkış SMS'i daha sonra yalnız bu numaraya gönderilir.
     */
    fun createPermissions(
        session:IzinSession,studentIds:List<Long>,reason:String,receiver:String,approvalMethod:String,sameDayReturn:Boolean,note:String,
        notificationTargets:JSONObject=JSONObject(),
        parentName:String="",parentPhone:String="",authorizedPerson:String=""
    ):JSONObject{
        val ids=studentIds.filter{it>0L}.distinct()
        if(ids.isEmpty())throw IllegalArgumentException("En az bir öğrenci seçilmelidir.")
        val arr=JSONArray();ids.forEach{arr.put(it)}
        val body=JSONObject()
            .put("student_ids",arr)
            .put("student_id",ids.first())
            .put("reason",reason)
            .put("receiver",receiver)
            .put("approval_method",approvalMethod)
            .put("same_day_return",if(sameDayReturn)1 else 0)
            .put("note",note)
            .put("notification_targets",notificationTargets)
            .put("parent_name",parentName)
            .put("parent_phone",parentPhone)
            .put("authorized_person",authorizedPerson)
        val action=if(ids.size>1)"permission_bulk_create" else "permission_create"
        return requestJson(session,action,method="POST",body=body)
    }

    fun createPermission(
        session:IzinSession,studentId:Long,reason:String,receiver:String,approvalMethod:String,sameDayReturn:Boolean,note:String,
        notificationName:String="",notificationPhone:String="",
        parentName:String="",parentPhone:String="",authorizedPerson:String=""
    ):JSONObject{
        val target=JSONObject().put(studentId.toString(),JSONObject()
            .put("name",notificationName)
            .put("phone",notificationPhone))
        return createPermissions(
            session,listOf(studentId),reason,receiver,approvalMethod,sameDayReturn,note,target,parentName,parentPhone,authorizedPerson
        )
    }

    fun securityExit(session:IzinSession,permissionId:Long):JSONObject=requestJson(session,"security_exit",method="POST",body=JSONObject().put("id",permissionId))
    fun securityReturn(session:IzinSession,permissionId:Long):JSONObject=requestJson(session,"security_return",method="POST",body=JSONObject().put("id",permissionId))
    fun cancelPermission(session:IzinSession,permissionId:Long):JSONObject=requestJson(session,"permission_cancel",method="POST",body=JSONObject().put("id",permissionId))

    private fun requestJson(session:IzinSession,action:String,query:String="",method:String="GET",body:JSONObject?=null):JSONObject{
        if(!session.isReady)throw IllegalStateException("İzin Takip oturumu bulunamadı.")
        val scope=if(session.role=="admin")"&view_scope=${enc(session.schoolScope)}" else ""
        val conn=open(BASE+"api.php?action=${enc(action)}$query$scope&_=${System.currentTimeMillis()}",method,session.cookie).apply{setRequestProperty("Accept","application/json");if(method!="GET"){doOutput=true;setRequestProperty("Content-Type","application/json; charset=UTF-8");setRequestProperty("X-CSRF-Token",session.csrf)}}
        if(method!="GET"){val payload=JSONObject(body?.toString()?:"{}").put("csrf",session.csrf);conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}}
        return parseJsonResponse(session,conn)
    }

    private fun requestForm(session:IzinSession,action:String,fields:Map<String,String>):JSONObject{
        if(!session.isReady)throw IllegalStateException("İzin Takip oturumu bulunamadı.")
        val scope=if(session.role=="admin")"&view_scope=${enc(session.schoolScope)}" else "";val body=fields.entries.joinToString("&"){"${enc(it.key)}=${enc(it.value)}"}
        val conn=open(BASE+"api.php?action=${enc(action)}$scope&_=${System.currentTimeMillis()}","POST",session.cookie).apply{doOutput=true;setRequestProperty("Accept","application/json");setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");setRequestProperty("X-CSRF-Token",session.csrf)}
        conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};return parseJsonResponse(session,conn)
    }

    private fun parseJsonResponse(session:IzinSession,conn:HttpURLConnection):JSONObject{
        val code=conn.responseCode;val raw=readText(conn);extractCookie(conn).takeIf{it.isNotBlank()}?.let{session.cookie=it};conn.disconnect()
        val json=try{JSONObject(raw.trim().removePrefix("\uFEFF").ifBlank{"{}"})}catch(_:Exception){throw IllegalStateException("Sunucudan geçersiz yanıt alındı.")}
        if(code==401){session.clear();throw IllegalStateException("İzin Takip oturumu sona erdi.")}
        if(code !in 200..299||(json.has("ok")&&!json.optBoolean("ok",false)))throw IllegalStateException(json.optString("message",json.optString("error","İşlem başarısız (HTTP $code).")))
        return json
    }

    private fun parseAppBootstrap(html:String,cookie:String):LoginResult{
        val csrfRaw=findGroup(html,"csrf\\s*:\\s*(\"(?:\\\\.|[^\"])*\")")?:throw IllegalStateException("İzin Takip güvenlik anahtarı alınamadı.")
        val roleRaw=findGroup(html,"role\\s*:\\s*(\"(?:\\\\.|[^\"])*\")")?:"\"\"";val userRaw=findGroup(html,"user\\s*:\\s*(\\{.*?\\})\\s*,\\s*permissions\\s*:")?:"{}";val permissionsRaw=findGroup(html,"permissions\\s*:\\s*(\\[.*?\\])\\s*,\\s*permissionCatalog\\s*:")?:"[]"
        val csrf=JSONObject("{\"v\":$csrfRaw}").optString("v");val role=JSONObject("{\"v\":$roleRaw}").optString("v");val user=JSONObject(userRaw);val arr=JSONArray(permissionsRaw);val permissions=ArrayList<String>(arr.length());for(i in 0 until arr.length())arr.optString(i).trim().takeIf{it.isNotEmpty()}?.let{permissions.add(it)}
        return LoginResult(cookie,csrf,role,user.optString("full_name"),user.optString("username"),user.optString("admin_access","authorized"),user.optString("school_scope","both").ifBlank{"both"},permissions)
    }

    private fun isToday(v:String):Boolean{if(v.length<10)return false;val today=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date());return v.substring(0,10)==today}
    private fun inferLevel(clazz:String):String{val g=Regex("(?:^|[^0-9])(5|6|7|8|9|10|11|12)(?:[^0-9]|$)").find(clazz)?.groupValues?.getOrNull(1)?.toIntOrNull()?:5;return if(g>=9)"high" else "middle"}
    private fun normalizeNo(v:String):String{val d=v.filter{it.isDigit()};return if(d.isBlank())v.trim().lowercase() else d.trimStart('0').ifBlank{"0"}}
    private fun open(url:String,method:String,cookie:String?):HttpURLConnection=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=15000;readTimeout=30000;useCaches=false;setRequestProperty("Accept-Language","tr-TR,tr;q=0.9");setRequestProperty("User-Agent",UA);if(!cookie.isNullOrBlank())setRequestProperty("Cookie",cookie)}
    private fun readText(conn:HttpURLConnection):String{val stream=try{if(conn.responseCode in 200..399)conn.inputStream else conn.errorStream}catch(_:Exception){conn.errorStream}?:return "";return BufferedReader(InputStreamReader(stream,Charsets.UTF_8)).use{it.readText()}}
    private fun extractCookie(conn:HttpURLConnection):String{val values=conn.headerFields.entries.filter{it.key?.equals("Set-Cookie",true)==true}.flatMap{it.value?:emptyList()};return values.firstOrNull{it.startsWith("MCOAIHL_IZIN_SESSID=",true)}?.substringBefore(';')?.trim().orEmpty()}
    private fun extractLoginError(html:String):String?=Regex("<div[^>]*class=\"alert error\"[^>]*>(.*?)</div>",RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"),"")?.trim()
    private fun findGroup(text:String,pattern:String):String?=Regex(pattern,setOf(RegexOption.DOT_MATCHES_ALL)).find(text)?.groupValues?.getOrNull(1)
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
}
