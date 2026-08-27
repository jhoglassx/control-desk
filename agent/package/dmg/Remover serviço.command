#!/bin/bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
APP="${1:-$DIR/ControlDeskAgent.app}"
INSTALLED="/Applications/ControlDeskAgent.app"
SCRIPT="$INSTALLED/Contents/app/resources/macos/uninstall-macos.sh"
if [[ ! -f "$SCRIPT" ]]; then
    SCRIPT="$APP/Contents/app/resources/macos/uninstall-macos.sh"
fi
if [[ ! -f "$SCRIPT" ]]; then
    SCRIPT="$DIR/../resources/macos/uninstall-macos.sh"
fi
if [[ ! -f "$SCRIPT" ]]; then
    echo "uninstall-macos.sh não encontrado."
    read -r -p "Pressione Enter para fechar…"
    exit 1
fi
bash "$SCRIPT"
echo
read -r -p "Pressione Enter para fechar…"
