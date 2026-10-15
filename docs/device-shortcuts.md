# Dispositivos e ações pelo agente

Atualizado em 15/10/2026. Requer **agente 1.1.14** (`deviceCatalogVersion=1`) e o APK desta entrega.

## Fluxo implementado

1. Na tela principal, toque em **+ / Adicionar atalho** → **Adicionar controle ou dispositivo**.
2. Escolha câmera, microfone, saída de áudio ou outro dispositivo.
3. Escolha **Dispositivo detectado pelo agente** e aguarde a descoberta.
4. Selecione o dispositivo e uma ação suportada. Um dispositivo sem capacidades aparece para consulta, com a limitação explicada; não é possível salvar uma ação inexistente.
5. Defina o nome e salve. Reordenação e tamanhos 1×1, 2×1, 2×2 e 1×2 continuam disponíveis.

O caminho completo é **mobile → API autenticada do agente → adaptador nativo → dispositivo**. Não há acesso direto do celular ao periférico e não é necessário integrar cada aplicativo para os controles nativos suportados.

As alternativas **Abrir aplicativo** e **Abrir painel web** permanecem disponíveis, mas não representam controle direto de hardware. Os knobs antigos continuam controlando o áudio padrão do Mac; um atalho associado a dispositivo físico executa a ação escolhida, sem confundir o dispositivo com o áudio padrão.

## Suporte entregue

| Sistema / categoria | Descoberta | Ações |
|---|---|---|
| macOS / câmeras | `system_profiler SPCameraDataType`, usando o UID do dispositivo | Consulta. Sem chave pública global de bloquear/liberar câmera neste adaptador; nenhuma sessão de captura é iniciada |
| macOS / áudio | CoreAudio via JNA, com UID e direção entrada/saída | Selecionar padrão; mute e nível quando a propriedade principal existir e for gravável |
| Windows / câmeras | PnP: classe Camera e dispositivos Image associados ao serviço USB Video | Habilitar/desabilitar quando o agente já tiver privilégios administrativos e o dispositivo não apresentar outro erro |
| Windows / áudio | Endpoints MMDevice presentes via PnP | Consulta. Controle Core Audio de endpoints Windows ainda pendente |
| Linux / câmeras | `/sys/class/video4linux` e `v4l2-ctl` | Bloquear/liberar privacidade somente quando o driver expõe `privacy` gravável |
| Linux / áudio | `pactl`, PulseAudio ou compatibilidade PulseAudio do PipeWire | Selecionar padrão, mute e nível por entrada/saída; fontes monitor são excluídas |

Não existe suporte automático a todo periférico USB/Bluetooth ou a todo parâmetro de câmera. O catálogo cobre as categorias acima. Zoom, foco, exposição, protocolos de câmeras IP, plugins de fabricantes e captura em OBS/Teams/Zoom continuam fora desta entrega. O registro de adaptadores permite acrescentá-los sem codificar regras por fabricante no mobile.

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

- Atualize o agente para **1.1.14**. O build do DMG não atualiza a cópia já instalada. Preserve o pareamento atual; não desinstale o mobile para atualizar.
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

Na primeira abertura, o agente consulta o inventário e mostra **Dispositivos e permissões**, com diagnóstico e botão de nova verificação. O fluxo de Acessibilidade continua sendo solicitado automaticamente no Mac, com confirmação manual obrigatória do sistema. A lista de câmeras não inicia captura e não pede acesso de captura sem necessidade.

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
