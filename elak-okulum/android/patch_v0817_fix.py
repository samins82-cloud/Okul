from pathlib import Path

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
print('v0.9.7 lunch Kotlin compatibility fix applied')
