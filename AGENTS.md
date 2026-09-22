# Instruções do projeto para agentes

## Escopo e precedência

Estas instruções se aplicam a todo o repositório `C:\proj\gestao-producao`.

Antes de alterar código, documentação, dependências ou configuração:

1. Leia este arquivo.
2. Leia `docs/PRD-V0.md` para confirmar o escopo funcional.
3. Verifique o estado real do `pom.xml` e dos arquivos afetados.
4. Identifique e leia as skills aplicáveis antes de executar a tarefa.
5. Consulte a documentação oficial da versão exata do componente envolvido.

As decisões do usuário e o PRD têm precedência sobre recomendações genéricas.
Estas instruções não autorizam a expansão do escopo da V0.

## Contexto do projeto

- **Raiz do projeto:** `C:\proj\gestao-producao`
- **Produto:** TAAS Gestão de Produção
- **Artefato Maven:** `gestao-producao`
- **Group ID:** `br.com.taas.saas`
- **Package base:** `br.com.taas.saas.gestaoproducao`
- **PRD da V0:** `docs/PRD-V0.md`
- **Build descriptor:** `pom.xml`
- **Configuração atual:** `src/main/resources/application.yaml`
- **PostgreSQL local:** `compose.yaml`
- **Perfil de testes:** `src/test/resources/application-test.yaml`
- **Nome da aplicação:** `gestao-producao`

O contexto HTTP (`server.servlet.context-path`) ainda não foi definido. Não
assuma um context path diferente da raiz até que essa decisão seja registrada
no PRD ou na configuração.

A marmitaria é o primeiro caso de validação, mas o domínio deve permanecer
centrado em planejamento de produção, custos e insumos para pequenos
produtores de alimentos. Não introduza acoplamentos a marmitas sem justificativa
no PRD.

## Escopo funcional

Trabalhe exclusivamente no escopo da V0 descrito em `docs/PRD-V0.md`.

- Não implemente, planeje ou pergunte sobre funcionalidades classificadas como
  V1, V2 ou V3.
- Preserve o fluxo ponta a ponta da V0: insumos e fichas técnicas, cardápio,
  pedidos, planejamento, necessidade de insumos, estoque, compras, produção,
  consumo, movimentações, CMV, margem e indicadores.
- Preserve o isolamento de tenant. Na V0, cada usuário acessa somente um
  tenant.
- Separe autenticação gerenciada pelo Supabase da identificação de tenant no
  domínio.
- A aplicação é a camada principal de autorização e resolução de tenant; o
  PostgreSQL deve aplicar RLS como segunda barreira nos dados pertencentes a
  tenants.
- O backend deve estabelecer o contexto de tenant resolvido em cada transação
  operacional; nunca confie no `tenant_id` enviado pelo cliente.
- Cada caso de uso que altera o domínio deve ser uma transação ACID local: se
  uma etapa falhar, nenhuma alteração parcial pode permanecer persistida.
- O schema, RLS, policies, grants, constraints e índices devem evoluir por
  migrations versionadas no Git, com script de aplicação e reversão controlada;
  não altere o banco remoto manualmente no fluxo normal.
- O Flyway é o único executor e histórico de migrations da aplicação; não
  misture o histórico do Flyway com migrations do Supabase CLI.
- A V0 usa Flyway Community sem depender do comando `flyway undo` do Teams;
  reversões devem seguir procedimento explícito, revisado e controlado.
- As migrations pendentes devem ser executadas na inicialização da aplicação;
  uma falha de migration deve impedir o startup considerado bem-sucedido.
- Não remova rastreabilidade de compras, lotes, produção, consumo, perdas,
  cancelamentos ou destinações.

## Política obrigatória de documentação oficial

Use documentação oficial como fonte primária para APIs, configuração, ciclo de
vida, compatibilidade, comportamento de versões, snippets e validação de erros.

Regras obrigatórias:

- Confirme primeiro a versão presente no inventário abaixo e no `pom.xml`.
- Use a página oficial correspondente à versão, quando houver seletor de
  versão.
- Quando uma dependência for gerenciada pelo Spring Boot, consulte a tabela
  oficial de dependências gerenciadas do mesmo release do Spring Boot.
- Não use snippets de blog, fóruns, vídeos ou respostas de terceiros como
  autoridade para comportamento do framework.
- Fontes secundárias podem ajudar a localizar um problema, mas qualquer
  decisão técnica precisa ser confirmada na documentação oficial e registrada
  com a versão consultada.
- Se a documentação oficial não for suficiente, registre a lacuna e valide o
  comportamento no projeto com um teste reproduzível.
- Não atualize uma dependência apenas porque existe uma versão mais nova. Toda
  atualização deve considerar compatibilidade, escopo da V0, changelog oficial
  e impacto no inventário deste arquivo.

### Índice oficial por componente

Estes são os context paths oficiais que devem ser usados como ponto de partida:

| Componente | Documentação oficial |
| --- | --- |
| Java | https://docs.oracle.com/en/java/javase/21/docs/api/ |
| Apache Maven | https://maven.apache.org/ |
| Maven Wrapper | https://maven.apache.org/tools/wrapper/ |
| Spring Boot | https://docs.spring.io/spring-boot/ |
| Spring Framework | https://docs.spring.io/spring-framework/reference/ |
| Spring Security | https://docs.spring.io/spring-security/reference/ |
| Nimbus JOSE + JWT | https://connect2id.com/products/nimbus-jose-jwt |
| Spring Data JPA | https://docs.spring.io/spring-data/jpa/reference/ |
| Spring Cloud | https://spring.io/projects/spring-cloud |
| Hibernate ORM | https://docs.hibernate.org/orm/ |
| Jakarta Persistence | https://jakarta.ee/specifications/persistence/ |
| Jakarta Transactions | https://jakarta.ee/specifications/transactions/ |
| Vaadin | https://vaadin.com/docs/latest/ |
| PostgreSQL JDBC | https://jdbc.postgresql.org/documentation/ |
| Flyway | https://documentation.red-gate.com/flyway |
| Supabase | https://supabase.com/docs |
| Docker | https://docs.docker.com/ |
| Eclipse Temurin | https://adoptium.net/ |

Ao adicionar um novo componente, inclua seu context path oficial nesta tabela e
registre a versão no inventário antes de utilizar exemplos da documentação.

## Diretiva de skills

Todas as skills disponíveis para o agente estão sujeitas a esta diretiva. A
lista de skills pode evoluir; por isso, descubra-a nos diretórios configurados
em vez de copiar uma lista estática para o projeto.

### Diretórios de descoberta

- `C:\Users\thiag\.agents\skills`
- `C:\Users\thiag\.codex\skills`
- `C:\Users\thiag\.codex\plugins\cache`
- `.agents\skills`, caso o projeto passe a possuir skills locais

### Regras de uso

- Se o usuário nomear uma skill, ela é obrigatória para a tarefa.
- Se a descrição da tarefa corresponder a uma skill disponível, use essa skill
  mesmo que o usuário não a nomeie.
- Leia o `SKILL.md` completo antes de executar ações da tarefa.
- Siga as referências adicionais exigidas pelo `SKILL.md`, lendo somente os
  recursos necessários para a tarefa.
- Verifique no filesystem se a skill está realmente disponível; não presuma
  que uma skill apenas recomendada ou mencionada esteja instalada.
- Skills orientam o processo. Elas não substituem o PRD, as decisões do
  usuário, os testes do projeto ou a documentação oficial do componente.
- Anuncie ao usuário quando uma skill for usada e explique no resultado final
  qualquer decisão material influenciada por ela.

### Skills relevantes para este projeto

Quando aplicável, priorize as skills abaixo, sem ignorar outras que sejam
acionadas pela tarefa:

- `docs-writer`: PRD, `AGENTS.md`, README e demais documentos.
- `tlc-spec-driven`: especificação, design, tarefas e execução rastreável.
- `domain-analysis`: análise de domínios e limites do produto.
- `tactical-ddd`: entidades, agregados, value objects e regras de domínio.
- `best-practices`: revisão de qualidade, compatibilidade e segurança geral.
- `supabase:supabase`: Supabase Auth, Storage, Database ou produtos Supabase.
- `supabase:supabase-postgres-best-practices`: SQL, schema e configuração
  PostgreSQL/Supabase.
- `technical-design-doc-creator`: decisões técnicas e documentos de design.

## Inventário de versões

Este inventário registra somente versões observadas ou oficialmente gerenciadas
para o estado atual do projeto. A data de referência é 20 de setembro de 2026.

### Declarado no projeto

| Componente | Coordenada ou origem | Versão | Situação |
| --- | --- | --- | --- |
| Java | `<java.version>` em `pom.xml` | 21 | alvo do projeto |
| Java runtime observado | `java -version` | 21.0.12 LTS | ambiente local observado |
| Maven Wrapper scripts | cabeçalho de `mvnw` e `mvnw.cmd` | 3.3.4 | versionado no projeto |
| Apache Maven distribution | `.mvn/wrapper/maven-wrapper.properties` | 3.9.16 | fixado pelo wrapper |
| Spring Boot parent | `org.springframework.boot:spring-boot-starter-parent` | 4.1.1 | parent do projeto |
| Spring Boot starter | `org.springframework.boot:spring-boot-starter` | 4.1.1 | dependência direta, versão gerenciada |
| Spring Security starter | `org.springframework.boot:spring-boot-starter-security` | 4.1.1 | dependência direta, versão gerenciada |
| OAuth2 Resource Server starter | `org.springframework.boot:spring-boot-starter-oauth2-resource-server` | 4.1.1 | dependência direta, versão gerenciada |
| Spring Data JPA starter | `org.springframework.boot:spring-boot-starter-data-jpa` | 4.1.1 | dependência direta, versão gerenciada |
| Flyway starter | `org.springframework.boot:spring-boot-starter-flyway` | 4.1.1 | dependência direta, versão gerenciada |
| Spring Boot test starter | `org.springframework.boot:spring-boot-starter-test` | 4.1.1 | dependência direta de teste, versão gerenciada |
| Spring Boot Maven plugin | `org.springframework.boot:spring-boot-maven-plugin` | 4.1.1 | plugin, versão gerenciada |
| PostgreSQL local de teste | `postgres:17` em `compose.yaml` | 17 | somente ambiente local de testes |
| Vaadin BOM | `com.vaadin:vaadin-bom` | 25.2.8 | BOM importado pelo projeto |
| Vaadin Spring Boot starter | `com.vaadin:vaadin-spring-boot-starter` | 25.2.8 | dependência direta, versão gerenciada pelo BOM |
| Vaadin development tools | `com.vaadin:vaadin-dev` | 25.2.8 | dependência direta opcional |
| Vaadin Maven plugin | `com.vaadin:vaadin-maven-plugin` | 25.2.8 | plugin direto do projeto |
| Docker build image | `eclipse-temurin:21-jdk-jammy` | Java 21 | etapa de compilação da imagem |
| Docker runtime image | `eclipse-temurin:21-jre-jammy` | Java 21 | etapa de execução da imagem |

As versões das dependências sem `<version>` devem continuar sendo gerenciadas
pelo parent/BOM do Spring Boot. Não fixe versões individuais sem justificativa
de compatibilidade registrada.

### Componentes gerenciados pelo Spring Boot e relevantes para a fundação

Estas versões são referências do gerenciamento oficial do Spring Boot 4.1.1.
Elas não significam que todos os componentes já estejam no classpath. Quando a
dependência for adicionada ao `pom.xml`, confirme novamente a árvore efetiva e
atualize esta tabela se houver divergência.

| Componente | Coordenada ou família | Versão gerenciada | Estado no projeto |
| --- | --- | --- | --- |
| Spring Framework | `org.springframework:spring-*` | 7.0.9 | transitivo do starter atual |
| SLF4J | `org.slf4j:slf4j-*` | 2.0.18 | transitivo do logging |
| Logback | `ch.qos.logback:logback-*` | 1.5.38 | transitivo do logging |
| JUnit Jupiter | `org.junit.jupiter:junit-jupiter-*` | 6.0.3 | transitivo do starter de teste |
| Mockito | `org.mockito:mockito-*` | 5.23.0 | transitivo do starter de teste |
| AssertJ | `org.assertj:assertj-core` | 3.27.7 | transitivo do starter de teste |
| Hamcrest | `org.hamcrest:hamcrest-*` | 3.0 | transitivo do starter de teste |
| Spring Security | `org.springframework.security:spring-security-*` | 7.1.1 | transitivo dos starters de Security |
| Nimbus JOSE + JWT | `com.nimbusds:nimbus-jose-jwt` | 10.9.1 | transitivo do Resource Server; assinatura ES256/JWKS |
| Spring Data JPA | `org.springframework.data:spring-data-jpa` | 4.1.1 | transitivo do starter de JPA |
| Hibernate ORM | `org.hibernate.orm:hibernate-core` | 7.4.5.Final | transitivo do starter de JPA |
| Hibernate Validator | `org.hibernate.validator:hibernate-validator` | 9.1.3.Final | transitivo do baseline de validação |
| Jakarta Persistence | `jakarta.persistence:jakarta.persistence-api` | 3.2.0 | transitivo do starter de JPA |
| Jakarta Transactions API | `jakarta.transaction:jakarta.transaction-api` | 2.0.1 | transitivo do starter de JPA; não implica JTA |
| PostgreSQL JDBC | `org.postgresql:postgresql` | 42.7.13 | dependência direta de runtime |
| Flyway Core | `org.flywaydb:flyway-core` | 12.4.0 | transitivo do starter Flyway, Community |
| Flyway PostgreSQL | `org.flywaydb:flyway-database-postgresql` | 12.4.0 | dependência direta do banco PostgreSQL |

### Componentes planejados ou condicionais

Os itens abaixo permanecem planejados ou condicionais para a V0, mas não fazem
parte da baseline de dependências adicionada na T2. Integrações gerenciadas por
serviço devem registrar a versão do cliente ou protocolo efetivamente escolhido
quando forem implementadas.

| Componente | Decisão atual | Regra para definir a versão |
| --- | --- | --- |
| Supabase Auth | Resource Server JWT no Spring Security; sem SDK de cliente no backend | T5; integração baseada no protocolo JWT/JWKS do projeto |
| Supabase Storage | armazenamento de objetos | registrar versão do cliente/SDK escolhido |
| Spring Cloud | não é necessário no esqueleto atual | só adicionar se uma necessidade da V0 exigir |
| Implementação JTA | não definida para a V0 | não adicionar sem requisito de transação distribuída |
| Plataforma de hospedagem | custo zero, pausas e cold starts aceitos | registrar versão/imagem/runtime do provedor |

Não trate a versão de uma biblioteca apenas listada como gerenciada como
decisão de adoção. A adoção ocorre somente quando a coordenada entrar no
`pom.xml` e for validada no build.

## Regras de dependências e fundação

Ao adicionar ou alterar uma dependência:

1. Confirme a compatibilidade com Java 21 e Spring Boot 4.1.1 na documentação
   oficial.
2. Prefira o starter oficial e o gerenciamento do parent/BOM, sem declarar uma
   versão redundante.
3. Registre no inventário a coordenada, a versão declarada ou gerenciada, o
   motivo e o link oficial.
4. Atualize este arquivo no mesmo conjunto de mudanças do `pom.xml`.
5. Gere e inspecione a árvore de dependências e o effective POM quando o Maven
   estiver disponível.
6. Execute os testes e registre separadamente qualquer validação bloqueada.

Não introduza Spring Cloud, JTA, mensageria, cache distribuído, observabilidade
ou outro componente transversal sem uma necessidade explícita da V0.

## Regras de implementação

- Modele regras de negócio no domínio, evitando lógica essencial somente em
  telas ou controladores.
- Mantenha a inversão de dependência: domínio e aplicação dependem de portas,
  enquanto adapters de infraestrutura implementam essas portas. Não injete
  JPA, JDBC ou detalhes de provedores diretamente nos casos de uso.
- Use interfaces somente em fronteiras reais de integração, substituição ou
  teste. Não crie uma abstração para cada classe sem necessidade arquitetural.
- Separe commands e queries na camada de aplicação conforme o ADR-024. A V0
  usa CQRS lógico, síncrono e dentro do monólito; não introduza mensageria,
  bancos de leitura separados ou event sourcing sem novo ADR.
- Mantenha cálculos projetados separados dos realizados.
- Preserve unidades, conversões, rendimentos, lotes, arredondamentos de compra,
  margem de segurança, consumo real, perdas e destinações.
- Toda leitura ou escrita de dado de negócio deve respeitar o tenant do usuário.
- Não considere o RLS efetivo se a conexão usada pela aplicação bypassar as
  políticas sem uma decisão explícita, restrita e auditada.
- Não use credenciais, tokens ou segredos em código, documentação ou commits.
- O Compose local é apenas infraestrutura descartável de desenvolvimento e
  testes; não representa o banco gerenciado de produção.
- O perfil `test` usa `app_runtime` para a aplicação e `postgres` somente para
  o Flyway. Nunca simplifique o teste usando uma role com `BYPASSRLS`.
- O arquivo `.env` local não é versionado. O `.env.example` contém somente
  valores de demonstração e não deve ser usado como segredo de implantação.
- Prefira mudanças pequenas, verificáveis e coerentes com o PRD.
- Não altere arquivos preexistentes sem verificar o diff e sem preservar
  mudanças do usuário.

## Validação mínima

Antes de concluir uma mudança relevante:

- confirme o diff dos arquivos alterados;
- execute testes automatizados aplicáveis;
- valide o build Maven com `mvnw.cmd` no Windows ou `./mvnw` em ambiente POSIX;
- registre se a validação foi concluída, parcial ou bloqueada;
- não declare aceitação com base somente em inspeção estática.

O Maven Wrapper foi revalidado no ambiente local com Java 21. A árvore de
dependências e o effective POM foram gerados durante a T2. O empacotamento e o
gate de integração com PostgreSQL real passaram; a configuração obrigatória de
datasource/Flyway permanece explícita e não há exclusão de auto-configuração
para mascarar sua ausência.

O startup da aplicação exige as seguintes variáveis de ambiente, sem defaults
de produção no `application.yaml`:

- `DB_URL`: JDBC URL do PostgreSQL;
- `APP_RUNTIME_USER` e `APP_RUNTIME_PASSWORD`: credencial sem `BYPASSRLS` para
  o datasource da aplicação;
- `MIGRATION_DB_USER` e `MIGRATION_DB_PASSWORD`: credencial separada usada pelo
  Flyway no startup.
- `SUPABASE_JWT_ISSUER` e `SUPABASE_JWT_JWK_SET_URI`: issuer e endpoint JWKS
  do projeto Supabase configurados para o Resource Server;
- `SUPABASE_PUBLISHABLE_KEY`: chave publicável enviada somente no header
  `apikey` da leitura do JWKS protegido;
- `SUPABASE_JWT_AUDIENCE`: audience esperada dos access tokens, normalmente
  `authenticated`.
- `PLATFORM_BOOTSTRAP_OWNER_SUBJECT`: subject Supabase autorizado para o
  bootstrap controlado do primeiro `PLATFORM_OWNER`; sem esse valor o bootstrap
  permanece bloqueado.

O `spring.flyway.url` usa o mesmo `DB_URL`, mas mantém usuário e senha
separados. O perfil `test` possui defaults locais controlados para executar o
Compose descartável; eles não devem ser usados como segredo de implantação.

Para validar a fundação localmente, o PostgreSQL deve ser iniciado com
`docker compose --env-file .env.example up -d`; depois, com Java 21 ativo,
execute `mvnw.cmd verify`. O Compose usa PostgreSQL 17, provisiona `app_runtime`
sem `BYPASSRLS` e expõe a porta local 55432 por padrão. Se os valores do
`.env.example` forem alterados, as mesmas variáveis devem estar disponíveis no
processo Maven do host.

### Contrato do container

O `Dockerfile` usa duas etapas: `eclipse-temurin:21-jdk-jammy` compila o Jar com
o Maven Wrapper, e `eclipse-temurin:21-jre-jammy` executa somente o artefato
empacotado como o usuário não-root `app`. A imagem de runtime não recebe o
diretório-fonte, o cache Maven, os testes, os documentos ou arquivos `.env`.

O container não define credenciais nem valores de conexão. As variáveis
obrigatórias do datasource, Flyway e Supabase listadas acima devem ser
fornecidas pelo ambiente de execução. Como o entrypoint chama diretamente o
processo Java, uma variável obrigatória ausente continua provocando falha de
startup do Spring Boot; ela não é substituída por um valor silencioso pelo
container.

Para construir e executar localmente:

```text
docker build -t gestao-producao:local .
docker run --rm -p 8080:8080 --env-file .env gestao-producao:local
```

O arquivo `.env` usado nesse exemplo é local e não deve ser versionado. Não
use `.env.example` como segredo de implantação.

## Atualização obrigatória deste arquivo

Atualize `AGENTS.md` sempre que ocorrer qualquer uma das situações abaixo:

- alteração do parent ou da versão do Spring Boot;
- alteração do Java, Maven Wrapper ou distribuição Maven;
- inclusão, remoção ou atualização de dependência ou plugin;
- inclusão de Vaadin, Supabase, PostgreSQL, Spring Security, Spring Data JPA,
  Hibernate, JTA ou Spring Cloud;
- mudança de context path, regra de tenant ou baseline de hospedagem;
- mudança dos diretórios de skills ou da política de documentação oficial.

O arquivo deve refletir o estado real do repositório. Nunca deixe uma versão
obsoleta ser usada como fonte para novos exemplos ou diagnósticos.
