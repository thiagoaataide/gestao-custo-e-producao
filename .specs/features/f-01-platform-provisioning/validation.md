# F-01 — Validação e verificação independente

**Data:** 22 de setembro de 2026
**Escopo:** T16 — validação ponta a ponta da F-01 Platform Provisioning
**Resultado:** PASS

> Este resultado PASS é histórico e permanece válido para o gate T16 e os
> requisitos F01-01 a F01-16 então definidos. Ele não significa que o
> fechamento atual da F-01 esteja completo.

## Estado de fechamento após validação publicada — 23 de setembro de 2026

**Estado atual:** ABERTO — T17 e T18 passaram pelos gates automatizados; revisão
independente e UAT no Render não foram concluídas. T18 implementa a rota do
convite e a continuidade após login; T19 passou pelo gate automatizado. T20–T23
seguem pendentes para e-mail opcional, revisão visual, regressão publicada e
UAT.

## Gate da feature

A validação foi executada contra um PostgreSQL 17 recém-criado pelo compose
isolado `gestao-producao-gate`, com schema vazio. O gate final foi executado
por `cmd.exe /d /c mvnw.cmd verify`, com Maven Wrapper 3.9.16 e Java 21.0.12.

Evidências do gate:

- Flyway aplicou as migrations da aplicação e das fixtures de teste no banco
  vazio; as verificações de inicialização e idempotência passaram.
- `Tests run: 155, Failures: 0, Errors: 0, Skipped: 0`.
- Build de frontend Vaadin concluído.
- JAR executável Spring Boot empacotado.
- A execução limpa equivalente também foi concluída com sucesso antes do
  fechamento da tarefa.
- `git diff --check` passou.

A validação automatizada não substitui uma UAT manual pelo navegador; essa
etapa visual não foi executada neste gate.

## Cobertura dos requisitos

| Requisito | Evidência principal | Resultado |
|---|---|---|
| F01-01 | `PlatformProvisioningEndToEndIntegrationTests`, bootstrap idempotente e identidade ordinária não provisionada | PASS |
| F01-02 | `PlatformProvisioningEndToEndIntegrationTests`, separação de owner/admin de plataforma | PASS |
| F01-03 | `PlatformProvisioningEndToEndIntegrationTests`, tenant criado sem membership | PASS |
| F01-04 | cenário de auditoria administrativa e `AdministrativeAuditViewIntegrationTests` | PASS |
| F01-05 | ciclo de vida ativo, suspenso, reativado e encerrado do tenant | PASS |
| F01-06 | suspensão bloqueia operação e reativação restaura o acesso | PASS |
| F01-07 | convite pendente, token com expiração de 24 horas e aceite | PASS |
| F01-08 | aceite de convite cria membership ativo e associação ao tenant | PASS |
| F01-09 | entrega opcional/manual e fluxo de confirmação do convite | PASS |
| F01-10 | convite expirado não pode ser aceito | PASS |
| F01-11 | vínculo ativo é obrigatório para acesso ao tenant | PASS |
| F01-12 | usuário administrativo de plataforma não recebe acesso operacional ao tenant | PASS |
| F01-13 | usuário não pode possuir membership ativo em mais de um tenant | PASS |
| F01-14 | auditoria registra tentativa negada sem dados operacionais sensíveis | PASS |
| F01-15 | consulta de auditoria fica restrita à administração da plataforma | PASS |
| F01-16 | falha transacional não deixa estado parcial persistido | PASS |

## Cenários transversais executados

A suíte `PlatformProvisioningEndToEndIntegrationTests` cobre cinco fluxos
transversais em PostgreSQL real:

1. bootstrap idempotente do owner e ausência de provisionamento automático;
2. criação do tenant, ausência inicial de membership e ciclo de vida;
3. convite, aceite, expiração, membership único e propagação do tenant para
   RLS;
4. separação entre administração da plataforma e operação do tenant;
5. tentativa negada, preservação do estado e consulta segura da auditoria.

Também foram verificados rollback transacional, isolamento por tenant,
reinicialização idempotente e configuração inválida de banco na suíte
existente.

## Sensor de discriminação

Foi aplicado um sensor independente com duas mutações temporárias no código de
produção, sempre restauradas antes do commit:

1. desabilitar a concessão de acesso de plataforma em
   `AccessDecisionResolver`: **mutação eliminada**, pois a suíte produziu duas
   falhas;
2. trocar a transição `SUSPEND` por `reactivate` em
   `TenantProvisioningCommandService`: **mutação eliminada**, pois a suíte
   produziu erro de ciclo de vida inválido.

Resultado do sensor: **2/2 mutações eliminadas — PASS**.

## Verificação independente

Foi realizada uma revisão fresh-eyes, ancorada na especificação e no
traceability de F01-01 a F01-16. Todos os requisitos possuem teste ou
evidência existente correspondente, e nenhum gap residual exigiu criação de
tarefa corretiva.

**Resultado da validação T16: PASS — 16/16 requisitos então definidos foram verificados.**

## Complemento — T17: bootstrap do owner pela tela inicial

**Data:** 23 de setembro de 2026
**Escopo:** T17 / F01-17
**Resultado automatizado:** PASS
**Revisão independente e UAT no ambiente publicado:** pendentes

A tela inicial passou a exibir a ação explícita **“Ativar administração da
plataforma”** somente quando a identidade autenticada corresponde exatamente
ao `platform.bootstrap.owner-subject` configurado. O login não cria owner
automaticamente; ao clicar, o serviço revalida a identidade, persiste owner e
auditoria na transação existente e a interface navega para a administração.

Evidências da validação automatizada:

- `AccessShellViewIntegrationTests`: 7 testes, sem falhas ou erros; cobre
  bootstrap explícito, ausência da ação para identidade diferente e transição
  para acesso de plataforma após o bootstrap.
- `mvn verify`: 177 testes, sem falhas ou erros; frontend Vaadin construído e
  JAR executável empacotado.
- PostgreSQL 17 em container temporário
  `gestao-owner-bootstrap-gate-20260923` (ID `50d6453de649`), usando `--rm` e
  `tmpfs`; foi parado ao fim da validação e removido automaticamente. Nenhuma
  conexão ou alteração no Supabase ou no banco publicado.
- `git diff --check` sem erros.

Esta validação automatizada não confirma a experiência visual no Render. Após
o próximo deploy, ainda é necessário autenticar com a identidade cujo subject
está configurado, clicar na ação e verificar a abertura da administração. A
revisão independente fresh-eyes da T17 também não foi executada nesta rodada.

## Gaps restantes para T20–T23

- **Convite publicado:** a rota `/invitations/{token}`, o retorno após login e
  a origem pública HTTPS explícita passaram nos gates automatizados T18/T19.
  Ainda faltam a regressão completa publicada (T22) e a UAT real no Render
  (T23). O valor `PLATFORM_INVITATION_BASE_URL` precisa ser cadastrado
  manualmente no Render antes do próximo deploy; não alteramos a configuração
  do serviço.
- **E-mail:** existe `InvitationDeliveryPort` e o teste da entrega pós-commit,
  mas não foi encontrada implementação de produção SendGrid. E-mail continua
  opcional; link copiado permanece o fallback garantido. A integração real não
  foi testada nem configurada nesta rodada.
- **Apresentação:** a tela administrativa requer revisão de alinhamento e
  adaptação a viewports estreitos, preservando permissões e ações. A validação
  visual publicada ainda está pendente.

Esses pontos não invalidam os resultados automatizados já registrados; eles
impedem declarar o fluxo publicado da F-01 como concluído. A próxima evidência
deve ser produzida por T20–T23, com teste automatizado separado da UAT manual e
sem registrar ou copiar segredos para este arquivo.

## Complemento — T19: origem pública dos links de convite

**Data:** 23 de setembro de 2026

**Escopo:** T19 / F01-09 / F01-19

**Resultado automatizado:** PASS
**Deploy e UAT publicados:** pendentes; dependem de configuração manual pelo owner

A origem dos links não possui mais fallback para `localhost`: deve ser definida
por `PLATFORM_INVITATION_BASE_URL`. Perfis locais/testes podem usar HTTP local;
fora deles, a inicialização valida origem HTTPS absoluta e rejeita hosts locais,
endereços IP não públicos, host de rótulo único, userinfo, path, query e
fragmento. A normalização elimina barra final, e os links mantêm o caminho
`/invitations/{token}` e token opaco existentes.

Evidências:

- `InvitationLinkPropertiesTests`: 4 testes cobrindo normalização local,
  origem HTTPS do Render e origens/formatos rejeitados.
- `mvnw.cmd verify`: 186 testes, sem falhas, erros ou skips; frontend Vaadin
  construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em Compose isolado
  `gestao-producao-test-f01-t19-20260923-01`, porta 55432. O comando exato
  `docker compose -p gestao-producao-test-f01-t19-20260923-01 down --volumes --remove-orphans`
  removeu container, rede e volume temporário ao final; os recursos foram
  confirmados como removidos. Os demais volumes foram preservados.
- `git diff --check` passou. Nenhuma variável/secreto foi configurado no Render
  e nenhuma alteração foi feita no Supabase.

Antes do próximo deploy, o owner deve adicionar no serviço Render a variável
`PLATFORM_INVITATION_BASE_URL=https://gestao-custo-e-producao.onrender.com`.
Esta rodada não executou deploy nem UAT do link publicado.

## Complemento — T21: interface administrativa responsiva

**Data:** 23 de setembro de 2026

**Escopo:** T21 / F01-20

**Resultado automatizado:** PASS

**Inspeção manual em navegador/Render:** pendente para T23; este commit não faz deploy.

`/platform` agora separa Tenants, Convites, Membros e Papéis da plataforma em
abas. A auditoria continua em `/platform/audit`, com navegação de ida e volta.
Os formulários usam `FormLayout` responsivo; as ações primárias/destrutivas
usam variantes Aura; os grids deixam de reservar uma área vazia fixa e exibem
as linhas carregadas. A aba Membros explica que o vínculo é ativado no aceite
do convite e leva ao formulário de convite, sem criar membership diretamente.

Evidências:

- `PlatformAdministrationViewIntegrationTests`: 5 testes aprovados, incluindo
  as quatro abas, aba inicial, encaminhamento de “Convidar membro”, formulários
  responsivos, grids dimensionados ao conteúdo e preservação de acesso.
- `AdministrativeAuditViewIntegrationTests`: 6 testes aprovados, incluindo
  formulário responsivo e grid dimensionado ao conteúdo; autorização e
  mensagens seguras seguem cobertas.
- `mvnw.cmd verify`: 189 testes, sem falhas, erros ou skips; frontend Vaadin
  de produção construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em Compose isolado
  `gestao-producao-test-f01-t21-20260923-01`, porta 55434. O comando exato
  `docker compose -p gestao-producao-test-f01-t21-20260923-01 down --volumes --remove-orphans`
  removeu container, rede e volume temporário; ausência confirmada, demais
  volumes preservados.
- `git diff --check` passou. Nenhuma chamada ao Supabase ou ao Render foi feita.
- Dependência e APIs conferidas contra Vaadin Flow 25.2.8 do `pom.xml` e seus
  artefatos de fonte; documentação oficial: [Form Layout](https://vaadin.com/docs/latest/components/form-layout),
  [Tabs/TabSheet](https://vaadin.com/docs/latest/components/tabs),
  [Aura](https://vaadin.com/docs/latest/styling/themes/aura) e
  [Stylesheets](https://vaadin.com/docs/latest/styling/stylesheets).
- Checklist manual para a inspeção publicada na T23:
  - [ ] largura estreita (aprox. 390 px): abas utilizáveis; campos empilhados;
        botões e conteúdo sem corte ou rolagem horizontal da página;
  - [ ] largura ampla (aprox. 1366 px): campos e ações alinhados; grids sem
        grande área vazia;
  - [ ] teclado: foco visível, setas/Enter nas abas e foco no e-mail após
        “Convidar membro”;
  - [ ] atalho de auditoria abre `/platform/audit` e retorna para `/platform`;
  - [ ] convite criado/reenviado mantém a origem pública do Render, e o fluxo
        de aceite continua ativando a membership.

O checklist é um plano de inspeção, não uma afirmação de UAT visual executada.

## Complemento — T18: rota de aceitação de convites

**Data:** 23 de setembro de 2026
**Escopo:** T18 / F01-08 / F01-18
**Resultado automatizado:** PASS
**Revisão independente e UAT publicada:** pendentes

A aplicação registra a rota Vaadin `/invitations/{token}` como acessível a
usuários autenticados. A tela não exibe o token, e-mail convidado ou nome do
tenant; somente a ação explícita de confirmação chama o
`InvitationAcceptanceService`. Respostas de falha são genéricas e não exibem
mensagens ou causas do serviço.

Evidências:

- `InvitationAcceptanceViewTests`: 4 testes cobrindo ausência de mutação ao
  abrir, confirmação explícita com a identidade/token validados da sessão,
  bloqueio sem autenticação e mensagem genérica sem detalhes sensíveis.
- `InvitationRouteSecurityIntegrationTests`: GET não autenticado é salvo e
  redirecionado ao login; a autenticação simulada retorna ao mesmo caminho do
  convite com o parâmetro `continue` usado pelo fluxo Vaadin. Não há chamada
  real ao Supabase.
- `mvnw.cmd verify`: 182 testes, sem falhas, erros ou skips; frontend Vaadin
  construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em um projeto Compose isolado
  `gestao-producao-t18-gate-20260923`, porta 55433, com volume nomeado
  temporário próprio. Container, rede e volume temporário foram removidos e a
  ausência foi confirmada. O volume persistente
  preexistente do projeto foi preservado. Ele continha schemas não vazios sem
  histórico Flyway, portanto não foi usado nem alterado pelo gate.
- O `.env` ativo aponta para Supabase, mas não foi carregado. Nenhuma chamada
  ou alteração foi feita no Supabase ou no Render.
