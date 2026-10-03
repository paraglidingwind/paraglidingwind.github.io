#!/usr/bin/env python3
"""Generează docs/index.html (GitHub Pages) din src/index.html + data/*.json."""
import json, pathlib
root = pathlib.Path(__file__).parent
ro = (root / "data/ro_sites.json").read_text()
init = (root / "data/initial_sites.json").read_text()
html = (root / "src/index.html").read_text().replace("__RO_SITES__", ro).replace("__DEFAULT_SITES__", init)
(root / "docs/index.html").write_text(html)
print("docs/index.html", len(html), "bytes")
