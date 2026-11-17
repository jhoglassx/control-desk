#!/usr/bin/env bash
set -euo pipefail

APP="${1:?Usage: sign-macos-app.sh /path/to/ControlDeskAgent.app}"
if [[ "$(uname -s)" != "Darwin" ]]; then
  exit 0
fi

IDENTITY="${CONTROL_DESK_CODESIGN_IDENTITY:-}"
KEYCHAIN="${CONTROL_DESK_SIGNING_KEYCHAIN:-}"

HELPER="$APP/Contents/app/resources/macos/control-desk-camera"
ENTITLEMENTS="$APP/Contents/app/resources/macos/camera-entitlements.plist"
if [[ ! -f "$HELPER" ]]; then
  HELPER="$APP/Contents/app/resources/control-desk-camera"
  ENTITLEMENTS="$APP/Contents/app/resources/camera-entitlements.plist"
fi
if [[ -f "$HELPER" ]]; then
  /bin/chmod 755 "$HELPER"
  HELPER_IDENTITY="${IDENTITY:--}"
  if [[ -n "$KEYCHAIN" ]]; then
    /usr/bin/codesign --force --options runtime --timestamp=none --keychain "$KEYCHAIN" --entitlements "$ENTITLEMENTS" --sign "$HELPER_IDENTITY" "$HELPER"
  else
    /usr/bin/codesign --force --options runtime --timestamp=none --entitlements "$ENTITLEMENTS" --sign "$HELPER_IDENTITY" "$HELPER"
  fi
fi

if [[ -z "$IDENTITY" ]]; then
  echo "Aviso: nenhum certificado persistente configurado; o app ficará ad hoc assinado."
  echo "Para preservar Acessibilidade entre builds, configure CONTROL_DESK_CODESIGN_IDENTITY."
  /usr/bin/codesign --force --deep --preserve-metadata=entitlements --timestamp=none --sign - "$APP"
  /usr/bin/codesign --verify --deep --strict "$APP"
  exit 0
fi

if [[ -n "$KEYCHAIN" ]]; then
  /usr/bin/codesign --force --deep --preserve-metadata=entitlements --options runtime --timestamp=none \
    --keychain "$KEYCHAIN" --sign "$IDENTITY" "$APP"
else
  /usr/bin/codesign --force --deep --preserve-metadata=entitlements --options runtime --timestamp=none \
    --sign "$IDENTITY" "$APP"
fi

/usr/bin/codesign --verify --deep --strict "$APP"
DESIGNATED_REQUIREMENT="$(/usr/bin/codesign -dr - "$APP" 2>&1 | sed -n 's/^designated => //p')"
echo "Assinatura verificada: $IDENTITY"
echo "Identidade persistente: $DESIGNATED_REQUIREMENT"
