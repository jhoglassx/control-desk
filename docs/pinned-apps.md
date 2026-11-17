# Apps fixos e projetos

Estado em 15/10/2026.

## Uso

- Cada aplicativo tem um launcher persistente no grid. Abrir/fechar janelas mantém o card, seu tamanho e sua ordem.
- Toque no aplicativo para abrir ou focar. Com várias instâncias, escolha a janela na lista; ela exibe título e monitor. O card indica a quantidade de janelas.
- Segure o card para fechar. Com várias instâncias, escolha qual janela fechar. O launcher permanece disponível após fechar a última janela.
- Toque em **↕ Reorganizar apps** e arraste qualquer parte do card. Toque no tamanho para alternar **1×1**, **2×1**, **2×2**, **1×2**. Conclua em **✓**.
- O primeiro ajuste salva a organização dos cards atuais. Novos aplicativos entram ao final e as atualizações do agente não mudam a ordem escolhida.
- Toque em **+** para adicionar um favorito. Use **Escolher projeto ou arquivo** para navegar pelo Mac e configurar o destino.
- Favoritos de projeto têm identidade e tamanho próprios. O caminho configurado permanece mesmo quando outras janelas do mesmo app são abertas. Uma janela pertence a um único atalho: o projeto tem prioridade, e o launcher padrão recebe somente as janelas restantes. Sem janela restante, ele fica inativo e continua disponível para abrir o aplicativo.

## Implementação

`DockShortcutProjector` projeta janelas temporárias sobre `PairedHost.applicationShortcuts`. O catálogo, a ordem e os tamanhos ficam no arquivo local de hosts com backup. O tamanho é salvo pelo ID persistente do launcher ou pelo ID do favorito, nunca pelo título da janela.

O ViewModel mantém a seleção de instância em estado MVI. Os comandos HTTP/WebSocket compartilham o mapeamento, enviando o ID da janela escolhida. Uma janela que desapareceu não é substituída automaticamente por outra na execução do comando.

Preferências antigas são migradas na descoberta do app. Quando havia vários cards de janela para o mesmo aplicativo, eles passam a um launcher com uma lista de instâncias. A migração conserva as chaves antigas e prioriza uma configuração com tamanho personalizado. Consulte [a revisão técnica](shortcut-identity-review.md) para os critérios e limites.

O catálogo de apps usa `GET /apps/installed` autenticado; o navegador de arquivos usa `POST /files/browse` e limita a navegação à pasta do usuário. O lançamento usa `open -a <bundle> <projeto/documento/pasta>`. O agente valida a existência dos caminhos antes de lançar.

## Limites

- O catálogo e controle nativos de janelas estão implementados no agente macOS; Windows/Linux ainda precisam de integração nativa.
- O navegador mostra até 200 entradas por pasta.
- Os atalhos são locais por celular e computador pareado; não há sincronização entre celulares.
- Um bundle movido para outro caminho pode gerar outro launcher.
- A identificação do projeto aberto ainda depende do título fornecido pelo aplicativo.

## Diagnóstico

No Android, filtre o Logcat por `[app-layout]`: os eventos indicam ID persistente, chave do tamanho, título e ID da janela associada.

No Mac, `[window-identity]` em `~/.control-desk/agent.log` registra alterações na associação Accessibility/Quartz. Logs incluem títulos de janelas e devem ser revisados antes do compartilhamento.

## Controles e dispositivos

A mesma tela **+** oferece controles de microfone/saída de áudio e ações de câmera/dispositivos por aplicativo ou URL. Os controles participam da mesma grade, sem posições reservadas. Veja [device-shortcuts.md](device-shortcuts.md) para uso, capacidades disponíveis e evolução do controle direto pelo agente.
