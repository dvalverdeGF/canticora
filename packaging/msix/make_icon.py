#!/usr/bin/env python3
"""Aplica el PACK DE ICONOS de Canticora con el logo TRANSPARENTE a pleno tamano.

Fuente: `dist/logos/imagotip.png` (1000x1000, RGBA, sin fondo). Es el maestro de todo lo que se
GENERA: el logo se recorta a su caja alfa, se encuadra en un cuadrado y se reescala a cada
tamano EXACTO con alfa premultiplicado (sin halos oscuros en los bordes) y LANCZOS.

NO se usa `macos/AppIcon.iconset/*` ni `android/playstore-icon.png` (traen placa blanca) ni
`windows/SplashScreen.png` / `Wide310x150Logo.png` (fondo blanco).

Windows / MSIX (todo transparente, a su tamano):
  - laudate.ico: 16, 20, 24, 32, 40, 48, 64, 96, 128, 256 pre-renderizados (no reescalados al vuelo).
  - Square44x44 (LaudateAppList) + `targetsize-N` (+ `_altform-unplated` y `-lightunplated`), a
    sangre: el logo llena el icono para que la barra de tareas lo muestre grande y sin placa.
  - Square71, Square150, Square310, Wide310x150, StoreLogo y SplashScreen, con variante `scale-200`.
  - app_logo.png (icono de la ventana/barra de tareas en escritorio, y logo in-app).

Android: los mipmaps del pack se copian tal cual (ya son transparentes/adaptativos).

Uso:  python3 packaging/msix/make_icon.py
"""
import os
import shutil

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PACK = os.path.join(ROOT, "dist", "logos", "my-app-app-icons")
MSIX = os.path.join(ROOT, "packaging", "msix")
ASSETS = os.path.join(MSIX, "Assets")
RES = os.path.join(ROOT, "app", "src", "main", "res")
# Maestro TRANSPARENTE de alta resolucion (1000x1000).
MASTER = os.path.join(ROOT, "dist", "logos", "imagotip.png")

DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
APP_LIST_SIZES = [16, 20, 24, 30, 32, 36, 40, 44, 48, 60, 64, 72, 80, 96, 256]
ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
# (archivo, ancho, alto, fraccion del lado menor que ocupa el logo)
TILES = [
    ("Square71x71Logo", 71, 71, 0.92),
    ("Square150x150Logo", 150, 150, 0.90),
    ("Square310x310Logo", 310, 310, 0.90),
    ("Wide310x150Logo", 310, 150, 0.86),
    ("StoreLogo", 50, 50, 0.96),
    ("SplashScreen", 620, 300, 0.62),
]
# Los iconos de lista/barra de tareas van a sangre (Windows ya aporta el margen).
APP_LIST_FILL = 1.0
_square_cache: Image.Image | None = None


def _square_master() -> Image.Image:
    """Logo recortado a su caja alfa y centrado en un lienzo cuadrado transparente."""
    global _square_cache
    if _square_cache is None:
        src = Image.open(MASTER).convert("RGBA")
        logo = src.crop(src.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox())
        side = max(logo.size)
        canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        canvas.alpha_composite(logo, ((side - logo.width) // 2, (side - logo.height) // 2))
        _square_cache = canvas
    return _square_cache


def _resize(img: Image.Image, w: int, h: int) -> Image.Image:
    """Reescala con alfa premultiplicado (evita bordes oscuros) y realce suave en tamanos pequenos."""
    out = img.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
    if max(w, h) <= 64:
        rgb = out.convert("RGB").filter(ImageFilter.UnsharpMask(radius=0.6, percent=60, threshold=0))
        rgb.putalpha(out.getchannel("A"))
        out = rgb
    return out


def _render(width: int, height: int, fill: float) -> Image.Image:
    """Logo cuadrado contenido en `fill` del lado menor, centrado sobre lienzo transparente."""
    side = max(1, round(min(width, height) * fill))
    logo = _resize(_square_master(), side, side)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.alpha_composite(logo, ((width - side) // 2, (height - side) // 2))
    return canvas


def copy_android():
    for d in DENSITIES:
        src = os.path.join(PACK, "android", "res", f"mipmap-{d}")
        dst = os.path.join(RES, f"mipmap-{d}")
        for f in os.listdir(src):
            shutil.copy2(os.path.join(src, f), os.path.join(dst, f))
    for sub in ["mipmap-anydpi-v26", "values"]:
        src = os.path.join(PACK, "android", "res", sub)
        dst = os.path.join(RES, sub)
        for f in os.listdir(src):
            shutil.copy2(os.path.join(src, f), os.path.join(dst, f))
    # Logo in-app SOLO como PNG de Android (mipmap/drawable). El logo de los recursos
    # compartidos de Compose es un VECTOR (`core/ui/.../composeResources/drawable/app_logo.xml`);
    # NO escribir aquí un PNG con el mismo nombre o chocarían los dos recursos.
    logo = _render(512, 512, 0.96)
    logo.save(os.path.join(RES, "drawable", "app_logo.png"))
    print("Android: mipmaps verbatim + app_logo transparente")


def _save_ico(path: str):
    frames = [_render(n, n, APP_LIST_FILL) for n in ICO_SIZES]
    frames[-1].save(path, format="ICO", sizes=[(n, n) for n in ICO_SIZES], append_images=frames[:-1])


def generate_windows():
    os.makedirs(ASSETS, exist_ok=True)
    _save_ico(os.path.join(MSIX, "laudate.ico"))
    for name, w, h, fill in TILES:
        _render(w, h, fill).save(os.path.join(ASSETS, f"{name}.png"), optimize=True)
        _render(w * 2, h * 2, fill).save(os.path.join(ASSETS, f"{name}.scale-200.png"), optimize=True)
    # Base del icono de la lista (tareas/Inicio) y sus variantes por tamano.
    _render(44, 44, APP_LIST_FILL).save(os.path.join(ASSETS, "LaudateAppList.png"), optimize=True)
    for s in APP_LIST_SIZES:
        r = _render(s, s, APP_LIST_FILL)
        for suffix in ["", "_altform-unplated", "_altform-lightunplated"]:
            r.save(os.path.join(ASSETS, f"LaudateAppList.targetsize-{s}{suffix}.png"), optimize=True)
    print(f"Windows: .ico ({len(ICO_SIZES)} tamanos), {len(TILES)} logos (+scale-200), "
          f"{len(APP_LIST_SIZES) * 3} variantes targetsize; todo transparente")


def main():
    if not os.path.isdir(PACK):
        raise SystemExit("No encuentro el pack de iconos: " + PACK)
    copy_android()
    generate_windows()
    print("Listo (sin fondo).")


if __name__ == "__main__":
    main()
