# Instalar o agente (Control Desk)

O **agente** é o serviço que roda no Mac e responde ao app mobile na porta **8741**.

Especificação completa: **[README.md](README.md)** (índice) e **[spec.md](spec.md)**.

---

## Comportamento esperado

| Ação | Resultado |
|------|-----------|
| **ControlDeskAgent.app** (duplo clique ou arrastar para Aplicativos) | Instala/atualiza em background e abre janela de PIN/IP |
| Fechar janela de configurações / Cmd+Q | UI some (sai do Dock). Serviço headless **continua** |
| Ctrl+C em `./gradlew :agent:run` | Processo do Gradle encerra; agente **respawna** headless (use `CONTROL_DESK_NO_RESPAWN=1` para parar) |
| Nova versão no DMG | Substitui app em `/Applications` e reinicia serviço |
| Reinstalar mesma versão | Mantém app; garante LaunchAgent ativo |

**Instância única:** um app (`/Applications/ControlDeskAgent.app`) + um LaunchAgent (`com.controldesk.agent`).

---

## macOS — instalador (.dmg)

### 1. Gerar o app

```bash
./gradlew :agent:packageDmg
```

O `.dmg` sai em:

`agent/build/compose/binaries/main/dmg/ControlDeskAgent-1.0.3.dmg`

Conteúdo: **ControlDeskAgent.app**, atalho **Applications** e **LEIA-ME.txt**.

### 2. Instalar (usuário)

1. Abra o DMG
2. Arraste **ControlDeskAgent.app** para **Aplicativos** (ou dê duplo clique no app)
3. Leia **LEIA-ME.txt** se precisar
4. Na janela: copie PIN/IP e habilite **Acessibilidade**
5. Feche a janela — o serviço continua em segundo plano na porta 8741

### 3. Acessibilidade (obrigatório)

**Ajustes → Privacidade e Segurança → Acessibilidade** → marque **ControlDeskAgent**.

Sem isso, a lista de apps/janelas fica vazia no celular.

### 4. Parear no celular (uma vez)

1. Na janela do agente: copie PIN e IP (ou `grep pin= ~/.control-desk/agent.log`)
2. No app: **Parear PC** → nome, IP, porta **8741**, PIN
3. Token salvo no celular; **não precisa parear de novo** após reiniciar o Mac

| Contexto | IP no formulário |
|----------|------------------|
| Emulador Android no Mac | `10.0.2.2` |
| Celular na Wi‑Fi | IP LAN do Mac (mostrado na janela) |

---

## Instalação via terminal (dev / este Mac)

```bash
./gradlew :agent:installMacos
```

Isso gera o `.app` e registra o LaunchAgent (`KeepAlive` + `RunAtLoad`). Equivale a:

```bash
./gradlew :agent:packageDistributionForCurrentOS
bash agent/package/resources/macos/install-macos.sh
```

Equivalente ao script dentro do app:

```bash
bash /Applications/ControlDeskAgent.app/Contents/app/resources/install-macos.sh
```

---

## Uso diário

| Ação | Como |
|------|------|
| Ver se está rodando | `lsof -i :8741` |
| Ver PIN / IP | Abrir `ControlDeskAgent.app` ou `cat ~/.control-desk/agent.log` |
| Abrir configurações | `open -a ControlDeskAgent --args --settings` |
| Parar serviço | `launchctl bootout gui/$(id -u)/com.controldesk.agent` |
| Parar daemon do `:agent:run` | `kill $(lsof -t -iTCP:8741 -sTCP:LISTEN)` |
| Iniciar serviço | `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.controldesk.agent.plist` |

---

## Remover o agente

### Opção 1 — Terminal (recomendado)

```bash
bash agent/package/resources/macos/uninstall-macos.sh
```

ou, se o app ainda está instalado:

```bash
bash /Applications/ControlDeskAgent.app/Contents/app/resources/macos/uninstall-macos.sh
```

### Opção 2 — Manual

```bash
launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/com.controldesk.agent.plist
rm -rf /Applications/ControlDeskAgent.app
```

### O que é removido

- LaunchAgent (`com.controldesk.agent`)
- `/Applications/ControlDeskAgent.app`

### O que permanece

- `~/.control-desk/` (tokens, logs, PIN) — remova manualmente se quiser:

```bash
rm -rf ~/.control-desk
```

### Opcional

Desmarque **ControlDeskAgent** em Ajustes → Acessibilidade.

---

## Vários Macs

Não use `./gradlew :agent:run` em cada máquina — isso é só desenvolvimento.

1. Gere o instalador uma vez: `./gradlew :agent:packageDmg`
2. Copie o **mesmo `.dmg`** em cada Mac
3. Em cada Mac: abra o DMG e instale **ControlDeskAgent.app**
4. Habilite **Acessibilidade** em cada Mac
5. No celular: pareie cada PC com um nome diferente (`Trabalho`, `Pessoal`, …)

---

## Desenvolvimento (sem instalador)

```bash
./gradlew :agent:run
```

Abre o servidor na porta **8741** e a janela de configurações (PIN e IP).

- **Cmd+Q / Fechar:** a janela e o ícone no Dock somem; o agente respawna `--headless` (log em `~/.control-desk/agent-daemon.log`).
- **Rodar `:agent:run` de novo:** encerra o processo na porta **8741** e sobe a versão nova.
- **Ctrl+C:** o Gradle encerra e o agente respawna `--headless` (log em `~/.control-desk/agent-daemon.log`).
- Para **parar** de verdade: `CONTROL_DESK_NO_RESPAWN=1 ./gradlew :agent:run` e então Ctrl+C, ou `kill $(lsof -t -iTCP:8741 -sTCP:LISTEN)`.

Só terminal: `--args="--headless"`.

Se a janela não aparecer (Gradle daemon no macOS), instale de verdade neste Mac:

```bash
./gradlew :agent:installMacos
```

Ou, só para testar o `.app` sem LaunchAgent:

```bash
CONTROL_DESK_SKIP_INSTALL=1 ./gradlew :agent:runDistributable
```

---

## Problemas comuns

| Sintoma | O que fazer |
|---------|-------------|
| `Instalar` / app não sobe | `cat ~/.control-desk/install.log` — botão direito → **Abrir** se o Gatekeeper bloquear |
| Duplo clique no .app não faz nada | App é background (sem Dock). Deve aparecer notificação; senão `cat ~/.control-desk/install.log` |
| Gatekeeper bloqueia | Botão direito → **Abrir** na primeira vez |
| Porta 8741 vazia | `launchctl print gui/$(id -u)/com.controldesk.agent` e `cat ~/.control-desk/agent-launchd-error.log` |
| PIN mostra "—" na janela | Aguarde ou `grep pin= ~/.control-desk/agent.log` |
| Lista vazia no celular | Verificar Acessibilidade |
| 401 no app | Re-parear com novo PIN do agente |

---

## Windows / Linux (futuro)

- Windows: `./gradlew :agent:packageMsi`
- Linux: `./gradlew :agent:packageDistributionForCurrentOS` → `.deb` / AppImage
- Auto-start: Agendador de Tarefas (Windows), systemd user (Linux)
