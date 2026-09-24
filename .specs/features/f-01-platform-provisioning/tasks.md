# F-01 — Tasks de provisionamento da plataforma

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: activate it by name and
follow its Execute flow and Critical Rules. The skill is the source of truth
for the per-task cycle, tests, atomic commits, independent verification and
discrimination sensor.

**Design:** `.specs/features/f-01-platform-provisioning/design.md`
**Status:** T1–T16 concluídas e verificadas; T17 e T18 passaram pelos gates automatizados. O fechamento continua aberto para T19–T23, revisão independente e UAT publicada.

## Test Coverage Matrix

> Gerada a partir de `AGENTS.md`, `pom.xml`, dos testes existentes em
> `src/test/java` e da especificação/design da F-01. `AGENTS.md` exige testes
> aplicáveis, build Maven com Java 21 e preservação de ACID/RLS; não há limiar
> de cobertura nem CI versionado. Aplicam-se os defaults fortes da skill.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Domain value objects and state transitions | unit | Todos os branches; 1:1 com regras F01; cada edge case da spec | `src/test/java/**/platform/identity/**`, `src/test/java/**/platform/administration/**` | `mvnw.cmd test` |
| Application commands and authorization | unit | Todos os papéis, transições permitidas/negadas, duplicidade e idempotência | `src/test/java/**/platform/administration/application/**` | `mvnw.cmd test` |
| Flyway schema, constraints and grants | integration | Banco vazio, migration/reversão revisada, constraints e isolamento de dados administrativos | `src/test/java/**/integration/database/**` | `mvnw.cmd verify` |
| JPA repositories and adapters | integration | Caminhos de leitura/escrita, constraints, estados e queries sem dados de operations | `src/test/java/**/integration/platform/administration/**` | `mvnw.cmd verify` |
| Supabase identity adapter | unit/contract | Headers, claims confiáveis, e-mail verificado, falhas HTTP e ausência de segredo no cliente | `src/test/java/**/platform/access/security/**`, `src/test/java/**/integration/platform/supabase/**` | `mvnw.cmd test` / `mvnw.cmd verify` |
| Vaadin Platform Administration | integration/smoke | Fluxos de owner/admin, tenant, convite, membership e auditoria; sem comandos para tenant user | `src/test/java/**/ui/platform/**` | `mvnw.cmd verify` |
| Public invitation route and login return | integration/smoke | GET não aceita; confirmação explícita; login retorna ao mesmo convite; estados inválidos não expõem dados | `src/test/java/**/ui/**`, `src/test/java/**/integration/platform/administration/**` | `mvnw.cmd verify` |
| Invitation public-origin configuration | unit/integration | URL usa host configurado; publicação rejeita origem vazia, localhost ou esquema inseguro; local profile permitido | `src/test/java/**/platform/administration/**` | `mvnw.cmd verify` |
| Optional SendGrid adapter | contract | HTTP simulado cobre sucesso, erro, segredo ausente e falha pós-commit sem vazamento de token/PII | `src/test/java/**/platform/administration/integration/**` | `mvnw.cmd verify` |
| Vaadin presentation and responsive layout | integration/smoke + manual browser UAT | Campos e ações alinhados; grids legíveis; layout estreito/largo; foco e fluxo preservados | `src/test/java/**/ui/platform/**` e navegador no Render | `mvnw.cmd verify` + UAT |
| Access shell owner bootstrap | integration/smoke | Ação exibida somente ao subject configurado; clique executa bootstrap autorizado e concede acesso de plataforma | `src/test/java/**/ui/access/**` | `mvnw.cmd verify` |
| Runtime/configuration | none | Build e empacotamento; nenhuma credencial versionada | `src/main/resources/**`, `AGENTS.md` | `mvnw.cmd clean verify` |

## Parallelism Assessment

> A integração existente usa PostgreSQL compartilhado, fixtures e contexto
> transacional. A execução de integração da F-01 será sequencial para evitar
> colisões e vazamento de estado. Testes unitários puros podem ser paralelos,
> mas o plano inicial mantém execução sequencial para facilitar a evidência.

| Test Type | Parallel-Safe? | Isolation Model | Evidence |
| --- | --- | --- | --- |
| unit | Yes | Objetos imutáveis, mocks e IDs locais por teste | `src/test/java/**/platform/access/application/**` |
| integration/database | No | PostgreSQL compartilhado, migrations e fixtures | `FoundationRlsIntegrationTests` |
| integration/platform | No | PostgreSQL compartilhado e constraints globais | `IdentityMembershipPersistenceIntegrationTests` |
| integration/ui | No | Spring context e PostgreSQL compartilhados | `AccessShellViewIntegrationTests` |
| contract/external | Yes | Mock HTTP isolado por teste | `SupabaseJwkSetApiKeyInterceptorTests` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Tasks somente de domínio, autorização ou adapter mockado | `mvnw.cmd test` |
| Full | Tasks de schema, persistência, fluxo ou UI | `mvnw.cmd verify` |
| Build | Fechamento da feature e alterações de configuração | `mvnw.cmd clean verify` |

**Co-located tests:** cada task que criar ou alterar camada com teste exigido
deve incluir os testes no mesmo commit. Nenhum teste será separado somente para
"cobrir" uma task anterior.

## Plano de execução

```text
Fase 1 — contratos e persistência de base
  T1 → T2 → T3, T4, T5, T6
  T1 → T7

Fase 2 — casos de uso e segurança
  T3, T7 → T8
  T6, T8 → T9
  T4, T7, T8 → T10
  T3, T4, T7, T8 → T11
  T5, T8 → T12

Fase 3 — entrega e interface
  T10 → T13
  T8, T9, T10, T11, T12, T13 → T14
  T5, T11 → T15

Fase 4 — validação transversal
  T2, T8, T9, T10, T11, T12, T14, T15 → T16

Fase 5 — completar o bootstrap inicial pelo shell
  T16 → T17

Fase 6 — fechar o fluxo publicado e a interface administrativa
  T8, T11, T14 → T18
  T10, T13 → T19 → T20
  T14, T15 → T21
  T17, T18, T19, T20, T21 → T22 → T23
```

Nenhuma task está marcada com `[P]`: embora algumas units sejam paralelizáveis,
os adapters e as integrações dependem da migration, compartilham PostgreSQL e
precisam de commits sequenciais para manter a evidência.

## Tasks

### T1: Separar papel de plataforma do papel de tenant

**What:** Criar o modelo `PlatformRoleAssignment`, seus estados/papéis e a
porta de saída que representa autorização de plataforma sem `tenant_id`.

**Where:** `src/main/java/**/platform/identity/model/` e
`src/main/java/**/platform/identity/application/port/out/`.

**Depends on:** Nenhuma; usa a F-00 como baseline.

**Requirements:** F01-01, F01-02, F01-11, F01-12.

**Done when:**

- [x] `PLATFORM_OWNER` e `PLATFORM_ADMIN` são papéis distintos de membership.
- [x] O modelo impede papel vazio/desconhecido e expõe as regras de owner.
- [x] A porta consulta owner/admin por identidade sem aceitar tenant do cliente.
- [x] O teste unitário cobre owner único, papel válido e revogação.

**Tests:** unit — regras de papel, unicidade sem persistência e decisão de
hierarquia.
**Gate:** quick.

### T2: Criar migration da plataforma e reversão revisada

**What:** Evoluir o schema `platform` para role assignments, convites,
auditoria, nome/metadados de tenant e constraints dos estados da F-01.

**Where:** `src/main/resources/db/migration/V2__platform_provisioning.sql` e
`src/main/resources/db/revert/V2__platform_provisioning.sql`.

**Depends on:** T1.

**Requirements:** F01-02, F01-03, F01-05, F01-06, F01-07, F01-14, F01-16.

**Done when:**

- [x] A migration parte do schema V1 e cria as tabelas/constraints necessárias.
- [x] Há constraint para um owner ativo e um convite pendente por tenant/e-mail.
- [x] Estados, timestamps, foreign keys e índices refletem o spec.
- [x] A migration é transacional quando suportado e a reversão é revisada.
- [x] Testes contra PostgreSQL real cobrem banco vazio, estados, constraints e
      grants sem conceder RLS bypass ao runtime.

**Tests:** integration — migration e constraints no PostgreSQL real.
**Gate:** full.

### T3: Persistir atribuições de papel de plataforma

**What:** Implementar entidade JPA, repository Spring Data e adapter da porta
de `PlatformRoleRepository`.

**Where:** `src/main/java/**/platform/identity/persistence/` e
`src/main/java/**/platform/identity/persistence/jpa/`.

**Depends on:** T1, T2.

**Requirements:** F01-01, F01-02, F01-12.

**Done when:**

- [x] O adapter persiste e consulta owner/admin por identidade interna.
- [x] A constraint de owner único é respeitada e traduzida para erro de
      domínio previsível.
- [x] Revogação preserva o histórico e não altera membership de tenant.
- [x] Testes de integração cobrem sucesso, duplicidade e revogação.

**Tests:** integration — `PlatformRolePersistenceIntegrationTests`.
**Gate:** full.

### T4: Persistir convites e seus estados

**What:** Implementar entidade JPA, repository e adapter para convite, incluindo
normalização de e-mail, digest do token e consulta de convite pendente.

**Where:** `src/main/java/**/platform/identity/persistence/` e
`src/main/java/**/platform/identity/persistence/jpa/`.

**Depends on:** T2.

**Requirements:** F01-07, F01-08, F01-09, F01-10.

**Done when:**

- [x] O token em claro nunca é persistido.
- [x] O repository encontra somente o convite pendente correto e respeita
      validade/estado.
- [x] A constraint de duplicidade por tenant/e-mail é exercitada.
- [x] Testes cobrem pendente, aceito, expirado, revogado e reenvio.

**Tests:** integration — `InvitationPersistenceIntegrationTests`.
**Gate:** full.

### T5: Persistir auditoria administrativa

**What:** Implementar entidade JPA, repository e adapter para eventos de
auditoria de plataforma.

**Where:** `src/main/java/**/platform/administration/audit/` e
`src/main/java/**/platform/administration/persistence/`.

**Depends on:** T2.

**Requirements:** F01-14, F01-15, F01-16.

**Done when:**

- [x] O evento registra ator, ação, alvo, resultado e timestamp.
- [x] Token, segredo e dados operacionais não entram no evento.
- [x] Query paginada filtra apenas metadados administrativos.
- [x] Testes de persistência cobrem sucesso, negação e consulta.

**Tests:** integration — `AdministrativeAuditPersistenceIntegrationTests`.
**Gate:** full.

### T6: Evoluir persistência do tenant e membership

**What:** Atualizar entidades, repositories e adapters existentes para nome,
ciclo de vida de tenant e transições de membership exigidas pela F-01.

**Where:** `src/main/java/**/platform/identity/model/`,
`src/main/java/**/platform/identity/persistence/`.

**Depends on:** T2.

**Requirements:** F01-03, F01-05, F01-06, F01-10, F01-13.

**Done when:**

- [x] Tenant pode ser criado sem membership.
- [x] Transições `ACTIVE ↔ SUSPENDED` e `→ CLOSED` respeitam as regras.
- [x] Membership revogada mantém histórico e não cria segundo vínculo ativo.
- [x] Queries não aceitam tenant escolhido pelo cliente.
- [x] Testes cobrem tenant sem usuário, suspensão, reativação, fechamento e
      revogação.

**Tests:** integration — `TenantLifecyclePersistenceIntegrationTests` e
membership cases no PostgreSQL real.
**Gate:** full.

### T7: Criar porta e adapter de perfil autenticado Supabase

**Status:** Concluída em 22 de setembro de 2026. A porta provider-neutral,
o contexto de token validado e o adapter HTTP do Supabase foram implementados
com teste contract/unit; o contexto Spring e o gate completo foram validados
contra PostgreSQL isolado, com commit atômico.

**What:** Criar a fronteira que obtém subject, e-mail e confirmação do usuário
autenticado para aceitação segura, sem usar `user_metadata`.

**Where:** `src/main/java/**/platform/access/application/port/out/` e
`src/main/java/**/platform/access/security/`.

**Depends on:** T1.

**Requirements:** F01-08, F01-09.

**Done when:**

- [x] O caso de uso recebe somente identidade autenticada já validada.
- [x] O adapter consulta a fonte oficial de perfil por HTTPS e usa apenas
      campos confiáveis para e-mail/verificação.
- [x] Chaves administrativas, se necessárias, ficam somente no backend e no
      ambiente; nenhuma vai para a UI.
- [x] Falhas, timeout, e-mail ausente e e-mail não confirmado falham fechado.
- [x] Testes unitários/contract cobrem headers, resposta válida e falhas HTTP.

**Tests:** unit/contract — `SupabaseAuthenticatedIdentityAdapterTests`.
**Gate:** quick; full se houver contexto Spring.

### T8: Implementar bootstrap e autorização da plataforma

**What:** Implementar o bootstrap idempotente do owner e o serviço que resolve
`PLATFORM_OWNER`/`PLATFORM_ADMIN`, substituindo a leitura legada de papel em
membership no resolver de acesso.

**Where:** `src/main/java/**/platform/administration/application/` e
`src/main/java/**/platform/access/application/`.

**Depends on:** T3, T7.

**Requirements:** F01-01, F01-02, F01-12.

**Done when:**

- [x] Somente a identidade autorizada pelo bootstrap pode criar o primeiro
      owner.
- [x] Repetição do bootstrap é idempotente e não cria segundo owner.
- [x] Owner e admin recebem decisões de plataforma sem tenant context.
- [x] Admin comum não cria/remove administrador; owner pode fazê-lo.
- [x] Tenant user não recebe acesso de plataforma.
- [x] Testes unitários cobrem todos os branches de hierarquia e bootstrap.

**Tests:** unit — `PlatformAuthorizationServiceTests` e
`BootstrapOwnerServiceTests`.
**Gate:** quick.

### T9: Implementar comandos de tenant e ciclo de vida

**What:** Implementar criação, consulta administrativa, suspensão, reativação e
fechamento terminal de tenant em uma transação local com auditoria.

**Where:** `src/main/java/**/platform/administration/application/tenant/`.

**Depends on:** T6, T8.

**Requirements:** F01-03, F01-04, F01-05, F01-06, F01-16.

**Done when:**

- [x] Owner/admin cria tenant ativo sem user obrigatório.
- [x] Somente owner executa suspensão, reativação e fechamento.
- [x] Fechamento é terminal e não apaga dados.
- [x] Cada mutação grava auditoria na mesma transação.
- [x] Falha em auditoria ou persistência faz rollback completo.
- [x] Testes unitários e de integração cobrem sucesso, negação e rollback.

**Tests:** unit + integration — `TenantProvisioningServiceTests` e
`TenantProvisioningIntegrationTests`.
**Gate:** full.

### T10: Implementar comandos de convite

**What:** Implementar criação, reenvio, revogação, geração de link e expiração
de convite, com deduplicação e auditoria.

**Where:** `src/main/java/**/platform/administration/application/invitation/`.

**Depends on:** T4, T7, T8.

**Requirements:** F01-07, F01-09, F01-14, F01-16.

**Done when:**

- [x] Convite pendente tem validade de 24 horas e token não persistido em claro.
- [x] Reenvio invalida o convite anterior e cria um novo link.
- [x] Convite duplicado é recusado fora do comando explícito de reenvio.
- [x] Convite para tenant suspenso/fechado é negado.
- [x] Link sempre pode ser copiado e a falha de e-mail não desfaz o convite.
- [x] Testes cobrem duplicidade, expiração, revogação, autorização e rollback.

**Tests:** unit + integration — `InvitationCommandServiceTests` e
`InvitationCommandIntegrationTests`.
**Gate:** full.

### T11: Implementar aceitação de convite e ativação de membership

**What:** Implementar a confirmação autenticada do convite, validação de e-mail,
regra de um tenant por usuário e ativação transacional de membership.

**Where:** `src/main/java/**/platform/administration/application/invitation/`
e `src/main/java/**/platform/identity/application/`.

**Depends on:** T3, T4, T7, T8.

**Requirements:** F01-08, F01-10, F01-11, F01-13, F01-16.

**Done when:**

- [x] Aceitação exige token válido, identidade autenticada e e-mail verificado
      correspondente.
- [x] Convite aceito torna-se inutilizável e ativa `TENANT_USER`.
- [x] Segundo tenant é bloqueado sem alterar a membership atual.
- [x] Associação manual não ativa sem confirmação do usuário.
- [x] Falha em qualquer etapa reverte membership, convite e auditoria.
- [x] Testes cobrem sucesso, divergência, expiração, tenant fechado, segundo
      tenant e concorrência/deduplicação.

**Tests:** unit + integration — `InvitationAcceptanceServiceTests` e
`InvitationAcceptanceIntegrationTests`.
**Gate:** full.

### T12: Implementar administração de memberships e administradores

**Status:** Concluída em 22 de setembro de 2026. O serviço administrativo,
as consultas de metadados e os testes unitários/PostgreSQL foram implementados
e validados no gate completo.

**What:** Implementar concessão/revogação de `PLATFORM_ADMIN`, revogação de
membership `TENANT_USER` e consultas administrativas sem dados operacionais.

**Where:** `src/main/java/**/platform/administration/application/membership/`
e `src/main/java/**/platform/administration/application/role/`.

**Depends on:** T5, T8, T11.

**Requirements:** F01-11, F01-12, F01-13, F01-14, F01-15.

**Done when:**

- [x] Somente owner concede ou revoga `PLATFORM_ADMIN`.
- [x] Owner não pode criar segundo owner nem revogar a própria condição de
       owner na UI.
- [x] Plataforma pode revogar membership sem apagar histórico.
- [x] Consultas retornam status, papel e metadados mínimos, sem operations.
- [x] Cada mutação gera auditoria e é transacional.
- [x] Testes cobrem hierarquia, auto-revogação, membership e consultas.

**Tests:** unit + integration — `PlatformMembershipAdminServiceTests` e
`PlatformMembershipAdminIntegrationTests`.
**Gate:** full.

### T13: Integrar delivery opcional após commit

**Status:** Concluída em 22 de setembro de 2026. A porta provider-neutral,
o evento de delivery e o listener `AFTER_COMMIT` foram implementados com
delivery desabilitado por padrão, auditoria segura de falhas e validação no
gate completo.

**What:** Criar a porta/adapter de entrega de convite e ligar a tentativa de
e-mail ao evento pós-commit, mantendo o link manual como caminho garantido.

**Where:** `src/main/java/**/platform/administration/application/port/out/` e
`src/main/java/**/platform/administration/integration/`.

**Depends on:** T10.

**Requirements:** F01-07, F01-09, F01-14, F01-16.

**Done when:**

- [x] Nenhuma chamada de e-mail ocorre antes do commit do convite.
- [x] Adapter ausente ou desabilitado não impede criação/cópia do link.
- [x] Falha de entrega preserva convite e registra o resultado conforme o
      contrato de auditoria.
- [x] Segredos de integração não aparecem em logs, UI ou commits.
- [x] Testes de contrato cobrem sucesso, falha e ausência de configuração.

**Tests:** unit/contract — `InvitationDeliveryAdapterTests` e
`InvitationDeliveryAfterCommitIntegrationTests`.
**Gate:** full.

### T14: Criar UI de tenants, convites e memberships

**Status:** Concluída em 22 de setembro de 2026. A rota Vaadin de administração
da plataforma, as consultas administrativas, os comandos de tenants, convites,
memberships e papéis, o bloqueio por identidade/papel e os testes de smoke foram
implementados. O gate completo passou com 145 testes, sem falhas, erros ou skips,
incluindo build frontend e empacotamento do JAR.

**What:** Criar as telas Vaadin do contexto Platform Administration para listar
e criar tenants, executar ciclo de vida, criar/reenviar/revogar convites e
consultar memberships.

**Where:** `src/main/java/**/ui/platform/`.

**Depends on:** T9, T10, T11, T12, T13.

**Requirements:** F01-03 a F01-13.

**Done when:**

- [x] Apenas owner/admin acessa as telas de plataforma.
- [x] A UI mostra tenant sem user, estados e ações permitidas por papel.
- [x] Link de convite pode ser copiado; falha de e-mail é comunicada sem
      perder o convite.
- [x] Estados expirado, revogado, segundo tenant e tenant fechado mostram
      mensagens seguras.
- [x] Tenant user não vê menu nem comandos de plataforma.
- [x] Testes de integração cobrem owner/admin, bloqueios e fluxos principais.

**Tests:** integration/smoke — `PlatformAdministrationViewIntegrationTests`.
**Gate:** full.

### T15: Criar UI de auditoria administrativa

**What:** Criar a consulta Vaadin paginada dos eventos administrativos,
filtrando por ação, alvo, ator, resultado e período quando aplicável.

**Where:** `src/main/java/**/ui/platform/audit/`.

**Depends on:** T5, T12, T14.

**Requirements:** F01-14, F01-15.

**Done when:**

- [x] Owner/admin consultam eventos sem acessar operations.
- [x] Tenant user e usuário não provisionado não acessam a auditoria.
- [x] A UI não mostra token, segredo ou conteúdo operacional.
- [x] Consulta vazia, filtro inválido e falha de banco são tratados.
- [x] Testes de integração cobrem autorização, filtro e conteúdo seguro.

**Tests:** integration/smoke — `AdministrativeAuditViewIntegrationTests`.
**Gate:** full.

### T16: Validar F-01 ponta a ponta e executar verificador independente

**What:** Criar a suíte transversal da F-01 e, após o último commit de
implementação, executar a verificação independente com evidence-or-zero,
incluindo sensor de discriminação.

**Where:** `src/test/java/**/integration/platform/administration/` e
`.specs/features/f-01-platform-provisioning/validation.md`.

**Depends on:** T2, T8, T9, T10, T11, T12, T14, T15.

**Requirements:** F01-01 a F01-16.

**Done when:**

- [x] Banco vazio aplica a migration F-01 e a reinicialização é idempotente.
- [x] Bootstrap cria somente o owner autorizado.
- [x] Tenant sem user, ciclo de vida, convites, memberships e auditoria são
      demonstrados no fluxo completo.
- [x] Link/e-mail opcional/associação manual respeitam confirmação e 24 horas.
- [x] Admin não acessa operations e tenant user não acessa plataforma.
- [x] Falha transacional não deixa estado parcial.
- [x] `mvnw.cmd verify`, build limpo e `git diff --check` passam.
- [x] Verificador independente registra PASS/FAIL por requisito e resultado do
      sensor; qualquer gap vira task de correção antes do encerramento.

**Tests:** integration — `PlatformProvisioningEndToEndIntegrationTests` mais
suíte existente.
**Gate:** full + independent verification.

### T17: Conectar o bootstrap do owner à tela inicial

**Status:** Implementada em 23 de setembro de 2026; gate automatizado PASS. Revisão independente e UAT visual pendentes.

**What:** Mostrar uma ação explícita para iniciar o bootstrap somente quando a
identidade Supabase autenticada corresponder ao `owner-subject` configurado.
No clique, chamar `BootstrapOwnerService`, que revalida o subject, registra o
owner e a auditoria na transação existente; após sucesso, abrir a administração
da plataforma. Não executar bootstrap automaticamente no login.
**Where:** `AccessShellView`, testes de integração da tela e documentos F-01
`spec.md`, `design.md`, `tasks.md` e `validation.md`.
**Depends on:** T8, T16.
**Requirements:** F01-01, F01-17.

**Done when:**

- [x] O usuário sem provisionamento vê a ação somente se seu `ExternalSubject`
      corresponde exatamente ao `platform.bootstrap.owner-subject`.
- [x] Identidades não autorizadas e estados de acesso já provisionados não
      recebem a ação de bootstrap.
- [x] O clique chama o serviço transacional com a identidade da sessão; o
      serviço revalida o subject e, ao concluir, a mesma identidade resolve para
      `PLATFORM_ACCESS` e pode abrir a tela administrativa.
- [x] O login por si só não cria owner; não há UUID hardcoded e nenhuma
      informação de segredo é mostrada na UI.
- [x] Testes verificam a ação para o subject configurado, a ausência para
      subject diferente e o resultado de acesso de plataforma após o bootstrap.
- [x] `mvnw.cmd verify` passa sem remover ou desabilitar testes (177 testes; frontend e JAR construídos).

**Tests:** integration/smoke — `AccessShellViewIntegrationTests`.
**Gate:** full — PASS em PostgreSQL 17 temporário local; a revisão independente e a UAT visual permanecem pendentes.
**Commit:** `fix(platform): expose configured owner bootstrap`

### T18: Abrir e aceitar convites pela rota Vaadin

**Status:** Implementada e validada em 23 de setembro de 2026; `mvnw.cmd verify` passou com 182 testes, sem falhas, erros ou skips.

**What:** Registrar a rota pública `/invitations/{token}` e conectar a tela de
convite ao serviço de aceitação existente. Se a pessoa não estiver autenticada,
preservar o destino completo durante o login e retornar ao mesmo convite; não
aceitar convite por simples GET ou redirecionamento.

**Where:** `src/main/java/**/ui/` e testes de UI/integração de convite.
**Depends on:** T8, T11, T14.
**Requirements:** F01-08, F01-18.

**Done when:**

- [x] Abrir o link apresenta confirmação explícita e não altera estado.
- [x] Após autenticar, a pessoa retorna ao mesmo convite e pode confirmar uma
      única vez; só o serviço existente decide a aceitação.
- [x] E-mail não verificado/divergente, convite expirado, revogado, já aceito,
      tenant fechado ou membership ativa em outro tenant recebem resultado
      seguro, sem revelar e-mail ou tenant de terceiros.
- [x] Token não aparece em logs, telemetria, auditoria ou texto de erro.
- [x] Testes de integração cobrem rota direta, sessão ausente/login/retorno,
      confirmação, estados inválidos e ausência de aceitação automática.

**Tests:** integration/smoke — nova suíte de rota/aceitação, mantendo os
testes do `InvitationAcceptanceService`.
**Gate:** full — PASS (`mvnw.cmd verify`, 182 testes; frontend Vaadin e JAR construídos).

### T19: Validar a origem pública dos links de convite

**What:** Separar a origem local da origem publicada para que todos os links
copiados ou entregues sejam navegáveis no ambiente correto.

**Where:** propriedades de convite, configuração por ambiente e testes da
fábrica de links.
**Depends on:** T10, T13.
**Requirements:** F01-09, F01-19.

**Done when:**

- [x] Perfil local aceita a origem local explicitamente definida.
- [x] Ambiente publicado exige origem HTTPS pública e rejeita vazio,
      `localhost`, loopback e esquema inseguro antes de emitir convite.
- [x] Link copiado e link passado ao adapter usam a mesma origem, caminho e
      token opaco, sem duplicar barras ou acrescentar URLs de callback alheias.
- [x] Testes cobrem configuração válida e cada origem inválida; exemplos de
      ambiente documentam apenas nomes/valores não secretos.

**Tests:** unit/integration — fábrica e binding de configuração por ambiente.
**Gate:** full.

### T20: Implementar entrega opcional de convite por SendGrid

**What:** Implementar o adapter SendGrid atrás da `InvitationDeliveryPort`
existente. A entrega segue opcional e posterior ao commit; o fluxo de link
copiável não depende do adapter.

**Where:** módulo de integração da F-01, configuração de runtime e testes de
contrato; sem tipo do vendor no domínio.
**Depends on:** T13, T19.
**Requirements:** F01-09.

**Done when:**

- [ ] SendGrid implementa somente a porta existente; não é criado bounded
      context, microserviço ou chamada de vendor no domínio.
- [ ] Adapter fica inativo se não configurado; quando ativo, entrega o link
      após commit sem atrasar nem desfazer a criação do convite.
- [ ] Falha do provedor é tratada/auditada sem invalidar convite nem remover o
      link copiável.
- [ ] API key existe apenas como secret de ambiente, sem logs/respostas; testes
      de contrato simulam sucesso, rejeição e indisponibilidade.
- [ ] A ativação usa somente o serviço/plano gratuito já disponível; se houver
      requisito de upgrade ou cobrança, não habilitar e parar para decisão.
- [ ] Documentação oficial atual do SendGrid é conferida para a chamada e os
      limites efetivamente usados; UAT real fica para a configuração manual.

**Tests:** contract — HTTP simulado e transação já confirmada antes da entrega.
**Gate:** full.

### T21: Refinar o estilo responsivo da administração Vaadin

**What:** Melhorar hierarquia visual, alinhamento e responsividade das telas de
tenants, convites, memberships, papéis e auditoria sem alterar regras ou
permissões.

**Where:** UI Vaadin e stylesheet da aplicação.
**Depends on:** T14, T15.
**Requirements:** F01-20.

**Done when:**

- [x] Tipografia, espaçamento, alinhamento de labels/campos e hierarquia de
      botões formam um padrão consistente nas telas administrativas.
- [x] Grids apresentam cabeçalhos, ações e conteúdo legíveis sem ocupar área
      vazia desproporcional; formulários e ações se reorganizam em viewport
      estreito sem corte horizontal.
- [x] Foco, contraste e navegação por teclado continuam perceptíveis e
      nenhuma ação/autorização existente muda.
- [x] A implementação usa tema/variantes/custom properties e CSS documentados
      na versão Vaadin 25.2.8; sem hacks de Shadow DOM ou dependência visual
      adicional não aprovada.
- [x] Testes smoke e checklist de inspeção visual cobrem viewport estreito e
      amplo; APIs exatas foram conferidas em documentação oficial versionada.

**Tests:** integration/smoke; confirmação visual manual no navegador fica em
T23.
**Gate:** full.

### T22: Revalidar os fluxos publicados da F-01

**What:** Adicionar regressão transversal que prova convite emitido → link
público → autenticação preservando retorno → confirmação → membership ativa;
revalidar configuração da origem e entrega opcional isolada.

**Where:** `src/test/java/**/integration/platform/administration/` e
`.specs/features/f-01-platform-provisioning/validation.md`.
**Depends on:** T17, T18, T19, T20, T21.
**Requirements:** F01-01 a F01-20.

**Done when:**

- [ ] PostgreSQL é o serviço compartilhado previsto no Compose/Testcontainers
      existente; nenhum container descartável é deixado após os testes.
- [ ] Fluxo completo confirma token, origem, autenticação, confirmação, estado
      da membership e auditoria sem exibir dados de outro tenant.
- [ ] Caminho sem SendGrid continua disponível; adapter é provado com HTTP
      simulado, sem chamada real no suite automatizado.
- [ ] `mvnw.cmd clean verify` e `git diff --check` passam sem excluir testes.
- [ ] `validation.md` distingue testes locais, testes com serviço simulado e
      UAT ainda não executada no Render.

**Tests:** integration — extensão da suíte ponta a ponta existente.
**Gate:** build limpo + full.

### T23: Revisar independentemente e concluir UAT da F-01

**What:** Após o último commit de implementação, executar revisão fresh-eyes
com sensor de discriminação e roteiro de UAT no Render para fechar as lacunas
visíveis em ambiente publicado.

**Where:** relatório final em `validation.md`; checklist de UAT da F-01.
**Depends on:** T22.
**Requirements:** F01-01 a F01-20.

**Done when:**

- [ ] Verificador independente confirma cada requisito, relatório por
      requisito e discriminação das regras críticas; gaps geram nova task.
- [ ] Com identidade de plataforma, UI alinha e funciona em viewport estreito
      e amplo; origem dos links é o domínio público do Render.
- [ ] Link real de convite abre a rota publicada, permite login/retorno e só
      ativa o vínculo após confirmação com identidade de e-mail verificado
      correspondente.
- [ ] Envio real por SendGrid é verificado apenas se configurado pelo usuário;
      ausência/falha mantém o link copiável e nenhum plano pago é ativado.
- [ ] Nenhum segredo é solicitado, copiado para os documentos ou exposto em
      evidência; UAT ausente fica explicitamente pendente e F01 não é declarada
      concluída.

**Tests:** independent review + manual browser UAT; sem dependência de acesso a
credenciais pelo agente.
**Gate:** revisão independente + UAT publicada.

## Requirement-to-task traceability

| Requirement | Tasks | Status |
| --- | --- | --- |
| F01-01 | T1, T3, T8, T16, T17 | Verified after T17 |
| F01-02 | T1, T2, T3, T8, T16 | Verified |
| F01-03 | T2, T6, T9, T14, T16 | Verified |
| F01-04 | T5, T9, T12, T14, T15, T16 | Verified |
| F01-05 | T2, T6, T9, T14, T16 | Verified |
| F01-06 | T2, T6, T9, T14, T16 | Verified |
| F01-07 | T2, T4, T10, T13, T16, T18, T19, T22, T23 | Core rule and route/authentication automated tests passed; published origin and UAT pending |
| F01-08 | T7, T10, T11, T16, T18, T22, T23 | Core rule, explicit confirmation and login return automated tests passed; published end-to-end UAT pending |
| F01-09 | T4, T7, T10, T13, T14, T16, T19, T20, T22, T23 | Link/port behavior verified; published origin and SendGrid adapter pending |
| F01-10 | T4, T6, T11, T16 | Verified |
| F01-11 | T1, T8, T11, T12, T16 | Verified |
| F01-12 | T1, T3, T8, T12, T16 | Verified |
| F01-13 | T6, T12, T16 | Verified |
| F01-14 | T2, T5, T8, T9, T10, T11, T12, T13, T16 | Verified |
| F01-15 | T5, T12, T15, T16 | Verified |
| F01-16 | T2, T5, T9, T10, T11, T12, T13, T16 | Verified |
| F01-17 | T17, T23 | Automated verification passed; independent review and browser UAT pending |
| F01-18 | T18, T22, T23 | Automated verification passed — T18; published end-to-end/UAT pending |
| F01-19 | T19, T22, T23 | Pending |
| F01-20 | T21, T23 | Pending |

**Coverage:** 20 requisitos definidos e mapeados para tasks, 0 sem cobertura.

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1 | Modelo/porta de papel de plataforma | ✅ Atomic |
| T2 | Migration e reversão da fundação F-01 | ✅ Atomic |
| T3 | Persistência de role assignment | ✅ Atomic |
| T4 | Persistência de convite | ✅ Atomic |
| T5 | Persistência de auditoria | ✅ Atomic |
| T6 | Persistência de tenant/membership | ✅ Atomic |
| T7 | Adapter de perfil Supabase | ✅ Atomic |
| T8 | Bootstrap e autorização de plataforma | ✅ Atomic |
| T9 | Casos de uso de tenant | ✅ Atomic |
| T10 | Comandos de convite | ✅ Atomic |
| T11 | Aceitação e ativação | ✅ Atomic |
| T12 | Administração de memberships/papéis | ✅ Atomic |
| T13 | Delivery pós-commit | ✅ Atomic |
| T14 | UI de tenants/convites/memberships | ✅ Atomic |
| T15 | UI de auditoria | ✅ Atomic |
| T16 | Validação transversal e verificador | ✅ Atomic |
| T17 | Ação de bootstrap do owner no shell | ✅ Atomic |
| T18 | Rota pública e continuidade do aceite | ✅ Atomic |
| T19 | Origem pública de links por ambiente | ✅ Atomic |
| T20 | Adapter opcional SendGrid atrás da porta existente | ✅ Atomic |
| T21 | Estilo responsivo das telas administrativas | ✅ Atomic |
| T22 | Regressão transversal publicada da F-01 | ✅ Atomic |
| T23 | Revisão independente e UAT Render | ✅ Atomic |

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T1, T2 | T1 → T2 → T3 | ✅ Match |
| T4 | T2 | T1 → T2 → T4 | ✅ Match |
| T5 | T2 | T1 → T2 → T5 | ✅ Match |
| T6 | T2 | T1 → T2 → T6 | ✅ Match |
| T7 | T1 | T1 → T7 | ✅ Match |
| T8 | T3, T7 | T3/T7 → T8 | ✅ Match |
| T9 | T6, T8 | T6/T8 → T9 | ✅ Match |
| T10 | T4, T7, T8 | T4/T7/T8 → T10 | ✅ Match |
| T11 | T3, T4, T7, T8 | T3/T4/T7/T8 → T11 | ✅ Match |
| T12 | T5, T8, T11 | T5/T8/T11 → T12 | ✅ Match |
| T13 | T10 | T10 → T13 | ✅ Match |
| T14 | T9, T10, T11, T12, T13 | T9/T10/T11/T12/T13 → T14 | ✅ Match |
| T15 | T5, T12, T14 | T5/T12/T14 → T15 | ✅ Match |
| T16 | T2, T8, T9, T10, T11, T12, T14, T15 | all listed predecessors → T16 | ✅ Match |
| T17 | T8, T16 | T8/T16 → T17 | ✅ Match |
| T18 | T8, T11, T14 | T8/T11/T14 → T18 | ✅ Match |
| T19 | T10, T13 | T10/T13 → T19 | ✅ Match |
| T20 | T13, T19 | T13/T19 → T20 | ✅ Match |
| T21 | T14, T15 | T14/T15 → T21 | ✅ Match |
| T22 | T17, T18, T19, T20, T21 | all listed predecessors → T22 | ✅ Match |
| T23 | T22 | T22 → T23 | ✅ Match |

## Test Co-location Validation

| Task | Code layer | Matrix requirement | Task includes | Status |
| --- | --- | --- | --- | --- |
| T1 | Domain/port | unit | Unit tests in same task | ✅ OK |
| T2 | Schema/migration | integration | PostgreSQL migration/constraint tests | ✅ OK |
| T3 | Repository/entity | integration | Persistence integration tests | ✅ OK |
| T4 | Repository/entity | integration | Invitation persistence tests | ✅ OK |
| T5 | Repository/entity | integration | Audit persistence tests | ✅ OK |
| T6 | Repository/entity | integration | Tenant/membership integration tests | ✅ OK |
| T7 | External adapter | unit/contract | Mock HTTP contract tests | ✅ OK |
| T8 | Application/security | unit | Bootstrap/authorization unit tests | ✅ OK |
| T9 | Application use case | unit + integration | Service and PostgreSQL tests | ✅ OK |
| T10 | Application use case | unit + integration | Command and persistence tests | ✅ OK |
| T11 | Application/use case | unit + integration | Acceptance and activation tests | ✅ OK |
| T12 | Application/use case | unit + integration | Admin service and integration tests | ✅ OK |
| T13 | External adapter | unit/contract | Delivery contract tests | ✅ OK |
| T14 | Vaadin UI | integration/smoke | Platform view integration tests | ✅ OK |
| T15 | Vaadin UI | integration/smoke | Audit view integration tests | ✅ OK |
| T16 | Cross-boundary | integration | Full end-to-end suite and verifier | ✅ OK |
| T17 | Access shell bootstrap | integration/smoke | Configured and non-configured subject, persisted platform access | ✅ OK |
| T18 | Public invitation route | integration/smoke | Explicit acceptance, auth return continuity, invalid invitation states | ✅ OK |
| T19 | Invitation URL configuration | unit/integration | Local/public profiles and rejection of unsafe published origins | ✅ OK |
| T20 | SendGrid adapter | contract | Mock HTTP client, after-commit ordering, failure without losing copied link | ✅ OK |
| T21 | Vaadin visual refinement | integration/smoke | Component hierarchy, responsive layout rules and no authorization regression | ✅ OK |
| T22 | Cross-boundary regression | integration | Existing PostgreSQL-backed suite plus invite/auth/accept flow; no leaked test containers | ✅ OK |
| T23 | Independent verification and Render UAT | independent/manual | Fresh-eyes report and evidence for deployed URL, invite, authentication and viewports | ✅ OK |

## Antes do Execute

Antes de executar as tasks, confirmar as ferramentas de cada tarefa. A proposta
é usar filesystem/terminal do projeto, Maven Wrapper, PostgreSQL Compose,
Docker quando necessário, documentação web oficial e as skills explicitadas em
cada task. Nenhum MCP externo é necessário para a decomposição atual.

Pergunta obrigatória do processo: **para cada task, quais ferramentas devo
usar?**

## Próximo passo

Confirmar as ferramentas de execução conforme o gate **Antes do Execute** e
iniciar T18. A ordem proposta termina em T23; só declarar F-01 concluída após
revisão independente e UAT no Render. A F-02 permanece preservada como
planejamento, sem tarefas implementadas.
