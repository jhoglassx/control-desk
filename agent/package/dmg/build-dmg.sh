#!/usr/bin/env bash
set -euo pipefail

APP="$1"
STAGE="$2"
OUT_DMG="$3"
EXTRAS="$4"

rm -rf "$STAGE"
mkdir -p "$STAGE"

ditto "$APP" "$STAGE/ControlDeskAgent.app"
cp "$EXTRAS/LEIA-ME.txt" "$STAGE/"
ln -s /Applications "$STAGE/Applications"

mkdir -p "$(dirname "$OUT_DMG")"
rm -f "$OUT_DMG"
hdiutil create \
    -volname "ControlDeskAgent" \
    -srcfolder "$STAGE" \
    -ov \
    -format UDZO \
    "$OUT_DMG"

echo "DMG: $OUT_DMG"
