# usage: python3 docs/play_assets.py <dir with menu/home/settings/bubble .jpg at 1080x2424> <out dir>, then rsvg-convert each SVG
"""Build Google Play listing images for Halo.

usage: python3 play_assets.py <screenshots dir> <out dir>
Writes SVGs; render each with rsvg-convert at its own size.
"""
import base64, os, sys

SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
SHOT_W, SHOT_H = 1080, 2424


def embed(name):
    with open(os.path.join(SRC, name), "rb") as f:
        return "data:image/jpeg;base64," + base64.b64encode(f.read()).decode()


DEFS = '''<defs>
  <radialGradient id="glow" cx="0.5" cy="0.62" r="0.7">
    <stop offset="0" stop-color="#5B4BB7" stop-opacity="0.55"/>
    <stop offset="1" stop-color="#14121F" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="title" x1="0" x2="1">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#D4CAFF"/>
  </linearGradient>
</defs>'''


def phone(x, y, w, img, cid):
    h = w * SHOT_H / SHOT_W
    pad = 12
    return (f'<rect x="{x-pad}" y="{y-pad}" width="{w+2*pad}" height="{h+2*pad}" rx="64" fill="#0B0A12" stroke="#3A3358" stroke-width="4"/>'
            f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="54"/></clipPath>'
            f'<image href="{img}" x="{x}" y="{y}" width="{w}" height="{h}" clip-path="url(#{cid})" preserveAspectRatio="xMidYMid slice"/>')


def screenshot(n, img, headline, sub):
    W, H = 1080, 1920
    pw = 640
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', DEFS,
           f'<rect width="{W}" height="{H}" fill="#14121F"/><rect width="{W}" height="{H}" fill="url(#glow)"/>',
           f'<text x="{W/2}" y="190" text-anchor="middle" font-family="Inter Display" font-weight="700" font-size="84" letter-spacing="-2" fill="url(#title)">{headline}</text>',
           f'<text x="{W/2}" y="262" text-anchor="middle" font-family="Inter" font-size="40" fill="#C9C2E8">{sub}</text>',
           phone((W - pw) / 2, 360, pw, embed(img), f"c{n}"),
           '</svg>']
    open(os.path.join(OUT, f"{n}.svg"), "w").write("".join(svg))


def privacy(n):
    W, H = 1080, 1920
    cx, cy = W / 2, 820
    rows = [("No internet permission", "Halo can't send anything off your phone"),
            ("No ads, no analytics", "Nothing to sign up for, nothing tracked"),
            ("Open source", "Read every line on GitHub")]
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', DEFS,
           f'<rect width="{W}" height="{H}" fill="#14121F"/><rect width="{W}" height="{H}" fill="url(#glow)"/>',
           f'<text x="{W/2}" y="190" text-anchor="middle" font-family="Inter Display" font-weight="700" font-size="84" letter-spacing="-2" fill="url(#title)">Nothing leaves</text>',
           f'<text x="{W/2}" y="290" text-anchor="middle" font-family="Inter Display" font-weight="700" font-size="84" letter-spacing="-2" fill="url(#title)">your phone</text>',
           # Big bubble mark
           f'<circle cx="{cx}" cy="{cy}" r="230" fill="#B9A8FF" fill-opacity="0.08"/>',
           f'<circle cx="{cx}" cy="{cy}" r="160" fill="#1E1B2E" stroke="#B9A8FF" stroke-opacity="0.3" stroke-width="3"/>',
           f'<circle cx="{cx}" cy="{cy}" r="92" fill="none" stroke="#B9A8FF" stroke-width="20"/>',
           f'<circle cx="{cx}" cy="{cy}" r="30" fill="#FFFFFF"/>']
    y = 1180
    for title, body in rows:
        svg.append(f'<rect x="120" y="{y}" width="840" height="150" rx="36" fill="#B9A8FF" fill-opacity="0.10" stroke="#B9A8FF" stroke-opacity="0.3" stroke-width="2"/>')
        svg.append(f'<circle cx="196" cy="{y+75}" r="26" fill="#B9A8FF"/><path d="M183,{y+75} l9,10 l18,-20" fill="none" stroke="#14121F" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>')
        svg.append(f'<text x="252" y="{y+66}" font-family="Inter" font-weight="700" font-size="40" fill="#F1EDFF">{title}</text>')
        svg.append(f'<text x="252" y="{y+112}" font-family="Inter" font-size="32" fill="#C9C2E8">{body}</text>')
        y += 180
    svg.append('</svg>')
    open(os.path.join(OUT, f"{n}.svg"), "w").write("".join(svg))


def feature_graphic():
    W, H = 1024, 500
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
           DEFS.replace('cx="0.5" cy="0.62"', 'cx="0.78" cy="0.5"'),
           f'<rect width="{W}" height="{H}" fill="#14121F"/><rect width="{W}" height="{H}" fill="url(#glow)"/>',
           '<text x="64" y="232" font-family="Inter Display" font-weight="700" font-size="118" letter-spacing="-4" fill="url(#title)">Halo</text>',
           '<text font-family="Inter" font-size="32" fill="#C9C2E8"><tspan x="68" y="292">The one-thumb</tspan><tspan x="68" y="334">app switcher</tspan></text>',
           # phone bleeding off the bottom edge
           phone(640, 64, 300, embed("menu.jpg"), "fg"),
           '</svg>']
    open(os.path.join(OUT, "featureGraphic.svg"), "w").write("".join(svg))


def icon():
    # Full-bleed square; Play applies its own mask. Matches the adaptive launcher icon.
    s = 512 / 108
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'
           '<rect width="512" height="512" fill="#14121F"/>'
           f'<circle cx="256" cy="256" r="{22*s:.1f}" fill="none" stroke="#B9A8FF" stroke-width="{5*s:.1f}"/>'
           f'<circle cx="256" cy="256" r="{7*s:.1f}" fill="#FFFFFF"/></svg>')
    open(os.path.join(OUT, "icon.svg"), "w").write(svg)


screenshot(1, "menu.jpg", "Hold, slide, let go", "Open any app with one thumb")
screenshot(2, "bubble.jpg", "Stays out of the way", "A small bubble on the edge of any screen")
screenshot(3, "home.jpg", "Your six favourites", "Recent apps fill in on their own")
screenshot(4, "settings.jpg", "Tune every gesture", "Tap action, hold delay and haptics")
privacy(5)
feature_graphic()
icon()
