# Revisão do fluxo de atalhos — 15/10/2026

## Resultado

A persistência já existia. A causa estrutural era usar IDs temporários de janela como identidade dos cartões e tentar transferir tamanho/ordem entre título, índice AX, PID e Quartz ID. Os logs recebidos mostravam o tamanho salvo em `card:app-Visual Studio Code-dock`, mas a reconciliação partia de `app-Code-dock` com `oldSize=null`.

O fluxo foi alterado para um launcher persistente por aplicativo. As janelas são instâncias associadas e selecionáveis. O ID da janela controla a ação; o ID do launcher controla tamanho e ordem. Não há condições específicas para um aplicativo.

## Fluxo implementado

1. `MacWindowLister` captura Accessibility e Quartz. Títulos únicos ou geometria exata associam janelas do mesmo PID; associações ambíguas ficam sem ID Quartz.
2. `MacDockApps` associa janelas e Dock pelo bundle disponível nos dados, com resolvedor como fallback. Bundles explícitos diferentes não são confundidos por nomes iguais.
3. HTTP/WebSocket transportam janelas e seus metadados. `RunningAppsGridBuilder` cria os dados temporários de execução.
4. `DockShortcutProjector` associa esses dados ao catálogo persistido `PairedHost.applicationShortcuts`. Um aplicativo gera um card; várias janelas não acrescentam cards ao grid. O catálogo conserva apps fechados.
5. O ViewModel aplica `appOrder`, `appGridPositions` e tamanhos por ID de atalho. A reconciliação contínua de identidade por PID/título/índice foi removida.
6. Favoritos de projeto preservam `PinnedAppShortcut.id`, nome e caminho configurado. Seus tamanhos usam `pinned:<id>`; launchers padrão usam `card:<id persistente>`.
7. O estado MVI `WindowSelection` oferece janelas com título e monitor ao focar/fechar um launcher com várias instâncias. A escolha é validada novamente contra os dados atuais antes do comando.
8. `AppCommandMapping` mantém a mesma conversão HTTP/WebSocket, incluindo `stableWindowId`. O agente reconsulta a janela por ID/PID; se não a encontra, devolve erro em vez de substituir por outra.
9. Solicitações identificadas de janela usam o botão `AXCloseButton`. A contagem antiga de uma única janela não autoriza terminar o processo. Títulos duplicados sem ID resolvido são recusados.
10. O gesto do card usa callbacks atualizados, mesmo quando o ID persistente continua igual e a janela associada muda.

## Persistência e migração

- A chave inicial do aplicativo usa o caminho completo do bundle normalizado; sem bundle, usa o alvo informado pelo agente. Duas cópias com o mesmo nome em caminhos diferentes têm launchers diferentes.
- O registro `ApplicationShortcut` é persistido junto do host. Abertura, fechamento, troca de foco, título, monitor, PID ou ID Quartz não o substituem.
- Na primeira descoberta após a atualização, o projetor migra as preferências antigas para o novo ID. Se houver várias entradas antigas do mesmo app, prioriza uma entrada com tamanho explicitamente personalizado, ou a primeira entrada ordenada. Um único launcher não pode representar simultaneamente tamanhos diferentes de antigas janelas separadas. As chaves antigas são mantidas para recuperação.
- A migração ocorre uma vez por aplicativo; preferências antigas não sobrescrevem edições posteriores.
- O re-pareamento do mesmo host atualiza nome/SO/token com `copy`, conservando todos os campos de layout e catálogo.
- Novos aplicativos entram ao final. Um favorito genérico substitui visualmente o launcher padrão do mesmo app; favoritos de projetos continuam como entradas próprias.
- A grade compacta continua sem coordenadas vazias reservadas. Como abrir/fechar janelas não altera quantidade, tamanho ou ordem dos launchers, essas transições não reorganizam a grade.

## Verificação automatizada

- Matriz de aplicativos com nomes de processo diferentes, IDs Quartz presentes/ausentes, mudança de título, duas janelas, fechamento total, reabertura com novo PID e serialização/releitura das preferências.
- Janelas com títulos iguais e processos diferentes continuam selecionáveis individualmente.
- Fechar uma instância preserva launcher, tamanho e ordem; janela removida durante a seleção não é substituída por outra.
- Migração é executada uma vez e não reaplica tamanho legado após uma edição.
- Bundles de mesmo nome em caminhos diferentes permanecem separados.
- Mapeamento e serialização dos comandos preservam ID/PID, inclusive no fechamento.
- Resolução no agente recusa ID ausente ou PID diferente.
- Teste instrumentado verifica que toque/toque longo usam os callbacks da janela atual com ID de card constante.

## Limites da validação

A execução nativa ainda depende de Accessibility/System Events e de dois snapshots do macOS. O ID Quartz é válido para a janela viva na sessão atual; não identifica projetos através de reabertura. A identidade persistente do launcher é independente disso. Entre resolver a janela e executar uma ação, o sistema ainda pode alterar suas janelas; o fechamento verifica o título quando disponível.

O APK foi atualizado por substituição no telefone e sua persistência foi conferida. A tela estava em Dozing, sem validação visual. Os testes instrumentados foram compilados, mas não executados. Nenhum aplicativo real do usuário foi fechado nesta revisão.

Mover um bundle para outro caminho pode criar um novo launcher, pois o protocolo ainda não oferece `bundleIdentifier`/identidade de instalação. A associação de favoritos a projetos ainda depende do título informado pela aplicação.

## Resultado da execução de 15/10/2026

Comando concluído com `BUILD SUCCESSFUL`:

```sh
./gradlew :agent:test :core:domain:allTests :core:data:allTests :composeApp:allTests \
  :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest :agent:packageInstallerDmg
```

As suítes comuns também executaram no simulador iOS ARM64; o target iOS x64 foi ignorado pelo ambiente. Não se considera compilação do APK de testes equivalente à execução instrumentada.

Pacotes atuais: `composeApp/build/outputs/apk/debug/composeApp-debug.apk` e `agent/build/compose/binaries/main/dmg/ControlDeskAgent-1.1.12.dmg`. `hdiutil verify` validou o checksum do DMG; `codesign --verify --deep --strict` validou o app empacotado. O APK passou na verificação de integridade ZIP. A instalação nativa do agente 1.1.12 ainda precisa ser confirmada; a última observada era 1.1.11.

O agente usa assinatura ad hoc quando não há certificado persistente configurado no ambiente de build. O macOS pode exigir reconfirmação da permissão de Acessibilidade ao substituir essa cópia do agente; isso é independente da persistência dos hosts/layouts no celular.

## Correção após os logs de duplicação / índice inválido (15/10)

A leitura do catálogo no telefone confirmou pares de registros: um identificado apenas pelo nome, outro pelo bundle do mesmo aplicativo. O catálogo tinha 22 entradas padrão, além de dois favoritos. A primeira implementação não promovia a identidade sem bundle nem reparava entradas já duplicadas.

`ApplicationCatalogResolver` agora registra aliases observados junto de um bundle. Um registro sem caminho é promovido somente quando o alias identifica um único bundle conhecido. Registros equivalentes já persistidos são consolidados, conservando o ID na ordem do usuário, transferindo tamanho/posição quando faltam no sobrevivente e preservando chaves antigas de tamanho para recuperação. Dois bundles explícitos diferentes ou nomes ambíguos não são fundidos.

Também foi encontrada uma inconsistência na captura nativa: quando não existiam janelas, o script emitia `hasWindow=false`, mas inventava `axWindowIndex=1` e `windowCount=1`. A captura e o mapeamento agora emitem contagem zero, índices nulos, sem estado visível/ativo de janela. O projetor não oferece processos sem janelas como instâncias selecionáveis. O foco sem janela usa a abertura do aplicativo; se a janela some durante o comando e o processo fica sem janelas, o fluxo de launcher permite reabrir pelo aplicativo.

Três testes direcionados falharam antes das correções e passaram depois: promoção de nome para bundle sem duplicação, reparo de catálogo já duplicado e exclusão de processos sem janelas da seleção. A cobertura inclui voltar a receber o processo sem bundle após já conhecer o caminho, títulos/nomes genéricos e bundles homônimos ambíguos. Um teste do mapeamento nativo impede que um snapshot `hasWindow=false` gere a janela fictícia 1, mesmo que campos antigos estejam presentes no JSON.

O APK foi atualizado no telefone conectado com `adb install -r`, após backup local de acesso restrito das preferências. A releitura do catálogo confirmou 14 entradas padrão, todas com bundle e sem chaves de aplicativo repetidas, mantendo um host pareado e dois favoritos. Essa é validação dos dados reais; a tela estava em modo Dozing, portanto não foi considerada validação visual. O agente instalado no Mac durante a inspeção era 1.1.11. A correção da captura sem janelas está no agente 1.1.12, `windowListerVersion=25`, fornecido em novo DMG.

## Associação exclusiva de janelas a atalhos (15/10)

O card padrão e um favorito de projeto podiam refletir a mesma janela, fazendo ambos aparecerem ativos e repetindo a janela nas seleções. A projeção do launcher e a associação dos favoritos eram calculadas separadamente.

`ShortcutWindowBinder` atribui cada janela uma única vez: primeiro aos favoritos de projeto, depois aos favoritos genéricos e por fim aos launchers padrão. A lista usada para escolher uma instância segue essa mesma atribuição. Se a única janela de um app pertence a um favorito de projeto, o launcher padrão permanece disponível, inativo e sem dados dessa janela; sua posição e tamanho continuam salvos. Outra janela não associada ao favorito pode ocupar o launcher padrão.

O teste de regressão reproduziu a dupla ativação antes da correção. A cobertura executa a sequência em vários aplicativos, verifica prioridade dos projetos, janelas de mesmo aplicativo atribuídas a destinos distintos, seleção/ações sem reutilizar janela já atribuída, apenas um destaque ativo e preservação de ordem e tamanhos.

Validação da associação exclusiva: `:core:data:allTests`, `:composeApp:allTests`, `:composeApp:assembleDebug` e `:composeApp:assembleDebugAndroidTest` concluíram com sucesso. O APK foi atualizado no celular por substituição (`adb install -r`). A releitura das preferências confirmou que hosts, tokens, favoritos, tamanhos e ordem permaneceram iguais ao backup anterior à atualização.
