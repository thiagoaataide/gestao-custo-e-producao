# F-01 — Validação e verificação independente

**Data:** 22 de setembro de 2026
**Escopo:** T16 — validação ponta a ponta da F-01 Platform Provisioning
**Resultado:** PASS

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
