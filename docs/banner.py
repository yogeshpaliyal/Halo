# generate: python3 docs/banner.py banner.svg && rsvg-convert -w 2560 -h 960 banner.svg -o docs/banner.png
import math, sys
W, H = 1280, 480
cx, cy = 1172, 240
span = 150
inner_r, outer_r = 120, 210
palette = ["#FF8A80", "#FFD180", "#A5D6A7", "#80DEEA", "#9FA8DA", "#F48FB1", "#CE93D8", "#FFAB91"]

def slot(r, i, n):
    step = span / n
    a = math.radians(180 + span / 2 - (i + 0.5) * step)
    return cx + r * math.cos(a), cy + r * math.sin(a)

def glyph(kind, x, y, s):
    c = "#FFFFFF"
    if kind == 0: return f'<circle cx="{x}" cy="{y}" r="{s*0.2}" fill="none" stroke="{c}" stroke-width="{s*0.08}"/>'
    if kind == 1: return f'<rect x="{x-s*0.18}" y="{y-s*0.18}" width="{s*0.36}" height="{s*0.36}" rx="{s*0.06}" fill="{c}" fill-opacity="0.9"/>'
    if kind == 2: return f'<path d="M{x-s*0.2},{y+s*0.16} L{x},{y-s*0.2} L{x+s*0.2},{y+s*0.16} Z" fill="{c}" fill-opacity="0.9"/>'
    if kind == 3: return f'<path d="M{x-s*0.2},{y} L{x+s*0.2},{y} M{x},{y-s*0.2} L{x},{y+s*0.2}" stroke="{c}" stroke-width="{s*0.08}" stroke-linecap="round"/>'
    return f'<circle cx="{x}" cy="{y}" r="{s*0.12}" fill="{c}" fill-opacity="0.9"/>'

def icon(x, y, s, color, kind, glow=False):
    out = ""
    if glow:
        out += f'<circle cx="{x}" cy="{y}" r="{s*0.95}" fill="#B9A8FF" fill-opacity="0.28"/>'
    out += f'<rect x="{x-s/2}" y="{y-s/2}" width="{s}" height="{s}" rx="{s*0.3}" fill="{color}"/>'
    out += f'<rect x="{x-s/2}" y="{y-s/2}" width="{s}" height="{s/2}" rx="{s*0.3}" fill="#FFFFFF" fill-opacity="0.12"/>'
    return out + glyph(kind, x, y, s)

def arc(r, width):
    a0, a1 = math.radians(180 - span/2 - 4), math.radians(180 + span/2 + 4)
    x0, y0 = cx + r*math.cos(a0), cy + r*math.sin(a0)
    x1, y1 = cx + r*math.cos(a1), cy + r*math.sin(a1)
    return (f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 0 1 {x1:.1f},{y1:.1f}" fill="none" '
            f'stroke="#1E1B2E" stroke-opacity="0.92" stroke-width="{width}" stroke-linecap="round"/>')

parts = [f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
  <radialGradient id="glow" cx="{cx/W}" cy="0.5" r="0.55">
    <stop offset="0" stop-color="#5B4BB7" stop-opacity="0.55"/>
    <stop offset="1" stop-color="#14121F" stop-opacity="0"/>
  </radialGradient>
  <radialGradient id="bubble" cx="0.5" cy="0.4" r="0.6">
    <stop offset="0" stop-color="#3A3358"/><stop offset="1" stop-color="#14121F"/>
  </radialGradient>
  <linearGradient id="title" x1="0" x2="1">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#D4CAFF"/>
  </linearGradient>
</defs>
<rect width="{W}" height="{H}" fill="#14121F"/>
<rect width="{W}" height="{H}" fill="url(#glow)"/>''']

# Phone edge
parts.append(f'<rect x="{W-48}" y="-40" width="120" height="{H+80}" rx="56" fill="none" stroke="#3A3358" stroke-width="6"/>')
parts.append(arc(inner_r, 68))
parts.append(arc(outer_r, 60))

for i in range(8):
    x, y = slot(outer_r, i, 8)
    parts.append(icon(x, y, 40, palette[(i + 3) % 8], (i + 2) % 5))
sel = 2
for i in range(5):
    if i == sel: continue
    x, y = slot(inner_r, i, 5)
    parts.append(icon(x, y, 48, palette[i], i % 5))
x, y = slot(inner_r, sel, 5)
parts.append(icon(x, y, 60, palette[sel], sel % 5, glow=True))
# label pill
pw, ph = 104, 40
px, py = cx - outer_r - 20 - 16 - pw, y - ph/2
parts.append(f'<rect x="{px}" y="{py}" width="{pw}" height="{ph}" rx="{ph/2}" fill="#F1EDFF"/>')
parts.append(f'<text x="{px+pw/2}" y="{py+27}" text-anchor="middle" font-family="Inter" font-weight="700" font-size="18" fill="#14121F">Notes</text>')

# Bubble
parts.append(f'<circle cx="{cx}" cy="{cy}" r="34" fill="url(#bubble)" stroke="#B9A8FF" stroke-opacity="0.25" stroke-width="2"/>')
parts.append(f'<circle cx="{cx}" cy="{cy}" r="19" fill="none" stroke="#B9A8FF" stroke-width="4"/>')
parts.append(f'<circle cx="{cx}" cy="{cy}" r="6" fill="#FFFFFF"/>')

# Copy
parts.append('<text x="80" y="210" font-family="Inter Display" font-weight="700" font-size="128" letter-spacing="-4" fill="url(#title)">Halo</text>')
parts.append('<text font-family="Inter" font-weight="400" font-size="30" fill="#C9C2E8">'
             '<tspan x="84" y="268">A floating bubble that switches apps</tspan>'
             '<tspan x="84" y="308">with one thumb.</tspan></text>')
cx0 = 84
for label in ["Hold · slide · let go", "Open source", "No ads"]:
    w = 34 + len(label) * 9.4
    parts.append(f'<rect x="{cx0}" y="352" width="{w:.0f}" height="42" rx="21" fill="#B9A8FF" fill-opacity="0.14" stroke="#B9A8FF" stroke-opacity="0.35"/>')
    parts.append(f'<text x="{cx0 + w/2:.0f}" y="379" text-anchor="middle" font-family="Inter" font-weight="600" font-size="18" fill="#E6E0FF">{label}</text>')
    cx0 += w + 12
parts.append('</svg>')
open(sys.argv[1], "w").write("\n".join(parts))
