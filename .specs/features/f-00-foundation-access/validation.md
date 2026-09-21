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
