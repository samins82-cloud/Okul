from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
incoming = ROOT / 'app/src/main/java/com/elak/okulum/rehber/IncomingCallerActivity.kt'

s = incoming.read_text(encoding='utf-8')

render_pat = re.compile(r'''    private fun render\(\) \{.*?\n    \}\n\n    private fun studentMatchRow''', re.S)

render_new = r'''    private fun render() {
        incomingPhone = intent.getStringExtra("phone").orEmpty()
        val contactType = intent.getStringExtra("contact_type").orEmpty()
        if (contactType == "teacher" || contactType == "staff") {
            renderStaffCaller(contactType)
            return
        }

        val fallbackGuardian = intent.getStringExtra("guardian").orEmpty()
        val fallbackRelationship = intent.getStringExtra("relationship").orEmpty()
        val fallbackStudent = intent.getStringExtra("student").orEmpty()
        val fallbackClass = intent.getStringExtra("class_name").orEmpty()
        val fallbackSchoolNo = intent.getStringExtra("school_no").orEmpty()
        val fallbackStudentId = intent.getLongExtra("student_id", 0L)
        val fallbackHasPhoto = intent.getBooleanExtra("has_photo", false)
        val fallbackPhotoVersion = intent.getLongExtra("photo_version", 0L)

        val all = if (incomingPhone.isNotBlank()) CallerCache(this).lookupAll(incomingPhone) else emptyList()
        val guardianMatches = all.filter { !isStudentRelationship(it.relationship) || it.guardianName.isNotBlank() }
        relatedMatches = (if (guardianMatches.isNotEmpty()) guardianMatches else all)
            .filter { it.studentId > 0L }
            .distinctBy { it.studentId }

        if (relatedMatches.isEmpty() && fallbackStudentId > 0L) {
            relatedMatches = listOf(
                CallerCache.Match(
                    guardianName = fallbackGuardian,
                    relationship = fallbackRelationship,
                    studentName = fallbackStudent,
                    schoolNo = fallbackSchoolNo,
                    className = fallbackClass,
                    studentId = fallbackStudentId,
                    hasPhoto = fallbackHasPhoto,
                    photoVersion = fallbackPhotoVersion,
                    phone = PhoneUtil.normalize(incomingPhone),
                    extraCount = 0
                )
            )
        }

        val primary = relatedMatches.firstOrNull()
        val guardianName = relatedMatches.map { it.guardianName.trim() }.filter { it.isNotBlank() }.distinct().firstOrNull()
            ?: fallbackGuardian.ifBlank { fallbackRelationship.ifBlank { "Veli" } }
        val relationships = relatedMatches.map { relationLabel(it.relationship) }.filter { it.isNotBlank() }.distinct()
        val relationshipText = when {
            relationships.isNotEmpty() -> relationships.joinToString(" / ")
            fallbackRelationship.isNotBlank() -> relationLabel(fallbackRelationship)
            else -> "Veli"
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            setBackgroundColor(navy)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        val summaryCircle = TextView(this).apply {
            text = if (relatedMatches.size > 1) relatedMatches.size.toString() else "☎"
            textSize = if (relatedMatches.size > 1) 21f else 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = circle(Color.rgb(24, 117, 136), Color.WHITE, dp(2))
        }
        head.addView(summaryCircle, LinearLayout.LayoutParams(dp(54), dp(54)))

        val headLabels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        headLabels.addView(TextView(this).apply {
            text = "Gelen Arama"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        headLabels.addView(TextView(this).apply {
            text = if (relatedMatches.size > 1) "${relatedMatches.size} öğrenci eşleşti · aynı veli numarası" else {
                primary?.studentName.orEmpty().ifBlank { fallbackStudent.ifBlank { "Okul rehberinde kayıtlı" } }
            }
            textSize = 10.8f
            setTextColor(Color.rgb(196, 217, 238))
            maxLines = 1
        })
        head.addView(headLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)

        val guardianCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.WHITE, dp(17).toFloat())
        }
        val guardianTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val guardianLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        guardianLabels.addView(TextView(this).apply {
            text = "ARAYAN VELİ"
            textSize = 9.2f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(muted)
        })
        guardianLabels.addView(TextView(this).apply {
            text = guardianName
            textSize = 18.2f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            maxLines = 1
        })
        guardianTop.addView(guardianLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        guardianTop.addView(TextView(this).apply {
            text = relationshipText
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (relationships.any { it == "Anne" }) mother else if (relationships.any { it == "Baba" }) blue else orange)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(Color.rgb(247, 250, 253), dp(14).toFloat())
        })
        guardianCard.addView(guardianTop)

        if (incomingPhone.isNotBlank()) guardianCard.addView(TextView(this).apply {
            text = displayInternational(incomingPhone)
            textSize = 15.3f
            setTextColor(ink)
            setPadding(0, dp(5), 0, dp(7))
        })

        val whatsapp = TextView(this).apply {
            text = "WhatsApp'tan Yaz"
            textSize = 12.3f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(12).toFloat())
            setOnClickListener {
                val n = PhoneUtil.international(incomingPhone)
                if (n.isNotBlank()) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n"))) } catch (_: Exception) { }
            }
        }
        guardianCard.addView(whatsapp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        root.addView(guardianCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (relatedMatches.isNotEmpty()) {
            val studentsCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(7))
                background = rounded(Color.WHITE, dp(17).toFloat())
            }
            studentsCard.addView(TextView(this).apply {
                text = if (relatedMatches.size > 1) "İlişkili Öğrenciler  ·  ${relatedMatches.size}" else "Öğrenci"
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
                setPadding(dp(3), 0, 0, dp(4))
            })
            relatedMatches.forEachIndexed { index, match ->
                studentsCard.addView(studentMatchRow(match))
                if (index < relatedMatches.lastIndex) {
                    studentsCard.addView(View(this).apply { setBackgroundColor(Color.rgb(232, 237, 243)) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            leftMargin = dp(4); rightMargin = dp(4)
                        })
                }
            }
            root.addView(studentsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }

        root.addView(TextView(this).apply {
            text = "✓  Okul rehberinde bulundu"
            textSize = 10.8f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(119, 235, 180))
            setPadding(0, dp(8), 0, dp(7))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val close = TextView(this).apply {
            text = "KAPAT"
            textSize = 11.7f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(100, 116, 139), dp(13).toFloat())
            setOnClickListener {
                CallerCardNotifier.dismiss(this@IncomingCallerActivity)
                finish()
            }
        }
        val open = TextView(this).apply {
            text = "REHBERDE AÇ"
            textSize = 11.7f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(13).toFloat())
            setOnClickListener { openRelatedStudent() }
        }
        actions.addView(close, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        actions.addView(open, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        root.addView(actions)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun renderStaffCaller(type: String) {
        val name = intent.getStringExtra("guardian").orEmpty().ifBlank {
            if (type == "teacher") "Öğretmen" else "Personel"
        }
        val roleTitle = intent.getStringExtra("role_title").orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(navy)
        }

        val head = TextView(this).apply {
            text = if (type == "teacher") "ÖĞRETMEN ARIYOR" else "PERSONEL ARIYOR"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(196, 217, 238))
        }
        root.addView(head)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(Color.WHITE, dp(18).toFloat())
        }
        card.addView(TextView(this).apply {
            text = name
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        card.addView(TextView(this).apply {
            text = (if (type == "teacher") "Branş" else "Görev") + " · " + roleTitle.ifBlank { "Belirtilmemiş" }
            textSize = 11.5f
            setTextColor(if (type == "teacher") blue else orange)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(4))
        })
        card.addView(TextView(this).apply {
            text = displayInternational(incomingPhone)
            textSize = 15.5f
            setTextColor(ink)
            setPadding(0, dp(4), 0, dp(10))
        })
        card.addView(TextView(this).apply {
            text = "WhatsApp'tan Yaz"
            textSize = 12.3f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(12).toFloat())
            setOnClickListener {
                val n = PhoneUtil.international(incomingPhone)
                if (n.isNotBlank()) try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n")))
                } catch (_: Exception) { }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        root.addView(TextView(this).apply {
            text = "KAPAT"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(100, 116, 139), dp(13).toFloat())
            setOnClickListener {
                CallerCardNotifier.dismiss(this@IncomingCallerActivity)
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
            topMargin = dp(10)
        })

        setContentView(root)
    }

    private fun studentMatchRow'''
s, n = render_pat.subn(lambda _m: render_new, s, count=1)
if n != 1:
    raise SystemExit('IncomingCallerActivity render marker missing')

row_pat = re.compile(r'''    private fun studentMatchRow\(match: CallerCache\.Match\): View \{.*?\n    \}\n\n    private fun openRelatedStudent''', re.S)
row_new = r'''    private fun studentMatchRow(match: CallerCache.Match): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(5))
            setOnClickListener { openStudent(match.studentId) }
        }

        val avatar = FrameLayout(this)
        val letter = TextView(this).apply {
            text = match.studentName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
            textSize = 15.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(navy)
            background = circle(Color.rgb(225, 238, 247), Color.TRANSPARENT, 0)
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(Color.TRANSPARENT, Color.TRANSPARENT, 0)
            clipToOutline = true
        }
        avatar.addView(letter, FrameLayout.LayoutParams(dp(42), dp(42)))
        avatar.addView(photo, FrameLayout.LayoutParams(dp(42), dp(42)))
        row.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))

        val token = RehberSession(this).token.orEmpty()
        if (token.isNotBlank() && match.studentId > 0 && (match.hasPhoto || match.photoVersion > 0L)) {
            FastPhotoLoader.load(this, token, match.studentId, match.photoVersion, photo) {
                letter.visibility = View.INVISIBLE
            }
        } else {
            photo.visibility = View.GONE
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(3), 0)
        }
        labels.addView(TextView(this).apply {
            text = match.studentName.ifBlank { "Öğrenci" }
            textSize = 12.8f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        labels.addView(TextView(this).apply {
            text = listOf(match.className, if (match.schoolNo.isNotBlank()) "No: ${match.schoolNo}" else "")
                .filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 9.5f
            setTextColor(muted)
        })
        labels.addView(TextView(this).apply {
            text = relationLabel(match.relationship)
            textSize = 9.4f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(relationColor(match.relationship))
            setPadding(0, dp(1), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(180, 190, 201))
        }, LinearLayout.LayoutParams(dp(24), dp(42)))
        return row
    }

    private fun openRelatedStudent'''
s, n = row_pat.subn(lambda _m: row_new, s, count=1)
if n != 1:
    raise SystemExit('studentMatchRow marker missing')

incoming.write_text(s, encoding='utf-8')
print('v0.9.16 compact incoming caller screen applied')
