# Roadmap da V0 — TAAS Gestão de Produção

## Status

- **Produto:** TAAS Gestão de Produção
- **Escopo:** somente V0
- **Fonte funcional:** docs/PRD-V0.md
- **Processo:** Specify → Design quando necessário → Tasks → Execute → Verify
- **Estado:** roadmap inicial para decomposição em especificações

Este roadmap organiza a implementação em fatias verticais, preservando o fluxo
operacional completo da V0:

Insumos e fichas técnicas
  → cardápio
  → pedidos confirmados
  → planejamento de produção
  → necessidade de insumos
  → estoque e compras
  → produção e consumo
  → movimentações
  → CMV, margem e indicadores

O roadmap não autoriza funcionalidades V1, V2 ou V3. Cada item deverá possuir
uma especificação própria antes da implementação.

## Princípios de execução

- Entregar fatias demonstráveis, evitando separar artificialmente backend e
  interface como entregas independentes.
- Manter o modelo genérico de planejamento de produção, custos e insumos; a
  marmitaria será apenas o primeiro caso de validação.
- Aplicar tenant, RLS, ACID, rastreabilidade e migrations em toda feature que
  persistir dados.
- Manter custos projetados separados de CMV realizado.
- Resolver ambiguidades na especificação da feature que as utiliza, sem reabrir
  decisões arquiteturais já registradas.
- Fazer um commit atômico por tarefa durante a execução.

## Visão geral das features

| ID | Feature | Objetivo | Dependências | Requisitos principais |
| --- | --- | --- | --- | --- |
| F-00 | Fundação técnica e acesso seguro | Subir aplicação, banco, migrations, autenticação, tenant e RLS | Nenhuma | FR-001, FR-002 |
| F-01 | Provisionamento da plataforma | Criar tenant, convite e membership sem conceder acesso operacional à plataforma | F-00 | ADR-015 |
| F-02 | Cadastro base, compras e estoque | Cadastrar insumos, unidades, estabelecimentos, compras, lotes e movimentações | F-00, F-01 | FR-003 a FR-007, FR-019, FR-020, FR-022 (descarte de insumo) |
| F-03 | Fichas técnicas e rendimentos | Cadastrar receitas, quantidades, preparo e rendimento bruto/pronto | F-02 | FR-008, FR-009 |
| F-04 | Produtos e cardápios | Compor produtos, definir preços e gerar mensagem para compartilhamento manual | F-03 | FR-010, FR-011 |
| F-05 | Clientes e pedidos confirmados | Registrar demanda confirmada associada ao período e ao produto | F-01, F-04 | FR-012, FR-013 |
| F-06 | Planejamento e necessidade de insumos | Explodir demanda, calcular necessidade líquida e margem de segurança | F-02, F-03, F-04, F-05 | FR-014 a FR-018 |
| F-07 | Produção, consumo e destinação | Apontar produção, consumo real, perdas e destinações | F-02, F-03, F-06 | FR-021, FR-022 |
| F-08 | CMV, margem e indicadores | Calcular valores projetados/realizados e apresentar o painel essencial | F-02 a F-07 | FR-023 a FR-025 |

## Sequência por marco

### Marco M0 — Fundação e acesso

Features: F-00 → F-01

Resultado: aplicação executável com login, resolução de tenant, RLS,
migrations Flyway no startup e provisionamento mínimo controlado. Um usuário
sem membership ativa não acessa a operação.

### Marco M1 — Base operacional e catálogo

Features: F-02 → F-04

Resultado: o produtor consegue cadastrar a base de insumos, registrar compras e
estoque, criar fichas técnicas, cadastrar produtos e montar um cardápio.

### Marco M2 — Demanda e planejamento

Features: F-05 → F-06

Resultado: o produtor registra pedidos confirmados e transforma a demanda em um
planejamento de produção com necessidade técnica, saldo disponível, rendimento,
margem de segurança e quantidade final de compra.

### Marco M3 — Execução e resultado financeiro

Features: F-07 → F-08

Resultado: o produtor aponta a produção e o consumo, destina excedentes,
calcula CMV realizado, compara planejado/comprado/consumido e acompanha margem
e indicadores.

## Escopo de cada feature

### F-00 — Fundação técnica e acesso seguro

Inclui:

- baseline do Spring Boot, Java, Maven, Vaadin e PostgreSQL;
- Flyway Community com migrations versionadas executadas no startup;
- conexão segura com PostgreSQL e configuração por ambiente;
- autenticação Supabase integrada ao Spring Security;
- resolução de identidade, membership e tenant;
- contexto de tenant por transação;
- RLS como segunda barreira;
- shell inicial da aplicação e tratamento de acesso não provisionado;
- testes de isolamento e de falha de migration.

Não inclui regras operacionais de insumos, pedidos ou produção.

### F-01 — Provisionamento da plataforma

Inclui:

- criação de tenant por PLATFORM_ADMIN;
- registro de convite e papel inicial;
- criação/ativação de membership;
- bloqueio de usuário autenticado sem vínculo ativo;
- separação entre metadados da plataforma e dados operacionais;
- auditoria mínima de criação de tenant, convite e membership.

O envio automático de e-mail pode ser detalhado durante a implementação sem
alterar o modelo de autorização definido no ADR-015.

### F-02 — Cadastro base, compras e estoque

Inclui:

- insumos e unidades-base;
- conversão entre unidade de compra e unidade-base;
- estabelecimentos;
- compras, itens, preços e histórico;
- lotes e entradas de estoque;
- movimentações rastreáveis;
- listas de insumos e notas ou recibos em imagem ou PDF, com OCR e revisão
  humana antes do cadastro ou da confirmação da compra;
- comprovante obrigatório e preço informado para cada item de compra;
- responsável operacional designado no tenant para corrigir, cancelar ou
  acrescentar itens após a confirmação da compra;
- saldo inicial opcional, descarte de insumo e ajustes manuais de entrada ou
  saída por responsável operacional, com motivo e sem saldo negativo;
- saldo disponível por movimentações, excluindo lotes vencidos sem descartar
  automaticamente seu estoque físico.

Esta feature fecha a conversão e a apresentação da quantidade efetivamente
comprada. A decisão sobre quanto planejar para compra, mesmo quando a
necessidade não corresponde a uma embalagem inteira, pertence à F-06.
A entrada de estoque segue a quantidade documentada e destinada à produção;
não há quantidade recebida separada nem perda natural automática na F-02.

### F-03 — Fichas técnicas e rendimentos

Inclui:

- receitas e insumos utilizados;
- quantidades brutas;
- modo de preparo quando necessário;
- rendimento estimado;
- rendimento real por peso e/ou porção;
- conversão de quantidade crua para quantidade pronta;
- custo normalizado da receita quando houver dados de compra.

### F-04 — Produtos e cardápios

Inclui:

- produtos com composição fixa;
- composição por receitas, porções e materiais/embalagens;
- preços aplicáveis;
- cardápio por período;
- mensagem padronizada para cópia e compartilhamento manual.

Não inclui integração direta com WhatsApp ou pedidos feitos por clientes.

### F-05 — Clientes e pedidos confirmados

Inclui:

- cadastro simples de clientes sem credenciais;
- pedidos já confirmados;
- produtos, quantidades, preços e observações;
- período ou planejamento relacionado;
- cancelamento conforme a etapa operacional alcançada.

Não inclui pipeline de pedidos não confirmados nem portal do cliente.

### F-06 — Planejamento e necessidade de insumos

Inclui:

- início da semana configurável como domingo ou segunda-feira;
- planejamento opcionalmente agrupado por semana;
- demanda derivada de pedidos confirmados;
- produção adicional;
- decomposição de produtos em receitas e insumos;
- necessidade técnica bruta;
- rendimento estimado;
- saldo disponível aproveitável;
- necessidade líquida;
- margem de segurança seletiva;
- ajuste manual da quantidade final de compra;
- CMV e margem projetados quando houver dados suficientes.

O planejamento não baixa estoque. A baixa ocorre no consumo real da produção.

### F-07 — Produção, consumo e destinação

Inclui:

- apontamento de produção;
- quantidade bruta utilizada;
- quantidade pronta obtida;
- consumo real por lote;
- movimentações de consumo, perda e descarte;
- produção disponível sem destinatário final;
- autoconsumo, doação, venda posterior e descarte;
- cancelamentos posteriores ao planejamento, compra ou produção.

Esta feature deverá fechar a semântica operacional entre Preparação, Produção
Disponível e Marmita Final sem criar estoque primário de marmitas prontas.

### F-08 — CMV, margem e indicadores

Inclui:

- CMV projetado pelo último preço normalizado;
- CMV realizado pelos lotes efetivamente consumidos;
- valorização do saldo remanescente;
- perda por descarte separada do CMV das vendas;
- margem bruta em reais e percentual;
- recuperação do custo dos insumos consumidos;
- comparação entre planejado, comprado e consumido;
- indicadores operacionais e financeiros essenciais.

Não inclui ponto de equilíbrio contábil nem custos de mão de obra, gás,
energia, depreciação ou equipamentos.

## Regras de dependência

1. F-00 deve estar funcional antes de qualquer feature operacional.
2. F-01 deve permitir provisionar o usuário de teste antes dos testes de
   qualquer dado de tenant.
3. F-02 é a base para unidades, preços, lotes e saldos utilizados nos cálculos.
4. F-03 e F-04 devem existir antes da explosão de produtos em insumos.
5. F-05 deve existir antes do planejamento baseado em demanda confirmada.
6. F-06 deve estar validada antes de apontar produção baseada em planejamento.
7. F-07 deve gerar dados realizados antes da validação completa de F-08.

## Critério de pronto por feature

Uma feature só pode ser considerada pronta quando:

- sua especificação estiver confirmada e com requisitos rastreáveis;
- decisões e ambiguidades estiverem registradas em context.md quando
  necessário;
- o design e as tarefas existirem quando a complexidade exigir;
- os fluxos de sucesso e falha estiverem cobertos por testes;
- a autorização e o tenant estiverem validados;
- as alterações de schema estiverem em migrations Flyway;
- a atomicidade ACID estiver preservada;
- o build e os testes aplicáveis passarem;
- houver verificação independente da feature antes do encerramento.

## Estado da sequência

F-00 e F-01 estão concluídas. A F-02 tem especificação, desenho e tarefas
propostos em `.specs/features/f-02-base-purchases-stock/`; a execução ainda
não começou. A ordem seguinte permanece a definida na matriz de dependências
deste roadmap.
