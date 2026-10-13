# Revisão geral do Control Desk

**Revisado em:** 13/10/2026
**Escopo:** inventário funcional, arquitetura, protocolo, app, agente, persistência, empacotamento, testes e documentação. Nesta atualização, as suítes JVM e Android e os builds Android/iOS passaram. A assinatura estável do agente continua pendente porque não há certificado confiável configurado neste Mac.

O Control Desk já tem o fluxo funcional principal para controlar um Mac pela rede local: parear o telefone, listar janelas/apps e atalhos do Dock, focar ou encerrar apps, controlar abas do Chrome e ajustar volume/microfone. O app usa Compose Multiplatform; o agente macOS é um serviço JVM com API HTTP e WebSocket.

## Estado por área

| Área | Estado verificado | Limite atual |
|---|---|---|
| App Android | UI Compose compartilhada, pareamento, Desk, vários hosts, mixer, cache, modo launcher/painel, grade de títulos por janela | APK debug compila; instalação em telefone físico não foi exercitada |
| App iOS | Targets Kotlin e integração `MainViewController` presentes | Não há projeto Xcode versionado; requer integração manual e validação do app completo |
| Agente macOS | API, pareamento, janelas, foco, Dock, Chrome, áudio, configuração e instalação LaunchAgent/DMG | Requer permissões de Acessibilidade e automação do macOS conforme o comando |
| Agente Windows/Linux | Processo JVM pode iniciar | Executor usa dados/ações simulados; não é controle nativo |
| Tempo real | WebSocket autenticado, eventos de apps/áudio, comandos correlacionados e reconexão | Não há teste integrado de reconexão do cliente; resultado ambíguo exige nova tentativa manual após conferir o Mac |
| Descoberta | Serviço mDNS `_controldesk._tcp`; Android NSD e iOS Bonjour listam agentes e preenchem os dados de conexão | Validar em LAN/dispositivo real, incluindo permissão de rede local no iOS |
| Personalização | Atalhos padrão por host e grade das janelas correntes | Sem editor para criar/reordenar layouts e botões |
| CI/E2E | Testes unitários e instrumentados existem no código | Não foram encontrados workflows GitHub Actions nem testes Maestro neste checkout |

## Recursos implementados

### Aplicativo

- Desk com estado vazio, atualização manual e snackbar de erro.
- Pareamento com nome, host/IP, porta, PIN e sistema operacional selecionado.
- Lista e remoção local de hosts pareados; troca de computadores por pager horizontal.
- Grade derivada dos apps/janelas em execução e itens fixados no Dock do Mac.
- Foco otimista, encerramento por toque longo, proteção para apps essenciais e placeholder de Dock durante fechamento.
- Controles de volume e microfone: arrastar nível, mute e botões de volume.
- Cache local de host, botões, estado por host e ícones para renderização imediata.
- Reconciliação de foco/fechamento e estados otimistas com snapshots recebidos.
- Modo painel Android e diálogo de acesso administrativo com PIN fixo `8741`.
- Feedback de pareamento, offline e sessão inválida; opção de abrir URL é local ao app.

### Agente macOS

- Servidor Ktor CIO na porta padrão `8741`, bind padrão `0.0.0.0` e configuração de porta/interface por ambiente.
- Health e configuração local, geração/consulta de PIN de seis dígitos e pareamento por PIN para emitir token Bearer.
- Autenticação das rotas protegidas e do WebSocket; snapshots e comandos em tempo real.
- Consulta de apps e janelas por Acessibilidade, ícones carregados em fase separada, app em foco e índice de monitor.
- Mescla de abas Chrome e aplicativos/atalhos fixados no Dock.
- Foco/ativação, encerramento de processos de app, abrir/fechar navegador e seleção de abas.
- Leitura e ajuste de volume/microfone por AppleScript.
- Janela de configuração, modo headless, keep-alive, substituição de instância na porta e integração LaunchAgent.
- Anúncio mDNS e instalador DMG macOS com app, atalho Applications e LEIA-ME.

### Contrato e dados

- Módulos separados para domain, protocolo, data, logging e fakes de teste.
- HTTP com JSON para pareamento, fallback, health e comandos; WebSocket para snapshots e comandos com `id`.
- Preferências do mobile em JSON nos diretórios de arquivos Android/iOS; token incluído no objeto do host.
- Token, PIN e logs do agente em `~/.control-desk/`; sem armazenamento criptografado implementado.
- Logs Kermit; inicialização Firebase/Crashlytics Android e logger Firestore no JVM quando há configuração Firebase.

## Revisão técnica e riscos

### Resolvido — o DMG empacotava classes Java 21 com runtime Java 17

O Mac de build usa JDK 17 para executar Gradle e `jpackage`, mas o agente e módulos JVM dependentes geravam classes versão 65 (Java 21). O runtime dentro do DMG era Java 17, que aceita no máximo classes versão 61; `agent-launchd-error.log` registrava `UnsupportedClassVersionError`, o serviço encerrava e o navegador mostrava `ERR_CONNECTION_REFUSED` em `127.0.0.1:8741`.

**Correção:** agente, protocolo e logging JVM compilam para Java 17; o empacotamento aponta explicitamente para o JDK que executa Gradle. A janela de setup recebe o PIN pela rota localhost e o instalador verifica `/health`. O pacote atual é `1.1.1`; `/health` responde com a mesma versão.

### Resolvido — Timeout podia repetir um comando que já foi executado

`DeskViewModel.runAppCommand()` e o fechamento de app tentavam HTTP quando a chamada pelo WebSocket falhava. Um timeout no cliente não informa se o agente recebeu/executou o comando; a repetição por HTTP podia abrir duas vezes um site ou disparar outra ação com efeito colateral.

**Correção:** removi a repetição automática. Se o WebSocket já está desconectado antes da chamada, o app usa HTTP normalmente. Depois que o envio pelo WebSocket começa, falha de envio ou timeout agora apresenta “resultado não confirmado”; o app pode atualizar a tela por leitura, sem reenviar a ação. Rejeições explícitas do agente são exibidas como erro do comando.

### P1 — Segurança do pareamento ainda precisa de controles adicionais

O servidor agora limita `/pair` a cinco PINs inválidos por endereço em cinco minutos, expira PIN após dez minutos e oferece revogação global de tokens pelo endpoint local `/setup/revoke-tokens`, encerrando sessões WebSocket. A revogação continua global: tokens não têm expiração nem controle por dispositivo. O agente aceita conexões na LAN por HTTP/WS sem TLS; o token do WebSocket é enviado na query string. Os arquivos do agente e hosts mobile não têm camada de criptografia implementada. O PIN administrativo `8741` é fixo e protege apenas a navegação da interface de painel.

**Próximo passo:** revogação por dispositivo, avaliar TLS/armazenamento protegido e separar o PIN de acesso ao painel dos controles de segurança de rede.

### P2 — Teste integrado de conexão e reconexão WebSocket ausente

O correlacionador agora propaga `error`/`ok=false` e limpa pendências em timeout, cancelamento, falha no envio e desconexão. Testes unitários cobrem esses caminhos; o agente mantém a sessão após erro de executor. Ainda falta testar o `AgentRealtimeClient` com conexão, snapshot inicial, queda, reconexão e cancelamento do escopo. O estado `Connected` sobe logo após o handshake e antes do snapshot autenticado.

**Próximo passo:** teste de ciclo de vida cliente-servidor; confirmar semântica do estado de conexão e que uma sessão antiga não sobrescreva o estado de uma nova ao cancelar/trocar host.

### P2 — Telemetria JVM pode bloquear e acumular logs

O logger Firestore tem uma fila sem limite e `HttpURLConnection` sem timeouts. Falha ou lentidão de rede pode deixar a thread de envio parada e aumentar a fila em memória.

**Próximo passo:** timeouts de conexão/leitura, fila limitada, política de descarte, fechamento garantido da conexão e testes de indisponibilidade.

### Resolvido — Versões e descoberta divergentes

O pacote, `/health` e o TXT mDNS agora usam a versão `1.1.1`. A descoberta mDNS anuncia o ComputerName real e escolhe o IPv4 da rota padrão.

**Validação:** health local e anúncio DNS-SD foram verificados neste Mac; ainda falta repetir a descoberta com um celular em outra rede real.

### Resolvido — ícones corrompidos, nomes cortados e grade desalinhada

O macOS podia entregar ao extrator uma representação transparente inválida (reproduzido com ChatGPT), e os caches permanentes no agente/mobile preservavam essa imagem. O agente agora prioriza o `.icns` dentro do app, usa um diretório de cache novo e a persistência mobile aceita substituir ícones antigos. O agente envia o título de cada janela e mantém IDs distintas para várias janelas do mesmo processo; os cards mostram esse título (por exemplo, o nome do projeto no Android Studio), com o nome do app como fallback. A grade cria linhas com células do mesmo peso para usar simetricamente a largura disponível. Ícones maiores e conteúdo centralizado reduzem o espaço ocioso nos cards; o título pode usar duas linhas.

**Validação:** endpoint local respondeu com agente `1.1.1`, Acessibilidade ativa e PNGs reais 72×72 para ChatGPT, Finder, Chrome e demais apps. `JsonRunningAppIconCacheTest`, testes de grade/títulos, suíte do agente, suíte Android completa, build Android e compilação do target iOS passaram. Os testes do ViewModel usam o `backgroundScope` do `runTest` para controlar o relógio e cancelar o polling contínuo ao fim do teste. Instalação e inspeção visual no telefone físico continuam pendentes.

### P1 — Acessibilidade não sobrevive a builds ad hoc

A cópia instalada está assinada ad hoc, e o Mac não tem uma identidade confiável de assinatura configurada. O macOS identifica builds ad hoc pelo hash do código; por isso, a chave de Acessibilidade pode continuar azul para a cópia anterior enquanto `AXIsProcessTrusted()` retorna `false` na recompilada.

**Correção no repositório:** os alvos `:agent:installMacos` e `:agent:packageDmg` agora aceitam `CONTROL_DESK_CODESIGN_IDENTITY` e o keychain opcional `CONTROL_DESK_SIGNING_KEYCHAIN`, verificam a assinatura e imprimem a identidade designada. O instalador espera o processo antigo sair antes de reiniciar e abre automaticamente o painel de Acessibilidade quando a permissão falta. A janela também chama `AXIsProcessTrustedWithOptions` para solicitar o fluxo nativo de autorização.

**Pendente neste Mac:** não foi encontrado certificado confiável; sem ele o build avisa e permanece ad hoc. É necessário configurar uma identidade persistente antes de gerar o app final e autorizar essa cópia uma vez em Ajustes → Privacidade e Segurança → Acessibilidade.

### P3 — UX/entrega ainda não fechadas

O editor de grade não existe; os campos do app permitem escolher Windows/Linux apesar de o agente não controlar esses sistemas nativamente; o DMG não leva a pasta de documentação; iOS exige um projeto externo; não há CI/E2E versionados. A descoberta mDNS está implementada, mas requer validação de rede real.

**Próximo passo:** alinhar escolhas de plataforma com suporte real, criar editor/layouts e automatizar builds/testes.

## Validação conhecida

Validação executada durante esta revisão:

```bash
./gradlew :core:domain:jvmTest :core:protocol:jvmTest :core:data:jvmTest :agent:test
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64
./gradlew :agent:packageDmg
```

As suítes JVM de domínio, protocolo, dados e agente e a suíte Android completa passaram. Os testes de `DeskViewModel` injetam o `backgroundScope` do runner e usam `runCurrent()` nos pontos de sincronização, evitando avanço infinito do scheduler mantendo cobertura do comportamento com polling. O APK debug e o target Kotlin iOS compilaram. O DMG `1.1.1` foi gerado e instalado neste Mac; `/health` respondeu `1.1.1` com Acessibilidade ativa. Os ícones ChatGPT, Finder e Chrome foram verificados como PNGs válidos de 72×72, e a imagem ChatGPT conferida visualmente. Não foi possível validar instalação em telefone físico porque nenhum dispositivo estava conectado. A assinatura continua ad hoc, pois não há certificado confiável configurado.

## Próximos passos sugeridos

1. Criar editor de layouts e botões, próxima feature de produto recomendada.
2. Testar conexão, queda e reconexão do WebSocket de ponta a ponta.
3. Validar descoberta e pareamento mDNS em LAN real; preparar projeto Xcode e validar iOS em dispositivo.
4. Configurar CI e tornar visível no produto o suporte apenas parcial a Windows/Linux.
5. Completar segurança com revogação individual, TLS e armazenamento protegido.
6. Limitar a fila e os tempos de espera do logger remoto.

A correlação WebSocket propaga rejeições explícitas e diferencia uma desconexão/timeout após o início do envio (resultado não confirmado) de uma desconexão anterior ao envio (fallback HTTP seguro). Os testes verificam os tipos de erro, limpeza de pendências e descarte de respostas tardias; ações incertas não são repetidas automaticamente.

Para acompanhamento priorizado, consulte o [roadmap](roadmap.md); para comandos e detalhes do transporte, consulte [architecture.md](architecture.md).
