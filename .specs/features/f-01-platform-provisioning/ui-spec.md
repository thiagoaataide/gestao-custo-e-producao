# F-01 — Especificação de interface para Membros

**Status:** especificação vinculante; T24 implementada localmente, com gate Maven e inspeção visual publicada pendentes.
**Escopo:** V0, Platform Administration.
**Fonte vinculante:** decisão do usuário nesta conversa, F-01 e PRD V0.
**Tarefa:** T24 em `tasks.md`.

## Objetivo

Administradores da plataforma precisam entender quem já tem acesso a cada
tenant e quem ainda precisa aceitar um convite. Hoje esses dados estão em abas
separadas; a aba Membros mostra IDs de identidade e o botão para adicionar
alguém leva para Convites. Isso esconde o ciclo de vida que o administrador
quer acompanhar.

A administração terá uma única aba **Membros**, com filtro de tenant, lista de
acessos ativos e convites ainda sem ativação, e formulário de convite aberto na
mesma página. O PRD de produto continua sendo a fonte única para regras de
negócio; esta especificação detalha somente a interação administrativa.

## Decisões de interface

- Em `/platform`, manter as abas **Tenants**, **Membros** e **Papéis da
  plataforma**. Remover a aba separada **Convites**. Auditoria permanece na
  rota `/platform/audit`.
- A aba **Membros** é uma visão de todos os tenants administráveis, não a área
  operacional de um tenant.
- O filtro de tenant começa em **Todos os tenants**. Selecionar um tenant
  restringe a lista sem navegar para outra tela. O filtro de situação começa em
  **Todas as situações** e oferece **Ativos**, **Convites pendentes**,
  **Convites expirados**, **Convites revogados** e **Acessos revogados**.
- A ação **Adicionar membro** abre um formulário na própria página. O
  formulário exige um tenant disponível e o e-mail do destinatário; o botão
  final diz **Criar convite** para deixar claro que o acesso ainda depende da
  aceitação.
- A explicação do formulário diz que o convite fica pendente até a pessoa
  entrar com a conta cujo e-mail verificado corresponde ao destinatário e
  confirmar o aceite.
- Depois da criação, a página continua em Membros, mostra a linha pendente e
  oferece o link copiável em uma confirmação na página. O fluxo não muda para
  outra aba nem cria membership ativa.
- Uma membership ativa ocupa uma linha. Convites pendentes, expirados e
  revogados aparecem como linhas de convite pendente ou histórico, conforme o
  estado. Um convite aceito que corresponda a uma membership é representado
  pela linha da membership, sem duplicar a pessoa.
- A lista identifica a pessoa pelo e-mail do convite associado à identidade;
  quando não houver e-mail associado, mantém um identificador de identidade
  como alternativa visível. Tenant, papel e situação são mostrados em texto.
- Convites pendentes mantêm as ações existentes de reenviar e revogar; convites
  expirados mantêm reenvio; memberships ativas mantêm revogação. A revogação
  pede confirmação que informa o e-mail e o tenant afetados.
- Convite por e-mail continua opcional. A página sempre permite copiar o link
  quando o caso de uso retorna um convite criado/reenviado.
- Em tela estreita, cada registro vira uma apresentação empilhada sem exigir
  rolagem horizontal para alcançar as ações.

## Esboço de hierarquia

```text
Administração da plataforma
  Tenants | Membros | Papéis da plataforma

Membros                                      [Adicionar membro]
Veja acessos ativos e convites aguardando confirmação.

Tenant [Todos os tenants v]   Situação [Todas as situações v]

E-mail / identidade | Tenant | Situação | Papel | Data / expiração | Ações
...                 | ...    | Ativo     | TENANT_USER | ...         | Revogar
...                 | ...    | Convite pendente | TENANT_USER | Expira ... | Copiar / Reenviar / Revogar

Adicionar membro  (painel aberto na mesma página)
Tenant [selecione...]  E-mail [nome@dominio...]
[Cancelar] [Criar convite]
```

No celular, cada linha apresenta e-mail/identidade, tenant e situação primeiro;
papel, prazo e ações vêm abaixo, com botões acessíveis sem rolagem lateral.

## Estados da tela

| Estado | Apresentação |
| --- | --- |
| Carregando | Indicador e texto “Carregando membros…” no conteúdo da lista; filtros e ações ficam indisponíveis até os dados chegarem. |
| Sem membros nem convites | Explica que ainda não há acessos e oferece **Adicionar membro**. |
| Filtro sem resultados | Informa que não há registros para o tenant/situação selecionados e permite limpar os filtros. |
| Lista carregada | Registros ordenados por tenant e e-mail/identidade; situação escrita por extenso, sem depender só de cor. |
| Formulário aberto | Campos Tenant e E-mail obrigatórios, validação próxima ao campo, ações **Cancelar** e **Criar convite**. |
| Convite criado | Mantém Membros aberto, atualiza a linha para **Convite pendente** e apresenta o link com ação **Copiar link** e confirmação acessível. |
| Falha ao criar ou atualizar | Mensagem próxima ao formulário ou à lista com próximo passo; os valores do formulário permanecem para correção/tentativa. |
| Revogar acesso/convite | Confirmação antes da mutação; cancelar preserva a situação, confirmar atualiza a linha após sucesso. |
| Sem autorização | Mantém a mensagem existente de acesso não autorizado; não carrega registros nem mostra ações administrativas. |

## Regras e fronteiras preservadas

- Convite e membership continuam entidades distintas. “Convite pendente” não é
  membership nem concede acesso operacional.
- Só a aceitação autenticada e confirmada, com e-mail verificado correspondente,
  ativa a membership `TENANT_USER`.
- A V0 continua sem papéis operacionais granulares: “admin do tenant” não é
  criado por esta tela.
- Identidades com papel de plataforma não recebem acesso operacional por causa
  desta interface; membership continua sendo a autorização operacional.
- A tela administrativa não opera dados de negócio de tenants.

## Fora de escopo desta mudança de interface

- Alterar cadastro/login do Supabase ou criar automaticamente conta de
  autenticação para o destinatário dentro da T24. O cadastro iniciado pelo
  convite está especificado em `invitation-onboarding-spec.md` e será planejado
  separadamente em T25.
- Alterar como a rota pública recupera uma sessão autenticada com e-mail
  diferente do destinatário. A troca/recuperação de identidade no aceite do
  convite permanece uma decisão separada.
- Tornar obrigatório o envio de e-mail ou mudar o provedor de entrega.
- Criar `TENANT_ADMIN`, permissões granulares ou acesso de plataforma a dados
  operacionais.
- Criar um PRD separado para o front-end.

## Referências de implementação revisadas

- `PlatformAdministrationView` atualmente mantém abas separadas, redireciona
  “Convidar membro” para Convites e apresenta o UUID da identidade na lista.
- `InvitationAdministrationView` já disponibiliza e-mail, tenant, estado,
  expiração e identidade associada; `MembershipAdministrationView` disponibiliza
  o vínculo ativo. A composição pode ser feita para apresentação sem tornar
  convite e membership o mesmo registro de domínio.
- A revogação atual é imediata; esta proposta acrescenta confirmação visual
  antes de revogar convite ou membership.
