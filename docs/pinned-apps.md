# Apps fixos e projetos

Estado em 14/10/2026. Todos os cards de apps exibidos na tela principal podem ser reorganizados juntos por arraste. A tela dedicada de configuração mostra o ícone real do app quando o macOS consegue extraí-lo do bundle; o catálogo resolve e armazena o ícone na primeira consulta, inclusive para apps ainda não abertos.

## Como usar

1. Na tela principal, toque em **↕ Reorganizar apps** no topo.
2. Segure qualquer parte do card e arraste para a posição desejada. O card selecionado acompanha o dedo e fica à frente; os outros cards abrem espaço conforme ele cruza as posições. Apps abertos, cards favoritos e cards da Dock participam da mesma ordenação.
3. Toque em **✓ Concluir reorganização** para voltar ao uso normal dos cards.
4. A ordem é salva automaticamente por Mac. Atualizações da lista preservam posições existentes; apps que aparecerem depois são acrescentados ao final até que o usuário os reorganize.
5. Para adicionar um atalho de inicialização ou associar um projeto/caminho, toque no botão **+** do topo. Na seção **Fixados**, escolha **Escolher projeto ou arquivo** e navegue pelas pastas do Mac.
6. Toque em um card de app fixo para abrir o app e o projeto/documento/pasta configurado.

Os cards fixos e dinâmicos compartilham a mesma ordem persistida. Quando um app fixo também está aberto, o card favorito representa uma de suas janelas e preserva título, monitor e estado ativo; janelas adicionais do mesmo app continuam como cards próprios. Um atalho sem caminho configurado apenas abre o aplicativo.

## Como funciona

- A configuração é persistida no arquivo de hosts do celular como `pinnedApps`, dentro do Mac pareado. Aplicativos, caminhos e ordem são configurações locais do celular e não são enviados para nuvem.
- O botão de reorganização, a seleção, o deslocamento e a prévia de posição do card seguem o estado MVI da tela; a nova ordem é persistida ao soltar. O campo `appOrder` preserva IDs ausentes e reconcilia a identidade quando um app passa de item da Dock para janela aberta, evitando que essa mudança de estado o envie para o fim da grade.
- O agente oferece `GET /apps/installed` autenticado para listar `.app` em `/Applications`, `/System/Applications` e `~/Applications`, incluindo o ícone real.
- O agente oferece `POST /files/browse` autenticado para navegar dentro da pasta do usuário no Mac; entradas ocultas são filtradas e caminhos fora dela são recusados.
- O atalho usa a rota de lançamento existente com `bundlePath` e `projectPath`. No macOS, o agente executa `open -a <app> <caminho>` para pedir ao Launch Services que abra o projeto ou documento no app correspondente.
- O caminho precisa ser válido e acessível ao usuário do macOS que executa o agente. O Android Studio trata a pasta do projeto como alvo de abertura e pode trazer uma instância/janela própria conforme as preferências do IDE.
- O agente valida que o bundle é uma pasta `.app` existente e que o caminho configurado existe antes de lançar.

## Limites da primeira versão

- A lista de apps é fornecida pelo agente macOS; agentes Windows/Linux ainda não têm catálogo nativo.
- O navegador exibe até 200 itens por pasta e limita a navegação à pasta do usuário do macOS.
- Atalhos são independentes por instalação do app e por computador pareado; ainda não há sincronização entre celulares.
- Caminhos removidos ou apps desinstalados falham com uma mensagem e podem ser atualizados no editor.

## Próximos refinamentos

1. Exibir atalhos favoritos em uma seção visual própria, separada da lista de janelas ativas.
2. Renomear atalhos e permitir mais de um atalho do mesmo app com projetos diferentes (ex.: Android Studio — sample-android-app e Android Studio — outro projeto).
3. Adicionar confirmação visual do resultado do lançamento.
