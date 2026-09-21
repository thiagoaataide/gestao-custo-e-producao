# ADR-021: Flyway Community sem Flyway Teams na V0

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decision:** `AD-020` in `.specs/STATE.md`

## Context

O Flyway foi escolhido como executor único das migrations para preservar a
portabilidade do banco. A V0 também precisa manter custo zero e aceita as
limitações operacionais desse estágio.

A documentação oficial do Flyway informa que o comando `undo` pertence ao
Flyway Teams. Portanto, exigir esse comando criaria uma dependência de licença
que não é necessária para manter o histórico, aplicar migrations e controlar o
schema da V0.

## Decision

A V0 utilizará o Flyway Community, sem licença ou dependência do Flyway Teams:

- o Flyway Community será responsável por aplicar e registrar as migrations
  versionadas;
- scripts de reversão continuarão versionados no Git conforme o ADR-019;
- o comando `flyway undo` não será usado como requisito da V0;
- se uma migration falhar durante a aplicação, a execução deverá usar o
  rollback transacional suportado pelo banco e pelo Flyway quando aplicável;
- para uma migration já aplicada, a reversão será uma operação explícita,
  revisada e controlada, usando o script de reversão mantido no repositório ou
  uma nova migration corretiva;
- operações destrutivas exigirão backup ou outra estratégia de recuperação
  compatível com o risco antes da execução;
- erro da aplicação não dispara rollback automático de schema.

Uma futura adoção do Flyway Teams ou de outro procedimento comercial exigirá
uma nova decisão arquitetural. Isso não faz parte do escopo da V0.

## Consequences

### Positive

- A estratégia mantém o custo zero definido para a V0.
- O projeto continua com histórico, validação e aplicação de migrations.
- O schema não depende de um recurso comercial para ser mantido.
- A reversão continua possível sem confundir erro de aplicação com rollback de
  banco.

### Negative

- A execução de reversões exige um procedimento operacional explícito.
- O time precisa testar os scripts de reversão separadamente.
- Algumas migrations serão mais seguras de corrigir com uma nova migration
  forward do que de desfazer diretamente.

## References

- [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
- [Flyway undo](https://documentation.red-gate.com/flyway/reference/commands/undo)
