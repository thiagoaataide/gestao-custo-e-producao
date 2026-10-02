# F-02 — Contexto das decisões funcionais

**Reunido:** 22 de setembro de 2026
**Spec:** `.specs/features/f-02-base-purchases-stock/spec.md`
**Status:** Decisões reunidas; design e tarefas propostos, sem implementação iniciada.

## Limite da feature

A F-02 cria os cadastros de insumos e estabelecimentos, a leitura assistida de
listas e comprovantes, o registro de compras e suas entradas, e o controle de
estoque por origem e movimentação. Planejamento de compra, produção e CMV
apresentado ao usuário pertencem às features posteriores.

## Decisões confirmadas pelo usuário

### Insumos e unidades

- Estoque por peso usa gramas; volume usa mililitros; itens contáveis usam
  unidades. `2 kg 278 g` são `2.278 g`.
- A marca não distingue o insumo. A variedade distingue: açúcar cristal e
  mascavo ou páprica doce e defumada não devem ser confundidos.
- Se a descrição da nota for genérica, o usuário esclarece se corresponde a
  insumo existente ou se deve criar um novo tipo específico.
- Nome idêntico ao de um insumo do mesmo tenant é bloqueado; nome parecido
  apenas gera aviso para conferência.
- Uma lista digitada ou manuscrita em imagem ou PDF pode sugerir insumos para
  cadastro. O usuário revisa e seleciona os itens antes de gravar.

### Documento e compra

- Compra exige nota fiscal ou recibo em imagem ou PDF, com quantidade e preço
  por item. Lista simples de nomes não fundamenta compra.
- OCR propõe informações; o usuário revisa e completa o que estiver ausente ou
  ilegível antes da confirmação. O arquivo original fica preservado.
- A nota pode misturar compras da operação e da casa. O usuário seleciona
  apenas os itens ou parcelas de linha destinados à produção.
- O preço entra no histórico da compra, sem valor obrigatório no cadastro do
  insumo. Desconto discriminado no item compõe seu valor líquido e, se apenas
  parte da linha entrar na compra, é distribuído proporcionalmente à
  quantidade selecionada. Desconto apenas no total do documento não é
  distribuído entre os itens.
- Quantidade que entra no estoque corresponde à parcela documentada e
  selecionada. Não há uma quantidade recebida diferente, nem perda natural
  automática no MVP.
- Chave legível na nota serve para prevenir duplicidade. Não se importa XML nem
  se consulta serviço fiscal externo pela chave nesta entrega.
- Documento idêntico já confirmado bloqueia duplicata sem chave; documento
  apenas semelhante gera alerta e permite conferência humana.
- Qualquer usuário operacional ativo pode confirmar compra. Após confirmação,
  somente responsável operacional designado pode corrigir, cancelar ou
  acrescentar itens. A plataforma designa um ou mais responsáveis sem acesso
  aos dados operacionais.
- Correção de preço recalcula custos derivados, inclusive consumo já ocorrido.
  Correção de quantidade não pode ser menor que a soma de todas as saídas já
  vinculadas à mesma entrada. Cancelamento de compra ou item exige quantidade integral
  ainda disponível na própria origem e preserva histórico.
- Item esquecido pode ser adicionado à compra original sem duplicar entradas
  dos itens já registrados.

### Estoque

- Saldo inicial opcional pode ser informado no cadastro do insumo, sem criar
  compra fictícia e sem exigir preço; custo desconhecido não vira zero.
- A validade retira lote do saldo aproveitável, mas não baixa automaticamente
  o estoque físico. O lote é aproveitável até o fim da data de validade.
  Descarte de insumo é registrado na F-02.
- Somente responsável operacional designado pode registrar ajuste manual,
  sempre com motivo. O ajuste pode aumentar ou reduzir estoque, sem permitir
  saldo negativo. Ajuste positivo não exige preço médio; se o custo não for
  informado, permanece desconhecido. A decisão entre origem própria e origem
  existente será fechada antes da T16. Ajustes e perdas permanecem rastreáveis.
- Se o OCR não conseguir ler o documento, o original continua anexado, o
  usuário recebe aviso claro da falha e pode revisar/preencher os dados
  manualmente. Nenhuma compra ou entrada de estoque é confirmada sem ação
  humana.
- Consumo na produção, perdas de preparação e destinação de produtos são F-07.
- Compras podem superar necessidade técnica: comprar 1 kg para usar 570 g
  permite manter 430 g para uso futuro. A decisão planejada é F-06.

## Decisão pendente para tarefa futura

Antes da T16, decidir se um ajuste positivo sem compra cria sempre uma origem
própria identificável ou se pode ser associado a uma origem existente quando
houver justificativa. A entrada não exige preço médio; sem custo informado,
seu custo permanece desconhecido. A transcrição “fruita ambiental” não foi
interpretada como regra de domínio por estar ambígua.

## Margem para o design

- Decisão do usuário em 2 de outubro de 2026: usar OCR.space Free API na T12,
  com chave privada no backend. A aplicação deve depender de uma porta de OCR
  para permitir substituição futura do provedor; limites do plano e fallback
  manual ficam registrados em `design.md` e `tasks.md`.
- Definir contratos, armazenamento de anexos e tratamento de indisponibilidade,
  mantendo a revisão humana obrigatória.
- Definir representação da designação operacional e da trilha de correções,
  sem conceder acesso operacional a administradores da plataforma.
- Definir precisão, arredondamento, persistência de custos e proteção contra
  concorrência, mantendo os totais do documento e os resultados da spec.
- Definir telas e ordem dos passos de revisão sem criar compra ou entrada
  antes da confirmação.

## Ideias adiadas

- Permitir item de compra sem preço informado foi citado como hipótese futura,
  sem versão atribuída. Não altera a regra obrigatória da V0.
- Perda natural automática do insumo comprado pode ser avaliada no futuro;
  nesta entrega qualquer baixa exige movimento explícito.
