"""Halo Play screenshots in a light 'callout' style: eyebrow + big headline, phone bleeding
off the bottom, zoomed callout cards with soft shadows, handwritten notes, highlighter swooshes.
usage: python3 docs/play_screenshots.py <shots dir with menu/home/settings/bubble.jpg (1080x2424)> <out dir>"""
import base64, math, os, sys

SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
W, H = 1080, 1920
SW, SH = 1080, 2424
INK, QUIET, PURPLE, LAV, BLUE, BG = "#1B1830", "#6B6684", "#5B47D6", "#B9A8FF", "#2F55D4", "#F2F2F5"
HAND = "TeX Gyre Chorus"

_cache = {}
def img(name):
    if name not in _cache:
        with open(os.path.join(SRC, name), "rb") as f:
            _cache[name] = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode()
    return _cache[name]

def head():
    grid = "".join(f'<line x1="{x}" y1="0" x2="{x}" y2="{H}"/>' for x in range(60, W, 60)) + \
           "".join(f'<line x1="0" y1="{y}" x2="{W}" y2="{y}"/>' for y in range(60, H, 60))
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
  <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="22"/><feOffset dy="18" result="b"/>
    <feComponentTransfer><feFuncA type="linear" slope="0.22"/></feComponentTransfer>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="soft" x="-30%" y="-30%" width="160%" height="160%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="10"/><feOffset dy="8" result="b"/>
    <feComponentTransfer><feFuncA type="linear" slope="0.18"/></feComponentTransfer>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
</defs>
<rect width="{W}" height="{H}" fill="{BG}"/>
<g stroke="#E6E6EC" stroke-width="2">{grid}</g>'''

from PIL import ImageFont
_FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"

def text_w(s, size, spacing=None):
    """Real advance width of Inter Display Bold, including the headline's tight letter-spacing."""
    spacing = -size * 0.035 if spacing is None else spacing
    return ImageFont.truetype(_FONT, int(size)).getlength(s) + spacing * len(s)

def headline(lines, size, y0, eyebrow=None, mark=None):
    """mark = (line index, word) to underline with a highlighter swoosh."""
    out = []
    y = y0
    if eyebrow:
        out.append(f'<text x="{W/2}" y="{y}" text-anchor="middle" font-family="Inter" font-weight="800" font-size="36" letter-spacing="5" fill="{PURPLE}">{eyebrow}</text>')
        y += size * 1.05
    for i, line in enumerate(lines):
        if mark and mark[0] == i:
            lw = text_w(line, size)
            start = W / 2 - lw / 2 + text_w(line[: line.index(mark[1])], size)
            ww = text_w(mark[1], size)
            out.append(f'<path d="M{start-6:.0f},{y+18} Q{start+ww/2:.0f},{y+4} {start+ww+10:.0f},{y+14}" fill="none" stroke="{LAV}" stroke-width="22" stroke-linecap="round" opacity="0.75"/>')
        out.append(f'<text x="{W/2}" y="{y}" text-anchor="middle" font-family="Inter Display" font-weight="700" font-size="{size}" letter-spacing="{-size*0.035:.1f}" fill="{INK}">{line}</text>')
        y += size * 1.02
    return "".join(out), y

def phone(x, y, w, name, cid):
    h = w * SH / SW
    b = 16
    return (f'<g filter="url(#shadow)"><rect x="{x-b}" y="{y-b}" width="{w+2*b}" height="{h+2*b}" rx="{78+b}" fill="#101014"/></g>'
            f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="78"/></clipPath>'
            f'<image href="{img(name)}" x="{x}" y="{y}" width="{w}" height="{h}" clip-path="url(#{cid})" preserveAspectRatio="none"/>'
            f'<circle cx="{x+w/2}" cy="{y+30}" r="13" fill="#0A0A0C"/>')

def callout(name, crop, x, y, w, rot, cid, rx=44, pad=0, fill="#FFFFFF"):
    """A zoomed piece of a screenshot as a floating rounded card."""
    cx, cy, cw, ch = crop
    h = w * ch / cw
    return (f'<g transform="rotate({rot} {x+w/2} {y+h/2})"><g filter="url(#shadow)">'
            f'<rect x="{x-pad}" y="{y-pad}" width="{w+2*pad}" height="{h+2*pad}" rx="{rx+pad}" fill="{fill}"/></g>'
            f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}"/></clipPath>'
            f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{cx} {cy} {cw} {ch}" preserveAspectRatio="none" clip-path="url(#{cid})">'
            f'<image href="{img(name)}" width="{SW}" height="{SH}"/></svg></g>')

def hand(text, x, y, size, rot, anchor="start", color=BLUE):
    return (f'<text x="{x}" y="{y}" transform="rotate({rot} {x} {y})" text-anchor="{anchor}" font-family="{HAND}" '
            f'font-size="{size}" fill="{color}" stroke="{color}" stroke-width="{size*0.025:.1f}">{text}</text>')

def arrow(d, color=BLUE):
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>')

def bubble(cx, cy, r):
    return (f'<g filter="url(#shadow)"><circle cx="{cx}" cy="{cy}" r="{r}" fill="#1E1B2E"/></g>'
            f'<circle cx="{cx}" cy="{cy}" r="{r*0.62}" fill="none" stroke="{LAV}" stroke-width="{r*0.13:.1f}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r*0.2}" fill="#FFFFFF"/>')

def badge(n, cx, cy):
    return (f'<g filter="url(#soft)"><circle cx="{cx}" cy="{cy}" r="30" fill="{INK}"/></g>'
            f'<text x="{cx}" y="{cy+12}" text-anchor="middle" font-family="Inter" font-weight="700" font-size="32" fill="#FFFFFF">{n}</text>')

def pill(cx, y, label, dark=True):
    w = ImageFont.truetype('/usr/share/fonts/opentype/inter/Inter-Bold.otf', 34).getlength(label) + 150
    x = cx - w / 2
    bg, fg = ("#1E1B2E", "#FFFFFF") if dark else ("#FFFFFF", INK)
    return (f'<g filter="url(#shadow)"><rect x="{x}" y="{y}" width="{w}" height="104" rx="52" fill="{bg}"/></g>'
            f'<circle cx="{x+58}" cy="{y+52}" r="22" fill="none" stroke="{LAV}" stroke-width="7"/><circle cx="{x+58}" cy="{y+52}" r="7" fill="#FFFFFF"/>'
            f'<text x="{x+100}" y="{y+64}" font-family="Inter" font-weight="700" font-size="34" fill="{fg}">{label}</text>')

def save(n, body):
    open(os.path.join(OUT, f"{n}.svg"), "w").write(head() + body + "</svg>")

# 1. Hero: no phone, big promise, the menu as a floating card.
t, _ = headline(["Switch apps", "with one", "thumb"], 150, 230, mark=(2, "thumb"))
s = t
s += callout("menu.jpg", (380, 360, 700, 1240), 430, 640, 560, 4, "c1a", rx=56)
s += badge(1, 120, 820) + hand("hold,", 175, 840, 92, -6)
s += badge(2, 120, 1010) + hand("slide,", 175, 1030, 92, -6)
s += badge(3, 120, 1200) + hand("let go!", 175, 1220, 92, -6)
s += arrow("M330,1260 Q420,1330 470,1270 M445,1262 l26,7 l-8,25")
s += pill(W / 2, 1700, "No ads. No tracking.")
save(1, s)

# 2. Hold: phone on the left, inner ring zoomed out to the right with a white border.
t, _ = headline(["Slide to any app"], 92, 170, eyebrow="HOLD", mark=(0, "any app"))
PX, PW = 70, 640
s = t + phone(PX, 470, PW, "menu.jpg", "p2")
s += callout("menu.jpg", (610, 560, 400, 760), 600, 640, 400, 3, "c2a", rx=40, pad=16)
s += hand("let go!", 640, 1620, 120, -10)
s += arrow("M840,1500 Q880,1450 860,1420 M838,1440 l22,-22 l22,20")
save(2, s)

# 3. Tap: phone on the left, the real bubble enlarged in place with tap ripples.
t, _ = headline(["Tap to jump back"], 92, 170, eyebrow="TAP", mark=(0, "back"))
s = t + phone(PX, 470, PW, "bubble.jpg", "p3")
k = PW / SW
cx, cy = PX + 1012 * k, 470 + 950 * k
for r, o in ((230, 0.10), (180, 0.16), (130, 0.26)):
    s += f'<circle cx="{cx:.0f}" cy="{cy:.0f}" r="{r}" fill="{LAV}" fill-opacity="{o}"/>'
s += bubble(cx, cy, 84)
s += hand("tap!", 790, 1460, 130, -12)
s += arrow(f"M860,1330 Q900,1260 {cx+60:.0f},{cy+110:.0f} M{cx+42:.0f},{cy+124:.0f} l20,-16 l10,26")
s += pill(W / 2, 1660, "Back to your last app", dark=True)
save(3, s)

# 4. Favourites: the favourites card on its own, big, with a note below.
t, _ = headline(["Your six,", "your order"], 92, 170, eyebrow="FAVOURITES", mark=(1, "order"))
s = t
s += callout("home.jpg", (60, 1330, 960, 790), 70, 520, 940, -2, "c4a", rx=56, pad=16)
s += hand("hold and drag", 250, 1520, 104, -8)
s += hand("to reorder!", 360, 1640, 104, -8)
s += arrow("M820,1500 Q900,1440 870,1340 M848,1362 l22,-24 l20,26")
s += pill(W / 2, 1760, "Search any app to add")
save(4, s)

# 5. Privacy: floating promise cards.
t, _ = headline(["Nothing leaves", "your phone"], 110, 190, eyebrow="PRIVATE", mark=(1, "phone"))
s = t
rows = [("No internet permission", "Halo can't send anything out", -3),
        ("No ads, no analytics", "No account, nothing tracked", 2),
        ("Open source", "Read every line on GitHub", -2)]
y = 640
for title, body, rot in rows:
    s += (f'<g transform="rotate({rot} {W/2} {y+110})"><g filter="url(#shadow)"><rect x="110" y="{y}" width="860" height="220" rx="56" fill="#FFFFFF"/></g>'
          f'<circle cx="230" cy="{y+110}" r="54" fill="{PURPLE}"/><path d="M204,{y+110} l18,20 l34,-38" fill="none" stroke="#FFFFFF" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>'
          f'<text x="320" y="{y+100}" font-family="Inter" font-weight="700" font-size="46" fill="{INK}">{title}</text>'
          f'<text x="320" y="{y+156}" font-family="Inter" font-size="36" fill="{QUIET}">{body}</text></g>')
    y += 290
s += hand("promise!", 640, 1610, 120, -10)
s += bubble(220, 1650, 70)
save(5, s)
