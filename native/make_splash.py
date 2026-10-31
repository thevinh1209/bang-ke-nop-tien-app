"""Thay mọi splash.png mặc định của Capacitor bằng màn hình khởi động dùng icon VH3.
Dùng:  python make_splash.py <thư mục res> <icon.png>"""
import sys, pathlib
from PIL import Image, ImageDraw, ImageFilter

res, icon = pathlib.Path(sys.argv[1]), Image.open(sys.argv[2]).convert("RGB")
for f in res.rglob("splash.png"):
    w, h = Image.open(f).size
    m = max(w, h)
    bg = icon.resize((m, m), Image.LANCZOS).filter(ImageFilter.GaussianBlur(m * 0.05))
    bg = bg.crop(((m - w) // 2, (m - h) // 2, (m - w) // 2 + w, (m - h) // 2 + h)).convert("RGBA")
    s = int(min(w, h) * 0.38)
    logo = icon.resize((s, s), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.2), fill=255)
    bg.paste(logo, ((w - s) // 2, (h - s) // 2), mask)
    bg.convert("RGB").save(f)
    print("splash:", f, w, h)
