# F-00 — Fundação técnica e acesso seguro

**Status:** Confirmada
**Escopo:** V0
**Fonte:** `docs/PRD-V0.md`, `docs/ROADMAP-V0.md` e ADR-015 a ADR-024
**Data:** 21 de setembro de 2026

## Problema

As features operacionais da V0 dependem de uma base que autentique o usuário,
resolva o tenant correto e impeça acesso indevido aos dados. Essa base também
precisa garantir que as migrations estejam aplicadas antes da aplicação ser
considerada disponível e que cada caso de uso preserve a atomicidade local.

Sem essa fundação, as features de insumos, compras, estoque, pedidos e
produção poderiam ser construídas sobre um contexto de acesso ou de persistência
inseguro e depois exigir mudanças incompatíveis no domínio.

## Objetivos

- [x] Disponibilizar a aplicação com o baseline tecnológico da V0 e migrations
      versionadas executadas na inicialização.
- [x] Autenticar por meio do Supabase Auth e separar a identidade externa da
      resolução de tenant no domínio.
- [x] Permitir acesso operacional somente a um usuário autenticado com uma
      membership ativa e inequívoca.
- [x] Aplicar isolamento em duas camadas: autorização da aplicação e RLS do
      PostgreSQL.
- [x] Estabelecer transações ACID locais para os casos de uso que alterarem o
      domínio.
- [x] Exibir um shell mínimo que diferencie acesso provisionado de acesso
      bloqueado, sem implementar funcionalidades operacionais nesta feature.

## Fora do escopo

| Item | Motivo |
| --- | --- |
| Criação de tenant | Pertence à F-01 — Provisionamento da plataforma. |
| Convite e ativação de membership | Pertence à F-01; F-00 apenas consome o vínculo ativo. |
| Administração de usuários e papéis complexos | Não faz parte da autorização da V0. |
| Cadastro público e recuperação de senha | F-00 autentica contas existentes; provisionamento de conta permanece fora do formulário de login. |
| Cadastro de insumos, compras, estoque ou produção | Pertence às features operacionais posteriores. |
| Envio efetivo de e-mail de convite | Pode ser detalhado na F-01 sem alterar o modelo de acesso. |
| Alternância entre tenants | A V0 vincula cada usuário a um único tenant. |
| Escolha de provedor de hospedagem | A fundação permanece neutra quanto ao provedor. |
| JTA, Spring Cloud ou transações distribuídas | Não existe requisito de transação distribuída na V0. |
| Upload e gestão de comprovantes | Pertence ao cadastro de compras e estoque da F-02. |

## Premissas e decisões

| Assunção ou decisão | Padrão adotado | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Identidade autenticada | O identificador externo do Supabase Auth é recebido pela aplicação e mapeado para o domínio; ele não define sozinho o tenant. | Mantém a fronteira entre autenticação e autorização de tenant definida no PRD e nos ADRs. | Sim, por ADR-015 e ADR-017 |
| Vínculo operacional | O acesso exige exatamente uma membership ativa para o usuário autenticado. | A V0 não permite alternância e cada usuário acessa um único tenant. | Sim, por AD-003 |
| Dados inconsistentes de vínculo | Se houver mais de uma membership ativa para o mesmo usuário, o acesso operacional será bloqueado até a correção administrativa; a aplicação não escolherá uma arbitrariamente. | Evita acesso imprevisível ou ao tenant errado. | Assunção de segurança |
| Usuário sem vínculo | Usuário autenticado sem membership ativa recebe mensagem de acesso não provisionado e não acessa dados operacionais. | O usuário não cria seu próprio tenant automaticamente. | Sim, por ADR-015 |
| Origem do tenant | O tenant é resolvido pelo backend a partir do vínculo persistido; `tenant_id` enviado pelo cliente não tem autoridade. | Evita que o cliente escolha ou atravesse tenants. | Sim, por ADR-017 |
| Momento do contexto | O backend estabelece o contexto resolvido em cada transação operacional e o elimina ao final da transação. | Reduz o risco de vazamento de contexto em conexões reutilizadas. | Sim, por ADR-017 |
| Proteção no banco | O acesso operacional usa uma conexão sujeita às policies RLS; qualquer contexto ausente ou incompatível deve negar a operação. | RLS é a segunda barreira, não uma justificativa para ignorar a autorização da aplicação. | Sim, por ADR-016 e ADR-017 |
| Migration com falha | Se uma migration pendente falhar, a aplicação não é considerada iniciada com sucesso nem aceita operação normal. | O schema disponível precisa corresponder ao código que foi iniciado. | Sim, por ADR-019 a ADR-022 |
| Transação de domínio | Cada caso de uso que altera o domínio usa uma transação ACID local; falha em qualquer etapa deixa a operação sem alteração parcial persistida. | A V0 não usa JTA nem transações distribuídas. | Sim, por ADR-018 |
| Idempotência | Não há requisito de idempotência de operação de negócio nesta feature, pois F-00 não cria compras, pedidos, produção ou outros dados operacionais. | Duplicidade e retry serão especificados na feature que criar cada mutação. | N/A para F-00 |
| Observabilidade | Falhas de autenticação, vínculo ambíguo, RLS e inicialização devem ser registráveis para diagnóstico, sem expor dados de outros tenants ao usuário. | A fundação precisa permitir diagnóstico seguro; o desenho definirá o formato. | Assunção de design |

**Questões abertas da F-00:** nenhuma questão de produto permanece aberta para
esta especificação. A escolha concreta de bibliotecas, configuração e formato
de logs pertence ao design e deve respeitar `AGENTS.md` e a documentação oficial.

## Histórias de usuário

### P1: Inicializar a aplicação com o schema compatível ⭐ MVP

**História:** Como responsável pela aplicação, quero que as migrations pendentes
sejam executadas antes da disponibilidade para garantir que o código não opere
contra um schema incompleto.

**Por que P1:** Todas as features da V0 dependem da fundação de persistência.

**Critérios de aceitação:**

1. **WHEN** a aplicação iniciar com configuração válida e migrations pendentes
   válidas **THEN** o executor de migrations SHALL aplicá-las antes de a
   aplicação ser considerada pronta para operação normal.
2. **WHEN** uma migration pendente falhar **THEN** o processo SHALL falhar a
   inicialização bem-sucedida e SHALL impedir o uso normal da aplicação.
3. **WHEN** a aplicação iniciar com o schema já atualizado **THEN** o executor
   SHALL validar o estado sem reaplicar migrations já concluídas.
4. **WHEN** a configuração obrigatória do banco estiver ausente ou inválida
   **THEN** a aplicação SHALL falhar a inicialização sem expor uma operação
   parcialmente configurada.

**Teste independente:** iniciar a aplicação com uma migration válida, reiniciar
com o mesmo schema e depois introduzir uma migration inválida; verificar,
respectivamente, aplicação única, validação sem duplicidade e falha de startup.

### P1: Autenticar e resolver o acesso provisionado ⭐ MVP

**História:** Como usuário provisionado, quero autenticar e acessar o tenant ao
qual fui associado sem escolher o tenant manualmente.

**Por que P1:** Sem esse fluxo não existe acesso seguro às funcionalidades da
V0.

**Critérios de aceitação:**

1. **WHEN** o usuário apresenta uma credencial válida do Supabase Auth **THEN**
   a aplicação SHALL reconhecer a identidade autenticada e consultar seu
   vínculo de domínio.
2. **WHEN** existe exatamente uma membership ativa para a identidade **THEN**
   a aplicação SHALL resolver o tenant no backend e SHALL permitir o acesso
   operacional correspondente.
3. **WHEN** a identidade autenticada não possui membership ativa **THEN** a
   aplicação SHALL bloquear o acesso operacional, informar que o acesso não foi
   provisionado e SHALL não criar tenant ou membership automaticamente.
4. **WHEN** existem múltiplas memberships ativas para a mesma identidade
   **THEN** a aplicação SHALL bloquear o acesso operacional por vínculo
   ambíguo e SHALL não escolher um tenant por inferência.
5. **WHEN** a credencial é inválida, expirada ou ausente **THEN** a aplicação
   SHALL negar a autenticação e SHALL não executar operação de domínio em nome
   do usuário.
6. **WHEN** um usuário não autenticado abre a rota de login **THEN** a
   aplicação SHALL apresentar campos de e-mail e senha, sem ação de cadastro
   público.
7. **WHEN** o Supabase Auth aceita as credenciais **THEN** a aplicação SHALL
   validar o access JWT com as regras existentes e SHALL criar uma sessão
   Spring Security no servidor; access e refresh tokens SHALL NOT ser enviados
   ao navegador.
8. **WHEN** o Supabase Auth rejeita credenciais **THEN** a interface SHALL
   apresentar mensagem genérica que não diferencie usuário inexistente, senha
   incorreta ou método de autenticação incompatível.
9. **WHEN** um access token da sessão está próximo do vencimento **THEN** o
   backend SHALL renová-lo pelo refresh token, validar o novo JWT e substituir
   o par de tokens na mesma sessão; requests concorrentes da mesma sessão
   SHALL NOT reutilizar simultaneamente o mesmo refresh token.
10. **WHEN** a sessão é encerrada pelo usuário **THEN** a aplicação SHALL
    invalidar a sessão Spring local e solicitar ao Supabase o logout somente
    daquela sessão.
11. **WHEN** o token expira e não pode ser renovado **THEN** a aplicação SHALL
    invalidar a sessão e SHALL exigir nova autenticação antes de qualquer
    operação protegida.

**Teste independente:** exercitar os cenários de identidade válida provisionada,
identidade sem vínculo, vínculo ambíguo e credencial inválida, verificando o
resultado de acesso e a ausência de criação automática; cobrir também login,
persistência da sessão, erro genérico, rotação e logout.

### P1: Isolar cada tenant na aplicação e no banco ⭐ MVP

**História:** Como responsável pela operação, quero que uma sessão só leia e
altere dados do próprio tenant, mesmo que uma requisição tente informar outro
tenant.

**Por que P1:** Isolamento é requisito transversal de segurança e LGPD da V0.

**Critérios de aceitação:**

1. **WHEN** uma operação é executada por uma identidade provisionada **THEN** o
   backend SHALL usar o tenant resolvido no vínculo persistido, ignorando
   qualquer `tenant_id` fornecido pelo cliente.
2. **WHEN** uma operação consulta ou altera dados do tenant autenticado **THEN**
   a aplicação SHALL limitar o resultado ao tenant resolvido.
3. **WHEN** uma operação tenta acessar dados de outro tenant por parâmetro,
   URL, formulário ou manipulação equivalente **THEN** a operação SHALL ser
   negada ou retornar ausência de recurso sem revelar dados do outro tenant.
4. **WHEN** uma consulta operacional chega ao PostgreSQL sem contexto de tenant
   ou com contexto incompatível **THEN** a policy RLS SHALL impedir a leitura
   ou alteração dos dados protegidos.
5. **WHEN** uma conexão de banco for reutilizada para outra transação **THEN**
   o contexto da transação anterior SHALL não influenciar a nova transação.

**Teste independente:** executar a mesma operação com dois tenants, alterar o
parâmetro de tenant no cliente e executar uma consulta sem contexto; verificar
que nenhuma leitura ou alteração atravessa a barreira da aplicação ou do RLS.

### P1: Preservar atomicidade dos casos de uso ⭐ MVP

**História:** Como operador, quero que uma falha durante um caso de uso não
deixe alterações parciais persistidas.

**Por que P1:** Compras, estoque, produção e indicadores dependerão de
operações compostas e rastreáveis.

**Critérios de aceitação:**

1. **WHEN** todas as etapas de uma operação transacional são concluídas
   **THEN** a aplicação SHALL confirmar todas as alterações como uma única
   unidade local.
2. **WHEN** uma etapa de uma operação transacional falha **THEN** a aplicação
   SHALL reverter as alterações feitas por aquela operação e SHALL deixar o
   banco sem estado parcial daquela tentativa.
3. **WHEN** um caso de uso da V0 for executado **THEN** ele SHALL não depender
   de uma segunda base, serviço ou transação distribuída para confirmar suas
   alterações.

**Teste independente:** executar uma operação composta com falha deliberada em
uma etapa posterior e verificar que nenhuma alteração anterior da mesma
operação permanece persistida.

### P2: Exibir estado de acesso no shell inicial

**História:** Como usuário, quero saber se estou autenticado e provisionado
quando abro a aplicação.

**Por que P2:** Melhora o diagnóstico de acesso sem antecipar a interface das
features operacionais.

**Critérios de aceitação:**

1. **WHEN** o usuário autenticado possui membership ativa **THEN** o shell SHALL
   apresentar o estado de acesso provisionado e uma entrada para a aplicação.
2. **WHEN** o usuário está autenticado, mas sem membership ativa ou com vínculo
   ambíguo **THEN** o shell SHALL apresentar o bloqueio de acesso provisionado e
   SHALL não apresentar dados ou comandos operacionais.
3. **WHEN** o usuário não está autenticado **THEN** o shell SHALL conduzir ao
   fluxo de autenticação e SHALL não apresentar conteúdo protegido.

**Teste independente:** abrir o shell com cada um dos três estados de acesso e
verificar a mensagem e os comandos disponíveis em cada caso.

## Casos de borda

- **Token válido, membership revogada durante a sessão:** a próxima operação
  protegida deve revalidar o vínculo e bloquear o acesso se ele não estiver
  mais ativo.
- **Tenant ausente no contexto da transação:** a operação de dados deve falhar
  de forma fechada; não deve assumir um tenant padrão.
- **Tentativa de forjar `tenant_id`:** o valor enviado pelo cliente não pode
  substituir o tenant resolvido no backend.
- **Erro de banco após alterações intermediárias:** a transação deve fazer
  rollback completo da operação local.
- **Falha de migration no startup:** a aplicação não deve ser tratada como
  disponível nem permitir que o usuário opere contra o schema incompleto.
- **Mensagem de bloqueio:** deve explicar que o acesso não foi provisionado,
  sem revelar a existência de tenants, usuários ou dados de terceiros.

## Rastreabilidade de requisitos

| ID | Requisito | História | Origem | Status |
| --- | --- | --- | --- | --- |
| F00-01 | Executar migrations pendentes antes da operação normal. | Inicializar | FR-001, ADR-022 | Implementado; verificado em T4/T11 |
| F00-02 | Reprovar startup quando migration ou configuração obrigatória falhar. | Inicializar | ADR-019, ADR-022 | Implementado; verificado em T4/T11 |
| F00-03 | Não reaplicar migrations já concluídas. | Inicializar | ADR-020 | Implementado; verificado em T4/T11 |
| F00-04 | Autenticar identidade pelo Supabase Auth. | Acesso | FR-001, ADR-015 | Implementado; verificado em T5/T11 |
| F00-05 | Resolver exatamente uma membership ativa no backend. | Acesso | FR-002, AD-003, ADR-017 | Implementado; verificado em T6/T7/T11 |
| F00-06 | Bloquear identidade sem vínculo e não criar tenant automaticamente. | Acesso | FR-002, ADR-015 | Implementado; verificado em T7/T10/T11 |
| F00-07 | Bloquear vínculo ambíguo sem escolher tenant arbitrariamente. | Acesso | AD-003 | Implementado; verificado em T7/T10/T11 |
| F00-08 | Negar credencial inválida, expirada ou ausente. | Acesso | FR-001 | Implementado; verificado em T5/T10/T11 |
| F00-09 | Ignorar `tenant_id` informado pelo cliente. | Isolamento | ADR-017 | Implementado; verificado em T9/T11 |
| F00-10 | Restringir leituras e alterações ao tenant resolvido. | Isolamento | FR-002, ADR-016 | Implementado; verificado em T3/T8/T9/T11 |
| F00-11 | Aplicar RLS quando o contexto estiver ausente ou incompatível. | Isolamento | ADR-016, ADR-017 | Implementado; verificado em T3/T8/T11 |
| F00-12 | Limpar o contexto ao trocar de transação/conexão. | Isolamento | ADR-017 | Implementado; verificado em T8/T9/T11 |
| F00-13 | Confirmar alterações como uma única transação local. | Atomicidade | ADR-018 | Implementado; verificado em T9/T11 |
| F00-14 | Reverter alterações parciais quando uma etapa falhar. | Atomicidade | ADR-018 | Implementado; verificado em T9/T11 |
| F00-15 | Não depender de JTA ou transação distribuída. | Atomicidade | ADR-018 | Implementado; verificado em T9/T11 |
| F00-16 | Exibir estado provisionado, bloqueado ou não autenticado sem dados protegidos. | Shell | PRD seção 4.2 | Implementado; verificado em T10/T11 |
| F00-17 | Exibir login por e-mail/senha para contas existentes, sem cadastro público. | Acesso | ADR-025 | Implementado; T13/T14 e testes locais; UAT Supabase pendente |
| F00-18 | Manter a autenticação Vaadin em sessão Spring server-side sem enviar tokens ao navegador. | Acesso | ADR-025 | Implementado; T14 e inspeção do fluxo/configuração; UAT publicado pendente |
| F00-19 | Renovar access token e rotacionar refresh token com proteção contra requests concorrentes. | Acesso | ADR-025 | Implementado; T15 e testes de rotação, concorrência e expiração |
| F00-20 | Encerrar sessão local e solicitar logout local ao Supabase; falhar fechado quando a renovação expirar. | Acesso | ADR-025 | Implementado; T14/T15 e testes locais; UAT Supabase pendente |

**Cobertura:** 20 requisitos identificados e mapeados em `tasks.md`. F00-01 a
F00-20 têm implementação e evidência de teste local registradas. O login,
refresh e logout contra a conta Supabase real e a instância publicada ainda
precisam de UAT.

## Critérios de sucesso

- [x] Uma aplicação recém-configurada só é considerada disponível depois de
      concluir as migrations válidas.
- [x] Um usuário provisionado acessa exclusivamente o tenant resolvido no
      backend.
- [x] Usuários sem vínculo ou com vínculo ambíguo não acessam dados
      operacionais nem criam tenant automaticamente.
- [x] Uma tentativa de atravessar tenant falha na aplicação e permanece
      protegida pelo RLS.
- [x] Uma falha em operação composta não deixa alterações parciais persistidas.
- [x] A especificação de F-00 não antecipa nenhuma regra de insumos, compras,
      estoque, pedidos ou produção.
