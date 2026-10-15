# Dispositivos e ações pelo agente

Atualizado em 15/10/2026. Requer **agente 1.1.15** (`deviceCatalogVersion=1`) e o APK desta entrega.

## Fluxo implementado

1. Na tela principal, toque em **+ / Adicionar atalho** → **Adicionar controle ou dispositivo**.
2. Escolha câmera, microfone, saída de áudio ou outro dispositivo.
3. Escolha **Dispositivo detectado pelo agente** e aguarde a descoberta.
4. Selecione o dispositivo. O padrão recomendado é **Abrir todos os controles**: o card abre uma tela dedicada de controles Material 3. Alternativamente, escolha uma ação direta suportada. Mesmo sem ações, é possível salvar o painel para consultar disponibilidade e permissões.
5. Defina o nome e salve. Reordenação e tamanhos 1×1, 2×1, 2×2 e 1×2 continuam disponíveis.

O caminho completo é **mobile → API autenticada do agente → adaptador nativo → dispositivo**. Não há acesso direto do celular ao periférico e não é necessário integrar cada aplicativo para os controles nativos suportados.

As alternativas **Abrir aplicativo** e **Abrir painel web** permanecem disponíveis, mas não representam controle direto de hardware. Os knobs antigos continuam controlando o áudio padrão do Mac; um atalho associado a dispositivo físico executa a ação escolhida, sem confundir o dispositivo com o áudio padrão.

## Suporte entregue

| Sistema / categoria | Descoberta | Ações |
|---|---|---|
| macOS / câmeras | Módulo AVFoundation empacotado, usando UID; inventário de fallback via `system_profiler` | Modos de foco, exposição e balanço de branco que o driver declarar configuráveis, após autorização. Sem bloqueio global ou sessão de captura |
| macOS / áudio | CoreAudio via JNA, com UID e direção entrada/saída | Selecionar padrão; mute e nível quando a propriedade principal existir e for gravável |
| Windows / câmeras | PnP: classe Camera e dispositivos Image associados ao serviço USB Video | Habilitar/desabilitar quando o agente já tiver privilégios administrativos e o dispositivo não apresentar outro erro |
| Windows / áudio | Endpoints MMDevice presentes via PnP | Consulta. Controle Core Audio de endpoints Windows ainda pendente |
| Linux / câmeras | `/sys/class/video4linux` e `v4l2-ctl` | Bloquear/liberar privacidade somente quando o driver expõe `privacy` gravável |
| Linux / áudio | `pactl`, PulseAudio ou compatibilidade PulseAudio do PipeWire | Selecionar padrão, mute e nível por entrada/saída; fontes monitor são excluídas |

Não existe suporte automático a todo periférico USB/Bluetooth ou a todo parâmetro de câmera. O catálogo cobre as categorias acima. Zoom e valores manuais contínuos de foco/exposição, protocolos de câmeras IP, plugins de fabricantes e captura em OBS/Teams/Zoom continuam fora desta entrega. Os modos nativos de foco/exposição/balanço de branco no Mac dependem das capacidades e permissões de cada câmera. O registro de adaptadores permite acrescentá-los sem codificar regras por fabricante no mobile.

No Windows, desabilitar o dispositivo afeta o sistema e alguns drivers exigem reinício. O agente não solicita elevação silenciosa. No Linux, privacidade não significa remover/desligar eletricamente o dispositivo. No macOS, não se apresenta uma câmera detectada como “ligada” ou “capturando”: disponibilidade não é captura.

## Identidade, execução e confirmação

- O atalho guarda seu próprio ID, o ID nativo do dispositivo e a operação. Nome, posição e tamanho não dependem do estado do hardware.
- IDs: UID de câmera e CoreAudio no Mac; InstanceId PnP no Windows; alias `/dev/v4l/by-id` ou caminho de hardware + índice de interface no Linux; nome persistente do servidor de áudio para PulseAudio/PipeWire. O fallback de câmera Linux pode mudar se a porta/caminho de hardware mudar.
- Nomes iguais não identificam dispositivos. O agente reconsulta o catálogo a cada comando, valida capacidade/parâmetros e nunca usa outro dispositivo como fallback.
- Alternar mute/disponibilidade consulta um estado recente e envia um comando explícito com o estado desejado. O mobile bloqueia toques concorrentes para o mesmo dispositivo durante a execução.
- Depois do comando, o agente consulta o estado até cinco vezes e só retorna `confirmed=true` se confirmar a alteração. Falha, remoção ou resultado não confirmado não geram sucesso fictício e não causam reenvio automático.
- O painel consulta o catálogo a cada cinco segundos quando o computador selecionado possui atalhos nativos, além da atualização após uma ação. Respostas antigas são descartadas por geração de requisição.
- Um dispositivo desconectado conserva o card e seu layout, com indicação de indisponibilidade. Estado desconhecido não é apresentado como ativo. O estado de dispositivos fica separado do foco e das janelas de aplicativos.

## Contrato implementado

Ambas as rotas exigem Bearer token de pareamento:

- `GET /devices` → `DeviceCatalogResponse { devices, diagnostics }`.
- `POST /devices/actions` → `DeviceActionRequest { deviceId, action, booleanValue?, level? }`.
- Resposta de comando → `DeviceActionResponse { device, confirmed }` com o estado confirmado.

`DeviceInfo` informa `id`, `name`, `kind`, `adapter`, `capabilities`, estados opcionais (`enabled`, `muted`, `level`, `isDefault`) e `notice`. `diagnostics` distingue adaptadores indisponíveis de uma lista simplesmente vazia; a falha de um adaptador não esconde dispositivos de outros.

Ações de transporte: `SET_ENABLED`, `SET_MUTED`, `SET_LEVEL` (0–100), `SET_DEFAULT`. Operações do atalho incluem habilitar, desabilitar, alternar disponibilidade, mutar, desmutar, alternar mute, nível fixo e dispositivo padrão. Parâmetros são tipados; o mobile não envia comandos de shell nem scripts arbitrários.

`ControlShortcut` preserva compatibilidade com os atalhos antigos de app/URL. `ShortcutDestination.DEVICE` e `ActionType.REMOTE_DEVICE` usam `ExecuteDeviceShortcutUseCase` → `AgentGateway` → `AgentApiClient`. O editor mantém estado/intents MVI. `DeviceService` valida e confirma ações; `DeviceAdapter` encapsula cada integração nativa. A API roda as consultas bloqueantes em `Dispatchers.IO`.

## Instalação e permissões

- Atualize o agente para **1.1.15**. O build do DMG não atualiza a cópia já instalada. Preserve o pareamento atual; não desinstale o mobile para atualizar.
- No Mac, CoreAudio e o inventário de câmeras usam APIs/ferramentas do sistema, sem instalar compiladores ou pacotes extras. Acessibilidade continua necessária para controle de janelas, mas não representa autorização global de câmera.
- Windows requer PowerShell/PnpDevice. A capacidade de habilitar/desabilitar só é anunciada quando o processo já é administrador. O instalador Windows ainda não faz parte do projeto.
- Linux requer `v4l-utils` para controles V4L2 e `pactl` com um servidor de áudio compatível. Permissões da sessão, grupo de vídeo e driver continuam aplicáveis. Não há fallback para comandos simulados de dispositivo.
- O restante do controle de apps/áudio legado em Windows/Linux ainda usa executores simulados; estes novos adaptadores de dispositivo não tornam todo o agente multiplataforma completo.

## Testes e limites

Cobertura de serviço: IDs homônimos, remoção, capacidades ausentes, parâmetros inválidos, adaptador com falha e ausência de confirmação. Fixtures de plataforma verificam câmera Mac sem captura, privilégios/IDs exatos no Windows e áudio Linux por nome persistente. Domínio/mobile cobrem serialização, alternância com estado recente, seleção de capacidades, toque duplicado, card desconectado e preservação de layout.

A leitura nativa de inventário de câmeras e áudio foi exercitada neste Mac, sem modificar propriedades ou iniciar captura. Windows/Linux usam fixtures nesta revisão; comandos de habilitar/desabilitar/mute/nível não foram executados em hardware real. Testes instrumentados compilados não equivalem à validação visual no telefone.

## Evolução

Próximos adaptadores: controle de endpoints Windows, controles UVC/V4L2 adicionais de câmera, integrações de fabricantes e APIs de aplicativos quando necessárias. Novas capacidades devem ter identidade persistente, validação de permissão, leitura do estado e testes por plataforma antes de aparecer como ação disponível.

Referências: [CoreAudio](https://developer.apple.com/documentation/coreaudio/kaudiohardwarepropertydefaultoutputdevice), [Windows PnP](https://learn.microsoft.com/en-us/powershell/module/pnpdevice/disable-pnpdevice), [controles V4L2](https://docs.kernel.org/userspace-api/media/v4l/ext-ctrls-camera.html). O modelo de catálogo/ações/configuração segue a referência de experiência do [Stream Deck](https://docs.elgato.com/streamdeck/sdk/guides/actions/), sem compatibilidade com seus plugins.

## Onboarding de permissões

Na primeira abertura, o agente consulta o inventário e mostra **Dispositivos e permissões**, com diagnóstico e botão de nova verificação. O fluxo de Acessibilidade continua sendo solicitado automaticamente no Mac, com confirmação manual obrigatória do sistema. A lista de câmeras não inicia captura. Para consultar e configurar modos nativos, o onboarding solicita autorização de câmera após Acessibilidade quando o estado é ainda não decidido; o usuário confirma no diálogo do macOS. Se negado, o botão de autorização abre os Ajustes.

A rota local `GET /setup/devices` permite esse diagnóstico antes de parear um telefone; é bloqueada para clientes fora de localhost. A API remota `/devices` e os comandos continuam exigindo token. No Windows/Linux, permissões administrativas/grupos não são elevadas silenciosamente. Consulte [instalação e permissões](agent-install.md#permissões-na-primeira-abertura-agente-1114).

### Correção de atualização na versão 1.1.14

O serviço instalado pode manter uma compilação antiga em memória mesmo exibindo o mesmo número de versão. Isso foi confirmado por `/health` 1.1.13 e `/setup/devices` 404. A versão 1.1.14 compara `buildId` do serviço com os JARs instalados e reinicia quando necessário, incluindo alterações em dependências. O diagnóstico distingue esse caso de permissão negada e a janela exibe a compilação do serviço.

## Validação desta entrega

Comando concluído com sucesso:

```sh
./gradlew :agent:test :core:domain:allTests :core:protocol:allTests :core:data:allTests \
  :composeApp:allTests :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest \
  :agent:packageInstallerDmg
```

O teste opt-in `CONTROL_DESK_TEST_NATIVE_DEVICES=1 ./gradlew :agent:test --tests '*MacDeviceInventoryTest'` também passou, exercitando somente leitura. O APK instrumentado foi compilado; os testes instrumentados e a validação visual no telefone não foram executados nesta revisão. Os testes comuns executaram no simulador iOS ARM64; iOS x64 foi ignorado pelo ambiente.

Após atualizar este Mac com `:agent:installMacos`, `/health` confirmou **1.1.14**, `deviceCatalogVersion=1` e identidade de compilação; `/setup/devices` retornou **200**, uma câmera, uma entrada e uma saída de áudio, sem diagnósticos de erro. O checksum do arquivo de tokens permaneceu igual. Nenhuma ação de alteração de dispositivo foi executada em hardware real.

A nova assinatura ad hoc voltou a exigir confirmação de Acessibilidade pelo usuário; o instalador abriu os Ajustes automaticamente. Essa autorização continua pendente na última verificação, embora o catálogo de dispositivos funcione. Para distribuição com preservação de permissões entre atualizações, continua necessário configurar assinatura persistente adequada.


## Painel de controles e câmera nativa — 1.1.15

O atalho **Abrir todos os controles** guarda `OPEN_CONTROLS`, mas abrir o painel não envia uma ação ao dispositivo. A tela apresenta estado confirmado, autorização, foco, exposição e balanço de branco; áudio apresenta mute, nível e padrão conforme capacidades. Ações diretas continuam opcionais. Controles ausentes têm explicação; desconectar não remove o card nem altera tamanho/posição. Voltar retorna ao grid e mudar de computador fecha o painel.

O contrato adiciona `settings` (mapa opcional) com `authorization`, `focusMode`, `exposureMode` e `whiteBalanceMode`. Ações nativas: `FOCUS_LOCKED/AUTO/CONTINUOUS`, `EXPOSURE_LOCKED/AUTO/CONTINUOUS` e `WHITE_BALANCE_LOCKED/AUTO/CONTINUOUS`. `REQUEST_CAMERA_ACCESS` solicita autorização separadamente. A rota `POST /setup/camera-access` aceita somente essa solicitação e somente localhost; a rota remota continua autenticada. Requisições aguardam até 95 segundos pelo diálogo. Comandos são serializados por dispositivo, sem bloquear o áudio de outro dispositivo durante a autorização.

O módulo `control-desk-camera` é compilado durante o build e incluído no app; o usuário final não precisa de Xcode. Inclui descrição de uso e entitlement de câmera. A entrada de privacidade pode aparecer como **Control Desk Camera**; confirme o diálogo no Mac. Se já negado, autorize em Ajustes → Privacidade e Segurança → Câmera. Uma assinatura persistente é necessária para distribuição previsível entre atualizações. Autorizar não garante controles que o driver não implementa.

Validação 1.1.15: testes verificam UID exato, capacidades, confirmação de modo, concorrência entre dispositivos, painel sem comando ao abrir, salvamento sem capacidades e manutenção do card desconectado. A descoberta real encontrou uma câmera com autorização `notDetermined`; nenhuma mudança de modo nem captura foi executada. O teste em hardware autorizado e a validação visual no telefone permanecem pendentes. O registro anterior de instalação 1.1.14 acima é histórico.

O empacotamento 1.1.15 também restaura a permissão executável do helper antes da assinatura e considera o conteúdo do `.app` como entrada do DMG, evitando reutilizar instaladores antigos após alterações no serviço.

A suíte final (`:agent:test`, `:core:protocol:allTests`, `:core:domain:allTests`, `:core:data:allTests`, `:composeApp:allTests`) e os builds do APK e APK instrumentado concluíram com sucesso. Os testes comuns rodaram em iOS ARM64; iOS x64 foi ignorado pelo ambiente. O APK instrumentado foi compilado, sem execução no telefone.

O DMG 1.1.15 foi regenerado com sucesso; a assinatura profunda foi verificada e o helper empacotado executou descoberta real. A atualização não foi instalada automaticamente neste Mac nesta etapa.
