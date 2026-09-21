# ADR-020: Flyway como executor de migrations

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decision:** `AD-019` in `.specs/STATE.md`

## Context

O projeto utiliza Supabase no início, mas o banco não deve ficar acoplado ao
fluxo de migrations do fornecedor. A aplicação pode futuramente usar outro
PostgreSQL ou outro provedor compatível sem perder o histórico e os scripts do
schema.

O Supabase CLI é útil para o ciclo de vida específico do Supabase, mas adotá-lo
como executor principal faria o histórico de migrations depender de uma
ferramenta do provedor. O projeto já decidiu que as migrations devem ser
versionadas, reproduzíveis e executadas por uma única fonte de histórico.

## Decision

O Flyway será a ferramenta única para executar e controlar as migrations do
schema da aplicação:

- o histórico de migrations será mantido pelo Flyway;
- as migrations serão scripts SQL versionados no Git;
- o Flyway será executado por JDBC sobre o banco configurado para o ambiente;
- não será mantido um segundo histórico de migrations pelo Supabase CLI;
- migrations de tabelas, constraints, índices, RLS, policies, grants e funções
  do domínio ficarão sob o mesmo fluxo do Flyway;
- migrations já aplicadas não serão editadas; correções serão feitas por uma
  nova migration versionada;
- a escolha do Flyway preserva a portabilidade do schema, sem eliminar as
  diferenças específicas do PostgreSQL ou as regras específicas do Supabase
  Auth e Storage;
- o starter e as dependências do Flyway serão adicionados usando o
  gerenciamento de versões do Spring Boot 4.1.1.

Os scripts de reversão continuarão sendo mantidos conforme o ADR-019. A
execução automática do comando `undo` não será considerada requisito da V0,
pois a documentação oficial do Flyway o classifica como recurso do Flyway
Teams. A edição e o procedimento operacional de reversão serão definidos na
fundação, sem alterar a obrigação de manter uma estratégia controlada de
rollback.

## Alternatives considered

### Supabase CLI como executor principal

Não adotado. Vincularia o histórico e o fluxo de deploy do schema ao provedor
atual, embora o domínio deva permanecer portátil.

### Liquibase

Não adotado. O projeto prefere migrations SQL diretas e a integração mais
simples com o ecossistema Spring e PostgreSQL oferecida pelo Flyway.

### Flyway e Supabase CLI em paralelo

Não adotado. Dois históricos de migration podem divergir e tornar ambíguo qual
ferramenta representa o estado oficial do banco.

## Consequences

### Positive

- O histórico do schema permanece no projeto e não no fornecedor de banco.
- A migração futura para outro PostgreSQL ou provedor é mais previsível.
- RLS e permissões evoluem junto com o código e os commits da aplicação.
- A ferramenta pode ser usada com o PostgreSQL do Supabase e com outro banco
  compatível por JDBC.
- A estratégia mantém uma única fonte de verdade para o estado esperado do
  schema.

### Negative

- Algumas policies RLS e integrações com `auth.uid()` podem depender de
  capacidades específicas do Supabase e precisarão de adaptação futura.
- A execução de `undo` pode exigir uma edição comercial; a V0 deverá usar um
  procedimento controlado compatível com o custo definido.
- O projeto precisa evitar alterações manuais no Dashboard para não criar
  divergência entre o histórico do Flyway e o banco remoto.

## Version baseline

Para o Spring Boot 4.1.1, a matriz oficial de dependências gerenciadas indica:

- `org.flywaydb:flyway-core` na versão `12.4.0`;
- `org.flywaydb:flyway-database-postgresql` na versão `12.4.0`;
- `org.springframework.boot:spring-boot-starter-flyway` na versão `4.1.1`.

Esses componentes ainda não foram adicionados ao `pom.xml`; a inclusão será
feita na fundação, com validação da árvore efetiva de dependências.

## References

- [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
- [Flyway undo](https://documentation.red-gate.com/flyway/reference/commands/undo)
- [Spring Boot managed dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
