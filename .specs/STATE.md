# STATE

## Decisions

### AD-001
- **Decision**: A V0 is the only implementation scope currently authorized.
- **Reason**: V1, V2, and V3 ideas provide evolution context but must not
  expand the first validated product flow.
- **Trade-off**: Future capabilities may need later adaptation instead of
  being anticipated in the V0 model.
- **Scope**: Product requirements, architecture, feature specifications, and
  implementation planning.
- **Date**: 2026-09-20
- **Status**: active

### AD-002
- **Decision**: Marmitaria is the first validation case, while the domain
  remains centered on production planning, costs, and inputs for small food
  producers.
- **Reason**: Validate with a real operation without coupling the core model
  unnecessarily to finished lunch boxes.
- **Trade-off**: Some marmitaria-specific behavior must be expressed through
  generic production and product concepts.
- **Scope**: Domain model and V0 feature specifications.
- **Date**: 2026-09-20
- **Status**: active

### AD-003
- **Decision**: Each user accesses only one tenant in the V0.
- **Reason**: Keep the first access model simple while preserving explicit
  tenant isolation in the domain.
- **Trade-off**: A future multi-tenant user relationship may require a new
  decision and migration.
- **Scope**: Authentication boundary, authorization, and all tenant-owned
  data.
- **Date**: 2026-09-20
- **Status**: active

### AD-004
- **Decision**: The production week start is configurable as Sunday or Monday,
  and weekly grouping may be optional for planning.
- **Reason**: Different operations organize production cycles differently; the
  system should configure the planning convention instead of hard-coding it.
- **Trade-off**: Planning and reporting need an explicit interpretation of
  dates when a week is not used.
- **Scope**: Production planning and related reports.
- **Date**: 2026-09-20
- **Status**: active

### AD-005
- **Decision**: V0 orders are entered as confirmed orders only; cancellations
  are handled according to the operational stage reached by the order.
- **Reason**: The application is intended to plan real demand, not a sales
  pipeline of unconfirmed requests.
- **Trade-off**: The system does not model an unconfirmed-order workflow in V0.
- **Scope**: Orders, planning recalculation, purchasing, production, and
  cancellation history.
- **Date**: 2026-09-20
- **Status**: active

### AD-006
- **Decision**: V0 does not maintain finished-meal stock as the primary stock
  concept; it controls produced components or production availability and
  records their later destination.
- **Reason**: Meals are assembled from confirmed demand and are not intended
  to be produced as a generic finished-goods inventory.
- **Trade-off**: Production can exist without a final customer assignment and
  requires a subsequent destination such as own consumption, donation,
  resale, or discard.
- **Scope**: Production, consumption, losses, destinations, and inventory
  movements.
- **Date**: 2026-09-20
- **Status**: active

### AD-007
- **Decision**: Products have fixed composition in V0; customer-specific
  changes are captured as order notes and do not automatically recalculate the
  technical production plan.
- **Reason**: The production plan is based on the declared product composition
  and controlled purchases.
- **Trade-off**: Operational substitutions remain a manual responsibility.
- **Scope**: Products, recipes, orders, and production planning.
- **Date**: 2026-09-20
- **Status**: active

### AD-008
- **Decision**: Base units, purchase units, package quantities, and raw-to-ready
  yields are represented separately and converted explicitly.
- **Reason**: Inputs can be purchased in discrete packages and transform during
  preparation, such as raw weight becoming a different ready weight.
- **Trade-off**: Planning and stock calculations require explicit conversion
  and yield rules.
- **Scope**: Inputs, technical sheets, purchases, stock, production, and
  consumption.
- **Date**: 2026-09-20
- **Status**: active

### AD-009
- **Decision**: Projected cost uses the latest normalized purchase price;
  realized CMV uses the actual cost of lots consumed in the cycle.
- **Reason**: Planning needs a practical current estimate, while realized
  indicators must reflect what was actually consumed.
- **Trade-off**: Projected and realized values may differ and must remain
  visibly distinct.
- **Scope**: Cost calculation, CMV, margin, recovery indicators, and reports.
- **Date**: 2026-09-20
- **Status**: active

### AD-010
- **Decision**: The V0 technology baseline is Java 21, Spring Boot 4.1.1,
  Maven with the project wrapper, Vaadin, managed PostgreSQL, Supabase Auth,
  Supabase Storage, and a multi-stage Docker image.
- **Reason**: Use the selected developer stack, managed services, and a low-cost
  deployment model suitable for the first validation.
- **Trade-off**: V0 accepts pauses, cold starts, and no high-availability
  guarantee; dependencies must remain compatible with the Spring Boot baseline.
- **Scope**: Foundation, build, runtime configuration, authentication,
  persistence, storage, and deployment.
- **Date**: 2026-09-20
- **Status**: active

### AD-011
- **Decision**: Supabase manages authentication, while the application domain
  owns the user-to-tenant association and tenant isolation rules.
- **Reason**: Gain implementation speed without coupling domain tenancy to the
  authentication provider, preserving a future migration boundary.
- **Trade-off**: The application must explicitly translate authenticated
  identity into its domain tenant context.
- **Scope**: Authentication, Spring Security boundary, authorization, and all
  tenant-scoped use cases.
- **Date**: 2026-09-20
- **Status**: active

### AD-012
- **Decision**: No JTA implementation, Spring Cloud, or other transversal
  infrastructure is added without a concrete V0 requirement.
- **Reason**: Keep the first foundation small and avoid complexity that the
  current single-application flow does not need.
- **Trade-off**: A future distributed transaction or service decomposition
  would require a new architectural decision.
- **Scope**: Dependency foundation and runtime architecture.
- **Date**: 2026-09-20
- **Status**: active

### AD-013
- **Decision**: The project follows the TLC spec-driven flow: roadmap,
  feature specification, context when gray areas exist, design when the
  feature is large or complex, atomic tasks, implementation, and independent
  verification.
- **Reason**: Prevent context loss and preserve traceability from the PRD to
  implementation and validation.
- **Trade-off**: Documentation is created before implementation for features
  whose behavior or dependencies are not trivial.
- **Scope**: Project planning and every V0 feature.
- **Date**: 2026-09-20
- **Status**: active

### AD-014
- **Decision**: Supabase Auth owns the user account and authentication; the
  application domain separately provisions the tenant and the active
  user-to-tenant association. An authenticated user without an active
  association is blocked from domain access with a clear not-provisioned
  message.
- **Reason**: Keep authentication separate from tenancy, avoid accidental
  tenant creation, and make the access decision explicit and auditable.
- **Trade-off**: The initial user and tenant setup requires a controlled
  provisioning operation before the first successful domain access.
- **Scope**: Authentication boundary, tenant resolution, authorization, and
  initial environment bootstrap.
- **Date**: 2026-09-20
- **Status**: active

### AD-015
- **Decision**: The application has two bounded contexts inside the modular
  monolith: Platform Administration and Tenant Operations. Platform
  Administration creates tenants, manages invitations and memberships, and
  accesses only platform metadata; it cannot access operational tenant data.
- **Reason**: Separate platform governance from customer operation and enforce
  least privilege for future SaaS and LGPD requirements without introducing a
  microservice prematurely.
- **Trade-off**: The monolith must maintain explicit module and authorization
  boundaries so the platform context cannot depend directly on tenant
  operational data.
- **Scope**: Architecture, authentication roles, tenant provisioning,
  invitations, authorization, and V0 module boundaries.
- **Date**: 2026-09-21
- **Status**: active

### AD-016
- **Decision**: Tenant isolation is enforced in two layers: application
  authorization and PostgreSQL Row Level Security (RLS). The application
  resolves the tenant from the authenticated identity and active membership;
  RLS provides a second barrier for tenant-owned data.
- **Reason**: Prevent cross-tenant access caused by an omitted application
  filter while preserving application ownership of roles, use cases, and
  business rules.
- **Trade-off**: The foundation must define how the application connection
  carries the resolved tenant context and must test both allowed and denied
  access paths.
- **Scope**: Tenant isolation, PostgreSQL access policies, authorization, and
  all tenant-owned V0 data.
- **Date**: 2026-09-21
- **Status**: active

### AD-017
- **Decision**: The backend is the trusted source for the tenant context. It
  resolves the active membership and tenant, establishes that context for each
  operational transaction, and uses a database connection subject to RLS.
  Transactions without a valid tenant context fail safely.
- **Reason**: Prevent the client from selecting a tenant and ensure that RLS
  remains effective on the normal application path.
- **Trade-off**: The foundation must define and test transaction-scoped
  context propagation without allowing context leakage through the connection
  pool.
- **Scope**: Tenant resolution, transaction boundary, PostgreSQL RLS, database
  roles, and tenant-scoped V0 operations.
- **Date**: 2026-09-21
- **Status**: active

### AD-018
- **Decision**: Each state-changing use case runs in one local ACID PostgreSQL
  transaction. If any step fails, the whole transaction is rolled back and no
  partial domain state is persisted. JTA and distributed transactions are not
  used in V0.
- **Reason**: Keep purchases, stock, production, consumption, destinations,
  and realized costs consistent within each operation.
- **Trade-off**: External effects outside PostgreSQL are not atomically
  included and require explicit treatment; use cases must keep a clear
  transaction boundary.
- **Scope**: Transaction boundaries, PostgreSQL persistence, tenant-scoped
  operations, stock, production, purchases, consumption, and cost records.
- **Date**: 2026-09-21
- **Status**: active

### AD-019
- **Decision**: The database schema is managed exclusively through versioned,
  Git-tracked migrations. Each migration includes an application script and a
  reviewed reversal script when technically reversible; failed executions are
  rolled back by their transaction when supported, while rollback of an
  already-applied migration is explicit and controlled.
- **Reason**: Keep schema, RLS, grants, constraints, indexes, and data changes
  reproducible, auditable, and recoverable across environments.
- **Trade-off**: Destructive changes require backup or a data-recovery plan,
  and every schema change requires migration review and validation.
- **Scope**: PostgreSQL schema, Supabase migrations, RLS, permissions, and
  foundation/deployment workflow.
- **Date**: 2026-09-21
- **Status**: active

### AD-020
- **Decision**: Flyway is the single migration executor and schema history for
  the application. Supabase CLI is not used as a second migration history.
  Flyway dependencies will use the Spring Boot 4.1.1 managed baseline; undo
  execution is not assumed to require the paid Teams edition in V0.
- **Reason**: Preserve database-provider portability while keeping one
  versioned, Git-tracked source of truth for PostgreSQL schema changes.
- **Trade-off**: Supabase-specific RLS/Auth behavior remains provider-aware,
  and rollback execution needs a controlled procedure if Flyway Teams is not
  adopted.
- **Scope**: Database migrations, schema history, RLS, permissions, build
  foundation, and deployment workflow.
- **Date**: 2026-09-21
- **Status**: active

### AD-021
- **Decision**: V0 uses Flyway Community without a Flyway Teams license or
  dependency. Versioned migrations remain in Git with reviewed reversal
  scripts, but the `flyway undo` command is not a V0 requirement.
- **Reason**: Preserve the zero-cost V0 while retaining versioned migrations,
  transaction rollback for failed executions, and controlled recovery for
  applied migrations.
- **Trade-off**: Schema reversions require an explicit operational procedure or
  a corrective forward migration; they are not an automatic Teams command.
- **Scope**: Flyway edition, migration execution, rollback, recovery, and V0
  foundation cost constraints.
- **Date**: 2026-09-21
- **Status**: active

### AD-022
- **Decision**: Pending Flyway migrations run automatically during application
  startup. The application is not considered ready until migrations succeed;
  a migration failure causes startup failure.
- **Reason**: Keep the V0 deployment simple and reproducible with one monolith,
  one primary database, low volume, and accepted cold starts.
- **Trade-off**: A bad or slow migration delays or blocks startup; multi-instance
  deployment will require a future review of this strategy.
- **Scope**: Application startup, Flyway execution, deployment, and schema
  compatibility.
- **Date**: 2026-09-21
- **Status**: active

### AD-023
- **Decision**: Tenant isolation uses a transaction-local PostgreSQL setting.
  After the backend resolves exactly one active membership, the tenant ID is
  written with `set_config('app.tenant_id', ..., true)` on the same JDBC
  connection and transaction that executes tenant operations. RLS policies
  compare the row tenant ID with `current_setting('app.tenant_id', true)`.
  The runtime database role must not have `BYPASSRLS`; Flyway uses a separate
  controlled migration credential when provider capabilities allow it.
- **Reason**: Preserve backend ownership of tenant resolution, make the
  context transaction-scoped, and prevent context leakage through pooled
  connections while keeping PostgreSQL as a second isolation barrier.
- **Trade-off**: The design requires a reliable transaction-bound connection
  adapter, a runtime role that is subject to RLS, and integration tests against
  real PostgreSQL rather than an in-memory substitute.
- **Scope**: Tenant context propagation, PostgreSQL RLS, connection pooling,
  database roles, all tenant-owned V0 operations, and future feature design.
- **Date**: 2026-09-21
- **Status**: active

### AD-024
- **Decision**: A F-02 planeja o saldo de insumos a partir de movimentos
  rastreáveis por entrada identificável. Toda saída ou correção de quantidade
  valida o saldo da própria origem sob trava transacional, sem editar ou
  apagar movimentos anteriores.
- **Reason**: Cancelamentos e correções precisam respeitar consumo, descarte
  e outras saídas já vinculadas à compra, sem permitir que outro lote cubra
  um saldo insuficiente.
- **Trade-off**: Consultas de saldo exigem agregação e comandos concorrentes
  exigem ordenação estável de travas; projeções poderão ser avaliadas depois
  de medir desempenho.
- **Scope**: F-02, estoque e integrações futuras de F-06, F-07 e F-08.
- **Date**: 2026-09-22
- **Status**: proposed in F-02 design

### AD-025
- **Decision**: Saldo inicial e ajuste positivo sem compra documentada têm
  custo desconhecido (`NULL`), nunca custo zero ou preço herdado. Valor e
  quantidade de compra permanecem vinculados ao documento e às suas revisões.
- **Reason**: O estoque pode reunir entradas de preços diferentes; preencher
  custo fictício distorceria o CMV realizado quando ele for implementado.
- **Trade-off**: Algumas consultas futuras de custo terão resultado incompleto
  até haver regra explícita de valoração para essas origens.
- **Scope**: F-02 e cálculo realizado futuro de F-08.
- **Date**: 2026-09-22
- **Status**: proposed in F-02 design

### AD-026
- **Decision**: O login Vaadin usa e-mail/senha validados pelo Supabase Auth e
  mantém uma sessão Spring Security no servidor. Access e refresh tokens ficam
  somente nessa sessão; o tenant e as permissões continuam sendo resolvidos
  pelo domínio da aplicação.
- **Reason**: Completar o login web da F-00 com o modelo de sessão natural do
  Vaadin sem deslocar autenticação, autorização de tenant ou dados pessoais
  para o navegador.
- **Trade-off**: A aplicação precisa renovar e rotacionar refresh tokens sob
  sincronização por sessão; cold starts ou reinícios da instância encerram as
  sessões em memória e exigem novo login.
- **Scope**: Login Vaadin, sessão de aplicação, refresh/logout Supabase e
  integração com a identidade e autorização já existentes.
- **Date**: 2026-09-23
- **Status**: implemented locally; full isolated verification recorded in F-00 `validation.md`; live Supabase/Render UAT pending

## Temporary test containers

Registre aqui cada container descartável criado para uma validação isolada e
remova a entrada somente depois de confirmar sua remoção. Não liste o serviço
compartilhado de `compose.yaml` nesta seção.

- **Ativos**: nenhum container temporário do projeto encontrado em 23 de
  setembro de 2026 (`docker ps -a --filter name=gestao`).

## Handoff

- **Feature**: F-02 — Cadastro base, compras e estoque.
- **Phase / Task**: Specify, Design e Tasks concluídos como propostas; nenhuma
  tarefa T01–T25 executada ou validada em runtime.
- **Completed**: F-00 encerrada; entregas T1–T16 da F-01 verificadas; PRD e
  roadmap da F-02 alinhados;
  decisões funcionais em `context.md`; 32 critérios em `spec.md`; desenhos
  técnicos em `design.md`; 25 tarefas com dependências e gates em `tasks.md`.
- **In progress**: Correção de fechamento da F-01 (T17): bootstrap explícito
  do owner pela tela inicial implementado; gate automatizado passou com 177
  testes. Revisão independente e UAT visual no Render pendentes. O planejamento
  da F-02 permanece preservado, sem tasks executadas.
- **Next step**: Revisar independentemente T17 e validar no Render com a
  identidade owner configurada; após aprovação, retomar a revisão do design e
  das tarefas da F-02. Quando a execução da F-02 for autorizada, iniciar pela
  T01, com um commit e gate por tarefa. Antes da T11, confirmar PDFBox e
  atualizar `AGENTS.md` junto da dependência.
- **External prerequisites**: Projeto dedicado de OCR externo com credencial
  privada, limite de uso e amostras reais para aferir lista manuscrita e
  comprovante. Google Cloud Vision e seus limites são propostas técnicas.
- **Preserve local changes**: Alterações em `docs/PRD-V0.md`,
  `docs/ROADMAP-V0.md` e arquivos da F-02 pertencem ao planejamento.
  `src/main/resources/application.yaml` tem alteração independente e não
  deve ser incluído em commits dessas tarefas sem inspeção.
- **Branch**: `main` (à frente de `origin/main` por um commit, na última
  verificação deste planejamento).
