# Control Desk Agent — início rápido (DMG)

Guia curto para quem instalou pelo `.dmg`. Documentação completa: **[README.md](README.md)** nesta pasta ou `docs/` no repositório.

---

## Instalação (padrão — background)

1. Duplo clique em **Instalar em segundo plano.command**
   - Instala ou **atualiza** em `/Applications` (instância única)
   - Um único serviço: `com.controldesk.agent`
   - Nova versão substitui a anterior automaticamente

## Ver PIN e IP (opcional)

2. Duplo clique em **Abrir configurações.command**
   - Janela com PIN, IP e status
   - Fechar a janela **ou Cmd+Q** encerra a UI; o serviço continua em segundo plano (sem Dock)

**ControlDeskAgent.app:** instala/atualiza em background e encerra (sem janela). Use **Abrir configurações** para ver o PIN.

## Depois de instalar

1. **Ajustes → Privacidade e Segurança → Acessibilidade** → marque **ControlDeskAgent**
2. No celular: **Parear PC** → IP deste Mac, porta **8741**, PIN

| Contexto | IP no app |
|----------|-----------|
| Emulador Android no Mac | `10.0.2.2` |
| Celular na Wi‑Fi | IP LAN do Mac (na janela de configurações) |

## Remover

Duplo clique em **Remover serviço.command**

## Problemas

- Log de instalação: `~/.control-desk/install.log`
- PIN: `grep pin= ~/.control-desk/agent.log`
- Guia completo: [agent-install.md](agent-install.md)
