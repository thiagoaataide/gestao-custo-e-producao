# ADR-024: CQRS lógico no monólito modular

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `ADR-015`, `ADR-018` and `ADR-023`

## Context

O produto possui operações que alteram estado e consultas que apresentam
planejamento, estoque, custos e indicadores. Esses fluxos têm necessidades
diferentes: comandos precisam validar regras e preservar atomicidade, enquanto
consultas precisam retornar informações apropriadas para leitura sem se tornar
um caminho alternativo de mutação.

A V0, porém, utiliza um monólito modular, um PostgreSQL principal e transações
locais. Não há requisito para mensageria, replicação de leitura, consistência
eventual ou processamento distribuído.

## Decision

O projeto adotará CQRS lógico na camada de aplicação. Comandos e consultas
terão contratos, handlers e portas de saída distintos, mas permanecerão no
mesmo monólito e poderão usar o mesmo PostgreSQL.

### Commands

Commands representam intenções que podem alterar o estado do domínio. Cada
command:

- possui um caso de uso explícito;
- valida a autorização e o contexto de tenant antes de alterar dados;
- executa as alterações dentro da fronteira ACID definida no `ADR-018`;
- usa agregados e portas de escrita apropriados;
- não recebe `tenant_id` do cliente como fonte autoritativa;
- pode retornar um identificador ou resultado mínimo da operação, sem se tornar
  uma consulta geral.

Exemplos dentro do escopo da V0 incluem registrar pedido, criar planejamento
de produção, registrar compra, apontar produção e consumo, registrar
movimentação e registrar destinação de uma produção cancelada.

### Queries

Queries representam leituras sem alteração de estado. Cada query:

- possui um contrato de leitura explícito;
- aplica autenticação, membership, tenant e RLS da mesma forma que um command;
- usa portas de leitura ou projeções adequadas ao resultado solicitado;
- não executa mutações como efeito colateral;
- pode otimizar sua consulta sem obrigar o modelo de leitura a reproduzir o
  modelo de escrita.

Exemplos dentro do escopo da V0 incluem consultar cardápio, pedidos,
planejamento, necessidade de insumos, estoque, compras, CMV, margem e
indicadores.

### Limites da decisão

O CQRS da V0 é lógico e síncrono. Esta decisão não introduz:

- banco separado para leitura;
- mensageria ou barramento de comandos;
- event sourcing;
- projeções assíncronas;
- consistência eventual entre modelos;
- duplicação de dados para simular escala futura;
- JTA ou transação distribuída.

As implementações podem começar com o mesmo PostgreSQL e adapters JPA
separados por intenção. Uma necessidade futura de leitura especializada ou
processamento assíncrono exigirá uma decisão arquitetural própria.

### Relação com inversão de dependência

Commands e queries dependem de portas de aplicação. Adapters de escrita e
leitura implementam essas portas. A camada de domínio não conhece a forma
como uma consulta é executada, e a camada de apresentação não acessa
diretamente repositories para contornar os handlers.

## Alternatives considered

### Serviços CRUD sem separação de intenção

Não adotado como padrão. Misturar leitura e mutação nos mesmos serviços
facilitaria atalhos que ignoram regras, autorização ou fronteiras
transacionais.

### CQRS distribuído com mensageria e banco de leitura

Não adotado na V0. Adicionaria infraestrutura, consistência eventual e
operações de recuperação sem uma necessidade atual do produto.

### Event sourcing

Não adotado. O domínio precisa rastrear movimentações, compras, produção,
consumo, perdas e destinações, mas isso não exige que eventos sejam a fonte
primária de estado na V0.

## Consequences

### Positive

- cada fluxo deixa explícito se consulta ou altera o domínio;
- comandos podem preservar a fronteira ACID sem contaminar consultas;
- consultas podem evoluir para projeções específicas sem reescrever os
  comandos;
- o desenho mantém uma trilha de evolução para escala sem antecipar
  infraestrutura fora da V0.

### Negative

- haverá mais contratos e classes de aplicação do que em um serviço CRUD único;
- alguns casos de uso precisarão de modelos de entrada e saída distintos;
- handlers e portas precisarão manter nomes e responsabilidades claros para
  não se tornarem apenas wrappers de repositories.

## References

- [Spring Framework transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/)
- [ADR-018: Transação ACID por caso de uso](ADR-018-acid-transaction-per-use-case.md)
- [ADR-023: Inversão de dependência entre aplicação, domínio e adapters](ADR-023-dependency-inversion-and-ports.md)
