#!/usr/bin/env python3
"""Genera los assets del MSIX de Canticora.

DESDE EL PACK DE ICONOS: toda la generacion vive ahora en `make_icon.py`, que COPIA el
pack `dist/logos/my-app-app-icons/` (Android, Windows, web...) sin reescalar y solo crea
las variantes `LaudateAppList.targetsize-*` que Windows exige. Este script se mantiene por
compatibilidad (build_msix.bat / README) y simplemente delega en el.

Uso:  python3 packaging/msix/generate_assets.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_icon  # noqa: E402

if __name__ == "__main__":
    make_icon.main()
