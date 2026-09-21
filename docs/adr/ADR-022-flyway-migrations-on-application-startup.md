# ADR-022: Execução das migrations na inicialização

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `AD-020` and `AD-021` in `.specs/STATE.md`

## Context

A V0 terá um único monólito, um único banco principal e uma operação inicial de
baixo volume. Manter uma etapa externa obrigatória para executar migrations
exigiria um pipeline adicional antes de cada inicialização ou deploy.

O Spring Boot possui integração oficial com o Flyway e pode executar as
migrations automaticamente durante a inicialização da aplicação.

## Decision

As migrations pendentes do Flyway serão executadas automaticamente na
inicialização da aplicação:

- o Spring Boot iniciará o Flyway com a configuração do ambiente;
- o Flyway validará o histórico e aplicará as migrations pendentes em ordem;
- a aplicação não será considerada pronta para uso antes da conclusão bem-
  sucedida das migrations;
- se uma migration falhar, a inicialização da aplicação falhará e o erro será
  tratado como falha de startup;
- não haverá uma etapa manual obrigatória no Supabase Dashboard ou no Supabase
  CLI antes de iniciar a aplicação;
- migrations devem ser pequenas, revisadas e testadas para não transformar o
  startup em uma operação longa ou imprevisível;
- a execução automática poderá ser alterada somente por uma nova decisão
  arquitetural.

Essa decisão vale para a V0, que aceita pausas, cold starts e ausência de alta
disponibilidade. Um cenário futuro com múltiplas instâncias ou deploys
independentes deverá reavaliar a estratégia sem alterar o histórico já aplicado.

## Alternatives considered

### Executar migrations em pipeline separado

Não adotado na V0. Exigiria um processo adicional de deploy e coordenação para
o primeiro ambiente, sem benefício proporcional ao volume atual.

### Executar migrations manualmente antes de cada startup

Não adotado. Aumentaria o risco de esquecer uma migration e faria o estado do
schema depender de uma operação humana fora do repositório.

### Iniciar a aplicação e migrar depois

Não adotado. A aplicação poderia iniciar esperando um schema incompatível com
o código em execução.

## Consequences

### Positive

- O processo de inicialização é reproduzível e simples.
- O código não inicia contra um schema conhecido como desatualizado.
- A estratégia reduz dependências operacionais para a V0.
- O comportamento combina com o modelo de baixo custo e cold starts aceitos.

### Negative

- Uma migration com erro impede a aplicação de iniciar.
- Migrations demoradas aumentam o tempo de startup e de recuperação após uma
  pausa.
- O deploy passa a depender da disponibilidade do banco no momento do startup.
- A estratégia não substitui uma política futura de coordenação entre múltiplas
  instâncias.

## References

- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Boot Flyway auto-configuration](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration.html)
- [Spring Boot Flyway migration initializer](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/flyway/autoconfigure/FlywayMigrationInitializer.html)
