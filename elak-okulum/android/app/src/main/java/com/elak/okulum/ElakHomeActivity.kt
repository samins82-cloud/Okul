package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.izin.IzinSession
import com.elak.okulum.rehber.RehberActivity81
import com.elak.okulum.rehber.RehberSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class ElakHomeActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 31, 68)
    private val navy2 = Color.rgb(17, 55, 105)
    private val pageBg = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val blue = Color.rgb(37, 99, 235)
    private val cyan = Color.rgb(8, 145, 178)
    private val green = Color.rgb(5, 150, 105)
    private val orange = Color.rgb(234, 88, 12)
    private val purple = Color.rgb(124, 58, 237)
    private val red = Color.rgb(220, 38, 38)

    private lateinit var core: OkulumSession
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout
    private val nav = linkedMapOf<String, TextView>()

    data class Module(val key:String,val name:String,val icon:String,val desc:String,val route:String,val color:Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        core = OkulumSession(this)
        if (!core.isReady) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        buildShell(); showHome()
    }

    private fun buildShell() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy; window.navigationBarColor = Color.WHITE
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        content = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        bottom = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
            setPadding(dp(5),dp(6),dp(5),dp(6)); setBackgroundColor(Color.WHITE); elevation=dp(12).toFloat()
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70)))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->
            val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left,b.top,b.right,b.bottom); i
        }
        WindowInsetsControllerCompat(window,root).apply{isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=true}
        ViewCompat.requestApplyInsets(root); buildBottom()
    }

    private fun buildBottom(){
        addNav("home","⌂","Ana Sayfa",blue){showHome()}
        addNav("notifications","●","Bildirimler",red){showNotifications()}
        addNav("calendar","▣","Takvim",purple){showInfo("Takvim","Sınav, randevu ve okul takvimi burada birleşecek.",purple)}
        addNav("messages","✉","Mesajlar",green){showInfo("Mesajlar","Okul içi mesajlar ve veli iletişimleri burada birleşecek.",green)}
        addNav("profile","●","Profil",orange){showProfile()}
    }

    private fun addNav(key:String,icon:String,label:String,color:Int,action:()->Unit){
        val v=TextView(this).apply{
            text="$icon\n$label";textSize=10.5f;gravity=Gravity.CENTER;setTextColor(muted);setPadding(dp(2),dp(4),dp(2),dp(4));tag=color
            setOnClickListener{selectNav(key);action()}
        }
        nav[key]=v;bottom.addView(v,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f))
    }

    private fun selectNav(key:String){
        nav.forEach{(k,v)->val c=v.tag as Int;val s=k==key;v.setTextColor(if(s)c else muted);v.setTypeface(null,if(s)Typeface.BOLD else Typeface.NORMAL);v.background=rounded(if(s)tint(c,.09f) else Color.TRANSPARENT,14,if(s)tint(c,.18f) else null)}
    }

    private fun showHome(){
        selectNav("home")
        val page=column();page.addView(headerCard());page.addView(space(12));page.addView(statusCard())
        page.addView(section("ELAK Modülleri","Aktif modüller kendi renkleriyle, pasif modüller kilitli ve ayrıştırılmış görünür."));page.addView(moduleGrid())
        page.addView(section("Tek Oturum","Kurum kodu + kullanıcı kodu + şifre ile bir kez giriş"))
        page.addView(infoCard("Akıllı Rehber ve İzin Takip ikinci kez parola istemeden hazırlanır. Öğrenci/veli bilgisi ortak Rehber kaynağından kullanılır.",cyan));show(page)
    }

    private fun headerCard():View=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(17),dp(18),dp(16));background=gradient(navy,navy2,22)
        addView(text(core.orgName.ifBlank{"ELAK Okulum"},12f,Color.rgb(196,215,241),true))
        addView(text("Merhaba, ${core.displayName.ifBlank{core.username}}",23f,Color.WHITE,true).apply{setPadding(0,dp(5),0,0)})
        addView(text("${core.roleName.ifBlank{roleLabel(core.roleKey)}}  •  ${core.orgCode}",12f,Color.rgb(210,224,244),false).apply{setPadding(0,dp(5),0,0)})
    }

    private fun statusCard():View{
        val mods=allModules();val active=mods.count{core.moduleEnabled(it.key)};val box=card(Color.WHITE,blue)
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text("Kurum Modülleri",18f,ink,true));addView(text("Lisans durumuna göre otomatik yönetiliyor",11.5f,muted,false))},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        top.addView(badge("$active / ${mods.size} AKTİF",green));box.addView(top)
        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(14),0,0)}
        stats.addView(mini("Aktif",active.toString(),green),LinearLayout.LayoutParams(0,dp(68),1f));stats.addView(mini("Pasif",(mods.size-active).toString(),muted),LinearLayout.LayoutParams(0,dp(68),1f).apply{marginStart=dp(8)});stats.addView(mini("Sürüm","0.9.3",blue),LinearLayout.LayoutParams(0,dp(68),1f).apply{marginStart=dp(8)});box.addView(stats);return box
    }

    private fun moduleGrid():View{
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val list=allModules();var i=0
        while(i<list.size){val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};repeat(2){col->val m=list.getOrNull(i+col);if(m==null)row.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))else row.addView(moduleCard(m),LinearLayout.LayoutParams(0,dp(134),1f).apply{if(col==1)marginStart=dp(9)})};outer.addView(row,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(134)).apply{if(i>0)topMargin=dp(9)});i+=2};return outer
    }

    private fun moduleCard(m:Module):View{
        val enabled=core.moduleEnabled(m.key);val accent=if(enabled)m.color else muted
        val bg=if(enabled)tintOnWhite(m.color,.075f) else tintOnWhite(m.color,.025f)
        return LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(11),dp(13),dp(10));background=moduleBackground(bg,if(enabled)tint(m.color,.34f) else Color.rgb(190,199,211),!enabled);elevation=if(enabled)dp(2).toFloat() else 0f
            val top=LinearLayout(this@ElakHomeActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            top.addView(TextView(this@ElakHomeActivity).apply{text=m.icon;textSize=23f;gravity=Gravity.CENTER;background=rounded(if(enabled)tintOnWhite(m.color,.16f) else Color.rgb(232,236,241),11);setPadding(dp(7),dp(4),dp(7),dp(4))})
            if(!enabled)top.addView(badge("PASİF",muted),LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{marginStart=dp(7)})
            addView(top);addView(text(m.name,14f,if(enabled)ink else Color.rgb(92,103,118),true).apply{setPadding(0,dp(6),0,0)})
            addView(text(if(enabled)m.desc else "Kurum lisansında kapalı • açıldığında aktifleşir",10.5f,if(enabled)darken(m.color) else Color.rgb(132,143,158),false).apply{setPadding(0,dp(3),0,0);maxLines=2})
            setOnClickListener{if(!enabled)Toast.makeText(this@ElakHomeActivity,"${m.name} kurum lisansında pasif.",Toast.LENGTH_SHORT).show() else openModule(m)}
        }
    }

    private fun openModule(m:Module){
        when(m.key){
            "akilli_rehber"->startActivity(Intent(this,RehberActivity81::class.java))
            "izin_takip"->startActivity(Intent(this,IzinActivity88::class.java))
            else->{val r=m.route.trim();if(r.isBlank())Toast.makeText(this,"${m.name} için yayın yolu henüz tanımlanmadı.",Toast.LENGTH_LONG).show()else{val u=if(r.startsWith("http"))r else "https://elak.mcoaihl.com/${r.trimStart('/')}";startActivity(Intent(this,MainActivity::class.java).apply{putExtra(MainActivity.EXTRA_FORCE_WEB,true);putExtra(MainActivity.EXTRA_START_URL,u)})}}
        }
    }

    private fun allModules():List<Module>{
        val d=linkedMapOf(
            "izin_takip" to Module("izin_takip","İzin Takip","🪪","Öğrenci izin, çıkış ve dönüş işlemleri","https://mcoaihl.com/izin/",Color.rgb(22,163,74)),
            "akilli_rehber" to Module("akilli_rehber","Akıllı Rehber","☎","Öğrenci, veli ve personel iletişim rehberi","https://elak.mcoaihl.com/rehber/",Color.rgb(37,99,235)),
            "yoklama" to Module("yoklama","Online Yoklama","📋","Örgün eğitim, açık lise, DYK ve etkinlik yoklamaları","",Color.rgb(8,145,178)),
            "ders_programi" to Module("ders_programi","Ders Programı","🗓","Günlük ve haftalık ders programları","",Color.rgb(217,119,6)),
            "lgs_yks" to Module("lgs_yks","LGS / YKS","📊","Deneme sonuçları, analiz ve öğrenci takibi","https://elak.mcoaihl.com/deneme-sonuc/",Color.rgb(124,58,237)),
            "ortak" to Module("ortak","Ortak Sınav","📝","Ortak sınav planlama ve analiz","",Color.rgb(225,29,72)),
            "kelebek" to Module("kelebek","Kelebek Sınav","🦋","Sınav salonu ve yerleştirme","/kelebek/core-entry.php",Color.rgb(79,70,229)),
            "sorumluluk" to Module("sorumluluk","Sorumluluk Sınavı","🎯","Sorumluluk sınavı işlemleri","",Color.rgb(185,28,28)),
            "ogretmen" to Module("ogretmen","Öğretmen Nöbet","👩‍🏫","Öğretmen nöbet çizelgeleri","",Color.rgb(15,118,110)),
            "ogrenci" to Module("ogrenci","Öğrenci Nöbet","🧑‍🎓","Öğrenci nöbet planlaması","",Color.rgb(194,65,12))
        )
        val cat=core.catalog();for(i in 0 until cat.length()){val j=cat.optJSONObject(i)?:continue;val k=j.optString("module_key").ifBlank{j.optString("id")};if(k.isBlank())continue;val old=d[k];d[k]=Module(k,j.optString("module_name").ifBlank{j.optString("label")}.ifBlank{old?.name?:k},j.optString("icon").ifBlank{old?.icon?:"🧩"},j.optString("description").ifBlank{j.optString("desc")}.ifBlank{old?.desc?:"ELAK modülü"},j.optString("route").ifBlank{old?.route.orEmpty()},old?.color?:Color.rgb(51,65,85))};return d.values.toList()
    }

    private fun showNotifications(){
        selectNav("notifications")
        val loading=column();loading.addView(infoCard("Kurum Bildirim Merkezi",red,true));loading.addView(section("ELAK CORE","Kurumunuza ve tüm kurumlara yayınlanan aktif duyurular canlı olarak alınıyor."));loading.addView(infoCard("Bildirimler yenileniyor…",red));show(loading)
        thread{
            var arr=core.notifications();var err:String?=null
            try{val state=OkulumCoreApi.resume(core.coreCookie,core.orgCode,core.username);core.saveCore(state);arr=state.announcements}catch(e:Exception){err=e.message}
            runOnUiThread{renderNotifications(arr,err)}
        }
    }

    private fun renderNotifications(arr:JSONArray,error:String?){
        val page=column();page.addView(infoCard("Kurum Bildirim Merkezi",red,true));page.addView(section("ELAK CORE Duyuruları","Merkezi ve kurumunuza özel aktif duyurular"))
        if(!error.isNullOrBlank())page.addView(infoCard("Canlı yenileme yapılamadı; son alınan bildirimler gösteriliyor.",orange))
        if(arr.length()==0)page.addView(infoCard("Şu anda yayınlanmış aktif kurum bildirimi bulunmuyor.",blue))
        for(i in 0 until arr.length()){val a=arr.optJSONObject(i)?:continue;page.addView(notificationCard(a))}
        show(page)
    }

    private fun notificationCard(a:JSONObject):View{
        val level=a.optString("level","info");val c=when(level){"danger"->red;"warning"->orange;"success"->green;else->blue}
        return card(tintOnWhite(c,.045f),c).apply{addView(text(a.optString("title").ifBlank{"Bildirim"},15f,ink,true));addView(text(a.optString("body"),12.5f,Color.rgb(55,65,81),false).apply{setPadding(0,dp(6),0,0)});val meta=listOf(a.optString("created_at"),if(a.optString("created_by")=="super_admin")"ELAK Merkez" else "Kurum").filter{it.isNotBlank()}.joinToString(" • ");if(meta.isNotBlank())addView(text(meta,10.5f,muted,false).apply{setPadding(0,dp(8),0,0)})}
    }

    private fun showInfo(title:String,desc:String,color:Int){val page=column();page.addView(infoCard(title,color,true));page.addView(section(title,desc));page.addView(infoCard("Bu alan CORE merkezi verileriyle geliştirilmeye devam edecek.",color));show(page)}

    private fun showProfile(){selectNav("profile");val page=column();page.addView(infoCard("Profil",orange,true));val box=card(Color.WHITE,orange);box.addView(detail("Ad Soyad",core.displayName.ifBlank{core.username}));box.addView(detail("Kullanıcı Kodu",core.username));box.addView(detail("Kurum",core.orgName.ifBlank{core.orgCode}));box.addView(detail("Kurum Kodu",core.orgCode));box.addView(detail("Rol",core.roleName.ifBlank{roleLabel(core.roleKey)}));box.addView(detail("Lisans Bitiş",core.expiry.ifBlank{"—"}));val logout=Button(this).apply{text="Çıkış Yap";isAllCaps=false;setTextColor(red);background=rounded(Color.WHITE,12,tint(red,.45f));setOnClickListener{AlertDialog.Builder(this@ElakHomeActivity).setMessage("ELAK Okulum oturumu kapatılsın mı?").setNegativeButton("Vazgeç",null).setPositiveButton("Çıkış Yap"){_,_->logout()}.show()}};box.addView(logout,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)).apply{topMargin=dp(14)});page.addView(box);show(page)}

    private fun logout(){core.clear();RehberSession(this).clear();IzinSession(this).clear();startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK));finish()}
    private fun show(child:View){content.removeAllViews();content.addView(ScrollView(this).apply{isFillViewport=true;addView(child)},FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(24))}
    private fun section(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(2),dp(15),dp(2),dp(9));addView(text(t,18f,ink,true));addView(text(s,11.5f,muted,false).apply{setPadding(0,dp(2),0,0)})}
    private fun card(fill:Int=Color.WHITE,accent:Int=blue)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(14),dp(15),dp(14));background=rounded(fill,17,tint(accent,.20f));elevation=dp(1).toFloat();layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(9)}}
    private fun infoCard(v:String,color:Int,title:Boolean=false)=TextView(this).apply{text=v;textSize=if(title)22f else 12.5f;setTextColor(if(title)ink else color);if(title)setTypeface(typeface,Typeface.BOLD);setPadding(dp(16),dp(14),dp(16),dp(14));background=rounded(if(title)Color.WHITE else tintOnWhite(color,.065f),15,tint(color,.18f))}
    private fun mini(l:String,v:String,c:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=rounded(tintOnWhite(c,.075f),12,tint(c,.15f));addView(text(v,19f,c,true).apply{gravity=Gravity.CENTER});addView(text(l,10f,muted,false).apply{gravity=Gravity.CENTER})}
    private fun detail(k:String,v:String)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(5),0,dp(5));addView(text(k,12f,muted,false),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.4f));addView(text(v.ifBlank{"—"},12.5f,ink,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.6f))}
    private fun badge(v:String,c:Int)=TextView(this).apply{text=v;textSize=9.5f;setTextColor(c);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;setPadding(dp(8),dp(5),dp(8),dp(5));background=rounded(tintOnWhite(c,.1f),20)}
    private fun text(v:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=v;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun roleLabel(k:String)=when(k.lowercase()){ "admin"->"Kurum Yöneticisi";"manager"->"İdareci";"teacher"->"Öğretmen";"guidance"->"Rehber Öğretmen";"security"->"Güvenlik";"parent","guardian"->"Veli";else->"Kullanıcı"}
    private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
    private fun rounded(fill:Int,r:Int,stroke:Int?=null)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(fill);cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),stroke)}
    private fun moduleBackground(fill:Int,stroke:Int,dashed:Boolean)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(fill);cornerRadius=dp(17).toFloat();if(dashed)setStroke(dp(1),stroke,dp(5).toFloat(),dp(4).toFloat())else setStroke(dp(1),stroke)}
    private fun gradient(a:Int,b:Int,r:Int)=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(a,b)).apply{cornerRadius=dp(r).toFloat()}
    private fun tint(c:Int,a:Float)=Color.argb((255*a).toInt(),Color.red(c),Color.green(c),Color.blue(c))
    private fun tintOnWhite(c:Int,a:Float):Int{val ia=1f-a;return Color.rgb((255*ia+Color.red(c)*a).toInt(),(255*ia+Color.green(c)*a).toInt(),(255*ia+Color.blue(c)*a).toInt())}
    private fun darken(c:Int)=Color.rgb((Color.red(c)*.72f).toInt(),(Color.green(c)*.72f).toInt(),(Color.blue(c)*.72f).toInt())
    private fun dp(v:Int)=(v*resources.displayMetrics.density+.5f).toInt()
}
