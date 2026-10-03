#!/usr/bin/env python3
"""Generează docs/index.html (GitHub Pages) din src/index.html + data/*.json."""
import json, pathlib
root = pathlib.Path(__file__).parent
ro = (root / "data/ro_sites.json").read_text()
init = (root / "data/initial_sites.json").read_text()
icons = json.loads((root / "data/sky_icons.json").read_text())
html = (root / "src/index.html").read_text().replace("__RO_SITES__", ro).replace("__DEFAULT_SITES__", init) \
    .replace("__SKY_ICONS__", json.dumps(icons, separators=(",", ":"))) \
    .replace("__HOLFUY__", (root / "data/holfuy_ro.json").read_text()) \
    .replace("__GOOGLE_CLIENT_ID__", json.dumps(json.loads((root / "data/google.json").read_text())["clientId"]))
(root / "docs/index.html").write_text(html)
print("docs/index.html", len(html), "bytes")

# Aceleași pictograme pentru aplicația Android.
kt = ["package ro.parapanta.vant.ui", "", "// Generat de build.py din data/sky_icons.json. Nu edita manual.",
      "data class IconPart(val d: String, val role: String, val fill: Boolean)", "", "val SKY_ICONS: Map<String, List<IconPart>> = mapOf("]
for name, parts in icons.items():
    items = ", ".join(f'IconPart("{p["d"]}", "{p["r"]}", {"true" if p.get("f") else "false"})' for p in parts)
    kt.append(f'    "{name}" to listOf({items}),')
kt.append(")")
(root / "android/app/src/main/java/ro/parapanta/vant/ui/SkyIcons.kt").write_text("\n".join(kt) + "\n")
for f in ("ro_sites.json", "initial_sites.json", "holfuy_ro.json"):
    (root / "android/app/src/main/assets" / f).write_text((root / "data" / f).read_text())
print("android SkyIcons.kt + assets")
