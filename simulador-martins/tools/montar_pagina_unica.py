#!/usr/bin/env python3
"""Junta index.html + styles.css + data.js + app.js num arquivo só (para publicar como página
compartilhável). Uso: python3 tools/montar_pagina_unica.py saida.html"""
import re, sys
from pathlib import Path

base = Path(__file__).resolve().parent.parent
html = (base / "index.html").read_text(encoding="utf-8")
css = (base / "styles.css").read_text(encoding="utf-8")
css = re.sub(r"@font-face\s*{[^}]*}\n?", "", css)  # as fontes vêm do Google Fonts
fonte = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap">')
head = re.search(r"<head>(.*?)</head>", html, re.S).group(1)
titulo = re.search(r"<title>.*?</title>", head).group(0)
corpo = re.search(r"<body>(.*?)</body>", html, re.S).group(1)
corpo = re.sub(r"<script>\s*if \('serviceWorker'.*?</script>", "", corpo, flags=re.S)
corpo = corpo.replace('<script src="data.js"></script>', "<script>" + (base / "data.js").read_text(encoding="utf-8") + "</script>")
corpo = corpo.replace('<script src="app.js"></script>', "<script>" + (base / "app.js").read_text(encoding="utf-8") + "</script>")
saida = (f'{titulo}\n<meta name="claude:viewport-fit-sides" content="cover">\n{fonte}\n'
         f"<style>\n{css}\n</style>\n{corpo}")
Path(sys.argv[1]).write_text(saida, encoding="utf-8")
print(f"OK: {len(saida)//1024} KB")
