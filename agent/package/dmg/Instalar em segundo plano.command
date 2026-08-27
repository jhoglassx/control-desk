#!/bin/bash
# Duplo clique no DMG: instala o agente em segundo plano.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
APP="$DIR/ControlDeskAgent.app"
SCRIPT="$APP/Contents/app/resources/macos/install-macos.sh"
if [[ ! -f "$SCRIPT" ]]; then
    SCRIPT="$APP/Contents/app/resources/install-macos.sh"
fi
if [[ ! -d "$APP" || ! -f "$SCRIPT" ]]; then
    echo "ControlDeskAgent.app não encontrado ao lado deste script."
    echo "Abra o DMG e use o .command que está na mesma pasta do app."
    read -r -p "Pressione Enter para fechar…"
    exit 1
fi
xattr -cr "$APP" >/dev/null 2>&1 || true
bash "$SCRIPT" "$APP"
echo
read -r -p "Pressione Enter para fechar…"
