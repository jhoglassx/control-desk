# Control Desk — Documentação

**Ponto único de documentação do projeto.** Tudo que é spec, guia ou backlog está nesta pasta `docs/`.

**Estado verificado:** controle nativo macOS; apps Windows/Linux simulados, com suporte nativo parcial a dispositivos. iOS requer projeto Xcode externo. Veja [architecture.md](architecture.md).

Painel remoto estilo Stream Deck — **Kotlin Multiplatform** (Android + iOS) + **agente desktop** (JVM). O celular controla o computador na LAN: apps, abas Chrome, volume e microfone.

---

## Índice

| Documento | Conteúdo |
|-----------|----------|
| **[architecture.md](architecture.md)** | Arquitetura e comportamento implementado |
| **[review.md](review.md)** | Revisão geral, riscos e próximas etapas |
| **[feature-status.md](feature-status.md)** | Catálogo de features implementadas, parciais e futuras |
| **[shortcut-identity-review.md](shortcut-identity-review.md)** | Identidade dos launchers, migração, associação exclusiva de janelas e evidências de testes |
| **[device-shortcuts.md](device-shortcuts.md)** | Áudio na grade, cadastro de câmera/dispositivo e contrato futuro de controle via agente |
| **[pinned-apps.md](pinned-apps.md)** | Apps fixos, reordenação e projetos/pastas associados |
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

- JDK 21
- Android SDK 35 (build Android)
- Xcode (build iOS)

### Comandos

```bash
# Testes unitários
./gradlew :core:domain:jvmTest :core:protocol:jvmTest :core:data:jvmTest :agent:test

# APK debug
./gradlew :composeApp:assembleDebug

# Agente (dev — sobe o servidor e abre a janela PIN/IP)
./gradlew :agent:run

# Sem janela (só terminal)
./gradlew :agent:run --args="--headless"

# Instalar neste Mac (LaunchAgent — sobrevive a Cmd+Q / reboot)
./gradlew :agent:installMacos

# .app local, sem alterar LaunchAgent
CONTROL_DESK_SKIP_INSTALL=1 ./gradlew :agent:runDistributable

# Instalador macOS (.dmg) — copie para outros Macs
./gradlew :agent:packageDmg
```

### Fluxo do produto

1. **Mac:** instalar agente (DMG) → serviço em background na porta **8741**
2. **Mac:** autorizar ControlDeskAgent em Acessibilidade (a tela abre automaticamente na instalação; a chave requer confirmação manual)
3. **Celular:** buscar Macs na rede ou informar IP, depois inserir PIN de 6 dígitos
4. **Celular:** grade de apps + mixer

Detalhes: [agent-install.md](agent-install.md), [mobile-app.md](mobile-app.md).

Se os ícones da grade ficarem vazios, atualize agente e app mobile para descartar os caches anteriores. O agente extrai o ícone `.icns` real do bundle macOS; o mobile substitui imagens em cache. A grade distribui células igualmente pela largura útil, mantém launchers persistentes com títulos em até duas linhas. Janelas do mesmo aplicativo são selecionadas por título e monitor; favoritos preservam o destino configurado.

Na tela de pareamento, **Buscar Macs na rede** usa mDNS/Bonjour para preencher nome, host e porta; o usuário ainda informa o PIN exibido no agente. O PIN expira após 10 minutos e o servidor bloqueia novas tentativas por cinco minutos depois de cinco PINs inválidos do mesmo endereço. Em Configurações do agente, “Desconectar todos os dispositivos pareados” revoga os tokens globalmente.

---

## Estrutura do monorepo

```
core/domain      Use cases, entities, interfaces (Clean Architecture)
core/protocol    DTOs + AgentApiClient (compartilhado mobile ↔ agent)
core/data        AgentGatewayImpl (Ktor client), JSON persistence
core/testing     Fakes para testes
core/logging     Kermit, Crashlytics Android e logs Firestore JVM
composeApp       UI Compose + ViewModels MVI
agent            Servidor Ktor + PlatformExecutor
```

---

## API do agente (v1)

| Rota | Descrição |
|------|-----------|
| `GET /health` | Status |
| `GET /setup` | PIN e porta (localhost only) |
| `GET /setup/health` | Estado local do serviço, PIN e Acessibilidade (localhost only) |
| `POST /setup/new-pin` | Gerar PIN (somente localhost) |
| `POST /setup/revoke-tokens` | Revogar todos os tokens e sessões WebSocket (somente localhost) |
| `WS /ws?token=…` | Snapshots, eventos e comandos em tempo real |
| `POST /pair` | Pareamento PIN → token |
| `GET /apps/running` | Janelas + apps fixados na Dock |
| `GET /apps/installed` | Catálogo autenticado de apps instalados (macOS) |
| `POST /files/browse` | Navegar por pastas do usuário no Mac para configurar atalhos |
| `POST /apps/launch` | Abrir/focar app |
| `POST /apps/close` | Encerrar app |
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
| Android | `{filesDir}/control_desk_hosts.json` (substituição atômica + recuperação `.bak`; desinstalar apaga o sandbox) |
| iOS | Documents/`control_desk_hosts.json` |
| Agente (tokens) | `~/.control-desk/agent_tokens.txt` |

---

## Testes

- **Unitários:** domain, protocol, agent (`commonTest` / JVM)
- **UI Android:** `composeApp/src/androidInstrumentedTest`
- **CI/E2E:** não há workflows nem diretório Maestro no checkout revisado.
- **Resultado da revisão:** veja [review.md](review.md) para os comandos verificados e limites conhecidos da suíte Android.

---

## DMG (macOS)

O script de empacotamento inclui **ControlDeskAgent.app**, o atalho **Applications** e **LEIA-ME.txt**. A pasta `docs/` não é copiada para o DMG atualmente.
