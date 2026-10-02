# F-02 — Cadastro base, compras e estoque

**Status:** Especificada, com desenho e tarefas propostos para revisão.
**Escopo:** V0
**Fontes:** `docs/PRD-V0.md` (FR-003 a FR-007, FR-019, FR-020 e
FR-022 para descarte de insumo),
`docs/ROADMAP-V0.md` e decisões do usuário registradas em `context.md`.

## Problema

A operação precisa cadastrar insumos identificáveis, importar notas fiscais e
recibos, registrar somente a parte destinada à produção e conhecer o saldo
real por entrada. Erros de leitura, seleção ou preço precisam ser corrigidos
sem apagar o documento nem o histórico de estoque.

## Objetivos

- [ ] Cadastrar insumos, estabelecimentos e unidades-base consistentes.
- [ ] Usar OCR de imagem ou PDF como sugestão, com decisão humana antes da
      criação de insumos e da confirmação de compras.
- [ ] Registrar compras documentadas, preços, lotes e saldos rastreáveis.
- [ ] Corrigir, complementar ou cancelar compras confirmadas com autorização e
      sem permitir saldo negativo ou histórico apagado.
- [ ] Registrar saldo inicial, descartes de insumo e ajustes manuais auditáveis.

## Fora do escopo

| Item | Destino ou motivo |
| --- | --- |
| XML de NF-e e consulta fiscal pela chave | Não pertencem à V0. |
| Lista sem quantidade e preço como comprovante de compra | Serve apenas para sugerir insumos. |
| Compra sem nota fiscal ou recibo, ou item sem preço | A V0 exige ambos. |
| Cálculo da necessidade e quantidade planejada para comprar | F-06. |
| Fichas técnicas e rendimentos | F-03. |
| Consumo na produção, perda de preparação e destinação de produto | F-07. |
| CMV e margem projetados ou realizados na interface | F-08; a F-02 preserva os dados necessários ao recálculo. |
| Perda natural automática entre compra e uso | Não entra na V0. |
| Inventário completo ou estoque de marmitas prontas | Não faz parte desta feature. |
| Separação por marca ou preço fixo no cadastro de insumo | O preço pertence ao histórico de compras. |

## Conceitos e invariantes

- **Insumo:** tipo específico usado na operação, independente de marca.
  `Açúcar cristal` e `açúcar mascavo` são insumos distintos; `açúcar` exige
  esclarecimento quando puder representar mais de um tipo.
- **Documento:** imagem ou PDF original de nota fiscal ou recibo, mantido mesmo
  quando contém itens pessoais que não entram na compra operacional.
- **Compra:** parte do documento destinada à operação, com data,
  estabelecimento e ao menos um item confirmado com insumo, quantidade,
  unidade e preço.
- **Entrada/lote:** origem identificável de estoque gerada por um item de
  compra, saldo inicial ou ajuste positivo. Movimentações posteriores apontam
  a origem afetada para impedir que saldo de outra entrada encubra uma baixa.
- **Saldo físico:** soma das entradas menos saídas rastreadas. **Saldo
  aproveitável:** parcela do saldo físico cujos lotes não estão vencidos.
  Nenhum saldo é editado diretamente.
- **Unidade-base:** grama (`g`) para peso, mililitro (`ml`) para volume e
  unidade (`un`) para contagem. Conversões entre grandezas não são implícitas.
- **Autorização:** usuário operacional ativo pode cadastrar insumos e confirmar
  compras. Correção, complementação e cancelamento após confirmação e ajuste
  manual exigem designação de responsável operacional no mesmo tenant.

## Pressupostos e questões para revisão

| Ponto | Comportamento proposto | Situação |
| --- | --- | --- |
| Seleção parcial com desconto no item | A parcela selecionada recebe a fração proporcional do valor líquido daquela linha; o total selecionado e o custo unitário devem permanecer consistentes. | Confirmado pelo usuário. |
| Correção de quantidade após outras saídas | A nova quantidade não pode ser inferior à soma de consumo, descarte e outras saídas já vinculadas à entrada; saídas antigas não são apagadas. | Confirmado pelo usuário. |
| Ajuste positivo | Registra uma entrada identificável sem exigir preço médio. Se não houver custo informado, ele permanece desconhecido (`NULL`); uma correção documentada da compra pertence à origem original. | Não exigir preço médio e preservar custo desconhecido quando omitido: confirmado. Origem própria para o ajuste: pendente de decisão antes da T16. |
| Validade informada | O lote é aproveitável até o fim da data de validade e deixa de ser aproveitável no dia seguinte, sem baixa automática. | Confirmado pelo usuário. |
| Duplicidade de insumo | Nome idêntico no mesmo tenant gera aviso e impede segundo cadastro; similaridade apenas avisa. | Confirmado pelo usuário. |
| Falha de OCR | O documento original continua anexado ao processo; o usuário recebe aviso claro de que a leitura não foi possível e pode revisar/preencher os dados manualmente. Sem confirmação humana, não há compra nem alteração de estoque. | Confirmado pelo usuário. |

**Questões abertas:** confirmar antes da T16 se ajuste positivo cria sempre
origem própria ou pode ser atribuído a uma origem existente, exceto quando a
correção documentada claramente pertence à compra original. O custo continua
desconhecido quando omitido; tratamento de eventual valor opcional não implica
cálculo de preço médio. Limites de arquivo, provedor de OCR, precisão e
arredondamento interno, representação da designação e telas estão propostos em
`design.md`; não alteram os resultados funcionais definidos aqui.

## Histórias e critérios de aceitação

### P1 — Cadastrar insumos e estabelecimentos

Como usuário operacional, quero identificar insumos e estabelecimentos para
registrar compras sem misturar variantes nem marcas.

1. **F02-01:** QUANDO cadastrar um insumo, ENTÃO o sistema exige nome
   específico e uma unidade-base entre `g`, `ml` e `un`; rejeita nome vazio,
   unidade inválida e nome equivalente já cadastrado no tenant.
2. **F02-02:** QUANDO o nome puder indicar variantes diferentes, ENTÃO o
   sistema pede que o usuário escolha um insumo existente ou especifique o
   tipo antes de salvar; marca comercial não gera insumo distinto.
3. **F02-03:** QUANDO cadastrar ou selecionar estabelecimento, ENTÃO ele fica
   disponível apenas ao tenant corrente; a compra retém a identificação do
   estabelecimento usada em sua confirmação.

**Teste independente:** cadastrar açúcar cristal e mascavo, impedir um segundo
cadastro idêntico, avisar sobre `açúcar` genérico e registrar um mercado.

### P1 — Importar lista para sugerir insumos

Como usuário operacional, quero ler uma lista digitada ou manuscrita para
acelerar o cadastro sem criar insumos por engano.

1. **F02-04:** QUANDO enviar imagem ou PDF de lista, ENTÃO o sistema apresenta
   os nomes sugeridos pelo OCR, inclusive os incertos, sem criar insumos.
2. **F02-05:** QUANDO revisar a lista, ENTÃO o usuário pode selecionar nomes,
   corrigir grafia, escolher insumo existente ou informar nome específico e
   unidade-base para os novos; somente os novos itens confirmados são criados.
3. **F02-06:** QUANDO a lista contiver apenas nomes, ENTÃO ela não pode ser
   confirmada como compra nem produzir entrada de estoque.

**Teste independente:** enviar lista com `páprica`, corrigir para `páprica
defumada`, desmarcar um item e confirmar somente os selecionados.

### P1 — Revisar documento e confirmar compra

Como usuário operacional, quero registrar a parcela da nota fiscal ou recibo
destinada à produção com quantidade e valor corretos.

1. **F02-07:** QUANDO iniciar uma compra, ENTÃO o sistema exige imagem ou PDF
   original de nota fiscal ou recibo; rejeita outro tipo de arquivo e não
   permite confirmar compra sem documento preservado.
2. **F02-08:** QUANDO ler o documento, ENTÃO o OCR sugere data,
   estabelecimento, chave quando visível, itens, quantidades, unidades, preços
   e descontos legíveis; o usuário vê os dados propostos antes de confirmar.
3. **F02-09:** QUANDO revisar a compra, ENTÃO o usuário pode excluir itens
   pessoais, selecionar parte da quantidade de uma linha, corrigir dados
   ilegíveis e vincular cada item a insumo existente ou criar insumo específico;
   o arquivo completo continua associado à compra.
4. **F02-10:** QUANDO confirmar, ENTÃO cada item selecionado precisa ter insumo
   específico, quantidade positiva, unidade compatível, preço conhecido e
   parcela selecionada não maior que a documentada; compra sem item válido
   não é confirmada. Valor ilegível deve ser informado na revisão.
5. **F02-11:** QUANDO confirmar uma compra válida, ENTÃO a operação cria compra,
   itens, histórico de preços, entradas identificáveis e movimentos em uma
   única transação; falha em qualquer etapa deixa tudo sem confirmação nem
   entrada parcial.

**Teste independente:** importar comprovante misto, selecionar dois de três
pacotes de 500 g a R$ 12 cada e conferir entrada de 1.000 g por R$ 24,
mantendo o documento completo.

### P1 — Converter quantidade e custo

Como usuário operacional, quero ver a quantidade documentada convertida para
uma unidade-base para usar saldos e preços comparáveis.

1. **F02-12:** QUANDO registrar peso, ENTÃO `2 kg 278 g` vira `2.278 g` e
   `1 kg` vira `1.000 g`; volume usa `ml` e itens contáveis usam `un`.
   Conversão de peso para volume ou vice-versa é rejeitada sem regra específica.
2. **F02-13:** QUANDO a nota discriminar embalagens, ENTÃO o registro mantém a
   apresentação comercial e converte somente a quantidade selecionada para
   produção, sem entrada separada de quantidade física recebida.
3. **F02-14:** QUANDO houver desconto no item, ENTÃO o custo desse item usa seu
   valor líquido; quando só parte da linha for selecionada, seu valor líquido
   é proporcional à quantidade escolhida. Desconto apenas no total do
   documento não é rateado nem altera o custo unitário dos itens; o custo
   unitário normalizado decorre do valor e da quantidade selecionados.

**Teste independente:** comparar compras de 1 kg e 500 g do mesmo insumo,
conferir ambos em gramas e seus preços por grama.

### P1 — Evitar duplicidade de compras

Como usuário operacional, quero receber alerta antes de confirmar um documento
já usado, sem bloquear por mera semelhança.

1. **F02-15:** QUANDO uma nota tem chave legível já vinculada a compra
   confirmada no mesmo tenant, ENTÃO o sistema avisa e bloqueia nova
   confirmação, inclusive em tentativas concorrentes.
2. **F02-16:** QUANDO não há chave e o arquivo é idêntico ao de compra
   confirmada no tenant, ENTÃO o sistema avisa e bloqueia segunda confirmação.
3. **F02-17:** QUANDO outra foto tiver data, estabelecimento e valor
   semelhantes, mas não chave ou arquivo idênticos, ENTÃO o sistema avisa
   para conferência e permite decisão humana de prosseguir.

**Teste independente:** repetir a mesma nota, depois apresentar outra foto
sem chave mas com metadados semelhantes e verificar resultados diferentes.

### P1 — Consultar lotes e saldos

Como usuário operacional, quero saber a origem e o saldo físico e aproveitável
de cada insumo.

1. **F02-18:** QUANDO confirmar item de compra, ENTÃO o sistema identifica sua
   entrada, quantidade, custo, data de compra e datas de fabricação,
   embalagem e validade quando informadas.
2. **F02-19:** QUANDO consultar estoque, ENTÃO o saldo físico de cada entrada e
   insumo deriva de movimentos, sem permitir edição direta; nenhuma saída
   pode deixar saldo de origem negativo.
3. **F02-20:** QUANDO um lote passa da validade informada, ENTÃO ele sai do
   saldo aproveitável, mas permanece no saldo físico e histórico até um
   descarte explícito; ausência de validade não causa vencimento automático.

**Teste independente:** registrar duas entradas do mesmo insumo, vencer uma
delas e comparar saldo físico e aproveitável sem criar perda automática.

### P1 — Registrar saldo inicial, descarte e ajustes

Como usuário operacional, quero começar com um saldo existente e registrar
saídas reais sem ocultar sua origem.

1. **F02-21:** QUANDO cadastrar um insumo, ENTÃO a quantidade inicial é
   opcional; se positiva, cria entrada de saldo inicial identificável, sem
   compra fictícia nem preço obrigatório. Seu custo permanece desconhecido,
   nunca zero nem herdado de compra posterior.
2. **F02-22:** QUANDO registrar descarte de insumo, ENTÃO o usuário informa
   insumo, origem, quantidade positiva e motivo; a saída reduz o saldo da
   origem, permanece no histórico e é bloqueada se exceder seu saldo físico.
3. **F02-23:** QUANDO responsável operacional designado registrar ajuste
   manual, ENTÃO informa insumo, direção, quantidade positiva e motivo; para
   saída, também escolhe a origem de baixa. A entrada cria origem própria e a
   saída reduz a origem escolhida. Saída que exceda esse saldo é bloqueada;
   usuário sem designação não pode ajustar.

**Teste independente:** criar saldo inicial sem custo, descartar parte,
ajustar entrada e saída como responsável e tentar saída maior que o saldo.

### P1 — Corrigir e cancelar compra confirmada

Como responsável operacional, quero corrigir erros e cancelar compras ou
itens sem apagar o que ocorreu.

1. **F02-24:** QUANDO usuário sem designação tenta alterar compra confirmada,
   ENTÃO a operação é negada. A administração da plataforma pode designar um
   ou mais responsáveis entre memberships do tenant, sem ler dados de compra.
2. **F02-25:** QUANDO responsável corrige preço conforme documento, ENTÃO o
   preço anterior, o novo, o autor e o momento permanecem no histórico; custo
   da entrada e valores derivados de consumo já ocorrido são recalculados
   quando existirem nas features seguintes.
3. **F02-26:** QUANDO responsável corrige quantidade conforme documento,
   ENTÃO saldo e custo unitário afetados são recalculados, preservando
   histórico; uma correção que deixaria quantidade inferior às saídas já
   atribuídas à entrada é rejeitada sem alteração parcial.
4. **F02-27:** QUANDO responsável cancela um item, ENTÃO a entrada dele é
   revertida apenas se toda sua quantidade ainda está disponível na própria
   entrada; caso contrário a ação falha e mantém compra e saldo intactos.
5. **F02-28:** QUANDO responsável cancela a compra inteira, ENTÃO todas as
   entradas precisam estar integralmente disponíveis nas respectivas origens;
   a operação é indivisível, preserva documento e histórico e falha inteira
   se qualquer item não puder ser revertido.
6. **F02-29:** QUANDO responsável acrescenta item esquecido à compra original,
   ENTÃO o novo item usa o mesmo documento e gera apenas sua própria entrada;
   itens antigos não são recriados, e a inclusão fica no histórico.

**Teste independente:** consumir parte de uma entrada, corrigir seu preço,
tentar cancelá-la e verificar recálculo e bloqueio; cancelar outro item intacto.

### P1 — Isolar e preservar operações

Como operador de um tenant, quero que meus dados e movimentos só possam ser
alterados por pessoas autorizadas na minha operação.

1. **F02-30:** QUANDO qualquer consulta, anexo ou comando da F-02 é executado,
   ENTÃO a aplicação resolve o tenant pela identidade autenticada; dados de
   outro tenant não aparecem nem podem ser modificados por identificador
   fornecido pelo cliente. RLS atua como segunda barreira.
2. **F02-31:** QUANDO compra, correção, cancelamento, descarte ou ajuste falha
   por validação, autorização, conflito concorrente ou persistência, ENTÃO não
   permanece mudança parcial em compra, histórico, lote, preço ou saldo.
3. **F02-32:** QUANDO uma ação muda quantidade, valor ou estado, ENTÃO o
   histórico registra origem, tipo de ação, quantidades/valores pertinentes,
   autor e momento, sem apagar movimentos anteriores.

**Teste independente:** tentar referência de outro tenant e repetir uma saída
concorrente; verificar isolamento, ausência de saldo negativo e trilha.

## Casos de borda e estados de falha

- Arquivo inválido ou ilegível não pode gerar compra ou insumo automaticamente.
  O documento original permanece anexado, o usuário é avisado de que a leitura
  falhou e pode preencher/revisar manualmente um documento válido; estoque só
  muda após confirmação humana.
- Item desmarcado da nota não gera preço nem estoque operacional; o documento
  original continua preservado.
- Compra confirmada não é sobrescrita silenciosamente por reenvio ou OCR novo.
- Correção de preço em entrada parcialmente consumida altera o custo derivado
  desse consumo; correção de quantidade respeita as saídas da mesma origem.
- Compra que mistura item disponível e item consumido não pode ser cancelada
  parcialmente por uma tentativa de cancelamento integral.
- Lote vencido continua fisicamente presente e pode ser descartado de forma
  explícita; o sistema não o trata como disponível aproveitável.
- Duas ações concorrentes sobre o mesmo saldo ou documento têm resultado
  equivalente a alguma ordem serial válida, sem saldo negativo ou duplicata.

## Cobertura de requisitos implícitos

| Dimensão | Cobertura |
| --- | --- |
| Validação de entrada | F02-01, F02-07, F02-10, F02-12, F02-22 e F02-23. Limites técnicos de arquivo ficam para design. |
| Falha parcial | F02-11 e F02-31. |
| Repetição e duplicidade | F02-15 a F02-17 e F02-29. |
| Autorização | F02-23, F02-24 e F02-30. Limites de taxa do OCR ficam para design. |
| Concorrência | F02-15, F02-19 e F02-31. |
| Ciclo de vida | F02-20 e F02-25 a F02-29. Sem exclusão física de histórico. |
| Observabilidade | F02-32 exige trilha de negócio; métricas técnicas ficam para design. |
| Falha externa | F02-04, F02-08 e fluxo confirmado de aviso, preservação do anexo e revisão manual após falha do OCR. |
| Integridade de estado | F02-11, F02-19 e F02-25 a F02-31. |

## Rastreabilidade

| Fonte | Requisitos F-02 | Estado |
| --- | --- | --- |
| FR-003 | F02-01, F02-02, F02-04, F02-05, F02-21 | Design e tarefas propostos |
| FR-004 | F02-12 a F02-14 | Design e tarefas propostos |
| FR-005 | F02-03, F02-08 | Design e tarefas propostos |
| FR-006 | F02-07 a F02-11, F02-14 a F02-17, F02-24 a F02-29 | Design e tarefas propostos |
| FR-007 | F02-04 a F02-09 | Design e tarefas propostos |
| FR-019 | F02-11, F02-19 a F02-23, F02-26 a F02-28, F02-31, F02-32 | Design e tarefas propostos |
| FR-020 | F02-18, F02-20 | Design e tarefas propostos |
| FR-022 (descarte de insumo) | F02-22 | Design e tarefas propostos |
| Tenant e transação V0 | F02-24, F02-30 a F02-32 | Design e tarefas propostos |

**Cobertura:** 32 requisitos funcionais vinculados a histórias, fontes e
tarefas em `tasks.md`. Nenhuma tarefa da F-02 foi executada.

## Critérios de sucesso

- [ ] Um usuário cadastra insumos a partir de lista com revisão humana e
      confirma compra documentada sem inclusão acidental de itens pessoais.
- [ ] Quantidade selecionada, custo líquido por item, lote e saldo conferem
      com o documento e com as movimentações.
- [ ] Reenvio do mesmo documento não duplica estoque.
- [ ] Correções e cancelamentos preservam histórico e nunca produzem saldo
      negativo ou atualização parcial.
- [ ] Usuários de outro tenant e usuários sem designação não executam ações
      restritas.
