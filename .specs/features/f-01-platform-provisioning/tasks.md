# F-01 — Tasks de provisionamento da plataforma

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: activate it by name and
follow its Execute flow and Critical Rules. The skill is the source of truth
for the per-task cycle, tests, atomic commits, independent verification and
discrimination sensor.

**Design:** `.specs/features/f-01-platform-provisioning/design.md`
**Status:** Rascunho — aguardando aprovação do design e das tasks

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

- [ ] O token em claro nunca é persistido.
- [ ] O repository encontra somente o convite pendente correto e respeita
      validade/estado.
- [ ] A constraint de duplicidade por tenant/e-mail é exercitada.
- [ ] Testes cobrem pendente, aceito, expirado, revogado e reenvio.

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

- [ ] O evento registra ator, ação, alvo, resultado e timestamp.
- [ ] Token, segredo e dados operacionais não entram no evento.
- [ ] Query paginada filtra apenas metadados administrativos.
- [ ] Testes de persistência cobrem sucesso, negação e consulta.

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

- [ ] Tenant pode ser criado sem membership.
- [ ] Transições `ACTIVE ↔ SUSPENDED` e `→ CLOSED` respeitam as regras.
- [ ] Membership revogada mantém histórico e não cria segundo vínculo ativo.
- [ ] Queries não aceitam tenant escolhido pelo cliente.
- [ ] Testes cobrem tenant sem usuário, suspensão, reativação, fechamento e
      revogação.

**Tests:** integration — `TenantLifecyclePersistenceIntegrationTests` e
membership cases no PostgreSQL real.
**Gate:** full.

### T7: Criar porta e adapter de perfil autenticado Supabase

**What:** Criar a fronteira que obtém subject, e-mail e confirmação do usuário
autenticado para aceitação segura, sem usar `user_metadata`.

**Where:** `src/main/java/**/platform/access/application/port/out/` e
`src/main/java/**/platform/access/security/`.

**Depends on:** T1.

**Requirements:** F01-08, F01-09.

**Done when:**

- [ ] O caso de uso recebe somente identidade autenticada já validada.
- [ ] O adapter consulta a fonte oficial de perfil por HTTPS e usa apenas
      campos confiáveis para e-mail/verificação.
- [ ] Chaves administrativas, se necessárias, ficam somente no backend e no
      ambiente; nenhuma vai para a UI.
- [ ] Falhas, timeout, e-mail ausente e e-mail não confirmado falham fechado.
- [ ] Testes unitários/contract cobrem headers, resposta válida e falhas HTTP.

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

- [ ] Somente a identidade autorizada pelo bootstrap pode criar o primeiro
      owner.
- [ ] Repetição do bootstrap é idempotente e não cria segundo owner.
- [ ] Owner e admin recebem decisões de plataforma sem tenant context.
- [ ] Admin comum não cria/remove administrador; owner pode fazê-lo.
- [ ] Tenant user não recebe acesso de plataforma.
- [ ] Testes unitários cobrem todos os branches de hierarquia e bootstrap.

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

- [ ] Owner/admin cria tenant ativo sem user obrigatório.
- [ ] Somente owner executa suspensão, reativação e fechamento.
- [ ] Fechamento é terminal e não apaga dados.
- [ ] Cada mutação grava auditoria na mesma transação.
- [ ] Falha em auditoria ou persistência faz rollback completo.
- [ ] Testes unitários e de integração cobrem sucesso, negação e rollback.

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

- [ ] Convite pendente tem validade de 24 horas e token não persistido em claro.
- [ ] Reenvio invalida o convite anterior e cria um novo link.
- [ ] Convite duplicado é recusado fora do comando explícito de reenvio.
- [ ] Convite para tenant suspenso/fechado é negado.
- [ ] Link sempre pode ser copiado e a falha de e-mail não desfaz o convite.
- [ ] Testes cobrem duplicidade, expiração, revogação, autorização e rollback.

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

- [ ] Aceitação exige token válido, identidade autenticada e e-mail verificado
      correspondente.
- [ ] Convite aceito torna-se inutilizável e ativa `TENANT_USER`.
- [ ] Segundo tenant é bloqueado sem alterar a membership atual.
- [ ] Associação manual não ativa sem confirmação do usuário.
- [ ] Falha em qualquer etapa reverte membership, convite e auditoria.
- [ ] Testes cobrem sucesso, divergência, expiração, tenant fechado, segundo
      tenant e concorrência/deduplicação.

**Tests:** unit + integration — `InvitationAcceptanceServiceTests` e
`InvitationAcceptanceIntegrationTests`.
**Gate:** full.

### T12: Implementar administração de memberships e administradores

**What:** Implementar concessão/revogação de `PLATFORM_ADMIN`, revogação de
membership `TENANT_USER` e consultas administrativas sem dados operacionais.

**Where:** `src/main/java/**/platform/administration/application/membership/`
e `src/main/java/**/platform/administration/application/role/`.

**Depends on:** T5, T8, T11.

**Requirements:** F01-11, F01-12, F01-13, F01-14, F01-15.

**Done when:**

- [ ] Somente owner concede ou revoga `PLATFORM_ADMIN`.
- [ ] Owner não pode criar segundo owner nem revogar a própria condição de
      owner na UI.
- [ ] Plataforma pode revogar membership sem apagar histórico.
- [ ] Consultas retornam status, papel e metadados mínimos, sem operations.
- [ ] Cada mutação gera auditoria e é transacional.
- [ ] Testes cobrem hierarquia, auto-revogação, membership e consultas.

**Tests:** unit + integration — `PlatformMembershipAdminServiceTests` e
`PlatformMembershipAdminIntegrationTests`.
**Gate:** full.

### T13: Integrar delivery opcional após commit

**What:** Criar a porta/adapter de entrega de convite e ligar a tentativa de
e-mail ao evento pós-commit, mantendo o link manual como caminho garantido.

**Where:** `src/main/java/**/platform/administration/application/port/out/` e
`src/main/java/**/platform/administration/integration/`.

**Depends on:** T10.

**Requirements:** F01-07, F01-09, F01-14, F01-16.

**Done when:**

- [ ] Nenhuma chamada de e-mail ocorre antes do commit do convite.
- [ ] Adapter ausente ou desabilitado não impede criação/cópia do link.
- [ ] Falha de entrega preserva convite e registra o resultado conforme o
      contrato de auditoria.
- [ ] Segredos de integração não aparecem em logs, UI ou commits.
- [ ] Testes de contrato cobrem sucesso, falha e ausência de configuração.

**Tests:** unit/contract — `InvitationDeliveryAdapterTests`.
**Gate:** quick; full se o adapter subir contexto real.

### T14: Criar UI de tenants, convites e memberships

**What:** Criar as telas Vaadin do contexto Platform Administration para listar
e criar tenants, executar ciclo de vida, criar/reenviar/revogar convites e
consultar memberships.

**Where:** `src/main/java/**/ui/platform/`.

**Depends on:** T9, T10, T11, T12, T13.

**Requirements:** F01-03 a F01-13.

**Done when:**

- [ ] Apenas owner/admin acessa as telas de plataforma.
- [ ] A UI mostra tenant sem user, estados e ações permitidas por papel.
- [ ] Link de convite pode ser copiado; falha de e-mail é comunicada sem
      perder o convite.
- [ ] Estados expirado, revogado, segundo tenant e tenant fechado mostram
      mensagens seguras.
- [ ] Tenant user não vê menu nem comandos de plataforma.
- [ ] Testes de integração cobrem owner/admin, bloqueios e fluxos principais.

**Tests:** integration/smoke — `PlatformAdministrationViewIntegrationTests`.
**Gate:** full.

### T15: Criar UI de auditoria administrativa

**What:** Criar a consulta Vaadin paginada dos eventos administrativos,
filtrando por ação, alvo, ator, resultado e período quando aplicável.

**Where:** `src/main/java/**/ui/platform/audit/`.

**Depends on:** T5, T12, T14.

**Requirements:** F01-14, F01-15.

**Done when:**

- [ ] Owner/admin consultam eventos sem acessar operations.
- [ ] Tenant user e usuário não provisionado não acessam a auditoria.
- [ ] A UI não mostra token, segredo ou conteúdo operacional.
- [ ] Consulta vazia, filtro inválido e falha de banco são tratados.
- [ ] Testes de integração cobrem autorização, filtro e conteúdo seguro.

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

- [ ] Banco vazio aplica a migration F-01 e a reinicialização é idempotente.
- [ ] Bootstrap cria somente o owner autorizado.
- [ ] Tenant sem user, ciclo de vida, convites, memberships e auditoria são
      demonstrados no fluxo completo.
- [ ] Link/e-mail opcional/associação manual respeitam confirmação e 24 horas.
- [ ] Admin não acessa operations e tenant user não acessa plataforma.
- [ ] Falha transacional não deixa estado parcial.
- [ ] `mvnw.cmd verify`, build limpo e `git diff --check` passam.
- [ ] Verificador independente registra PASS/FAIL por requisito e resultado do
      sensor; qualquer gap vira task de correção antes do encerramento.

**Tests:** integration — `PlatformProvisioningEndToEndIntegrationTests` mais
suíte existente.
**Gate:** full + independent verification.

## Requirement-to-task traceability

| Requirement | Tasks | Status |
| --- | --- | --- |
| F01-01 | T1, T3, T8, T16 | Mapped |
| F01-02 | T1, T2, T3, T8, T16 | Mapped |
| F01-03 | T2, T6, T9, T14, T16 | Mapped |
| F01-04 | T5, T9, T12, T14, T15, T16 | Mapped |
| F01-05 | T2, T6, T9, T14, T16 | Mapped |
| F01-06 | T2, T6, T9, T14, T16 | Mapped |
| F01-07 | T2, T4, T10, T13, T16 | Mapped |
| F01-08 | T7, T10, T11, T16 | Mapped |
| F01-09 | T4, T7, T10, T13, T14, T16 | Mapped |
| F01-10 | T4, T6, T11, T16 | Mapped |
| F01-11 | T1, T8, T11, T12, T16 | Mapped |
| F01-12 | T1, T3, T8, T12, T16 | Mapped |
| F01-13 | T6, T12, T16 | Mapped |
| F01-14 | T2, T5, T8, T9, T10, T11, T12, T13, T16 | Mapped |
| F01-15 | T5, T12, T15, T16 | Mapped |
| F01-16 | T2, T5, T9, T10, T11, T12, T13, T16 | Mapped |

**Coverage:** 16 requisitos definidos, 16 mapeados para tasks, 0 sem cobertura.

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

## Antes do Execute

Antes de executar as tasks, confirmar as ferramentas de cada tarefa. A proposta
é usar filesystem/terminal do projeto, Maven Wrapper, PostgreSQL Compose,
Docker quando necessário, documentação web oficial e as skills explicitadas em
cada task. Nenhum MCP externo é necessário para a decomposição atual.

Pergunta obrigatória do processo: **para cada task, quais ferramentas devo
usar?**

## Próximo passo

Após a aprovação deste `design.md` e `tasks.md`, executar T1 em ciclo atômico:
implementar, testar, inspecionar diff, fazer commit e atualizar `.specs/STATE.md`.
