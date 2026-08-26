# Control Desk — App mobile (Android / iOS)

Regras de negócio e comportamentos esperados do **composeApp**.  
Especificação geral do projeto: **[spec.md](spec.md)**.  
Interação instantânea: **[interaction-rules.md](interaction-rules.md)**.

---

## 1. Visão

O app mobile é o **painel remoto**: pareia com o agente desktop na LAN e controla apps, janelas e áudio.

| Plataforma | Stack |
|------------|-------|
| Android + iOS | Kotlin Multiplatform + Compose Multiplatform |
| Arquitetura | MVI (ViewModel + `StateFlow`) + Clean Architecture (`core/domain`, `core/data`) |
| DI | Koin (`AppModule`) |
| HTTP | Ktor client via `AgentApiClient` / `AgentGatewayImpl` |

**Fora de escopo v1:** descoberta mDNS, QR, editor drag-and-drop, sub-páginas, sync na nuvem.

---

## 2. Navegação e telas

Definida em `App.kt` — enum `AppScreen { Desk, Pair }`.

| Tela | Composable | Entrada |
|------|------------|---------|
| **Desk** | `DeskScreen` | Padrão ao abrir o app |
| **Pair** | `PairHostScreen` | Botões `+`, `Hosts`, ou CTA no estado vazio |

**Transições:**

- Desk → Pair: `onPairHost` / `onManageHosts`
- Pair → Desk: `← Voltar` ou após pareamento bem-sucedido
- Após parear: `deskViewModel.refreshHosts()` + volta ao Desk

**Persistência de navegação:** `rememberSaveable` mantém a tela atual na rotação (se aplicável).

---

## 3. Tela Desk (`DeskScreen`)

### 3.1 Estado vazio (sem hosts pareados)

Componente: `EmptyDeskState`

| Elemento | Texto / ação |
|----------|----------------|
| Título | "Conecte seu computador" |
| Descrição | Pareie Mac, Windows ou Linux… |
| CTA | **Parear computador** → navega para Pair |

**Regra:** não exibe header, grade nem mixer.

### 3.2 Com hosts pareados

Layout vertical:

1. **DeskHeader** — título, ações, card de conexão
2. **WorkspacePanel** — grade de apps (`weight(1f)`)
3. **MixerPanel** — volume e mic na parte inferior

**Snackbar:** exibe `uiState.feedback` (erros de ação ou carga); `clearFeedback()` após mostrar.

### 3.3 DeskHeader

| Elemento | Comportamento |
|----------|---------------|
| Título | "Control Desk" / "Painel remoto" |
| **↻** | `refreshRunningApps()` — recarga com loading; desabilitado enquanto `isLoadingApps` |
| **Hosts** | Abre Pair (gerenciar hosts) |
| **+** | Abre Pair (adicionar PC) |

**Connection card:**

| Campo | Conteúdo |
|-------|----------|
| Nome | `host.name` |
| Status | Ver tabela abaixo |
| IP | `host:port` (monospace) |
| Chips de host | Só se `hosts.size > 1` — scroll horizontal |

**Status dot e label:**

| Condição | Dot | Label |
|----------|-----|-------|
| `isLoadingApps` | Amarelo (`Warning`) | "Sincronizando…" |
| `!isLoadingApps && hasHosts` | Verde (`Live`) | "Conectado · atualiza automaticamente" |
| Outro | Cinza | "Aguardando conexão" |

**Chip de OS no switcher:** MAC / WIN / LIN / PC (`osShortLabel`).

### 3.4 WorkspacePanel — grade de apps

**Seção:** "Apps no Mac" (subtitle dinâmico).

**Layout:** `LazyVerticalGrid` com `GridCells.Adaptive(minSize = 76.dp)` — **não** fixo 3 colunas na UI (o builder usa 3 colunas lógicas `row/col` nos dados).

**Vazio:** `EmptyWorkspaceCard` com `listHint` ou "Nenhuma janela detectada no PC".

**Subtitle da seção** (quando há apps):

```
{N} apps · {focused} em uso · {onMonitor} visíveis · {hidden} ocultos · azul = em uso · verde = visível · chip = monitor
```

### 3.5 Tile de app (`AppLauncherTile`)

| Estado | Borda | Chip | Alpha |
|--------|-------|------|-------|
| **Em uso** (`isActive`) | Azul 3dp + glow | "Em uso" | 1.0 |
| **Visível** (`isVisible && !isActive`) | Verde 2dp | "Visível" | 1.0 |
| **Oculto** (`!isVisible`) | Cinza 1dp | "Oculto" | 0.45 |

**Ícone:**

- Base64 do agente → `rememberBase64Image`
- Fallback: emoji por label (`emojiForLabel`) + tint (`accentForLabel`)

**Label do tile:**

- `tileLabel`: parte antes do primeiro ` · ` (remove ` · min`)
- `tileSubtitle`: parte após ` · ` (título da janela)
- Badge `#N` se subtitle começa com `#` (instância duplicada)

**Monitor:** chip "Monitor N" se `monitorIndex > 0`.

**Clique:** `onAppClick(button)` → `DeskViewModel.onButtonClick`.

### 3.6 MixerPanel

Barra compacta (52dp altura):

| Controle | Ação ViewModel |
|----------|----------------|
| **−** | `onVolumeDown()` → delta −5 |
| **Slider** 0–100 | `onVolumeLevelChange` ao soltar |
| **+** | `onVolumeUp()` → delta +5 |
| **Mute / Unmute** | `onVolumeKnobClick()` |
| **Mic on / Mic off** | `onMicKnobClick()` |

**Regras UI:**

- Slider não atualiza externamente durante drag (`isDragging`)
- Mudo: thumb/track vermelho (`Danger`), texto "Mudo"
- Mic destacado quando muted (`highlighted`, `DangerSoft`)
- `enabled = hasHosts` — desabilitado sem host

**Regras de negócio (ViewModel):**

| Ação | API | Detalhe |
|------|-----|---------|
| Slider / +/− | `POST /system/volume` | `level` absoluto; unmute implícito |
| Mute toggle | `POST /system/volume` | `mute=true/false`; se unmute, envia `level` atual |
| Mic toggle | `POST /system/mic` | `mute=true/false` |
| Rotação knob (se usado) | volume: `delta = steps × 2`; mic: `inputDelta = steps × 2` | steps de `deltaDegrees / 5`, clamp ±20 |

**Otimista:** mute volume/mic atualiza UI antes da resposta; **reverte** em falha.

**`armAudioHold()`:** bloqueia poll de áudio por 2 s após interação do usuário.

---

## 4. Tela Pair (`PairHostScreen`)

### 4.1 Formulário "Adicionar PC"

| Campo | Default | Validação |
|-------|---------|-----------|
| Nome | vazio | obrigatório |
| IP / hostname | `10.0.2.2` | obrigatório |
| Porta | `8741` | inteiro válido |
| PIN | vazio | ≥ 4 caracteres (agente usa 6) |
| Tipo SO | `MACOS` | chips: MACOS, WINDOWS, LINUX |

**Card de ajuda IP:**

- Emulador Android no Mac → `10.0.2.2`
- Celular físico → IP LAN do Mac (janela do agente)
- **Não** usar `127.0.0.1` no emulador/celular

### 4.2 Fluxo de pareamento

1. Validação local (`PairHostViewModel.pair`)
2. `GET /health` (via `PairHostRemoteImpl`)
3. `POST /pair` com PIN
4. Salva `PairedHost` + token em JSON
5. `id = "$host:$port"` — **re-parear mesmo IP/porta atualiza token**
6. `sortOrder = quantidade atual de hosts`
7. `onPaired()` → refresh Desk + volta

**Erro:** mensagem em vermelho no formulário.  
**Sucesso:** limpa nome e PIN; mensagem verde (antes de navegar).

### 4.3 Lista "PCs pareados"

- Lista todos os hosts com `HostChip`
- **🗑** → `removeHost(hostId)` — remove só do JSON local (**não** revoga no agente)

---

## 4.1 Interação instantânea (requisito)

**Todas** as interações do usuário devem parecer locais. Detalhes em **[interaction-rules.md](interaction-rules.md)**.

Resumo:

- Toque na grade → tile **Em uso** na hora (otimista); HTTP em paralelo
- Mixer → slider/mute/mic mudam na hora; poll de áudio pausado durante uso
- Troca de host → cache imediato, sem grade vazia
- Poll (1,5 s / 2,5 s) só sincroniza fundo; **nunca** é o primeiro feedback do toque
- Erro → snackbar + revert do otimista; sucesso não exige modal

---

## 5. DeskViewModel — estado e ciclo de vida

### 5.1 `DeskUiState`

| Campo | Uso |
|-------|-----|
| `hosts` | Lista pareada |
| `selectedHostIndex` | Host ativo |
| `buttons` | Tiles da grade |
| `isExecuting` | Ação em andamento |
| `isLoadingApps` | Carga / refresh manual |
| `volumeLevel`, `volumeMuted`, `micMuted` | Mixer |
| `listHint` | Hint quando lista vazia ou erro |
| `feedback` | Snackbar (erro de ação) |

### 5.2 Inicialização

1. Carrega `iconCache` de `RunningAppIconCache`
2. Observa `hostPreferences.observeHosts()`
3. Se hosts vazios → limpa estado, para polling
4. Se há hosts → `loadHost()` para índice selecionado

### 5.3 `loadHost()` — sequência

1. Se sem cache e `showLoading` → `isLoadingApps = true`
2. Se tem cache → delay `HOST_SWITCH_SETTLE_MS` (180 ms) antes de atualizar
3. **Paralelo:** `loadRunningApps(includeIcons=false)` + `fetchAudio()`
4. Publica apps rápidos → `isLoadingApps = false`
5. Se faltam ícones → segunda carga `includeIcons=true` com `preferMerge=true`
6. `startAutoRefresh()` — polling contínuo

### 5.4 Cache por host

| Cache | Conteúdo |
|-------|----------|
| `buttonsCache` | Lista de `GridButton` |
| `audioCache` | volume, mute, mic |
| `listHintCache` | hint da lista |
| `iconCache` (memória + disco) | base64 por `buttonId` |

**Troca de host:** restaura cache imediatamente; recarrega em background se necessário.

### 5.5 Timers

| Constante | Valor | Uso |
|-----------|-------|-----|
| `AUTO_REFRESH_MS` | 400 ms | Poll apps (ícones do cache do agente) |
| `AUDIO_REFRESH_MS` | 2500 ms | Poll áudio + check ícones |
| `AUDIO_HOLD_MS` | 2000 ms | Bloqueia fetch áudio após ajuste usuário |
| `BURST_REFRESH_MS` | 350 ms | Intervalo burst após clique |
| `BURST_REFRESH_COUNT` | 4 | Repetições do burst |
| `PINNED_FOCUS_MAX_MS` | 8000 ms | Timeout highlight otimista |
| `ACTIVE_CONFIRM_POLLS` | 1 | Aceita o app ativo no primeiro poll |
| `FOCUS_SETTLE_MS` | 400 ms | Entre polls de confirmação |
| `FOCUS_CONFIRM_POLLS` | 3 | Polls após foco bem-sucedido |
| `HOST_SWITCH_SETTLE_MS` | 180 ms | Após trocar host |
| `MISSING_ICON_CHECK_EVERY` | 6 ticks | Check ícones faltando (6 × 2,5 s ≈ 15 s) |

### 5.6 Clique em botão (`onButtonClick`)

1. `armPinnedFocus(button)` — guarda buttonId, windowNumber, appName+title
2. UI otimista: clicado `isActive=true`, outros `false`
3. `startBurstRefresh()` — 1 + 4 polls rápidos
4. `executeGridButton(button, host)`
5. **Sucesso:** delay `FOCUS_SETTLE_MS` + 3× refresh com `FOCUS_SETTLE_MS`
6. **Falha:** `clearPinnedFocus()`, snackbar com erro

### 5.7 Pinned focus

Mantém tile clicado como "Em uso" até:

- Agent confirma `isActive` no poll, ou
- Timeout 8 s, ou
- Falha na ação

**Match:** `buttonId`, ou `windowNumber` no payload, ou `target + windowTitle`.

### 5.8 Estabilização de lista (`stabilizeActiveState`)

Quando janela ativa muda no poll:

- Exige **2 polls consecutivos** com mesmo novo `incomingActiveId` antes de aceitar
- Entre polls 1 e 2: mantém visual do `currentActiveId` (evita flicker)

### 5.9 Merge de botões (`preferMerge=true`)

- Atualiza botões existentes por `id`
- Adiciona novos apps
- Preserva ícones anteriores se novo poll não traz ícone
- Se `preferMerge && apps.isEmpty()` → mantém lista atual (não limpa em poll vazio)

### 5.10 Falha ao carregar apps

| Situação | Comportamento |
|----------|---------------|
| Primeira carga (`showLoading`) | Lista vazia, `listHint = erro`, snackbar |
| Poll silencioso com cache | Mantém cache; não snackbar |
| Sem cache | Para auto-refresh, lista vazia + hint |

### 5.11 Hint lista vazia

```
Nenhuma janela detectada. No Mac, permita Acessibilidade para o agente (foco nas janelas).
```

---

## 6. Geração de botões (`RunningAppsGridBuilder`)

Executado no **data layer**, consumido pelo ViewModel.

| Tipo | ActionType | Payload |
|------|------------|---------|
| Janela normal | `REMOTE_LAUNCH` | `target`, `targetType=appName`, `windowTitle?`, `windowNumber?`, `windowIndex?` |
| Aba Chrome | `REMOTE_BROWSER_TAB` | `browser=chrome`, `action=select`, `value`, `windowIndex?` |

**Ordenação:** ativo → visível → label alfabético.

**ID estável:** `windowNumber` → `axWindowIndex` → `windowIndex` → hash título.

**Grid lógica:** `row = index/3`, `col = index%3` (para futuro editor; UI usa adaptive grid).

---

## 7. Execução de ações (`ExecuteGridButtonUseCase`)

| ActionType | Endpoint | Payload obrigatório |
|------------|----------|---------------------|
| `REMOTE_LAUNCH` | `POST /apps/launch` | `target` |
| `REMOTE_BROWSER_OPEN_SITE` | `POST /browser/open` | `url` |
| `REMOTE_BROWSER_CLOSE_APP` | `POST /browser/close` | — |
| `REMOTE_BROWSER_TAB` | `POST /browser/tabs/switch` | — |
| `REMOTE_VOLUME` | `POST /system/volume` | `delta` ou `mute` |
| `REMOTE_MIC_MUTE` | `POST /system/mic` | `toggle=true` |
| `OPEN_URL` | — | Erro: local only |

Sem token → `ControlDeskError.Unauthorized`.

---

## 8. Persistência local

### 8.1 Hosts pareados (`JsonHostPreferences`)

| Plataforma | Arquivo | Diretório |
|------------|---------|-----------|
| Android | `control_desk_hosts.json` | `context.filesDir` |
| iOS | `control_desk_hosts.json` | Documents |
| JVM | `hosts.json` | `~/.control-desk/` |

**Por host:** `id`, `name`, `host`, `port`, `osType`, `sortOrder`, **token**, `buttons[]`.

**Ao parear:** se host novo, `DefaultButtonTemplates` cria VOL+ e MIC nos `buttons` (salvos mas **não exibidos** na grade dinâmica v1).

### 8.2 Cache de ícones

`control_desk_icon_cache.json` — mapa `buttonId → base64`.

`putIfAbsent` ao receber ícone novo; persistido em disco.

### 8.3 Android init

`ControlDeskApplication` → `PlatformStorage.init(context)` antes do Koin.

---

## 9. Rede e erros (`AgentGatewayImpl`)

| Situação | Mensagem / comportamento |
|----------|--------------------------|
| HTTP 401 | "Sessão expirada. Vá em + Parear, use o PIN do agente e pareie o PC de novo..." |
| Conexão recusada | "Agente offline. Inicie no Mac: ./gradlew :agent:run" |
| Outros | Mensagem da exceção ou genérica |

**Transporte:** HTTP cleartext (sem TLS v1).

---

## 10. Plataforma Android

**Manifest:**

- `INTERNET`
- `usesCleartextTraffic=true`
- `adjustResize` no teclado (formulário Pair)

**Default IP pareamento:** `10.0.2.2` (emulador).

---

## 11. Plataforma iOS

**ATS:** `NSAllowsArbitraryLoads=true` em `Info.plist`.

**Persistência:** Documents/`control_desk_hosts.json`.

Build: ver [ios-setup.md](ios-setup.md).

---

## 12. Tema e design (`DeskDesign`, `DeskColors`)

- Tema escuro estilo painel profissional
- Gradiente de fundo na Desk (`DeskColors.ScreenGradient`)
- Cores semânticas: `Accent` (azul), `Mint` (verde), `Danger` (vermelho), `Warning` (amarelo)
- `AppTheme` / `ControlDeskTheme` para Pair (Material3)

---

## 13. Testes

| Tipo | Local |
|------|-------|
| Domain | `ExecuteGridButtonUseCaseTest`, etc. |
| Android UI | `composeApp/src/androidInstrumentedTest/DeskScreenTest.kt` |
| E2E | `e2e/maestro/launch_desk.yaml` |

**Contrato mínimo UI:** estado vazio com CTA; com host mockado exibe header + grade.

---

## 14. Roadmap mobile (não v1)

- [ ] mDNS browse — listar PCs sem IP manual
- [ ] Pareamento QR
- [ ] Sub-páginas: Apps · Browser · Áudio
- [ ] Editor drag-and-drop de botões
- [ ] Exibir VOL+/MIC templates na grade ou mixer dedicado
- [ ] `RotaryKnob` (existe componente; Mixer usa slider)
- [ ] Revogar token no agente ao remover host
- [ ] HTTPS LAN opcional

---

## 15. Referência de código

| Área | Arquivo |
|------|---------|
| Navegação | `composeApp/.../App.kt` |
| Desk UI | `composeApp/.../DeskScreen.kt` |
| Pair UI | `composeApp/.../PairHostScreen.kt` |
| Desk lógica | `composeApp/.../viewmodel/DeskViewModel.kt` |
| Pair lógica | `composeApp/.../viewmodel/PairHostViewModel.kt` |
| Grade UI | `composeApp/.../components/WorkspacePanel.kt` |
| Mixer UI | `composeApp/.../components/MixerPanel.kt` |
| Header | `composeApp/.../components/DeskHeader.kt` |
| DI | `composeApp/.../di/AppModule.kt` |
| Grid builder | `core/data/.../RunningAppsGridBuilder.kt` |
| Gateway HTTP | `core/data/.../AgentGatewayImpl.kt` |
| Pareamento remoto | `core/data/.../PairHostRemoteImpl.kt` |
