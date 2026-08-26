#!/usr/bin/env bash
set -euo pipefail

LABEL="com.controldesk.agent"
PLIST_PATH="$HOME/Library/LaunchAgents/${LABEL}.plist"
APP="/Applications/ControlDeskAgent.app"

USER_UID="$(id -u)"
launchctl bootout "gui/${USER_UID}/${LABEL}" 2>/dev/null || true
rm -f "$PLIST_PATH"
rm -rf "$APP"

echo "Serviço $LABEL removido. Dados em ~/.control-desk foram mantidos."
echo "Para apagar dados: rm -rf ~/.control-desk"
