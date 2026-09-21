# ADR-017: Propagação do contexto de tenant para o RLS

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `AD-016` in `.specs/STATE.md` and
  [ADR-016](ADR-016-tenant-isolation-application-and-rls.md)

## Context

O ADR-016 definiu que a aplicação e o PostgreSQL participarão do isolamento
de tenant. Para que o RLS consiga aplicar essa segunda barreira, o banco precisa
receber um contexto de tenant confiável durante a execução das operações.

O identificador enviado pelo cliente não é uma fonte suficiente de autorização.
O contexto precisa ser derivado da identidade autenticada, do membership ativo
e das regras de acesso da aplicação.

Também não é suficiente declarar RLS se as operações normais da aplicação forem
executadas por uma conexão privilegiada que ignore suas políticas.

## Decision

Na V0:

- o backend valida a identidade autenticada recebida do Supabase Auth;
- o backend resolve o membership ativo e o tenant autorizado;
- o backend estabelece o contexto resolvido para cada transação operacional;
- a transação utiliza uma conexão e um papel de banco efetivamente sujeitos às
  políticas RLS;
- as políticas RLS usam esse contexto para permitir somente as linhas do tenant
  autorizado;
- uma transação operacional sem contexto de tenant válido falha de forma segura;
- o cliente não escolhe, substitui ou amplia o tenant autorizado;
- conexões privilegiadas que bypassam RLS não fazem parte do caminho normal de
  operações de tenant.

O mecanismo específico para estabelecer o contexto no PostgreSQL será definido
no design da fundação, considerando o ciclo de vida das transações Spring e a
forma de conexão escolhida. Essa definição técnica não poderá alterar as regras
de segurança desta decisão.

## Consequences

### Positive

- O tenant é derivado de uma decisão de autorização do backend, e não de uma
  entrada confiada do cliente.
- O RLS recebe um contexto limitado à transação e reduz o risco de vazamento
  entre requisições.
- O caminho normal de operação permanece protegido mesmo quando uma consulta
  não aplica corretamente o filtro de tenant.
- A regra é compatível com a separação entre Platform Administration e Tenant
  Operations.

### Negative

- A fundação precisa configurar e testar a propagação do contexto em todas as
  transações operacionais.
- O pool de conexões não pode permitir que o contexto de uma requisição vaze
  para outra.
- Consultas, relatórios, views, jobs e operações administrativas precisarão
  declarar explicitamente se são tenant-scoped ou platform-scoped.
- O caminho de acesso privilegiado, se existir, exigirá justificativa, auditoria
  e testes próprios.

## Alternatives considered

### Confiar no `tenant_id` enviado pelo cliente

Não adotado. O cliente pode adulterar esse valor e tentar acessar outro tenant.

### Usar somente o JWT diretamente nas políticas RLS

Não adotado como regra geral. A identidade do JWT autentica o usuário, mas o
membership e a autorização de tenant pertencem ao domínio da aplicação e podem
ser alterados sem que um token já emitido seja imediatamente renovado.

### Executar as operações com uma conexão privilegiada

Não adotado para o fluxo normal. Uma conexão que bypassa RLS remove a segunda
barreira justamente nos caminhos mais importantes da aplicação.

## References

- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [PostgreSQL row security policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
