# Banco de dados — AlertaBairro

Oracle. Modelo de dados, automação em PL/SQL e os testes que provam as duas coisas.

## O que tem aqui

| Caminho | Conteúdo |
|---|---|
| `migrations/V1__tables.sql` | 6 tabelas, constraints nomeadas, índices |
| `migrations/V2__pk_sequences.sql` | 5 sequences + 5 triggers de PK |
| `migrations/V3__occurrence_routines.sql` | `prc_registrar_ocorrencia`, `trg_ocorrencia_escalona` |
| `migrations/V4__shelter_routines.sql` | `fn_abrigo_mais_proximo`, `prc_controle_vagas` |
| `migrations/V5__help_indicators_routines.sql` | `prc_encerrar_pedidos_antigos`, `fn_ocupacao_abrigos`, `vw_indicadores` |
| `migrations/V6__shelters_seed.sql` | carga inicial de abrigos |
| `seed_demo.sql` | dados de demonstração — **opt-in**, fora das migrações |
| `reset_environment.sql` | **apaga tudo** — só no Oracle local |
| `apply.sh` | aplica as migrações em ordem |
| `tests/` | 6 scripts de teste + `run_tests.sh` |

## Como rodar

### No Docker local

```bash
docker compose up -d oracle     # primeiro boot leva alguns minutos: ele cria o banco
docker compose ps               # aguarde "healthy"

./database/apply.sh             # aplica V1..V6
./database/tests/run_tests.sh   # roda os 6 testes
```

Para recomeçar do zero (**apaga os dados**):

```bash
./database/apply.sh --reset
```

As migrações são **create-only**: reaplicá-las sobre um schema que já existe
dá `ORA-00955`. Durante o desenvolvimento use `--reset`. A partir da API, quem
cuida da incrementalidade é o Flyway.

### À mão, no SQL Developer (caminho da FIAP)

Abra e execute, nesta ordem: `V1`, `V2`, `V3`, `V4`, `V5`, `V6`. Depois
`tests/t1_schema.sql` até `t5_seed.sql` para conferir.

Os arquivos de migração são **SQL puro**, sem diretivas do SQL\*Plus — o mesmo
arquivo serve ao Flyway e ao SQL Developer. Em compensação, executado à mão o
script não aborta sozinho no primeiro erro: confira a saída de cada um.

Nada aqui exige privilégio de DBA. Não há `CREATE USER`, `TABLESPACE` nem
`GRANT`: tudo roda no schema corrente. O usuário da aplicação, no ambiente
local, é criado pela própria imagem Docker.

## Rotinas PL/SQL, e por que cada uma está no banco

O critério: entra no banco o que fica mais barato ou mais correto no banco.
Procedure decorativa não entra.

| Rotina | Por que no banco |
|---|---|
| `prc_registrar_ocorrencia` | Deriva o bairro do usuário, define a prioridade inicial, insere e devolve o id em **uma** ida ao banco e uma transação. |
| `trg_ocorrencia_escalona` | Garantia estrutural: a regra vale para qualquer caminho que incremente `relatos`, inclusive carga manual. |
| `fn_abrigo_mais_proximo` | A decisão é sobre o **conjunto** de abrigos. Resolver aqui devolve um id; resolver fora exigiria trafegar todos. |
| `prc_controle_vagas` | `SELECT ... FOR UPDATE` trava a linha. Dois voluntários simultâneos não passam da capacidade. |
| `prc_encerrar_pedidos_antigos` | `UPDATE` em lote com `SQL%ROWCOUNT`: uma ida ao banco onde a aplicação faria uma por linha. |
| `fn_ocupacao_abrigos` + `vw_indicadores` | 11 agregações sobre 4 tabelas devolvendo uma linha, em vez de trazer linhas para contar fora. |

Os 5 triggers `trg_*_bi` são infraestrutura de chave primária, não automação de
processo — não contam como rotina.

### Códigos de erro de negócio

`RAISE_APPLICATION_ERROR` entre `-20000` e `-20999`. A API traduz em **HTTP 422**
e repassa a mensagem ao app, então elas são escritas para o usuário final.

| Código | Mensagem |
|---|---|
| `-20001` | Usuario nao encontrado |
| `-20002` | Abrigo sem vagas suficientes |
| `-20003` | Abrigo nao encontrado |
| `-20004` | Quantidade de saida maior que as vagas ocupadas |
| `-20005` | Dados invalidos |

`-20900` é reservado para falha de asserção nos testes.

## Dados de demonstração

```bash
docker compose exec -T oracle sqlplus -S \
  alertabairro/alertabairro_dev@//localhost:1521/FREEPDB1 @/database/seed_demo.sql
```

Três contas, senha única `alertabairro123`:

| E-mail | Perfil | Papel na demonstração |
|---|---|---|
| `maria@alertabairro.demo` | morador | autora da ocorrência que está a um relato de escalar |
| `joao@alertabairro.demo` | idoso | já confirmou essa ocorrência |
| `ana@alertabairro.demo` | voluntario | pode atender, resolver e mexer em vagas |

### Receita para mostrar o trigger funcionando

A ocorrência "Rua alagada" já está com **2 relatos** e prioridade **alta**.
Entre com a conta da **Ana** e toque em "Também vejo isso aqui": o terceiro
relato dispara `trg_ocorrencia_escalona` e a prioridade vira **critica** na tela.

Precisa ser a Ana porque ninguém confirma a própria ocorrência (Maria) e ninguém
confirma duas vezes (João já confirmou). É por isso que o seed traz três contas:
com duas, o escalonamento não é demonstrável pelo app.

## Decisões de portabilidade

A versão do Oracle da FIAP é desconhecida, então o DDL foi escrito para o menor
denominador comum.

| Decisão | Em vez de | Por quê |
|---|---|---|
| `SEQUENCE` + trigger `BEFORE INSERT` | `GENERATED AS IDENTITY` | `IDENTITY` exige 12c+. Isto roda de 11g a 23ai. |
| `ROWNUM = 1` | `FETCH FIRST 1 ROW ONLY` | `FETCH FIRST` exige 12c+. |
| `DROP` em bloco com `EXCEPTION` | `DROP ... IF EXISTS` | `IF EXISTS` só existe no 23ai. |
| `VARCHAR2`/`NUMBER(1)` para flags | `BOOLEAN` em coluna | `BOOLEAN` em coluna só no 23ai. |
| `SELECT ... FROM dual` explícito | `VALUES` sem `FROM` | Sintaxe sem `FROM` só no 23ai. |
| `VARCHAR2(n CHAR)` | `VARCHAR2(n)` | `NLS_LENGTH_SEMANTICS` é `BYTE` por padrão: em `AL32UTF8`, `VARCHAR2(500)` comporta 500 **bytes**, ou seja 250 caracteres acentuados. Uma descrição normal em português era recusada com `ORA-12899`. `CHAR` existe desde o 9i. |

**Ressalva honesta:** isso foi garantido por disciplina de escrita e está
validado contra o Oracle 23ai Free do Docker. Não foi executado contra o Oracle
da FIAP.

### Uma armadilha que custou tempo

### Charset do cliente

`apply.sh` e `run_tests.sh` exportam `NLS_LANG=.AL32UTF8`. Sem isso, um cliente
com charset diferente (Windows em `Cp1252` é comum) grava os literais acentuados
de `V6` e `seed_demo.sql` deturpados. O `t5_seed.sql` detecta — mas só se alguém
o rodar.

O SQL\*Plus **encerra o comando ao encontrar `;` dentro de um comentário `--`**.
Um comentário terminado em ponto e vírgula no meio de um `CREATE TABLE` corta o
comando ao meio e produz `ORA-00931`. Nenhum comentário destes arquivos contém
`;`, e vale manter assim — isso quebraria também o SQL Developer.

## Testes

```bash
./database/tests/run_tests.sh                    # todos
./database/tests/run_tests.sh t2_occurrence.sql  # um só
./database/tests/run_tests.sh t6_lock.sh         # o teste de concorrência
```

Um script só conta como aprovado quando o `sqlplus` sai com código 0 **e** a
saída traz a linha `OK <nome>`. A segunda checagem não é redundante: as
diretivas `WHENEVER` moram dentro do arquivo de teste, então um arquivo
renomeado ou fora do *mount* faria o `sqlplus` imprimir `SP2-0310` e sair 0 —
e o runner anunciaria "passou" para um teste que nunca rodou.

Testes de concorrência precisam de duas sessões simultâneas, o que não cabe num
script SQL. Esses vêm como `.sh` e valem pelo código de saída.

Cada script afirma com `RAISE_APPLICATION_ERROR(-20900, 'FALHA: ...')` e usa
`WHENEVER SQLERROR EXIT FAILURE`, então o `sqlplus` sai com código diferente de
zero e o runner propaga a falha.

| Script | O que prova |
|---|---|
| `t0_connection.sql` | o container responde e o usuário da aplicação conecta |
| `t1_schema.sql` | tabelas, defaults, domínios (CHECK) e `ON DELETE CASCADE` |
| `t2_occurrence.sql` | registro, prioridade inicial, `-20001`, e o escalonamento disparando **uma vez** |
| `t3_shelter.sql` | abrigo mais próximo com vaga, `NULL` quando todos lotados, e `-20002`/`-20003`/`-20004` |
| `t4_help.sql` | encerramento em lote, ocupação sem divisão por zero, e a view |
| `t5_seed.sql` | a carga chegou inteira, e o acento não virou mojibake |
| `t6_lock.sh` | duas sessões simultâneas: o `FOR UPDATE` serializa de verdade |

`t3` e `t4` isolam-se da carga inicial antes de afirmar números, para não
quebrarem depois de `seed_demo.sql` rodar.

**Por que eles não deixam resíduo**, exatamente: cada script termina em
`ROLLBACK`, mas numa falha essa linha nunca é alcançada. O que protege os dados
são duas outras coisas — todo o DML vive dentro de **um único bloco anônimo**, e
uma exceção não tratada desfaz o bloco inteiro como se fosse um comando só; e as
diretivas são `WHENEVER SQLERROR EXIT FAILURE ROLLBACK`. Sem o `ROLLBACK` na
diretiva o padrão do SQL\*Plus é **COMMIT**, e qualquer DML emitido fora do bloco
sobreviveria a uma falha.
