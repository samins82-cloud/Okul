from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
overlay = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerOverlay.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# ---- Incoming caller overlay: pin to the highest usable point on screen ----
s = overlay.read_text(encoding='utf-8')
old_y = 'y = dp(app, 54)'
if old_y not in s:
    raise SystemExit('CallerOverlay y-offset marker missing')
s = s.replace(old_y, 'y = 0', 1)

# Let the overlay use the full screen coordinate space. Android still keeps its own
# protected system UI where required, but no extra application-side top offset remains.
old_flags = '''WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,'''
new_flags = '''WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,'''
if old_flags not in s:
    raise SystemExit('CallerOverlay flags marker missing')
s = s.replace(old_flags, new_flags, 1)
overlay.write_text(s, encoding='utf-8')

# ---- Akıllı Rehber home cards: keep the same style, make them more compact ----
r = activity.read_text(encoding='utf-8')

# Slightly tighter tile inner spacing.
r = r.replace('setPadding(dp(7), dp(8), dp(7), dp(8))', 'setPadding(dp(6), dp(6), dp(6), dp(6))', 1)

# Icon and typography scale down proportionally.
r = r.replace('}, LinearLayout.LayoutParams(dp(40), dp(40)))', '}, LinearLayout.LayoutParams(dp(34), dp(34)))', 1)
r = r.replace('textSize = 14.7f', 'textSize = 13.4f', 1)
r = r.replace('textSize = 9.5f\n                gravity = Gravity.CENTER\n                setTextColor(Color.argb(230, 255, 255, 255))',
              'textSize = 9.0f\n                gravity = Gravity.CENTER\n                setTextColor(Color.argb(230, 255, 255, 255))', 1)

# Card/row height from 118/128dp to 98/108dp.
old_row = '''        row.addView(a, LinearLayout.LayoutParams(0, dp(118), 1f).apply { setMargins(dp(5), 0, dp(7), 0) })
        row.addView(b, LinearLayout.LayoutParams(0, dp(118), 1f).apply { setMargins(dp(7), 0, dp(5), 0) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128)))'''
new_row = '''        row.addView(a, LinearLayout.LayoutParams(0, dp(98), 1f).apply { setMargins(dp(5), 0, dp(7), 0) })
        row.addView(b, LinearLayout.LayoutParams(0, dp(98), 1f).apply { setMargins(dp(7), 0, dp(5), 0) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(108)))'''
if old_row not in r:
    raise SystemExit('Rehber tile row size marker missing')
r = r.replace(old_row, new_row, 1)

# Reduce home page outer whitespace slightly so four rows fit comfortably.
r = r.replace('setPadding(dp(16), dp(13), dp(16), dp(18))', 'setPadding(dp(14), dp(10), dp(14), dp(14))', 1)

# Visible version label.
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.13 · Üst Arayan Kartı & Kompakt Rehber")', r, count=1)
activity.write_text(r, encoding='utf-8')

print('v0.9.13 top caller overlay + compact Akilli Rehber home tiles applied')
