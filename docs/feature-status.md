# Features do Control Desk

Inventário conferido no código em 15/10/2026. “Implementada” significa que há implementação no repositório; não significa que a feature foi validada em dispositivo ou com permissões de sistema reais. A análise e os riscos estão em [review.md](review.md).

## Prontas no código

| Feature | O que está implementado | Onde funciona |
|---|---|---|
| Desk remoto | Launchers persistentes por aplicativo, seleção das janelas, foco otimista e vários hosts | Android; UI compartilhada também compila como target iOS, sem app Xcode versionado |
| Pareamento | PIN de seis dígitos do agente, token Bearer persistido no host | Mobile ↔ agente na mesma LAN |
| Apps e janelas | Listagem nativa de instâncias; um launcher por aplicativo, seleção por título/monitor e estado ativo/visível | Nativo no macOS, requer Acessibilidade |
| Ação por instância | ID/PID da janela selecionada enviados por HTTP/WS e revalidados no agente; layout identificado pelo atalho persistente; fechar uma janela não termina o processo por contagem antiga | macOS; agente 1.1.14 |
| Dock | Atalhos de apps fixos, placeholders e estado de encerramento | macOS |
| Encerrar app | Toque longo e bloqueio de processos essenciais | macOS |
| Navegador | Abrir URL, fechar navegador, listar/selecionar abas Chrome | macOS; ações disponíveis na API e nos templates configurados |
| Áudio | Volume, mute, nível/mute do microfone e feedback otimista | Controle de SO real no macOS |
| Tempo real | WebSocket com snapshots, eventos de apps/áudio, ACK/erro e reconexão | Cliente e agente JVM |
| Fallback | Rotas HTTP protegidas e sincronização HTTP quando não conectado em tempo real | Mobile ↔ agente |
| Cache e grade | Ícones persistidos/substituíveis em JSON; células de largura igual; nome do launcher em até duas linhas; títulos e monitores na seleção de instâncias | Android/iOS |
| Ações da tela principal | Reorganizar, adicionar app fixo e abrir configurações em uma barra horizontal com ícones Material UI e áreas de toque uniformes | Android/iOS; teste instrumentado verifica alinhamento vertical |
| Configuração do agente | Janela com dados locais, PIN/IP, modo headless e keep-alive | Desktop macOS |
| Apps fixos e projetos | Tela dedicada com ícones reais, busca, fixar/remover/ordenar e associar projetos; cards da grade podem ser redimensionados (1×1, 2×1, 2×2, 1×2) e reorganizados em grid compacto | Android ↔ agente macOS; tamanho e ordem persistidos por Mac pareado |
| Distribuição | LaunchAgent, scripts de instalação/remoção e DMG | macOS |
| Painel dedicado | Activity apta a HOME/launcher e PIN local para abrir configuração | Android |
| Diagnóstico | Kermit, Crashlytics Android configurado no código e envio de logs JVM ao Firestore | Conforme Firebase e conectividade configurados |

## Parcial ou só infraestrutura

- **iOS:** fontes comuns, target iOS e controller existem; falta projeto Xcode dentro do repositório e validação de build/execução.
- **Windows/Linux:** seletor de SO está na UI e há caminho de execução JVM; o executor de apps e áudio usa comportamento simulado.
- **mDNS:** agente anuncia `_controldesk._tcp`; Android (NSD) e iOS (Bonjour) localizam Macs na LAN e preenchem host/porta; entrada manual continua disponível.
- **Grade:** launchers padrão e favoritos podem ser reorganizados em grade compacta; mudanças nas janelas não alteram tamanho ou ordem dos launchers.
- **Testes:** suites JVM e testes comuns/instrumentados estão no código; a validação Android/iOS e os workflows CI/E2E ainda faltam.

## Não implementadas

- Pareamento via QR e gestão/revogação individual por dispositivo.
- TLS na LAN e armazenamento protegido dos tokens.
- Editor de ações avançadas (macros/hotkeys), perfis e sincronização entre aparelhos. Reordenação e redimensionamento da grade já estão implementados.
- Integração nativa de controle de apps/áudio e instaladores para Windows/Linux.
- Projeto iOS completo versionado e distribuição TestFlight.
- Pipeline CI e fluxo E2E Maestro versionados no checkout.

## Próxima sequência recomendada

1. Testar o ciclo completo de conexão/reconexão do WebSocket.
2. Preparar projeto Xcode versionado e validar discovery/pareamento iOS em dispositivo real.
3. Centralizar versões do agente e configurar CI/E2E.
4. Completar segurança: revogação individual, TLS na LAN e armazenamento protegido.
5. Permitir vários atalhos do mesmo app para abrir projetos diferentes e mostrar confirmação do lançamento.

Confira o [roadmap](roadmap.md) para prioridades, a [arquitetura](architecture.md) para os módulos e o [guia de instalação](agent-install.md) para operar o agente macOS.

A [revisão de identidade](shortcut-identity-review.md) descreve a migração para launchers persistentes, os testes e os limites da validação em dispositivo.

## Atualização: controles na grade

Microfone e saída de áudio compartilham movimento, tamanho e persistência com os aplicativos. O cadastro de controles inclui câmera/dispositivo por aplicativo ou URL, executados pelo agente. As alternativas de app/URL abrem o painel configurado. O agente 1.1.15 também oferece descoberta e controles nativos por capacidade, com limites por sistema. Veja [device-shortcuts.md](device-shortcuts.md).


Câmeras e dispositivos podem usar **Abrir todos os controles**: um único atalho abre o painel Material 3 de capacidades e estados confirmados, com ações diretas opcionais. No Mac, a versão 1.1.15 inclui autorização e modos nativos de foco, exposição e balanço de branco conforme o driver; não oferece bloqueio global nem captura. Consulte [documentação de dispositivos](device-shortcuts.md).
