# Instalar o agente (Control Desk)

O **agente** é o serviço que roda no Mac e responde ao app mobile na porta **8741**.

Especificação completa: **[README.md](README.md)** (índice) e **[spec.md](spec.md)**.

---

## Comportamento esperado

| Ação | Resultado |
|------|-----------|
| **Instalar em segundo plano.command** (padrão) | Instala/atualiza serviço headless, sem janela |
| **Abrir configurações.command** | Instala se necessário + janela PIN/IP |
| **ControlDeskAgent.app** | Instala/atualiza em background e encerra |
| Fechar janela de configurações | Serviço **continua** |
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

`agent/build/compose/binaries/main/dmg/`

### 2. Instalar (usuário)

1. Abra o DMG
2. Leia **LEIA-ME.txt** ou a pasta **Documentacao/** (cópia de `docs/`)
3. **Padrão:** duplo clique em **Instalar em segundo plano.command**
   - Copia/atualiza app em `/Applications` (instância única)
   - Registra LaunchAgent (`com.controldesk.agent`)
   - Inicia serviço headless na porta 8741
3. **Ver PIN/IP:** duplo clique em **Abrir configurações.command**
4. **ControlDeskAgent.app:** instala/atualiza e encerra (sem janela — use o item 3 para PIN)

Scripts no DMG: `Instalar em segundo plano.command`, `Abrir configurações.command`, `Remover serviço.command`.

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

## Instalação via terminal (dev)

```bash
./gradlew :agent:packageDistributionForCurrentOS
chmod +x agent/scripts/install-macos.sh
./agent/scripts/install-macos.sh
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
| Abrir configurações | **Abrir configurações.command** ou `CONTROL_DESK_OPEN_SETTINGS=1 open -a ControlDeskAgent --args --settings` |
| Parar serviço | `launchctl bootout gui/$(id -u)/com.controldesk.agent` |
| Iniciar serviço | `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.controldesk.agent.plist` |

---

## Remover o agente

### Opção 1 — DMG

Duplo clique em **Remover serviço.command**.

### Opção 2 — Terminal

```bash
bash agent/package/resources/macos/uninstall-macos.sh
```

ou, se o app ainda está instalado:

```bash
bash /Applications/ControlDeskAgent.app/Contents/app/resources/uninstall-macos.sh
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

1. Copie o **mesmo `.dmg`** em cada máquina
2. Em cada Mac: abra **ControlDeskAgent.app** (ou instalar em segundo plano)
3. Habilite **Acessibilidade** em cada Mac
4. No celular: pareie cada PC com um nome diferente (`Trabalho`, `Pessoal`, …)

---

## Desenvolvimento (sem instalador)

```bash
./gradlew :agent:run
```

Abre o servidor na porta **8741** e a janela de configurações (PIN e IP). Só terminal: `--args="--headless"`.

Se a janela não aparecer (Gradle daemon no macOS), use o `.app` gerado:

```bash
CONTROL_DESK_SKIP_INSTALL=1 ./gradlew :agent:runDistributable
```

---

## Problemas comuns

| Sintoma | O que fazer |
|---------|-------------|
| App abre mas serviço não sobe | `cat ~/.control-desk/install.log` |
| `Instalar em segundo plano.command` fecha rápido | Terminal: `bash .../install-macos.sh` |
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
