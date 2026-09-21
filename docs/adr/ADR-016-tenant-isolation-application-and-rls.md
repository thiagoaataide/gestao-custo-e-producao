# ADR-016: Isolamento de tenant em duas camadas

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related project decision:** `AD-016` in `.specs/STATE.md`

## Context

O produto possui dados operacionais de múltiplos tenants e utiliza o Supabase
Auth somente para autenticação. A aplicação precisa resolver o tenant a partir
da identidade autenticada e do membership ativo antes de executar qualquer
caso de uso.

Confiar apenas nos filtros aplicados pelo código da aplicação deixaria o
isolamento dependente de todas as consultas futuras lembrarem de aplicar o
tenant correto. Uma consulta, relatório ou caminho administrativo implementado
incorretamente poderia expor dados de outro tenant.

Ao mesmo tempo, o banco não deve substituir as regras de negócio da aplicação.
Papéis, casos de uso, contexto de plataforma e validações operacionais
continuam pertencendo ao domínio da aplicação.

## Decision

O isolamento de tenant da V0 será aplicado em duas camadas complementares:

### Aplicação

- O Spring valida a identidade autenticada recebida do Supabase Auth.
- A aplicação resolve o membership ativo e o tenant autorizado.
- Casos de uso, comandos, consultas e indicadores recebem um contexto de
  tenant resolvido pelo servidor.
- O tenant não será aceito como uma autorização fornecida livremente pelo
  cliente.
- A aplicação continua responsável por papéis, permissões, regras de negócio
  e pela separação entre Platform Administration e Tenant Operations.

### PostgreSQL com RLS

- Tabelas que armazenam dados pertencentes a tenants terão Row Level Security
  habilitado.
- As políticas impedirão leitura, inserção, alteração ou exclusão de linhas de
  outro tenant.
- As políticas serão uma segunda barreira de segurança, e não a única
  implementação de autorização.
- Tabelas de metadados da plataforma terão regras próprias e não concederão ao
  administrador da plataforma acesso implícito aos dados operacionais.

A implementação da fundação deverá provar que a conexão usada pela aplicação
está efetivamente sujeita às políticas RLS. Uma conexão privilegiada que
bypasse RLS não poderá ser considerada evidência de isolamento; caso seja
necessária para uma operação confiável, seu uso ficará restrito a casos
explicitamente definidos, auditados e fora dos fluxos comuns de tenant.

## Security boundaries

- O vínculo entre identidade autenticada, membership e tenant é mantido no
  domínio da aplicação.
- Identificadores de tenant enviados pelo cliente não substituem o tenant
  resolvido no servidor.
- Dados de autorização não serão derivados de campos editáveis pelo usuário
  no perfil do Supabase Auth.
- Chaves privilegiadas do Supabase permanecem somente no backend ou em outro
  ambiente confiável.
- A aplicação não usará RLS para permitir que um `PLATFORM_ADMIN` consulte
  dados operacionais de um tenant.
- Cada política RLS deverá ser validada para os caminhos permitidos e negados,
  incluindo leitura, inserção, atualização e exclusão quando aplicável.

## Alternatives considered

### Isolamento somente na aplicação

Não adotado. É mais simples inicialmente, mas transforma qualquer consulta
esquecida ou caminho futuro em um possível vazamento entre tenants.

### Isolamento somente por RLS

Não adotado. RLS não substitui a autorização de casos de uso, os papéis da
plataforma, as regras de negócio ou a validação do fluxo operacional.

### Um banco ou schema separado por tenant

Não adotado na V0. O isolamento lógico com aplicação e RLS atende ao primeiro
produto sem introduzir uma estratégia de provisionamento e operação de bancos
separados.

## Consequences

### Positive

- Uma falha de filtro na aplicação encontra uma segunda barreira no banco.
- O modelo preserva a fronteira entre autenticação e autorização de tenant.
- A estratégia é compatível com a evolução para uma plataforma SaaS.
- O isolamento pode ser testado independentemente dos casos de uso.
- O princípio de menor privilégio fica explícito no desenho da solução.

### Negative

- A fundação terá de definir como a identidade e o tenant resolvidos pela
  aplicação serão reconhecidos pelas políticas RLS.
- Consultas, relatórios, views e operações administrativas exigirão revisão
  específica para não contornar as políticas.
- A V0 precisará de testes de acesso permitido e negado entre tenants.
- O uso de conexões privilegiadas ou funções que ignorem RLS aumenta o risco e
  deverá ser excepcional.

## References

- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [PostgreSQL row security policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [Supabase Auth users and invitations](https://supabase.com/docs/guides/auth/users)
