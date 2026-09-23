# F-01 — Provisionamento da plataforma

**Status:** Confirmada e verificada em 22 de setembro de 2026
**Escopo:** V0
**Fonte:** `docs/PRD-V0.md`, `docs/ROADMAP-V0.md`, ADR-015 e decisões
registradas durante a especificação

## Problema

A F-00 autentica identidades e bloqueia acessos sem membership, mas ainda não
existe um fluxo controlado para criar tenants, convidar usuários e ativar seus
vínculos operacionais. Sem essa capacidade, o acesso seguro da V0 dependeria de
associações manuais fora do produto e não haveria uma fronteira clara entre a
administração da plataforma e a operação dos tenants.

A F-01 cria essa capacidade dentro do monólito modular, sem permitir que a
administração da plataforma consulte ou opere dados de pedidos, insumos,
estoque, produção, custos ou indicadores dos tenants.

## Objetivos

- [ ] Provisionar o primeiro `PLATFORM_OWNER` por bootstrap controlado e único.
- [ ] Permitir que a administração da plataforma crie e gerencie tenants,
      convites e memberships sem acessar dados operacionais.
- [ ] Permitir a ativação segura de um `TENANT_USER` por convite aceito ou
      associação manual confirmada pelo próprio usuário.
- [ ] Registrar e consultar a auditoria mínima das ações administrativas.

## Fora do escopo

| Item | Motivo |
| --- | --- |
| Autoatendimento para qualquer usuário criar tenant | O tenant só pode ser criado pelo contexto de Platform Administration. |
| Acesso da plataforma a dados operacionais | Contraria o ADR-015 e o princípio de menor privilégio. |
| Papéis operacionais além de `TENANT_USER` | Autorização granular e administração avançada estão fora da V0. |
| Segundo `PLATFORM_OWNER` ou transferência de ownership | A V0 terá um único owner fixo: o usuário responsável pelo bootstrap. |
| Portal do cliente ou credenciais de clientes | Cliente de pedido não é usuário da aplicação na V0. |
| Dependência obrigatória de provedor de e-mail | O convite por link deve funcionar sem envio automático. |
| Exclusão física de tenants, convites ou auditoria | O histórico administrativo deve permanecer rastreável. |
| Operações de tenant como insumos, pedidos ou estoque | Pertencem às features operacionais posteriores. |

## Assumptions & Open Questions

As ambiguidades de produto relevantes foram resolvidas durante a especificação.

| Decisão | Padrão adotado | Racional | Confirmada? |
| --- | --- | --- | --- |
| Primeiro administrador | O primeiro `PLATFORM_OWNER` é criado por bootstrap controlado e único. | Evita o ciclo de convite sem um administrador inicial e impede criação livre de tenants. | Sim |
| Identidades do responsável | A conta de plataforma e a conta operacional do responsável são identidades Supabase distintas. | Mantém menor privilégio e separa administração da plataforma de operação de tenant. | Sim |
| Papéis de plataforma | Existe um único `PLATFORM_OWNER`; ele pode criar e remover `PLATFORM_ADMIN`. | Preserva um responsável principal sem criar uma hierarquia maior na V0. | Sim |
| Papel operacional | A V0 usa somente `TENANT_USER`. | Evita autorização granular antes de haver necessidade real. | Sim |
| Tenant sem usuário | Um tenant pode existir sem membership ativa. | Permite preparar o tenant antes do convite. | Sim |
| Membership sem tenant | Não é permitida. | Todo vínculo operacional precisa apontar para um tenant válido. | Sim |
| Convite | O convite é de uso único e expira em 24 horas. | Limita a exposição temporal do vínculo pendente. | Sim |
| Destinatário | O convite só é aceito por identidade autenticada com e-mail verificado igual ao e-mail do convite. | Reduz risco de associação com a identidade errada. | Sim |
| Canais do convite | Link copiado, e-mail opcional e associação manual de identidade existente usam o mesmo fluxo de convite. | Mantém um único modelo de domínio e permite operar sem provedor de e-mail. | Sim |
| Associação manual | Mesmo quando o administrador associa uma identidade existente, o usuário precisa autenticar e confirmar antes da ativação. | Evita ativação silenciosa ou vínculo com a conta errada. | Sim |
| Convite duplicado | Há no máximo um convite pendente por tenant e e-mail; reenvio invalida o anterior. | Evita múltiplos tokens concorrentes e ambiguidades de aceitação. | Sim |
| Usuário em outro tenant | A aceitação de um segundo convite é bloqueada enquanto houver membership ativa. | A V0 permite apenas um tenant por usuário. | Sim |
| Ciclo de vida do tenant | `ACTIVE` permite operação, `SUSPENDED` bloqueia operação temporariamente e `CLOSED` é terminal. | Diferencia pausa de encerramento e preserva os dados. | Sim |
| Ações de ciclo de vida | Apenas o `PLATFORM_OWNER` pode suspender, reativar ou fechar tenants. | Concentra ações de maior impacto no responsável principal. | Sim |
| Auditoria | Criação, ciclo de vida, convites e memberships geram eventos consultáveis por administradores da plataforma. | Permite rastreabilidade sem expor dados operacionais. | Sim |
| E-mail automático | O envio é opcional; falha de envio não desfaz o convite criado. | O link copiado permanece como caminho garantido. | Sim |

**Open questions:** nenhuma questão de produto permanece aberta para esta
especificação. O design deverá detalhar os contratos internos, a forma de
bootstrap e a proteção das operações sem alterar essas decisões.

## Modelo conceitual

- **Platform Role Assignment:** vínculo entre uma identidade externa e um
  papel de plataforma (`PLATFORM_OWNER` ou `PLATFORM_ADMIN`), sem tenant.
- **Tenant:** organização ou operação cadastrada na plataforma, com estado
  `ACTIVE`, `SUSPENDED` ou `CLOSED`.
- **Invitation:** intenção de vincular um e-mail a um tenant e ao papel
  `TENANT_USER`, com estado pendente, aceito, revogado ou expirado.
- **Membership:** vínculo operacional ativo, pendente ou revogado entre uma
  identidade e um tenant. A V0 permite no máximo uma membership ativa por
  identidade.
- **Administrative Audit Event:** registro da ação administrativa, seu ator,
  alvo, momento e resultado, sem dados operacionais do tenant.

## User Stories

### P1: Inicializar o administrador principal ⭐ MVP

**História:** Como responsável pelo produto, quero criar meu acesso de
`PLATFORM_OWNER` por um bootstrap controlado para iniciar a administração da
plataforma sem abrir a criação de tenants a qualquer usuário.

**Critérios de aceitação:**

1. **WHEN** o bootstrap inicial é executado com uma identidade válida **THEN**
   o sistema SHALL criar ou reconhecer exatamente um `PLATFORM_OWNER`.
2. **WHEN** o bootstrap já foi concluído **THEN** uma nova execução SHALL ser
   idempotente e SHALL não criar um segundo owner.
3. **WHEN** uma identidade comum se autentica **THEN** ela SHALL não receber
   papel de plataforma nem permissão para criar tenant automaticamente.
4. **WHEN** a identidade do owner acessa a plataforma **THEN** ela SHALL ver
   somente as capacidades administrativas autorizadas, sem acesso implícito a
   dados operacionais de tenants.
5. **WHEN** a identidade autenticada corresponde ao `owner-subject`
   configurado e ainda não possui acesso de plataforma **THEN** a tela inicial
   SHALL oferecer uma ação explícita para solicitar o bootstrap; identidades
   diferentes SHALL não receber essa ação, e o login sozinho SHALL não criar
   o papel.
6. **WHEN** o bootstrap explícito é concluído com sucesso **THEN** a aplicação
   SHALL permitir o acesso à administração da plataforma na mesma sessão.

**Teste independente:** executar o bootstrap, repetir a operação e verificar a
unicidade do owner e a ausência de acesso operacional implícito.

### P1: Criar e administrar tenants ⭐ MVP

**História:** Como administrador da plataforma, quero criar e acompanhar
tenants para preparar operações sem consultar seus dados de negócio.

**Critérios de aceitação:**

1. **WHEN** `PLATFORM_OWNER` ou `PLATFORM_ADMIN` cria um tenant com dados
   válidos **THEN** o sistema SHALL criar o tenant em `ACTIVE` sem exigir uma
   membership imediata.
2. **WHEN** um tenant é criado **THEN** o sistema SHALL permitir sua consulta
   como metadado de plataforma sem retornar pedidos, estoque, produção, custos,
   receitas ou indicadores.
3. **WHEN** `PLATFORM_OWNER` suspende um tenant **THEN** o sistema SHALL
   bloquear o acesso operacional e SHALL preservar seus dados e memberships.
4. **WHEN** `PLATFORM_OWNER` reativa um tenant suspenso **THEN** o sistema
   SHALL restaurar o acesso operacional dos vínculos que continuarem válidos.
5. **WHEN** `PLATFORM_OWNER` fecha um tenant **THEN** o sistema SHALL bloquear
   o acesso, preservar os dados e tornar o estado terminal na V0.
6. **WHEN** qualquer usuário tenta criar ou alterar tenant por uma operação de
   Tenant Operations **THEN** o sistema SHALL negar a operação.

**Teste independente:** criar um tenant sem usuários, verificar seu metadado,
suspender, reativar e fechar o tenant, confirmando as barreiras de acesso e a
preservação do histórico.

### P1: Convidar e ativar um usuário operacional ⭐ MVP

**História:** Como administrador da plataforma, quero convidar uma pessoa para
um tenant e ativar seu acesso somente depois que ela confirmar sua identidade.

**Critérios de aceitação:**

1. **WHEN** um administrador cria um convite válido **THEN** o sistema SHALL
   criar um convite pendente de uso único para o e-mail e tenant informados,
   com papel inicial `TENANT_USER` e validade de 24 horas.
2. **WHEN** já existe convite pendente para o mesmo tenant e e-mail **THEN** o
   sistema SHALL impedir duplicidade ou SHALL invalidar o convite anterior
   explicitamente quando a ação for um reenvio.
3. **WHEN** o destinatário acessa o convite com identidade autenticada e
   e-mail verificado correspondente **THEN** o sistema SHALL ativar a
   membership `TENANT_USER` e invalidar o convite para novo uso.
4. **WHEN** a identidade não está autenticada, não tem e-mail verificado ou o
   e-mail não corresponde **THEN** o sistema SHALL negar a aceitação e SHALL
   manter a membership não ativa.
5. **WHEN** o convite está expirado, revogado, aceito ou destinado a tenant
   fechado **THEN** o sistema SHALL negar sua aceitação.
6. **WHEN** o convite é enviado por e-mail e o envio falha **THEN** o sistema
   SHALL preservar o convite pendente e SHALL permitir o uso do link copiado.
7. **WHEN** o administrador associa manualmente uma identidade Supabase
   existente **THEN** o sistema SHALL manter a confirmação do próprio usuário
   como requisito para ativar a membership.
8. **WHEN** a identidade já possui membership ativa em outro tenant **THEN** o
   sistema SHALL bloquear a aceitação e SHALL preservar a membership atual.

**Teste independente:** criar convites por link, e-mail opcional e associação
manual; validar aceitação, expiração, revogação, duplicidade, e-mail divergente
e usuário já vinculado a outro tenant.

### P1: Administrar memberships e papéis de plataforma ⭐ MVP

**História:** Como `PLATFORM_OWNER`, quero administrar os acessos da plataforma
e os vínculos operacionais para corrigir ou revogar acessos sem entrar nos
dados de negócio.

**Critérios de aceitação:**

1. **WHEN** o owner cria um administrador de plataforma **THEN** o sistema
   SHALL criar um `PLATFORM_ADMIN` separado de qualquer membership operacional.
2. **WHEN** um `PLATFORM_ADMIN` tenta criar, remover ou alterar outro
   administrador de plataforma **THEN** o sistema SHALL negar a operação.
3. **WHEN** o owner revoga um administrador de plataforma **THEN** o sistema
   SHALL bloquear seu acesso administrativo sem alterar memberships de tenant.
4. **WHEN** um administrador revoga uma membership operacional **THEN** o
   sistema SHALL bloquear o acesso do usuário ao tenant sem apagar o histórico
   administrativo.
5. **WHEN** uma membership é ativada **THEN** ela SHALL sempre referenciar um
   tenant existente e SHALL usar somente o papel `TENANT_USER` na V0.

**Teste independente:** criar, revogar e consultar papéis de plataforma e
memberships, verificando que o owner é único e que o tenant user não recebe
permissões administrativas.

### P1: Auditar operações administrativas ⭐ MVP

**História:** Como administrador da plataforma, quero consultar o histórico das
ações administrativas para rastrear mudanças de acesso e ciclo de vida.

**Critérios de aceitação:**

1. **WHEN** uma ação de tenant, convite, membership ou papel de plataforma é
   concluída **THEN** o sistema SHALL registrar ator, ação, alvo, data/hora e
   resultado.
2. **WHEN** uma ação administrativa falha por validação ou autorização **THEN**
   o sistema SHALL registrar o resultado sem persistir uma alteração parcial.
3. **WHEN** um administrador consulta a auditoria **THEN** o sistema SHALL
   retornar somente metadados administrativos autorizados.
4. **WHEN** qualquer caso de uso de auditoria é executado **THEN** o sistema
   SHALL não retornar dados operacionais de tenants.

**Teste independente:** executar ações administrativas aprovadas e negadas,
consultar seus eventos e verificar completude mínima, atomicidade e ausência de
dados operacionais.

## Regras de negócio

- Existe exatamente um `PLATFORM_OWNER` na V0: a identidade definida no
  bootstrap inicial; ownership transferível está fora do escopo.
- `PLATFORM_OWNER` e `PLATFORM_ADMIN` são papéis de plataforma e não conferem
  acesso a dados operacionais.
- `TENANT_USER` é o único papel operacional da V0.
- Um tenant pode existir sem membership ativa; uma membership não pode existir
  sem tenant.
- Uma identidade pode ter no máximo uma membership ativa.
- Um tenant `SUSPENDED` ou `CLOSED` não permite operação; `CLOSED` é terminal.
- Convite aceito, revogado ou expirado não pode ser reutilizado.
- O convite é validado pelo e-mail verificado da identidade autenticada.
- Toda mutação administrativa deve ser transacional e gerar auditoria sem
  deixar alteração parcial persistida.
- A administração da plataforma não escolhe nem recebe contexto operacional de
  tenant para executar seus casos de uso.

## Casos de borda

- Bootstrap repetido não cria owner duplicado.
- Dois administradores tentam convidar o mesmo e-mail para o mesmo tenant ao
  mesmo tempo; somente um convite pendente deve permanecer válido.
- Convite expira durante a tentativa de aceitação; a membership não deve ser
  ativada.
- O e-mail do token não corresponde ao e-mail verificado; a aceitação deve ser
  negada sem revelar dados de terceiros.
- O tenant é suspenso ou fechado enquanto há convites pendentes; os convites
  não podem ser aceitos.
- A membership é revogada enquanto o token de autenticação ainda é válido; a
  próxima operação deve ser bloqueada pela F-00.
- Falha durante criação do tenant, convite, membership ou auditoria; a
  transação deve deixar o estado sem persistência parcial.
- O owner tenta revogar a própria identidade ou criar outro owner; a operação
  deve ser negada.

## Rastreabilidade de requisitos

| ID | Requisito | História | Origem | Status |
| --- | --- | --- | --- | --- |
| F01-01 | Bootstrap idempotente de um único owner | Inicializar | AD-014, AD-015 | Verified |
| F01-02 | Separar papéis de plataforma e membership de tenant | Inicializar | ADR-015 | Verified |
| F01-03 | Criar tenant sem membership obrigatória | Criar tenant | PRD 4.2 | Verified |
| F01-04 | Consultar apenas metadados de plataforma | Criar tenant | ADR-015 | Verified |
| F01-05 | Suspender e reativar tenant | Criar tenant | AD-015 | Verified |
| F01-06 | Fechar tenant de forma terminal e sem exclusão | Criar tenant | AD-015 | Verified |
| F01-07 | Criar convite único com validade de 24 horas | Convidar | Decisão F-01 | Verified |
| F01-08 | Aceitar convite somente com identidade verificada correspondente | Convidar | Decisão F-01 | Verified |
| F01-09 | Permitir link, e-mail opcional e associação manual no mesmo fluxo | Convidar | Decisão F-01 | Verified |
| F01-10 | Impedir membership ativa em dois tenants | Convidar | AD-003 | Verified |
| F01-11 | Manter somente `TENANT_USER` como papel operacional | Memberships | AD-003, PRD 10 | Verified |
| F01-12 | Administrar `PLATFORM_ADMIN` somente pelo owner | Memberships | Decisão F-01 | Verified |
| F01-13 | Revogar membership sem apagar histórico | Memberships | ADR-015 | Verified |
| F01-14 | Registrar auditoria administrativa mínima | Auditar | ADR-015 | Verified |
| F01-15 | Consultar auditoria sem dados operacionais | Auditar | ADR-015, LGPD | Verified |
| F01-16 | Preservar atomicidade e isolamento nas mutações | Todas | ADR-016 a ADR-023 | Verified |
| F01-17 | Permitir que a identidade configurada solicite explicitamente o bootstrap do owner na tela inicial | Inicializar | F01-01, AD-014, AD-015 | Automated verification passed — T17; browser UAT pending |

**Coverage:** 17 requisitos definidos e mapeados para tarefas. F01-01 a
F01-16 permanecem verificados; F01-17 passou pela verificação automatizada da
T17. A confirmação visual por UAT no ambiente publicado continua pendente.

## Critérios de sucesso

- [ ] O owner consegue iniciar a plataforma sem criar um segundo owner.
- [ ] Um tenant pode ser criado, suspenso, reativado e fechado sem acesso da
      plataforma aos seus dados operacionais.
- [ ] Um usuário convidado consegue ativar exatamente um vínculo operacional
      após autenticar e confirmar o e-mail.
- [ ] Convites expirados, duplicados, revogados ou incompatíveis são negados.
- [ ] Todas as ações administrativas relevantes são consultáveis em auditoria.
- [ ] Falhas não deixam tenant, convite, membership ou auditoria em estado
      parcialmente persistido.
