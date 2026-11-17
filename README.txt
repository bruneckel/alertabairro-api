Este arquivo e a versao em texto puro do README.md, gerada a partir dele.
Para ler com as tabelas e os diagramas formatados, abra o README.md no GitHub.
============================================================================

===================
AlertaBairro -- API
===================


Rede comunitária de alerta e ajuda para eventos climáticos extremos.
Back-end da Global Solution 2 -- 2026 | turma 3SIOA

Moradores registram ocorrências do bairro, encontram o abrigo com vaga mais próximo, pedem
ou oferecem ajuda entre vizinhos, e avisam que estão bem com um toque.

  Parte                             Onde
  --------------------------------  ---------------------------------------------------------------------------------------------------------------------------------
  API, banco e automação PL/SQL     este repositório
  App mobile (React Native + Expo)  luanaestanislau/GlobalSolution26_App <https://github.com/luanaestanislau/GlobalSolution26_App> -- branch feat/integracao-api-real


----------------------------------------------------------------------------


O problema
----------


Quando o bairro alaga, a informação que decide o resgate está espalhada em grupos de
WhatsApp, e some no meio da conversa. Ninguém sabe quantas pessoas relataram o mesmo ponto
de alagamento, qual abrigo ainda tem vaga, ou se a vizinha idosa do número 40 está bem.

O público não é "a cidade": é o bairro durante o evento, e dentro dele as pessoas que
dependem de alguém saber onde elas estão -- idosos, famílias com crianças, quem mora em área
de risco.

A proposta é encurtar o tempo entre "o bairro alagou" e *"alguém sabe, alguém foi
atendido, e há registro disso"*.

ODS da ONU
----------


ODS 11 -- Cidades e comunidades sustentáveis, alvo 11.5: reduzir mortes e perdas
causadas por desastres, com foco na população vulnerável.

A contribuição é concreta e mensurável pelos próprios dados do sistema: o escalonamento
automático de prioridade faz um ponto de alagamento confirmado por três moradores subir na
fila antes de qualquer triagem manual; o controle de vagas impede que alguém seja enviado a
um abrigo lotado; e o check-in "estou bem" transforma ausência de notícia em informação.

ODS 13 -- Ação contra a mudança global do clima, alvo 13.1: fortalecer a capacidade
de adaptação a desastres relacionados ao clima. O histórico de ocorrências por bairro, com
tipo, prioridade e tempo até a resolução, é a matéria-prima de que uma prefeitura precisa
para saber onde investir em drenagem.


----------------------------------------------------------------------------


As três funcionalidades
-----------------------


Login e cadastro não contam, conforme o enunciado.

1. Mapa de ocorrências do bairro


Registrar alagamento, árvore caída, falta de luz ou deslizamento, com foto e GPS. Qualquer
vizinho pode tocar em "também vejo isso aqui" -- e ao terceiro relato a prioridade sobe
sozinha, por um trigger no banco. Voluntários assumem e resolvem; o autor pode excluir um
registro feito por engano.

2. Abrigos e rota segura


Lista dos abrigos com ocupação atual e, com um toque, o abrigo com vaga mais próximo --
calculado por uma function Haversine no banco, que ignora quem já está lotado. Voluntários
registram entrada e saída de pessoas, e o banco recusa passar da capacidade.

3. Rede de ajuda e check-in


Pedir ou oferecer ajuda (remédio, transporte, abrigo), com urgência. A lista mostra a que
distância cada pedido está -- mas nunca onde a pessoa está. E o botão "estou bem", que
avisa a rede com um toque.


----------------------------------------------------------------------------


Arquitetura
-----------


   [diagrama -- ver a versao em Markdown do README]


    web/         Controller · DTO (records) · @RestControllerAdvice
      ↓          Bean Validation nos DTOs de entrada
    service/     regras de fluxo · @Transactional · @PreAuthorize
      ↓
    repository/  JdbcClient (SQL) · SimpleJdbcCall (procedures e functions)
      ↓
    Oracle       tabelas · constraints · rotinas PL/SQL


Dependência só para baixo: o service não conhece HTTP, o repository não conhece regra
de negócio.

Onde cada regra mora, e por quê


Esta é a decisão central do projeto. O critério: cada regra fica onde ela é mais barata
ou mais correta.

  Regra                             Onde                                    Por quê
  --------------------------------  --------------------------------------  ----------------------------------------------------------
  Formato da entrada                web (Bean Validation)                   Recusar antes de ocupar conexão de banco
  "só voluntário atende"            service (@PreAuthorize)                 É política de acesso, não integridade de dado
  "só o autor exclui"               service                                 Idem
  Máquina de estados da ocorrência  service @Transactional + FOR UPDATE     Lê com lock, decide, escreve, commita -- uma transação
  Confirmação + incremento          service @Transactional                  Os dois passos num commit só: ou ambos, ou nenhum
  Escalonamento de prioridade       banco (trigger)                         Garantia estrutural: vale para qualquer caminho de escrita
  Capacidade do abrigo              banco (procedure + FOR UPDATE + CHECK)  Lock e invariante onde o dado mora
  Abrigo mais próximo               banco (function)                        Operação de conjunto
  Encerrar pedidos antigos          banco (procedure)                       Operação de conjunto, em lote
  Indicadores                       banco (view)                            Agregação
  Distância e privacidade           web (mapper de DTO)                     É formatação e política de exposição, não dado


O que isso corrigiu. O back-end anterior afirmava que "as regras pesadas ficam no
banco", mas mantinha a máquina de estados em JavaScript, sem transação e sem lock. Dois
defeitos reais vinham daí, e os dois têm teste agora:

  - confirmar fazia dois commits separados (gravar a confirmação, incrementar o
contador). Se o segundo falhasse, a pessoa gastava sua única confirmação e o contador não
subia. Hoje é uma transação, e um teste força a falha no incremento para provar que a
confirmação não permanece.
  - atender e resolver liam o status e só depois gravavam, sem lock. Dois voluntários
passavam juntos pelo teste "ainda está aberta?". Hoje a leitura é FOR UPDATE, e um teste
com duas requisições simultâneas afirma uma 200 e uma 409.


----------------------------------------------------------------------------


Como rodar
----------


Ordem: banco -> API -> app.

1 e 2. Banco e API, juntos


    cp .env.example .env
    # gere o segredo do JWT e coloque no .env:
    openssl rand -hex 48
    
    docker compose up -d --build


  NOTA: O primeiro boot do Oracle leva alguns minutos -- ele cria o banco. A API espera o
  NOTA: healthcheck antes de subir, então não é preciso cronometrar nada. Os boots seguintes são
  NOTA: rápidos, por causa do volume nomeado.

O Flyway aplica as seis migrações de database/migrations/ na subida da API.

    curl localhost:8080/actuator/health      # {"status":"UP"}
    open http://localhost:8080/swagger-ui.html


Dados de demonstração (opcional, recomendado antes de gravar vídeo):

    docker compose exec -T oracle sqlplus -S \
      alertabairro/alertabairro_dev@//localhost:1521/FREEPDB1 @/database/seed_demo.sql


Três contas, senha alertabairro123 -- e a receita para demonstrar o trigger ao vivo está em
database/README.md <database/README.md>.

3. App


No repositório do app, na branch feat/integracao-api-real:

    cd app/alertabairro
    npm install
    EXPO_PUBLIC_API_URL=http://SEU_IP:8080 npx expo start


Use o IP da máquina, não localhost -- o celular precisa alcançar o computador, e os
dois na mesma rede Wi-Fi. Sem a variável, o app continua nos dados falsos.

Rodando na FIAP, sem Docker


Os arquivos de database/migrations/ são SQL puro, sem diretivas do SQL\*Plus: abra
V1 a V6 no SQL Developer e execute em ordem. Depois aponte a API com
ORACLE_URL, ORACLE_USER e ORACLE_PASSWORD. Detalhes e ressalvas em
database/README.md <database/README.md>.


----------------------------------------------------------------------------


Endpoints
---------


17 endpoints. [JWT] exige Authorization: Bearer <token>; [VOL] só perfil voluntario.

  Método  Rota                                                                   Usada pelo app
  ------  ---------------------------------------------------------------------  --------------
  GET     /actuator/health                                                       não
  POST    /auth/cadastro                                                         sim
  POST    /auth/login                                                            sim
  GET     /ocorrencias [JWT]                                                     sim
  POST    /ocorrencias [JWT]                                                     sim
  PATCH   /ocorrencias/{id} [JWT] -- confirmar | atender [VOL] | resolver [VOL]  só confirmar
  DELETE  /ocorrencias/{id} [JWT] (autor)                                        não
  GET     /abrigos [JWT]                                                         sim
  GET     /abrigos/proximo?lat=&lng= [JWT]                                       sim
  PATCH   /abrigos/{id}/vagas [JWT][VOL]                                         não
  GET     /ajuda [JWT]                                                           sim
  POST    /ajuda [JWT]                                                           sim
  PATCH   /ajuda/{id} [JWT] (autor)                                              não
  DELETE  /ajuda/{id} [JWT] (autor)                                              não
  POST    /ajuda/encerrar-antigos [JWT][VOL]                                     não
  POST    /checkins [JWT]                                                        sim
  GET     /indicadores [JWT]                                                     não


Os sete que o app não usa são demonstráveis pelo Swagger UI.

Códigos: 400 entrada inválida | 401 token | 403 perfil | 404 inexistente |
409 conflito de estado | 413 corpo grande | 422 regra recusada pelo banco | 500
interno. Erro sempre como { "erro": "mensagem" }.

O 422 é o mais interessante: as rotinas PL/SQL falam com o usuário final por
RAISE_APPLICATION_ERROR entre −20000 e −20999, e a API repassa a mensagem. Estourar a
capacidade de um abrigo devolve, literalmente, {"erro":"Abrigo sem vagas suficientes"} --
escrito no banco, lido na tela.


----------------------------------------------------------------------------


Gerenciamento de memória e eficiência
-------------------------------------


Na API


  - Pool de conexões com teto explícito (HikariCP, maximum-pool-size). Conexão Oracle é
o recurso caro: pool sem limite derruba o banco antes da API.
  - Paginação obrigatória em toda listagem (limite <= 200). O heap que uma requisição
pode gastar fica limitado por contrato, não pela sorte de a tabela ainda ser pequena.
  - fetchSize do JDBC alinhado ao limite de página: o cursor não é materializado inteiro
na memória da aplicação.
  - Projeção explícita de colunas -- nunca SELECT *. senha_hash não é projetado em
nenhuma consulta de leitura.
  - Agregação e filtro no banco: a view de indicadores devolve uma linha em vez de N para
contar fora; a function de distância devolve um id em vez de todos os abrigos.
  - Uma ida ao banco por operação, via procedure com parâmetro OUT. Sem N+1.
  - @Transactional(readOnly = true) nas leituras: evita custo de flush e deixa a
intenção explícita.
  - JVM ciente do container: -XX:MaxRAMPercentage=75 em vez de -Xmx fixo, para o heap
respeitar o limite do Docker em vez de ignorá-lo. Com -XX:+ExitOnOutOfMemoryError, o
container morre e reinicia em vez de ficar degradado.
  - Imagem final só com JRE (multi-stage: build com Maven, runtime com JRE 21).
  - Corpo de requisição limitado a 100 KB por filtro próprio -- nenhuma propriedade do
Spring Boot cobre corpo JSON, e sem isso um POST grande vira pressão de heap antes de
qualquer validação rodar.

No banco


  - Índice em toda coluna de FK: sem ele, um DELETE no pai pega lock de tabela no filho.
  - Índices de ordenação que sustentam a paginação, para a listagem ler o índice em vez de
ordenar o conjunto.
  - Paginação pelo idioma ROWNUM, não OFFSET … FETCH NEXT -- além de portável para 11g, é
o que o otimizador do Oracle reconhece como Top-N.

No app


Já implementado pela equipe do front: FlatList virtualizada com initialNumToRender e
removeClippedSubviews; React.memo nos itens de lista; useMemo nos filtros; useCallback
nas props de função; limpeza em useEffect com flag de montagem; e compressão da foto
(1024 px, JPEG 60%) antes de enviar.


----------------------------------------------------------------------------


Tecnologias, e por que cada uma
-------------------------------


  Escolha                                   Alternativa descartada        Motivo
  ----------------------------------------  ----------------------------  -----------------------------------------------------------------------------------------------------------------------------------------
  Java 21 + Spring Boot 3.5.16              Boot 4.1.2 (o estável atual)  O Boot 4 renomeou os starters e saiu há pouco; com prazo curto, a geração 3.5 -- ainda suportada -- troca novidade por previsibilidade
  Spring JDBC (JdbcClient, SimpleJdbcCall)  JPA / Hibernate               O projeto é PL/SQL-cêntrico. JPA abstrai justamente o que precisa de controle: chamada de procedure, bind de parâmetro OUT, SQL explícito
  Spring Security + Resource Server         jjwt na mão                   Assinatura, expiração e algoritmo fixo HS256 validados pela biblioteca, não por código nosso
  Flyway                                    script solto                  O projeto anterior tinha um ajustes_api_v2.sql com <nome_da_constraint> para editar à mão -- exatamente o que migração versionada resolve
  Oracle 23ai Free (gvenzl/oracle-free)     oracle-xe:21                  PL/SQL é requisito do enunciado, e esta é a única imagem com ARM64 nativo
  springdoc-openapi                         nenhuma                       É o que torna demonstráveis, no vídeo, as sete rotas que o app não usa
  Bean Validation                           validação manual              A regra fica junto do campo, declarativa


----------------------------------------------------------------------------


Testes
------


    ./mvnw test                      # unitários, SEM Docker
    ./mvnw verify                    # tudo, precisa do banco de pé
    ./database/tests/run_tests.sh    # as rotinas PL/SQL, direto no Oracle


76 testes Java (5 unitários + 71 de integração) e 7 scripts de banco, todos passando.

A separação importa: classe terminada em Test é unitária e roda sem Docker; terminada em
IT precisa de banco. Quem não tem Docker ainda roda parte da suíte.

Os testes de banco rodam contra o Oracle de verdade, não contra um mock -- foi assim que
se descobriu que VARCHAR2(500) recusava uma descrição normal em português, porque conta
bytes e não caracteres. Três testes existem só para provar que a suíte pega o defeito que
alega pegar: removendo o FOR UPDATE de prc_controle_vagas, zerando o peso cos(latitude)
do Haversine, ou forçando a falha no incremento da confirmação, o teste correspondente falha.


----------------------------------------------------------------------------


Limites conhecidos
------------------


Escolhas, não esquecimentos.

  - foto_uri guarda o caminho no aparelho, não o arquivo. A solução de mercado (upload
para object storage, URL assinada) exigiria mexer na tela NovaOcorrencia, fora do escopo.
  - A tela de Detalhe depende das 200 ocorrências mais recentes. Ela filtra a lista em
memória em vez de buscar por id. Um GET /ocorrencias/{id} resolveria, mas a paridade com
as rotas existentes foi mantida de propósito. Nenhum cenário de demonstração chega a 200.
  - A portabilidade para o Oracle da FIAP é garantida por construção, não verificada. O
DDL foi escrito para o menor denominador comum (sem IDENTITY, sem FETCH FIRST, sem
IF EXISTS, sem privilégio de DBA), mas só foi executado contra o Oracle 23ai do Docker.
  - As janelas de 7 dias e 24 h deslocam se a sessão do banco estiver em outro fuso que o
servidor: criado_em é TIMESTAMP sem fuso. O Compose fixa os dois em
America/Sao_Paulo, então isso só morde no caminho FIAP.
  - 39 constraints NOT NULL ficaram com nome gerado pelo Oracle. As 30 de PK, FK, UK e
CHECK têm nome próprio.
  - A rota no mapa do app é uma linha reta até o abrigo, não segue as ruas.


----------------------------------------------------------------------------


Estrutura
---------


    alertabairro-api/
    ├── pom.xml · Dockerfile · docker-compose.yml
    ├── src/main/java/br/com/fiap/alertabairro/
    │   ├── config/        segurança, OpenAPI, limite de corpo
    │   ├── security/      emissão e leitura do JWT
    │   ├── web/           6 controllers · DTOs · tratador de erros
    │   ├── service/       regras de fluxo e transações
    │   └── repository/    acesso a dados e chamada das rotinas
    ├── database/          migrações, seed, reset, testes  → database/README.md
    └── docs/
        ├── modelo-dados.md   DER · MER · dicionário de dados
        └── superpowers/      design e plano de implementação


Os nomes de código são em inglês, por convenção de mercado. Os nomes de domínio --
tabelas (ocorrencia), rotas (/ocorrencias) e campos JSON (tipo, criadaEm) -- seguem
em português, porque são contrato fechado com o app; @JsonProperty faz a ponte.

Links
-----


Repositório do app e vídeos: links.txt <links.txt>

