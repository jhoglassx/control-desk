# Atalhos de áudio, câmera e dispositivos

Revisão de 15/10/2026. Este documento separa o que está implementado do contrato proposto para controle direto de hardware.

## Requisitos confirmados

- Microfone e saída de áudio devem participar da mesma grade dos aplicativos, com identidade, posição e tamanho persistidos por computador.
- O usuário deve poder cadastrar ações de câmera e outros dispositivos.
- Todo comando remoto deve passar pelo agente: **mobile → agente autenticado → sistema operacional / integração / dispositivo**. O celular não acessa diretamente a câmera ou seus serviços de rede.
- A descoberta futura deve considerar dispositivos disponíveis no PC/notebook, sem regras por marca, e informar capacidades reais em macOS, Windows e Linux.
- A referência de experiência é o Stream Deck: escolher ação, configurar destino e colocar o card na grade. Isso não implica compatibilidade com seus plugins.

## Revisão do layout e fluxo anterior

Volume e microfone eram inseridos pelo componente visual como duas células reservadas antes dos aplicativos. O layout impedia arrastar apps para essas células; os controles não tinham preferências próprias de tamanho/ordem. O cadastro existente aceitava somente aplicativos com caminho opcional de projeto, documento ou pasta.

Agora a composição de workspace reúne launchers e controles persistentes antes de renderizar. A grade não reserva as duas primeiras posições. Cada controle usa o mesmo shell do card, seleção, arraste, elevação e redimensionamento dos aplicativos. Em edição, o knob não captura os gestos de ajuste de nível; o card inteiro pode ser movido. Fora da edição, o controle conserva toque para mute e arraste para nível.

## Uso implementado

1. Na tela principal, toque em **+ / Adicionar atalho**.
2. Em **Controles e dispositivos**, toque em **Adicionar controle ou dispositivo**.
3. Escolha saída de áudio, microfone, câmera, outro dispositivo ou site/painel web.
4. Informe um nome. Para câmera/dispositivo/site, escolha um aplicativo instalado no Mac ou uma URL HTTP(S).
5. Salve. O card aparece na grade. Use **Reorganizar** para arrastar ou alternar 1×1, 2×1, 2×2 e 1×2.
6. Na lista de controles, use **Editar** ou **Remover**. A remoção explícita dos controles padrão permanece salva.

O formulário é uma tela dedicada com intents e estado MVI, validação, indicação de gravação e feedback de erro. A seleção de aplicativo exibe seu ícone. O card de câmera/dispositivo tem ícone Material correspondente ao tipo de ação; não representa uma câmera detectada automaticamente.

## Capacidades disponíveis nesta entrega

| Ação | Execução | Limite |
|---|---|---|
| Saída de áudio | Agente ajusta nível/mute do dispositivo padrão do Mac | Não escolhe uma saída física específica |
| Microfone | Agente ajusta nível/mute da entrada padrão do Mac | Não é o botão de mute interno de Teams/Zoom/OBS |
| Câmera / dispositivo por aplicativo | Agente abre o aplicativo de controle selecionado | Não altera captura, zoom ou liga/desliga o hardware |
| Câmera IP / painel web | Agente abre a URL no navegador do computador | Não transmite vídeo para o mobile nem envia comandos proprietários ao dispositivo |
| Site | Mesmo comando remoto de abertura de URL | Não é `OPEN_URL` local ao celular |

Não há descoberta automática de hardware nem endpoint de seleção de dispositivo nesta entrega. O catálogo existente é de **aplicativos instalados**, não de periféricos. O agente Windows/Linux continua com executores simulados; essas plataformas não devem ser tratadas como suporte de controle nativo pronto.

## Dados e arquitetura implementados

`PairedHost.controlShortcuts` persiste uma lista de `ControlShortcut`, com ID, nome, categoria, tipo de destino e alvo. Hosts antigos recebem os dois controles de áudio por padrão; uma lista explicitamente vazia permanece vazia. A edição usa as preferências mais recentes para preservar layout e pareamento.

`workspaceButtons` projeta os controles junto aos aplicativos para aplicar `appGridPositions`, `appOrder` e `appTileSizes["card:<id>"]`. Os controles ficam fora de `DockShortcutProjector` e `ShortcutWindowBinder`: não recebem janelas, monitor ou estado ativo de outro app. Favoritos de projeto continuam usando sua própria identidade.

Os comandos reutilizam o contrato autenticado do agente: volume/microfone, abertura de app e abertura de navegador. Ações de câmera/dispositivo não acionam foco otimista de aplicativo, não substituem o catálogo e não fecham processos por toque longo. Nome e destino configurados permanecem independentes dos snapshots de janelas.

## Próxima etapa: dispositivos reais pelo agente — proposta, não implementada

O agente deve expor um catálogo por capacidades, com adaptadores de plataforma e integrações desacopladas:

- **Identidade:** ID persistente fornecido pelo SO/driver, categoria, nome, adaptador, presença e capacidades. Não identificar hardware por índice na lista ou só pelo nome amigável.
- **Descoberta autenticada:** proposta `GET /devices`, retornando dispositivos e operações suportadas. Ausência de dispositivo, falta de permissão e adaptador indisponível devem ser estados diferentes.
- **Execução autenticada:** proposta `POST /devices/{id}/actions`, com ação tipada, parâmetros validados e ID de correlação. Revalidar presença/capacidade antes de executar; não selecionar outro dispositivo se o escolhido sumir.
- **Estado:** snapshots/eventos de conexão, dispositivo padrão, nível/mute e resultado confirmado pelo adaptador. Não indicar câmera ligada ou ação concluída com base só no toque.
- **Áudio:** adaptadores CoreAudio no macOS, Core Audio no Windows e PipeWire/PulseAudio/ALSA conforme disponibilidade no Linux. Permitir selecionar entrada/saída padrão e controlar nível/mute quando suportado.
- **Câmera:** enumerar dispositivos e recursos expostos pelo SO/driver. Não pressupor uma chave global de ligar/desligar nem seleção global da câmera usada por todos os apps. Ações de captura/cena/filtro exigem integração com o aplicativo; controles de câmera IP exigem protocolo suportado.
- **Integrações:** adaptador por API, como OBS ou fabricante, com configuração no agente. Segredos de integração devem ficar no armazenamento protegido do computador; o mobile referencia a conexão cadastrada.
- **Outros dispositivos:** publicar apenas ações que o adaptador consegue executar; dispositivo desconhecido pode oferecer abertura de painel, sem inventar capacidades de controle.

O fluxo final de cadastro deve ser **tipo de ação → dispositivo encontrado pelo agente → capacidade disponível → parâmetros → nome/ícone → salvar**. O atalho permanece na grade se o dispositivo desconectar, com indicação de indisponibilidade, tamanho e ordem preservados.

### Critérios de aceite para os adaptadores

Testar descoberta, dispositivos homônimos, mudança de porta, reconexão, remoção durante comando, falta de permissões, capacidade ausente, persistência e migração. Validar em máquinas reais dos três sistemas. Ações enviadas com resultado incerto não podem ser repetidas automaticamente. Abertura de painel e controle de hardware devem aparecer como ações distintas.

## Referências e validação

O modelo de integrações do produto de referência está descrito no [SDK Stream Deck](https://docs.elgato.com/streamdeck/sdk/guides/devices/) e no [controle de Cam Link Pro](https://help.elgato.com/hc/en-us/articles/360058671492-Cam-Link-Pro-How-to-Control-Multiview-with-Stream-Deck). São referências de interação e integração, sem dependência dessas APIs neste projeto.

A cobertura adicionada verifica migração de controles padrão, remoção persistida, validação de URL, destinos remotos, independência de identidade, preservação do layout mais recente ao salvar, cancelamento do formulário e tamanho/ordem de áudio após snapshot vazio. A compilação do APK instrumentado não equivale à validação visual ou a testes com periféricos reais.

### Execução registrada nesta revisão

Comando concluído com `BUILD SUCCESSFUL`:

```sh
./gradlew :core:domain:allTests :core:data:allTests :composeApp:allTests \
  :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
```

Os testes comuns executaram em Android/JVM e no simulador iOS ARM64; iOS x64 foi ignorado pelo ambiente. O APK de aplicação e o APK instrumentado foram gerados. Os testes instrumentados não foram executados, o APK não foi instalado no telefone nesta entrega e não houve validação de controle com periféricos reais. Nenhum adaptador de hardware novo foi entregue no agente.
