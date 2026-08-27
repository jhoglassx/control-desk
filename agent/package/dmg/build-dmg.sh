#!/usr/bin/env bash
set -euo pipefail

APP="$1"
STAGE="$2"
OUT_DMG="$3"
REPO_ROOT="$4"
EXTRAS="$5"

rm -rf "$STAGE"
mkdir -p "$STAGE"

ditto "$APP" "$STAGE/ControlDeskAgent.app"
cp "$EXTRAS/"*.command "$STAGE/"
cp "$EXTRAS/LEIA-ME.txt" "$STAGE/"
chmod +x "$STAGE/"*.command

mkdir -p "$STAGE/Documentacao"
cp -R "$REPO_ROOT/docs/." "$STAGE/Documentacao/"

mkdir -p "$(dirname "$OUT_DMG")"
rm -f "$OUT_DMG"
hdiutil create \
    -volname "ControlDeskAgent" \
    -srcfolder "$STAGE" \
    -ov \
    -format UDZO \
    "$OUT_DMG"

echo "DMG: $OUT_DMG"
