"""Draws the shop's brass plates and the newspaper. Run with Pillow: make_plates.py <assets_lab/clock_v2> <font.otf> <out textures dir>"""
import sys
from PIL import Image, ImageDraw, ImageFont
src, font, out = sys.argv[1:4]
def plate(name, lines, sizes, ink=(44, 26, 10)):
    im = Image.open(f"{src}/plate_blank.jpeg").convert("RGB").resize((768, 768), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    fonts = [ImageFont.truetype(font, s) for s in sizes]
    heights = [f.getbbox(t)[3] - f.getbbox(t)[1] for f, t in zip(fonts, lines)]
    y = (768 - sum(heights) - 26 * (len(lines) - 1)) / 2
    for t, f, h in zip(lines, fonts, heights):
        b = f.getbbox(t)
        x = (768 - (b[2] - b[0])) / 2 - b[0]
        d.text((x + 3, y - b[1] + 3), t, font=f, fill=(250, 222, 150))
        d.text((x, y - b[1]), t, font=f, fill=ink)
        y += h + 26
    im.save(f"{out}/{name}.jpg", quality=88)
import math
INK = (44, 26, 10)
def sun(d, cx, cy, r):
    for i in range(12):
        a = i * math.pi / 6
        d.line((cx + math.cos(a) * r * 1.25, cy + math.sin(a) * r * 1.25, cx + math.cos(a) * r * 1.8, cy + math.sin(a) * r * 1.8), fill=INK, width=max(4, int(r / 8)))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=INK)
def moon(d, cx, cy, r):
    # A crescent as one polygon: outer arc and the inner arc back.
    outer = [(cx + r * math.cos(t), cy + r * math.sin(t)) for t in [math.pi * (.5 + i / 40) for i in range(41)]]
    inner = [(cx - r * .55 + r * 1.05 * math.cos(t), cy + r * 1.05 * math.sin(t)) for t in [math.pi * (1.5 - .92 + i / 40 * 0) for i in range(0)]]
    ox, oy, ir = cx + r * .42, cy - r * .08, r * .86
    a0 = math.atan2(cy - r - oy, cx - ox)
    inner = [(ox + ir * math.cos(math.pi * (1.42 - i * .84 / 40)), oy + ir * math.sin(math.pi * (1.42 - i * .84 / 40))) for i in range(41)]
    d.polygon(outer + inner, fill=INK)
def star(d, cx, cy, r):
    p = [(cx + (r if i % 2 == 0 else r * .42) * math.sin(i * math.pi / 5), cy - (r if i % 2 == 0 else r * .42) * math.cos(i * math.pi / 5)) for i in range(10)]
    d.polygon(p, fill=INK)
def icon_plate(name, draw, text=None):
    im = Image.open(f"{src}/plate_blank.jpeg").convert("RGB").resize((768, 768), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    cy = 384 if text is None else 300
    draw(d, 384, cy, 150 if text is None else 110)
    if text:
        f = ImageFont.truetype(font, 120)
        d.text((384, 540), text, font=f, fill=INK, anchor="mm")
    im.save(f"{out}/{name}.jpg", quality=88)
# The mantel's back: 三 and the sun beside it.
im = Image.open(f"{src}/plate_blank.jpeg").convert("RGB").resize((768, 768), Image.LANCZOS)
d = ImageDraw.Draw(im); sun(d, 384, 220, 62); d.text((384, 470), "三", font=ImageFont.truetype(font, 300), fill=INK, anchor="mm")
im.save(f"{out}/plate_san.jpg", quality=88)
icon_plate("plate_sun", sun, "3 点"); icon_plate("plate_moon", moon, "10 点"); icon_plate("plate_star", star, "4 点")
icon_plate("mark_sun", sun); icon_plate("mark_moon", moon); icon_plate("mark_star", star)
# A dial stopped at three.
im = Image.new("RGB", (512, 512), (232, 224, 200)); d = ImageDraw.Draw(im)
d.ellipse((6, 6, 506, 506), outline=(40, 30, 20), width=14)
for i in range(12):
    a = i * math.pi / 6; d.line((256 + math.sin(a) * 200, 256 - math.cos(a) * 200, 256 + math.sin(a) * 232, 256 - math.cos(a) * 232), fill=(30, 22, 14), width=12 if i % 3 == 0 else 6)
d.line((256, 256, 256, 70), fill=(20, 16, 10), width=12); d.line((256, 256, 400, 256), fill=(20, 16, 10), width=18); d.ellipse((238, 238, 274, 274), fill=(20, 16, 10))
im.save(f"{out}/dial_three.jpg", quality=88)
# Floor inlay: the hours the sun's patch passes, ending at III by the display case.
im = Image.open(f"{src}/parquet.jpeg").convert("RGB").resize((256, 1024), Image.LANCZOS); d = ImageDraw.Draw(im)
d.rectangle((0, 0, 255, 1023), outline=(150, 112, 48), width=10)
for i, t in enumerate(["III", "II", "I", "XII", "XI", "X", "IX"]):
    d.text((128, 72 + i * 146), t, font=ImageFont.truetype(font, 74), fill=(190, 148, 66), anchor="mm")
im.save(f"{out}/floor_hours.jpg", quality=88)
for n in (2, 3, 4, 5):
    plate(f"plate_r{n}", [f"{n}:1"], [300])
plate("plate_rule", ["一来一回 两秒", "分针十二圈", "时针走一圈"], [68, 68, 68])
n = Image.open(f"{src}/news_blank.jpeg").convert("RGB").resize((1024, 1024), Image.LANCZOS)
d = ImageDraw.Draw(n)
d.rectangle((60, 40, 964, 210), fill=(226, 212, 176))
big = ImageFont.truetype(font, 92); small = ImageFont.truetype(font, 54)
d.text((512, 100), "城 中 晚 报", font=big, fill=(30, 22, 14), anchor="mm")
d.text((512, 178), "1936 年 3 月 12 日　星期四", font=small, fill=(30, 22, 14), anchor="mm")
n.save(f"{out}/news.jpg", quality=88)
print("PLATES-DONE")
