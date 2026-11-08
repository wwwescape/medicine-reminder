"""Generates the Medicine Reminder logo and launcher icons.

The glyph is Material Symbols Outlined "prescriptions" (FILL 0, wght 400, GRAD 0, opsz 24): a
clipboard with a capsule, for the app's courses of medicine and their prescriptions.

Outputs follow the sibling apps' conventions: #2A7FFF glyph on white, a monochrome variant for
themed icons, a white status-bar icon, assets/logo.svg, and design/ PNGs for the Play Store.

Usage: python scripts/generate_icons.py   (needs resvg-py: pip install resvg-py)
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
# The Wear OS companion uses the same launcher and status-bar icons.
WEAR_RES = ROOT / "wear" / "src" / "main" / "res"
WEAR_SHARED = {
    "drawable/ic_launcher_foreground.xml",
    "drawable/ic_launcher_monochrome.xml",
    "drawable/ic_launcher_background.xml",
    "drawable/ic_stat_prescriptions.xml",
    "mipmap-anydpi-v26/ic_launcher.xml",
    "mipmap-anydpi-v26/ic_launcher_round.xml",
}
BLUE = "#2A7FFF"

# prescriptions, viewBox 0 -960 960 960.
GLYPH = (
    "m678-134 46-46-64-64-46 46q-14 14-14 32t14 32q14 14 32 14t32-14Zm102-102 46-46q14-14 14-32"
    "t-14-32q-14-14-32-14t-32 14l-46 46 64 64ZM735-77q-37 37-89 37t-89-37q-37-37-37-89t37-89l148-148"
    "q37-37 89-37t89 37q37 37 37 89t-37 89L735-77ZM200-200v-560 560Zm0 80q-33 0-56.5-23.5T120-200"
    "v-560q0-33 23.5-56.5T200-840h168q13-36 43.5-58t68.5-22q38 0 68.5 22t43.5 58h168q33 0 56.5 23.5"
    "T840-760v245q-20-5-40-5t-40 3v-243H200v560h243q-3 20-3 40t5 40H200Zm280-670q13 0 21.5-8.5"
    "T510-820q0-13-8.5-21.5T480-850q-13 0-21.5 8.5T450-820q0 13 8.5 21.5T480-790ZM280-600v-80h400v80"
    "H280Zm0 160v-80h400v34q-8 5-15.5 11.5T649-460l-20 20H280Zm0 160v-80h269l-49 49q-8 8-14.5 15.5"
    "T474-280H280Z"
)


def vector(width_dp, viewport, body):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width_dp}dp"\n'
        f'    android:height="{width_dp}dp"\n'
        f'    android:viewportWidth="{viewport}"\n'
        f'    android:viewportHeight="{viewport}">\n'
        f"{body}"
        "</vector>\n"
    )


def path(color, indent="    "):
    return (
        f"{indent}<path\n"
        f'{indent}    android:fillColor="{color}"\n'
        f'{indent}    android:pathData="{GLYPH}" />\n'
    )


def group(scale, tx, ty, color):
    return (
        "    <group\n"
        f'        android:scaleX="{scale}"\n'
        f'        android:scaleY="{scale}"\n'
        f'        android:translateX="{tx}"\n'
        f'        android:translateY="{ty}">\n'
        f"{path(color, '        ')}"
        "    </group>\n"
    )


def write(rel, text):
    targets = [RES / rel if not rel.startswith("/") else ROOT / rel[1:]]
    if rel in WEAR_SHARED:
        targets.append(WEAR_RES / rel)
    for target in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")


def main():
    # Adaptive icon: 108dp canvas with the glyph inside the 66dp safe zone (same placement as
    # the sibling apps).
    write("drawable/ic_launcher_foreground.xml", vector(108, 1200, group(0.5625, 330, 870, BLUE)))
    write("drawable/ic_launcher_monochrome.xml", vector(108, 1200, group(0.5625, 330, 870, "#FFFFFFFF")))
    write("drawable-nodpi/splash_icon.xml", vector(108, 1200, group(0.5625, 330, 870, BLUE)))
    write(
        "drawable/ic_launcher_background.xml",
        vector(108, 108, '    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M0,0h108v108h-108z" />\n'),
    )
    write(
        "drawable-nodpi/ic_app_icon.xml",
        vector(
            48,
            1200,
            '    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M0,0h1200v1200h-1200z" />\n'
            + group(0.8125, 210, 990, BLUE),
        ),
    )
    write("drawable-nodpi/ic_logo_mark.xml", vector(48, 960, '    <group android:translateY="960">\n' + path(BLUE, "        ") + "    </group>\n"))
    write("drawable/ic_stat_prescriptions.xml", vector(24, 960, '    <group android:translateY="960">\n' + path("#FFFFFFFF", "        ") + "    </group>\n"))
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    write("mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write("mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

    logo = f'<svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="{BLUE}"><path d="{GLYPH}"/></svg>\n'
    write("/assets/logo.svg", logo)

    import resvg_py

    def png(svg, out):
        (ROOT / "design").mkdir(exist_ok=True)
        (ROOT / "design" / out).write_bytes(bytes(resvg_py.svg_to_bytes(svg_string=svg)))

    # Play Store icon: full-bleed white square, glyph at the same proportion as the launcher icon.
    png(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 1200 1200">'
        f'<rect width="1200" height="1200" fill="#fff"/>'
        f'<g transform="translate(210 990) scale(0.8125)"><path fill="{BLUE}" d="{GLYPH}"/></g></svg>',
        "logo-playstore-512.png",
    )
    png(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="960" viewBox="0 -960 960 960">'
        f'<path fill="{BLUE}" d="{GLYPH}"/></svg>',
        "logo-source.png",
    )


if __name__ == "__main__":
    main()
