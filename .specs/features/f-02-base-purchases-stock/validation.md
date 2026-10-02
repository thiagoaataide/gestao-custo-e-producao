# F-02 — Validação incremental

## T11 — processamento local de PDF

**Estado:** PASS local para implementação e gates automatizados. Esta evidência
não declara OCR real nem validação hospedada.

### Revisão independente do comportamento

- A raiz de PDFBox fica em `infrastructure`; a aplicação depende de
  `PdfDocumentProcessor`, sem chamada a provedor externo de OCR nesta tarefa.
- O tipo é validado pela assinatura PDF, limite herdado de 6 MiB e máximo de 5
  páginas antes da extração/renderização.
- Extração usa writer limitado a 100.000 caracteres por página. Renderização
  ocorre por página sem texto, em escala fixa de 150 DPI e tons de cinza, após
  validar dimensões de até 8 megapixels; a soma PNG não pode exceder 12 MiB.
- `PDDocument` fecha em `try-with-resources`; erros do parser são convertidos
  em exceção sem causa ou detalhes de entrada.
- O teste de PDF misto discrimina a regra central: texto digital na página 1
  não é renderizado; página escaneada 2 vira PNG e somente ela requer OCR.
- Limites de página, pixels e texto têm testes negativos dedicados. Não foi
  feito profiling de heap com documentos reais; os limites são verificados
  funcionalmente por dimensões, quantidade de páginas e bytes produzidos.

### Gate

- PDFBox 3.0.8 confirmado na documentação Apache; a documentação indica Java 8
  como mínimo e testes upstream até Java 19. O projeto compilou e executou os
  testes com Java 21.0.12.
- `dependency:tree`: `pdfbox`, `pdfbox-io` e `fontbox`, todos `3.0.8`.
- Testes direcionados: 6 aprovados, 0 falhas/erros/skips.
- `mvn -B verify` em PostgreSQL 17 novo (`f02_t11_release_20261002`):
  **279 testes**, 0 falhas/erros/skips; frontend Vaadin e JAR concluídos.
- Nenhuma credencial, chamada Vision ou serviço Supabase/Render foi usado.

### Limite de aceitação

Fixtures geradas testam o parser e os limites, mas não substituem a validação
manual com notas/recibos reais prevista para T25. PDFBox pode consumir CPU ao
processar documentos maliciosos; o limite de bytes/páginas, o cache temporário,
o teto de pixels e a saída de texto/PNG reduzem recursos alocados, sem alegar
profiling ou garantia de tempo máximo de execução.
