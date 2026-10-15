# Revisão geral do Control Desk

Estado revisto em 15/10/2026. Este documento descreve o checkout atual; a evidência detalhada dos atalhos está em [shortcut-identity-review.md](shortcut-identity-review.md).

## Estado atual

| Área | Implementado | Limite |
|---|---|---|
| Android | Pareamento, múltiplos hosts, mixer, configurações, descoberta, favoritos e grade compacta redimensionável/reordenável | Validação visual dos últimos ajustes ainda pendente |
| Agente macOS | HTTP/WebSocket, Dock, janelas, Chrome, áudio, instalação LaunchAgent e DMG | Accessibility e permissões de automação exigidas pelo macOS |
| Atalhos | Catálogo persistente por host, migração de identidades antigas e reparo de duplicatas nome/bundle | Mover o bundle pode criar outro launcher |
| Instâncias | Seleção por título/monitor, comandos com ID/PID e associação exclusiva a um atalho | Títulos de projeto dependem do aplicativo; snapshots podem mudar durante a ação |
| iOS | Código compartilhado, targets e controller | Projeto Xcode completo e validação em dispositivo pendentes |
| Windows/Linux | Estrutura JVM e seleção de SO | Controle nativo ainda simulado |

## Correções revisadas

A organização pertence ao launcher persistente, não à janela. Abrir, fechar, focar ou alterar título/PID não deve substituir o card nem apagar seu tamanho. Novos aplicativos são acrescentados ao catálogo. Preferências antigas são migradas, preservando chaves de tamanho para recuperação.

O catálogo consolida registros sem bundle somente quando os aliases indicam um único bundle conhecido. Bundles explícitos diferentes e aliases ambíguos permanecem separados. A validação dos dados do telefone encontrou 22 registros padrão antes do reparo e 14 depois, mantendo o host e os dois favoritos.

Cada janela tem um único proprietário: favorito de projeto, favorito genérico ou launcher padrão, nessa prioridade. Um projeto pode coexistir com o launcher padrão; se a única janela pertence ao projeto, o padrão permanece disponível e inativo. Dois destinos de abertura não significam duas instâncias abertas.

Processos sem janela não geram mais a janela fictícia de índice 1. Com identidade de janela, foco e fechamento revalidam ID/PID; o fechamento usa `AXCloseButton`. A ausência da janela não autoriza selecionar outra. Solicitações legadas sem identidade ainda têm fallback de processo. Um resultado incerto após envio pelo WebSocket não é repetido automaticamente por HTTP.

O empacotamento usa bytecode/runtime Java 17 compatíveis. O agente fonte e DMG atuais são **1.1.12**, com **windowListerVersion 25**. Na última inspeção, o agente instalado era **1.1.11**: gerar o pacote não atualiza automaticamente a instalação.

## Validação registrada

As suítes de agente, domínio, dados e app, builds APK/test APK e geração de DMG passaram na execução registrada em 15/10. Após a associação exclusiva, dados/app e ambos os APKs foram validados novamente. O teste que reproduzia dupla ativação falhou antes da correção e passou depois. Veja comandos e limites na [revisão técnica](shortcut-identity-review.md).

O APK foi atualizado no telefone com `adb install -r`. A releitura confirmou preservação de hosts, tokens, favoritos, tamanhos e ordem. A tela estava em Dozing: isso valida persistência, não a aparência ou os gestos. Testes instrumentados foram compilados, mas não executados. Nenhum aplicativo real do Mac foi fechado para validar os comandos.

## Pendências prioritárias

1. Executar testes instrumentados e o ciclo real abrir → redimensionar → fechar → reabrir em vários apps e múltiplas janelas, com agente atualizado.
2. Testar conexão, queda, reconexão e troca de host do WebSocket de ponta a ponta; automatizar em CI.
3. Configurar assinatura persistente e notarização para distribuição macOS. Builds ad hoc podem exigir nova autorização de Acessibilidade.
4. Validar descoberta/pareamento em outras LANs e concluir integração iOS.
5. Completar revogação individual, TLS e armazenamento protegido. Atualmente os tokens são persistidos sem criptografia e o controle usa HTTP/WS na LAN; o PIN administrativo local não substitui autenticação de rede.
6. Limitar fila e tempos de espera do logger remoto e explicitar o suporte parcial a Windows/Linux na interface.

O editor de reordenação/redimensionamento já existe. Próximas funcionalidades de produto estão no [roadmap](roadmap.md); o inventário completo está em [feature-status.md](feature-status.md).
