#!/bin/sh
# Verificarea locală înainte de commit: build, teste web, teste Android.
# Nu face push și nu publică nimic.
set -e
cd "$(dirname "$0")"

echo "== build"
python3 build.py

# Întâi Android: testul Xlsx scrie exportul Kotlin, pe care testul web îl compară octet cu octet.
echo "== teste Android"
(cd android && gradle testDebugUnitTest --console=plain -q)
echo "Android: OK"

echo "== teste web (logica, traduceri, contrast, Excel)"
node --test tests/*.test.mjs

if ! git diff --quiet -- docs android/app/src/main/assets android/app/src/main/java/ro/parapanta/vant/ui/SkyIcons.kt; then
  echo "== atenție: build-ul a schimbat fișiere generate (docs/, assets). Include-le în commit împreună cu sursa."
fi
echo "== totul e în regulă"
