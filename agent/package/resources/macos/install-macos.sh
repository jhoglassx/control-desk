#!/usr/bin/env bash
set -euo pipefail

LABEL="com.controldesk.agent"
APP_NAME="ControlDeskAgent.app"
INSTALL_DIR="/Applications"
PLIST_PATH="$HOME/Library/LaunchAgents/${LABEL}.plist"
LOG_DIR="$HOME/.control-desk"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${CONTROL_DESK_PORT:-8741}"

usage() {
    cat <<EOF
Instala ou atualiza o Control Desk Agent no macOS (instância única).

Uso:
  $0 [caminho/ControlDeskAgent.app]

Sem argumento, detecta o app ao lado do script, no bundle ou em /Applications.

Regras:
  - Um único app em /Applications/ControlDeskAgent.app
  - Um único LaunchAgent (${LABEL})
  - App vindo do DMG (caminho diferente) sempre sincroniza, mesmo com a mesma versão
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

mkdir -p "$LOG_DIR"
LOCKDIR="$LOG_DIR/install.lockdir"
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25; do
    if mkdir "$LOCKDIR" 2>/dev/null; then
        trap 'rmdir "$LOCKDIR" 2>/dev/null || true' EXIT
        break
    fi
    echo "→ Outra instalação em andamento — aguardando…"
    sleep 0.4
done

read_bundle_version() {
    local app_path="$1"
    if [[ ! -d "$app_path" ]]; then
        echo "0"
        return 0
    fi
    defaults read "${app_path}/Contents/Info" CFBundleShortVersionString 2>/dev/null || echo "0"
}

canonical_path() {
    local p="$1"
    if [[ -d "$p" ]]; then
        (cd "$p" && pwd -P)
    else
        echo "$p"
    fi
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

agent_service() {
    echo "gui/$(id -u)/${LABEL}"
}

service_loaded() {
    launchctl print "$(agent_service)" >/dev/null 2>&1
}

port_listening() {
    /usr/sbin/lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1
}

unload_agent() {
    local service
    service="$(agent_service)"
    launchctl bootout "$service" >/dev/null 2>&1 || true
    launchctl disable "$service" >/dev/null 2>&1 || true
    sleep 1
}

write_plist() {
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
}

kick_agent() {
    local service
    service="$(agent_service)"
    launchctl enable "$service" >/dev/null 2>&1 || true
    launchctl kickstart -k "$service" >/dev/null 2>&1 || true
}

# Error 5 = já está no launchd. Não é falha se o serviço (ou a porta) estiver no ar.
load_agent() {
    local domain service err n
    domain="gui/$(id -u)"
    service="${domain}/${LABEL}"
    err=""

    if service_loaded; then
        kick_agent
        echo "→ LaunchAgent já estava carregado — reiniciado."
        return 0
    fi

    for n in 1 2 3 4 5 6; do
        if err="$(launchctl bootstrap "$domain" "$PLIST_PATH" 2>&1)"; then
            kick_agent
            return 0
        fi
        if service_loaded || port_listening; then
            kick_agent
            echo "→ LaunchAgent já estava ativo (bootstrap: ${err%%$'\n'*}) — ok."
            return 0
        fi
        sleep 0.5
    done

    kick_agent
    sleep 0.5
    if service_loaded || port_listening; then
        echo "→ LaunchAgent ativo após kickstart."
        return 0
    fi

    echo "Falha ao registrar LaunchAgent (${service}):"
    echo "${err:-bootstrap falhou}"
    return 1
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
SRC_CANON="$(canonical_path "$SOURCE_APP")"
DST_CANON=""
if [[ -d "$TARGET_APP" ]]; then
    DST_CANON="$(canonical_path "$TARGET_APP")"
fi
NEEDS_COPY=false

if [[ ! -d "$TARGET_APP" ]]; then
    echo "→ Instalação inicial (versão $SOURCE_VER)"
    NEEDS_COPY=true
elif [[ "$SRC_CANON" != "$DST_CANON" ]]; then
    echo "→ Sincronizando $SOURCE_APP → $TARGET_APP (versão $SOURCE_VER)"
    NEEDS_COPY=true
else
    echo "→ Já instalado em $TARGET_APP (versão $TARGET_VER)"
fi

if [[ "$NEEDS_COPY" == true ]]; then
    unload_agent
    echo "→ Copiando $SOURCE_APP → $TARGET_APP"
    rm -rf "$TARGET_APP"
    ditto "$SOURCE_APP" "$TARGET_APP"
    TARGET_VER="$(read_bundle_version "$TARGET_APP")"
    xattr -cr "$TARGET_APP" >/dev/null 2>&1 || true
fi

if [[ ! -x "$BINARY" ]]; then
    echo "Binário não encontrado: $BINARY"
    exit 1
fi

write_plist "$BINARY"
load_agent

echo
echo "✓ Control Desk Agent ($TARGET_VER) — instância única em segundo plano."
echo "  App: $TARGET_APP"
echo "  Serviço: ${LABEL} (LaunchAgent)"
echo "  Porta: $PORT"
echo "  Log: $LOG_DIR/agent.log"
echo "  PIN: grep pin= $LOG_DIR/agent.log"
echo
echo "Acessibilidade (obrigatório para apps/janelas):"
echo "  Ajustes → Privacidade e Segurança → Acessibilidade → ControlDeskAgent"
