#!/bin/sh
# Publică ramura curentă pe mediul de TEST: https://paraglidingwind-test.github.io
# (repo paraglidingwind-test/paraglidingwind-test.github.io, GitHub Pages din rădăcina lui main).
# Nu atinge paraglidingwind.com: fără CNAME, cu bandă „TEST” și fără indexare în motoarele de căutare.
set -e
cd "$(dirname "$0")/.."
REPO=paraglidingwind-test/paraglidingwind-test.github.io
EMAIL=$(git config user.email); NAME=$(git config user.name)
python3 build.py >/dev/null
SRC="$(git branch --show-current) $(git rev-parse --short HEAD)$(git status --porcelain src data build.py | grep -q . && echo +)"
TMP=$(mktemp -d)
if ! git clone -q "https://github.com/$REPO.git" "$TMP" 2>/dev/null; then git -C "$TMP" init -q -b main; git -C "$TMP" remote add origin "https://github.com/$REPO.git"; fi
find "$TMP" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -R docs/. "$TMP/"
rm -f "$TMP/CNAME"
printf 'User-agent: *\nDisallow: /\n' > "$TMP/robots.txt"
python3 - "$TMP/index.html" "$SRC" <<'PY'
import sys, pathlib
p, src = pathlib.Path(sys.argv[1]), sys.argv[2]
s = p.read_text()
s = s.replace('<meta charset="utf-8">', '<meta charset="utf-8">\n<meta name="robots" content="noindex,nofollow">', 1)
s = s.replace('<title>Vânt la Decolare</title>', '<title>TEST · Vânt la Decolare</title>', 1)
s = s.replace('<body>', '<body>\n<div style="height:20px;display:flex;align-items:center;justify-content:center;background:#6d28d9;color:#fff;font:700 11px/1 system-ui,sans-serif;letter-spacing:.05em">VERSIUNE DE TEST · ' + src + ' · nu e paraglidingwind.com</div>\n<style>.app{height:calc(100% - 20px)}</style>', 1)
p.write_text(s)
PY
git -C "$TMP" add -A
git -C "$TMP" -c user.email="$EMAIL" -c user.name="$NAME" commit -q -m "Test: $SRC" || echo "nimic nou de publicat"
git -C "$TMP" push -q origin HEAD:main
rm -rf "$TMP"
echo "publicat pe https://paraglidingwind-test.github.io ($SRC)"
