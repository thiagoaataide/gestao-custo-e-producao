# ADR-018: Transação ACID por caso de uso

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `AD-012` and `AD-017` in `.specs/STATE.md`

## Context

Os fluxos da V0 executam várias alterações relacionadas. Por exemplo, uma
compra pode criar o registro da compra, seus itens, um lote e a entrada
correspondente no estoque. Um apontamento de produção pode registrar a
produção, o consumo dos insumos, as movimentações e os valores realizados.

Persistir somente parte dessas alterações produziria um estado operacional
inconsistente. Como a V0 utiliza um único PostgreSQL e não possui necessidade
de transações distribuídas, a consistência deve ser garantida por uma
transação local do banco.

## Decision

Cada caso de uso que altera o estado do domínio será executado dentro de uma
única transação ACID local do PostgreSQL:

- todas as alterações pertencentes ao caso de uso entram na mesma transação;
- a transação só será confirmada depois que todas as validações e gravações
  forem concluídas com sucesso;
- se qualquer etapa falhar, a transação será revertida integralmente;
- em caso de falha, nenhum estado parcial do caso de uso ficará persistido no
  banco;
- alterações feitas dentro de uma transação não serão observáveis como estado
  parcial por outras transações;
- o contexto de tenant definido no ADR-017 acompanhará a transação;
- não será utilizado JTA ou outro mecanismo de transação distribuída na V0.

A fronteira transacional será definida no caso de uso, e não em cada operação
isolada de repositório. Módulos diferentes do monólito poderão participar da
mesma transação local quando fizerem parte do mesmo caso de uso, sem eliminar
suas fronteiras de responsabilidade.

Esta garantia cobre os dados transacionais do domínio no PostgreSQL. Efeitos
externos, como armazenamento de arquivos no Supabase Storage ou envio de
mensagens, não participam atomicamente da transação do banco e deverão ter
tratamento explícito quando fizerem parte de um fluxo.

## Examples in V0

### Compra e entrada de estoque

O registro da compra, os itens, o lote e a movimentação de entrada devem ser
confirmados juntos. Se a movimentação não puder ser criada, a compra também
não fica persistida como concluída.

### Produção e consumo

O apontamento da produção, o consumo dos lotes, as movimentações de estoque e
os valores realizados devem ser confirmados juntos. Se o consumo falhar, o
apontamento não fica parcialmente persistido.

### Cancelamento e destinação

As alterações do pedido, da produção disponível e da destinação devem respeitar
a mesma fronteira quando fizerem parte de uma única operação do usuário.

## Alternatives considered

### Persistir cada etapa separadamente

Não adotado. Uma falha intermediária poderia deixar compra sem estoque,
consumo sem produção ou destinação sem a quantidade correspondente.

### JTA ou transação distribuída

Não adotado na V0. A aplicação possui um único banco e não tem requisito de
coordenação transacional entre múltiplos recursos transacionais.

### Corrigir inconsistências posteriormente

Não adotado como mecanismo principal. Reconciliação pode ser útil para
diagnóstico, mas não substitui a atomicidade da operação original.

## Consequences

### Positive

- O banco não fica com estados parciais após falhas de negócio ou infraestrutura.
- Estoque, produção, compras, consumo e custos permanecem coerentes dentro de
  cada operação.
- A regra é compatível com o uso do PostgreSQL e com o suporte transacional do
  Spring.
- O modelo evita complexidade de JTA, mensageria e coordenação distribuída na
  V0.

### Negative

- Casos de uso muito grandes podem manter transações abertas por mais tempo e
  precisarão de uma fronteira bem definida.
- Efeitos externos ao banco não terão rollback automático junto com os dados
  transacionais.
- Testes deverão validar tanto o sucesso completo quanto a reversão integral
  em falhas intermediárias.

## References

- [PostgreSQL transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html)
- [Spring Framework transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
