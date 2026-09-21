# Contexto da F-00 — Fundação técnica e acesso seguro

**Coletado:** 21 de setembro de 2026
**Especificação:** `.specs/features/f-00-foundation-access/spec.md`
**Status:** Decisões confirmadas — design aprovado e pronto para tasks

## Validação ambiental da T1 — 21 de setembro de 2026

- O projeto Supabase alvo foi identificado como **Gestão de Custos e
  Produção**, `project ref` `clcgyhsjbenywugcagjo`, região `us-east-1`, status
  `ACTIVE_HEALTHY`.
- O banco informado pelo projeto é PostgreSQL 17.6.1.166 e não há migrations
  registradas no projeto.
- O issuer esperado nos tokens Supabase é
  `https://clcgyhsjbenywugcagjo.supabase.co/auth/v1` e o endpoint JWKS é
  `https://clcgyhsjbenywugcagjo.supabase.co/auth/v1/.well-known/jwks.json`.
- O projeto usa chave assimétrica ECC P-256 (`ES256`). O endpoint JWKS exige o
  header `apikey`; há uma chave publicável ativa no projeto para essa leitura.
  Essa chave não substitui a validação criptográfica do JWT e não será usada
  como identidade ou autorização de domínio.
- A sessão administrativa de consulta usa `postgres`, com login e
  `BYPASSRLS=true`. Ela permanece reservada para administração e migrations,
  não para operações normais da aplicação.
- As roles `anon` e `authenticated` existentes não possuem login; `service_role`
  possui `BYPASSRLS`.
- A role de runtime `app_runtime` foi criada no projeto com login habilitado,
  sem superuser, sem criação de roles ou bancos, sem replicação e sem
  `BYPASSRLS`.
- Não foi executado DDL remoto, não foi aplicada migration e nenhum segredo foi
  salvo no repositório.

**Resultado:** os pré-requisitos externos da T1 foram validados. A
implementação não deve usar `postgres`, `service_role`, `JWT_SECRET` ou
`sb_secret` como credencial normal de operações de tenant.

## Limite da feature

A F-00 entrega a base executável de autenticação, resolução segura de tenant,
isolamento por aplicação e RLS, migrations no startup, transações ACID locais e
um shell mínimo de acesso. Ela não cria tenants, não administra memberships e
não implementa as operações de produção.

## Decisões registradas

### Identidade e tenant

- O Supabase Auth autentica a identidade externa.
- A aplicação não trata o token como fonte autoritativa do tenant.
- O backend consulta o vínculo persistido e resolve exatamente um tenant para o
  usuário autenticado.
- A V0 não oferece alternância entre tenants.
- Usuário autenticado sem membership ativa é bloqueado com mensagem de acesso
  não provisionado.
- Vínculo com mais de uma membership ativa é tratado como inconsistência e
  bloqueia o acesso até correção administrativa.

### Isolamento

- A aplicação aplica autorização e contexto de tenant.
- O PostgreSQL aplica RLS como segunda barreira.
- O `tenant_id` recebido de tela, URL ou requisição não substitui o contexto
  resolvido no backend.
- Contexto de tenant é estabelecido por transação e não pode vazar para outra
  transação em uma conexão reutilizada.

### Persistência e inicialização

- Flyway é o executor e histórico único das migrations.
- Migrations pendentes executam na inicialização.
- Falha de migration impede o startup considerado bem-sucedido.
- Casos de uso de domínio usam transação ACID local.
- JTA e transação distribuída não fazem parte da V0.

### Contexto do tenant no RLS

- A aplicação grava o tenant resolvido com `set_config` configurado como local
  para a transação.
- As policies usam `current_setting` para comparar o tenant da linha com o
  contexto da transação.
- O contexto deve ser estabelecido na mesma conexão JDBC que executará as
  consultas de domínio.
- A credencial de runtime não pode possuir `BYPASSRLS` nem ser a credencial
  `service_role` do Supabase.
- A credencial usada pelo Flyway pode ter privilégios de migration, mas não é
  utilizada por operações de tenant.

### Interface inicial

- A F-00 exibe somente o estado de autenticação/provisionamento e o shell
  mínimo.
- A interface das operações de negócio começa nas features seguintes.
- A mensagem de bloqueio não revela dados de outros tenants.

## Flexibilidade do design

O design pode escolher a forma concreta de integração entre Spring Security,
Supabase Auth, contexto transacional e RLS, desde que preserve os critérios de
aceitação. Também pode definir o formato de logs e a aparência do shell, sem
alterar as regras de acesso ou criar novas capacidades na F-00.

## Áreas não discutidas → assunções

- O formato exato da mensagem e da tela de acesso bloqueado fica para o design
  da interface, mantendo a informação mínima de "acesso não provisionado".
- O formato de auditoria e logs de falha fica para o design, sem registrar
  tokens, segredos ou dados de outros tenants.
- A decisão de hospedagem permanece fora desta feature; a fundação não depende
  de um provedor específico.

## Ideias adiadas

- Criação de tenant, convite, membership e administração de plataforma: F-01.
- Papéis e permissões complexos: fora da V0 conforme PRD.
- Alternância de tenants: versão posterior, caso a decisão de domínio mude.
- Gestão de dados operacionais: F-02 em diante.
