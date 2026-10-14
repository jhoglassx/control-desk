# Features do Control Desk

Inventário conferido no código em 14/10/2026. “Implementada” significa que há implementação no repositório; não significa que a feature foi validada em dispositivo ou com permissões de sistema reais. A análise e os riscos estão em [review.md](review.md).

## Prontas no código

| Feature | O que está implementado | Onde funciona |
|---|---|---|
| Desk remoto | Grade das janelas abertas, foco otimista, atualização e vários hosts | Android; UI compartilhada também compila como target iOS, sem app Xcode versionado |
| Pareamento | PIN de seis dígitos do agente, token Bearer persistido no host | Mobile ↔ agente na mesma LAN |
| Apps e janelas | Listagem por janela a cada 1 s, títulos próprios para distinguir várias janelas do mesmo app, ativo/visível e dados de monitor | Nativo no macOS, requer Acessibilidade |
| Ação por instância | Identidade PID/janela preservada no foco e no fechamento; atalho de projeto foca janela já aberta, e janelas do mesmo processo fecham individualmente | macOS; agente 1.1.7 |
| Dock | Atalhos de apps fixos, placeholders e estado de encerramento | macOS |
| Encerrar app | Toque longo e bloqueio de processos essenciais | macOS |
| Navegador | Abrir URL, fechar navegador, listar/selecionar abas Chrome | macOS; ações disponíveis na API e nos templates configurados |
| Áudio | Volume, mute, nível/mute do microfone e feedback otimista | Controle de SO real no macOS |
| Tempo real | WebSocket com snapshots, eventos de apps/áudio, ACK/erro e reconexão | Cliente e agente JVM |
| Fallback | Rotas HTTP protegidas e sincronização HTTP quando não conectado em tempo real | Mobile ↔ agente |
| Cache e grade | Ícones persistidos/substituíveis em JSON; células de largura igual; título da janela em até duas linhas com nome do app como fallback | Android/iOS |
| Configuração do agente | Janela com dados locais, PIN/IP, modo headless e keep-alive | Desktop macOS |
| Apps fixos e projetos | Tela dedicada com ícones reais, busca, fixar/remover/ordenar, navegador de pastas e associação de projeto/documento/arquivo | Android ↔ agente macOS; navegação autenticada limitada à pasta do usuário |
| Distribuição | LaunchAgent, scripts de instalação/remoção e DMG | macOS |
| Painel dedicado | Activity apta a HOME/launcher e PIN local para abrir configuração | Android |
| Diagnóstico | Kermit, Crashlytics Android configurado no código e envio de logs JVM ao Firestore | Conforme Firebase e conectividade configurados |

## Parcial ou só infraestrutura

- **iOS:** fontes comuns, target iOS e controller existem; falta projeto Xcode dentro do repositório e validação de build/execução.
- **Windows/Linux:** seletor de SO está na UI e há caminho de execução JVM; o executor de apps e áudio usa comportamento simulado.
- **mDNS:** agente anuncia `_controldesk._tcp`; Android (NSD) e iOS (Bonjour) localizam Macs na LAN e preenchem host/porta; entrada manual continua disponível.
- **Grade:** cards fixos e janelas abertas podem ser reorganizados juntos; a lista de janelas e seus estados continuam vindo do agente.
- **Testes:** suites JVM e testes comuns/instrumentados estão no código; a validação Android/iOS e os workflows CI/E2E ainda faltam.

## Não implementadas

- Pareamento via QR e gestão/revogação individual por dispositivo.
- TLS na LAN e armazenamento protegido dos tokens.
- Editor visual de grade, ações customizadas, perfis e sincronização entre aparelhos.
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
