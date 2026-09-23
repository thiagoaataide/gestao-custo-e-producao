# F-01 — Contexto de provisionamento da plataforma

**Gathered:** 21 de setembro de 2026
**Spec:** `.specs/features/f-01-platform-provisioning/spec.md`
**Status:** Decisões de domínio confirmadas; T18 implementada e validada localmente. O fechamento após a validação publicada continua pendente para T19–T23, revisão independente e UAT.

## Limite da feature

A F-01 entrega o contexto de Platform Administration dentro do monólito
modular. Ele permite iniciar a plataforma, criar e administrar tenants,
convidar usuários, ativar ou revogar memberships, controlar o ciclo de vida do
tenant e consultar auditoria administrativa. Não permite consultar ou operar
dados de negócio dos tenants.

## Decisões confirmadas

### Bootstrap e identidades

- O primeiro `PLATFORM_OWNER` será o usuário responsável pelo produto,
  provisionado uma única vez por bootstrap controlado.
- Haverá duas identidades Supabase separadas para esse responsável: uma conta
  administrativa e outra conta `TENANT_USER` para operar um tenant.
- Existe somente um `PLATFORM_OWNER` na V0; ownership transferível não faz
  parte desta feature.

### Papéis e limites de acesso

- `PLATFORM_OWNER` pode criar e remover `PLATFORM_ADMIN`, criar tenants,
  convidar usuários e administrar memberships.
- `PLATFORM_ADMIN` pode administrar tenants, convites e memberships, mas não
  pode criar ou remover administradores da plataforma.
- `TENANT_USER` é o único papel operacional da V0.
- Papéis de plataforma são representados separadamente de memberships de
  tenant; não existe tenant artificial para administrador de plataforma.

### Tenant e membership

- Tenant pode ser criado sem usuário associado.
- Não existe membership sem tenant válido.
- Um usuário pode ter no máximo uma membership ativa.
- Se o usuário já estiver vinculado a outro tenant, a aceitação do segundo
  convite será bloqueada até a revogação do vínculo atual.
- `ACTIVE` permite operação, `SUSPENDED` bloqueia temporariamente e pode voltar
  a `ACTIVE`; `CLOSED` bloqueia definitivamente na V0 e preserva os dados.
- Somente o owner executa suspensão, reativação e encerramento de tenants.

### Convites

- Link copiado, e-mail opcional e associação manual de identidade existente
  são canais do mesmo fluxo de convite.
- Convites expiram em 24 horas e são de uso único.
- Há no máximo um convite pendente por tenant e e-mail; reenvio invalida o
  convite anterior.
- A aceitação exige autenticação, e-mail verificado e correspondência com o
  destinatário do convite.
- Associação manual não ativa a membership silenciosamente; o usuário ainda
  precisa autenticar e confirmar.
- Falha no envio automático de e-mail não desfaz o convite e não impede o uso
  do link copiado.

### Auditoria

- Criação, suspensão, reativação e encerramento de tenant são auditados.
- Criação, aceitação, revogação e expiração de convite são auditados.
- Criação, ativação e revogação de membership e alteração de papéis de
  plataforma são auditadas.
- A consulta da auditoria fica disponível para administradores da plataforma e
  contém somente metadados administrativos.

## Margens de decisão do design

- O design pode definir a forma concreta do bootstrap, desde que seja
  controlada, idempotente, auditável e não crie segundo owner.
- O design pode escolher a representação interna dos estados de convite e dos
  eventos de auditoria, desde que preserve as transições e informações
  previstas no spec.
- O design pode definir a apresentação visual do fluxo, desde que não exponha
  dados operacionais nem permita ações fora do papel do usuário.

## Áreas não discutidas convertidas em pressupostos

- A V0 não terá transferência de ownership.
- A V0 não terá recuperação ou reabertura de tenant `CLOSED`.
- O envio automático de e-mail não será requisito para concluir o fluxo; o
  link copiado será sempre o caminho operacional disponível.
- Não serão definidos limites comerciais, cobrança, planos ou quotas nesta
  feature.

## Ideias adiadas

- Transferência de ownership.
- Múltiplos owners ou hierarquia avançada de administradores.
- Papéis e permissões granulares dentro do tenant.
- SSO, domínio corporativo e provisionamento em lote.
- Cobrança, planos e onboarding comercial.

## Lacunas de fechamento encontradas na validação publicada

Estas são lacunas de execução/apresentação, não novas decisões de domínio nem
expansão da V0:

- O caso de uso de aceitação do convite existe, mas a URL pública
  `/invitations/{token}` ainda não possui uma rota Vaadin que conclua o fluxo.
- Quando a pessoa precisa autenticar, a aplicação deve preservar o destino do
  convite e retornar ao mesmo link após o login.
- A origem pública do link deve ser configurada por ambiente; Render não pode
  emitir URLs locais. O perfil local pode continuar usando endereço local.
- O desenho prevê uma porta opcional de entrega, mas não há adapter de produção
  SendGrid. A entrega automática continuará opcional e o link copiável será o
  caminho de contingência.
- A tela administrativa precisa de ajustes de alinhamento e responsividade,
  mantendo a mesma hierarquia de acesso e os mesmos dados apresentados.
