#!/usr/bin/env bash
set -euo pipefail

LABEL="com.controldesk.agent"
APP_NAME="ControlDeskAgent.app"
INSTALL_DIR="/Applications"
PLIST_PATH="$HOME/Library/LaunchAgents/${LABEL}.plist"
LOG_DIR="$HOME/.control-desk"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
    cat <<EOF
Instala ou atualiza o Control Desk Agent no macOS (instância única).

Uso:
  $0 [caminho/ControlDeskAgent.app]

Sem argumento, detecta o app ao lado do script, no bundle ou em /Applications.

Regras:
  - Um único app em /Applications/ControlDeskAgent.app
  - Um único LaunchAgent (${LABEL})
  - Nova versão substitui a anterior e reinicia o serviço
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

read_bundle_version() {
    local app_path="$1"
    if [[ ! -d "$app_path" ]]; then
        echo "0"
        return 0
    fi
    defaults read "${app_path}/Contents/Info" CFBundleShortVersionString 2>/dev/null || echo "0"
}

version_newer_than() {
    local candidate="$1"
    local installed="$2"
    if [[ "$candidate" == "$installed" ]]; then
        return 1
    fi
    local newer
    newer="$(printf '%s\n%s\n' "$installed" "$candidate" | sort -V | tail -n 1)"
    [[ "$newer" == "$candidate" ]]
}

resolve_source_app() {
    if [[ -n "${1:-}" ]]; then
        echo "$1"
        return 0
    fi
    if [[ -d "$SCRIPT_DIR/$APP_NAME" ]]; then
        echo "$SCRIPT_DIR/$APP_NAME"
        return 0
    fi
    local bundled_app="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
    if [[ -d "$bundled_app" && "$(basename "$bundled_app")" == "$APP_NAME" ]]; then
        echo "$bundled_app"
        return 0
    fi
    if [[ -d "$INSTALL_DIR/$APP_NAME" ]]; then
        echo "$INSTALL_DIR/$APP_NAME"
        return 0
    fi
    local repo_build="$(cd "$SCRIPT_DIR/../../.." && pwd)/build/compose/binaries/main/app/$APP_NAME"
    if [[ -d "$repo_build" ]]; then
        echo "$repo_build"
        return 0
    fi
    return 1
}

ensure_launch_agent() {
    local binary="$1"
    mkdir -p "$LOG_DIR" "$HOME/Library/LaunchAgents"

    cat >"$PLIST_PATH" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${LABEL}</string>
    <key>ProgramArguments</key>
    <array>
        <string>${binary}</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>CONTROL_DESK_HEADLESS</key>
        <string>1</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>ProcessType</key>
    <string>Background</string>
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/agent-launchd.log</string>
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/agent-launchd-error.log</string>
</dict>
</plist>
PLIST

    USER_UID="$(id -u)"
    launchctl bootout "gui/${USER_UID}/${LABEL}" 2>/dev/null || true
    launchctl bootstrap "gui/${USER_UID}" "$PLIST_PATH"
    launchctl enable "gui/${USER_UID}/${LABEL}" 2>/dev/null || true
}

SOURCE_APP="$(resolve_source_app "${1:-}")" || {
    echo "App não encontrado: $APP_NAME"
    echo
    usage
    exit 1
}

TARGET_APP="$INSTALL_DIR/$APP_NAME"
BINARY="$TARGET_APP/Contents/MacOS/ControlDeskAgent"
SOURCE_VER="$(read_bundle_version "$SOURCE_APP")"
TARGET_VER="$(read_bundle_version "$TARGET_APP")"
NEEDS_COPY=false

if [[ ! -d "$TARGET_APP" ]]; then
    echo "→ Instalação inicial (versão $SOURCE_VER)"
    NEEDS_COPY=true
elif version_newer_than "$SOURCE_VER" "$TARGET_VER"; then
    echo "→ Atualizando $TARGET_VER → $SOURCE_VER"
    NEEDS_COPY=true
elif [[ "$SOURCE_VER" != "$TARGET_VER" ]]; then
    echo "→ Versão instalada ($TARGET_VER) é igual ou mais recente que a fonte ($SOURCE_VER)"
    echo "  Mantendo instalação atual em $TARGET_APP"
else
    echo "→ Versão $TARGET_VER já instalada em $TARGET_APP"
fi

if [[ "$NEEDS_COPY" == true ]]; then
    echo "→ Copiando $SOURCE_APP → $TARGET_APP"
    rm -rf "$TARGET_APP"
    ditto "$SOURCE_APP" "$TARGET_APP"
    TARGET_VER="$(read_bundle_version "$TARGET_APP")"
fi

ensure_launch_agent "$BINARY"

echo
echo "✓ Control Desk Agent ($TARGET_VER) — instância única em segundo plano."
echo "  App: $TARGET_APP"
echo "  Serviço: ${LABEL} (LaunchAgent)"
echo "  Porta: 8741 (ou CONTROL_DESK_PORT)"
echo "  Log: $LOG_DIR/agent.log"
echo "  PIN: grep pin= $LOG_DIR/agent.log"
echo "  Configurações: Abrir configurações.command ou CONTROL_DESK_OPEN_SETTINGS=1"
echo
echo "Acessibilidade (obrigatório para apps/janelas):"
echo "  Ajustes → Privacidade e Segurança → Acessibilidade → ControlDeskAgent"
