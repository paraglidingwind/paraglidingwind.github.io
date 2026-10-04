#!/usr/bin/env python3
"""Generează docs/index.html (GitHub Pages) din src/index.html + data/*.json."""
import datetime, json, pathlib, subprocess
root = pathlib.Path(__file__).parent
init = (root / "data/initial_sites.json").read_text()
icons = json.loads((root / "data/sky_icons.json").read_text())
# Versiunea afișată în pagină: data build-ului + commitul de pornire (cu „+” dacă sursa avea modificări necomise).
try:
    rev = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=root, capture_output=True, text=True).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain", "src", "data", "build.py"], cwd=root, capture_output=True, text=True).stdout.strip()
    rev += "+" if dirty else ""
except OSError:
    rev = ""
version = datetime.date.today().strftime("%Y.%m.%d") + (f" · {rev}" if rev else "")
html = (root / "src/index.html").read_text().replace("__CORE_JS__", (root / "src/core.js").read_text().rstrip()) \
    .replace("__DEFAULT_SITES__", init) \
    .replace("__SKY_ICONS__", json.dumps(icons, separators=(",", ":"))) \
    .replace("__I18N_EN__", json.dumps(json.loads((root / "data/i18n_en.json").read_text()), ensure_ascii=False, separators=(",", ":"))) \
    .replace("__GOOGLE_CLIENT_ID__", json.dumps(json.loads((root / "data/google.json").read_text())["clientId"])) \
    .replace("__VERSION__", version)
assert "__" + "CORE_JS__" not in html
(root / "docs/index.html").write_text(html)
print("docs/index.html", len(html), "bytes ·", version)

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

# Decolările din toată lumea și stațiile Holfuy, încărcate de pagină la nevoie.
for src, dst in (("world_sites.json", "sites.json"), ("holfuy_world.json", "holfuy.json")):
    (root / "docs" / dst).write_text((root / "data" / src).read_text())
print("docs/sites.json + docs/holfuy.json")

# ONB-05: aceleași decolări împărțite pe țări (România ≈ 16 KB în loc de 1 MB) + un index cu numărul și chenarul fiecărei țări.
# Codurile stricate din ParaglidingEarth (ex. „C ”, gol) ajung în „XX”.
import re, shutil
by = {}
for x in json.loads((root / "data/world_sites.json").read_text()):
    cc = x.get("c", "").strip().upper()
    by.setdefault(cc if re.fullmatch(r"[A-Z]{2}", cc) else "XX", []).append({k: x[k] for k in ("n", "lat", "lon", "alt", "o") if k in x})
out = root / "docs/sites"
shutil.rmtree(out, ignore_errors=True); out.mkdir()
# Index: pentru fiecare țară, câte decolări are în fiecare celulă de 2°×2° („46,22” = lat 46–48, lon 22–24).
# Celulele (nu chenarul țării) evită teritoriile îndepărtate: Franța nu „atinge” Clujul prin Réunion sau Noua Caledonie.
index = {}
for cc, lst in sorted(by.items()):
    (out / f"{cc}.json").write_text(json.dumps(lst, ensure_ascii=False, separators=(",", ":")))
    cells = {}
    for x in lst:
        k = f"{int(x['lat'] // 2 * 2)},{int(x['lon'] // 2 * 2)}"
        cells[k] = cells.get(k, 0) + 1
    index[cc] = {"n": len(lst), "c": cells}
(out / "index.json").write_text(json.dumps(index, separators=(",", ":")))
print(f"docs/sites/: {len(by)} țări, România {len(by.get('RO', []))} decolări")
