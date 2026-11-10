"""Halo landscape promo (1920x1080, 30 fps), drawn frame by frame with Pillow and piped to ffmpeg.

usage: python3 docs/promo_video.py <dir with menu/bubble/home.jpg at 1080x2424> <out dir> [scene names...]
Writes one mp4 per scene into <out dir>/scenes, then joins them into <out dir>/halo-promo-landscape.mp4.
"""
import math, os, subprocess, sys
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC, OUT = sys.argv[1], sys.argv[2]
ONLY = set(sys.argv[3:])
W, H, FPS = 1920, 1080, 30
SW, SH = 1080, 2424
INK, QUIET, PURPLE, LAV, BLUE, BG = "#1B1830", "#6B6684", "#5B47D6", "#B9A8FF", "#2F55D4", "#F2F2F5"
F_DISPLAY = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
F_BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
F_SEMI = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"
F_MED = "/usr/share/fonts/opentype/inter/Inter-Medium.otf"
F_HAND = "/usr/share/texmf/fonts/opentype/public/tex-gyre/texgyrechorus-mediumitalic.otf"


# --- easing and timing ------------------------------------------------------------------
def clamp(v, a=0.0, b=1.0):
    return max(a, min(b, v))

def seg(t, a, b):
    return clamp((t - a) / (b - a)) if b > a else float(t >= b)

def out_cubic(p):
    return 1 - (1 - p) ** 3

def in_out(p):
    return 4 * p ** 3 if p < 0.5 else 1 - (-2 * p + 2) ** 3 / 2

def out_back(p, s=1.6):
    p -= 1
    return p * p * ((s + 1) * p + s) + 1

def lerp(a, b, p):
    return a + (b - a) * p

def rgba(hex_, a=255):
    hex_ = hex_.lstrip("#")
    return tuple(int(hex_[i:i + 2], 16) for i in (0, 2, 4)) + (int(a),)


# --- drawing helpers --------------------------------------------------------------------
@lru_cache(None)
def font(path, size):
    return ImageFont.truetype(path, int(size))

@lru_cache(None)
def text_img(text, path, size, color, tracking=0.0, stroke=0):
    f = font(path, size)
    widths = [f.getlength(c) for c in text]
    total = sum(widths) + tracking * (len(text) - 1)
    asc, desc = f.getmetrics()
    pad = 20
    img = Image.new("RGBA", (int(total) + 2 * pad, asc + desc + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if tracking == 0:
        d.text((pad, pad), text, font=f, fill=rgba(color), stroke_width=stroke, stroke_fill=rgba(color))
    else:
        x = pad
        for c, w in zip(text, widths):
            d.text((x, pad), c, font=f, fill=rgba(color), stroke_width=stroke, stroke_fill=rgba(color))
            x += w + tracking
    return img, pad

def with_alpha(img, a):
    if a >= 0.999:
        return img
    arr = np.array(img)
    arr[..., 3] = (arr[..., 3].astype(np.float32) * max(a, 0)).astype(np.uint8)
    return Image.fromarray(arr)

def paste(canvas, img, x, y, alpha=1.0, scale=1.0, anchor="lt", rotate=0.0):
    if alpha <= 0.003:
        return
    if scale != 1.0:
        img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.BICUBIC)
    if rotate:
        img = img.rotate(rotate, resample=Image.BICUBIC, expand=True)
    img = with_alpha(img, alpha)
    if anchor == "c":
        x, y = x - img.width / 2, y - img.height / 2
    elif anchor == "lc":
        y = y - img.height / 2
    elif anchor == "ct":
        x = x - img.width / 2
    canvas.alpha_composite(img, (int(round(x)), int(round(y))))

def text(canvas, s, path, size, color, x, y, alpha=1.0, anchor="lt", tracking=0.0, rotate=0.0, stroke=0):
    """x,y = left/top of the glyph box (anchor 'lt') or its centre (anchor 'c')."""
    img, pad = text_img(s, path, size, color, tracking, stroke)
    if anchor == "lt":
        paste(canvas, img, x - pad, y - pad, alpha, rotate=rotate)
    else:
        paste(canvas, img, x, y, alpha, anchor="c", rotate=rotate)
    return img.width - 2 * pad

def text_w(s, path, size, tracking=0.0):
    f = font(path, size)
    return f.getlength(s) + tracking * (len(s) - 1)

def hand(canvas, s, size, x, y, reveal=1.0, alpha=1.0, rotate=-6.0):
    """Handwritten note revealed left to right, as if being written."""
    if reveal <= 0:
        return
    img, pad = text_img(s, F_HAND, size, BLUE, 0, max(1, size // 40))
    if reveal < 1:
        img = img.copy()
        cut = int(pad + (img.width - 2 * pad) * reveal)
        ImageDraw.Draw(img).rectangle([cut, 0, img.width, img.height], fill=(0, 0, 0, 0))
    paste(canvas, img, x - pad, y - pad, alpha, rotate=rotate)

def stroke_path(canvas, pts, width, color, progress=1.0, alpha=255):
    """Draw the first `progress` of a polyline, like a marker stroke being drawn."""
    if progress <= 0:
        return
    lens = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    total, want, out = sum(lens), sum(lens) * progress, [pts[0]]
    for (a, b), L in zip(zip(pts, pts[1:]), lens):
        if want <= 0:
            break
        f = min(1, want / L)
        out.append((lerp(a[0], b[0], f), lerp(a[1], b[1], f)))
        want -= L
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).line(out, fill=rgba(color, alpha), width=width, joint="curve")
    r = width / 2
    d = ImageDraw.Draw(layer)
    for p in (out[0], out[-1]):
        d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=rgba(color, alpha))
    canvas.alpha_composite(layer)

def swoosh(x0, x1, y, lift=10, n=24):
    return [(lerp(x0, x1, i / n), y - lift * math.sin(math.pi * i / n)) for i in range(n + 1)]

def shadowed(img, radius=26, offset=18, opacity=0.24, margin=60):
    """Return img on a transparent canvas with a soft drop shadow, plus the margin used."""
    base = Image.new("RGBA", (img.width + 2 * margin, img.height + 2 * margin), (0, 0, 0, 0))
    a = Image.new("L", base.size, 0)
    a.paste(img.getchannel("A"), (margin, margin + offset))
    a = a.filter(ImageFilter.GaussianBlur(radius)).point(lambda v: int(v * opacity))
    shadow = Image.new("RGBA", base.size, (20, 16, 40, 0))
    shadow.putalpha(a)
    base.alpha_composite(shadow)
    base.alpha_composite(img, (margin, margin))
    return base, margin

def rounded(w, h, r, fill):
    img = Image.new("RGBA", (int(w), int(h)), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, w - 1, h - 1], r, fill=fill)
    return img

@lru_cache(None)
def pill_img(label, size=34, dark=True):
    tw = text_w(label, F_BOLD, size)
    w, h = int(tw + 150), int(size * 3)
    bg, fg = (rgba("#1E1B2E"), "#FFFFFF") if dark else (rgba("#FFFFFF"), INK)
    img = rounded(w, h, h / 2, bg)
    d = ImageDraw.Draw(img)
    cx, cy = h / 2 + 8, h / 2
    d.ellipse([cx - 21, cy - 21, cx + 21, cy + 21], outline=rgba(LAV), width=7)
    d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=rgba("#FFFFFF"))
    t, pad = text_img(label, F_BOLD, size, fg)
    img.alpha_composite(t, (int(h * 0.95) - pad, int(h / 2 - size * 0.62) - pad))
    return shadowed(img)

@lru_cache(None)
def bubble_img(r):
    s = int(r * 2 + 4)
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([c - r, c - r, c + r, c + r], fill=rgba("#1E1B2E"))
    rr, w = r * 0.62, max(2, int(r * 0.13))
    d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=rgba(LAV), width=w)
    rd = r * 0.2
    d.ellipse([c - rd, c - rd, c + rd, c + rd], fill=rgba("#FFFFFF"))
    return shadowed(img, radius=18, offset=12)

def bubble(canvas, cx, cy, r, alpha=1.0, scale=1.0):
    img, m = bubble_img(r)
    paste(canvas, img, cx, cy, alpha, scale=scale, anchor="c")

def ring(canvas, cx, cy, r, width, color, alpha):
    if alpha <= 0:
        return
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgba(color, 255 * alpha), width=int(width))
    canvas.alpha_composite(layer)

def disc(canvas, cx, cy, r, color, alpha):
    if alpha <= 0 or r <= 0:
        return
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba(color, 255 * alpha))
    canvas.alpha_composite(layer)

def finger(canvas, x, y, alpha, press=0.0):
    """A touch indicator: translucent white dot with a dark rim; `press` shrinks it slightly."""
    if alpha <= 0:
        return
    r = 30 * (1 - 0.18 * press)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, int(150 * alpha)), outline=(27, 24, 48, int(150 * alpha)), width=4)
    canvas.alpha_composite(layer)

def background():
    img = Image.new("RGBA", (W, H), rgba(BG))
    d = ImageDraw.Draw(img)
    for x in range(60, W, 60):
        d.line([(x, 0), (x, H)], fill=rgba("#E6E6EC"), width=2)
    for y in range(60, H, 60):
        d.line([(0, y), (W, y)], fill=rgba("#E6E6EC"), width=2)
    return img

BG_IMG = background()


# --- phone --------------------------------------------------------------------------------
class Phone:
    def __init__(self, screen_h):
        self.sh = int(screen_h)
        self.sw = int(round(screen_h * SW / SH))
        self.k = self.sw / SW
        self.b = max(10, int(self.sw * 0.034))
        self.r = int(self.sw * 0.12)
        body = rounded(self.sw + 2 * self.b, self.sh + 2 * self.b, self.r + self.b, rgba("#101014"))
        self.base, self.m = shadowed(body, radius=30, offset=24, opacity=0.28, margin=70)
        self.mask = Image.new("L", (self.sw, self.sh), 0)
        ImageDraw.Draw(self.mask).rounded_rectangle([0, 0, self.sw - 1, self.sh - 1], self.r, fill=255)
        self.cache = {}

    def screen(self, name):
        if name not in self.cache:
            self.cache[name] = Image.open(os.path.join(SRC, name)).convert("RGB").resize((self.sw, self.sh), Image.LANCZOS)
        return self.cache[name]

    def image(self, screen_rgb):
        img = self.base.copy()
        o = self.m + self.b
        img.paste(screen_rgb, (o, o), self.mask)
        d = ImageDraw.Draw(img)
        cx, cy, r = o + self.sw / 2, o + self.sh * 0.013, max(4, self.sw * 0.013)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba("#0A0A0C"))
        return img

    def draw(self, canvas, screen_rgb, sx, sy, alpha=1.0):
        """Place the phone so its screen's top-left corner lands at (sx, sy)."""
        o = self.m + self.b
        paste(canvas, self.image(screen_rgb), sx - o, sy - o, alpha)

    def pt(self, sx, sy, x, y):
        """Screenshot pixel (x, y) to canvas coordinates for a phone whose screen starts at (sx, sy)."""
        return sx + x * self.k, sy + y * self.k


# --- scenes -------------------------------------------------------------------------------
def scene_hook(t):
    c = BG_IMG.copy()
    size, x0 = 132, 180
    lines = [("Switching apps", 0.10, 250), ("shouldn't take", 0.35, 410), ("three taps.", 0.60, 570)]
    leave = out_cubic(seg(t, 2.95, 3.3))
    for s, start, y in lines:
        p = out_cubic(seg(t, start, start + 0.45))
        text(c, s, F_DISPLAY, size, INK, x0, y + 50 * (1 - p) - 80 * leave, alpha=p * (1 - leave), tracking=-4)
    # Cross out "three taps." with a marker stroke, then write the better answer.
    w3 = text_w("three taps.", F_DISPLAY, size, -4)
    sp = in_out(seg(t, 1.25, 1.6))
    if sp > 0 and leave < 1:
        stroke_path(c, [(x0 - 10, 650 - 80 * leave), (x0 + w3 + 10, 628 - 80 * leave)], 14, BLUE, sp, 235 * (1 - leave))
    hand(c, "one thumb is enough.", 112, x0 + 40, 760 - 80 * leave, reveal=seg(t, 1.7, 2.5), alpha=1 - leave, rotate=-5)
    # A bubble drifts in on the right as a teaser.
    bp = out_back(seg(t, 1.9, 2.5))
    for i, (r, a) in enumerate(((210, 0.16), (280, 0.10))):
        disc(c, 1560, 520 - 80 * leave, r * out_cubic(seg(t, 2.1 + i * 0.12, 2.7)), LAV, a * (1 - leave))
    bubble(c, 1560, 520 - 80 * leave, 120, alpha=seg(t, 1.9, 2.1) * (1 - leave), scale=max(0.01, bp))
    return c

ICONS = {"LinkedIn": (700, 876), "Claude": (700, 1023), "WhatsApp": (765, 1155), "X": (765, 745),
         "Reddit": (885, 655), "Chrome": (885, 1245)}
BUBBLE_XY = (1012, 950)

def scene_gesture(t, phone=Phone(940)):
    c = BG_IMG.copy()
    sx = lerp(W + 50, 1220, out_cubic(seg(t, 0.0, 0.6)))
    sx = lerp(sx, sx + 40, seg(t, 7.0, 7.5))
    sy = (H - phone.sh) / 2
    k = phone.k
    # Screen: home with bubble, menu revealed as a circle growing out of the bubble.
    home, menu = phone.screen("bubble.jpg"), phone.screen("menu.jpg")
    bx, by = BUBBLE_XY[0] * k, BUBBLE_XY[1] * k
    rmax = math.hypot(phone.sw, phone.sh)
    open_p = out_cubic(seg(t, 1.25, 1.75)) * (1 - in_out(seg(t, 5.05, 5.45)))
    if open_p > 0:
        mask = Image.new("L", (phone.sw, phone.sh), 0)
        r = rmax * open_p
        ImageDraw.Draw(mask).ellipse([bx - r, by - r, bx + r, by + r], fill=255)
        scr = Image.composite(menu, home, mask)
    else:
        scr = home
    fade_out = 1 - seg(t, 7.2, 7.5)
    words_canvas = c
    c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    phone.draw(c, scr, sx, sy, alpha=fade_out)

    # Finger path: press the bubble, slide over LinkedIn, Claude, then rest on WhatsApp.
    keys = [(0.8, BUBBLE_XY), (2.0, BUBBLE_XY), (2.8, ICONS["LinkedIn"]), (3.5, ICONS["Claude"]),
            (4.3, ICONS["WhatsApp"]), (5.0, ICONS["WhatsApp"])]
    pos = keys[0][1]
    for (ta, a), (tb, b) in zip(keys, keys[1:]):
        if t >= ta:
            p = in_out(seg(t, ta, tb))
            pos = (lerp(a[0], b[0], p), lerp(a[1], b[1], p))
    fx, fy = phone.pt(sx, sy, *pos)
    # Highlight whichever icon the finger is over.
    if open_p > 0.9:
        for name, (ix, iy) in ICONS.items():
            dist = math.dist(pos, (ix, iy))
            h = clamp(1 - dist / 90)
            if h > 0:
                cx, cy = phone.pt(sx, sy, ix, iy)
                disc(c, cx, cy, 44 * (0.8 + 0.2 * h), LAV, 0.35 * h)
                ring(c, cx, cy, 44 * (0.8 + 0.2 * h), 5, LAV, h)
    # Press pulse when the hold starts.
    pp = seg(t, 1.0, 1.5)
    if 0 < pp < 1:
        bxc, byc = phone.pt(sx, sy, *BUBBLE_XY)
        ring(c, bxc, byc, 30 + 60 * out_cubic(pp), 5, LAV, 1 - pp)
    f_alpha = seg(t, 0.8, 0.95) * (1 - seg(t, 5.0, 5.25))
    lift = seg(t, 5.0, 5.25)
    finger(c, fx, fy - 20 * lift, f_alpha, press=seg(t, 0.95, 1.05))
    # App name pops beside the finger while it rests on WhatsApp.
    lp = out_back(seg(t, 4.35, 4.65)) * (1 - seg(t, 5.6, 5.9))
    if lp > 0:
        img, m = pill_img("WhatsApp", 30, dark=False)
        cx, cy = phone.pt(sx, sy, *ICONS["WhatsApp"])
        paste(c, img, cx - 70 - img.width / 2 + m, cy, alpha=min(1, lp), scale=max(0.01, lp), anchor="c")

    # Camera push-in on the bubble while the menu is open, so the icons read at full size.
    z = 1 + 0.45 * in_out(seg(t, 1.2, 1.9)) * (1 - in_out(seg(t, 5.3, 6.0)))
    if z > 1.001:
        px, py = phone.pt(sx, sy, *BUBBLE_XY)
        big = c.resize((int(W * z), int(H * z)), Image.BICUBIC)
        ox, oy = int(px * z - px), int(py * z - py)
        c = big.crop((ox, oy, ox + W, oy + H))
    words_canvas.alpha_composite(c)
    c = words_canvas

    # Words in step with the gesture.
    words = [("Hold.", 1.0, 260), ("Slide.", 2.0, 430), ("Let go.", 5.0, 600)]
    for i, (w, start, y) in enumerate(words):
        p = out_cubic(seg(t, start, start + 0.35))
        nxt = words[i + 1][1] if i + 1 < len(words) else 99
        color = INK if t < nxt else "#B9B3CF"
        text(c, w, F_DISPLAY, 150, color, 180, y + 40 * (1 - p), alpha=p * fade_out, tracking=-5)
    cp = out_cubic(seg(t, 2.3, 2.7))
    text(c, "Favourites inside, recent apps outside.", F_MED, 40, QUIET, 186, 800 + 20 * (1 - cp), alpha=cp * fade_out)
    return c

def scene_tap(t, phone=Phone(920)):
    c = BG_IMG.copy()
    sx = lerp(-500, 330, out_cubic(seg(t, 0.0, 0.55)))
    sy = (H - phone.sh) / 2
    fade = 1 - seg(t, 4.2, 4.5)
    phone.draw(c, phone.screen("bubble.jpg"), sx, sy, alpha=fade)
    bx, by = phone.pt(sx, sy, *BUBBLE_XY)
    for tap in (1.0, 2.4):
        for j in range(3):
            rp = seg(t, tap + 0.08 + j * 0.12, tap + 0.9 + j * 0.12)
            if 0 < rp < 1:
                ring(c, bx, by, 32 + 150 * out_cubic(rp), 6, PURPLE, (1 - rp) * 0.8 * fade)
        fa = seg(t, tap - 0.25, tap - 0.1) * (1 - seg(t, tap + 0.2, tap + 0.4))
        finger(c, bx, by, fa * fade, press=seg(t, tap - 0.05, tap + 0.05) * (1 - seg(t, tap + 0.1, tap + 0.2)))
    x0 = 900
    ep = out_cubic(seg(t, 0.45, 0.8))
    text(c, "TAP", F_BOLD, 40, PURPLE, x0, 300 + 20 * (1 - ep), alpha=ep * fade, tracking=6)
    for i, (s, y) in enumerate((("Jump back to", 370), ("your last app", 510))):
        p = out_cubic(seg(t, 0.6 + i * 0.15, 1.0 + i * 0.15))
        text(c, s, F_DISPLAY, 128, INK, x0, y + 40 * (1 - p), alpha=p * fade, tracking=-4)
    # Highlighter under "last app".
    wl = text_w("your ", F_DISPLAY, 128, -4)
    wa = text_w("last app", F_DISPLAY, 128, -4)
    stroke_path(c, swoosh(x0 + wl, x0 + wl + wa, 668), 24, LAV, in_out(seg(t, 1.3, 1.8)), 190 * fade)
    text(c, "your last app", F_DISPLAY, 128, INK, x0, 510, alpha=seg(t, 1.3, 1.31) * fade, tracking=-4)
    pp = out_back(seg(t, 1.7, 2.1))
    if pp > 0:
        img, m = pill_img("Like Alt+Tab, for your thumb")
        paste(c, img, x0 - m, 720 + 30 * (1 - pp), alpha=min(1, pp) * fade)
    return c

def scene_favourites(t, phone=Phone(880)):
    c = BG_IMG.copy()
    fade = 1 - seg(t, 4.7, 5.0)
    sx = lerp(W + 50, 1420, out_cubic(seg(t, 0.0, 0.5)))
    sy = (H - phone.sh) / 2 + 20
    phone.draw(c, phone.screen("home.jpg"), sx, sy, alpha=fade)
    # Favourites grid flies out of the phone and lands big on the left.
    crop = Image.open(os.path.join(SRC, "home.jpg")).convert("RGB").crop((60, 1330, 1020, 2120))
    p = out_back(seg(t, 0.6, 1.3), 1.2)
    w_end, w_start = 800, 960 * phone.k
    w = lerp(w_start, w_end, p)
    h = w * crop.height / crop.width
    card = crop.resize((int(w), int(h)), Image.BICUBIC).convert("RGBA")
    mask = Image.new("L", card.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, card.width - 1, card.height - 1], int(46 * w / w_end), fill=255)
    framed = Image.new("RGBA", (card.width + 24, card.height + 24), (0, 0, 0, 0))
    ImageDraw.Draw(framed).rounded_rectangle([0, 0, framed.width - 1, framed.height - 1], int(56 * w / w_end), fill=rgba("#FFFFFF"))
    framed.paste(card, (12, 12), mask)
    img, m = shadowed(framed, radius=28, offset=20)
    start_c = phone.pt(sx, sy, 540, 1725)
    end_c = (610, 585)
    cx, cy = lerp(start_c[0], end_c[0], p), lerp(start_c[1], end_c[1], p)
    if t >= 0.6:
        paste(c, img, cx, cy, alpha=fade, anchor="c", rotate=lerp(0, 3, p))
    # Headline and note.
    for i, (s, y) in enumerate((("Your six.", 70), ("Your order.", 70))):
        pp = out_cubic(seg(t, 1.1 + i * 0.2, 1.5 + i * 0.2))
        x = 180 if i == 0 else 180 + text_w("Your six. ", F_DISPLAY, 104, -3)
        if i == 1:
            wo = text_w("order.", F_DISPLAY, 104, -3)
            xo = x + text_w("Your ", F_DISPLAY, 104, -3)
            stroke_path(c, swoosh(xo, xo + wo - 10, 186), 22, LAV, in_out(seg(t, 1.8, 2.2)), 190 * fade)
        text(c, s, F_DISPLAY, 104, INK, x, y + 30 * (1 - pp), alpha=pp * fade, tracking=-3)
    hand(c, "hold and drag to reorder!", 70, 250, 945, reveal=seg(t, 2.3, 3.2), alpha=fade, rotate=-4)
    return c

def scene_privacy(t):
    c = BG_IMG.copy()
    fade = 1 - seg(t, 4.2, 4.5)
    hp = out_cubic(seg(t, 0.1, 0.5))
    s = "Nothing leaves your phone"
    size = 112
    tw = text_w(s, F_DISPLAY, size, -4)
    x0 = (W - tw) / 2
    xs = x0 + text_w("Nothing leaves ", F_DISPLAY, size, -4)
    stroke_path(c, swoosh(xs, x0 + tw, 318), 26, LAV, in_out(seg(t, 0.6, 1.1)), 190 * fade)
    text(c, s, F_DISPLAY, size, INK, x0, 170 + 30 * (1 - hp), alpha=hp * fade, tracking=-4)
    cards = [("No internet", "permission"), ("No ads,", "no analytics"), ("Open source", "on GitHub")]
    cw, ch, gap = 500, 320, 50
    left = (W - (3 * cw + 2 * gap)) / 2
    for i, (a, b) in enumerate(cards):
        p = out_back(seg(t, 0.8 + i * 0.18, 1.4 + i * 0.18), 1.3)
        if p <= 0:
            continue
        card = rounded(cw, ch, 56, rgba("#FFFFFF"))
        d = ImageDraw.Draw(card)
        d.ellipse([60, 56, 160, 156], fill=rgba(PURPLE))
        d.line([(88, 108), (104, 124), (134, 90)], fill=rgba("#FFFFFF"), width=12, joint="curve")
        for line, y in ((a, 190), (b, 248)):
            ti, pad = text_img(line, F_BOLD, 48, INK)
            card.alpha_composite(ti, (60 - pad, y - pad))
        img, m = shadowed(card)
        x = left + i * (cw + gap) + 400 * (1 - p)
        rot = [-3, 2, -2][i]
        paste(c, img, x + cw / 2, 640, alpha=min(1, p) * fade, anchor="c", rotate=rot)
    return c

def scene_end(t):
    c = BG_IMG.copy()
    cx, cy = W / 2, 330
    for i, (r, a) in enumerate(((240, 0.14), (180, 0.20))):
        disc(c, cx, cy, r * out_cubic(seg(t, 0.25 + i * 0.1, 0.9 + i * 0.1)), LAV, a)
    bp = out_back(seg(t, 0.0, 0.55))
    bubble(c, cx, cy, 120, alpha=seg(t, 0, 0.15), scale=max(0.01, bp))
    for j in range(2):
        rp = seg(t, 0.5 + j * 0.25, 1.6 + j * 0.25)
        if 0 < rp < 1:
            ring(c, cx, cy, 120 + 260 * out_cubic(rp), 5, PURPLE, (1 - rp) * 0.6)
    p = out_cubic(seg(t, 0.55, 0.95))
    text(c, "Halo", F_DISPLAY, 190, INK, cx, 610 + 30 * (1 - p), alpha=p, anchor="c", tracking=-7)
    p = out_cubic(seg(t, 0.8, 1.2))
    text(c, "The one-thumb app switcher. Open source. No tracking.", F_MED, 46, QUIET, cx, 735 + 20 * (1 - p), alpha=p, anchor="c")
    p = out_back(seg(t, 1.1, 1.5))
    if p > 0:
        img, m = pill_img("github.com/yogeshpaliyal/Halo", 36)
        paste(c, img, cx, 870 + 30 * (1 - p), alpha=min(1, p), anchor="c")
    p = out_cubic(seg(t, 1.5, 1.9))
    text(c, "Free on GitHub. Coming to Google Play.", F_SEMI, 34, QUIET, cx, 985, alpha=p, anchor="c")
    return c

SCENES = [("10-hook", scene_hook, 3.3), ("20-gesture", scene_gesture, 7.5), ("30-tap", scene_tap, 4.5),
          ("40-favourites", scene_favourites, 5.0), ("50-privacy", scene_privacy, 4.5), ("90-end", scene_end, 4.5)]


def render(name, fn, dur):
    os.makedirs(os.path.join(OUT, "scenes"), exist_ok=True)
    tmp = os.path.join(OUT, "scenes", f"{name}.tmp.mp4")
    cmd = ["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
           "-i", "-", "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-r", str(FPS), tmp]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = round(dur * FPS)
    for i in range(n):
        proc.stdin.write(fn(i / FPS).convert("RGB").tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise SystemExit(f"ffmpeg failed on {name}")
    os.replace(tmp, os.path.join(OUT, "scenes", f"{name}.mp4"))


if __name__ == "__main__":
    if "frames" in ONLY:  # quick look: a few full-size frames at busy moments
        for name, fn, t in (("hook", scene_hook, 2.6), ("gesture", scene_gesture, 3.2), ("gesture2", scene_gesture, 4.6),
                            ("tap", scene_tap, 2.6), ("fav", scene_favourites, 3.5), ("priv", scene_privacy, 2.5),
                            ("end", scene_end, 2.5)):
            fn(t).convert("RGB").save(os.path.join(OUT, f"check_{name}.jpg"), quality=90)
        sys.exit()
    for name, fn, dur in SCENES:
        if not ONLY or name in ONLY:
            render(name, fn, dur)
    files = sorted(f for f in os.listdir(os.path.join(OUT, "scenes")) if f.endswith(".mp4") and ".tmp" not in f)
    with open(os.path.join(OUT, "list.txt"), "w") as f:
        f.writelines(f"file 'scenes/{x}'\n" for x in files)
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", os.path.join(OUT, "list.txt"),
                    "-c", "copy", os.path.join(OUT, "video_only.mp4")], check=True)
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", os.path.join(OUT, "video_only.mp4"), "-f", "lavfi", "-i",
                    "anullsrc=r=44100:cl=stereo", "-c:v", "copy", "-c:a", "aac", "-shortest",
                    os.path.join(OUT, "halo-promo-landscape.tmp.mp4")], check=True)
    os.replace(os.path.join(OUT, "halo-promo-landscape.tmp.mp4"), os.path.join(OUT, "halo-promo-landscape.mp4"))
