# Verificação independente — F-00

**Data:** 21 de setembro de 2026
**Escopo:** F-00 — Fundação técnica e acesso seguro
**Resultado:** Aprovada

## Método

Foi feita uma rodada independente após a conclusão de T12, confrontando a
especificação, o desenho, a decomposição de tarefas, os ADRs aplicáveis e o
contrato de runtime documentado em `AGENTS.md`. A revisão ficou restrita à
fundação da V0; não foram introduzidos requisitos das features operacionais ou
das versões V1, V2 e V3.

## Evidências executadas

| Gate | Evidência | Resultado |
| --- | --- | --- |
| Testes e empacotamento | `mvnw.cmd verify` com JDK 21.0.12 | 48 testes, 0 falhas, 0 erros, 0 skips; frontend Vaadin e JAR concluídos |
| Repetição do gate de T12 | `mvnw.cmd clean verify` com JDK 21.0.12 | Sucesso; build limpo, testes e empacotamento concluídos |
| Integração ponta a ponta | `FoundationEndToEndIntegrationTests` contra PostgreSQL 17.11 | 4 cenários aprovados: banco vazio, migrations/idempotência, isolamento, identidades negadas, RLS e rollback |
| Imagem de runtime | `docker build --tag gestao-producao:local .` | Sucesso; estágios Temurin 21 JDK/JRE concluídos |
| Inspeção da imagem | `docker inspect gestao-producao:local` | `app:app`, `/app`, `java -jar /app/app.jar`, porta 8080 |
| Conteúdo do runtime | Container iniciado com shell de inspeção | UID/GID 10001; somente `app.jar`; sem `/workspace` ou cache Maven |
| Configuração obrigatória | Execução da imagem sem variáveis de ambiente | Falha explícita por placeholder ausente `DB_URL`; não inicia parcialmente |
| Higiene do patch | `git diff --check` | Sem erro de whitespace |

## Matriz de cobertura da especificação

| Requisitos | Evidência principal |
| --- | --- |
| F00-01 a F00-03 | Flyway no startup, migration em banco vazio, validação e idempotência em T4/T11 |
| F00-04 a F00-08 | Adapter Supabase JWT, resolução de membership, bloqueio de identidades e shell em T5/T7/T10/T11 |
| F00-09 a F00-12 | Tenant resolvido no backend, contexto transacional, RLS, pool reuse e ausência de vazamento em T3/T8/T9/T11 |
| F00-13 a F00-15 | Commit, rollback e negação antes da operação em T9/T11 |
| F00-16 | Shell de acesso com estados não autenticado, provisionado, não provisionado e ambíguo em T10/T11 |
| T12 transversal | Docker multi-stage, runtime não-root, exclusão de código-fonte/cache/segredos e contrato de ambiente |

## Conclusão

Todos os objetivos e requisitos da F-00 estão implementados e cobertos pelos
gates definidos. Não há blocker conhecido para encerrar a feature. A próxima
ação é selecionar a próxima feature V0 no roadmap e iniciar seu ciclo de
especificação.

## Adendo — T13–T15, login Vaadin e sessão Supabase

**Data:** 23 de setembro de 2026
**Escopo:** ADR-025 e requisitos F00-17 a F00-20
**Resultado:** Implementação local e gate completo aprovados; UAT com Supabase
real/Render ainda pendente.

### Evidências executadas

| Gate | Evidência | Resultado |
| --- | --- | --- |
| Compilação, testes e empacotamento | Maven 3.9.16 direto com JDK 21.0.12; `mvn verify` contra PostgreSQL 17.11 novo e efêmero | 175 testes, 0 falhas, 0 erros, 0 skips; frontend Vaadin e JAR concluídos |
| Autenticação REST e provider | Testes unitários de password sign-in, validação JWT/subject, falhas e respostas inválidas | Aprovado na suíte completa |
| Formulário Vaadin | Teste do componente `LoginForm`, ação `login` e ausência de recuperação; revisão das mensagens em português e da configuração da rota | Aprovado; não prova login interativo real |
| Renovação de sessão | Testes de rotação, token ainda válido em indisponibilidade, expiração, subject divergente, acesso ainda fresco e requests concorrentes na mesma sessão | Aprovado na suíte completa |
| Logout local | Testes do pedido `scope=local` e do tratamento de indisponibilidade; Spring/Vaadin continua o logout local configurado | Aprovado na suíte completa; logout na conta real requer UAT |
| Higiene do patch | `git diff --check` | Sem erro de whitespace |

O wrapper Maven não executou corretamente neste ambiente PowerShell; o mesmo
gate foi executado com a instalação Maven 3.9.16 já disponível e o JDK 21.0.12.
O PostgreSQL do gate foi iniciado em container temporário, com diretório de
dados em `tmpfs`, sem volume persistente; não foi usado o banco local
compartilhado. Uma primeira tentativa em banco efêmero previamente usado acusou
falha em teste de persistência de auditoria; repetindo em banco novo e vazio,
todo o gate passou.

### Limites da validação

- Não foram fornecidas credenciais para autenticar uma pessoa de verdade no
  Supabase; portanto, login, refresh e logout interativos ainda precisam de
  UAT no ambiente publicado.
- Não foi executado deploy nem alterado qualquer recurso Render/Supabase.
- Não havia revisor independente disponível nesta execução; foi feita revisão
  local do diff, do contrato de sessão e dos testes, além do gate automatizado.
