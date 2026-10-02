from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

r = activity.read_text(encoding='utf-8')

# --- Home: remove duplicate ELAK Mobil heading, keep only compact profile/status area ---
home_pat = re.compile(r'''    private fun buildHome\(\): View \{.*?\n    \}\n\n    private fun tile\(''', re.S)
home_new = '''    private fun buildHome(): View {
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(12))
        }

        body.addView(TextView(this).apply {
            val p = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
            text = if (cache.profileRole().isBlank()) p else p + "  ·  " + cache.profileRole()
            textSize = 11.5f
            setTextColor(muted)
            setPadding(dp(2), dp(2), 0, dp(6))
        })

        body.addView(statusStrip())

        tileRow(body,
            tile(R.drawable.ic_rehber_people, "Öğrenci\\nRehberi", cache.studentCount().toString() + " öğrenci", brightBlue) { showDirectory(false) },
            tile(R.drawable.ic_rehber_people, "Veli\\nRehberi", cache.guardianCount().toString() + " veli", red) { showDirectory(true) })
        tileRow(body,
            tile(R.drawable.ic_rehber_people, "Öğretmenler", if (cache.teacherCount() > 0) cache.teacherCount().toString() + " öğretmen" else "Henüz veri yok", purple) { showStaffDirectory("teacher") },
            tile(R.drawable.ic_rehber_people, "Personel", if (cache.personnelCount() > 0) cache.personnelCount().toString() + " personel" else "Henüz veri yok", orange) { showStaffDirectory("staff") })
        tileRow(body,
            tile(R.drawable.ic_rehber_class, "Sınıf\\nListeleri", cache.classCount().toString() + " sınıf", teal) { showClasses() },
            tile(R.drawable.ic_rehber_announcement, "Duyurular", cache.listAnnouncements().size.toString() + " duyuru", orange) { showAnnouncements() })
        tileRow(body,
            tile(R.drawable.ic_rehber_call, "Aramalar", cache.listHistory(200).size.toString() + " arama", purple) { showCalls() },
            tile(R.drawable.ic_rehber_settings, "Ayarlar", "", Color.rgb(117, 140, 168)) { showSettings() })

        scroll.addView(body)
        return scroll
    }

    private fun tile('''
r, n = home_pat.subn(home_new, r, count=1)
if n != 1:
    raise SystemExit('buildHome marker missing')

# --- Tile appearance: one more compact step after v0.9.13 ---
r = r.replace('setPadding(dp(6), dp(6), dp(6), dp(6))', 'setPadding(dp(5), dp(4), dp(5), dp(4))', 1)
r = r.replace('cornerRadius = dp(20).toFloat()', 'cornerRadius = dp(18).toFloat()', 1)
r = r.replace('}, LinearLayout.LayoutParams(dp(34), dp(34)))', '}, LinearLayout.LayoutParams(dp(30), dp(30)))', 1)
r = r.replace('textSize = 13.4f', 'textSize = 12.6f', 1)
r = r.replace('textSize = 9.0f', 'textSize = 8.6f', 1)

old_row = '''        row.addView(a, LinearLayout.LayoutParams(0, dp(98), 1f).apply { setMargins(dp(5), 0, dp(7), 0) })
        row.addView(b, LinearLayout.LayoutParams(0, dp(98), 1f).apply { setMargins(dp(7), 0, dp(5), 0) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(108)))'''
new_row = '''        row.addView(a, LinearLayout.LayoutParams(0, dp(88), 1f).apply { setMargins(dp(4), 0, dp(6), 0) })
        row.addView(b, LinearLayout.LayoutParams(0, dp(88), 1f).apply { setMargins(dp(6), 0, dp(4), 0) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))'''
if old_row not in r:
    raise SystemExit('v0.9.13 tile row marker missing')
r = r.replace(old_row, new_row, 1)

# --- Status: replace one long sentence with a clean 2x2 dashboard ---
status_pat = re.compile(r'''    private fun statusStrip\(\): View \{.*?\n    \}\n\n    private fun showStaffDirectory''', re.S)
status_new = '''    private fun statusStrip(): View {
        val last = if (session.lastSync > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync))
        } else "Henüz yok"
        val callerOpen = isCallScreeningActive()
        val overlayOpen = CallerOverlay.canDraw(this)

        fun cell(label: String, value: String, valueColor: Int, action: (() -> Unit)? = null): View {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(8), dp(7))
                background = rounded(Color.rgb(248, 250, 253), dp(11).toFloat(), soft)
                addView(TextView(this@RehberActivity84).apply {
                    text = label
                    textSize = 8.8f
                    setTextColor(muted)
                    maxLines = 1
                })
                addView(TextView(this@RehberActivity84).apply {
                    text = value
                    textSize = 10.7f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(valueColor)
                    maxLines = 1
                })
                if (action != null) setOnClickListener { action() }
            }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(Color.WHITE, dp(14).toFloat(), soft)
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(
            cell("Arayan Kimliği", if (callerOpen) "Açık" else "Kapalı",
                if (callerOpen) green else red) { requestCallScreeningRole() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
        )
        row1.addView(
            cell("Ekran Kartı", if (overlayOpen) "Açık" else "İzin gerekli",
                if (overlayOpen) green else orange) { requestCallerOverlayPermission() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) }
        )

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(
            cell("Telefon Kaydı", cache.count().toString(), navy),
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
        )
        row2.addView(
            cell("Son Eşitleme", last, navy),
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) }
        )

        box.addView(row1)
        box.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })
        box.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(5))
        return box
    }

    private fun showStaffDirectory'''
r, n = status_pat.subn(status_new, r, count=1)
if n != 1:
    raise SystemExit('statusStrip/showStaffDirectory marker missing')

# Visible version label.
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)',
           'setting(body, "Sürüm", "v0.9.14 · Düzenlenmiş Kompakt Ana Ekran")',
           r, count=1)

activity.write_text(r, encoding='utf-8')
print('v0.9.14 compact home redesign applied')
