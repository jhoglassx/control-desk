# Instalar o agente (Control Desk)

O **agente** é o serviço que roda no Mac e responde ao app mobile na porta **8741**.

Especificação completa: **[README.md](README.md)** (índice) e **[spec.md](spec.md)**.

---

## Comportamento esperado

| Ação | Resultado |
|------|-----------|
| **ControlDeskAgent.app** (abrir em Aplicativos) | Instala/atualiza o serviço e mantém a janela visível no Dock |
| **Concluir** / fechar janela de configuração | Fecha a janela; LaunchAgent continua ativo em segundo plano |
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

`agent/build/compose/binaries/main/dmg/ControlDeskAgent-1.1.14.dmg`

O agente compilado é compatível com o runtime Java 17 incluído neste pacote; o usuário não precisa instalar Java.

Conteúdo: **ControlDeskAgent.app**, atalho **Applications** e **LEIA-ME.txt**.

Os alvos `:agent:installMacos` e `:agent:packageDmg` assinam o app se `CONTROL_DESK_CODESIGN_IDENTITY` apontar para uma identidade confiável disponível no Acesso às Chaves. `CONTROL_DESK_SIGNING_KEYCHAIN` é opcional e seleciona o keychain quando a identidade não está no keychain padrão. O build verifica a assinatura e mostra a identidade designada. Sem certificado configurado, ele avisa e usa a assinatura ad hoc padrão; essa assinatura não mantém a autorização do macOS entre recompilações.

Para builds que precisam preservar Acessibilidade, configure uma identidade confiável e persistente. Para distribuição pública, use um certificado **Developer ID Application**, configure `CONTROL_DESK_CODESIGN_IDENTITY` com o nome da identidade e `CONTROL_DESK_SIGNING_KEYCHAIN` com o keychain correspondente antes do build. Uma assinatura autoassinada local não substitui Developer ID nem notarização.

### 2. Instalar (usuário)

1. Abra o DMG e arraste **ControlDeskAgent.app** para **Aplicativos**.
2. Abra o app em Aplicativos. Na primeira execução, ele se instala como serviço de usuário, inicia junto com o login e abre a tela de configuração. Se faltar Acessibilidade, o agente abre automaticamente o painel correto dos Ajustes do Sistema.
3. Deixe esta janela aberta enquanto conecta o celular; use o PIN temporário exibido nela.
4. No app mobile, toque em **Buscar Mac na mesma rede**, selecione o Mac e informe o PIN.
5. Nos Ajustes abertos pelo agente, ative **ControlDeskAgent** em **Acessibilidade**. O macOS exige esta confirmação manual; o instalador não pode concedê-la em silêncio.
6. Toque em **Concluir**. O agente continua no fundo mesmo com a janela fechada.

### 3. Acessibilidade (obrigatório)

**Ajustes → Privacidade e Segurança → Acessibilidade** → marque **ControlDeskAgent**.

Sem essa permissão, a detecção e o controle nativos de janelas ficam indisponíveis. Launchers salvos e itens do Dock podem continuar visíveis; sua presença não confirma que Acessibilidade esteja autorizada.

Na instalação e ao abrir a janela do agente, o app abre **Privacidade e Segurança → Acessibilidade** e solicita a autorização ao macOS. O sistema exige que a pessoa confirme a chave: se **ControlDeskAgent** já estiver na lista, ligue-a; se não estiver, use `+` e escolha **ControlDeskAgent** em Aplicativos. Volte à janela do agente para confirmar o status. O app deve estar instalado em **Aplicativos**, não iniciado da Lixeira ou diretamente do DMG.

Sem identidade de assinatura persistente, cada build pode aparecer ao macOS como uma cópia diferente. Nesse caso, o status pode continuar desativado mesmo com uma entrada antiga marcada; remova essa entrada antiga com `−` e adicione a cópia atual instalada em `/Applications/ControlDeskAgent.app`. Para builds seguintes manterem a autorização, configure uma identidade persistente conforme descrito acima.

### 4. Parear no celular (uma vez)

1. Abra **Control Desk Agent** no Mac e deixe a janela mostrar o PIN atual.
2. No app: **Parear computador** → **Buscar Mac na mesma rede** → escolha este Mac e informe o PIN.
3. Se não aparecer, expanda “Problemas para encontrar?” na janela do agente para consultar endereços; no formulário do celular escolha configuração manual e use a porta **8741**.
4. O token fica salvo no celular; não é necessário parear novamente após reiniciar o Mac.

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
bash /Applications/ControlDeskAgent.app/Contents/app/resources/macos/install-macos.sh
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
| App não abre | Botão direito em Aplicativos → **Abrir** na primeira execução se o Gatekeeper bloquear |
| Duplo clique no .app não faz nada | Abra por Aplicativos e procure o aviso de erro. Veja `cat ~/.control-desk/install.log` e `cat ~/.control-desk/agent-launchd-error.log` |
| Gatekeeper bloqueia | Botão direito → **Abrir** na primeira vez |
| Instalação termina, mas o serviço não responde | Consulte `cat ~/.control-desk/agent-launchd-error.log`; a instalação agora valida `/health` antes de confirmar sucesso |
| `UnsupportedClassVersionError` nos logs | Está usando um pacote anterior ao `1.0.5`; substitua o app de Aplicativos pelo app do DMG novo e abra novamente |
| Porta 8741 vazia | `launchctl print gui/$(id -u)/com.controldesk.agent` e `cat ~/.control-desk/agent-launchd-error.log` |
| PIN mostra "—" na janela | Aguarde ou `grep pin= ~/.control-desk/agent.log` |
| Lista vazia no celular | Verificar Acessibilidade |
| 401 no app | Re-parear com novo PIN do agente |

---

## Windows / Linux (futuro)

O build configura apenas `TargetFormat.Dmg`. Não há MSI, DEB ou AppImage configurados; `NativePlatformExecutor` usa um executor simulado fora do macOS. Instaladores, controle nativo e auto-start dessas plataformas são backlog.

## Permissões na primeira abertura (agente 1.1.14)

O app abre a configuração após instalar/atualizar o serviço. A janela solicita Acessibilidade quando necessário e consulta automaticamente **Dispositivos e permissões**, mostrando os dispositivos encontrados, limitações de controle e falhas dos adaptadores. **Verificar novamente** repete a consulta depois de corrigir uma permissão. O diagnóstico usa `/setup/devices`, disponível somente em localhost.

| Sistema | Como conceder o necessário |
|---|---|
| macOS | Em Ajustes → Privacidade e Segurança → Acessibilidade, autorize ControlDeskAgent. Volte ao agente; o status é atualizado. A confirmação é exigida pelo macOS e não pode ser concedida pelo instalador |
| Windows | Para habilitar/desabilitar câmeras, o **processo servidor** precisa ser iniciado com **Executar como administrador** e o UAC deve ser confirmado. Elevar apenas uma janela de configuração não eleva um servidor já em execução. Não há instalador Windows com esse fluxo automatizado nesta entrega |
| Linux | Execute na sessão do usuário com acesso ao servidor PulseAudio/PipeWire e permissão de escrita no dispositivo V4L2. Confira os diagnósticos, instale `v4l-utils`/`pactl` conforme a distribuição e ajuste os grupos/permissões quando necessário; mudanças de grupo podem exigir sair e entrar na sessão |

Listar câmeras no Mac não inicia captura nem requer acesso de captura. O agente não solicita permissão de câmera para uma função que apenas consulta o inventário. Se uma futura integração iniciar captura, deverá solicitar a autorização correspondente nesse momento. Essa permissão não cria um comando global de bloquear/liberar câmera no macOS.

Permissão, dependência e suporte são situações diferentes: conceder Acessibilidade não habilita ações de câmera inexistentes; instalar uma ferramenta não torna todo driver compatível. O agente não contorna TCC/UAC nem altera permissões administrativas silenciosamente. Eventuais solicitações de Automação do macOS aparecem quando uma ação realmente usa a integração correspondente.

### Mesma versão, serviço de outra compilação

Uma atualização podia substituir o app em Aplicativos enquanto o LaunchAgent continuava com classes antigas em memória. Como o instalador verificava somente a versão, duas compilações `1.1.13` pareciam equivalentes; a janela nova consultava `/setup/devices`, mas o serviço antigo retornava 404.

A partir de `1.1.14`, `/health` e `/setup/health` incluem `buildId`, calculado no início do processo a partir de todos os JARs da aplicação. O instalador calcula a mesma assinatura dos arquivos instalados e reinicia o serviço se a compilação divergir ou o serviço antigo não anunciar identidade, mesmo com versão igual. A verificação final também compara a identidade. Não altera pareamentos.

A configuração mostra a compilação e avisa quando difere do serviço. Erro 404 no diagnóstico passa a orientar reabrir o app instalado para sincronizar; não é apresentado como falha de permissão de câmera.


## Câmera nativa e painel de controles (1.1.15)

Atualize o agente e o mobile para esta entrega. O primeiro uso solicita Acessibilidade e, após essa etapa, autorização de câmera quando ainda não decidida. Confirme os diálogos no Mac; o instalador não pode conceder essas permissões. Em **Dispositivos e permissões**, use **Autorizar controles da câmera** para tentar novamente; se negado, habilite **Control Desk Camera** em Privacidade e Segurança → Câmera.

O inventário não inicia captura. Modos de foco, exposição e balanço de branco são oferecidos somente conforme AVFoundation/driver e autorização. Não há botão de bloqueio global da câmera no macOS. O helper nativo acompanha o app, sem dependências de compilação no computador do usuário. Veja [dispositivos e atalhos](device-shortcuts.md#painel-de-controles-e-câmera-nativa--1115).

## Diagnóstico dos comandos de aplicativos (1.1.16)

A revisão de abrir/selecionar/fechar encontrou no serviço 1.1.15 Acessibilidade ativa, falhas de abertura por caminho de bundle indisponível e erro `-1719` ao consultar diretamente um botão de fechamento da janela inicial do Xcode. O catálogo atual informou caminhos existentes para Xcode e Preview; portanto, a origem do caminho inválido recebido do mobile ainda exige reproduzir o toque com os novos logs.

A versão 1.1.16 consulta o atributo acessível `AXCloseButton`, usa `AXPress` e conserva a busca direta como alternativa quando o atributo não existe. Janelas sem controle acessível retornam erro explícito, sem encerrar outras instâncias. Selecionar restaura uma janela minimizada antes de `AXRaise`. Abrir aguarda a aceitação de `/usr/bin/open` e propaga código de erro/timeout; aceitação pelo sistema não garante que um documento terminou de carregar. O caminho ausente aparece na mensagem para permitir corrigir o atalho, sem abrir outro aplicativo arbitrariamente. A busca antiga por título parcial sem PID foi removida.

Logs `[app-command]` registram alvo, bundle, PID e identidade da janela, sem token ou PIN. Testes cobrem falha/timeout do processo, script de fechamento por atributo e restauração antes do foco. Testes de scripts não equivalem a executar comandos sobre aplicativos reais. A validação de abrir/fechar/selecionar no mobile continua necessária.
