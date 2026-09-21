# ADR-015: Separação entre administração da plataforma e operação do tenant

- **Status:** Accepted
- **Date:** 2026-09-21
- **Related project decision:** `AD-015` in `.specs/STATE.md`

## Context

O produto será evoluído como uma plataforma SaaS. A aplicação precisa criar e
provisionar tenants, convites e vínculos de usuários, mas o administrador da
plataforma não deve consultar os dados operacionais dos clientes.

O mesmo sistema também precisa executar a operação de cada tenant, incluindo
pedidos, cardápio, produção, estoque, custos e indicadores. Misturar essas
responsabilidades em um único contexto de autorização criaria risco de acesso
indevido e dificultaria a aplicação do princípio do menor privilégio.

Autenticação e isolamento de tenant são responsabilidades diferentes. O
Supabase Auth autentica o usuário, mas a aplicação ainda precisa aplicar o
contexto do tenant em cada operação de negócio.

## Decision

O monólito modular terá dois bounded contexts explícitos:

### Platform Administration

Responsável por:

- criar e administrar tenants como metadados da plataforma;
- criar convites e acompanhar seu ciclo de vida;
- administrar memberships e papéis de acesso;
- registrar auditoria das ações administrativas;
- integrar-se ao Supabase Auth para operações administrativas de usuários.

O contexto não poderá consultar ou alterar pedidos, estoque, produção, custos,
receitas ou qualquer outro dado operacional pertencente a um tenant.

### Tenant Operations

Responsável pela operação de um tenant autenticado e autorizado:

- insumos e fichas técnicas;
- cardápios e pedidos;
- planejamento de produção;
- compras, estoque e movimentações;
- CMV, margem e indicadores.

Toda operação desse contexto deve ser executada dentro de um tenant resolvido
a partir da identidade autenticada e de um vínculo ativo no domínio.

Os dois contextos permanecem dentro do mesmo monólito modular na V0. A
separação será feita por módulos, casos de uso, políticas de autorização e
contratos internos explícitos. Não será criado um microserviço de administração
da plataforma nesta fase.

## Access model

- `PLATFORM_ADMIN` pode operar somente o contexto de administração da
  plataforma.
- Um usuário sem membership ativa não acessa o contexto de operação.
- Um usuário de tenant acessa somente os dados do tenant associado ao seu
  membership ativo.
- Impersonação de usuários de tenant não existe por padrão.
- Se uma mesma pessoa precisar administrar a plataforma e operar um tenant,
  o acesso operacional deverá ser concedido por uma identidade ou membership
  separada; o papel de plataforma, sozinho, não concede acesso aos dados do
  tenant.

## Provisioning flow

1. O administrador da plataforma autentica-se pelo Supabase Auth.
2. O administrador cria o tenant pela administração da plataforma.
3. O administrador registra um convite para o e-mail e o papel inicial do
   usuário do tenant.
4. O usuário aceita o convite e conclui sua autenticação no Supabase Auth.
5. A aplicação ativa o membership correspondente ao convite.
6. O usuário passa a acessar somente o tenant autorizado.

O envio do e-mail pode ser implementado depois do primeiro fluxo de
provisionamento, mas o conceito de convite e seu estado devem pertencer ao
contexto de administração da plataforma.

## Alternatives considered

### Usuário autenticado cria o próprio tenant

Não adotada. Entrega pouco controle sobre o ciclo de vida dos tenants e coloca
uma responsabilidade de plataforma nas mãos de qualquer usuário autenticado.

### Microserviço de administração na V0

Não adotado. A separação de bounded contexts dentro do monólito atende ao
isolamento de responsabilidade sem introduzir, neste momento, complexidade de
deploy, comunicação, observabilidade e consistência distribuída.

### Administrador da plataforma com acesso implícito a todos os tenants

Não adotada. Esse modelo conflita com o princípio de menor privilégio e com a
necessidade de impedir o acesso operacional da plataforma aos dados dos
clientes.

## Consequences

### Positive

- O ciclo de vida de tenants fica separado da operação dos clientes.
- A aplicação pode evoluir para múltiplos tenants e múltiplos produtos.
- O papel de administrador da plataforma não implica acesso aos dados
  operacionais.
- Um futuro microserviço de control plane pode ser extraído sem redefinir o
  domínio principal.
- Convites, memberships e auditoria têm um lugar explícito no modelo.

### Negative

- A V0 precisa de uma capacidade mínima de administração da plataforma.
- O modelo de autorização terá mais de um contexto e mais de um conjunto de
  papéis.
- Uma pessoa que precise exercer os dois papéis deverá ter uma identidade ou
  membership operacional separado.
- A aplicação deve evitar dependências diretas entre o contexto de plataforma
  e os dados operacionais dos tenants.

## Security and privacy guardrails

- A chave secreta do Supabase usada para operações administrativas permanece
  exclusivamente no backend ou em ambiente confiável.
- Dados de tenant não são carregados por casos de uso do contexto de
  administração da plataforma.
- A aplicação registra auditoria de criação de tenants, convites e alterações
  de memberships.
- O armazenamento de dados pessoais do convite deve ser limitado ao necessário
  para o provisionamento.
- Isolamento de tenant deve ser aplicado independentemente de o usuário estar
  autenticado.
- Esta decisão apoia privacidade e menor privilégio, mas não substitui a
  análise jurídica e os demais controles necessários para conformidade com a
  LGPD.

## References

- [Supabase Auth users and invitations](https://supabase.com/docs/guides/auth/users)
- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [AWS SaaS tenant isolation](https://docs.aws.amazon.com/whitepapers/latest/saas-architecture-fundamentals/tenant-isolation.html)
- [AWS control plane tenant management](https://docs.aws.amazon.com/prescriptive-guidance/latest/patterns/manage-tenants-across-multiple-saas-products-on-a-single-control-plane.html)
