#!/usr/bin/env python3
"""Extract the app's inline WebView JavaScript and ask Node to syntax-check it."""
from pathlib import Path
import re
import subprocess
import tempfile

html_path = Path("app/src/main/assets/index.html")
html = html_path.read_text(encoding="utf-8")
scripts = re.findall(r"<script\\b[^>]*>([\\s\\S]*?)</script\\s*>", html, re.IGNORECASE)
inline = [script for script in scripts if script.strip()]
if not inline:
    raise SystemExit("No se encontró JavaScript inline en index.html")
if len(inline) != 1:
    raise SystemExit(f"Se esperaba un script inline principal; encontrados: {len(inline)}")
with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
    handle.write(inline[0])
    temp_path = handle.name
try:
    result = subprocess.run(["node", "--check", temp_path], check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
finally:
    Path(temp_path).unlink(missing_ok=True)
print("JavaScript del WebView: sintaxis correcta")
