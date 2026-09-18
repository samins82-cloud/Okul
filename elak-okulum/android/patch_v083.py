from pathlib import Path

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberActivity82.kt"
cache = ROOT / "app/src/main/java/com/elak/okulum/rehber/CallerCache.kt"
main = ROOT / "app/src/main/java/com/elak/okulum/MainActivity.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


a = activity.read_text(encoding="utf-8")

# Ana sayfa kartlarını küçült, satırlar arasında görünür boşluk bırak.
a = replace_once(
    a,
    '''            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(color, 1.05f), darken(color, .87f))).apply { cornerRadius = dp(22).toFloat() }
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity82).apply { setImageResource(iconRes); imageTintList = ColorStateList.valueOf(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4)) }, LinearLayout.LayoutParams(dp(43), dp(43)))
            addView(TextView(this@RehberActivity82).apply { text = label; textSize = 15.5f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
            if (count.isNotBlank()) addView(TextView(this@RehberActivity82).apply { text = count; textSize = 9.8f; gravity = Gravity.CENTER; setTextColor(Color.argb(230,255,255,255)); setPadding(0,dp(3),0,0) })''',
    '''            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(color, 1.05f), darken(color, .87f))).apply { cornerRadius = dp(19).toFloat() }
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity82).apply { setImageResource(iconRes); imageTintList = ColorStateList.valueOf(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4)) }, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(TextView(this@RehberActivity82).apply { text = label; textSize = 14.2f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
            if (count.isNotBlank()) addView(TextView(this@RehberActivity82).apply { text = count; textSize = 9.2f; gravity = Gravity.CENTER; setTextColor(Color.argb(230,255,255,255)); setPadding(0,dp(2),0,0) })''',
    "tile size"
)

a = replace_once(
    a,
    '''    private fun tileRow(parent: LinearLayout, a: View, b: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(a, weightLp(1f, dp(4), dp(4)))
        row.addView(b, weightLp(1f, dp(4), dp(4)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)))
    }''',
    '''    private fun tileRow(parent: LinearLayout, a: View, b: View) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(a, weightLp(1f, dp(5), dp(5)))
        row.addView(b, weightLp(1f, dp(5), dp(5)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128)))
    }''',
    "tile spacing"
)

# Sabit E avatarı yerine öğrencinin ilk harfi; fotoğraf versiyonu varsa da yükle.
a = replace_once(
    a,
    '''        val letter = TextView(this).apply {
            text = "E"
            textSize = 25f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(19,119,137), dp(27).toFloat())
        }
        val photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = rounded(Color.TRANSPARENT,dp(27).toFloat()) }
        box.addView(letter, FrameLayout.LayoutParams(dp(54),dp(54)))
        box.addView(photo, FrameLayout.LayoutParams(dp(54),dp(54)))
        if (s.hasPhoto) loadPhoto(photo, s.id, s.photoVersion)''',
    '''        val letter = TextView(this).apply {
            text = s.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
            textSize = 23f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(19,119,137), dp(27).toFloat())
        }
        val photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = rounded(Color.TRANSPARENT,dp(27).toFloat()) }
        box.addView(letter, FrameLayout.LayoutParams(dp(54),dp(54)))
        box.addView(photo, FrameLayout.LayoutParams(dp(54),dp(54)))
        if (s.hasPhoto || s.photoVersion > 0L) loadPhoto(photo, s.id, s.photoVersion)''',
    "student photo avatar"
)

# Manuel eşitlemede sürümsüz fotoğraf önbelleğini yenile.
a = replace_once(
    a,
    'val count=cache.replaceFromSync(RehberApi.sync(token)); session.lastSync=System.currentTimeMillis();',
    'val count=cache.replaceFromSync(RehberApi.sync(token)); if(showToast) PhotoStore.invalidateUnversioned(this); session.lastSync=System.currentTimeMillis();',
    "sync photo invalidation"
)

a = a.replace('setting(body,"Sürüm","v0.8.2")', 'setting(body,"Sürüm","v0.8.3")')
activity.write_text(a, encoding="utf-8")

# Sunucunun farklı fotoğraf alan adlarını da fotoğraf var olarak tanı.
c = cache.read_text(encoding="utf-8")
c = replace_once(
    c,
    '''                hasPhoto = firstBool(s, "has_photo", "hasPhoto", "photo"),
                photoVersion = firstLong(s, "photo_version", "photoVersion", "photo_rev"),''',
    '''                hasPhoto = firstBool(s, "has_photo", "hasPhoto", "photo_exists", "photoExists") ||
                    firstLong(s, "photo_version", "photoVersion", "photo_rev", "photoVersionId") > 0L ||
                    first(s, "photo_url", "photoUrl", "photo_path", "photoPath", "photo_file", "photoFile", "photo_hash", "photoHash", "photo_updated_at", "photoUpdatedAt", "avatar").isNotBlank(),
                photoVersion = firstLong(s, "photo_version", "photoVersion", "photo_rev", "photoVersionId"),''',
    "student photo hints"
)
c = replace_once(
    c,
    '''            val hasPhoto = firstBool(studentObj, "has_photo", "hasPhoto") || firstBool(entry, "has_photo", "hasPhoto") || known?.hasPhoto == true
            val photoVersion = firstLong(studentObj, "photo_version", "photoVersion").takeIf { it > 0 }
                ?: firstLong(entry, "photo_version", "photoVersion").takeIf { it > 0 }
                ?: known?.photoVersion ?: 0L''',
    '''            val photoVersion = firstLong(studentObj, "photo_version", "photoVersion", "photo_rev", "photoVersionId").takeIf { it > 0 }
                ?: firstLong(entry, "photo_version", "photoVersion", "photo_rev", "photoVersionId").takeIf { it > 0 }
                ?: known?.photoVersion ?: 0L
            val hasPhoto = firstBool(studentObj, "has_photo", "hasPhoto", "photo_exists", "photoExists") ||
                firstBool(entry, "has_photo", "hasPhoto", "photo_exists", "photoExists") ||
                first(studentObj, "photo_url", "photoUrl", "photo_path", "photoPath", "photo_file", "photoFile", "photo_hash", "photoHash", "photo_updated_at", "photoUpdatedAt", "avatar").isNotBlank() ||
                first(entry, "photo_url", "photoUrl", "photo_path", "photoPath", "photo_file", "photoFile", "photo_hash", "photoHash", "photo_updated_at", "photoUpdatedAt", "avatar").isNotBlank() ||
                photoVersion > 0L || known?.hasPhoto == true''',
    "entry photo hints"
)
cache.write_text(c, encoding="utf-8")

# WebView user-agent sürümünü eşitle.
m = main.read_text(encoding="utf-8")
m = m.replace("ELAK-Okulum/0.8.1 tr-TR", "ELAK-Okulum/0.8.3 tr-TR")
main.write_text(m, encoding="utf-8")

print("v0.8.3 patch applied")
