# ADR-023: Inversão de dependência entre aplicação, domínio e adapters

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `ADR-015`, `ADR-016`, `ADR-017` and `ADR-018`

## Context

O produto utiliza um monólito modular, mas possui fronteiras importantes entre
autenticação, resolução de tenant, persistência, RLS e operação do domínio. A
implementação atual já possui um adapter de Resource Server para o Supabase,
enquanto as próximas tarefas adicionarão persistência de identidade,
membership e resolução de acesso.

Se os casos de uso dependerem diretamente de JPA, Spring Security, JDBC ou
classes concretas de infraestrutura, a regra de negócio ficará acoplada a
detalhes técnicos. Isso dificultará testes unitários, a evolução da
autenticação e a substituição de adapters sem alterar o domínio.

## Decision

As camadas de domínio e aplicação dependerão de abstrações que expressem suas
necessidades. Adapters de infraestrutura dependerão dessas abstrações e as
implementarão.

### Direção das dependências

- casos de uso e políticas de domínio dependem de portas, não de implementações
  concretas;
- adapters de persistência implementam portas de saída definidas pela camada
  interna;
- adapters de autenticação convertem credenciais externas em tipos mínimos do
  domínio, como `ExternalSubject`;
- o adapter de RLS implementa a porta necessária para aplicar o contexto
  transacional, sem expor JDBC aos casos de uso;
- a configuração Spring realiza a composição das implementações no limite da
  aplicação, usando injeção de dependência;
- não será usado service locator, acesso estático a beans ou instanciação de
  infraestrutura dentro dos casos de uso.

As portas devem representar uma necessidade real do caso de uso. O projeto
não criará interfaces apenas para cada classe ou para esconder classes sem
uma fronteira de substituição, teste ou integração.

### Aplicação na F-00

O resolvedor de acesso deverá consultar portas de identidade, membership e
tenant. Ele não deverá conhecer entidades JPA, `EntityManager`, SQL ou detalhes
do schema. Os adapters em `platform/identity/persistence` implementarão essas
portas.

O Resource Server permanece um adapter de entrada. Depois da validação
criptográfica, ele entrega somente `ExternalSubject` para a camada de acesso.
Claims de tenant, `user_metadata` e authorities do provedor não atravessam a
fronteira como autorização de domínio.

O limite transacional continua no caso de uso, conforme o `ADR-018`. Os
repositórios e adapters participam da transação, mas não definem sozinhos a
unidade transacional do negócio.

### Aplicação nas features operacionais

Cada bounded context deverá expor casos de uso e portas próprias. Um módulo
não poderá acessar diretamente o repository ou a entidade de outro bounded
context para contornar sua aplicação ou autorização.

Essa decisão não exige módulos Maven separados. A separação será aplicada por
pacotes, contratos, regras de dependência e testes no monólito modular.

## Alternatives considered

### Injetar implementações JPA diretamente nos serviços

Não adotado. A camada de aplicação ficaria acoplada à persistência e os
testes dependeriam de infraestrutura mesmo quando a regra não exigisse banco.

### Criar uma interface para cada classe

Não adotado. Interfaces sem uma fronteira real aumentariam o ruído e não
produziriam desacoplamento útil.

### Extrair os adapters para microserviços

Não adotado na V0. A necessidade atual é de separação de responsabilidades no
monólito, não de comunicação entre processos ou transações distribuídas.

## Consequences

### Positive

- casos de uso podem ser testados com fakes ou stubs das portas;
- mudanças de JPA, autenticação ou integração externa ficam concentradas nos
  adapters;
- a aplicação preserva a fronteira entre autenticação e autorização de tenant;
- o domínio permanece compatível com o monólito modular definido para a V0.

### Negative

- será necessário definir e manter contratos de portas;
- uma porta mal desenhada pode vazar detalhes de infraestrutura para a camada
  interna;
- o wiring inicial exige configuração explícita dos adapters.

## References

- [Spring Framework dependency injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [ADR-015: Separação entre administração da plataforma e operação do tenant](ADR-015-platform-administration-context.md)
- [ADR-018: Transação ACID por caso de uso](ADR-018-acid-transaction-per-use-case.md)
