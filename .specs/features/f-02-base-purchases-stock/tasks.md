# F-02 — Tarefas de implementação

**Status:** Planejadas; nenhuma tarefa executada.
**Design:** [design.md](design.md)
**Spec:** [spec.md](spec.md)
**Escopo:** somente V0. Cada Txx é um incremento coeso com testes no mesmo
commit; não iniciar F-03/F-06/F-07/F-08 aqui.

**Decisão de sequência (2 de outubro de 2026):** a T23 da F-01 permanece
aberta para revisão independente e UAT final, mas não bloqueia a F-02. O
núcleo de autenticação, membership, autorização e isolamento de tenant da F-01
está implementado, passou pelo gate automatizado e está publicado no Render.
A T23 pode ser concluída em paralelo. Se a revisão identificar uma falha nesse
núcleo, interrompa apenas as tarefas da F-02 afetadas até a correção e
revalidação.

## Protocolo de execução

- Ativar `tlc-spec-driven` na execução, ler seu fluxo Execute e fazer **um
  commit atômico por tarefa**, incluindo seus testes e evidência.
- Antes da T01, conferir o diff e preservar mudanças já presentes em
  `docs/PRD-V0.md`, `docs/ROADMAP-V0.md` e
  `src/main/resources/application.yaml`; não incluí-las automaticamente
  em commits de implementação.
- Revisar documentação oficial da versão efetiva antes de alterar dependência,
  migration ou configuração. PDFBox 3.0.8 é proposta, não dependência atual.
  Toda adição exige atualizar `AGENTS.md`, árvore efetiva e compatibilidade.
- Para mudanças de banco, criar migration Flyway versionada, script de
  reversão controlada, grants, índices e RLS. Nunca usar Supabase CLI como
  segundo histórico nem depender de `flyway undo`.
- Comandos operacionais entram por identidade autenticada e
  `TenantScopedTransactionExecutor`. O RLS com `app_runtime` é testado em
  PostgreSQL real. Chave do Storage e chave do Vision só no backend.
- O gate completo usa **banco PostgreSQL isolado e novo**, não o banco local
  compartilhado que precede V2. O histórico de testes da F-01 fechou em 155;
  registrar a contagem real no início da execução e garantir que não caia.
- Ao fim, executar revisão independente, teste de discriminação e registrar
  evidência em `validation.md`, conforme a skill. Nenhuma tarefa está
  autorizada a apagar testes existentes para passar no gate.

## Organização por camadas e agregados

Seguir em F-02 a convenção observada no serviço `ordering` do Algashop:
`domain`, `application`, `infrastructure` e `presentation` são as quatro
camadas no pacote raiz de cada bounded context. Dentro de cada camada, os
pacotes são agrupados por conceito/capacidade/agregado, conforme o desenho;
catálogo, importação, compras e estoque continuam dentro de Tenant Operations.

- Presentation chama Application; Application usa Domain e portas;
  Infrastructure implementa portas. Views não acessam JPA diretamente.
- Um bounded context não importa entidades ou repositórios internos de outro.
  Integrações passam pelo contrato de aplicação publicado, incluindo a query de
  Platform Administration usada pelo adapter da `OperationalAuthorizationPort`.
- A persistência JPA do domínio é a escolha pragmática específica da F-02; o
  restante do padrão de camadas não exige classes duplicadas para mapeamento.
- Cada tarefa mantém seus arquivos nas camadas/capacidades envolvidas e inclui
  testes das fronteiras que alterar. A F-02 não exige mover ou refatorar o
  código já entregue pela F-01.
## Regras de modelagem tática para todas as tarefas

- Entidades/raízes de agregado F-02 podem usar Jakarta Persistence/Hibernate
  diretamente no modelo de domínio, com acesso por campos. Isso evita duplicar
  cada objeto em um mapeamento JPA separado; ports/repositórios continuam sendo
  fronteiras de aplicação, implementadas pelos adapters Spring Data JPA.
- Estado de negócio não recebe setters públicos. Entidades JPA não são `final`, têm construtor `protected` sem argumentos e campos/métodos persistentes não finais, conforme Jakarta Persistence 3.2. Construtores/fábricas de domínio validam
  criação; métodos com intenção do domínio validam e executam transições.
  Getters necessários para consulta/persistência são permitidos, mas não podem
  servir como API de alteração livre.
- Value Objects são usados onde encapsulam invariantes ou removem ambiguidade,
  principalmente quantidade/unidade, valor monetário e candidato de importação.
  Não criar tipos ou serviços sem regra que justifique a abstração.
- A autorização de ações restritas usa `OperationalAuthorizationPort`, descrita
  no desenho; é chamada dentro da transação e antes da mutação. A UI não é
  autoridade.
- O caso de uso coordena identidade, autorização, tenant, locks, repositórios e
  transação; não replica regras que pertencem a uma entidade/raiz. Regras que
  dependem de consulta entre agregados podem ficar no caso de uso/domain policy
  e devem declarar essa necessidade.
- Operações em múltiplos agregados podem compartilhar uma transação local para
  cumprir atomicidade da V0. Não mover compra/estoque para consistência eventual
  nem adicionar eventos/mensageria/event sourcing sem requisito concreto.
- Testes unitários chamam comportamento de domínio diretamente e provam
  invariantes/estados inválidos; testes de integração provam persistência,
  constraints, locking, tenant/RLS e rollback. Não basta testar somente o
  coordenador ou a tela.
## Matriz de cobertura de testes

> Gerada de `AGENTS.md`, `pom.xml`,
> `src/test/java/.../platform/identity/model/PlatformRoleAssignmentTests.java`,
> `src/test/java/.../integration/tenancy/RlsTenantContextIntegrationTests.java`,
> `src/test/java/.../integration/platform/administration/PlatformProvisioningEndToEndIntegrationTests.java`
> e `src/test/java/.../ui/platform/PlatformAdministrationViewIntegrationTests.java`.
> O projeto usa JUnit/Spring Boot e PostgreSQL real nos fluxos integrados.

| Camada | Tipo obrigatório | Cobertura esperada | Local | Comando de gate |
| --- | --- | --- | --- | --- |
| Regras de domínio e cálculo | Unitário | Cada AC correspondente, variantes, limites e erros; custos/quantidades exatos. | `src/test/java/.../operations/**` | `.\mvnw.cmd verify` |
| Casos de uso e autorização | Unitário + integração | Sucesso, negação, rollback, idempotência e concorrência dos comandos. | `src/test/java/.../operations/**`, `integration/**` | `.\mvnw.cmd verify` |
| Persistência, RLS e migrations | Integração PostgreSQL | Dois tenants, `app_runtime`, contexto ausente, constraints, índices essenciais e reversão revisada. | `src/test/java/.../integration/database/**` | `.\mvnw.cmd verify` |
| Storage, PDF e OCR | Contrato + integração local | Upload/download privados, falhas, limites, cota, retry e parser; fakes substituem serviços externos no gate. | `src/test/java/.../operations/importing/**` | `.\mvnw.cmd verify` |
| Vaadin | Integração de UI | Acesso permitido/negado, estados vazios, revisão, erros e confirmações. | `src/test/java/.../ui/operations/**` | `.\mvnw.cmd verify` |

**Regra de cobertura:** cada tarefa que altera código inclui testes na mesma
tarefa. A T25 adiciona cenários de ponta a ponta que cruzam módulos, sem
substituir os testes específicos anteriores. Testes de OCR real com documentos
representativos e credencial externa compõem validação manual de aceitação,
fora do gate local determinístico.

## Avaliação de paralelismo

| Tipo | Seguro em paralelo? | Evidência e decisão |
| --- | --- | --- |
| Unitários puros | Sim | Objetos/fakes locais, como testes de modelo da F-01. |
| Contratos de adapter com fakes | Sim | Sem serviço externo ou banco compartilhado. |
| Integração PostgreSQL e UI Spring | Não presumir | Migrações, fixtures e limpeza usam o mesmo banco do gate; executar gates sequencialmente em banco isolado. |
| Tarefas desta lista | Não marcadas `[P]` | Mesmo onde o código é independente, commits e gates serão serializados para preservar evidência e histórico atômico. |

## Comandos de verificação

| Gate | Quando | Comando |
| --- | --- | --- |
| Infra local | Antes de gates completos | `docker compose --env-file .env.local.example up -d` |
| Completo | Ao concluir cada Txx de código | `.\mvnw.cmd verify` com variáveis apontando para banco PostgreSQL isolado novo |
| Documental | Após mudanças em spec/design/tasks | `git diff --check` e inspeção dos arquivos novos |

O comando `verify` é o gate observado no projeto; a criação do banco
isolado e suas variáveis são preparadas no início da execução conforme
`AGENTS.md`, sem reutilizar a base contaminada. Não presumir paralelismo
do Maven ou contagem fixa de testes após o primeiro novo caso.

## Plano e dependências

```mermaid
flowchart LR
  T01 --> T02 --> T03
  T04 --> T05 --> T06 --> T07
  T04 --> T08
  T04 --> T09 --> T10 --> T11 --> T12 --> T13
  T07 --> T13
  T04 --> T14 --> T15 --> T16
  T05 --> T15
  T02 --> T16
  T07 --> T16
  T07 --> T17
  T08 --> T17
  T09 --> T17
  T10 --> T17
  T13 --> T17
  T15 --> T17
  T17 --> T18
  T18 --> T19
  T18 --> T20
  T18 --> T21
  T07 --> T22
  T13 --> T22
  T16 --> T22
  T19 --> T23
  T20 --> T23
  T21 --> T23
  T13 --> T23
  T15 --> T24
  T16 --> T24
  T03 --> T25
  T22 --> T25
  T23 --> T25
  T24 --> T25
```

Fases: **1** permissões e cadastro (T01–T08); **2** documentos/OCR
(T09–T13); **3** estoque (T14–T16); **4** compras (T17–T21);
**5** telas e validação (T22–T25). A numeração é a ordem recomendada; o
grafo registra dependências reais. Não iniciar uma tarefa antes de seus pais
terem gate e commit concluídos.

## Tarefas

### Fase 1 — Permissões e cadastro

| Tarefa | Entrega atômica | Depende | Requisitos | Testes / mínimo novo |
| --- | --- | --- | --- | --- |
| **T01** | Migration + reversão da designação operacional em `platform`, auditoria e restrições de membership ativa. | — | F02-24, F02-30 | Integração PostgreSQL / 3 cenários |
| **T02** | `domain`: raiz `OperationalManagerAssignment`; `application`: commands de designar/revogar e contrato de query somente-leitura para identidade + tenant com membership/designação ativas; `infrastructure`: persistência/adapters. Sem ler operações. | T01 | F02-24, F02-30, F02-32 | Domínio + aplicação + integração / 5 cenários |
| **T03** | Controle da designação na tela de administração existente, com estado ativo/revogado e mensagem de acesso negado. | T02 | F02-24 | UI integração / 3 cenários |
| **T04** | Migration + reversão de insumo, estabelecimento e zona operacional do tenant, com RLS, grants, índices e unicidade. | — | F02-01 a F02-03, F02-20, F02-30 | Integração PostgreSQL / 4 cenários |
| **T05** | Raiz `Ingredient` mapeada por JPA, com comportamento de domínio para nome/unidade e Value Objects de unidade/quantidade onde agreguem validação; conversão decimal de `kg/g`, `l/ml` e `un`. Sem setters públicos de negócio. | T04 | F02-01, F02-02, F02-12 | Domínio unitário / 6 cenários |
| **T06** | Adapter de persistência de insumo com busca exata e candidatos semelhantes restritos ao tenant. | T05 | F02-01, F02-02, F02-30 | Integração PostgreSQL / 4 cenários |
| **T07** | Commands/queries de insumo: chamar operações de domínio para criar/renomear; coordenar unicidade exata por tenant e candidatos semelhantes via porta; sem setters em cadeia nem saldo inicial. | T06 | F02-01, F02-02, F02-30 | Domínio + aplicação: unitário e integração / 4 cenários |
| **T08** | Entidade `Establishment` com operações apenas para regras reais de cadastro; command/query e adapter de persistência por tenant, sem criar comportamento artificial. | T04 | F02-03, F02-30 | Domínio + integração / 3 cenários |

**Concluído quando:** T01–T08 têm seus testes e gates aprovados, migrations
aplicam em banco vazio, designação não abre dados operacionais à plataforma e
insumo/estabelecimento não atravessam tenant. Cada tarefa gera um commit
`feat(f02): Txx ...` limitado a seus arquivos.

### Fase 2 — Documentos e OCR

| Tarefa | Entrega atômica | Depende | Requisitos | Testes / mínimo novo |
| --- | --- | --- | --- | --- |
| **T09** | Migration + reversão de documento preparado, compra, itens, revisões, uso de OCR e contador global; índices de chave/hash e RLS operacional. | T04 | F02-07, F02-15 a F02-17, F02-30 | Integração PostgreSQL / 5 cenários |
| **T10** | Porta e adapter de Supabase Storage privado mais fluxo de preparar/ler documento por ID autorizado; hash, tipo real, tamanho e chave opaca. | T09 | F02-07, F02-09, F02-30, F02-31 | Contrato + integração / 6 cenários |
| **T11** | Extração de texto digital e renderização limitada de PDF para páginas de OCR com PDFBox; validar Java 21, limites e memória. | T10 | F02-04, F02-07, F02-08 | Unitário + contrato / 4 cenários |
| **T12** | Adapter Vision `DOCUMENT_TEXT_DETECTION` por página e contador mensal com reserva segura/retry; sem confirmação automática. | T11 | F02-04, F02-08, F02-31 | Contrato + integração / 6 cenários |
| **T13** | Value Objects de candidatos e commands que aplicam revisão/transições válidas em `ImportDocument`; parser determinístico; falha do OCR permite preenchimento humano sem confirmar compra/estoque. | T12, T07 | F02-04 a F02-06, F02-08 a F02-10 | Domínio + aplicação: unitário e integração / 7 cenários |

**Concluído quando:** arquivo original é recuperável apenas pelo tenant;
imagem, PDF digitado e PDF escaneado produzem candidatos revisáveis; cota e
falhas nunca geram insumo/compra/estoque sozinhos. Cada tarefa inclui testes e
um commit próprio.

### Fase 3 — Estoque

| Tarefa | Entrega atômica | Depende | Requisitos | Testes / mínimo novo |
| --- | --- | --- | --- | --- |
| **T14** | Migration + reversão de entrada e movimento imutável com RLS, FK, checks e índices por origem. | T04 | F02-18 a F02-23, F02-30, F02-32 | Integração PostgreSQL / 5 cenários |
| **T15** | Raiz `StockEntry` mapeada por JPA protege validade e registro de movimento; `StockMovement` imutável. Adapter calcula saldo derivado e trava origens em ordem estável; queries retornam saldo físico/aproveitável. | T14, T05 | F02-18 a F02-20, F02-31, F02-32 | Domínio unitário + concorrência PostgreSQL / 7 cenários |
| **T16** | Commands de saldo inicial, descarte e ajuste chamam operações de `StockEntry`/`StockMovement`; ajuste manual exige `OperationalAuthorizationPort` antes de qualquer mutação. O caso de uso coordena `Ingredient` + origem em uma transação quando necessário. | T15, T02, T07 | F02-21 a F02-23, F02-24, F02-31, F02-32 | Domínio + autorização: permitir/negar sem mutação parcial; integração / 8 cenários |

**Concluído quando:** nenhuma ação deixa saldo da origem negativo, validade
altera somente aproveitável, o saldo inicial não cria compra e o responsável
é obrigatório nos ajustes. Cada tarefa inclui testes e um commit próprio.

### Fase 4 — Compras

| Tarefa | Entrega atômica | Depende | Requisitos | Testes / mínimo novo |
| --- | --- | --- | --- | --- |
| **T17** | `Purchase` como raiz e `PurchaseItem` como entidade filha: operações de seleção/confirmação validam parcelas, conversão, desconto e custo. Caso de uso coordena `Purchase`, `StockEntry` e movimento na mesma transação; ainda sem expor o command ao usuário. | T07, T08, T09, T10, T13, T15 | F02-07 a F02-14, F02-18, F02-31 | Domínio + aplicação: unitário e integração / 9 cenários |
| **T18** | Caso de uso consulta duplicidade/similaridade pela porta; constraints e locking fecham corrida. A raiz `Purchase` só confirma se documento/itens satisfazem suas regras. | T17 | F02-15 a F02-17, F02-31 | Domínio + aplicação + concorrência PostgreSQL / 6 cenários |
| **T19** | Command chama `OperationalAuthorizationPort` para `CORRECT_PURCHASE` antes de corrigir `Purchase`/`PurchaseItem`; registra revisão imutável e coordena delta por origem sob lock e transação local. | T18 | F02-24 a F02-26, F02-31, F02-32 | Domínio + autorização: permitir/negar sem mutação parcial; integração / 7 cenários |
| **T20** | Commands chamam `OperationalAuthorizationPort` (`CANCEL_PURCHASE_ITEM` ou `CANCEL_PURCHASE`) antes de alterar a compra; validam estado, travam origens em ordem estável, registram reversões e mantêm rollback total. | T18 | F02-24, F02-27, F02-28, F02-31, F02-32 | Domínio + autorização: permitir/negar sem mutação parcial; concorrência PostgreSQL / 7 cenários |
| **T21** | Command exige `OperationalAuthorizationPort` (`ADD_PURCHASE_ITEM`) antes de acrescentar item à `Purchase`; cria somente a nova entrada/movimento e preserva os anteriores na mesma transação. | T18 | F02-24, F02-29, F02-31, F02-32 | Domínio + autorização: permitir/negar sem mutação parcial; integração / 4 cenários |

**Concluído quando:** nota com três pacotes de 500 g a R$ 12 confirma
somente dois como 1.000 g e R$ 24; desconto do item é proporcional, desconto
global não é rateado; documento duplicado não gera saldo; correções e
cancelamentos preservam trilha e atomicidade. Cada tarefa inclui testes e um
commit próprio.

### Fase 5 — Telas e aceitação

| Tarefa | Entrega atômica | Depende | Requisitos | Testes / mínimo novo |
| --- | --- | --- | --- | --- |
| **T22** | Tela de insumos e revisão de lista importada, incluindo existentes, ambiguidades, seleção e saldo inicial opcional. | T07, T13, T16 | F02-01, F02-02, F02-04 a F02-06, F02-21 | UI integração / 5 cenários |
| **T23** | Tela de comprovante/compra: original, candidatos, parcelas, valor obrigatório, confirmação e ações restritas de correção/cancelamento/adição. | T19, T20, T21, T13 | F02-07 a F02-17, F02-24 a F02-29 | UI integração / 8 cenários |
| **T24** | Tela de saldos, lotes, validade, descarte, ajustes e histórico com estados de permissão e erro. | T15, T16 | F02-18 a F02-23, F02-32 | UI integração / 5 cenários |
| **T25** | Suíte integrada de aceitação F-02 em PostgreSQL isolado, com cobertura cruzada F02-01–F02-32; concluir seu próprio gate e commit antes da revisão independente. | T03, T22, T23, T24 | F02-01 a F02-32 | Integração ponta a ponta / 8 cenários |

**Concluído quando:** gate completo de T25 passa em banco novo e, depois do
commit, a revisão independente confirma resultados da spec, sensores de
discriminação detectam regressões representativas e `validation.md` registra
evidência.
O teste manual de documentos reais fecha a aceitação do OCR; a nota de
qualidade deve separar reconhecimento observado de correções humanas.

## Cobertura dos critérios da spec

| Faixa | Tarefas principais |
| --- | --- |
| F02-01–F02-03 | T04–T08, T22 |
| F02-04–F02-06 | T10–T13, T22 |
| F02-07–F02-11 | T09–T13, T17, T23 |
| F02-12–F02-14 | T05, T17, T23 |
| F02-15–F02-17 | T09, T18, T23 |
| F02-18–F02-20 | T14, T15, T24 |
| F02-21–F02-23 | T14–T16, T22, T24 |
| F02-24–F02-29 | T01–T03, T16, T19–T21, T23 |
| F02-30–F02-32 | T01–T25, com prova cruzada na T25 |

## Checagem de granularidade

Cada linha Txx entrega uma migration coesa, um componente de domínio,
um adapter, um caso de uso ou uma tela; seus testes acompanham a entrega.
T07, T08, T15 e T16 podem tocar alguns arquivos relacionados para fechar a
mesma fronteira verificável. Se a execução revelar duas capacidades
independentes em qualquer uma, dividir antes de programar e atualizar este
grafo; não juntar commits. T25 é exclusivamente validação cruzada.

## Conferência de dependências do desenho

| Tarefa | `Depende` no corpo | Setas recebidas no desenho | Resultado |
| --- | --- | --- | --- |
| T01 | — | — | Confere |
| T02 | T01 | T01 | Confere |
| T03 | T02 | T02 | Confere |
| T04 | — | — | Confere |
| T05 | T04 | T04 | Confere |
| T06 | T05 | T05 | Confere |
| T07 | T06 | T06 | Confere |
| T08 | T04 | T04 | Confere |
| T09 | T04 | T04 | Confere |
| T10 | T09 | T09 | Confere |
| T11 | T10 | T10 | Confere |
| T12 | T11 | T11 | Confere |
| T13 | T12, T07 | T12, T07 | Confere |
| T14 | T04 | T04 | Confere |
| T15 | T14, T05 | T14, T05 | Confere |
| T16 | T15, T02, T07 | T15, T02, T07 | Confere |
| T17 | T07, T08, T09, T10, T13, T15 | T07, T08, T09, T10, T13, T15 | Confere |
| T18 | T17 | T17 | Confere |
| T19 | T18 | T18 | Confere |
| T20 | T18 | T18 | Confere |
| T21 | T18 | T18 | Confere |
| T22 | T07, T13, T16 | T07, T13, T16 | Confere |
| T23 | T19, T20, T21, T13 | T19, T20, T21, T13 | Confere |
| T24 | T15, T16 | T15, T16 | Confere |
| T25 | T03, T22, T23, T24 | T03, T22, T23, T24 | Confere |

## Validação de testes junto da tarefa

| Tarefa | Camada | Matriz exige | Planejado | Resultado |
| --- | --- | --- | --- | --- |
| T01 | Migration | Integração | Integração | Confere |
| T02 | Aplicação/autorização | Unitário + integração | Unitário + integração | Confere |
| T03 | UI | Integração | Integração | Confere |
| T04 | Migration | Integração | Integração | Confere |
| T05 | Domínio | Unitário | Unitário | Confere |
| T06 | Persistência | Integração | Integração | Confere |
| T07 | Aplicação | Unitário + integração | Unitário + integração | Confere |
| T08 | Domínio/aplicação/persistência | Unitário + integração | Unitário + integração | Confere |
| T09 | Migration | Integração | Integração | Confere |
| T10 | Storage | Contrato + integração | Contrato + integração | Confere |
| T11 | PDF | Unitário + contrato | Unitário + contrato | Confere |
| T12 | Vision/cota | Contrato + integração | Contrato + integração | Confere |
| T13 | Parser/aplicação | Unitário + integração | Unitário + integração | Confere |
| T14 | Migration | Integração | Integração | Confere |
| T15 | Domínio/aplicação | Unitário + integração | Unitário + integração | Confere |
| T16 | Aplicação/autorização | Unitário + integração | Unitário + integração | Confere |
| T17 | Aplicação | Unitário + integração | Unitário + integração | Confere |
| T18 | Aplicação/persistência | Unitário + integração | Unitário + integração | Confere |
| T19 | Aplicação | Unitário + integração | Unitário + integração | Confere |
| T20 | Aplicação | Unitário + integração | Unitário + integração | Confere |
| T21 | Aplicação | Unitário + integração | Unitário + integração | Confere |
| T22 | UI | Integração | Integração | Confere |
| T23 | UI | Integração | Integração | Confere |
| T24 | UI | Integração | Integração | Confere |
| T25 | Ponta a ponta | Integração | Integração | Confere |

## Condições por tarefa

- T01–T11 não dependem de provisionar nem chamar o Google Cloud Vision. Antes
  da T12, confirmar projeto dedicado, credencial privada e limites de uso; nunca
  registrar segredos no Git. A franquia gratuita proposta não garante custo
  zero para usos adicionais.
- Testes com lista manuscrita, lista digitada e comprovante real pertencem à
  aceitação do fluxo OCR em T25. O gate local usa fakes e fixtures sem serviço
  externo.
- Antes da T16, fechar a decisão pendente de origem do ajuste positivo em
  `spec.md`; a falha do OCR já tem comportamento confirmado.
- Definir e registrar a versão efetiva do PDFBox e qualquer nova dependência
  em `AGENTS.md` ao iniciar T11, com documentação oficial e build de Java 21.
