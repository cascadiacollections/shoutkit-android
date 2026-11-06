"""Hand-built geometric vector of the iOS ShoutKit beacon (measured from
Squan-AppIcon.icon/Assets/Radio beacon.png, 1024px space) -> Android vector drawables + preview SVGs."""
import sys
from pathlib import Path

HERE = Path(__file__).parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else None

WHITE, TEAL, CORAL, NAVY = "#F7FFFD", "#13A8B4", "#FF826E", "#0A526F"
BG_TOP, BG_BOTTOM = "#3AA0FF", "#0088FF"  # icon.json fill #0088FF (extended-srgb 0,0.533,1), automatic gradient
CX = 511.5


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,1 {f(2 * r)},0a{f(r)},{f(r)} 0 1,1 {f(-2 * r)},0z"


def rrect(x0, y0, x1, y1, r):
    w, h = x1 - x0, y1 - y0
    return (f"M{f(x0 + r)},{f(y0)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0,1 {f(r)},{f(r)}v{f(h - 2 * r)}"
            f"a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(r)}h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(-r)}"
            f"v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0 0,1 {f(r)},{f(-r)}z")


def hw(y):  # tower half-width (tapers from 68.9 at y=520 to 109.3 at y=819)
    return 68.9 + 0.1353 * (y - 520)


def trap(y0, y1, inset=0.0):
    a, b = hw(y0) - inset, hw(y1) - inset
    return f"M{f(CX - a)},{f(y0)}L{f(CX + a)},{f(y0)}L{f(CX + b)},{f(y1)}L{f(CX - b)},{f(y1)}z"


ball = circle(CX, 262, 34)
antenna = f"M497,285h29v75h-29z"
roof = f"M{f(CX)},341L629,431.5L394,431.5z"
lantern = rrect(419.5, 431, 603.5, 520, 18)
window = rrect(465, 455, 558.5, 499, 13)
tower = trap(519, 820)
base = rrect(341.5, 819, 682, 857, 19)
teal = trap(620, 692, 3)
coral = trap(759, 819.5, 3)

COLOR = [(WHITE, ball), (WHITE, antenna), (WHITE, roof), (WHITE, lantern), (WHITE, tower), (WHITE, base),
         (TEAL, teal), (CORAL, coral), (NAVY, window)]
# Themed-icon glyph: the window and the teal band knocked out (band cut across the full tower width).
MONO = [(ball, False), (antenna, False), (roof, False), (lantern + window, True),
        (trap(519, 620), False), (trap(692, 820), False), (base, False)]

HDR = '<?xml version="1.0" encoding="utf-8"?>\n'
NOTE = "<!-- ShoutKit beacon, redrawn from the iOS app icon (Squan-AppIcon.icon). Source units: 1024 = the iOS icon tile. -->\n"


def vd_paths(items, indent="        "):
    out = []
    for color, data, eo in items:
        ft = f'\n{indent}    android:fillType="evenOdd"' if eo else ""
        out.append(f'{indent}<path\n{indent}    android:fillColor="{color}"{ft}\n{indent}    android:pathData="{data}" />\n')
    return "".join(out)


def vector(size_dp, vp, tx, ty, items, note=NOTE):
    return (HDR + note + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{vp}"\n    android:viewportHeight="{vp}">\n'
            f'    <group\n        android:translateX="{tx}"\n        android:translateY="{ty}">\n'
            f'{vd_paths(items)}    </group>\n</vector>\n')


color_items = [(c, d, False) for c, d in COLOR]
mono_items = [("#FFFFFFFF", d, eo) for d, eo in MONO]

# 108dp canvas = 1536 units; the iOS 1024 tile maps onto the central 72dp (offset 256).
files = {
    "drawable/ic_launcher_foreground.xml": vector(108, 1536, 256, 256, color_items),
    "drawable/ic_launcher_monochrome.xml": vector(108, 1536, 256, 256, mono_items),
    # 24dp tile/notification-style glyph cropped to the beacon (x 341..682, y 228..857).
    "drawable/ic_tile_beacon.xml": vector(24, 680, 340 - CX, 340 - 542.5, mono_items),
    "drawable/ic_launcher_background.xml": (
        HDR + "<!-- iOS icon.json fill #0088FF with a light top-to-bottom gradient. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient\n                android:type="linear"\n'
        '                android:startX="54"\n                android:startY="0"\n'
        '                android:endX="54"\n                android:endY="108"\n'
        f'                android:startColor="{BG_TOP}"\n                android:endColor="{BG_BOTTOM}" />\n'
        '        </aapt:attr>\n    </path>\n</vector>\n'),
}
if OUT:
    for rel, txt in files.items():
        p = OUT / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(txt)


def svg_paths(items):
    return "".join(f'<path fill="{c}"{" fill-rule=\"evenodd\"" if eo else ""} d="{d}"/>' for c, d, eo in items)


bg = (f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{BG_TOP}"/>'
      f'<stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient></defs><rect width="1536" height="1536" fill="url(#g)"/>')
g = lambda items: f'<g transform="translate(256,256)">{svg_paths(items)}</g>'
wrap = lambda inner, s=1536: f'<svg xmlns="http://www.w3.org/2000/svg" width="{s}" height="{s}" viewBox="0 0 {s} {s}">{inner}</svg>'
(HERE / "full.svg").write_text(wrap(bg + g(color_items)))
(HERE / "fg.svg").write_text(wrap(g(color_items)))
(HERE / "mono.svg").write_text(wrap(g(mono_items)))
(HERE / "tile.svg").write_text(wrap(f'<g transform="translate({340 - CX},{340 - 542.5})">{svg_paths(mono_items)}</g>', 680))
