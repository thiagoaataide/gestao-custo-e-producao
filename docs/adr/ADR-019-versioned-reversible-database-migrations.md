# ADR-019: Migrations de banco versionadas e reversíveis

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related decisions:** `AD-012` and `AD-018` in `.specs/STATE.md`

## Context

O PostgreSQL é parte central do domínio da V0. O schema, as tabelas de
tenant, as políticas RLS, os grants, as constraints e os índices precisam ser
reproduzíveis em cada ambiente e acompanhados pelo Git.

Alterações manuais diretamente no projeto remoto dificultariam a auditoria,
quebrariam a rastreabilidade do schema e poderiam deixar a aplicação e o
banco em versões diferentes.

Também precisamos de uma forma controlada de desfazer uma alteração quando uma
migration aplicada causar um problema. O rollback de uma migration já
confirmada é diferente do rollback automático da própria execução que falhou.

## Decision

O schema será mantido exclusivamente por migrations versionadas e commitadas
no repositório:

- toda alteração de schema será criada como uma migration identificada e
  ordenada;
- cada migration terá um script de aplicação e um script de reversão, quando a
  operação for tecnicamente reversível;
- tabelas, constraints, índices, RLS, policies, grants e demais objetos
  necessários ao domínio serão tratados pelas migrations;
- as migrations serão aplicadas e testadas em ambiente local antes de qualquer
  aplicação remota;
- o ambiente remoto não será alterado manualmente pelo Dashboard, SQL Editor ou
  Table Editor como parte do fluxo normal;
- a execução de uma migration que falhar deverá ser revertida pela transação
  da própria execução quando o recurso permitir;
- o script de reversão de uma migration já aplicada será executado de forma
  explícita, revisada e controlada, nunca automaticamente como reação genérica
  a qualquer erro da aplicação;
- migrations destrutivas deverão declarar o risco de perda de dados e possuir
  estratégia de recuperação compatível com o impacto.

A ferramenta de execução e o formato final dos arquivos serão definidos na
fundação, mas a ferramenta escolhida deverá preservar versionamento,
reprodutibilidade, testes e histórico no Git.

## Migration contract

Cada migration deverá documentar, no mínimo:

- objetivo da alteração;
- versão ou identificador ordenável;
- script de aplicação;
- script de reversão ou justificativa formal de irreversibilidade;
- impacto em dados existentes;
- impacto sobre RLS, grants, views e funções, quando aplicável;
- validação local executada;
- condição segura para reversão.

O script de reversão deve restaurar o schema anterior sem presumir que os dados
criados depois da migration possam ser recuperados por DDL. Quando houver
risco de perda de dados, a reversão exigirá backup, migração de dados ou outro
plano explícito antes da execução.

## Alternatives considered

### Alterar o banco remoto manualmente

Não adotado. A alteração não ficaria reproduzível nem alinhada ao histórico do
repositório.

### Manter somente scripts de aplicação

Não adotado. A ausência de uma estratégia de reversão aumentaria o risco de
recuperação improvisada após uma migration problemática.

### Reverter automaticamente qualquer migration após um erro da aplicação

Não adotado. Um erro de negócio posterior não significa que o schema deva ser
desfeito, e uma reversão automática pode causar perda de dados.

## Consequences

### Positive

- O schema pode ser reconstruído e auditado a partir do Git.
- O ambiente local e o Supabase remoto podem ser comparados por histórico.
- RLS, permissões e estrutura de dados evoluem junto com o código.
- A equipe possui um caminho explícito de recuperação para alterações
  problemáticas.
- A estratégia é compatível com deploys repetíveis e revisão por commit.

### Negative

- Toda alteração de banco exige disciplina documental e revisão.
- Migrations destrutivas exigem planejamento adicional e podem precisar de
  backup ou migração de dados.
- A ferramenta escolhida precisará ser validada para o fluxo Spring,
  PostgreSQL e Supabase.

## References

- [Supabase database migrations](https://supabase.com/docs/guides/deployment/database-migrations)
- [PostgreSQL transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html)
