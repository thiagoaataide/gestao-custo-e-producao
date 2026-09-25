# F-01 — Validação e verificação independente

**Data:** 22 de setembro de 2026
**Escopo:** T16 — validação ponta a ponta da F-01 Platform Provisioning
**Resultado:** PASS

> Este resultado PASS é histórico e permanece válido para o gate T16 e os
> requisitos F01-01 a F01-16 então definidos. Ele não significa que o
> fechamento atual da F-01 esteja completo.

## Estado de fechamento registrado antes do T22 — 23 de setembro de 2026 (histórico)

**Estado atual:** ABERTO — T17 e T18 passaram pelos gates automatizados; revisão
independente e UAT no Render não foram concluídas. T18 implementa a rota do
convite e a continuidade após login; T19, T20 e T21 passaram pelos gates
automatizados. T22 e T23 seguem pendentes para regressão publicada, revisão
independente e UAT.

## Estado de fechamento após T22 — 24 de setembro de 2026

**Estado atual:** ABERTO — T22 passou pelos gates automatizados locais; T23
continua pendente para revisão independente e UAT manual publicada. A F-01 não
está concluída. Nenhum deploy, alteração no Render/Supabase ou envio real de
e-mail foi feito.

### Evidência de T22

- A regressão `PlatformProvisioningEndToEndIntegrationTests.publicOriginInvitationFlowCreatesLinkAndActivatesMembershipWithAudit`
  passou em PostgreSQL 17 local: convite emitido com origem
  `https://gestao-custo-e-producao.onrender.com`, token no caminho canônico,
  aceite autenticado, uma membership ativa, contexto de tenant aplicado na
  transação RLS e auditoria de aceite sem metadata sensível.
- A classe ponta a ponta executou 5 testes: 0 falhas, 0 erros e 0 skips.
- `mvnw.cmd clean verify`, com Java 21.0.12 e PostgreSQL 17 isolado e vazio na
  porta 55433: 193 testes, 0 falhas, 0 erros e 0 skips; build Vaadin e JAR
  Spring Boot concluídos.
- O primeiro gate após o teste focado encontrou um erro em
  `AdministrativeAuditPersistenceIntegrationTests`, pois o banco temporário
  continha dados persistidos da execução focada. O Compose isolado registrado
  foi recriado vazio; o gate completo repetido passou. O serviço/volume local
  compartilhado (porta 55432) não foi modificado.
- `git diff --check` passou. O container, volume e rede do Compose temporário
  `gestao-producao-test-f01-t20-20260924-01` foram removidos após o gate e a
  ausência dos três recursos foi confirmada.
- Os testes de email usam HTTP simulado. O adapter SendGrid segue desabilitado
  por padrão; nenhuma credencial foi usada e nenhum e-mail foi enviado.
- A UAT no Render permanece pendente, inclusive a configuração manual de
  `PLATFORM_INVITATION_BASE_URL` para a origem HTTPS pública.

## Gate da feature

A validação foi executada contra um PostgreSQL 17 recém-criado pelo compose
isolado `gestao-producao-gate`, com schema vazio. O gate final foi executado
por `cmd.exe /d /c mvnw.cmd verify`, com Maven Wrapper 3.9.16 e Java 21.0.12.

Evidências do gate:

- Flyway aplicou as migrations da aplicação e das fixtures de teste no banco
  vazio; as verificações de inicialização e idempotência passaram.
- `Tests run: 155, Failures: 0, Errors: 0, Skipped: 0`.
- Build de frontend Vaadin concluído.
- JAR executável Spring Boot empacotado.
- A execução limpa equivalente também foi concluída com sucesso antes do
  fechamento da tarefa.
- `git diff --check` passou.

A validação automatizada não substitui uma UAT manual pelo navegador; essa
etapa visual não foi executada neste gate.

## Cobertura dos requisitos

| Requisito | Evidência principal | Resultado |
|---|---|---|
| F01-01 | `PlatformProvisioningEndToEndIntegrationTests`, bootstrap idempotente e identidade ordinária não provisionada | PASS |
| F01-02 | `PlatformProvisioningEndToEndIntegrationTests`, separação de owner/admin de plataforma | PASS |
| F01-03 | `PlatformProvisioningEndToEndIntegrationTests`, tenant criado sem membership | PASS |
| F01-04 | cenário de auditoria administrativa e `AdministrativeAuditViewIntegrationTests` | PASS |
| F01-05 | ciclo de vida ativo, suspenso, reativado e encerrado do tenant | PASS |
| F01-06 | suspensão bloqueia operação e reativação restaura o acesso | PASS |
| F01-07 | convite pendente, token com expiração de 24 horas e aceite | PASS |
| F01-08 | aceite de convite cria membership ativo e associação ao tenant | PASS |
| F01-09 | entrega opcional/manual e fluxo de confirmação do convite | PASS |
| F01-10 | convite expirado não pode ser aceito | PASS |
| F01-11 | vínculo ativo é obrigatório para acesso ao tenant | PASS |
| F01-12 | usuário administrativo de plataforma não recebe acesso operacional ao tenant | PASS |
| F01-13 | usuário não pode possuir membership ativo em mais de um tenant | PASS |
| F01-14 | auditoria registra tentativa negada sem dados operacionais sensíveis | PASS |
| F01-15 | consulta de auditoria fica restrita à administração da plataforma | PASS |
| F01-16 | falha transacional não deixa estado parcial persistido | PASS |

## Cenários transversais executados

A suíte `PlatformProvisioningEndToEndIntegrationTests` cobre cinco fluxos
transversais em PostgreSQL real:

1. bootstrap idempotente do owner e ausência de provisionamento automático;
2. criação do tenant, ausência inicial de membership e ciclo de vida;
3. convite, aceite, expiração, membership único e propagação do tenant para
   RLS;
4. separação entre administração da plataforma e operação do tenant;
5. tentativa negada, preservação do estado e consulta segura da auditoria.

Também foram verificados rollback transacional, isolamento por tenant,
reinicialização idempotente e configuração inválida de banco na suíte
existente.

## Sensor de discriminação

Foi aplicado um sensor independente com duas mutações temporárias no código de
produção, sempre restauradas antes do commit:

1. desabilitar a concessão de acesso de plataforma em
   `AccessDecisionResolver`: **mutação eliminada**, pois a suíte produziu duas
   falhas;
2. trocar a transição `SUSPEND` por `reactivate` em
   `TenantProvisioningCommandService`: **mutação eliminada**, pois a suíte
   produziu erro de ciclo de vida inválido.

Resultado do sensor: **2/2 mutações eliminadas — PASS**.

## Verificação independente

Foi realizada uma revisão fresh-eyes, ancorada na especificação e no
traceability de F01-01 a F01-16. Todos os requisitos possuem teste ou
evidência existente correspondente, e nenhum gap residual exigiu criação de
tarefa corretiva.

**Resultado da validação T16: PASS — 16/16 requisitos então definidos foram verificados.**

## Complemento — T17: bootstrap do owner pela tela inicial

**Data:** 23 de setembro de 2026
**Escopo:** T17 / F01-17
**Resultado automatizado:** PASS
**Revisão independente e UAT no ambiente publicado:** pendentes

A tela inicial passou a exibir a ação explícita **“Ativar administração da
plataforma”** somente quando a identidade autenticada corresponde exatamente
ao `platform.bootstrap.owner-subject` configurado. O login não cria owner
automaticamente; ao clicar, o serviço revalida a identidade, persiste owner e
auditoria na transação existente e a interface navega para a administração.

Evidências da validação automatizada:

- `AccessShellViewIntegrationTests`: 7 testes, sem falhas ou erros; cobre
  bootstrap explícito, ausência da ação para identidade diferente e transição
  para acesso de plataforma após o bootstrap.
- `mvn verify`: 177 testes, sem falhas ou erros; frontend Vaadin construído e
  JAR executável empacotado.
- PostgreSQL 17 em container temporário
  `gestao-owner-bootstrap-gate-20260923` (ID `50d6453de649`), usando `--rm` e
  `tmpfs`; foi parado ao fim da validação e removido automaticamente. Nenhuma
  conexão ou alteração no Supabase ou no banco publicado.
- `git diff --check` sem erros.

Esta validação automatizada não confirma a experiência visual no Render. Após
o próximo deploy, ainda é necessário autenticar com a identidade cujo subject
está configurado, clicar na ação e verificar a abertura da administração. A
revisão independente fresh-eyes da T17 também não foi executada nesta rodada.

## Gaps registrados antes de T20 — histórico

> Este registro antecede a conclusão de T20–T22 e não representa o estado
> atual. O fechamento atualizado está em “Estado de fechamento após T22”.

- **Convite publicado:** a rota `/invitations/{token}`, o retorno após login e
  a origem pública HTTPS explícita passaram nos gates automatizados T18/T19.
  Ainda faltam a regressão completa publicada (T22) e a UAT real no Render
  (T23). O valor `PLATFORM_INVITATION_BASE_URL` precisa ser cadastrado
  manualmente no Render antes do próximo deploy; não alteramos a configuração
  do serviço.
- **E-mail:** existe `InvitationDeliveryPort` e o teste da entrega pós-commit,
  mas não foi encontrada implementação de produção SendGrid. E-mail continua
  opcional; link copiado permanece o fallback garantido. A integração real não
  foi testada nem configurada nesta rodada.
- **Apresentação:** a tela administrativa requer revisão de alinhamento e
  adaptação a viewports estreitos, preservando permissões e ações. A validação
  visual publicada ainda está pendente.

Esses pontos não invalidam os resultados automatizados já registrados; eles
impedem declarar o fluxo publicado da F-01 como concluído. A próxima evidência
deve ser produzida por T20–T23, com teste automatizado separado da UAT manual e
sem registrar ou copiar segredos para este arquivo.

## Complemento — T19: origem pública dos links de convite

**Data:** 23 de setembro de 2026

**Escopo:** T19 / F01-09 / F01-19

**Resultado automatizado:** PASS
**Deploy e UAT publicados:** pendentes; dependem de configuração manual pelo owner

A origem dos links não possui mais fallback para `localhost`: deve ser definida
por `PLATFORM_INVITATION_BASE_URL`. Perfis locais/testes podem usar HTTP local;
fora deles, a inicialização valida origem HTTPS absoluta e rejeita hosts locais,
endereços IP não públicos, host de rótulo único, userinfo, path, query e
fragmento. A normalização elimina barra final, e os links mantêm o caminho
`/invitations/{token}` e token opaco existentes.

Evidências:

- `InvitationLinkPropertiesTests`: 4 testes cobrindo normalização local,
  origem HTTPS do Render e origens/formatos rejeitados.
- `mvnw.cmd verify`: 186 testes, sem falhas, erros ou skips; frontend Vaadin
  construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em Compose isolado
  `gestao-producao-test-f01-t19-20260923-01`, porta 55432. O comando exato
  `docker compose -p gestao-producao-test-f01-t19-20260923-01 down --volumes --remove-orphans`
  removeu container, rede e volume temporário ao final; os recursos foram
  confirmados como removidos. Os demais volumes foram preservados.
- `git diff --check` passou. Nenhuma variável/secreto foi configurado no Render
  e nenhuma alteração foi feita no Supabase.

Antes do próximo deploy, o owner deve adicionar no serviço Render a variável
`PLATFORM_INVITATION_BASE_URL=https://gestao-custo-e-producao.onrender.com`.
Esta rodada não executou deploy nem UAT do link publicado.

## Complemento — T21: interface administrativa responsiva

**Data:** 23 de setembro de 2026

**Escopo:** T21 / F01-20

**Resultado automatizado:** PASS

**Inspeção manual em navegador/Render:** pendente para T23; este commit não faz deploy.

`/platform` separa Tenants, Membros e Papéis da plataforma em abas. A auditoria
continua em `/platform/audit`, com navegação de ida e volta. Os formulários
usam `FormLayout` responsivo; as ações primárias/destrutivas usam variantes
Aura; os grids exibem somente as linhas carregadas. T24 une convites e
memberships em Membros, com filtros e convite inline, sem criar membership
diretamente.

Evidências:

- `PlatformAdministrationViewIntegrationTests`: 5 testes aprovados, incluindo
  as quatro abas, aba inicial, encaminhamento de “Convidar membro”, formulários
  responsivos, grids dimensionados ao conteúdo e preservação de acesso.
- `AdministrativeAuditViewIntegrationTests`: 6 testes aprovados, incluindo
  formulário responsivo e grid dimensionado ao conteúdo; autorização e
  mensagens seguras seguem cobertas.
- `mvnw.cmd verify`: 189 testes, sem falhas, erros ou skips; frontend Vaadin
  de produção construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em Compose isolado
  `gestao-producao-test-f01-t21-20260923-01`, porta 55434. O comando exato
  `docker compose -p gestao-producao-test-f01-t21-20260923-01 down --volumes --remove-orphans`
  removeu container, rede e volume temporário; ausência confirmada, demais
  volumes preservados.
- `git diff --check` passou. Nenhuma chamada ao Supabase ou ao Render foi feita.
- Dependência e APIs conferidas contra Vaadin Flow 25.2.8 do `pom.xml` e seus
  artefatos de fonte; documentação oficial: [Form Layout](https://vaadin.com/docs/latest/components/form-layout),
  [Tabs/TabSheet](https://vaadin.com/docs/latest/components/tabs),
  [Aura](https://vaadin.com/docs/latest/styling/themes/aura) e
  [Stylesheets](https://vaadin.com/docs/latest/styling/stylesheets).
- Checklist manual para a inspeção publicada na T23:
  - [ ] largura estreita (aprox. 390 px): abas utilizáveis; campos empilhados;
        botões e conteúdo sem corte ou rolagem horizontal da página;
  - [ ] largura ampla (aprox. 1366 px): campos e ações alinhados; grids sem
        grande área vazia;
  - [ ] teclado: foco visível, setas/Enter nas abas e foco no e-mail após
        “Convidar membro”;
  - [ ] atalho de auditoria abre `/platform/audit` e retorna para `/platform`;
  - [ ] convite criado/reenviado mantém a origem pública do Render, e o fluxo
        de aceite continua ativando a membership.

O checklist é um plano de inspeção, não uma afirmação de UAT visual executada.

## Roteiro UAT T23 — pendente de execução pelo owner

Execute esta validação depois de publicar o commit que contém T24 e T25 e
confirmar que o deploy terminou com sucesso. Codex não alterou o serviço Render nem fez
deploy nesta rodada.

### Preparação

- No projeto Supabase Auth, configure SMTP próprio e o template **Confirm sign up**
  para entregar `{{ .Token }}` como código OTP; mantenha a confirmação de e-mail
  habilitada e confirme uma entrega de teste antes do fluxo completo.
- No Web Service do Render, configure
  `PLATFORM_INVITATION_BASE_URL=https://gestao-custo-e-producao.onrender.com`.
- Mantenha `PLATFORM_INVITATION_DELIVERY_ENABLED=false`, salvo se você
  confirmar no painel do provedor que o envio está disponível sem custo e
  decidir habilitá-lo. Não cole chaves em arquivos versionados nem as envie
  nesta conversa.
- Use uma identidade operacional de teste sob seu controle, com e-mail
  verificado e diferente da identidade `PLATFORM_OWNER`.
- Não inclua token de convite, senha, chave ou endereço de e-mail pessoal em
  capturas ou neste documento.

### Verificações

- [ ] Confirme que o serviço implantou o commit T24/T25 e que `/` permite entrar
      sem retornar repetidamente à tela inicial de login.
- [ ] Entre em `/platform`; confirme que a conta está autorizada e que as abas
      Tenants, Membros e Papéis da plataforma aparecem sem dados operacionais
      de tenants.
- [ ] Em aproximadamente 390 px e 1366 px, verifique abas, filtros por tenant e
      situação, cards sem rolagem horizontal, convite inline, botões, confirmações
      de revogação e foco por teclado conforme T24 e o checklist de T21 acima.
- [ ] Crie um tenant de teste e um convite para uma identidade operacional
      controlada diretamente na aba Membros. Confirme o estado Convite pendente,
      copie o link e confirme a origem pública HTTPS `/invitations/`, nunca
      `localhost`.
- [ ] Em janela privada, abra o link. Confirme que somente o e-mail convidado é
      exibido como endereço imutável para cadastro e que abrir a rota não cria
      identidade de domínio nem membership.
- [ ] Cadastre uma conta Auth com o e-mail do convite. Confirme que o código OTP
      é entregue no email; informe o código na própria tela e veja a confirmação
      explícita para aceitar.
- [ ] Com o owner conectado em Gmail e um convite para Outlook, use “Trocar de
      conta”, continue no mesmo convite com a conta Outlook, verifique o OTP e
      confirme que apenas o aceite explícito ativa a membership. Teste também o
      fluxo de destinatário Gmail quando aplicável.
- [ ] Confirme que a identidade operacional consegue acesso ao tenant
      provisionado; confirme que a administração da plataforma não passa a
      expor pedidos, estoque, produção, custos ou indicadores desse tenant.
- [ ] Verifique na tela de auditoria o aceite e o ator/alvo/resultado; não
      exponha token, senha, chave, e-mail ou dados operacionais na evidência.
- [ ] Opcional: se você configurar o SendGrid e confirmar a condição gratuita,
      teste apenas com um destinatário sob seu controle. Caso contrário, marque
      o envio real como não executado; o link copiável continua sendo o caminho
      de convite e nenhum plano pago deve ser ativado.

**Resultado desta rodada:** roteiro preparado; nenhum passo acima foi executado
contra o Render. A revisão independente fresh-eyes também permanece pendente,
pois não foi entregue um relatório verificável nesta rodada. Portanto, T23 e a
F-01 seguem abertas.

## Complemento — T18: rota de aceitação de convites

**Data:** 23 de setembro de 2026
**Escopo:** T18 / F01-08 / F01-18
**Resultado automatizado:** PASS
**Revisão independente e UAT publicada:** pendentes

A aplicação registra a rota Vaadin `/invitations/{token}` como acessível a
usuários autenticados. A tela não exibe o token, e-mail convidado ou nome do
tenant; somente a ação explícita de confirmação chama o
`InvitationAcceptanceService`. Respostas de falha são genéricas e não exibem
mensagens ou causas do serviço.

Evidências:

- `InvitationAcceptanceViewTests`: 4 testes cobrindo ausência de mutação ao
  abrir, confirmação explícita com a identidade/token validados da sessão,
  bloqueio sem autenticação e mensagem genérica sem detalhes sensíveis.
- `InvitationRouteSecurityIntegrationTests`: GET não autenticado é salvo e
  redirecionado ao login; a autenticação simulada retorna ao mesmo caminho do
  convite com o parâmetro `continue` usado pelo fluxo Vaadin. Não há chamada
  real ao Supabase.
- `mvnw.cmd verify`: 182 testes, sem falhas, erros ou skips; frontend Vaadin
  construído e JAR executável empacotado.
- O gate usou PostgreSQL 17 em um projeto Compose isolado
  `gestao-producao-t18-gate-20260923`, porta 55433, com volume nomeado
  temporário próprio. Container, rede e volume temporário foram removidos e a
  ausência foi confirmada. O volume persistente
  preexistente do projeto foi preservado. Ele continha schemas não vazios sem
  histórico Flyway, portanto não foi usado nem alterado pelo gate.
- O `.env` ativo aponta para Supabase, mas não foi carregado. Nenhuma chamada
  ou alteração foi feita no Supabase ou no Render.

## Complemento — T20: adapter opcional de entrega SendGrid

**Data:** 24 de setembro de 2026
**Escopo:** T20 / F01-09
**Resultado automatizado:** PASS
**Envio real/UAT no Render:** pendente; adapter permanece desabilitado

O adapter implementa somente `InvitationDeliveryPort`, usa a API Mail Send v3
por HTTPS e só é registrado quando delivery está habilitado e a chave e o
remetente estão configurados. A chamada ocorre no listener existente após o
commit; somente HTTP 202 é considerado aceito. Connect/read timeout são
limitados a 2/3 segundos. Rejeição ou indisponibilidade gera uma falha
provider-neutral sem causa, resposta HTTP, destinatário ou link no texto da
exceção; o listener mantém o convite já confirmado e registra metadados
seguros. Não foi adicionada dependência Maven nem tipo SendGrid ao domínio.

As variáveis documentadas são `PLATFORM_INVITATION_DELIVERY_ENABLED` (default
`false`), `PLATFORM_INVITATION_DELIVERY_SENDGRID_API_KEY` (secret) e
`PLATFORM_INVITATION_DELIVERY_SENDGRID_FROM_EMAIL` (remetente autenticado).
O `.env.local` real não foi aberto/alterado e nenhum segredo foi usado.
Nenhuma variável foi aplicada ao Render e nenhum envio real foi feito.

Evidências:

- `SendGridInvitationDeliveryAdapterTests`: 3 testes de contrato simulam HTTP
  202 com URL/header/payload, rejeição e indisponibilidade sem vazamento.
- `SendGridInvitationDeliveryConfigurationTests`: 1 teste cobre delivery
  desligado e configuração incompleta.
- `InvitationDeliveryAfterCommitIntegrationTests`: 2 testes confirmam entrega
  somente após commit e falha não fatal/auditada.
- `mvnw.cmd verify`: 193 testes, 0 falhas, 0 erros, 0 skips; frontend Vaadin
  construído e JAR Spring Boot empacotado. Compilação principal e de testes
  também passaram com JDK 21.0.12.
- O primeiro gate contra o volume persistente local existente falhou antes de
  migrar: Flyway encontrou schemas não vazios sem histórico. Nenhum schema ou
  volume foi limpo/alterado para contornar a falha. O gate PASS foi repetido
  contra PostgreSQL 17 limpo em Compose isolado
  `gestao-producao-test-f01-t20-20260924-01`, host port 55433; o stack fica
  registrado em `.specs/STATE.md` para ser removido após T22, preservando o
  volume persistente do Compose local.
- `git diff --check` passou antes do gate completo.
- Documentação oficial conferida: [SendGrid Mail Send API](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send),
  [SendGrid trial/plan](https://www.twilio.com/docs/sendgrid/ui/account-and-settings/upgrading-your-plan) e
  [Spring Framework REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html).

A documentação pública atual descreve o Email API em teste grátis como até
100 envios/dia por 60 dias e exige upgrade após o período. O estado/validade
da conta específica do owner não foi consultado. Portanto a aplicação fica
com `PLATFORM_INVITATION_DELIVERY_ENABLED=false`; o owner deve confirmar no
painel que a conta permanece em condição de custo zero antes de habilitar.


## Complemento — T24/T25: implementação local e gate bloqueado

**Data:** 25 de setembro de 2026

**Escopo:** T24 / F01-21 e T25 / F01-22

**Estado:** implementadas no workspace; publicação solicitada. Gate Maven e UAT
publicada permanecem pendentes.

T24 substitui Convites/Membros por uma aba única **Membros**, com filtros por
tenant e situação, registros compostos em cards, formulário de convite inline,
link copiável, confirmação de revogação e IDs como fallback quando não existe
e-mail associado. T25 acrescenta onboarding sob convite válido: e-mail imutável,
cadastro/login Auth, OTP por e-mail e reenvio, troca de sessão preservando o
convite e aceite explícito. A confirmação do OTP valida JWT/subject e consulta
o perfil `/user` antes de criar a sessão autenticada da aplicação. O cadastro e
a verificação não chamam os casos de uso de identidade/membership; somente o
`InvitationAcceptanceService` existente é invocado no aceite.

Evidência local disponível:

- `git diff --check` passou.
- O checklist `.checks/f01-t24-t25.md` identifica os testes focados de UI,
  contrato HTTP Supabase, consulta de convite, validação de perfil e aceite.
- Nenhum teste Maven foi executado nesta sessão: `java` não existe no WSL e
  `JAVA_HOME` não está configurado; o comando bloqueou antes de compilar/executar.
- Docker Desktop não está integrado a esta distribuição WSL; o gate que exige
  PostgreSQL local não pôde ser iniciado. Nenhuma imagem, migration, schema ou
  volume foi alterado.
- Nenhuma chamada, configuração ou alteração foi feita no Supabase ou Render;
  nenhuma conta/Auth ou email real foi criada/enviado.

Antes do UAT publicado, o owner precisa configurar SMTP próprio em Supabase Auth
e o template **Confirm sign up** com `{{ .Token }}`; o email deve continuar
confirmado antes de login. A tela espera código OTP e o remetente hospedado
padrão não é uma garantia para enviar a destinatários externos. A UAT da T23
deve testar destinatários Gmail e Outlook, troca owner Gmail → destinatário
Outlook, continuação do mesmo convite, aceite e ativação de membership, além da
inspeção visual em aproximadamente 390 px e 1366 px. A revisão independente e
a execução em ambiente com Java 21/PostgreSQL continuam pendentes.
