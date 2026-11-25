#!/usr/bin/env python3
"""Syntax-check the inline JavaScript bundled in the Android WebView."""
from pathlib import Path
import subprocess
import tempfile

html = Path("app/src/main/assets/index.html").read_text(encoding="utf-8")
start = html.find("<script>")
end = html.find("</script>", start + 8)
if start < 0 or end < 0:
    raise SystemExit("No se encontró el bloque JavaScript principal")
script = html[start + 8:end]
if not script.strip():
    raise SystemExit("El bloque JavaScript está vacío")
with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
    handle.write(script)
    temp_path = handle.name
try:
    result = subprocess.run(["node", "--check", temp_path], check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
finally:
    Path(temp_path).unlink(missing_ok=True)
print("JavaScript del WebView: sintaxis correcta")
