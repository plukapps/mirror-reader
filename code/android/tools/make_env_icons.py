#!/usr/bin/env python3
"""Íconos del lanzador con la etiqueta del entorno (AND-007, ADR 0020).

Toma `ic_launcher_foreground.xml` e `ic_launcher_monochrome.xml` de `src/main` y escribe una copia con la
etiqueta (DEV, STAGE, DEBUG) en el source set de cada variante, que pisa a la de `main`. `prodRelease` usa el
ícono sin etiqueta.

El texto se convierte en trazados con Host Grotesk Bold (`assets/fonts`), así el ícono sigue siendo vectorial.
Requiere fonttools, fuera del build: `pip install fonttools` en un venv, y desde `code/android`:

    python tools/make_env_icons.py
"""
import re
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent / "app" / "src"
FONT = ROOT / "main" / "assets" / "fonts" / "HostGrotesk.ttf"
VARIANTS = {"dev": "DEV", "stage": "STAGE", "prodDebug": "DEBUG"}

INK = "#FF1C0A06"  # libros del ícono
PAPER = "#FFFFFFFF"

# Lienzo de 108 dp; zona segura: círculo de 66 dp de diámetro al centro. La etiqueta entra entera en ella.
BADGE_LEFT, BADGE_RIGHT, BADGE_TOP, BADGE_BOTTOM, BADGE_RADIUS = 28.0, 80.0, 59.0, 74.0, 3.0
BORDER = 1.6  # margen entre la etiqueta y los libros
CAP_HEIGHT = 7.4
MAX_TEXT_WIDTH = BADGE_RIGHT - BADGE_LEFT - 8.0


def rounded_rect(left, top, right, bottom, r):
    return (
        f"M{left + r:.3f},{top:.3f}L{right - r:.3f},{top:.3f}A{r:.3f},{r:.3f} 0 0 1 {right:.3f},{top + r:.3f}"
        f"L{right:.3f},{bottom - r:.3f}A{r:.3f},{r:.3f} 0 0 1 {right - r:.3f},{bottom:.3f}"
        f"L{left + r:.3f},{bottom:.3f}A{r:.3f},{r:.3f} 0 0 1 {left:.3f},{bottom - r:.3f}"
        f"L{left:.3f},{top + r:.3f}A{r:.3f},{r:.3f} 0 0 1 {left + r:.3f},{top:.3f}Z"
    )


def rounded_rect_ccw(left, top, right, bottom, r):
    """Igual que `rounded_rect`, en sentido contrario: dentro de otro contorno, deja un hueco."""
    return (
        f"M{left + r:.3f},{top:.3f}A{r:.3f},{r:.3f} 0 0 0 {left:.3f},{top + r:.3f}"
        f"L{left:.3f},{bottom - r:.3f}A{r:.3f},{r:.3f} 0 0 0 {left + r:.3f},{bottom:.3f}"
        f"L{right - r:.3f},{bottom:.3f}A{r:.3f},{r:.3f} 0 0 0 {right:.3f},{bottom - r:.3f}"
        f"L{right:.3f},{top + r:.3f}A{r:.3f},{r:.3f} 0 0 0 {right - r:.3f},{top:.3f}Z"
    )


def text_path(font, label):
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    names = [cmap[ord(c)] for c in label]
    width_units = sum(hmtx[n][0] for n in names)
    scale = CAP_HEIGHT / font["OS/2"].sCapHeight
    scale = min(scale, MAX_TEXT_WIDTH / width_units)
    cx = (BADGE_LEFT + BADGE_RIGHT) / 2
    cy = (BADGE_TOP + BADGE_BOTTOM) / 2
    x = cx - width_units * scale / 2
    baseline = cy + font["OS/2"].sCapHeight * scale / 2
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.3f}")
    for n in names:
        # La fuente crece hacia arriba; el lienzo, hacia abajo.
        glyphs[n].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
        x += hmtx[n][0] * scale
    return pen.getCommands()


def clipped(source, hole):
    """Envuelve los trazados del ícono en un grupo que no pinta dentro de `hole`."""
    start = source.index("    <path")
    end = source.rindex("</vector>")
    paths = "".join("    " + line + "\n" for line in source[start:end].rstrip().split("\n"))
    clip = f'        <clip-path android:pathData="M0,0L108,0L108,108L0,108Z{hole}" />\n'
    return source[:start] + "    <group>\n" + clip + paths + "    </group>\n" + source[end:]


def badged(source, paths):
    extra = "".join(
        f'    <path\n        android:fillColor="{color}"\n'
        + (f'        android:fillType="evenOdd"\n' if even_odd else "")
        + f'        android:pathData="{data}" />\n'
        for color, data, even_odd in paths
    )
    header = "<!-- Generado por tools/make_env_icons.py (AND-007, ADR 0020). No editar a mano. -->\n"
    body = re.sub(r"</vector>\s*$", extra + "</vector>\n", source)
    return body.replace("?>\n", "?>\n" + header, 1)


def main():
    font = instantiateVariableFont(TTFont(FONT), {"wght": 700})
    foreground = (ROOT / "main/res/drawable/ic_launcher_foreground.xml").read_text()
    monochrome = (ROOT / "main/res/drawable/ic_launcher_monochrome.xml").read_text()
    badge = rounded_rect(BADGE_LEFT, BADGE_TOP, BADGE_RIGHT, BADGE_BOTTOM, BADGE_RADIUS)
    # Hueco alrededor de la etiqueta, hasta el borde de abajo: no asoma la base de los libros.
    hole = rounded_rect_ccw(BADGE_LEFT - BORDER, BADGE_TOP - BORDER, BADGE_RIGHT + BORDER, 112.0,
                            BADGE_RADIUS + BORDER)
    for variant, label in VARIANTS.items():
        text = text_path(font, label)
        out = ROOT / variant / "res" / "drawable"
        out.mkdir(parents=True, exist_ok=True)
        (out / "ic_launcher_foreground.xml").write_text(
            badged(clipped(foreground, hole), [(INK, badge, False), (PAPER, text, False)]))
        # Ícono temático: solo cuenta la opacidad; las letras quedan caladas en la etiqueta.
        (out / "ic_launcher_monochrome.xml").write_text(
            badged(clipped(monochrome, hole), [("#FFFFFFFF", badge + text, True)]))
        print(f"{variant}: {label}")


if __name__ == "__main__":
    main()
