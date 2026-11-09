# generate: python3 docs/social_card.py card.svg && rsvg-convert -w 2400 -h 1260 card.svg -o docs/social-card.png
import base64, sys

W, H = 1200, 630


def embed(path):
    with open(path, "rb") as f:
        return "data:image/jpeg;base64," + base64.b64encode(f.read()).decode()


def phone(x, y, w, img, clip_id, opacity=1.0):
    h = w * 1212 / 540
    pad = 7
    return f'''<g opacity="{opacity}">
  <rect x="{x-pad}" y="{y-pad}" width="{w+2*pad}" height="{h+2*pad}" rx="38" fill="#0B0A12" stroke="#3A3358" stroke-width="3"/>
  <clipPath id="{clip_id}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="31"/></clipPath>
  <image href="{img}" x="{x}" y="{y}" width="{w}" height="{h}" clip-path="url(#{clip_id})" preserveAspectRatio="xMidYMid slice"/>
</g>'''


shots = sys.argv[2] if len(sys.argv) > 2 else "docs/screenshots"
parts = [f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
  <radialGradient id="glow" cx="0.78" cy="0.5" r="0.6">
    <stop offset="0" stop-color="#5B4BB7" stop-opacity="0.6"/>
    <stop offset="1" stop-color="#14121F" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="title" x1="0" x2="1">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#D4CAFF"/>
  </linearGradient>
</defs>
<rect width="{W}" height="{H}" fill="#14121F"/>
<rect width="{W}" height="{H}" fill="url(#glow)"/>''']

# Two phones: the app's home screen behind, the menu in action in front.
parts.append(phone(700, 70, 226, embed(f"{shots}/home.jpg"), "c-home", opacity=0.9))
parts.append(phone(905, 40, 244, embed(f"{shots}/menu.jpg"), "c-menu"))

parts.append('<text x="72" y="236" font-family="Inter Display" font-weight="700" font-size="120" letter-spacing="-4" fill="url(#title)">Halo</text>')
parts.append('<text font-family="Inter" font-size="30" fill="#C9C2E8">'
             '<tspan x="76" y="294">The one-thumb app switcher</tspan>'
             '<tspan x="76" y="334">for Android.</tspan></text>')
x = 76
for label in ["Hold · slide · let go", "Open source", "No tracking"]:
    w = 34 + len(label) * 9.4
    parts.append(f'<rect x="{x}" y="378" width="{w:.0f}" height="42" rx="21" fill="#B9A8FF" fill-opacity="0.14" stroke="#B9A8FF" stroke-opacity="0.35"/>')
    parts.append(f'<text x="{x + w/2:.0f}" y="405" text-anchor="middle" font-family="Inter" font-weight="600" font-size="18" fill="#E6E0FF">{label}</text>')
    x += w + 12
parts.append('<text x="76" y="560" font-family="Inter" font-weight="500" font-size="20" fill="#8F86B8">github.com/yogeshpaliyal/Halo</text>')
parts.append("</svg>")
open(sys.argv[1], "w").write("\n".join(parts))
