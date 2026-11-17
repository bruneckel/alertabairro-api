# AlertaBairro Back-end — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar a API REST, o banco Oracle modelado, as rotinas PL/SQL, o ambiente Docker e a documentação que sustentam o app AlertaBairro, substituindo os dados falsos por dados reais.

**Architecture:** Camadas no Spring (`Controller → Service @Transactional → Repository JDBC`), com PL/SQL responsável apenas pelo que é naturalmente de banco: distância em conjunto, escalonamento por trigger, controle de vagas com lock, encerramento em lote e agregação de indicadores. Regras de fluxo ficam no serviço, dentro de transação, porque é onde elas podem ser testadas e onde o back-end anterior falhava.

**Tech Stack:** Java 21 · Spring Boot 3.5.x · Spring JDBC (`JdbcClient`, `SimpleJdbcCall`) · Spring Security (Resource Server, JWT HS256) · Flyway · Oracle Database 23ai Free (`gvenzl/oracle-free:23-slim`) · Maven · springdoc-openapi · JUnit 5 + MockMvc · Testcontainers

**Spec:** [`docs/superpowers/specs/2026-11-17-backend-alertabairro-design.md`](../specs/2026-11-17-backend-alertabairro-design.md)

## Global Constraints

Valem para toda tarefa, sem repetição nos requisitos de cada uma.

- **Java 21**; Spring Boot **3.5.x** (fixar a estável do `start.spring.io` na Tarefa 7).
- **Superfície HTTP congelada em 17 endpoints** (spec 9.4) + `/actuator/health`. Rota nova é violação de R8 e precisa de aprovação.
- **PK por `SEQUENCE` + trigger `BEFORE INSERT`**, nunca `GENERATED AS IDENTITY`.
- **Nada de sintaxe exclusiva do 23ai:** sem `DROP ... IF EXISTS`, sem `BOOLEAN` em coluna, `FROM dual` sempre explícito.
- **Nada que exija DBA:** sem `CREATE USER`, `TABLESPACE`, `GRANT`. O usuário da aplicação é criado pela imagem Docker, não pelos nossos scripts.
- **Migrações são create-only.** Todo `DROP` vive só em `database/reset_environment.sql`, fora da sequência do Flyway.
- **Valores de domínio sempre minúsculos e sem acento:** `aberta`·`em_atendimento`·`resolvida` / `normal`·`alta`·`critica` / `aguardando`·`atendido`·`resolvido` / `pedido`·`oferta` / `baixa`·`media`·`alta` / `morador`·`idoso`·`voluntario` / `estou_bem`·`preciso_de_ajuda`.
- **Erros de negócio no banco:** `RAISE_APPLICATION_ERROR` em `-20001`(usuário não encontrado) `-20002`(abrigo sem vagas) `-20003`(abrigo não encontrado) `-20004`(saída maior que ocupação) `-20005`(dados inválidos) → HTTP **422** com a mensagem repassada.
- **Datas** em ISO-8601 **com offset**; container da API com `TZ=America/Sao_Paulo`.
- **Paginação** em toda listagem: `limite` inteiro 1..200 (padrão 200), `deslocamento` inteiro ≥ 0.
- **Todo SQL com bind variables.** Nenhuma concatenação de valor em SQL.
- **`senha_hash` nunca é projetado** em consulta de leitura nem sai em resposta.
- **Corpo de requisição limitado a 100 KB.**
- Erro sempre no formato `{ "erro": "mensagem" }`.
- Objetos de banco criados **sem aspas** no nome (senão o Oracle passa a diferenciar maiúsculas).

## Review Focus

Classes de entrada que a spec implica e que precisam de teste explícito. Cada linha tem o teste fixado na tarefa que possui o código.

1. **Token adulterado, expirado ou com algoritmo trocado** (`alg: none`, assinatura de outro segredo) deve dar **401**, nunca 500 nem acesso liberado — Tarefa 8.
2. **Duas confirmações simultâneas do mesmo usuário** na mesma ocorrência: uma aplica, a outra dá **409**, e `relatos` sobe exatamente 1 — Tarefa 10.
3. **`limite`/`deslocamento` fora da faixa ou não numérico** (`limite=0`, `limite=500`, `limite=abc`) deve dar **400**, nunca 500 nem lista inteira — Tarefa 9.
4. **Coordenada fora de faixa** (`lat=91`, `lng=-181`, `NaN`) deve dar **400**; coordenada ausente onde é opcional deve ser aceita — Tarefa 12.
5. **Acento atravessando API → Oracle → API** (`tipo = "Árvore caída"`) deve voltar idêntico, sem mojibake por charset do container — Tarefa 9.

---

## File Structure

```
alertabairro-api/
├── docker-compose.yml              oracle (healthcheck + volume) + api
├── Dockerfile                      multi-stage: build Maven → runtime JRE 21
├── pom.xml                         o repositório É a API: projeto Maven na raiz
├── .gitignore
├── README.md · README.txt          mesmo conteúdo; o .txt é o nome que o enunciado pede
├── links.txt                       placeholders dos vídeos + 2 links de repo
├── database/
│   ├── migrations/                 fonte única das migrações (Flyway lê daqui)
│   │   ├── V1__tables.sql          6 tabelas + CHECK + FK nomeadas
│   │   ├── V2__pk_sequences.sql    5 sequences + 5 triggers BEFORE INSERT
│   │   ├── V3__occurrence_routines.sql
│   │   ├── V4__shelter_routines.sql
│   │   ├── V5__help_indicators_routines.sql
│   │   └── V6__shelters_seed.sql
│   ├── seed_demo.sql               opt-in, fora do Flyway
│   ├── reset_environment.sql       único lugar com DROP
│   ├── apply.sh                    aplica as migrações à mão (antes do Flyway, e na FIAP)
│   ├── tests/
│   │   ├── t0_connection.sql · t1_schema.sql · t2_occurrence.sql
│   │   ├── t3_shelter.sql · t4_help.sql · t5_seed.sql
│   │   └── run_tests.sh
│   └── README.md
├── src/
│   ├── main/java/br/com/fiap/alertabairro/
│   │   ├── AlertaBairroApplication.java
│   │   ├── config/        SecurityConfig · OpenApiConfig
│   │   ├── seguranca/     TokenService · Autenticado
│   │   ├── web/           6 controllers + TratadorDeErros + dto/
│   │   ├── service/       6 services (regra + @Transactional)
│   │   └── repository/    6 repositories (JdbcClient · SimpleJdbcCall)
│   ├── main/resources/application.yml
│   └── test/java/...      por tarefa
└── docs/
    ├── modelo-dados.md             DER + MER + dicionário de dados
    └── superpowers/                spec e este plano
```

Pastas em inglês, seguindo a convenção de mercado. Os **nomes de domínio** seguem em
português — tabelas (`ocorrencia`, `abrigo`), rotinas (`prc_registrar_ocorrencia`) e rotas
(`/ocorrencias`, `/abrigos`) — porque são contrato já fechado com o app e com a API
anterior, e traduzi-los violaria a paridade (R8).

Regra de decomposição: um arquivo por responsabilidade de domínio, não por camada técnica. `OcorrenciaService` e `OcorrenciaRepository` mudam juntos e ficam próximos na árvore de pacotes.

**Flyway lê `database/migrations/` diretamente** (`spring.flyway.locations=filesystem:...`), sem cópia em `resources`. Os arquivos continuam SQL puro, abríveis no SQL Developer — é o que torna o objetivo 5 da spec verdadeiro por construção.

---

# Fase 1 — Banco

### Task 1: Oracle de pé em Docker

**Files:**
- Create: `docker-compose.yml`, `.gitignore`, `.env.example`
- Test: `database/tests/run_tests.sh`, `database/tests/t0_connection.sql`

**Interfaces:**
- Consumes: nada.
- Produces: serviço `oracle` acessível em `localhost:1521/FREEPDB1`, usuário `APP_USER=alertabairro`. `run_tests.sh <arquivo.sql>` executa um script no container e sai com código ≠ 0 se qualquer `RAISE_APPLICATION_ERROR` disparar.

- [ ] **Step 1: Write the failing test**

`database/tests/t0_connection.sql`:
```sql
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v NUMBER;
BEGIN
  SELECT 1 INTO v FROM dual;
  IF v <> 1 THEN RAISE_APPLICATION_ERROR(-20900, 'FALHA: dual'); END IF;
  DBMS_OUTPUT.PUT_LINE('OK t0_conexao');
END;
/
EXIT
```

`database/tests/run_tests.sh` recebe zero ou mais nomes de arquivo (sem argumento, roda todos os `t*.sql` em ordem), executa cada um com `docker compose exec -T oracle sqlplus -S alertabairro/<senha>@//localhost:1521/FREEPDB1 @/teste/<arquivo>` e propaga o primeiro código de saída não-zero. Os scripts chegam ao container por bind mount de `database/` em `/`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd alertabairro-api && ./database/tests/run_tests.sh t0_connection.sql`
Expected: FAIL — serviço `oracle` não existe ainda.

- [ ] **Step 3: Write `docker-compose.yml` com o serviço `oracle`**

Imagem `gvenzl/oracle-free:23-slim`. Variáveis: `ORACLE_PASSWORD`, `APP_USER=alertabairro`, `APP_USER_PASSWORD`. Porta `1521:1521`. Volume nomeado em `/opt/oracle/oradata` para o primeiro boot não repetir. Bind mount de `./banco` em `/banco` e `./banco/teste` em `/teste`. Healthcheck usando o `healthcheck.sh` que a imagem já traz, com `start_period` generoso — o primeiro boot leva minutos. `.env.example` documenta as senhas; `.gitignore` exclui `.env`.

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose up -d oracle && docker compose ps` (aguardar `healthy`), depois `./database/tests/run_tests.sh t0_connection.sql`
Expected: PASS com `OK t0_conexao`

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .gitignore .env.example database/tests/
git commit -m "chore: Oracle 23ai Free em Docker com healthcheck e runner de testes SQL"
```

---

### Task 2: Tabelas, constraints e PKs portáveis

**Files:**
- Create: `database/migrations/V1__tables.sql`, `database/migrations/V2__pk_sequences.sql`, `database/reset_environment.sql`
- Test: `database/tests/t1_schema.sql`

**Interfaces:**
- Consumes: Tarefa 1 (`run_tests.sh`, container).
- Produces: tabelas `usuario`, `ocorrencia`, `abrigo`, `pedido_ajuda`, `checkin`, `confirmacao_ocorrencia` com as colunas da spec 7.1, defaults (`status`, `prioridade`, `relatos DEFAULT 1`, `criado_em/criada_em DEFAULT SYSTIMESTAMP`) e PK preenchida por trigger.

- [ ] **Step 1: Write the failing test**

`database/tests/t1_schema.sql` — asserções, cada falha com `RAISE_APPLICATION_ERROR(-209xx, 'FALHA: ...')`:
- as 6 tabelas existem em `user_tables`;
- `INSERT INTO usuario (nome,email,senha_hash,bairro,perfil) VALUES (...)` **sem id** devolve id > 0 via `RETURNING id INTO`;
- defaults aplicados: ocorrência inserida sem `status`/`prioridade`/`relatos` fica `aberta`/`normal`/`1`;
- `perfil = 'sindico'` é recusado pela CHECK (esperar exceção);
- `OCORRENCIA.status = 'ABERTA'` (maiúsculo) é recusado pela CHECK;
- `abrigo` com `vagas_ocupadas > capacidade` é recusado pela CHECK;
- `confirmacao_ocorrencia` recusa par `(id_ocorrencia, id_usuario)` repetido com `ORA-00001`;
- `DELETE` da ocorrência remove as confirmações filhas (`ON DELETE CASCADE`);
- `ROLLBACK` no fim.

- [ ] **Step 2: Run test to verify it fails**

Run: `./database/tests/run_tests.sh t1_schema.sql`
Expected: FAIL — `ORA-00942: table or view does not exist`

- [ ] **Step 3: Escrever `V1__tables.sql` e `V2__pk_sequences.sql`**

`V1`: as 6 tabelas da spec 7.1. `latitude`/`longitude` como `NUMBER(9,6)`; `senha_hash VARCHAR2(100)`; `foto_uri VARCHAR2(500) NULL`. Toda constraint nomeada (`pk_`, `fk_`, `uk_`, `ck_`), nenhuma gerada pelo Oracle. `ck_pedido_status` já aceita os três valores, incluindo `resolvido` (spec 7.3). FK de `confirmacao_ocorrencia` para `ocorrencia` com `ON DELETE CASCADE`.

`V2`: uma `SEQUENCE` e um trigger `BEFORE INSERT FOR EACH ROW` para cada tabela **com id surrogate**, atribuindo `:NEW.id` quando vier nulo — são **5**, não 6: `confirmacao_ocorrencia` tem PK composta `(id_ocorrencia, id_usuario)` e não leva sequence. Decisão da spec 6.1: portabilidade de 11g a 23ai.

`reset_environment.sql`: único arquivo com `DROP`, cada um em bloco `BEGIN ... EXCEPTION WHEN OTHERS THEN NULL; END;` (não existe `IF EXISTS` fora do 23ai). Aviso no topo de que é só para o Docker local.

- [ ] **Step 4: Run test to verify it passes**

Run: `./database/tests/run_tests.sh t1_schema.sql` (aplicando `V1` e `V2` antes, à mão via sqlplus)
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add database/migrations/V1__tables.sql database/migrations/V2__pk_sequences.sql database/reset_environment.sql database/tests/t1_schema.sql
git commit -m "feat(banco): 6 tabelas com constraints nomeadas e PK por sequence+trigger"
```

---

### Task 3: Rotinas de ocorrência — registro e escalonamento

**Files:**
- Create: `database/migrations/V3__occurrence_routines.sql`
- Test: `database/tests/t2_occurrence.sql`

**Interfaces:**
- Consumes: Tarefa 2 (tabelas).
- Produces, exatamente nesta ordem de parâmetros:
  - `prc_registrar_ocorrencia(p_id_usuario IN NUMBER, p_tipo IN VARCHAR2, p_descricao IN VARCHAR2, p_latitude IN NUMBER, p_longitude IN NUMBER, p_foto_uri IN VARCHAR2, p_id_ocorrencia OUT NUMBER)`
  - trigger `trg_ocorrencia_escalona` em `ocorrencia`

- [ ] **Step 1: Write the failing test**

`database/tests/t2_occurrence.sql`:
- `prc_registrar_ocorrencia` com usuário válido devolve `p_id_ocorrencia` não nulo, grava `status='aberta'`, `relatos=1` e `bairro` **igual ao do usuário**;
- `tipo='Alagamento'` nasce `prioridade='alta'`; `tipo='Sem luz'` nasce `'normal'`;
- usuário inexistente levanta `-20001`;
- escalonamento: dois `UPDATE ocorrencia SET relatos = relatos + 1` levam `relatos` a 3 e `prioridade` de `alta` para `critica` — **uma única vez**: um terceiro incremento mantém `critica` (spec 8.2);
- ocorrência com `status='resolvida'` não escala;
- o `UPDATE` não levanta `ORA-04091`;
- `ROLLBACK`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./database/tests/run_tests.sh t2_occurrence.sql`
Expected: FAIL — `PLS-00201: identifier 'PRC_REGISTRAR_OCORRENCIA' must be declared`

- [ ] **Step 3: Implementar as duas rotinas em `V3__occurrence_routines.sql`**

`prc_registrar_ocorrencia`: busca `bairro` em `usuario` (`NO_DATA_FOUND` → `-20001`), define prioridade inicial (`Alagamento` e `Deslizamento` → `alta`, demais → `normal`), insere com `status='aberta'` e `relatos=1`, `RETURNING id INTO p_id_ocorrencia`.

`trg_ocorrencia_escalona`: `BEFORE UPDATE OF relatos ... FOR EACH ROW`. Condição de disparo único: `:OLD.relatos < 3 AND :NEW.relatos >= 3 AND :NEW.status <> 'resolvida'`. Sobe um nível alterando `:NEW.prioridade` direto — **sem `SELECT` na própria `ocorrencia`**, que é o que evitaria `ORA-04091`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./database/tests/run_tests.sh t2_occurrence.sql`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add database/migrations/V3__occurrence_routines.sql database/tests/t2_occurrence.sql
git commit -m "feat(banco): prc_registrar_ocorrencia e trigger de escalonamento de prioridade"
```

---

### Task 4: Rotinas de abrigo — distância e controle de vagas

**Files:**
- Create: `database/migrations/V4__shelter_routines.sql`
- Test: `database/tests/t3_shelter.sql`

**Interfaces:**
- Consumes: Tarefa 2 (tabelas).
- Produces:
  - `fn_abrigo_mais_proximo(p_latitude IN NUMBER, p_longitude IN NUMBER) RETURN NUMBER`
  - `prc_controle_vagas(p_id_abrigo IN NUMBER, p_quantidade IN NUMBER)`

- [ ] **Step 1: Write the failing test**

`database/tests/t3_shelter.sql`, com três abrigos inseridos em coordenadas conhecidas:
- `fn_abrigo_mais_proximo` devolve o id do mais perto **entre os que têm vaga**, ignorando o mais perto se estiver com `vagas_ocupadas = capacidade`;
- com todos lotados, devolve `NULL` sem levantar `NO_DATA_FOUND`;
- é chamável dentro de `SELECT ... FROM dual` (garante ausência de DML na função, que daria `ORA-14551`);
- `prc_controle_vagas(id, 2)` soma 2 em `vagas_ocupadas`; `(id, -1)` subtrai 1;
- estourar a capacidade levanta `-20002`; id inexistente `-20003`; saída maior que a ocupação `-20004`;
- `ROLLBACK`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./database/tests/run_tests.sh t3_shelter.sql`
Expected: FAIL — `PLS-00201: identifier 'FN_ABRIGO_MAIS_PROXIMO' must be declared`

- [ ] **Step 3: Implementar as duas rotinas em `V4__shelter_routines.sql`**

`fn_abrigo_mais_proximo`: Haversine com raio 6371 km, graus→radianos por `graus * ACOS(-1) / 180`, filtrando `vagas_ocupadas < capacidade`, ordenando por distância e pegando a primeira linha. `NO_DATA_FOUND` → `RETURN NULL`. Sem DML.

`prc_controle_vagas`: `SELECT ... FOR UPDATE` no abrigo (o lock é o motivo de a rotina existir — spec 9.3), `NO_DATA_FOUND` → `-20003`, valida os dois limites contra `capacidade` e zero, e só então `UPDATE`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./database/tests/run_tests.sh t3_shelter.sql`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add database/migrations/V4__shelter_routines.sql database/tests/t3_shelter.sql
git commit -m "feat(banco): fn_abrigo_mais_proximo (Haversine) e prc_controle_vagas com lock"
```

---

### Task 5: Rotinas de ajuda e indicadores

**Files:**
- Create: `database/migrations/V5__help_indicators_routines.sql`
- Test: `database/tests/t4_help.sql`

**Interfaces:**
- Consumes: Tarefa 2 (tabelas).
- Produces:
  - `prc_encerrar_pedidos_antigos(p_dias IN NUMBER, p_qtd_encerrados OUT NUMBER)`
  - `fn_ocupacao_abrigos RETURN NUMBER` — percentual com uma casa, 0 quando a capacidade total é 0
  - view `vw_indicadores` — uma linha com as 12 colunas da spec 9.4/5.2 (o README antigo listava 11 por omitir `pedidosResolvidos`; o `repositorio.js` devolvia 12)

- [ ] **Step 1: Write the failing test**

`database/tests/t4_help.sql`:
- `prc_encerrar_pedidos_antigos(7, qtd)` marca `atendido` só os `aguardando` com `criado_em` anterior a 7 dias, devolve a contagem em `p_qtd_encerrados`, e **não** toca em pedidos recentes nem nos já `resolvido`;
- sem nada a encerrar, devolve 0 (não `NULL`);
- `fn_ocupacao_abrigos` devolve 0 quando não há abrigo, e o percentual correto com capacidade conhecida (ex.: 68 de 80 → 85);
- `vw_indicadores` devolve exatamente uma linha, e `ocorrenciasCriticas` conta críticas **não resolvidas**;
- `ROLLBACK`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./database/tests/run_tests.sh t4_help.sql`
Expected: FAIL — identificadores não declarados

- [ ] **Step 3: Implementar em `V5__help_indicators_routines.sql`**

`prc_encerrar_pedidos_antigos`: `UPDATE ... WHERE status='aguardando' AND criado_em < SYSTIMESTAMP - NUMTODSINTERVAL(p_dias,'DAY')`, depois `p_qtd_encerrados := SQL%ROWCOUNT`. Alvo é `atendido`, não `resolvido` (spec 8.2).

`vw_indicadores`: as 12 agregações em um `SELECT ... FROM dual`, nomes de coluna em maiúsculo (o repositório faz o apelido para camelCase). `fn_ocupacao_abrigos` isolada para a view e a API não duplicarem a divisão.

- [ ] **Step 4: Run test to verify it passes**

Run: `./database/tests/run_tests.sh t4_help.sql`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add database/migrations/V5__help_indicators_routines.sql database/tests/t4_help.sql
git commit -m "feat(banco): encerramento em lote, ocupacao e view de indicadores"
```

---

### Task 6: Carga de abrigos, seed de demonstração e README do banco

**Files:**
- Create: `database/migrations/V6__shelters_seed.sql`, `database/seed_demo.sql`, `database/README.md`
- Test: `database/tests/t5_seed.sql`

**Interfaces:**
- Consumes: Tarefas 2–5.
- Produces: 3 abrigos na região central de São Paulo (~-23.55/-46.63, coerentes com as coordenadas que as telas já usam); `seed_demo.sql` com 3 usuários de demonstração, ocorrências em estágios diferentes, pedidos, ofertas e check-ins.

- [ ] **Step 1: Write the failing test**

`database/tests/t5_seed.sql`: após `V6`, `COUNT(*) FROM abrigo >= 3`, ao menos um com vaga e um lotado (para `fn_abrigo_mais_proximo` ter os dois casos), e `fn_abrigo_mais_proximo(-23.55, -46.63)` devolve id não nulo.

- [ ] **Step 2: Run test to verify it fails**

Run: `./database/tests/run_tests.sh t5_seed.sql`
Expected: FAIL — tabela `abrigo` vazia

- [ ] **Step 3: Escrever a carga, o seed e o README do banco**

`V6`: `INSERT` dos abrigos, idempotente por `MERGE` ou `WHERE NOT EXISTS` (migração create-only roda uma vez, mas o script precisa ser seguro se alguém rodar à mão).

`seed_demo.sql`: **fora** do Flyway (spec 13.2/13.3), com aviso no topo. Três usuários com hash bcrypt pré-computado de uma senha conhecida e documentada — nunca senha em texto. Ocorrências, pedidos e check-ins suficientes para o Mapa e a aba Ajuda não nascerem vazios.

`database/README.md`: substitui o `[PESSOA 3/4]` do projeto antigo. Ordem de execução, como rodar à mão no SQL Developer, a tabela de códigos `-20001..-20005`, e a **receita de três contas** para demonstrar o trigger pelo app (registra com A, confirma com B, confirma com C).

- [ ] **Step 4: Run test to verify it passes**

Run: `./database/tests/run_tests.sh` (todos, do t0 ao t5)
Expected: PASS em todos

- [ ] **Step 5: Commit**

```bash
git add database/migrations/V6__shelters_seed.sql database/seed_demo.sql database/README.md database/tests/t5_seed.sql
git commit -m "feat(banco): carga de abrigos, seed de demonstracao opt-in e README do banco"
```

---

# Fase 2 — API

### Task 7: Projeto Spring Boot, Flyway e health

**Files:**
- Create: `pom.xml`, `Dockerfile`, `src/main/resources/application.yml`, `src/main/java/br/com/fiap/alertabairro/AlertaBairroApplication.java`, `src/main/java/br/com/fiap/alertabairro/config/OpenApiConfig.java`
- Modify: `docker-compose.yml` (serviço `api`)
- Test: `src/test/java/br/com/fiap/alertabairro/SchemaIT.java`

**Interfaces:**
- Consumes: Tarefas 2–6 (arquivos em `database/migrations/`).
- Produces: contexto Spring com `DataSource` Oracle e `JdbcClient` disponíveis para injeção; Flyway aplicando `database/migrations/` na subida; `/actuator/health` e `/swagger-ui.html`.

- [ ] **Step 1: Write the failing test**

`SchemaIT`: `@SpringBootTest` que, com o contexto de pé, consulta `user_objects` e afirma que existem as 6 tabelas, as 4 procedures/functions nomeadas (`prc_registrar_ocorrencia`, `prc_controle_vagas`, `prc_encerrar_pedidos_antigos`, `fn_abrigo_mais_proximo`), o trigger `trg_ocorrencia_escalona` e a view `vw_indicadores`, todos com `status = 'VALID'` — objeto PL/SQL inválido compila mas falha em runtime, e esse teste é o que pega isso.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SchemaIT`
Expected: FAIL — projeto não existe

- [ ] **Step 3: Criar o projeto**

`pom.xml`: Java 21, Spring Boot 3.5.x (fixar a estável), dependências `web`, `jdbc`, `security`, `oauth2-resource-server`, `validation`, `actuator`, `flyway-core`, `flyway-database-oracle`, `com.oracle.database.jdbc:ojdbc11`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-test`.

**Convenção de testes, e ela importa para o risco da spec seção 11:** classe terminada em `Test` é unitária e roda em `mvn test` **sem Docker**; terminada em `IT` precisa de banco e roda em `mvn verify`, via `maven-failsafe-plugin` configurado aqui. É isso que mantém a promessa de que quem não tem Docker ainda roda parte da suíte. No desenvolvimento, uma classe `IT` isolada roda com `mvn -q test -Dtest=<Classe>`, porque o `-Dtest` explícito sobrepõe os padrões do surefire.

`application.yml`: datasource por variável de ambiente; `spring.flyway.locations=filesystem:${CAMINHO_MIGRACAO:../banco/migracao}`; `spring.jackson.date-format` / serialização de `OffsetDateTime` com offset; limite de corpo 100 KB; Hikari com `maximum-pool-size` explícito; `spring.jdbc.template.fetch-size` alinhado a 200.

`Dockerfile` multi-stage: build com Maven, runtime com JRE 21, `-XX:MaxRAMPercentage`, `TZ=America/Sao_Paulo`, e `COPY banco/migracao` — logo o **contexto de build é a raiz do repositório**, não `api/`.

`docker-compose.yml`: serviço `api` com `depends_on: oracle: condition: service_healthy`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SchemaIT` e `docker compose up -d --build && curl -fsS localhost:8080/actuator/health`
Expected: PASS; health `{"status":"UP"}`

- [ ] **Step 5: Commit**

```bash
git add pom.xml Dockerfile src/ docker-compose.yml
git commit -m "feat(api): projeto Spring Boot 3 com Flyway, Actuator e OpenAPI"
```

---

### Task 8: Cadastro, login, JWT e tratamento de erros

**Files:**
- Create: `web/AuthController.java`, `web/dto/CadastroRequest.java`, `web/dto/LoginRequest.java`, `web/dto/SessaoResponse.java`, `web/dto/UsuarioResponse.java`, `web/TratadorDeErros.java`, `service/UsuarioService.java`, `repository/UsuarioRepository.java`, `seguranca/TokenService.java`, `config/SecurityConfig.java`
- Test: `src/test/java/.../AuthControllerIT.java`

**Interfaces:**
- Consumes: Tarefa 7 (`JdbcClient`).
- Produces:
  - `UsuarioRepository.buscarPorEmail(String email) -> Optional<UsuarioComHash>`, `criar(String nome, String email, String bairro, String perfil, String senhaHash) -> UsuarioResponse`
  - `TokenService.gerar(long id, String perfil) -> String`; claims `sub` = id, `perfil`; HS256; expiração 8h
  - `UsuarioResponse(long id, String nome, String email, String bairro, String perfil)` — sem `senhaHash`
  - `Autenticado.id()` / `.perfil()` para os controllers seguintes obterem o usuário do token
  - `NaoEncontradoException` e `ConflitoException` — é o que as Tarefas 9–15 lançam para obter 404 e 409 sem conhecer HTTP
  - `TratadorDeErros` mapeando: `MethodArgumentNotValidException`→400, `AuthenticationException`→401, `AccessDeniedException`→403, `NaoEncontradoException`→404, `ConflitoException`→409, erro Oracle `-20000..-20999`→**422** com a mensagem limpa de `ORA-xxxxx:`, `DuplicateKeyException`→409, resto→500 sem detalhe. Sempre `{"erro": "..."}`.

- [ ] **Step 1: Write the failing test**

`AuthControllerIT` (MockMvc):
- `POST /auth/cadastro` válido → 201, corpo com `token` e `usuario`, **sem** nenhum campo de senha;
- e-mail é normalizado (`" Maria@X.COM "` grava `maria@x.com`);
- e-mail repetido → 409;
- `perfil` fora do domínio → 400; senha com 5 caracteres → 400;
- `POST /auth/login` correto → 200 com token;
- senha errada e e-mail inexistente → 401 **com a mesma mensagem** nos dois casos;
- **Review Focus 1:** `GET /ocorrencias` sem header → 401; com `Bearer lixo` → 401; com token assinado por outro segredo → 401; com token expirado → 401. Nenhum deles 500.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AuthControllerIT`
Expected: FAIL — 404 em todas as rotas

- [ ] **Step 3: Implementar autenticação e o tratador**

`SecurityConfig`: `SecurityFilterChain` stateless, CSRF off, `/auth/**`, `/actuator/health`, `/swagger-ui/**` e `/v3/api-docs/**` liberados, resto autenticado. `NimbusJwtDecoder.withSecretKey` com algoritmo fixo HS256 — assinatura, expiração e algoritmo validados pela biblioteca, não por nós. Converter que transforma a claim `perfil` em `ROLE_<PERFIL>` maiúsculo, para `@PreAuthorize("hasRole('VOLUNTARIO')")` funcionar nas tarefas seguintes. `BCryptPasswordEncoder` com custo 10. CORS liberado para `Authorization` e `Content-Type` (o Expo Web precisa; o app nativo não).

`UsuarioService.autenticar`: compara sempre contra um hash — o do usuário ou um hash fixo quando o e-mail não existe — para o tempo de resposta não revelar quem tem conta.

Validação por Bean Validation nos records de request: `@NotBlank`, `@Size`, `@Email`, `@Pattern` para os domínios.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AuthControllerIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): cadastro, login com bcrypt, JWT HS256 e tratador global de erros"
```

---

### Task 9: Listar e registrar ocorrências

**Files:**
- Create: `web/OcorrenciaController.java`, `web/dto/NovaOcorrenciaRequest.java`, `web/dto/OcorrenciaResponse.java`, `service/OcorrenciaService.java`, `repository/OcorrenciaRepository.java`, `web/dto/Paginacao.java`
- Test: `.../OcorrenciaListagemIT.java`

**Interfaces:**
- Consumes: Tarefa 8 (`Autenticado`, `TratadorDeErros`).
- Produces:
  - `OcorrenciaResponse(long id, String tipo, String descricao, double latitude, double longitude, String status, String prioridade, int relatos, String bairro, String fotoUri, OffsetDateTime criadaEm, boolean meu)` — `idUsuario` **nunca** sai
  - `OcorrenciaRepository.listar(Paginacao p) -> List<OcorrenciaComAutor>`, `buscar(long id) -> Optional<OcorrenciaComAutor>`, `registrar(long idUsuario, NovaOcorrenciaRequest r) -> long` (via `SimpleJdbcCall` em `prc_registrar_ocorrencia`)
  - `Paginacao(int limite, int deslocamento)` com validação 1..200 / ≥0 — reutilizado pela Tarefa 13

- [ ] **Step 1: Write the failing test**

`OcorrenciaListagemIT`:
- `GET /ocorrencias` autenticado → 200, ordenado por `criadaEm` desc, nenhum item com campo `idUsuario`;
- `meu` é `true` só nas ocorrências do usuário do token;
- `POST /ocorrencias` → 201 com `status='aberta'`, `relatos=1`, e `bairro` igual ao do usuário (prova que passou pela procedure, não por `INSERT` direto);
- `criadaEm` casa com `^\d{4}-\d{2}-\d{2}T.*[+-]\d{2}:\d{2}$` — **com offset**;
- **Review Focus 5:** `tipo = "Árvore caída"` volta idêntico do banco, byte a byte;
- **Review Focus 3:** `?limite=0`, `?limite=201`, `?limite=abc`, `?deslocamento=-1` → 400, nunca 500;
- `?limite=1&deslocamento=1` devolve 1 item e pula o primeiro;
- `POST` sem `tipo`, com `descricao` de 501 caracteres, ou `fotoUri` com 501 caracteres → 400.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OcorrenciaListagemIT`
Expected: FAIL — 404

- [ ] **Step 3: Implementar listagem e registro**

`OcorrenciaRepository.registrar` usa `SimpleJdbcCall` declarando os 7 parâmetros na ordem da Tarefa 3 — ordem errada dá `ORA-06550`, então a ordem é a da Interfaces. `listar` usa `OFFSET :deslocamento ROWS FETCH NEXT :limite ROWS ONLY` com projeção explícita de colunas.

`OcorrenciaService` converte para `OcorrenciaResponse` calculando `meu` a partir do id do token; o id do autor morre na camada de serviço.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OcorrenciaListagemIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): GET e POST /ocorrencias com paginacao validada e flag meu"
```

---

### Task 10: Confirmar ocorrência numa só transação

**Files:**
- Modify: `web/OcorrenciaController.java`, `service/OcorrenciaService.java`, `repository/OcorrenciaRepository.java`
- Test: `.../OcorrenciaConfirmacaoIT.java`

**Interfaces:**
- Consumes: Tarefa 9.
- Produces: `PATCH /ocorrencias/{id}` com `{"acao":"confirmar"}`; `OcorrenciaRepository.registrarConfirmacao(long idOcorrencia, long idUsuario) -> boolean` (false quando já existia) e `incrementarRelatos(long id) -> int`.

Esta tarefa corrige o defeito 5.3(d) da spec: os dois passos num único commit.

- [ ] **Step 1: Write the failing test**

`OcorrenciaConfirmacaoIT`:
- confirmar ocorrência de outro usuário → 200 com `relatos` + 1;
- confirmar duas vezes → a segunda dá 409 e `relatos` **não** muda;
- confirmar a própria ocorrência → 409;
- confirmar ocorrência `resolvida` → 409;
- `acao` desconhecida → 400;
- id inexistente → 404;
- três usuários distintos confirmando levam `prioridade` de `alta` a `critica` (o trigger da Tarefa 3 visto de fora);
- **Review Focus 2:** duas chamadas concorrentes do mesmo usuário (duas threads, mesmo token) → exatamente uma 200 e uma 409, e `relatos` sobe exatamente 1.
- **atomicidade:** forçar falha no incremento depois da confirmação gravada (ex.: id removido entre os passos, ou stub do repositório que lança) e afirmar que a confirmação **não** permaneceu — o teste que o back antigo não tinha.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OcorrenciaConfirmacaoIT`
Expected: FAIL

- [ ] **Step 3: Implementar `confirmar` como uma transação**

`OcorrenciaService.confirmar` anotado `@Transactional`: lê a ocorrência **com lock** (`SELECT ... FOR UPDATE`), aplica as três recusas (resolvida / própria / já confirmada) e só então grava confirmação e incremento. `registrarConfirmacao` devolve `false` ao capturar violação de PK (`DuplicateKeyException`), que o serviço traduz em 409.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OcorrenciaConfirmacaoIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "fix(api): confirmacao de ocorrencia atomica, com lock e sem corrida"
```

---

### Task 11: Atender, resolver e excluir ocorrência

**Files:**
- Modify: `web/OcorrenciaController.java`, `service/OcorrenciaService.java`, `repository/OcorrenciaRepository.java`
- Test: `.../OcorrenciaFluxoIT.java`

**Interfaces:**
- Consumes: Tarefa 10.
- Produces: ações `atender` e `resolver` em `PATCH /ocorrencias/{id}`; `DELETE /ocorrencias/{id}`; `OcorrenciaRepository.alterarStatus(long id, String status) -> int`, `excluir(long id) -> int`.

Corrige o defeito 5.3(e) da spec.

- [ ] **Step 1: Write the failing test**

`OcorrenciaFluxoIT`:
- `atender` com perfil `morador` → 403; com `voluntario` → 200 e `em_atendimento`;
- `atender` ocorrência que não está `aberta` → 409;
- `resolver` com `voluntario` → 200 e `resolvida`; `resolver` a já resolvida → 409;
- status nunca retrocede: `resolver` depois `atender` → 409;
- `DELETE` pelo autor → 204; por outro usuário → 403; de id inexistente → 404;
- `DELETE` remove as confirmações filhas sem erro de FK;
- **concorrência:** dois voluntários chamando `atender` ao mesmo tempo → uma 200 e uma 409, nunca duas 200.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OcorrenciaFluxoIT`
Expected: FAIL

- [ ] **Step 3: Implementar a máquina de estados com lock**

`atender`/`resolver` em `@Transactional`, lendo com `FOR UPDATE` antes de decidir. Transições permitidas apenas para frente: `aberta → em_atendimento → resolvida`. `@PreAuthorize("hasRole('VOLUNTARIO')")` nos dois. `excluir` verifica autoria antes.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OcorrenciaFluxoIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): maquina de estados da ocorrencia com lock, e exclusao pelo autor"
```

---

### Task 12: Abrigos

**Files:**
- Create: `web/AbrigoController.java`, `web/dto/AbrigoResponse.java`, `web/dto/VagasRequest.java`, `service/AbrigoService.java`, `repository/AbrigoRepository.java`
- Test: `.../AbrigoIT.java`

**Interfaces:**
- Consumes: Tarefa 8.
- Produces:
  - `AbrigoResponse(long id, String nome, String endereco, double latitude, double longitude, int capacidade, int vagasOcupadas)`
  - `AbrigoRepository.listar()`, `maisProximo(double lat, double lng) -> Optional<AbrigoResponse>` (via `fn_abrigo_mais_proximo`), `controlarVagas(long id, int quantidade)` (via `prc_controle_vagas`)

- [ ] **Step 1: Write the failing test**

`AbrigoIT`:
- `GET /abrigos` → 200 ordenado por nome;
- `GET /abrigos/proximo?lat=-23.55&lng=-46.63` → 200 com o abrigo com vaga mais próximo;
- com todos lotados → 200 e corpo `null` (é o que o app espera para mostrar "Nenhum abrigo com vagas");
- **Review Focus 4:** `?lat=91`, `?lng=-181`, `?lat=abc`, e ausência de `lat`/`lng` → 400, nunca 500;
- `PATCH /abrigos/{id}/vagas` com `morador` → 403; com `voluntario` e `{"quantidade":2}` → 200 e `vagasOcupadas` + 2;
- `quantidade` de 0 → 400; acima de 1000 → 400; não inteiro → 400;
- estourar a capacidade → **422** com a mensagem `Abrigo sem vagas suficientes` vinda do `-20002`;
- id inexistente → 422 ou 404 conforme o `-20003` (afirmar o que o tratador produz, e que a mensagem chega ao cliente).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AbrigoIT`
Expected: FAIL — 404

- [ ] **Step 3: Implementar os três endpoints**

`maisProximo` chama a function dentro de `SELECT ... FROM dual` como subconsulta do `SELECT` do abrigo — uma ida ao banco. `controlarVagas` via `SimpleJdbcCall`; o 422 vem do tratador da Tarefa 8 sem código novo.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AbrigoIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): abrigos, abrigo mais proximo via function e controle de vagas"
```

---

### Task 13: Listar e criar pedidos de ajuda

**Files:**
- Create: `web/AjudaController.java`, `web/dto/NovoPedidoRequest.java`, `web/dto/PedidoResponse.java`, `service/AjudaService.java`, `service/Distancia.java`, `service/Coordenada.java`, `repository/AjudaRepository.java`, `repository/CheckinRepository.java`
- Test: `.../AjudaListagemIT.java`, `.../DistanciaTest.java`

**Interfaces:**
- Consumes: Tarefas 8, 9 (`Paginacao`).
- Produces:
  - `PedidoResponse(long id, String modo, String categoria, String descricao, String urgencia, String status, String distancia, boolean meu)` — **sem** `latitude`, `longitude` nem `idUsuario`
  - `Distancia.km(double lat1, double lng1, double lat2, double lng2) -> double` e `Distancia.formatar(double km) -> String` (`"300 m"`, `"1,2 km"`)
  - `Coordenada(double latitude, double longitude)` — record
  - `AjudaRepository.listar(Paginacao)`, `criar(...)`
  - `CheckinRepository.ultimaPosicao(long idUsuario) -> Optional<Coordenada>` — lê a tabela `checkin`, por isso mora no repositório de check-in e não no de ajuda; a Tarefa 15 acrescenta `registrar` ao mesmo arquivo

- [ ] **Step 1: Write the failing test**

`DistanciaTest` (unitário, sem Spring): `formatar(0.3)` → `"300 m"`; `formatar(1.23)` → `"1,2 km"` (vírgula, não ponto); `km` entre dois pontos conhecidos com tolerância.

`AjudaListagemIT`:
- `GET /ajuda?lat=&lng=` → 200 com `distancia` preenchida, `aguardando` antes de `atendido`;
- resposta **não contém** `latitude`, `longitude` nem `idUsuario` em nenhum item — a asserção é sobre o JSON bruto, não sobre o DTO;
- `meu` correto para o usuário do token;
- sem `lat`/`lng` na query, usa o último check-in do usuário;
- sem check-in e sem query, `distancia` é a string de fallback e a requisição ainda é 200;
- `POST /ajuda` → 201 com `status='aguardando'`;
- `modo`/`urgencia` fora do domínio → 400; `categoria` vazia → 400.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest='DistanciaTest,AjudaListagemIT'`
Expected: FAIL

- [ ] **Step 3: Implementar listagem, criação e o mapper de privacidade**

A conversão para `PedidoResponse` é o único lugar que vê `latitude`/`longitude` do solicitante, e é onde eles param (spec 9.3). Posição de referência: a da query, senão `ultimaPosicao`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest='DistanciaTest,AjudaListagemIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): rede de ajuda com distancia calculada e coordenada do solicitante preservada"
```

---

### Task 14: Resolver, excluir e encerrar pedidos

**Files:**
- Modify: `web/AjudaController.java`, `service/AjudaService.java`, `repository/AjudaRepository.java`
- Test: `.../AjudaFluxoIT.java`

**Interfaces:**
- Consumes: Tarefa 13.
- Produces: `PATCH /ajuda/{id}` (`{"acao":"resolver"}`, só o autor), `DELETE /ajuda/{id}` (só o autor), `POST /ajuda/encerrar-antigos` (🦺) → `{"encerrados": n}`; `AjudaRepository.encerrarAntigos(int dias) -> int` via `prc_encerrar_pedidos_antigos`.

- [ ] **Step 1: Write the failing test**

`AjudaFluxoIT`:
- `PATCH` pelo autor → 200 com `status='resolvido'`; por outro → 403; id inexistente → 404; `acao` diferente → 400;
- `DELETE` pelo autor → 204; por outro → 403;
- `POST /ajuda/encerrar-antigos` com `morador` → 403; com `voluntario` → 200 e `{"encerrados": n}`;
- `{"dias": 0}` e `{"dias": 366}` → 400; corpo vazio usa o padrão 7;
- pedido recente não é encerrado.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AjudaFluxoIT`
Expected: FAIL

- [ ] **Step 3: Implementar as três ações**

Verificação de autoria no serviço antes de qualquer escrita. `encerrarAntigos` via `SimpleJdbcCall` lendo o parâmetro `OUT`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AjudaFluxoIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): resolver/excluir pedido pelo autor e encerramento em lote"
```

---

### Task 15: Check-ins e indicadores

**Files:**
- Create: `web/CheckinController.java`, `web/IndicadorController.java`, `web/dto/CheckinRequest.java`, `web/dto/IndicadoresResponse.java`, `service/CheckinService.java`, `service/IndicadorService.java`, `repository/IndicadorRepository.java`
- Modify: `repository/CheckinRepository.java` (criado na Tarefa 13 — acrescentar `registrar`)
- Test: `.../CheckinIT.java`, `.../IndicadorIT.java`

**Interfaces:**
- Consumes: Tarefa 8.
- Produces: `POST /checkins` → 201 `{id, status}`; `GET /indicadores` → 200 com as 11 métricas + `ocupacaoAbrigosPct`; `CheckinRepository.registrar(long idUsuario, String status, Double latitude, Double longitude) -> long`, acrescentado ao repositório que a Tarefa 13 criou.

- [ ] **Step 1: Write the failing test**

`CheckinIT`: `{"status":"estou_bem"}` com coordenada → 201; sem coordenada → 201 (opcional); `status` fora do domínio → 400; coordenada fora de faixa → 400. Após um check-in com coordenada, `GET /ajuda` sem `lat`/`lng` passa a trazer `distancia` calculada — prova a ligação com a Tarefa 13.

`IndicadorIT`: `GET /indicadores` → 200 com as chaves exatas da spec 5.2; `ocupacaoAbrigosPct` com uma casa decimal; 0 quando não há abrigo (sem divisão por zero); contadores de 24h ignorando registro mais antigo.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest='CheckinIT,IndicadorIT'`
Expected: FAIL

- [ ] **Step 3: Implementar os dois endpoints**

`IndicadorRepository` lê `vw_indicadores` com uma consulta só, apelidando as colunas para camelCase. `@Transactional(readOnly = true)` nas leituras.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q verify`
Expected: PASS em toda a suíte

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): check-ins e painel de indicadores a partir da view"
```

---

# Fase 3 — Integração com o app

### Task 16: Branch no front ligando o app na API real

**Files:**
- Modify (no repositório `GlobalSolution26_App`, branch `feat/integracao-api-real`): `app/src/servicos/api.ts` **e** `app/alertabairro/src/servicos/api.ts` — as duas cópias, idênticas
- Test: `app/alertabairro` → `npx tsc --noEmit`

**Interfaces:**
- Consumes: Tarefas 8–15 (as 10 chamadas que o app faz).
- Produces: `api.ts` mantendo **exatamente** as assinaturas exportadas atuais, para nenhuma tela mudar.

- [ ] **Step 1: Criar a branch e verificar o ponto de partida**

```bash
cd ../GlobalSolution26_App && git checkout -b feat/integracao-api-real
diff app/src/servicos/api.ts app/alertabairro/src/servicos/api.ts   # deve ser vazio
```

- [ ] **Step 2: Reescrever `api.ts` (as duas cópias)**

Mudanças, todas dentro deste arquivo:
- `BASE_URL` vem de `process.env.EXPO_PUBLIC_API_URL`; `USE_MOCK` passa a ser `!process.env.EXPO_PUBLIC_API_URL` — sem a variável o app continua nos dados falsos, que é anti-objetivo da spec seção 2.
- `listarPedidos()` e `criarPedido()` chamam `obterLocalizacao()` e enviam `lat`/`lng` (spec 13.1). `obterLocalizacao` nunca lança: cai em `COORDENADA_PADRAO`.
- Normalizar `status === 'resolvido'` para `'atendido'` nos pedidos, com comentário apontando a spec 7.3 e marcando como remoção futura.
- `definirToken` passa a guardar o token também para as chamadas seguintes do mesmo processo, como já fazia.

- [ ] **Step 3: Verificar tipos e paridade das cópias**

Run: `cd app/alertabairro && npx tsc --noEmit` e `diff ../src/servicos/api.ts src/servicos/api.ts`
Expected: zero erro de tipo; `diff` vazio

- [ ] **Step 4: Teste de fumaça contra a API real**

Com `docker compose up -d` na pasta do back: subir o app com `EXPO_PUBLIC_API_URL=http://<IP>:8080`, cadastrar, registrar uma ocorrência, abrir o mapa, pedir o abrigo mais próximo, enviar "Estou bem" e criar um pedido. Confirmar que nenhum cartão da aba Ajuda mostra "distância desconhecida".
Expected: as 3 funcionalidades funcionando sem dados falsos

- [ ] **Step 5: Commit e push**

```bash
git add app/src/servicos/api.ts app/alertabairro/src/servicos/api.ts
git commit -m "feat: ligar o app na API real por EXPO_PUBLIC_API_URL"
git push -u origin feat/integracao-api-real
```

---

# Fase 4 — Documentação (fim do mínimo entregável)

### Task 17: DER, MER, README e links

**Files:**
- Create: `docs/modelo-dados.md`, `README.md`, `README.txt`, `links.txt`

**Interfaces:**
- Consumes: todas as tarefas anteriores.
- Produces: os entregáveis 3 do enunciado.

- [ ] **Step 1: Escrever `docs/modelo-dados.md`**

DER físico em Mermaid `erDiagram` (renderiza no GitHub sem imagem anexa), MER conceitual com cardinalidades, e dicionário de dados coluna por coluna: tipo, nulidade, default, domínio e descrição. Incluir a tabela de rotinas PL/SQL com a justificativa de uma linha de cada (spec 8 e 8.1).

- [ ] **Step 2: Escrever `README.md`**

Seções: o problema e o público; **ODS 11** (alvo 11.5) e ODS 13 com a ligação explicada; as 3 funcionalidades; arquitetura em camadas e onde cada regra mora (spec 9.3); como rodar (banco → API → app, com o aviso de que o primeiro boot do Oracle leva minutos); tabela das 17 endpoints; **gerenciamento de memória e eficiência** (spec 9.5, lado API, mais a seção já escrita do app); justificativa das tecnologias; link para o repositório do front e para a branch da integração; limites conhecidos — `foto_uri`, a tela de Detalhe dependendo das 200 mais recentes (spec 13.5), e R2 garantida por construção e não verificada.

- [ ] **Step 3: Gerar `README.txt` e `links.txt`**

`README.txt` com o mesmo conteúdo em texto puro, porque é o nome que o enunciado pede. `links.txt` com os dois links de repositório e placeholders marcados para os dois vídeos.

- [ ] **Step 4: Verificar**

Run: `./database/tests/run_tests.sh && mvn -q verify`
Expected: tudo verde; e os diagramas do `modelo-dados.md` renderizando (conferir no GitHub após o push)

- [ ] **Step 5: Commit**

```bash
git add docs/modelo-dados.md README.md README.txt links.txt
git commit -m "docs: DER, MER, dicionario de dados e README da entrega"
```

> **LINHA DE CORTE.** Com as Tarefas 1–17 concluídas a entrega está completa diante do enunciado. A Tarefa 18 é o diferencial técnico e só começa com o acima fechado (spec seção 10).

---

# Fase 5 — Diferencial

### Task 18: Suíte hermética com Testcontainers

**Files:**
- Modify: `pom.xml`, `src/test/resources/application-test.yml`
- Create: `src/test/java/.../SuporteOracle.java`
- Test: toda a suíte existente, rodando sem Oracle previamente de pé

**Interfaces:**
- Consumes: Tarefas 7–15 (os testes não mudam).
- Produces: classe base que provê o `DataSource` a partir de um container, via `@DynamicPropertySource`. Nenhum teste existente é reescrito — só passa a herdar de onde a URL vem.

- [ ] **Step 1: Write the failing test**

Com o compose derrubado (`docker compose down`), rodar `mvn -q verify`.
Expected: FAIL — conexão recusada em `localhost:1521`

- [ ] **Step 2: Adicionar Testcontainers**

`testcontainers`, `junit-jupiter` e `oracle-free` no escopo de teste. `SuporteOracle` com `@Container static OracleContainer` em `gvenzl/oracle-free:23-slim`, `withReuse(true)` para a iteração local não pagar o boot a cada execução, e `@DynamicPropertySource` injetando url/usuário/senha. Flyway roda as migrações de `database/migrations/` dentro do container — é isso que faz as rotinas PL/SQL serem de fato exercitadas, fechando o buraco 5.3(f) da spec.

- [ ] **Step 3: Fazer as classes de teste herdarem o suporte**

Única mudança nas classes existentes: passar a estender `SuporteOracle`. As asserções não mudam.

- [ ] **Step 4: Run tests to verify they pass**

Run: `docker compose down && mvn -q verify`
Expected: PASS, sem Oracle externo

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/test
git commit -m "test: suite hermetica com Testcontainers exercitando as rotinas PL/SQL"
```
