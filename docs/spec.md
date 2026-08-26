# Control Desk — Especificação (regras de negócio e comportamentos)

Documento de referência para reimplementação do zero. Descreve **o que** o produto deve fazer, não apenas o que o código faz hoje.

---

## 1. Visão do produto

**Control Desk** é um painel remoto estilo Stream Deck: o celular (Android/iOS) controla o computador (Mac/PC) via um **agente desktop** na LAN.

| Componente | Função |
|------------|--------|
| **App mobile** (`composeApp`) | UI Compose Multiplatform — grade de apps, volume, mic, pareamento |
| **Agente** (`agent`) | Servidor HTTP local (Ktor) + executores de SO |
| **Core** (`core/domain`, `protocol`, `data`) | Use cases, DTOs, gateway HTTP, persistência JSON |

**Princípios v1:**

- Comunicação **HTTP na LAN** (sem nuvem, sem relay).
- Pareamento **PIN** (válido até gerar outro) → token Bearer persistido.
- **Sem SQLDelight** na v1 — JSON em disco é suficiente.
- Agente **headless por padrão** após instalação; janela de configurações é opcional.

---

## 2. Arquitetura modular

```
core/domain      Use cases, entidades, interfaces (Clean Architecture)
core/protocol    DTOs + AgentApiClient (compartilhado mobile ↔ agent)
core/data        AgentGatewayImpl, JsonHostPreferences, grid builder
core/testing     Fakes para testes
composeApp       UI Compose + ViewModels (MVI)
agent            Ktor server + PlatformExecutor por SO
```

**Fluxo de dados:** Mobile → `AgentApiClient` → Agente → `PlatformExecutor` → SO.

---

## 3. Agente desktop

### 3.1 Modos de execução

| Modo | Como ativar | Comportamento esperado |
|------|-------------|------------------------|
| **Headless (padrão produção)** | `CONTROL_DESK_HEADLESS=1` (LaunchAgent) | Servidor Ktor, mDNS, PIN no log — **sem janela** |
| **Instalar/atualizar (GUI app)** | Abrir `ControlDeskAgent.app` | Sincroniza instalação em background e **encerra** (sem janela) |
| **Dev (`./gradlew :agent:run`)** | Sem `.app`, ou `CONTROL_DESK_SKIP_INSTALL=1` | Servidor **neste processo** + janela PIN/IP |
| **Configurações (opcional)** | `Abrir configurações.command`, `--settings` ou `CONTROL_DESK_OPEN_SETTINGS=1` | Instala se necessário + janela PIN/IP (consulta serviço local) |

**Regra de negócio — instalação padrão:**

1. **Padrão:** **Instalar em segundo plano.command** — instala ou atualiza serviço headless, sem janela.
2. **Instância única:** sempre `/Applications/ControlDeskAgent.app` + um LaunchAgent `com.controldesk.agent`.
3. **Atualização:** nova versão no DMG **substitui** a instalada (compara `CFBundleShortVersionString`) e reinicia o serviço.
4. **Não duplicar:** nunca múltiplos LaunchAgents nem múltiplos apps — `launchctl bootout` antes de `bootstrap`.
5. **Configurações (opcional):** **Abrir configurações.command** ou `--settings` — janela PIN/IP; fechar **não** para o serviço.
6. `ControlDeskAgent.app` (duplo clique): instala/atualiza e encerra; use o comando de configurações para ver PIN.

**Regra:** A janela de configurações **não** roda o servidor; consulta `GET /setup` em `127.0.0.1` no processo headless.

| Variável | Uso |
|----------|-----|
| `CONTROL_DESK_OPEN_SETTINGS=1` | Abre janela após instalar |
| `CONTROL_DESK_SKIP_INSTALL=1` | Dev: só UI, não altera LaunchAgent |

### 3.2 Configuração

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `CONTROL_DESK_PORT` | `8741` | Porta HTTP |
| `CONTROL_DESK_BIND` | `0.0.0.0` | Bind (LAN + emulador) |
| `CONTROL_DESK_HEADLESS` | `1` no LaunchAgent | Serviço sem UI |
| `CONTROL_DESK_OPEN_SETTINGS` | `0` (padrão no .app) | `1` ou `--settings` abre janela |
| `CONTROL_DESK_SKIP_INSTALL` | — | `1` = dev, não altera instalação |

### 3.3 Arquivos no macOS (`~/.control-desk/`)

| Arquivo | Conteúdo |
|---------|----------|
| `agent_tokens.txt` | Tokens Bearer (um por linha) |
| `agent_pin.txt` | PIN ativo (permanece até “Gerar novo PIN”) |
| `agent.log` | Log headless (`pin=`, hints de IP) |
| `agent-launchd.log` | stdout do LaunchAgent |
| `agent-launchd-error.log` | stderr do LaunchAgent |
| `install.log` | Log do script de instalação |
| `hosts.json` | Hosts pareados (só desktop JVM, se usado) |

**Instalação:**

| Caminho | Conteúdo |
|---------|----------|
| `/Applications/ControlDeskAgent.app` | App instalado |
| `~/Library/LaunchAgents/com.controldesk.agent.plist` | LaunchAgent |

### 3.4 LaunchAgent (macOS)

**Label:** `com.controldesk.agent`

| Propriedade | Valor |
|-------------|-------|
| `RunAtLoad` | `true` — inicia ao login |
| `KeepAlive` | `true` — reinicia se cair |
| `EnvironmentVariables` | `CONTROL_DESK_HEADLESS=1` |

**Instalar:** `agent/package/resources/macos/install-macos.sh`  
**Remover:** `agent/package/resources/macos/uninstall-macos.sh` (mantém `~/.control-desk/`)

**Atualização:** script compara versão fonte vs `/Applications`; copia só se fonte é mais nova ou primeira instalação; sempre re-registra LaunchAgent apontando ao binary instalado.

### 3.5 Janela de configurações

| Elemento | Comportamento |
|----------|---------------|
| Título | "Control Desk — Configurações" |
| Poll | `GET http://127.0.0.1:{port}/setup` a cada **2 s** |
| Status | "Rodando" se `setup.ok`; "Iniciando…" se não |
| PIN | De `/setup` ou fallback `agent_pin.txt` / `grep pin= agent.log` |
| Hints de IP | `NetworkInfo.connectionHints(port)` |
| "Gerar novo PIN" | `POST /setup/new-pin` (só se serviço rodando) |
| "Fechar" | Encerra só a janela; serviço continua |

**Hints de rede:**

- Emulador Android no Mac: `10.0.2.2:{port}`
- Celular na Wi‑Fi: IPs LAN do Mac
- Local: `127.0.0.1:{port}`

### 3.6 mDNS (agente)

- Tipo: `_controldesk._tcp.local.`
- Nome: `ControlDesk-{hostname}`
- TXT: `version=1.0.0`
- Falha no mDNS **não** impede o agente de rodar
- **Mobile ainda não consome mDNS** (roadmap)

### 3.7 Warmup macOS (ao iniciar)

Antes do servidor, no Mac:

1. `MacAccessibility.warnIfNeeded()` — aviso de Acessibilidade
2. `MacWindowLister.warmUp()`
3. `MacAppIconResolver.warmUp()`
4. `MacFocusApp.warmUp()`

---

## 4. Pareamento e segurança

### 4.1 PIN

| Regra | Detalhe |
|-------|---------|
| Formato | 6 dígitos (`100000–999999`) |
| Geração automática | Se não há PIN nem tokens → gera PIN |
| Armazenamento | `~/.control-desk/agent_pin.txt` |
| Reuso | O mesmo PIN vale para re-parear até “Gerar novo PIN” |
| Múltiplos dispositivos | Vários tokens permitidos (um por pareamento) |

### 4.2 Fluxo de pareamento (mobile)

1. Usuário preenche: **nome**, **host**, **porta**, **PIN**
2. App valida: nome e host não vazios; porta inteiro; PIN ≥ 4 caracteres (agente usa 6)
3. `GET /health` — teste de conectividade
4. `POST /pair` com `{ "pin": "..." }`
5. Sucesso → salva `PairedHost` + token em JSON
6. **ID do host** = `"$host:$port"` — re-parear mesmo IP/porta **atualiza** o token

**Defaults no formulário:**

- Host: `10.0.2.2` (emulador)
- Porta: `8741`

### 4.3 Autenticação HTTP

| Rota | Auth |
|------|------|
| `GET /health`, `POST /pair` | Públicas |
| `GET /setup`, `POST /setup/new-pin` | **Somente localhost** (`127.0.0.1`, `localhost`, `[::1]`) |
| Todas outras | Bearer token → `401` se inválido |

**PIN inválido:** `401` + `{ ok: false, message: "Invalid PIN" }`  
**Setup remoto:** `403 Forbidden`

### 4.4 Remover host no mobile

- Remove entrada do JSON local
- **Não revoga** token no agente (sem endpoint de revogação na v1)

### 4.5 Postura de segurança v1

| Aspecto | Estado v1 |
|---------|------------|
| Transporte | HTTP (sem TLS) |
| Alcance | Bind `0.0.0.0` — acessível na LAN |
| PIN | Reutilizável até “Gerar novo PIN” |
| Revogação de token | Não implementada |
| Android cleartext | `usesCleartextTraffic=true` |
| iOS ATS | `NSAllowsArbitraryLoads=true` |

### 4.6 Mensagens de erro (mobile)

| Situação | Mensagem |
|----------|----------|
| `401` | "Sessão expirada. Vá em + Parear, use o PIN do agente e pareie o PC de novo..." |
| Conexão recusada | "Agente offline. Inicie no Mac: ./gradlew :agent:run" |

---

## 5. API do agente (protocolo v1)

**Versão health:** `1.1.0`  
**windowListerVersion:** `16`

### 5.1 Endpoints implementados

| Método | Rota | Auth | Comportamento |
|--------|------|------|---------------|
| GET | `/health` | Não | `ok`, `version`, `os`, `windowListerVersion` |
| GET | `/setup` | Local | `port`, `pin`, `backgroundService` |
| POST | `/setup/new-pin` | Local | Regenera PIN |
| POST | `/pair` | Não | PIN → `token` |
| GET | `/apps/running?icons=0\|1` | Bearer | Lista janelas; `icons=0` sem ícones |
| POST | `/apps/launch` | Bearer | Focar/abrir app |
| POST | `/browser/open` | Bearer | Abrir URL |
| POST | `/browser/close` | Bearer | Fechar navegador |
| POST | `/browser/tabs/switch` | Bearer | Aba próxima/anterior/selecionar |
| GET | `/system/audio` | Bearer | Volume + mute |
| POST | `/system/volume` | Bearer | Ajustar volume |
| POST | `/system/mic` | Bearer | Ajustar mic |

### 5.2 DTOs principais

Ver `core/protocol/.../AgentDtos.kt`.

**AppLaunchRequest:** `target`, `targetType` (default `appName`), `windowTitle?`, `windowIndex?`, `windowNumber?`

**BrowserTabSwitchRequest:** `browser`, `action` (`next`/`prev`/`select`), `value?`, `windowIndex?`, `focusFirst` (default `true`)

**VolumeRequest:** `level?`, `delta?`, `mute?`

**MicRequest:** `mute?`, `toggle` (default `false`), `inputDelta?`

**RunningAppInfo:** `name`, `appName`, `windowTitle`, `windowIndex`, `axWindowIndex`, `windowNumber`, `iconBase64`, `isActive`, `isVisible`, `browserWindowIndex`, `isBrowserTab`, `monitorIndex`

### 5.3 Endpoints planejados (não v1)

`GET /apps/installed`, `POST /apps/focus`, `POST /apps/close`, `POST /input/hotkey`, `GET /browser/tabs`, `POST /browser/tabs/new`, `POST /browser/tabs/close`

---

## 6. Executores de plataforma (agente)

### 6.1 Interface

`PlatformExecutor` — `LoggingPlatformExecutor` (stub/dev) vs `NativePlatformExecutor` (SO real).

### 6.2 macOS — listagem de apps

**Requisito:** permissão de **Acessibilidade** para ControlDeskAgent (e `java`/Terminal em dev).

**Processos ignorados:** ControlDeskAgent, AutoFill, coreauthd, loginwindow, Wallpaper, etc.

**Chrome:** janelas substituídas por **abas individuais** na grade.

**Labels:** `"{app} · {título}"` ou `#{index}` para duplicatas; `" · min"` se minimizado.

**Ordenação:** ativo → visível → alfabético.

**Ícones:** bundle do app, favicon para abas Chrome.

**Limite:** ~32 processos para resolução de ícones.

### 6.3 macOS — foco / launch

- Primário: binário Swift `MacFocusApp`
- Fallback: `open -a {appName}`

### 6.4 macOS — navegador

| Ação | Implementação |
|------|---------------|
| Abrir URL | `open -a {browser} {url}` |
| Fechar | AppleScript quit |
| Aba next/prev | Cmd+` / Cmd+Shift+` |
| Selecionar aba | `MacChromeTabs` (Chrome) |

**Mapeamento:** chrome→Google Chrome, safari→Safari, firefox→Firefox, edge→Microsoft Edge

### 6.5 macOS — áudio (`MacAudioControl`)

| Leitura | Detalhe |
|---------|---------|
| Volume saída | 0–100 via AppleScript |
| Mute saída | `output muted` |
| Mic muted | `input volume ≤ 1` |

| Ação | Regra |
|------|-------|
| Mute volume | `set volume output muted true/false` |
| Nível | Desmuta + define volume (clamp 0–100) |
| Delta volume | Lê atual + delta, clamp |
| Mute mic | input 0; unmute → 75 |
| Toggle mic | 0 ↔ 75 |
| inputDelta | Ajusta input volume |

### 6.6 Windows / Linux (parcial)

- **Windows:** PowerShell para processos com `MainWindowTitle`; `cmd /c start`; `taskkill`
- **Linux:** `wmctrl`, `xdg-open`, `pkill` — volume/mic usa stub

---

## 7. App mobile

**Documento completo:** **[mobile-app.md](mobile-app.md)** — telas, ViewModels, polling, mixer, pareamento, persistência, Android/iOS.

Resumo:

| Área | Regra principal |
|------|-----------------|
| **Navegação** | Desk (padrão) ↔ Pair; após parear → `refreshHosts()` |
| **Desk vazio** | CTA "Parear computador" |
| **Desk ativo** | Header + grade adaptive + mixer inferior |
| **Pair** | Nome, IP, porta, PIN; defaults `10.0.2.2` / `8741` |
| **Polling** | Apps 1,5 s; áudio 2,5 s; burst após clique |
| **Clique app** | Foco otimista + `REMOTE_LAUNCH` ou `REMOTE_BROWSER_TAB` |
| **Mixer** | Slider 0–100, ±5, mute otimista, mic toggle |
| **Persistência** | `control_desk_hosts.json` (Android/iOS); token no JSON |
| **Remover host** | Só local; não revoga token no agente |
| **Interação** | Instantânea na UI (otimista); ver [interaction-rules.md](interaction-rules.md) |

---

## 7.1 Interação instantânea

Todas as ações do usuário (tocar app, volume, mic) devem ter **feedback visual imediato** (< 100 ms). A rede confirma em background; erro reverte o estado otimista.

Documento completo: **[interaction-rules.md](interaction-rules.md)**.

## 8. Persistência

### 8.1 Hosts pareados

| Plataforma | Arquivo | Diretório |
|------------|---------|-----------|
| Android | `control_desk_hosts.json` | `filesDir` |
| iOS | `control_desk_hosts.json` | Documents |
| JVM/desktop | `hosts.json` | `~/.control-desk/` |

**Conteúdo por host:** `id`, `name`, `host`, `port`, `osType`, `sortOrder`, **token**, `buttons[]`

### 8.2 Cache de ícones

`control_desk_icon_cache.json` (mesmos diretórios por plataforma).

### 8.3 Tokens do agente

`~/.control-desk/agent_tokens.txt` — texto plano, um token por linha.

---

## 9. Instalação e operação (macOS)

### 9.1 Build

```bash
./gradlew :agent:packageDmg              # DMG instalador
./gradlew :agent:packageDistributionForCurrentOS
./gradlew :agent:run                      # Dev: servidor + janela PIN/IP
./gradlew :agent:runDistributable        # Dev: .app (use se a janela do :run não aparecer)
./gradlew :composeApp:assembleDebug    # APK Android
```

DMG: `agent/build/compose/binaries/main/dmg/`

### 9.2 Fluxo do usuário (produção)

1. Abrir DMG → **ControlDeskAgent.app** (padrão)
2. Serviço instala e sobe em background
3. Janela de configurações mostra PIN e IP
4. Ajustes → Acessibilidade → ControlDeskAgent
5. Celular: Parear PC → IP, porta 8741, PIN

### 9.3 Comandos operacionais

| Ação | Comando |
|------|---------|
| Verificar rodando | `lsof -i :8741` |
| Ver PIN | Abrir app ou `grep pin= ~/.control-desk/agent.log` |
| Abrir configurações | `open /Applications/ControlDeskAgent.app` |
| Parar serviço | `launchctl bootout gui/$(id -u)/com.controldesk.agent` |
| Iniciar serviço | `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.controldesk.agent.plist` |
| Remover tudo | `Remover serviço.command` ou `uninstall-macos.sh` |
| Limpar dados | `rm -rf ~/.control-desk` |

### 9.4 Múltiplos Macs

- Mesmo DMG em cada máquina
- Parear cada um com nome distinto no celular
- Acessibilidade em cada Mac

### 9.5 Emulador vs celular físico

| Contexto | IP no app |
|----------|-----------|
| Emulador Android no Mac | `10.0.2.2` |
| Celular na mesma Wi‑Fi | IP LAN do Mac |

---

## 10. Testes e CI

| Área | Onde |
|------|------|
| Agent server | `agent/src/test/.../AgentServerTest.kt` |
| Domain | `core/domain/src/commonTest/` |
| Protocol | `core/protocol/src/commonTest/` |
| Android UI | `composeApp/src/androidInstrumentedTest/` |
| E2E Maestro | `e2e/maestro/` |
| CI | `.github/workflows/ci.yml` |

**Contrato mínimo (testes):** health ok; pair funciona; rotas protegidas 401 sem token, 200 com token.

---

## 11. Fora de escopo (v1)

- Relay/nuvem/VPN integrada
- SSH fallback
- Sync de layouts entre celulares
- Wake-on-LAN, mouse remoto, streaming
- HTTPS na LAN (roadmap v1.1+)
- Loja de plugins

---

## 12. Débito técnico conhecido (corrigir na reimplementação)

| Item | Problema |
|------|----------|
| `DeskViewModel.kt` | Corrupção em `resolveIcon` / `refreshMissingIconsIfNeeded` |
| README vs código | README diz `control_desk_hosts.json` no desktop; JVM usa `hosts.json` |
| Revogação de token | Não implementada |
| mDNS no mobile | Agente anuncia; app não descobre |
| Tray/menu bar | Só janela Compose; sem ícone na bandeja |

---

## 13. Referências

| Documento | Conteúdo |
|-----------|----------|
| [interaction-rules.md](interaction-rules.md) | **Interação instantânea** — otimista, latência, poll vs toque |
| [mobile-app.md](mobile-app.md) | **App mobile** — regras completas Android/iOS |
| [agent-install.md](agent-install.md) | Instalação macOS passo a passo |
| [ios-setup.md](ios-setup.md) | Build iOS |
| [roadmap.md](roadmap.md) | Backlog e fases |
| [README.md](README.md) | Índice central da documentação |
