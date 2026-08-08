"""Compose Galaxy Store listing assets from raw device captures.

Keeps to the app's own palette and type so the listing and the product read as one thing — a store
page in a different visual language is the first thing that makes an app look assembled rather than
made.
"""
import pathlib
from PIL import Image, ImageDraw, ImageFont

SP = pathlib.Path("/private/tmp/claude-501/-Users-amritha-mymak/19c354b1-749b-49ac-aaef-9408ac814f89/scratchpad")
OUT = pathlib.Path("/Users/amritha/twofold/docs/store")
OUT.mkdir(parents=True, exist_ok=True)
FONTS = pathlib.Path("/Users/amritha/twofold/app/src/main/res/font")

PAPER = (246, 243, 238)
INK = (27, 25, 23)
INK_MUTED = (110, 103, 92)
SEAL = (122, 52, 40)
RULE = (224, 217, 206)

W, H = 1600, 2000


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def wrap(draw, text, fnt, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = f"{cur} {w}".strip()
        if draw.textlength(trial, font=fnt) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def screenshot(src, headline, sub, dest):
    canvas = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(canvas)

    head_f = font("source_serif_4.ttf", 74)
    sub_f = font("inter.ttf", 40)

    margin = 90
    y = 120
    for line in wrap(d, headline, head_f, W - 2 * margin):
        d.text((margin, y), line, font=head_f, fill=INK)
        y += 92
    y += 18
    for line in wrap(d, sub, sub_f, W - 2 * margin):
        d.text((margin, y), line, font=sub_f, fill=INK_MUTED)
        y += 54

    y += 46
    d.line([(margin, y), (margin + 120, y)], fill=SEAL, width=5)
    y += 60

    shot = Image.open(SP / src).convert("RGB")
    avail_w, avail_h = W - 2 * margin, H - y - margin
    scale = min(avail_w / shot.width, avail_h / shot.height)
    shot = shot.resize((int(shot.width * scale), int(shot.height * scale)), Image.LANCZOS)

    # Centre what's left over. A foldable capture is nearly square, so on a portrait canvas it
    # never fills the space — better balanced than pinned to the top with a dead strip beneath.
    y += max(0, (avail_h - shot.height) // 2)
    x = (W - shot.width) // 2
    # Hairline edge so the capture reads as a device screen rather than bleeding into the page
    d.rectangle([x - 2, y - 2, x + shot.width + 1, y + shot.height + 1], outline=RULE, width=2)
    canvas.paste(shot, (x, y))

    canvas.save(OUT / dest, "PNG")
    print(f"  {dest}  {canvas.width}x{canvas.height}")


def promo(dest):
    """16:9 — the ratio Galaxy Store considers for its own promotions."""
    pw, ph = 1920, 1080
    canvas = Image.new("RGB", (pw, ph), PAPER)
    d = ImageDraw.Draw(canvas)

    shot = Image.open(SP / "shot_twofold.png").convert("RGB")
    scale = (ph - 150) / shot.height
    shot = shot.resize((int(shot.width * scale), int(shot.height * scale)), Image.LANCZOS)
    sx = pw - shot.width - 90
    sy = (ph - shot.height) // 2
    d.rectangle([sx - 2, sy - 2, sx + shot.width + 1, sy + shot.height + 1], outline=RULE, width=2)
    canvas.paste(shot, (sx, sy))

    head_f = font("source_serif_4.ttf", 92)
    sub_f = font("inter.ttf", 38)
    max_w = sx - 200

    y = 300
    for line in wrap(d, "One device. Two sides of the table.", head_f, max_w):
        d.text((100, y), line, font=head_f, fill=INK)
        y += 112
    y += 24
    d.line([(100, y), (220, y)], fill=SEAL, width=5)
    y += 52
    for line in wrap(d, "They read the document. You keep your notes.", sub_f, max_w):
        d.text((100, y), line, font=sub_f, fill=INK_MUTED)
        y += 52

    canvas.save(OUT / dest, "PNG")
    print(f"  {dest}  {pw}x{ph}")


def icon(dest, size=512):
    """512px store icon, drawn from the same marks as the launcher icon."""
    s = size
    canvas = Image.new("RGB", (s, s), PAPER)
    d = ImageDraw.Draw(canvas)
    u = s / 108.0

    d.rectangle([26 * u, 24 * u, 82 * u, 84 * u], fill=(255, 253, 249), outline=RULE, width=max(1, int(u)))
    for x0, y0, x1 in [(34, 36, 68), (34, 44, 74), (34, 52, 60)]:
        d.rectangle([x0 * u, y0 * u, x1 * u, (y0 + 3.2) * u], fill=INK)
    d.rectangle([26 * u, 58.6 * u, 82 * u, 60.2 * u], fill=SEAL)
    for x0, y0, x1 in [(40, 64, 74), (34, 71, 74), (48, 78, 74)]:
        d.rectangle([x0 * u, y0 * u, x1 * u, (y0 + 3.2) * u], fill=INK_MUTED)

    canvas.save(OUT / dest, "PNG")
    print(f"  {dest}  {s}x{s}")


print("screenshots:")
screenshot("shot_twofold.png",
           "One device. Two sides of the table.",
           "They read the document. You keep your notes, your talk track, and the controls.",
           "01-two-sided.png")
screenshot("shot_prepare.png",
           "Prepare before you sit down.",
           "Write a private note and a talk track for each page. Held in your hand, it is yours alone.",
           "02-prepare.png")
screenshot("shot_spotlight.png",
           "Point at a clause.",
           "Drag across your copy and it lights up on theirs. No reaching across the table.",
           "03-spotlight.png")
screenshot("shot_signing.png",
           "They sign on their half.",
           "You never hand over your phone. The signed copy carries the time and a fingerprint of the document.",
           "04-signature.png")
print("promo:")
promo("promo-16x9.png")
print("icon:")
icon("icon-512.png")
