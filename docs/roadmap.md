# Control Desk — Roadmap

Estado revisto em 13/10/2026. Código existente descrito em [architecture.md](architecture.md); evidências dos problemas em [review.md](review.md).

Para o inventário feature a feature, consulte [feature-status.md](feature-status.md).

## Base implementada

- Agente Ktor com health, pareamento, autenticação Bearer e WebSocket.
- Controle macOS de apps/janelas, Dock, Chrome, volume e microfone.
- LaunchAgent, janela de configurações e empacotamento DMG.
- UI Compose, pareamento, múltiplos hosts, mixer, cache de ícones e ações otimistas.
- Polling HTTP quando o WebSocket não está conectado.
- Anúncio mDNS pelo agente.
- Descoberta mDNS no app Android/iOS, com seleção do agente e preenchimento de host/porta.
- PIN do agente expira em 10 minutos; `/pair` limita falhas a cinco por endereço em cinco minutos.
- Revogação global dos tokens no painel local do agente, incluindo desconexão das sessões ativas.
- Testes de domínio, protocolo, dados, agente, ViewModels e UI Android presentes no código. Presença não significa execução bem-sucedida.

## Prioridade de correção

1. Concluído: não repetir automaticamente por HTTP ações cujo envio via WebSocket falhou ou expirou; informar resultado não confirmado e atualizar estado apenas por leitura. Se o WebSocket já estiver indisponível antes do envio, o fallback HTTP continua permitido.
2. Adicionar teste integrado de ciclo de vida/reconexão do WebSocket.
3. Validar descoberta e pareamento Android/iOS em LAN real; versionar um projeto Xcode e testar iOS em dispositivo.
4. Centralizar versão do agente e criar CI para testes/builds.
5. Limitar fila e tempos de espera do uploader de logs Firestore.
6. Fazer a UI refletir o suporte real de plataforma e completar segurança com revogação individual, TLS e armazenamento protegido.

Concluído: corrigidos os bloqueios de compilação em `core/testing` e nos testes do agente. A última execução registrada passou nas suítes JVM de domínio, protocolo, dados e agente. Também foram corrigidas a propagação de erros WebSocket, a limpeza de pendências e o retorno de falhas correlacionadas do executor.

Próxima feature de produto recomendada: editor visual para personalizar botões e layouts. A descoberta mDNS já está implementada no app; falta exercitá-la em rede e dispositivos reais. Os controles básicos de pareamento também foram adicionados, mas o uso ainda deve ser tratado como LAN confiável enquanto não houver TLS e armazenamento protegido.

## Próximas entregas

- Projeto Xcode versionado e validação iOS em dispositivo/simulador.
- Testes E2E de pareamento, foco e mixer; testes instrumentados em CI.
- Pareamento por QR.
- Menu nativo macOS e gestão de dispositivos pareados.
- Editor de botões/layouts e novos comandos de browser/hotkeys.
- Controle nativo Windows/Linux e respectivos instaladores/auto-start.
- TLS na LAN e armazenamento protegido dos tokens.

## Fora de escopo atual

Relay na nuvem, streaming, mouse remoto, sync entre celulares e loja de plugins.
