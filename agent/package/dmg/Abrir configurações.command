#!/bin/bash
# Instala se preciso e abre a janela de PIN/IP.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
APP="$DIR/ControlDeskAgent.app"
INSTALLED="/Applications/ControlDeskAgent.app"
SCRIPT="$APP/Contents/app/resources/macos/install-macos.sh"
if [[ ! -f "$SCRIPT" ]]; then
    SCRIPT="$APP/Contents/app/resources/install-macos.sh"
fi
if [[ -d "$APP" && -f "$SCRIPT" ]]; then
    xattr -cr "$APP" >/dev/null 2>&1 || true
    bash "$SCRIPT" "$APP" || true
fi
TARGET="$INSTALLED"
if [[ ! -d "$TARGET" ]]; then
    TARGET="$APP"
fi
if [[ ! -d "$TARGET" ]]; then
    echo "App não encontrado. Rode primeiro: Instalar em segundo plano.command"
    read -r -p "Pressione Enter para fechar…"
    exit 1
fi
CONTROL_DESK_OPEN_SETTINGS=1 open -n "$TARGET" --args --settings
