# PRD — TAAS Gestão de Produção V0

## Status do documento

- **Produto:** TAAS Gestão de Produção
- **Versão:** V0
- **Status:** especificação inicial para implementação
- **Artefato:** `gestao-producao`
- **Package base:** `br.com.taas.saas.gestaoproducao`
- **Fonte de descoberta:** [Ideia de Produto - Gestão de Marmitas](https://docs.google.com/document/d/10G-BymeZsBdu55I5lrH8AotA6dFq4Pygl0uh8ygLnY0/edit)

Este documento transforma a descoberta do produto em requisitos operacionais para a
primeira entrega. A marmitaria é o primeiro caso real de validação, mas o modelo
deve evitar acoplamentos desnecessários ao conceito de marmita quando o conceito
for naturalmente aplicável a pequenos produtores de alimentos.

## 1. Visão do produto

O produto centraliza o planejamento de produção, os insumos, as compras, o
estoque, o consumo, o custo e a margem de pequenos produtores de alimentos.

Na V0, o sistema deve substituir a planilha utilizada pelo produtor e permitir
executar o fluxo completo:

```text
Insumos e fichas técnicas
  → cardápio
  → pedidos confirmados
  → planejamento de produção
  → necessidade de insumos
  → estoque, rendimento e margem de segurança
  → compras
  → produção e consumo real
  → movimentações de estoque
  → CMV, margem e indicadores
```

## 2. Objetivos da V0

A V0 deve permitir ao produtor:

- cadastrar os insumos utilizados na operação;
- registrar compras e acompanhar o histórico de preços;
- cadastrar receitas, fichas técnicas e rendimentos;
- cadastrar produtos com composição fixa;
- montar cardápios e gerar uma mensagem para compartilhamento manual;
- registrar pedidos já confirmados;
- planejar produção para encomenda, pronta entrega ou operação híbrida;
- calcular a necessidade técnica e líquida de insumos;
- considerar saldo disponível, rendimento e margem de segurança;
- registrar compras e entradas de estoque;
- apontar produção, consumo real, perdas e destinações;
- calcular CMV projetado e realizado, margens e indicadores essenciais.

## 3. Critérios de sucesso

A V0 será considerada bem-sucedida quando o produtor puder abandonar a planilha
para a operação cotidiana e responder, a partir do sistema:

- quanto custa produzir;
- quanto custa cada produto;
- quanto cada produto gera de margem;
- o que foi vendido ou solicitado no ciclo;
- o que precisa ser produzido;
- quanto precisa ser comprado;
- quanto do custo dos insumos consumidos já foi recuperado;
- qual produto apresenta melhor ou pior margem.

## 4. Usuários e contexto de acesso

### 4.1 Usuário

O usuário é a pessoa que opera o sistema. Na V0, o sistema possui login e o
usuário acessa somente um tenant.

O cliente da marmitaria é um conceito separado do usuário do sistema. O cliente
é utilizado para identificar quem solicitou um pedido e não possui credenciais
na V0.

### 4.2 Tenant

Multi-tenancy é requisito transversal da V0:

- os dados de tenants diferentes devem permanecer logicamente isolados;
- cada usuário acessa apenas o tenant ao qual está vinculado;
- o usuário não alterna entre tenants na V0;
- funcionalidades de planos, cobrança, onboarding comercial, administração
  avançada e permissões complexas estão fora do escopo;
- a regra de associação inicial entre usuário e tenant permanece uma decisão de
  onboarding a ser detalhada no PRD de execução.

## 5. Linguagem do domínio

Os conceitos principais da V0 são:

- **Insumo:** ingrediente ou material utilizado na operação;
- **Compra:** registro do desembolso e dos itens adquiridos;
- **Estabelecimento:** local onde a compra foi realizada;
- **Lote:** entrada identificável de insumo, com quantidade, custo e datas
  relevantes quando aplicável;
- **Movimentação de Estoque:** entrada, consumo, ajuste, descarte ou outra saída
  rastreável;
- **Saldo Disponível:** quantidade aproveitável derivada das movimentações;
- **Receita:** preparação composta por insumos e com rendimento definido;
- **Ficha Técnica:** estrutura de ingredientes, quantidades, preparo e rendimento
  de uma receita;
- **Rendimento:** relação entre quantidade bruta utilizada e quantidade pronta;
- **Produto Produzido:** produto comercial composto por receitas, porções e
  materiais;
- **Marmita:** primeiro produto validado na operação real;
- **Cardápio:** conjunto de produtos ofertados em um período, com preços e
  informações de apresentação;
- **Cliente:** pessoa associada a um pedido;
- **Pedido:** solicitação confirmada de produtos para um período;
- **Demanda:** quantidade necessária derivada dos pedidos e de produção
  adicional planejada;
- **Planejamento de Produção:** definição do que se pretende produzir;
- **Produção Disponível:** quantidade de componentes ou preparações produzida
  sem destinatário final definido;
- **Preparação:** componente produzido, como arroz, feijão, carne, purê ou
  legumes;
- **Apontamento de Produção:** registro do que foi efetivamente utilizado e
  produzido;
- **Margem de Segurança:** acréscimo operacional aplicado depois do cálculo
  técnico da necessidade líquida;
- **CMV Projetado:** custo estimado antes da compra e do consumo real;
- **CMV Realizado:** custo dos insumos efetivamente consumidos.

## 6. Escopo funcional da V0

### 6.1 Insumos e unidades

O sistema deve permitir cadastrar insumos com uma unidade-base apropriada, como
gramas, quilogramas, mililitros, litros ou unidades.

Uma compra pode utilizar unidade diferente da unidade-base. O sistema deve
converter a quantidade comprada para a unidade-base de estoque.

O PRD de execução deve detalhar as regras de apresentação comercial e
arredondamento quando a necessidade não corresponder a uma embalagem inteira.

### 6.2 Estabelecimentos e compras

O sistema deve permitir:

- cadastrar estabelecimentos de forma simples;
- registrar data, estabelecimento, itens, insumos, quantidade, unidade e preço
  pago;
- calcular o custo unitário normalizado;
- manter o histórico de preços;
- vincular uma compra ao tenant correto;
- anexar o comprovante como arquivo, sem leitura automática;
- gerar a entrada correspondente no estoque.

Compra não é sinônimo de consumo. O insumo comprado que não for consumido
permanece no estoque e pode ser utilizado em ciclos futuros.

### 6.3 Receitas e fichas técnicas

Cada receita deve possuir:

- nome;
- insumos e quantidades;
- modo de preparo quando necessário;
- rendimento estimado;
- possibilidade de registrar rendimento real;
- custo total e custo normalizado por peso ou porção.

Na V0, o rendimento será controlado por peso e/ou porções. Não será necessário
suportar rendimento por unidades produzidas.

O apontamento de produção deve permitir registrar a quantidade bruta utilizada
e a quantidade pronta obtida. Por exemplo, 1.000 g de insumo cru podem resultar
em 780 g de preparação pronta.

### 6.4 Produtos e composição

O produto é composto por receitas, porções e materiais ou embalagens. A
composição do produto é fixa conforme a ficha técnica e o cardápio.

Modificações pontuais solicitadas por clientes podem ser registradas como
observação do pedido, mas não alteram automaticamente a ficha técnica, a
necessidade de insumos, o CMV ou a margem na V0.

A marmita final não será mantida como estoque de produto pronto. A montagem
ocorre conforme os pedidos.

### 6.5 Cardápio

O sistema deve permitir:

- selecionar os produtos ofertados em um período;
- informar os preços aplicáveis;
- registrar informações de apresentação;
- gerar uma mensagem padronizada para cópia e compartilhamento manual.

Integração direta com WhatsApp e envio automático de mensagens estão fora da
V0.

### 6.6 Pedidos

Somente pedidos já confirmados são registrados na V0. Um pedido deve conter:

- cliente;
- período ou planejamento relacionado;
- produtos;
- quantidades;
- preços aplicáveis;
- observações;
- situação de cancelamento quando aplicável.

Pedidos confirmados formam a demanda mínima do planejamento. O planejamento
também pode incluir produção adicional.

### 6.7 Semana e planejamento de produção

O início da semana de produção é configurável, como domingo ou segunda-feira.
A semana pode ser utilizada apenas como agrupador operacional e não é
obrigatória em todos os planejamentos.

O Planejamento de Produção é o objeto operacional central. Ele funciona como
uma ordem de produção simplificada, sem reproduzir o modelo industrial.

O planejamento deve permitir:

- existir sem pedidos, para pronta entrega ou produção adicional;
- consolidar pedidos confirmados;
- definir quantidades pretendidas de produção;
- acrescentar unidades além da demanda confirmada;
- decompor produtos em receitas e receitas em insumos;
- mostrar quantidade planejada por produto e preparação;
- mostrar necessidade de insumos;
- mostrar custo e margem projetados quando houver dados suficientes.

### 6.8 Planejamento de insumos

O Planejamento de Insumos deve apresentar, no mínimo:

- necessidade técnica bruta;
- impacto do rendimento estimado;
- saldo disponível aproveitável;
- necessidade líquida;
- margem de segurança;
- quantidade calculada para aquisição;
- quantidade final planejada para aquisição após ajuste manual.

A margem de segurança é aplicada depois do cálculo técnico de rendimento e da
necessidade líquida. O usuário pode:

- aplicar um percentual a todos os itens;
- desmarcar itens específicos;
- usar percentuais diferentes;
- ajustar manualmente a quantidade final planejada.

O planejamento não baixa estoque. A baixa ocorre no consumo real apontado na
produção.

### 6.9 Estoque, lotes e movimentações

O estoque deve ser controlado por movimentações rastreáveis, e não somente por
um saldo editável.

Movimentações mínimas:

- entrada decorrente de compra;
- consumo real na produção;
- ajuste;
- descarte ou perda;
- destinação de produção disponível quando aplicável.

Os lotes devem permitir registrar quantidade, custo, data de compra e, quando
aplicável, datas de fabricação, embalagem e validade.

O saldo remanescente de um ciclo pode ser utilizado em ciclos seguintes se
estiver apto para consumo. Lotes consumidos devem ser identificáveis quando
isso for aplicável à operação.

O controle de lote, data de produção e validade de componentes preparados ainda
deve ser detalhado no PRD de execução.

### 6.10 Produção disponível e destinação

A V0 não mantém estoque de marmitas finais prontas para venda. Ela controla
quantidades de componentes ou preparações produzidas sem usuário final definido.

Uma produção disponível pode ser:

- vinculada a um novo pedido;
- destinada a autoconsumo;
- doada;
- vendida posteriormente;
- descartada.

O descarte reduz o estoque e registra uma perda. Autoconsumo, doação e venda
posterior são destinações diferentes e não devem ser confundidos com descarte.

### 6.11 Cancelamentos

As regras da V0 são:

- antes do planejamento: o pedido pode ser removido e os insumos recalculados;
- depois do planejamento e antes da compra: o pedido pode ser removido e o
  planejamento recalculado;
- depois da compra: a compra não é revertida e os insumos permanecem em
  estoque;
- depois da produção de componentes: a produção permanece disponível e deve
  receber uma destinação;
- depois da montagem da marmita: o pedido deve permanecer identificável e o
  produto deve receber uma destinação.

A preservação do histórico de cancelamentos depois da compra deve ser detalhada
no PRD de execução.

### 6.12 Custos e CMV

O sistema deve distinguir custo projetado de CMV realizado.

#### Custo projetado

O custo projetado utiliza o último preço de compra normalizado para a
unidade-base do insumo. Ele apoia o planejamento e a visão de margem antes da
compra.

#### CMV realizado

O CMV realizado utiliza o custo real dos lotes efetivamente consumidos. Quando
mais de um lote for consumido, o sistema calcula o custo pela soma das
quantidades consumidas multiplicadas pelo custo unitário de cada lote.

O custo da receita considera o custo dos insumos consumidos e o rendimento real.
O custo da porção utiliza a quantidade da receita aplicada ao produto.

Saldo remanescente continua valorizado no estoque e não entra no CMV até ser
consumido. Descarte é perda separada do CMV das vendas.

### 6.13 Recuperação do custo dos insumos

A métrica da V0 será denominada **recuperação do custo dos insumos consumidos
no ciclo**.

O cálculo considera somente o custo dos insumos efetivamente consumidos na
produção do ciclo. Compras parcialmente utilizadas entram proporcionalmente ao
consumo realizado. O saldo não utilizado permanece no estoque e será
considerado em ciclos futuros.

Essa métrica não representa:

- recuperação do valor total desembolsado em compras;
- ponto de equilíbrio contábil;
- cobertura de custos não incluídos na V0, como mão de obra, gás, energia,
  depreciação e equipamentos.

### 6.14 Indicadores e dashboard

O dashboard operacional e financeiro deve apresentar, conforme os dados
disponíveis:

- pedidos e unidades por produto;
- receita prevista e realizada;
- CMV projetado e realizado;
- margem bruta em reais;
- margem percentual;
- CMV médio;
- ticket médio;
- investimento ou custo recente em insumos;
- recuperação do custo dos insumos consumidos;
- evolução dos custos dos principais insumos;
- comparação de rentabilidade entre produtos;
- comparação entre planejado, comprado e consumido.

## 7. Requisitos funcionais

| ID | Requisito |
| --- | --- |
| FR-001 | O sistema deve autenticar o usuário. |
| FR-002 | O sistema deve restringir cada usuário ao tenant vinculado. |
| FR-003 | O sistema deve cadastrar insumos e unidades-base. |
| FR-004 | O sistema deve converter unidades de compra para a unidade-base. |
| FR-005 | O sistema deve cadastrar estabelecimentos. |
| FR-006 | O sistema deve registrar compras, itens, preços e histórico. |
| FR-007 | O sistema deve permitir anexar comprovantes sem leitura automática. |
| FR-008 | O sistema deve cadastrar receitas, fichas técnicas e rendimentos. |
| FR-009 | O sistema deve registrar rendimento real de preparações. |
| FR-010 | O sistema deve cadastrar produtos com composição fixa. |
| FR-011 | O sistema deve montar cardápios e gerar mensagem para cópia. |
| FR-012 | O sistema deve cadastrar clientes simples. |
| FR-013 | O sistema deve registrar pedidos confirmados. |
| FR-014 | O sistema deve consolidar demanda por planejamento. |
| FR-015 | O sistema deve planejar produção adicional além dos pedidos. |
| FR-016 | O sistema deve explodir produtos em receitas e insumos. |
| FR-017 | O sistema deve calcular necessidade líquida, rendimento e margem de segurança. |
| FR-018 | O sistema deve permitir ajuste manual da quantidade planejada para compra. |
| FR-019 | O sistema deve controlar estoque por movimentações. |
| FR-020 | O sistema deve registrar lotes e datas relevantes quando aplicável. |
| FR-021 | O sistema deve registrar produção, consumo real e rendimento obtido. |
| FR-022 | O sistema deve registrar perdas, descartes e destinações. |
| FR-023 | O sistema deve calcular CMV projetado e realizado. |
| FR-024 | O sistema deve calcular margens e recuperação do custo consumido. |
| FR-025 | O sistema deve apresentar dashboard operacional e financeiro essencial. |

## 8. Regras transversais

### 8.1 Determinismo

Cálculos de quantidade, rendimento, estoque, custo, CMV e margem devem ser
determinísticos e executados pela aplicação. A V0 deve funcionar plenamente sem
inteligência artificial.

### 8.2 Isolamento de tenant

Toda informação operacional deve pertencer a um tenant. Nenhuma consulta,
alteração, movimentação, anexação ou indicador pode atravessar o contexto do
tenant autenticado.

### 8.3 Rastreabilidade

Entradas, consumos, ajustes, perdas, descartes e destinações que alterem saldo
devem ser rastreáveis. O sistema não deve depender de edição direta de saldo
como mecanismo primário de controle.

### 8.4 Fonte dos cálculos

O sistema deve distinguir claramente valores projetados de valores realizados e
não deve apresentar a recuperação do custo dos insumos como ponto de equilíbrio
contábil.

## 9. Critérios de aceitação ponta a ponta

### Cenário 1 — Preparação da operação

1. O usuário acessa o tenant correto.
2. O usuário cadastra insumos, unidades-base e estabelecimentos.
3. O usuário registra compras com preços e lotes.
4. O estoque mostra as entradas correspondentes.

### Cenário 2 — Ficha, produto e cardápio

1. O usuário cadastra uma receita com insumos, quantidades e rendimento.
2. O usuário registra um rendimento real.
3. O usuário cadastra um produto com composição fixa.
4. O usuário monta um cardápio e gera a mensagem para cópia.

### Cenário 3 — Pedido e planejamento

1. O usuário registra somente pedidos confirmados.
2. O usuário cria um planejamento com base nos pedidos.
3. O usuário acrescenta produção adicional quando necessário.
4. O sistema explode produtos em receitas e insumos.
5. O sistema desconta o saldo disponível.
6. O usuário aplica margem de segurança seletiva.
7. O usuário ajusta a quantidade final planejada para compra.

### Cenário 4 — Compra, produção e consumo

1. O usuário registra a compra planejada.
2. O sistema gera a entrada dos lotes.
3. O usuário aponta a quantidade bruta utilizada.
4. O usuário aponta a quantidade pronta obtida.
5. O sistema registra o consumo real e atualiza o saldo.
6. O sistema calcula CMV realizado com o custo dos lotes consumidos.

### Cenário 5 — Cancelamento e destinação

1. Um pedido cancelado antes da produção pode ser removido e recalculado.
2. Insumos comprados não são revertidos automaticamente.
3. Componentes produzidos permanecem disponíveis.
4. O usuário registra autoconsumo, doação, venda posterior ou descarte.
5. Somente o descarte é tratado como perda física de estoque.

### Cenário 6 — Indicadores

1. O sistema apresenta CMV projetado antes da compra quando houver dados.
2. O sistema apresenta CMV realizado após consumo.
3. O sistema apresenta margem projetada e realizada quando houver dados.
4. O sistema calcula a recuperação do custo dos insumos consumidos no ciclo.

## 10. Fora do escopo da V0

Não fazem parte da primeira entrega:

- portal ou login de clientes;
- pedidos realizados diretamente por clientes;
- integração com WhatsApp ou envio automático de mensagens;
- OCR, visão computacional ou leitura automática de comprovantes;
- entrada automática de estoque por imagem;
- agentes, recomendações ou análises baseadas em inteligência artificial;
- pesquisa externa de preços;
- recomendação de mercados ou fornecedores;
- cadastro avançado de fornecedores;
- otimização inteligente de compras;
- cobrança, assinatura ou planos SaaS;
- administração avançada de organizações e permissões;
- custos de mão de obra, gás, energia, depreciação e equipamentos;
- funcionalidades amplas de ERP;
- microserviços, Kafka ou Kubernetes;
- estoque de marmitas finais prontas como produto armazenado.

## 11. Baseline tecnológico já definido

As decisões tecnológicas iniciais da V0 são:

- Java 21;
- Spring Boot 4.1.1;
- Maven;
- Vaadin como interface web da aplicação;
- PostgreSQL gerenciado;
- Supabase Storage para comprovantes e outros anexos;
- Supabase Auth para autenticação;
- Spring Security como camada de proteção da aplicação;
- vínculo usuário–tenant mantido no domínio próprio;
- Docker com build multi-stage;
- Eclipse Temurin JDK no estágio de build;
- Eclipse Temurin JRE no estágio de runtime;
- configuração e secrets fornecidos em runtime;
- hospedagem inicial em plano gratuito, aceitando pausas e cold starts;
- alta disponibilidade fora do requisito da V0.

O provedor específico de hospedagem da aplicação ainda deve ser escolhido. A
imagem final não deve conter código-fonte, compilador, ferramentas de
desenvolvimento ou secrets.

## 12. Pontos abertos para detalhamento antes da implementação

Os pontos abaixo não ampliam o escopo, mas precisam ser fechados no detalhamento
do PRD e nos critérios de implementação:

1. Definir o fluxo de associação inicial do usuário ao tenant.
2. Definir lote, data de produção e validade para preparações armazenadas.
3. Definir como preservar o histórico de cancelamentos depois de uma compra.
4. Formalizar a diferença entre Produção Disponível, Preparação e Marmita Final.
5. Definir arredondamento e apresentação comercial quando a compra exigir uma
   embalagem inteira.
6. Definir o provedor de hospedagem compatível com o plano gratuito da V0.

## 13. Estado atual do projeto

O projeto contém atualmente o esqueleto gerado pelo Spring Initializr, com
Spring Boot, Maven, Java 21, classe principal, configuração YAML e teste de
carregamento do contexto. As funcionalidades descritas neste PRD ainda não
estão implementadas.
