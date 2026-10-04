#!/bin/sh
# Verificarea locală înainte de commit: build, teste web, teste Android.
# Nu face push și nu publică nimic.
set -e
cd "$(dirname "$0")"

echo "== build"
python3 build.py

echo "== teste web (logica, traduceri, contrast)"
node --test tests/*.test.mjs

echo "== teste Android"
(cd android && gradle testDebugUnitTest --console=plain -q)
echo "Android: OK"

if ! git diff --quiet -- docs android/app/src/main/assets android/app/src/main/java/ro/parapanta/vant/ui/SkyIcons.kt; then
  echo "== atenție: build-ul a schimbat fișiere generate (docs/, assets). Include-le în commit împreună cu sursa."
fi
echo "== totul e în regulă"
