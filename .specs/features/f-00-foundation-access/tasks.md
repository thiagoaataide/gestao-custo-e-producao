# Tasks da F-00 — Fundação técnica e acesso seguro

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: activate it by name and
follow its Execute flow and Critical Rules. Do not search for skill files by
filesystem path. The skill is the source of truth for the per-task cycle,
tests, commits, independent verification, and the discrimination sensor.

**Design:** `.specs/features/f-00-foundation-access/design.md`
**Status:** Execução em andamento — T1 a T11 concluídas; próxima tarefa
operacional: T12

## Test Coverage Matrix

> Gerada a partir de `AGENTS.md`, `pom.xml`, do teste existente em
> `src/test/java/br/com/taas/saas/gestaoproducao/GestaoProducaoApplicationTests.java`
> e da especificação/design da F-00. Não há `CONTRIBUTING.md`, workflow de CI ou
> limiar de cobertura no repositório; aplica-se o padrão forte da skill.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Security adapter and access decision | unit | Todos os branches e estados da especificação, incluindo token inválido, identidade sem vínculo e membership ambígua | `src/test/java/**/platform/access/**` | `./mvnw.cmd test` |
| Platform repositories and entities | integration | Caminhos principais de consulta, status e constraints em PostgreSQL real | `src/test/java/**/platform/identity/**` | `./mvnw.cmd verify` |
| Flyway, schema, grants and RLS | integration | Migrations desde banco vazio, idempotência, permissões, allow/deny por tenant e ausência de contexto | `src/test/java/**/integration/database/**` | `./mvnw.cmd verify` |
| Transaction and RLS context | integration | Mesmo connection/transaction, ausência de vazamento no pool, commit e rollback | `src/test/java/**/integration/tenancy/**` | `./mvnw.cmd verify` |
| Vaadin access shell | integration/smoke | Estados não autenticado, provisionado, não provisionado e membership ambígua sem conteúdo protegido | `src/test/java/**/ui/access/**` | `./mvnw.cmd verify` |
| Application configuration and container | none | Gate de build e empacotamento; não substituir os testes de segurança | `src/main/resources/**`, `Dockerfile` | `./mvnw.cmd clean verify` |

O Maven Wrapper foi executado de fato com o JDK 21. A validação de testes deve
continuar separada do gate de build: o teste de contexto atual requer datasource
e Flyway configurados, o que pertence à T4.

## Parallelism Assessment

> A execução permanece sequencial: a configuração de build e o YAML são
> compartilhados, e os testes de PostgreSQL usam estado e conexão não seguros
> para paralelismo. Mesmo tarefas unitárias não recebem `[P]` para preservar a
> ordem de validação da fundação.

| Test Type | Parallel-Safe? | Isolation Model | Evidence |
| --- | --- | --- | --- |
| Unit | Yes in isolation | Mocks e dados locais por teste | Não há estado global definido, mas o projeto ainda possui somente um teste de contexto. |
| Integration / PostgreSQL | No | Banco, migrations, roles e pool compartilhados por execução | `AGENTS.md` exige PostgreSQL real para RLS; o design exige teste de reuso de conexão. |
| Vaadin smoke | No | Contexto Spring e sessão de UI compartilhados por execução | O teste existente usa `@SpringBootTest`; não há isolamento paralelo documentado. |
| Build/container | No | Diretório `target` e artefato compartilhados | O build produz o artefato único da aplicação. |

## Gate Check Commands

> Comandos derivados do Maven Wrapper presente no repositório. A execução real
> deve ser registrada por tarefa; falha do wrapper, falta de Docker ou falta de
> acesso ao PostgreSQL é validação bloqueada, não aprovação parcial.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Tarefas somente unitárias | `./mvnw.cmd test` |
| Full | Tarefas com integração, banco ou UI | `./mvnw.cmd verify` |
| Build | Fase concluída, configuração ou container | `./mvnw.cmd clean verify` |

## Execution Plan

### Phase 1: Prerequisites and build foundation (Sequential)

```text
T1 → T2 → T3 → T4
```

### Phase 2: Access and tenant components (Sequential)

```text
Depois de T4, executar T5, T6 e T8 em sequência operacional.
T5 + T6 → T7; T4 + T7 + T8 → T9
```

### Phase 3: User-facing shell and integration (Sequential)

```text
T9 → T10 → T11 → T12
```

## Task Breakdown

### T1: Validar pré-requisitos externos da fundação

**Status:** Concluída em 21 de setembro de 2026. A role `app_runtime` foi
confirmada sem `BYPASSRLS`; o projeto usa ECC P-256 (`ES256`), possui chave
publicável ativa e expõe JWKS protegido pelo header `apikey`. Ver evidência em
`context.md`.

**What:** Confirmar o modo de assinatura JWT do projeto Supabase, issuer/JWKS,
credencial de runtime PostgreSQL e capacidade de manter essa credencial sem
`BYPASSRLS`, registrando evidências sem incluir segredos.
**Where:** `.specs/features/f-00-foundation-access/context.md`,
`.specs/STATE.md` e `AGENTS.md` somente se o inventário mudar.
**Depends on:** None
**Reuses:** AD-011, AD-016, AD-017, AD-023 e o design da F-00.
**Requirement:** F00-04, F00-11

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase`,
  `supabase:supabase-postgres-best-practices`
- Fonte: documentação oficial do Supabase e PostgreSQL; acesso ao ambiente
  gerenciado somente para leitura/validação.

**Done when:**

- [x] O issuer e o endpoint JWKS ou a limitação do modo simétrico estão
      documentados.
- [x] A role de runtime está comprovadamente sujeita a RLS e não é
      `service_role`, superuser ou role com `BYPASSRLS`.
- [x] A role/contexto de migration está identificado separadamente ou a
      limitação do provedor está registrada como risco bloqueador.
- [x] Nenhum token, chave, senha ou segredo foi salvo nos documentos ou no Git.
- [x] A validação foi executada de fato ou marcada como bloqueada com evidência.

**Tests:** none — validação documental/ambiental
**Gate:** build/documentation review
**Commit:** `chore(f00): validate foundation prerequisites`

---

### T2: Adicionar o baseline de dependências da fundação

**Status:** Concluída em 21 de setembro de 2026. O baseline foi adicionado ao
`pom.xml`, as versões foram registradas no `AGENTS.md`, a árvore e o effective
POM foram inspecionados e o empacotamento com testes ignorados passou. O teste de
contexto do esqueleto ainda falha por ausência da configuração de datasource,
uma condição esperada até a T4; essa falha não foi mascarada por exclusões de
auto-configuração.

**What:** Atualizar o `pom.xml` com os starters e drivers necessários para
Spring Security Resource Server, JPA, Flyway/PostgreSQL, PostgreSQL JDBC e
Vaadin, sem fixar versões já gerenciadas pelo Spring Boot.
**Where:** `pom.xml`, `AGENTS.md`
**Depends on:** T1
**Reuses:** parent Spring Boot 4.1.1, Java 21 e Maven Wrapper existentes.
**Requirement:** F00-01, F00-04, F00-13, F00-16

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `docs-writer`, `supabase:supabase`
- Fonte: documentação oficial dos componentes e dependency management do
  Spring Boot 4.1.1.

**Done when:**

- [x] As dependências necessárias estão no `pom.xml` com coordenadas oficiais.
- [x] Versões declaradas ou gerenciadas estão registradas no inventário de
      `AGENTS.md`.
- [x] Não foram adicionados JTA, Spring Cloud ou outra infraestrutura fora da
      V0.
- [x] A árvore de dependências e o effective POM foram inspecionados quando o
      Maven estiver executável.
- [x] O gate de build passa com `mvnw.cmd -DskipTests package` usando Java 21.

**Validação separada:** `mvnw.cmd test` foi executado com Java 21, mas o teste
de contexto falhou porque JPA/Flyway passaram a exigir datasource configurado.
O reparo pertence à T4; não foi introduzida uma exclusão de auto-configuração
apenas para fazer o teste passar.

**Tests:** none — configuração/build
**Gate:** build
**Commit:** `build(f00): add foundation dependencies`

---

### T3: Criar o schema de identidade, membership e RLS

**Status:** Concluída em 21 de setembro de 2026. A migration cria os
schemas `platform` e `operations`, identidade externa, tenant, membership,
`operations.tenant_settings`, grants mínimos, role de runtime sem bypass e
policies explícitas de RLS. A reversão é separada e não remove a role de
ambiente. O Compose local, a role de teste separada, o perfil `test` e os
testes de integração foram executados contra PostgreSQL real. O gate full
passou com Java 21 e PostgreSQL 17.11 local.

**What:** Criar a migration inicial com schemas `platform` e `operations`,
identidade externa, tenant, membership, a tabela real `operations.tenant_settings`,
constraints, índices, grants, roles necessários e policies RLS por operação.
**Where:** `src/main/resources/db/migration/V1__foundation_platform_and_rls.sql`,
`src/main/resources/db/revert/V1__foundation_platform_and_rls.sql`
**Depends on:** T2
**Reuses:** AD-014 a AD-023 e os padrões de migration definidos em `AGENTS.md`.
**Requirement:** F00-05, F00-06, F00-07, F00-09, F00-10, F00-11

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase`,
  `supabase:supabase-postgres-best-practices`
- Fonte: PostgreSQL `CREATE ROLE`, `CREATE POLICY`, Supabase RLS e Flyway.

**Done when:**

- [x] A migration é executável em PostgreSQL vazio e cria os objetos na ordem
      correta.
- [x] A unicidade normal de uma membership ativa por identidade está protegida
      por constraint/índice apropriado.
- [x] Tabelas tenant-scoped habilitam RLS e têm policies explícitas de
      `SELECT`, `INSERT`, `UPDATE` e `DELETE`, usando `USING` e `WITH CHECK`.
- [x] Ausência de `app.tenant_id` nega acesso à tabela tenant-scoped por
      comparação nula na policy.
- [x] O runtime não recebe privilégios de bypass; grants são mínimos.
- [x] O script de reversão é revisado e não é tratado como `flyway undo`.
- [x] Testes de migration e RLS incluídos nesta tarefa passam em PostgreSQL
      real.

**Validação da implementação:** `mvnw.cmd -DskipTests package`,
`docker compose --env-file .env.example config`, `bash -n` do script de
provisionamento e `mvnw.cmd verify` passaram com Java 21. O gate full subiu o
PostgreSQL 17.11 local, criou a role `app_runtime` sem `BYPASSRLS`, aplicou as
migrações V1 e V9999 pelo Flyway e executou os testes de isolamento RLS com
contexto ausente, dois tenants, INSERT, UPDATE e verificação da role de
runtime. Não foi aplicado DDL diretamente no Supabase.

Para repetir o gate local:

```text
docker compose --env-file .env.example up -d
./mvnw.cmd verify
```

O perfil de teste usa `app_runtime` para a aplicação e `postgres` somente para
o Flyway, mantendo a validação de RLS em PostgreSQL real.

**Tests:** integration
**Gate:** full
**Commit:** `feat(f00): add platform schema and tenant rls`

---

### T4: Configurar startup, datasource e Flyway

**Status:** Concluída em 21 de setembro de 2026. O startup exige a URL do
PostgreSQL e as credenciais separadas de runtime e migration; o Flyway usa
`classpath:db/migration`, valida o histórico e falha o startup quando a
configuração ou uma migration não pode ser aplicada.

**What:** Configurar propriedades externas, datasource de runtime, contexto de
migration, localização de migrations e falha de startup quando configuração ou
migration obrigatória não puder ser aplicada.
**Where:** `src/main/resources/application.yaml`,
`src/main/java/br/com/taas/saas/gestaoproducao/platform/bootstrap/`
**Depends on:** T2, T3
**Reuses:** `GestaoProducaoApplication` e autoconfiguração Spring Boot/Flyway.
**Requirement:** F00-01, F00-02, F00-03

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `docs-writer`
- Fonte: documentação oficial do Spring Boot e Flyway.

**Done when:**

- [x] Migrations pendentes executam no startup via Flyway.
- [x] A mesma migration não é reaplicada após reinicialização com schema
      atualizado.
- [x] Configuração de banco ausente ou inválida impede startup bem-sucedido.
- [x] Falha de migration impede que a aplicação seja tratada como disponível.
- [x] Configurações sensíveis vêm do ambiente e não entram no Git.
- [x] Testes de startup válido, schema atualizado e falha de migration passam.

**Validação da implementação:** `mvnw.cmd verify` passou com Java 21 contra
PostgreSQL 17.11 local. O Flyway validou o histórico, informou o schema como
atualizado e a aplicação conectou com `app_runtime`; o teste de startup válido
foi executado duas vezes sem reaplicar migrations. Os testes de configuração
inválida e migration SQL quebrada confirmaram falha de startup. Foram
executados 9 testes, sem falhas, erros ou skips. O contrato de variáveis e os
valores de demonstração foram atualizados em `.env.example` e `AGENTS.md`; não
há segredo de implantação versionado.

**Tests:** integration
**Gate:** full
**Commit:** `feat(f00): configure startup migrations`

---

### T5: Integrar o Resource Server JWT do Supabase

**Status:** Concluída em 21 de setembro de 2026. O Resource Server valida
issuer, assinatura ES256 via JWKS do Supabase, expiração e audience configurável;
o cliente JWKS envia a chave publicável somente no header `apikey`. O conversor
produz um `ExternalSubject` com `provider=SUPABASE` e `sub`, sem converter
claims editáveis em tenant ou autoridade. Os testes unitários cobrem identidade
válida, `sub` ausente ou em branco, issuer incorreto, expiração, audience
incorreta, assinatura inválida e header do JWKS.

**What:** Configurar o Spring Security Resource Server e o conversor que
transforma o JWT validado em uma identidade externa mínima, sem usar claims de
tenant ou `user_metadata` para autorização.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/platform/access/security/`
**Depends on:** T2
**Reuses:** `application.yaml` e configuração externa validada em T1.
**Requirement:** F00-04, F00-08

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase`
- Fonte: Spring Security Resource Server JWT e Supabase JWT/JWKS oficiais.

**Done when:**

- [x] Issuer, assinatura, expiração e audience quando aplicável são validados.
- [x] O claim `sub` é convertido para `ExternalSubject`.
- [x] Token inválido, expirado, sem `sub` utilizável ou com issuer incorreto é
      rejeitado antes do domínio.
- [x] Claims editáveis pelo usuário não definem tenant ou permissão.
- [x] Testes unitários cobrem todos os cenários de token definidos na spec.

**Tests:** unit
**Gate:** quick
**Commit:** `feat(f00): integrate supabase jwt authentication`

---

### T6: Implementar o modelo de identidade e membership

**Status:** Concluída em 21 de setembro de 2026. O modelo de domínio mantém
identificador externo e interno separados, os adapters consultam a identidade
e memberships ativas sem aceitar tenant do cliente, e a persistência foi
validada contra PostgreSQL real com o gate completo.

**What:** Implementar entidades/value objects e repositórios de identidade,
tenant e membership, incluindo status, papel mínimo e consulta de memberships
ativas.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/platform/identity/`
**Depends on:** T2, T3
**Reuses:** schema `platform` da T3 e o modelo do design.
**Requirement:** F00-05, F00-06, F00-07

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `tactical-ddd`

**Done when:**

- [x] O identificador externo é distinto do identificador interno da aplicação.
- [x] O repositório consulta membership por identidade externa sem aceitar
      tenant escolhido pelo cliente.
- [x] Status inativo/revogado não é retornado como vínculo operacional.
- [x] A constraint de identidade ativa é refletida no modelo e nos testes.
- [x] Testes de persistência executam contra PostgreSQL real.

**Tests:** integration — `IdentityMembershipPersistenceIntegrationTests`
 (5 testes; PostgreSQL real)
**Gate:** full
**Commit:** `feat(f00): add identity and membership persistence`

---

### T7: Implementar a decisão de acesso e resolução do tenant

**Status:** Concluída em 21 de setembro de 2026. O resolver produz decisões
determinísticas a partir do `ExternalSubject`, bloqueia identidades e vínculos
inativos ou ambíguos, não aceita tenant do cliente e cria contexto operacional
somente para tenant ativo.

**What:** Implementar o serviço que produz `TENANT_ACCESS`, `PLATFORM_ACCESS`,
`NOT_PROVISIONED` ou `AMBIGUOUS_MEMBERSHIP` a partir da identidade autenticada.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/platform/access/application/`
**Depends on:** T5, T6
**Reuses:** `ExternalSubject` e repositórios de T6.
**Requirement:** F00-05, F00-06, F00-07, F00-08

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `tactical-ddd`

**Done when:**

- [x] Exatamente uma membership ativa produz contexto de tenant determinístico.
- [x] Zero memberships produz decisão não provisionada sem criação automática.
- [x] Mais de uma membership ativa produz bloqueio por ambiguidade.
- [x] Membership revogada ou tenant indisponível produz bloqueio.
- [x] O serviço não recebe nem confia em `tenant_id` do cliente.
- [x] Testes unitários cobrem todos os branches e casos de borda da spec.

**Tests:** unit — `AccessDecisionResolverTests` (9 testes)
**Gate:** quick
**Commit:** `feat(f00): resolve provisioned tenant access`

---

### T8: Implementar o escritor de contexto RLS transacional

**Status:** Concluída em 21 de setembro de 2026. O port de saída e o adapter
PostgreSQL gravam o tenant resolvido com escopo local na conexão vinculada à
transação, sem aceitar entrada de tenant do cliente.

**What:** Implementar o adapter que grava `app.tenant_id` com escopo local na
conexão JDBC vinculada à transação atual.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/persistence/rls/`
**Depends on:** T2, T3
**Reuses:** `TenantId` e `TenantAccessContext` do design.
**Requirement:** F00-09, F00-10, F00-11, F00-12

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase-postgres-best-practices`
- Fonte: PostgreSQL `set_config/current_setting` e Spring transaction
  management oficiais.

**Done when:**

- [x] O valor é gravado com escopo local à transação.
- [x] O comando usa a mesma conexão que executará a operação tenant-scoped.
- [x] Ausência de transação/conexão vinculada falha de forma segura.
- [x] Nenhum valor vindo diretamente do cliente chega ao escritor.
- [x] Testes comprovam ausência de contexto, tenant correto, tenant diferente e
      reuso de conexão sem vazamento.

**Tests:** integration — `RlsTenantContextIntegrationTests` (4 testes; PostgreSQL
real; o teste de reuso valida o mesmo `pg_backend_pid()` com pool unitário).
**Gate:** full
**Validação:** `mvnw.cmd verify` passou com Java 21 contra PostgreSQL 17.11
local; 36 testes, 0 falhas, 0 erros e 0 skips.
**Commit:** `feat(f00): propagate tenant context to rls`

---

### T9: Integrar contexto, autorização e transação de caso de uso

**Status:** Concluída em 21 de setembro de 2026. O executor resolve a decisão
de acesso antes da transação, aplica o contexto RLS e executa o caso de uso na
mesma transação local, com commit ou rollback integral.

**What:** Integrar a decisão de acesso e o escritor RLS ao limite transacional
dos casos de uso, garantindo que operações tenant-scoped tenham contexto antes
da primeira consulta e rollback completo em falha.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/tenancy/`,
`src/main/java/br/com/taas/saas/gestaoproducao/platform/access/`
**Depends on:** T4, T7, T8
**Reuses:** serviços de T7, adapter de T8 e transações Spring.
**Requirement:** F00-09, F00-10, F00-11, F00-12, F00-13, F00-14, F00-15

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase-postgres-best-practices`

**Done when:**

- [x] Um caso de uso tenant-scoped só inicia após decisão de acesso válida.
- [x] O tenant do contexto não pode ser substituído por parâmetro do comando.
- [x] Commit confirma alterações locais como uma unidade.
- [x] Falha em etapa posterior reverte todas as alterações da operação.
- [x] A solução não adiciona JTA, Spring Cloud ou transação distribuída.
- [x] Testes de integração cobrem acesso permitido, acesso negado e rollback.

**Tests:** integration — `TenantScopedTransactionExecutorIntegrationTests` (4
testes; PostgreSQL real).
**Gate:** full
**Validação:** `mvnw.cmd verify` passou com Java 21 contra PostgreSQL 17.11
local; 40 testes, 0 falhas, 0 erros e 0 skips.
**Commit:** `feat(f00): enforce tenant transaction boundary`

---

### T10: Criar o shell Vaadin de autenticação e provisionamento

**Status:** Concluída em 21 de setembro de 2026. O shell Vaadin expõe os
estados não autenticado, provisionado, não provisionado, membership ambígua e
acesso de plataforma sem decidir tenant nem acessar repositórios diretamente.
As rotas públicas foram integradas à cadeia de segurança e os quatro estados
exigidos têm cobertura de integração.

**What:** Criar o shell mínimo que apresenta os estados não autenticado,
provisionado, não provisionado e membership ambígua, sem expor conteúdo
operacional bloqueado.
**Where:** `src/main/java/br/com/taas/saas/gestaoproducao/ui/access/`
**Depends on:** T5, T7
**Reuses:** decisão de acesso e entrypoint Spring Boot existentes.
**Requirement:** F00-16

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `docs-writer`

**Done when:**

- [x] Usuário não autenticado é conduzido ao fluxo de autenticação.
- [x] Usuário provisionado visualiza o estado de acesso permitido.
- [x] Usuário não provisionado recebe mensagem clara sem dados de terceiros.
- [x] Membership ambígua recebe bloqueio seguro.
- [x] Nenhum comando operacional aparece em estados bloqueados.
- [x] Testes de smoke/integrados cobrem os quatro estados.

**Tests:** integration
**Gate:** full
**Commit:** `feat(f00): add access state shell`

---

### T11: Validar a fundação ponta a ponta em PostgreSQL real

**Status:** Concluída em 21 de setembro de 2026. A suíte ponta a ponta foi
adicionada e validada contra PostgreSQL 17.11 real. Ela cria uma base isolada
vazia, aplica as migrations disponíveis uma única vez, confirma idempotência,
exercita a resolução de tenant contra tentativa por parâmetro, bloqueia
identidades sem acesso e confirma a negação de escrita cross-tenant por RLS.
As demais fronteiras da suíte completa cobrem ausência de contexto, reuso de
conexão, membership ambígua e rollback transacional.

**What:** Criar a suíte de integração que exercita startup, autenticação,
membership, RLS, reuso de conexão, isolamento entre dois tenants e rollback
de uma operação composta.
**Where:** `src/test/java/br/com/taas/saas/gestaoproducao/integration/`
**Depends on:** T4, T7, T8, T9, T10
**Reuses:** todas as fronteiras implementadas nas tarefas anteriores; não criar
uma tabela exclusiva de teste.
**Requirement:** F00-01 a F00-16

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `supabase:supabase-postgres-best-practices`

**Done when:**

- [x] O banco inicia vazio e recebe migrations uma única vez.
- [x] Usuários provisionados não atravessam tenants por parâmetro, URL ou
      consulta direta.
- [x] Usuários sem vínculo e com vínculo ambíguo são bloqueados.
- [x] Ausência de contexto é negada pelo RLS.
- [x] Contexto não vaza entre conexões/transações reutilizadas.
- [x] Falha deliberada não deixa estado parcial persistido.
- [x] Toda a suíte passa no gate full sem testes desabilitados ou removidos.

**Tests:** integration — `FoundationEndToEndIntegrationTests` (4 testes) e
suíte existente de startup, RLS, identidade, tenancy e UI.
**Validação:** `mvnw.cmd -Dtest=FoundationEndToEndIntegrationTests test` e
`mvnw.cmd verify` passaram com Java 21 contra PostgreSQL 17.11 local; o gate
full executou 48 testes, sem falhas, erros ou skips, e concluiu o build
frontend Vaadin e o Jar. `git diff --check` também passou.
**Gate:** full — aprovado
**Commit:** `test(f00): verify foundation isolation and rollback`

---

### T12: Empacotar runtime e contrato de configuração

**Status:** Concluída em 21 de setembro de 2026. A imagem multi-stage foi
criada com Eclipse Temurin 21 JDK para build e JRE para runtime, executando
como usuário não-root e copiando somente o Jar repacotado. O `.dockerignore`
remove arquivos `.env`, código de documentação, Git, `target` e caches do
contexto; o contrato de variáveis foi registrado no `AGENTS.md`.

**What:** Criar a imagem Docker multi-stage e documentar o contrato de
variáveis de ambiente necessário para runtime, sem incluir segredos na imagem.
**Where:** `Dockerfile`, `.dockerignore`, `AGENTS.md` e documentação da F-00
quando necessária.
**Depends on:** T4, T11
**Reuses:** Maven Wrapper, Java 21 Temurin e o artefato Jar do Spring Boot.
**Requirement:** F00-01, F00-02, F00-04, F00-11

**Tools:**

- MCP: NONE
- Skill: `tlc-spec-driven`, `docs-writer`, `best-practices`
- Fonte: documentação oficial do Docker, Eclipse Temurin, Spring Boot e
  Supabase.

**Done when:**

- [x] A imagem usa etapa de build e etapa de runtime separadas.
- [x] A etapa de runtime não contém código-fonte, caches ou segredos.
- [x] Java 21 é usado de forma coerente com `pom.xml` e `AGENTS.md`.
- [x] Variáveis obrigatórias de banco, Supabase e contexto são documentadas.
- [x] O build da imagem e o artefato Jar passam, quando Docker estiver
      disponível.
- [x] Falhas de validação de ambiente não são mascaradas pelo container.

**Tests:** none — build/container
**Validação:** `mvnw.cmd clean verify` passou com Java 21, executando 48 testes
sem falhas, erros ou skips, frontend Vaadin e Jar. `docker build
--tag gestao-producao:local .` passou; a inspeção confirmou usuário `app:app`,
entrypoint direto e somente `app.jar` no runtime, sem workspace ou cache Maven.
`docker run --rm gestao-producao:local` falhou explicitamente por ausência de
`DB_URL`, sem mascarar a configuração obrigatória.
**Gate:** build — aprovado
**Commit:** `build(f00): add multi-stage runtime image`

## Parallel Execution Map

```text
Dependências diretas:
  T1 → T2
  T2 → T3
  T2 → T4; T3 → T4
  T2 → T5
  T2 → T6; T3 → T6
  T5 → T7; T6 → T7
  T2 → T8; T3 → T8
  T4 → T9; T7 → T9; T8 → T9
  T5 → T10; T7 → T10
  T4 → T11; T7 → T11; T8 → T11; T9 → T11; T10 → T11
  T4 → T12; T11 → T12
```

Nenhuma tarefa está marcada com `[P]`. A dependência técnica e os testes com
PostgreSQL compartilhado tornam a execução sequencial a opção verificável para
esta fundação.

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Validar pré-requisitos externos | Uma validação e registro de evidências | ✅ Granular |
| T2: Adicionar dependências | Um baseline de build no `pom.xml` | ✅ Granular |
| T3: Criar schema e RLS | Uma migration coesa de fundação | ✅ Granular |
| T4: Configurar startup/Flyway | Um componente de bootstrap | ✅ Granular |
| T5: Integrar Resource Server | Um adapter de autenticação | ✅ Granular |
| T6: Implementar persistência de identidade | Um modelo/repositórios coesos | ✅ Granular |
| T7: Resolver decisão de acesso | Um serviço de decisão | ✅ Granular |
| T8: Escrever contexto RLS | Um adapter de infraestrutura | ✅ Granular |
| T9: Integrar limite transacional | Uma fronteira transversal | ✅ Granular |
| T10: Criar shell de acesso | Um componente de UI | ✅ Granular |
| T11: Validar integração | Uma suíte ponta a ponta da feature | ✅ Granular |
| T12: Empacotar runtime | Um artefato de build e contrato de ambiente | ✅ Granular |

## Diagram-Definition Cross-Check

| Task | Depends on (task body) | Diagram shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T2 | T2 → T3 | ✅ Match |
| T4 | T2, T3 | T2 → T4; T3 → T4 | ✅ Match |
| T5 | T2 | T2 → T5 | ✅ Match |
| T6 | T2, T3 | T2 → T6; T3 → T6 | ✅ Match |
| T7 | T5, T6 | T5 → T7; T6 → T7 | ✅ Match |
| T8 | T2, T3 | T2 → T8; T3 → T8 | ✅ Match |
| T9 | T4, T7, T8 | T4 → T9; T7 → T9; T8 → T9 | ✅ Match |
| T10 | T5, T7 | T5 → T10; T7 → T10 | ✅ Match |
| T11 | T4, T7, T8, T9, T10 | T4 → T11; T7 → T11; T8 → T11; T9 → T11; T10 → T11 | ✅ Match |
| T12 | T4, T11 | T4 → T12; T11 → T12 | ✅ Match |

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Validation/documentation | none | none | ✅ OK |
| T2 | Build/configuration | none | none | ✅ OK |
| T3 | Schema, grants and RLS | integration | integration | ✅ OK |
| T4 | Startup/Flyway configuration | integration | integration | ✅ OK |
| T5 | Security adapter | unit | unit | ✅ OK |
| T6 | Repository/entity | integration | integration | ✅ OK |
| T7 | Access decision service | unit | unit | ✅ OK |
| T8 | RLS context infrastructure | integration | integration | ✅ OK |
| T9 | Transaction/use-case boundary | integration | integration | ✅ OK |
| T10 | Vaadin shell | integration/smoke | integration | ✅ OK |
| T11 | Cross-boundary integration suite | integration | integration | ✅ OK |
| T12 | Container/configuration | none | none | ✅ OK |

## Antes do Execute

Antes de executar T1, confirme as ferramentas de cada tarefa. A proposta atual
é usar somente o filesystem/terminal do projeto, documentação web oficial e as
skills explicitadas em cada tarefa; não há MCP externo selecionado para esta
feature.

Pergunta obrigatória do processo: **para cada tarefa, quais ferramentas devo
usar?**

## Próximo passo

Após a aprovação deste `tasks.md` e da lista de ferramentas, executar T11. Cada
tarefa deverá terminar com seu gate, inspeção de diff, um commit atômico e
atualização do estado antes da próxima tarefa.
