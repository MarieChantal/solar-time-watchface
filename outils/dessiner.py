"""Génère les images du cadran (fond, aiguilles, aperçu) en 450x450.
Usage : python3 outils/dessiner.py cadran/src/main/res/drawable-nodpi"""
import math, sys
from PIL import Image, ImageDraw

OUT = sys.argv[1]
S, C = 450, 225
SS = 4  # suréchantillonnage pour l'anticrénelage


def big(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def small(img, w, h):
    return img.resize((w, h), Image.LANCZOS)


def fond():
    img = big(S, S)
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, S * SS, S * SS], fill=(0, 0, 0, 255))
    for i in range(60):
        a = math.radians(i * 6)
        if i % 5 == 0:
            if i == 15:  # 3 h : remplacé par le guichet de date
                continue
            r1, r2, w = 222, 192, 9
        else:
            r1, r2, w = 222, 212, 3
        x1, y1 = C + r1 * math.sin(a), C - r1 * math.cos(a)
        x2, y2 = C + r2 * math.sin(a), C - r2 * math.cos(a)
        d.line([x1 * SS, y1 * SS, x2 * SS, y2 * SS], fill=(235, 235, 235, 255), width=w * SS)
    # guichet de date (œil à 3 h)
    d.rounded_rectangle([331 * SS, 205 * SS, 385 * SS, 245 * SS], radius=5 * SS,
                        fill=(240, 240, 240, 255), outline=(150, 150, 150, 255), width=2 * SS)
    return small(img, S, S)


def aiguille(w, h, couleur, pivot):
    """Aiguille verticale pointe en haut, pivot à la fraction `pivot` de la hauteur."""
    img = big(w, h)
    d = ImageDraw.Draw(img)
    cx = w * SS / 2
    py = h * SS * pivot
    half = w * SS / 2 - SS
    d.polygon([(cx, 0), (cx + half, h * SS * 0.12), (cx + half * 0.8, h * SS),
               (cx - half * 0.8, h * SS), (cx - half, h * SS * 0.12)],
              fill=(20, 20, 20, 255))
    inner = half - 2 * SS
    d.polygon([(cx, 2 * SS), (cx + inner, h * SS * 0.12), (cx + inner * 0.8, h * SS - 2 * SS),
               (cx - inner * 0.8, h * SS - 2 * SS), (cx - inner, h * SS * 0.12)], fill=couleur)
    r = half
    d.ellipse([cx - r, py - r, cx + r, py + r], fill=couleur, outline=(20, 20, 20, 255), width=SS)
    return small(img, w, h)


def trotteuse(w, h, pivot):
    img = big(w, h)
    d = ImageDraw.Draw(img)
    cx = w * SS / 2
    py = h * SS * pivot
    d.rectangle([cx - SS * 1.5, 0, cx + SS * 1.5, h * SS], fill=(230, 60, 50, 255))
    r = w * SS / 2 - SS
    d.ellipse([cx - r, py - r, cx + r, py + r], fill=(230, 60, 50, 255))
    return small(img, w, h)


HANDS = {
    # nom : (largeur, hauteur, pivot)
    "aiguille_heure": (20, 140, 0.88),
    "aiguille_minute": (16, 205, 0.91),
    "trotteuse": (12, 230, 0.83),
}

f = fond()
f.save(f"{OUT}/fond.png")
imgs = {
    "aiguille_heure": aiguille(*HANDS["aiguille_heure"][:2], (245, 245, 245, 255), HANDS["aiguille_heure"][2]),
    "aiguille_minute": aiguille(*HANDS["aiguille_minute"][:2], (245, 245, 245, 255), HANDS["aiguille_minute"][2]),
    "trotteuse": trotteuse(*HANDS["trotteuse"]),
}
for k, v in imgs.items():
    v.save(f"{OUT}/{k}.png")

# Aperçu (10:08:32, soleil 09:18, glycémie 112, date 1)
from PIL import ImageFont
prev = f.copy()
d = ImageDraw.Draw(prev)
try:
    font_big = ImageFont.truetype("DejaVuSans-Bold.ttf", 44)
    font_small = ImageFont.truetype("DejaVuSans.ttf", 18)
    font_date = ImageFont.truetype("DejaVuSans-Bold.ttf", 26)
except OSError:
    font_big = font_small = font_date = ImageFont.load_default()
d.text((C, 118), "SOL", font=font_small, fill=(255, 193, 7), anchor="mm")
d.text((C, 150), "09:18", font=font_big, fill=(255, 255, 255), anchor="mm")
d.text((C, 300), "112", font=font_big, fill=(255, 255, 255), anchor="mm")
d.text((C, 332), "mg/dL →", font=font_small, fill=(200, 200, 200), anchor="mm")
d.text((358, 225), "1", font=font_date, fill=(0, 0, 0), anchor="mm")
try:
    font_hr = ImageFont.truetype("DejaVuSans-Bold.ttf", 32)
except OSError:
    font_hr = font_small
d.text((83, 202), "♥", font=font_small, fill=(230, 70, 60), anchor="mm")
d.text((83, 236), "68", font=font_hr, fill=(255, 255, 255), anchor="mm")
for name, angle in (("aiguille_heure", 304), ("aiguille_minute", 51), ("trotteuse", 192)):
    w, h, p = HANDS[name]
    hand = imgs[name]
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    canvas.paste(hand, (int(C - w / 2), int(C - h * p)), hand)
    canvas = canvas.rotate(-angle, center=(C, C), resample=Image.BICUBIC)
    prev = Image.alpha_composite(prev, canvas)
prev.save(f"{OUT}/preview.png")
print("ok")
