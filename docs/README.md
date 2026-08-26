# Control Desk — Documentação

**Ponto único de documentação do projeto.** Tudo que é spec, guia ou backlog está nesta pasta `docs/`.

Painel remoto estilo Stream Deck — **Kotlin Multiplatform** (Android + iOS) + **agente desktop** (JVM). O celular controla o computador na LAN: apps, abas Chrome, volume e microfone.

---

## Índice

| Documento | Conteúdo |
|-----------|----------|
| **[spec.md](spec.md)** | Especificação geral — agente, API, arquitetura, regras de negócio |
| **[mobile-app.md](mobile-app.md)** | App mobile — telas, ViewModels, pareamento, UX |
| **[interaction-rules.md](interaction-rules.md)** | Interação instantânea — otimista, latência, poll vs toque |
| **[agent-install.md](agent-install.md)** | Instalar, atualizar e remover agente macOS |
| **[dmg-quick-start.md](dmg-quick-start.md)** | Início rápido no DMG (usuário final) |
| **[ios-setup.md](ios-setup.md)** | Build e setup iOS |
| **[roadmap.md](roadmap.md)** | Fases de implementação e backlog |

---

## Início rápido (desenvolvedor)

### Requisitos

- JDK 17+
- Android SDK 35 (build Android)
- Xcode (build iOS)

### Comandos

```bash
# Testes unitários
./gradlew :core:domain:jvmTest :core:protocol:jvmTest :agent:test

# APK debug
./gradlew :composeApp:assembleDebug

# Agente (dev — configurações com --args --settings)
CONTROL_DESK_SKIP_INSTALL=1 ./gradlew :agent:runDistributable --args --settings

# Instalador macOS (.dmg)
./gradlew :agent:packageDmg
```

### Fluxo do produto

1. **Mac:** instalar agente (DMG) → serviço em background na porta **8741**
2. **Mac:** Acessibilidade → ControlDeskAgent
3. **Celular:** Parear PC → IP, porta, PIN de 6 dígitos
4. **Celular:** grade de apps + mixer

Detalhes: [agent-install.md](agent-install.md), [mobile-app.md](mobile-app.md).

---

## Estrutura do monorepo

```
core/domain      Use cases, entities, interfaces (Clean Architecture)
core/protocol    DTOs + AgentApiClient (compartilhado mobile ↔ agent)
core/data        AgentGatewayImpl (Ktor client), JSON persistence
core/testing     Fakes para testes
composeApp       UI Compose + ViewModels MVI
agent            Servidor Ktor + PlatformExecutor
```

---

## API do agente (v1)

| Rota | Descrição |
|------|-----------|
| `GET /health` | Status |
| `GET /setup` | PIN e porta (localhost only) |
| `POST /pair` | Pareamento PIN → token |
| `GET /apps/running` | Apps com janela visível |
| `POST /apps/launch` | Abrir/focar app |
| `POST /browser/open` | Abrir URL |
| `POST /browser/close` | Fechar navegador |
| `POST /browser/tabs/switch` | Próxima/anterior aba |
| `GET /system/audio` | Volume + mic |
| `POST /system/volume` | Ajustar volume |
| `POST /system/mic` | Mute microfone |

Detalhes e DTOs: [spec.md](spec.md#5-protocol--api).

---

## Persistência

| Onde | Arquivo |
|------|---------|
| Android | `{filesDir}/control_desk_hosts.json` |
| iOS | Documents/`control_desk_hosts.json` |
| Desktop JVM | `~/.control-desk/hosts.json` |
| Agente (tokens) | `~/.control-desk/agent_tokens.txt` |

---

## Testes

- **Unitários:** domain, protocol, agent (`commonTest` / JVM)
- **UI Android:** `composeApp/src/androidInstrumentedTest`
- **E2E:** Maestro em `e2e/maestro/` (CI nightly)

---

## DMG (macOS)

O instalador inclui a pasta **Documentacao/** com esta pasta `docs/` e **LEIA-ME.txt** apontando ao [dmg-quick-start.md](dmg-quick-start.md).
