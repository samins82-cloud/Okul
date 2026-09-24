from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent
activity=ROOT/'app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt'
api=ROOT/'app/src/main/java/com/elak/okulum/izin/IzinApi.kt'
home=ROOT/'app/src/main/java/com/elak/okulum/ElakHomeActivity.kt'
gradle=ROOT/'app/build.gradle.kts'

s=activity.read_text(encoding='utf-8')

nav_old='''            if (can("permission_create")) addNav("new", "+", "Yeni İzin") { showNewPermission() }\n            if (can("security_view")) addNav("security", "✓", "Güvenlik") { showSecurity() }'''
nav_new='''            if (can("permission_create")) addNav("new", "+", "Yeni İzin") { showNewPermission() }\n            if (can("permission_create")) addNav("lunch", "☀", "Öğle") { showLunchPermissions() }\n            if (can("security_view")) addNav("security", "✓", "Güvenlik") { showSecurity() }'''
if nav_old not in s:
    raise SystemExit('navigation marker missing')
s=s.replace(nav_old,nav_new,1)
s=s.replace('textSize = 10.5f; setTextColor(muted)','textSize = 9.7f; setTextColor(muted)',1)

pat=re.compile(r'''    private fun showSecurity\(\) \{.*?\n    \}\n\n    private fun showPermissions\(\)\{''',re.S)
block=r'''    private fun showLunchPermissions() {
        currentNav="lunch"; selectNav(currentNav); titleText.text="İzin Takip • Öğle İzni"
        val page=pageColumn()
        page.addView(hero("Öğle İzinleri", "Dönemlik öğle izinlerini bir kez tanımlayın. Bu izin türünde SMS / WhatsApp bildirimi gönderilmez."))

        val info=card(accent=cyan)
        info.addView(cardHeading("☀ Sürekli Öğle İzni"))
        info.addView(bodyText("Öğrenci için tarih aralığı, geçerli günler ve öğle saatleri tanımlanır. Güvenlik her gün yalnız gerçek çıkış ve dönüşü kaydeder."))
        page.addView(info)

        val search=input("Öğrenci adı veya okul no")
        val searchButton=primaryButton("ÖĞRENCİ ARA / ÖĞLE İZNİ EKLE",blue)
        val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        page.addView(formLabel("Yeni Öğle İzni"));page.addView(search);page.addView(space(8));page.addView(searchButton);page.addView(space(8));page.addView(results)
        page.addView(sectionTitle("Tanımlı Öğle İzinleri","Aktif veya geçici olarak durdurulmuş öğrenciler"));page.addView(list)
        showContent(wrapScroll(page))

        fun loadList(){
            list.removeAllViews();list.addView(emptyText("Öğle izinleri yükleniyor…"))
            thread{
                try{
                    val arr=IzinApi.lunchPermissions(session)
                    runOnUiThread{
                        list.removeAllViews()
                        if(arr.length()==0)list.addView(emptyCard("☀","Henüz öğle izni yok","Yukarıdan öğrenci seçerek ilk öğle iznini tanımlayın."))
                        for(i in 0 until arr.length()){
                            val row=arr.optJSONObject(i)?:continue
                            list.addView(lunchPermissionCard(row))
                        }
                    }
                }catch(e:Exception){runOnUiThread{list.removeAllViews();list.addView(emptyText(e.message?:"Öğle izinleri yüklenemedi."))}}
            }
        }

        searchButton.setOnClickListener{
            val q=search.text.toString().trim()
            if(q.isBlank()){toast("Öğrenci adı veya okul numarası girin.");return@setOnClickListener}
            results.removeAllViews();results.addView(emptyText("Akıllı Rehber aranıyor…"))
            thread{
                try{
                    ensureRehberData()
                    val loc=java.util.Locale.forLanguageTag("tr-TR")
                    val qq=q.lowercase(loc)
                    val rows=rehberCache.listStudents().filter{
                        it.name.lowercase(loc).contains(qq)||it.schoolNo.lowercase(loc).contains(qq)
                    }.take(40)
                    runOnUiThread{
                        results.removeAllViews()
                        if(rows.isEmpty())results.addView(emptyText("Öğrenci bulunamadı."))
                        rows.forEach{r->
                            val base=JSONObject().put("student_no",r.schoolNo).put("full_name",r.name).put("class_name",r.className).put("student_phone",r.phone)
                            val enriched=enrichStudent(base)?:base
                            val item=studentCard(enriched,true)
                            item.setOnClickListener{
                                thread{
                                    try{
                                        val synced=IzinApi.syncStudentFromDirectory(session,enriched)
                                        runOnUiThread{showLunchEditor(synced,null)}
                                    }catch(e:Exception){runOnUiThread{toast(e.message?:"Öğrenci eşitlenemedi.")}}
                                }
                            }
                            results.addView(item)
                        }
                    }
                }catch(e:Exception){runOnUiThread{results.removeAllViews();results.addView(emptyText(e.message?:"Arama yapılamadı."))}}
            }
        }
        loadList()
    }

    private fun showLunchEditor(student:JSONObject, existing:JSONObject?) {
        currentNav="lunch";selectNav(currentNav);titleText.text="İzin Takip • Öğle İzni Tanımla"
        val page=pageColumn()
        page.addView(hero(if(existing==null)"Yeni Öğle İzni" else "Öğle İznini Düzenle","Bu izin tekrar eden öğle çıkışı içindir; SMS gönderimi kapalıdır."))
        val who=card(accent=cyan)
        who.addView(cardHeading(student.optString("full_name")))
        who.addView(bodyText("${student.optString("class_name")} • No: ${student.optString("student_no")}"))
        who.addView(detailRow("Bildirim","SMS / WhatsApp gönderilmez"))
        page.addView(who)

        val start=input("Başlangıç tarihi: YYYY-AA-GG")
        val end=input("Bitiş tarihi: YYYY-AA-GG")
        val exitTime=input("Çıkış saati: 12:00")
        val returnTime=input("Dönüş saati: 13:00")
        val note=input("Açıklama / veli onayı notu").apply{minLines=2;gravity=Gravity.TOP}
        val dayWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val dayDefs=listOf(1 to "Pzt",2 to "Sal",3 to "Çar",4 to "Per",5 to "Cum")
        val checks=linkedMapOf<Int,CheckBox>()
        val r1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val r2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        dayDefs.forEachIndexed{idx,(day,label)->
            val c=CheckBox(this).apply{text=label;isChecked=true;textSize=12f;setTextColor(text)}
            checks[day]=c
            (if(idx<3)r1 else r2).addView(c,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        }
        dayWrap.addView(r1);dayWrap.addView(r2)

        val today=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date())
        val cal=java.util.Calendar.getInstance()
        val schoolEndYear=if(cal.get(java.util.Calendar.MONTH)>=7)cal.get(java.util.Calendar.YEAR)+1 else cal.get(java.util.Calendar.YEAR)
        start.setText(existing?.optString("start_date")?.takeIf{it.isNotBlank()}?:today)
        end.setText(existing?.optString("end_date")?.takeIf{it.isNotBlank()}?:String.format(java.util.Locale.US,"%04d-06-18",schoolEndYear))
        exitTime.setText(existing?.optString("exit_time")?.take(5)?.takeIf{it.isNotBlank()}?:"12:00")
        returnTime.setText(existing?.optString("return_time")?.take(5)?.takeIf{it.isNotBlank()}?:"13:00")
        note.setText(existing?.optString("note").orEmpty())
        val existingDays=existing?.optString("weekdays").orEmpty().split(',').mapNotNull{it.trim().toIntOrNull()}.toSet()
        if(existingDays.isNotEmpty())checks.forEach{(d,c)->c.isChecked=d in existingDays}

        page.addView(formLabel("Geçerlilik Tarihleri"));page.addView(start);page.addView(space(7));page.addView(end)
        page.addView(space(10));page.addView(formLabel("Geçerli Günler"));page.addView(dayWrap)
        page.addView(space(8));page.addView(formLabel("Öğle Saatleri"));page.addView(exitTime);page.addView(space(7));page.addView(returnTime)
        page.addView(space(8));page.addView(formLabel("Açıklama / Veli Onayı Notu"));page.addView(note)
        val noSms=infoBox("Bu izin türünde çıkış sırasında veliye SMS veya WhatsApp gönderilmez.",green)
        page.addView(space(10));page.addView(noSms)
        val save=primaryButton(if(existing==null)"ÖĞLE İZNİNİ TANIMLA" else "DEĞİŞİKLİKLERİ KAYDET",green)
        val back=secondaryButton("ÖĞLE İZİNLERİNE DÖN",navy)
        page.addView(space(10));page.addView(save);page.addView(space(7));page.addView(back)
        showContent(wrapScroll(page))

        back.setOnClickListener{showLunchPermissions()}
        save.setOnClickListener{
            val days=checks.filterValues{it.isChecked}.keys.joinToString(",")
            if(days.isBlank()){toast("En az bir gün seçin.");return@setOnClickListener}
            if(start.text.toString().trim().isBlank()||end.text.toString().trim().isBlank()){toast("Başlangıç ve bitiş tarihlerini girin.");return@setOnClickListener}
            save.isEnabled=false
            thread{
                try{
                    val r=IzinApi.lunchSave(session,existing?.optLong("id")?:0L,student.optLong("id"),start.text.toString().trim(),end.text.toString().trim(),days,exitTime.text.toString().trim(),returnTime.text.toString().trim(),note.text.toString().trim())
                    runOnUiThread{save.isEnabled=true;toast(r.optString("message","Öğle izni kaydedildi."));showLunchPermissions()}
                }catch(e:Exception){runOnUiThread{save.isEnabled=true;toast(e.message?:"Öğle izni kaydedilemedi.")}}
            }
        }
    }

    private fun lunchPermissionCard(p:JSONObject):View {
        val paused=p.optString("status")=="paused"
        val skipped=p.optInt("today_skipped")==1
        val accent=if(paused)muted else cyan
        val box=card(accent=accent)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(initials(p.optString("full_name"),accent))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        info.addView(cardHeading(p.optString("full_name")));info.addView(bodyText("${p.optString("class_name")} • No: ${p.optString("student_no")}"))
        head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        head.addView(badge(if(paused)"DURDURULDU" else if(skipped)"BUGÜN YOK" else "AKTİF",accent));box.addView(head)
        box.addView(divider())
        box.addView(detailRow("Dönem","${p.optString("start_date")} → ${p.optString("end_date")}"))
        box.addView(detailRow("Günler",lunchDays(p.optString("weekdays"))))
        box.addView(detailRow("Saat","${p.optString("exit_time").take(5)} → ${p.optString("return_time").take(5)}"))
        box.addView(detailRow("SMS","Gönderilmez"))
        if(p.optString("note").isNotBlank())box.addView(detailRow("Not",p.optString("note")))

        val edit=secondaryButton("DÜZENLE",blue)
        edit.setOnClickListener{
            val student=JSONObject().put("id",p.optLong("student_id")).put("full_name",p.optString("full_name")).put("student_no",p.optString("student_no")).put("class_name",p.optString("class_name"))
            showLunchEditor(student,p)
        }
        box.addView(space(8));box.addView(edit)

        if(!paused){
            val skip=secondaryButton(if(skipped)"BUGÜN TEKRAR KULLANABİLİR" else "BUGÜN KULLANMAYACAK",orange)
            skip.setOnClickListener{runAction({if(skipped)IzinApi.lunchUnskipToday(session,p.optLong("id")) else IzinApi.lunchSkipToday(session,p.optLong("id"))}){showLunchPermissions()}}
            box.addView(space(6));box.addView(skip)
        }
        val pause=secondaryButton(if(paused)"YENİDEN AKTİF ET" else "GEÇİCİ DURDUR",if(paused)green else purple)
        pause.setOnClickListener{runAction({IzinApi.lunchStatus(session,p.optLong("id"),if(paused)"active" else "paused")}){showLunchPermissions()}}
        box.addView(space(6));box.addView(pause)
        val cancel=secondaryButton("ÖĞLE İZNİNİ İPTAL ET",red)
        cancel.setOnClickListener{confirm("Bu öğrencinin sürekli öğle izni iptal edilsin mi?"){runAction({IzinApi.lunchStatus(session,p.optLong("id"),"cancelled")}){showLunchPermissions()}}}
        box.addView(space(6));box.addView(cancel)
        return box
    }

    private fun lunchDays(raw:String):String {
        val labels=mapOf(1 to "Pzt",2 to "Sal",3 to "Çar",4 to "Per",5 to "Cum",6 to "Cmt",7 to "Paz")
        return raw.split(',').mapNotNull{labels[it.trim().toIntOrNull()]}.joinToString(" · ").ifBlank{"-"}
    }

    private fun lunchSecurityCard(p:JSONObject,exit:Boolean):View {
        val accent=if(exit)cyan else orange
        val box=card(accent=accent)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(initials(p.optString("full_name"),accent))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        info.addView(cardHeading(p.optString("full_name")));info.addView(bodyText("${p.optString("class_name")} • No: ${p.optString("student_no")}"))
        head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));head.addView(badge(if(exit)"ÖĞLE İZNİ" else "DIŞARIDA",accent));box.addView(head)
        box.addView(divider());box.addView(detailRow("Planlanan Saat","${p.optString("exit_time").take(5)} → ${p.optString("return_time").take(5)}"));box.addView(detailRow("Bildirim","SMS gönderilmez"))
        if(!exit&&p.optString("exited_at").isNotBlank())box.addView(detailRow("Çıkış",shortDate(p.optString("exited_at"))))
        if(p.optString("note").isNotBlank())box.addView(detailRow("Not",p.optString("note")))
        if(canSecurityProcess()){
            val b=primaryButton(if(exit)"ÖĞLE ÇIKIŞI VER" else "DÖNÜŞÜ KAYDET",if(exit)cyan else green)
            b.setOnClickListener{confirm(if(exit)"Öğrencinin öğle çıkışı kaydedilsin mi? SMS gönderilmeyecek." else "Öğrencinin öğle dönüşü kaydedilsin mi?"){
                runAction({if(exit)IzinApi.lunchExit(session,p.optLong("id")) else IzinApi.lunchReturn(session,p.optLong("movement_id"))}){showSecurity()}
            }}
            box.addView(space(10));box.addView(b)
        }
        return box
    }

    private fun showSecurity() {
        currentNav="security"; selectNav(currentNav); titleText.text="İzin Takip • Güvenlik"; setBusy(true)
        thread {
            try {
                val normal=IzinApi.securityQueue(session)
                val lunch=IzinApi.lunchToday(session)
                runOnUiThread { setBusy(false); renderSecurity(normal,lunch) }
            } catch(e:Exception){ runOnUiThread { handleError(e) } }
        }
    }

    private fun renderSecurity(d:JSONObject,lunch:JSONObject){
        val lunchWaiting=lunch.optJSONArray("waiting")?:JSONArray();val lunchReturning=lunch.optJSONArray("returning")?:JSONArray()
        val waiting=d.optJSONArray("waiting")?:JSONArray(); val returning=d.optJSONArray("returning")?:JSONArray(); val page=pageColumn()
        page.addView(hero("Güvenlik Paneli", "Normal izinler ve sürekli öğle izinleri ayrı takip edilir."))

        page.addView(sectionTitle("☀ Öğle İzinliler", "SMS gönderilmez; yalnız gerçek çıkış ve dönüş saati kaydedilir"))
        page.addView(countBanner("Öğle çıkışı bekleyen",lunchWaiting.length(),cyan));page.addView(space(8))
        if(lunchWaiting.length()==0)page.addView(emptyCard("☀","Öğle çıkışı bekleyen yok","Bugün geçerli öğle izinleri burada görünür."))
        for(i in 0 until lunchWaiting.length())lunchWaiting.optJSONObject(i)?.let{page.addView(lunchSecurityCard(it,true))}
        if(lunchReturning.length()>0){
            page.addView(countBanner("Öğle dönüşü bekleyen",lunchReturning.length(),orange));page.addView(space(8))
            for(i in 0 until lunchReturning.length())lunchReturning.optJSONObject(i)?.let{page.addView(lunchSecurityCard(it,false))}
        }

        page.addView(sectionTitle("Normal İzinler", "İdare tarafından tek seferlik oluşturulan izinler"))
        page.addView(countBanner("Çıkış Bekleyen", waiting.length(), purple)); page.addView(space(8))
        if(waiting.length()==0) page.addView(emptyCard("✓","Çıkış bekleyen yok","Yeni izinler burada görünecek."))
        for(i in 0 until waiting.length()) page.addView(securityCard(waiting.optJSONObject(i),true))
        page.addView(sectionTitle("Normal İzin Dönüşleri", "Aynı gün dönüş yapacak öğrenciler")); page.addView(countBanner("Dışarıda / dönüş bekleyen", returning.length(), orange)); page.addView(space(8))
        if(returning.length()==0) page.addView(emptyCard("↩","Dönüş bekleyen yok","Aynı gün dönüşlü öğrenciler burada görünecek."))
        for(i in 0 until returning.length()) page.addView(securityCard(returning.optJSONObject(i),false))
        showContent(wrapScroll(page))
    }

    private fun showPermissions(){'''

s,n=pat.subn(block,s,count=1)
if n!=1:
    raise SystemExit('showSecurity marker missing')
activity.write_text(s,encoding='utf-8')

a=api.read_text(encoding='utf-8')
marker='''    fun securityExit(session:IzinSession,permissionId:Long):JSONObject=requestJson(session,"security_exit",method="POST",body=JSONObject().put("id",permissionId))'''
api_methods=r'''    fun lunchPermissions(session:IzinSession):JSONArray=requestJson(session,"lunch_list").optJSONArray("permissions")?:JSONArray()

    fun lunchSave(session:IzinSession,id:Long,studentId:Long,startDate:String,endDate:String,weekdays:String,exitTime:String,returnTime:String,note:String):JSONObject=
        requestJson(session,"lunch_save",method="POST",body=JSONObject()
            .put("id",id).put("student_id",studentId).put("start_date",startDate).put("end_date",endDate)
            .put("weekdays",weekdays).put("exit_time",exitTime).put("return_time",returnTime).put("note",note))

    fun lunchStatus(session:IzinSession,id:Long,status:String):JSONObject=requestJson(session,"lunch_status",method="POST",body=JSONObject().put("id",id).put("status",status))
    fun lunchSkipToday(session:IzinSession,id:Long):JSONObject=requestJson(session,"lunch_skip_today",method="POST",body=JSONObject().put("id",id))
    fun lunchUnskipToday(session:IzinSession,id:Long):JSONObject=requestJson(session,"lunch_unskip_today",method="POST",body=JSONObject().put("id",id))
    fun lunchToday(session:IzinSession):JSONObject=requestJson(session,"lunch_today")
    fun lunchExit(session:IzinSession,id:Long):JSONObject=requestJson(session,"lunch_exit",method="POST",body=JSONObject().put("id",id))
    fun lunchReturn(session:IzinSession,movementId:Long):JSONObject=requestJson(session,"lunch_return",method="POST",body=JSONObject().put("movement_id",movementId))

    fun securityExit(session:IzinSession,permissionId:Long):JSONObject=requestJson(session,"security_exit",method="POST",body=JSONObject().put("id",permissionId))'''
if marker not in a:
    raise SystemExit('IzinApi securityExit marker missing')
a=a.replace(marker,api_methods,1)
a=re.sub(r'private const val UA = "ELAK-Okulum/[0-9.]+ Android"','private const val UA = "ELAK-Okulum/0.9.7 Android"',a)
api.write_text(a,encoding='utf-8')

h=home.read_text(encoding='utf-8')
h=re.sub(r'stats\.addView\(mini\("Sürüm","[0-9.]+",blue\)', 'stats.addView(mini("Sürüm","0.9.7",blue)', h)
home.write_text(h,encoding='utf-8')

g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 43',g)
g=re.sub(r'versionName\s*=\s*"[0-9.]+"','versionName = "0.9.7"',g)
gradle.write_text(g,encoding='utf-8')

print('v0.9.7 recurring lunch permission patch applied')
