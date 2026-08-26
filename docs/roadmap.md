# Control Desk — Roadmap

Backlog organizado para **reimplementação do zero**. A fonte de verdade de comportamento é **[spec.md](spec.md)**.

---

## Fase 0 — Fundação (obrigatório)

Corrigir base antes de novas features.

- [ ] Corrigir arquivos corrompidos (`AgentAuthStore`, `AgentTrayApp`, `DeskViewModel`, `AgentGatewayImpl`)
- [ ] Unificar nome do arquivo de hosts no desktop (`hosts.json` vs `control_desk_hosts.json`)
- [ ] Wire `MacBackgroundService.ensureInstalledAndRunning()` ao abrir o app (não-headless)
- [ ] Testes unitários passando: domain, protocol, agent
- [ ] CI verde

---

## Fase 1 — Agente mínimo funcional

- [ ] Servidor Ktor: `/health`, `/pair`, rotas protegidas com Bearer
- [ ] `AgentAuthStore`: PIN 6 dígitos, tokens persistidos, PIN uso único
- [ ] Modo headless + LaunchAgent (`install-macos.sh`, `uninstall-macos.sh`)
- [ ] Janela de configurações consultando `GET /setup` local
- [ ] **Padrão:** abrir app → instala background + mostra configurações
- [ ] `GET /apps/running` no macOS (com Acessibilidade)
- [ ] `POST /apps/launch` — focar janela
- [ ] `GET/POST /system/audio` — volume/mic real (AppleScript)
- [ ] DMG com `Documentacao/` (pasta `docs/`), instalar/remover `.command`

---

## Fase 2 — App mobile mínimo

- [ ] Pareamento: nome, host, porta, PIN → token salvo
- [ ] Desk vazio → CTA parear
- [ ] Grade dinâmica 3 colunas a partir de `/apps/running`
- [ ] Clique → focar app + feedback visual (ativo/visível/oculto)
- [ ] Mixer: volume, mute, mic
- [ ] Polling (apps 1,5 s, áudio 2,5 s)
- [ ] APK debug + esqueleto iOS

---

## Fase 3 — Polimento agente macOS

- [ ] Chrome: abas como tiles individuais + favicon
- [ ] `POST /browser/open`, `/close`, `/tabs/switch`
- [ ] mDNS anúncio `_controldesk._tcp`
- [ ] Ícones de apps (lazy load `icons=0` depois `icons=1`)
- [ ] Documentar Acessibilidade (dev: java/Terminal também)

---

## Fase 4 — UX mobile

- [ ] **Interação instantânea** — ver [interaction-rules.md](interaction-rules.md)
- [ ] Burst refresh e pinned focus após clique
- [ ] Cache de ícones local
- [ ] Seletor de múltiplos hosts
- [ ] Mensagens de erro claras (401, offline)
- [ ] Testes UI Android (instrumented)
- [ ] Maestro E2E: parear + focar app

---

## Fase 5 — Descoberta e pareamento avançado

- [ ] mDNS browse no mobile (sem digitar IP)
- [ ] Pareamento por QR (opcional)
- [ ] Revogar token no agente ("Desparear dispositivo")
- [ ] Ícone na bandeja do sistema (menu nativo macOS)

---

## Fase 6 — Painel completo

- [ ] Sub-páginas por PC: Apps · Browser · Áudio
- [ ] Editor drag-and-drop de botões
- [ ] Wizard: apps instalados + sites favoritos
- [ ] Botões padrão (VOL+, MIC) visíveis na grade ou mixer

---

## Fase 7 — Protocolo v1 completo

- [ ] `GET /apps/installed`
- [ ] `POST /apps/focus`, `POST /apps/close`
- [ ] `POST /input/hotkey`
- [ ] `GET /browser/tabs`, `POST /browser/tabs/new`, `POST /browser/tabs/close`
- [ ] Widgets: Audio Status, PC Status (online/offline)

---

## Fase 8 — Multi-plataforma desktop

- [ ] Windows: listagem apps, volume, instalador MSI
- [ ] Linux: wmctrl/xdotool/pactl, `.deb`/AppImage
- [ ] Auto-start: Task Scheduler / systemd user

---

## v1.1+ (baixa prioridade)

- [ ] HTTPS LAN (cert self-signed)
- [ ] Temas visuais + editor custom
- [ ] Perfis multi-PC
- [ ] SQLDelight (se layouts/histórico crescer)
- [ ] Detekt + ktlint no CI
- [ ] App iOS TestFlight

---

## Fora de escopo

- Relay/nuvem fora da LAN
- SSH fallback
- Sync entre celulares
- Wake-on-LAN, mouse remoto, streaming
- Loja de plugins

---

## Notas operacionais

| Situação | Ação |
|----------|------|
| Alterou **agente** | Reiniciar serviço ou `launchctl bootout` + bootstrap |
| Alterou só **mobile** | Rebuild/reinstalar app |
| **Reiniciou Mac** | Serviço sobe via LaunchAgent (após Fase 1) |
| **Abriu/fechou apps** | Auto-refresh no app ou ↻ manual |
| **Emulador no Mac** | IP `10.0.2.2`, porta `8741` |
| **Celular na Wi‑Fi** | IP LAN do Mac |

---

## Referência

- Especificação: [spec.md](spec.md)
- Interação instantânea: [interaction-rules.md](interaction-rules.md)
- App mobile: [mobile-app.md](mobile-app.md)
- Instalação: [agent-install.md](agent-install.md)
- iOS: [ios-setup.md](ios-setup.md)
