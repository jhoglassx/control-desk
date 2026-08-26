# Control Desk — Regras de interação instantânea

Princípio: **o usuário nunca espera a rede para ver feedback**. Toque no celular = resposta visual imediata; a rede confirma ou corrige em background.

Relacionado: [spec.md](spec.md), [mobile-app.md](mobile-app.md).

---

## 1. Princípio geral

| Camada | Regra |
|--------|-------|
| **UI (mobile)** | Atualiza **na hora** do toque (otimista). Não bloqueia a interface esperando HTTP. |
| **Rede** | Confirma ou reverte em background. Erro → snackbar + reversão do estado otimista. |
| **Agente** | Executa o comando o mais rápido possível na LAN (sem filas longas, sem trabalho pesado no hot path). |
| **Sync (poll)** | Só para **espelhar** o estado real do PC; **nunca** é o primeiro feedback do toque. |

**Meta de percepção:** o painel deve parecer um controle local (Stream Deck), não um “site lento”.

---

## 2. O que deve ser instantâneo (< 100 ms na UI)

Todas as interações do usuário no **app mobile**:

| Interação | Feedback imediato obrigatório |
|-----------|------------------------------|
| Toque em app/janela na grade | Tile clicado → **Em uso** (azul); outros perdem foco visual |
| Toque em aba Chrome | Mesmo comportamento de foco |
| Slider de volume | Thumb e % movem **durante** o drag, sem “pular” |
| Botões − / + volume | Nível na UI muda na hora |
| Mute volume | Estado mudo/unmute na hora |
| Mic on/off | Estado na hora |
| Trocar host pareado | Grade + mixer do **cache** aparecem sem tela vazia |
| Botão ↻ (refresh) | Indicador “Sincronizando…”; **não** esvaziar a grade se já há cache |

**Proibido na UI:**

- Esperar resposta HTTP antes de mudar visual do tile ou do mixer
- Desabilitar a grade inteira durante uma ação (`isExecuting` não pode travar todos os botões)
- Spinner full-screen após cada clique
- Slider “voltar” ao valor antigo enquanto o usuário arrasta

---

## 3. O que NÃO precisa ser instantâneo (background)

| Situação | Comportamento aceito |
|----------|------------------------|
| Lista de apps no PC | Atualização contínua em background (poll) |
| Ícones faltando | Tile com emoji/fallback primeiro; ícone real chega depois |
| Confirmação de foco no SO | Pode levar 200 ms–1 s; UI já mostrou otimista |
| Primeira carga sem cache | Loading na grade é aceitável **só** na primeira abertura do host |
| PIN / pareamento | Loading no formulário é aceitável (ação rara) |

O poll **complementa** o estado otimista; não substitui o feedback do toque.

---

## 4. Fluxo padrão de uma ação remota

```
Toque → UI otimista (0 ms)
     → HTTP fire-and-forget (async)
     → burst de sync rápido (confirmar)
     → poll normal continua
```

### 4.1 Sucesso

1. UI já refletiu o toque
2. Burst refresh (~350 ms × N) alinha com o agente
3. Poll periódico mantém sincronizado
4. **Não** “piscar” tile se o estado já bate com o otimista

### 4.2 Falha (timeout, 401, offline)

1. Reverter **só** o que foi otimista para essa ação
2. Snackbar curto com causa (sessão expirada, agente offline)
3. Refresh para estado real do agente

### 4.3 Falha parcial / lento

- Se o comando demora mas não falhou: manter otimista até timeout (ex.: 8 s pinned focus)
- Após timeout: alinhar com poll ou mostrar hint

---

## 5. Regras por tipo de ação

### 5.1 Focar app / aba (`REMOTE_LAUNCH`, `REMOTE_BROWSER_TAB`)

| Regra | Detalhe |
|-------|---------|
| Otimista | Tile clicado `isActive=true`, `isVisible=true`; outros `isActive=false` |
| Pinned focus | Manter highlight otimista se poll ainda não confirmou (até ~8 s) |
| Debounce de poll | Poll não deve “roubar” foco visual em 1 ciclo; exige confirmação antes de trocar tile ativo |
| Burst | Após clique, polls rápidos (não esperar o intervalo normal de 1,5 s) |
| Bloqueio | Outros tiles **continuam clicáveis** (ações paralelas permitidas na spec futura; hoje não travar UI) |

### 5.2 Volume

| Regra | Detalhe |
|-------|---------|
| Slider | Valor local durante drag; envia ao **soltar** |
| +/− | UI ±5 imediato; request com nível absoluto |
| Mute | Toggle visual imediato; reverte só em erro HTTP |
| Poll de áudio | **Suspenso** durante interação do usuário (~2 s) para não “lutar” com o slider |
| Conflito | Poll não altera slider enquanto `isDragging` |

### 5.3 Microfone

| Regra | Detalhe |
|-------|---------|
| Toggle | Estado imediato na UI |
| Erro | Reverte mute visual |
| Poll | Idem volume — hold durante toggle |

### 5.4 Trocar host

| Regra | Detalhe |
|-------|---------|
| Cache | Mostrar `buttonsCache` + `audioCache` **imediatamente** |
| Loading | `isLoadingApps` só se não há cache; nunca grade vazia se havia cache |
| Settle | Delay mínimo (~180 ms) opcional antes de overwrite com rede |

---

## 6. Agente — latência e execução

O mobile é otimista; o agente deve ser **rápido** para a confirmação chegar logo.

| Comando | Expectativa na LAN |
|---------|-------------------|
| `POST /apps/launch` | Foco perceptível no Mac em **< 500 ms** (ideal) |
| `POST /browser/tabs/switch` | Idem |
| `POST /system/volume` / `mic` | **< 300 ms** |
| `GET /apps/running` sem ícones | **< 400 ms** (lista leve) |
| `GET /apps/running` com ícones | Pode ser mais lento; mobile usa carga em duas fases |

**Regras do agente:**

- Hot path sem logs excessivos, sem AppleScript lento repetido no mesmo request
- Listagem com `icons=0` deve ser prioridade para sync rápido
- Comando de foco/volume **não** deve esperar listagem de apps
- Um único processo headless — sem fila artificial de comandos

---

## 7. Polling vs interação (limites atuais vs alvo)

| Constante | Valor hoje | Papel | Regra |
|-----------|------------|-------|-------|
| `AUTO_REFRESH_MS` | 1500 ms | Sync de apps | **Não** é o feedback do toque |
| `AUDIO_REFRESH_MS` | 2500 ms | Sync de áudio | Pausado durante uso do mixer |
| `BURST_REFRESH_MS` | 350 ms | Confirmação após clique | Obrigatório após ações do usuário |
| `AUDIO_HOLD_MS` | 2000 ms | Evita conflito slider/poll | Obrigatório durante ajuste |
| `PINNED_FOCUS_MAX_MS` | 8000 ms | Teto do otimista | Não reverter foco antes disso sem erro |

**Alvo futuro (opcional):** reduzir burst a **< 200 ms** e confirmar foco em **< 1 s** total; poll de fundo pode permanecer 1–2 s.

---

## 8. Indicadores de status (não bloqueantes)

| Indicador | Uso |
|-----------|-----|
| Dot amarelo + “Sincronizando…” | Refresh manual ou primeira carga |
| Dot verde + “Conectado…” | Sync em background OK |
| Snackbar | **Só erros** de ação; não sucesso rotineiro |
| `isExecuting` | Se existir, **não** deve bloquear outras interações |

---

## 9. Cenários de erro

| Cenário | UI instantânea | Depois |
|---------|----------------|--------|
| Agente offline | Toque ainda otimista (opcional: shake/haptic) | Snackbar + revert + hint |
| 401 token | — | Snackbar “re-parear” + revert |
| Acessibilidade negada no Mac | Grade vazia com hint | Não spinner infinito |
| Timeout > 5 s | Manter otimista até pinned timeout | Alinhar com estado real |

---

## 10. Fora de escopo v1 (mas regras já definidas)

- Animações de transição longas (> 200 ms) em ações críticas
- Modal de confirmação antes de focar app
- “Aguarde…” em cada clique
- Desabilitar mixer enquanto foca app

---

## 11. Checklist de implementação (referência futura)

- [ ] Toque na grade → visual em **mesmo frame** (otimista)
- [ ] Mixer → drag sem snap-back do poll
- [ ] `holdingAudio` / `isDragging` impedem conflito com sync
- [ ] Burst após cada ação do usuário
- [ ] Troca de host → cache imediato
- [ ] Falha → revert + snackbar, não silent fail
- [ ] Agente: foco/volume no hot path rápido
- [ ] Lista sem ícones no burst; ícones em carga lazy

---

## 12. Referência de código (estado atual)

| Área | Arquivo |
|------|---------|
| Otimista grade | `DeskViewModel.onButtonClick`, `applyPinnedFocus` |
| Otimista áudio | `onVolumeKnobClick`, `onVolumeLevelChange`, `onMicKnobClick` |
| Poll / burst | `DeskViewModel.startAutoRefresh`, `startBurstRefresh` |
| Slider local | `MixerPanel` (`isDragging`) |
| Cache host | `DeskViewModel.selectHost`, `buttonsCache` |

**Gap conhecido:** código atual tem timers e otimista parcial; reimplementação deve tratar **instantâneo** como requisito, não otimização opcional.
