#!/usr/bin/env bash
set -euo pipefail

APP="${1:?Usage: sign-macos-app.sh /path/to/ControlDeskAgent.app}"
if [[ "$(uname -s)" != "Darwin" ]]; then
  exit 0
fi

IDENTITY="${CONTROL_DESK_CODESIGN_IDENTITY:-}"
KEYCHAIN="${CONTROL_DESK_SIGNING_KEYCHAIN:-}"

if [[ -z "$IDENTITY" ]]; then
  echo "Aviso: nenhum certificado persistente configurado; o app ficará ad hoc assinado."
  echo "Para preservar Acessibilidade entre builds, configure CONTROL_DESK_CODESIGN_IDENTITY."
  exit 0
fi

if [[ -n "$KEYCHAIN" ]]; then
  /usr/bin/codesign --force --deep --options runtime --timestamp=none \
    --keychain "$KEYCHAIN" --sign "$IDENTITY" "$APP"
else
  /usr/bin/codesign --force --deep --options runtime --timestamp=none \
    --sign "$IDENTITY" "$APP"
fi

/usr/bin/codesign --verify --deep --strict "$APP"
DESIGNATED_REQUIREMENT="$(/usr/bin/codesign -dr - "$APP" 2>&1 | sed -n 's/^designated => //p')"
echo "Assinatura verificada: $IDENTITY"
echo "Identidade persistente: $DESIGNATED_REQUIREMENT"
