# Decisões de implementação

Registro das decisões tomadas durante a construção, das correções vindas das duas
revisões independentes, e do que ficou deliberadamente de fora.

Existe porque as mensagens de commit são de uma linha: o *porquê* de cada escolha não
cabe lá, e sem este arquivo ele se perderia.

## Decisões tomadas durante a implementação

1. | T2 `ck_abrigo_vagas` → T4 `prc_controle_vagas` | CHECK (vagas BETWEEN 0 AND capacidade) poderia disparar ORA-02290 ANTES do -20002 da procedure | CONFLITO — ver Ruling 1 |

2. Ruling 1: `prc_controle_vagas` valida os limites e levanta -20002/-20004 ANTES de executar o UPDATE, de modo que a CHECK constraint nunca é alcançada nesse caminho — a constraint permanece como rede de segurança para escrita fora da API. — Motivo: a spec exige que a mensagem chegue ao usuário como HTTP 422, e ORA-02290 viraria 500. — Custo se errado: um caminho de escrita que burle a procedure recebe 500 em vez de 422.

3. Ruling 2: acrescentei `database/apply.sh`, que nao esta na lista de Files do plano. — Motivo: o Flyway so chega na Tarefa 7, e as Tarefas 2 a 6 precisam aplicar as migracoes repetidamente; o script tambem e o caminho "rodar a mao" que a spec quer documentado para a FIAP. — Custo se errado: um script de desenvolvimento a mais, que o Flyway depois supera.

4. Ruling 3: os arquivos de migracao nao levam diretivas do SQL*Plus (`WHENEVER SQLERROR EXIT FAILURE`); quem as injeta e o `apply.sh`, na sessao. — Motivo: o Flyway Community nao interpreta comandos do SQL*Plus, e o objetivo 5 da spec exige que o MESMO arquivo rode pelo Flyway e aberto a mao no SQL Developer. — Custo se errado: rodar um arquivo a mao sem o wrapper falha silenciosamente em vez de abortar.

5. Ruling 4: criei indices que o plano nao especifica (4 de FK + 3 de ordenacao). — Motivo: sem indice na FK, um DELETE no pai pega lock de tabela no filho; os de ordenacao sustentam a afirmacao de eficiencia da spec 9.5. A spec 12 deixa os NOMES de indice ao builder, o que pressupoe que existam. — Custo se errado: 7 indices a mais em tabelas pequenas, custo de escrita desprezivel nesta escala.

6. Ruling 5 (a pedido do usuario, mid-task): repositorio renomeado para `alertabairro-api`, pastas em ingles, e o projeto Maven movido de `api/` para a raiz. Nomes de dominio permanecem em portugues por R8. — Custo se errado: o plano e a spec ja foram reescritos para os novos caminhos; reverter custaria o mesmo trabalho de novo.

7. Ruling 6: acrescentei `restart: unless-stopped` ao servico oracle, que o plano nao pede. — Motivo: o container morreu com SIGTERM no meio da tarefa junto a uma atualizacao do Docker Desktop (29.8.2 -> 29.9.3), e o ambiente ficou parado exigindo up manual; a politica tambem protege a gravacao do video. — Custo se errado: o banco volta sozinho mesmo quando a intencao era deixa-lo parado (contornavel com `docker compose stop`).

8. Ruling 7: o teste t3_shelter comeca lotando todos os abrigos pre-existentes (`UPDATE abrigo SET vagas_ocupadas = capacidade`) antes das asercoes. — Motivo: o plano escreve o teste da T4 antes da carga inicial da T6, e sem esse isolamento a asercao "todos lotados -> NULL" passaria agora e quebraria na Tarefa 6. — Custo se errado: nenhum; o ROLLBACK desfaz, e o teste fica independente de dados de carga.

9. Ruling 8: rodei o fix pass ANTES de concluir a Tarefa 7, fora da ordem do plano. — Motivo: quatro achados (I1, I2, M8, M13) vivem no V1, e o loop ainda era `apply.sh --reset`; depois de o Flyway rastrear o V1, cada um viraria uma migracao nova e o argumento "um script, dois Oracles" ficaria mais longo. Foi tambem a recomendacao numero 1 do revisor. — Custo se errado: a Tarefa 7 terminou alguns minutos depois do previsto.

10. Ruling 9 (declined to judge, Tarefas 7-18): o revisor nao julgou nada fora da Fase 1, e eu aceito o recorte -- a Fase 2 tera sua propria revisao. Das 6 recomendacoes dele para a Fase 2, adoto 4 como restricao de implementacao: paginacao com ROWNUM e nao OFFSET/FETCH (senao a promessa de portabilidade do README fica falsa); incremento relativo `SET relatos = relatos + 1` na mesma transacao do INSERT da confirmacao; politica explicita para erro Oracle fora da faixa -20xxx (ORA-00001->409, ORA-12899/01400/02290/01438->400, resto->500); e baseline/reset documentado antes do primeiro `docker compose up --build`. — Custo se errado: a Fase 2 repete defeitos que a Fase 1 ja pagou para descobrir.

11. Ruling 10 (declined to judge): aceito como corretos e mantidos sem mudanca — `restart: unless-stopped` (ja era Ruling 6), a existencia do apply.sh (Ruling 2, e o plano a lista na File Structure), os 7 indices extras (Ruling 4, spec 12 entrega indices ao builder), nomes de dominio em portugues (R8), README.md + README.txt (spec 13.4), seed e DROPs fora das migracoes versionadas (spec 13.2/13.3), hash bcrypt de senha de demonstracao documentada (spec 13.3 pede exatamente isso), o isolamento de t3/t4 (Ruling 7), e ocorrencia.bairro anulavel enquanto usuario.bairro e NOT NULL (desnormalizacao intencional da spec). — Custo se errado: cada um tem a sua justificativa registrada e e reversivel isoladamente.

12. Ruling 11 (declined to judge): senhas default nos fallbacks do Compose e a porta 1521 publicada em todas as interfaces ficam como estao. — Motivo: ambiente local de desenvolvimento, e endurecimento de operacao e nao-objetivo explicito da spec secao 2. — Custo se errado: quem rodar isto numa rede nao confiavel expoe um Oracle com senha conhecida; o README diz que o ambiente e local.

13. Ruling 12: removi do HelpListIT o teste que dependia de POST /checkins e o recriei no CheckinIT. — Motivo: /checkins e da Tarefa 15, e o plano coloca essa asercao justamente la ("prova a ligacao com a Tarefa 13"); manter no 13 inverteria a dependencia entre tarefas. — Custo se errado: nenhum, a cobertura e a mesma e esta na tarefa que possui o endpoint.

14. Ruling 13: Spring Boot 3.5.16 em vez do 4.1.2, que e o estavel atual do start.spring.io. — Motivo: o Boot 4 renomeou os starters (spring-boot-starter-webmvc em vez de -web) e meu conhecimento tem corte anterior ao lancamento dele; com entrega em 19/11, a geracao 3.5 (ainda suportada) troca novidade por previsibilidade, e a spec ja previa 3.x. — Custo se errado: a entrega usa uma geracao anterior do framework, o que e normal no mercado mas e uma pergunta possivel na banca.

15. Ruling 14 (a pedido do usuario, mid-task): todo o codigo Java em ingles -- pacotes, classes e campos -- com @JsonProperty preservando o contrato de fio em portugues (rotas, colunas, campos JSON, claim "perfil"). Comentarios reduzidos ao que o codigo nao diz sozinho. — Custo se errado: ~40 anotacoes de mapeamento, e comparar DTO com o front exige traducao mental.

16. Ruling 15: fiz a Tarefa 17 antes da 16 e da 18, fora da ordem do plano. — Motivo: a 16 reescreve o api.ts e depende das conclusoes da revisao da Fase 2, que esta em andamento conferindo exatamente o contrato JSON; a 18 altera pom.xml e as classes de teste que o revisor esta executando. A 17 so cria arquivos novos de documentacao, entao nao interfere. — Custo se errado: nenhum; nenhuma das tres depende das outras no sentido inverso.

17. Ruling 16: README.txt e GERADO do README.md por script (/tmp/md2txt.py, logica embutida no commit), em vez de mantido a mao. — Motivo: o enunciado pede README.txt e o GitHub renderiza .md; duas copias manuais divergem na primeira correcao. — Custo se errado: o .txt tem tabelas largas e perde os diagramas, que ficam indicados como "ver a versao em Markdown".

18. Ruling 17: convencao de commit corrigida a pedido do usuario, no meio do fix pass -- uma linha, em ingles, com prefixo convencional, sem trailer de co-autoria. Os 21 commits anteriores violam as duas regras, mas JA ESTAO PUBLICADOS em github.com/bruneckel/alertabairro-api (origin/main = ef455a3), entao reescrever exigiria force-push numa branch publicada: parei e levei a decisao ao usuario. Preferencia salva na memoria do projeto. — Custo se errado: o historico publicado fica inconsistente com a convencao ate o usuario decidir.

19. Ruling 18 (declined to judge): aceito o recorte do revisor e as suas nao-decisoes. Mantidos sem mudanca: foto_uri guardando caminho do aparelho (spec 7.5), coordenada exata das OCORRENCIAS ser publica (e um mapa publico de incidentes, ao contrario da rede de ajuda), /v3/api-docs e /swagger-ui publicos (spec 9.2), tamanho do pool e fetch-size sem teste de carga. — Custo se errado: cada um tem justificativa propria e e reversivel isoladamente.

20. Ruling 19: nao corrigi o erro de tipo pre-existente em Ajuda.tsx:77 (ListEmptyComponent recebendo null). Provei que ele existe com o api.ts ORIGINAL, entao nao e meu. — Motivo: o escopo que o usuario definiu foi "so o api.ts"; alargar por conta propria seria decidir por ele. — Custo se errado: `tsc --noEmit` do projeto segue com 1 erro, que um avaliador pode notar. A correcao e trocar null por undefined.

21. Ruling 20: reescrevi o historico dos 21 commits publicados e fiz force-push, com autorizacao explicita do usuario. Conferi antes que a arvore era byte a byte identica ao backup. Backup local em `backup-antes-da-reescrita`. — Custo se errado: quem ja tivesse clonado precisa de reset --hard; o usuario avaliou e aceitou.

## Correções vindas das revisões independentes

Duas revisões de contexto novo leram o código contra o sistema rodando. Cada correção
abaixo teve um teste que falhou primeiro e passou depois.

- fixed I1 (VARCHAR2 em bytes recusava portugues acentuado normal: 300 caracteres = 600 bytes -> ORA-12899 -> HTTP 500) — t1_schema secao 9 RED(ORA-12899)->GREEN, 19 colunas para VARCHAR2(n CHAR), suite 7/7.
- fixed I2 (e-mail unico sensivel a caixa: 'Maria@x' convivia com 'maria@x', e a pessoa nao conseguiria entrar) — t1_schema secao 10 RED->GREEN, indice unico funcional em LOWER(email), suite 7/7.
- fixed I3 (nada testava o FOR UPDATE, que e o argumento central do video) — novo t6_lock.sh com duas sessoes; PROVADO discriminante: removi o FOR UPDATE e o teste falhou com o diagnostico de lost update, restaurei e passou. suite 7/7.
- fixed I4 (o runner anunciava "passou" e saia 0 para arquivo de teste inexistente -- era o mecanismo de evidencia de TODAS as tarefas) — RED: `run_tests.sh t9_nao_existe.sql` saia 0; GREEN: falha. Agora exige codigo 0 E a linha "OK <nome>". suite 7/7.
- fixed I5 (t3 nao distinguia Haversine de euclidiana em graus: os abrigos estavam na mesma diagonal) — t3_shelter secao 8 com par leste/norte; PROVADO discriminante: zerei o peso cos(latitude) e o teste falhou, restaurei e passou. suite 7/7.
- fixed I6 (WHENEVER ... EXIT FAILURE tem COMMIT como padrao: seed_demo.sql, sem diretiva alguma, deixava seed PARCIAL COMMITADO saindo 0) — diretivas `ROLLBACK` nos 6 testes, em apply.sh e em seed_demo.sql; README passou a explicar por que a garantia vale (bloco anonimo unico + diretiva), em vez de afirma-la. suite 7/7.
- fixed M14 promovido (reset_environment nao derrubava as 5 rotinas standalone) — era a causa real do `Found non-empty schema(s) but no schema history table` que travou a Tarefa 7. suite 7/7.
- fixed M8 promovido (sem faixa de coordenada e sem escala nos contadores: latitude 91 aceita, vagas_ocupadas 68.5 aceita) — t1_schema secoes 11 e 12 RED->GREEN, 8 CHECKs de faixa e NUMBER(6) nos contadores. suite 7/7.
- fixed M15 junto de I1/I2 (tres asercoes negativas usavam WHEN OTHERS e passariam por qualquer erro) — passaram a conferir SQLCODE = -2290 / -1. Minor, mas entrou porque eu estava reescrevendo exatamente essas linhas e sem isso o RED->GREEN delas nao significaria nada.
- fixed M20 (texto do plano dizia 11 colunas em vw_indicadores; sao 12, e a implementacao estava certa) — corrigido no plano, codigo intocado.
- fixed C1 (a coordenada de quem pede ajuda era recuperavel por trilateracao com 0,2 m de erro: o cliente escolhe o ponto de referencia e a API respondia a distancia exata) — novo HelpPrivacyIT. RED: 20 sondas vizinhas produziram 20 respostas distintas. GREEN: no maximo 3. CoarseLocation arredonda AS DUAS pontas numa grade de 0,002 grau (~200 m) antes de medir, e a coordenada precisa nunca chega ao banco. Descobri no RED que o ataque so existe abaixo de 1 km, onde o formato sai em metros inteiros -- sondar de longe nao provava nada, e meu primeiro teste passou por isso. suite 94/94.
- fixed C2 (PATCH /ocorrencias/{id} com corpo {} dava 500: @Pattern deixa null passar e o switch estourava NullPointerException) — ValidationIT.patchOcorrenciaSemAcao RED(500)->GREEN(400). @NotBlank nas duas acoes mais `case null` explicito. suite 94/94.
- fixed I1 (401 de token rejeitado vinha SEM CORPO e vazava o motivo em WWW-Authenticate: o entry point customizado so era usado quando NENHUM token era apresentado) — ApiContractIT.erroDeTokenTemCorpo RED->GREEN. Entry point unico tambem no oauth2ResourceServer. Era o 401 mais provavel na vida real, a cada 8 horas. suite 94/94.
- fixed I2 (o limite de 100 KB era furado por Transfer-Encoding: chunked, em rota publica, por chamador anonimo: getContentLengthLong devolve -1) — novo BodyLimitIT com HTTP real, porque o MockMvc sempre define Content-Length e nunca reproduziria o furo. RED->GREEN. O stream agora conta bytes conforme sao lidos. suite 94/94.
- fixed I3 (nenhuma das 16 operacoes do OpenAPI exigia o esquema JWT, entao o Authorize do Swagger nao anexava token e todo try-it-out dava 401; e AuthenticatedUser era publicado como parametro de query obrigatorio) — ApiContractIT RED->GREEN. Era exatamente o mecanismo que a spec usa para justificar as 7 rotas que o app nao usa. suite 94/94.
- fixed I4 (GET e POST /ajuda aceitavam coordenada fora de faixa e produziam "12737,3 km"; /abrigos/proximo rejeitava a mesma entrada) — HelpPrivacyIT.coordenadaInvalidaNaConsulta RED->GREEN. Validacao movida para Coordinate.requireValid, compartilhada. suite 94/94.
- fixed I5 ({"quantidade":1.5} era truncado para 1 e aplicado, com o plano exigindo 400) — ValidationIT.quantidadeFracionaria RED(200)->GREEN(400), accept-float-as-int desligado. suite 94/94.
- fixed I6 (tres campos com @Pattern sozinho deixavam null passar, e o 400 vinha do Oracle ORA-01400 -- o oposto da spec 9.3, que diz para recusar antes de ocupar conexao de banco; na rota publica de cadastro ainda pagava um bcrypt inteiro antes) — ValidationIT.dominioAusenteRecusadoNaWeb e cadastroSemPerfil RED->GREEN. suite 94/94.
- fixed M3 parcial (a coordenada guardada no pedido vinha do ultimo check-in) — agora e arredondada antes de gravar, e os dois argumentos mortos de HelpService.create foram removidos. Tambem corrigido o acento de "distância desconhecida".
- fixed M12 parcial (asercoes que passariam com implementacao quebrada) — is(not(401)) virou isOk() em AuthControllerIT, e ordenacaoPorStatus passava em lista vazia.
- fixed I7 via Tarefa 18 (a suite de integracao escrevia no mesmo Oracle do app e do video: 848 usuarios, 839 de teste, e 255 ocorrencias) — RED: `docker compose down` + `mvnw verify` dava Connection refused. GREEN: Testcontainers sobe um Oracle descartavel, o Flyway aplica as migracoes la dentro, 94/94 sem banco externo. `mvnw test` continua passando sem Docker.

## Deixado de fora, conscientemente

Nenhum destes afeta caminho que o aplicativo use.

- minor (deferred): M7 — fn_abrigo_mais_proximo(NULL, NULL) devolve o primeiro abrigo em vez de NULL. A API valida coordenada antes (Tarefa 12), mas "abrigo mais proximo" e resposta de seguranca.
- minor (deferred): M9 — o escalonamento dispara de novo se relatos cruzar 3 duas vezes (3->2->3). Nenhuma rota planejada decrementa relatos.
- minor (deferred): M10 — o trigger usa :OLD.prioridade, entao sobrescreve uma prioridade explicita escrita no mesmo UPDATE. Nenhuma rota planejada faz isso.
- minor (deferred): M11 — o README promete que a regra vale "para qualquer caminho, inclusive carga manual", mas o trigger e BEFORE UPDATE OF relatos e nao cobre INSERT.
- minor (deferred): M12 — criado_em e TIMESTAMP sem fuso, entao as janelas de 7 dias e 24h deslocam se a sessao estiver em outro fuso que o servidor. O Compose fixa os dois em America/Sao_Paulo; so morde no caminho FIAP.
- minor (deferred): M13 — 39 constraints NOT NULL inline ficam com nome gerado pelo Oracle (SYS_C00...), contra a regra global do plano. Nomear as 39 e ruido para o prazo; alternativa e estreitar a regra para PK/FK/UK/CHECK.
- minor (deferred): M16 — os -20005 de p_quantidade NULL/0 e p_dias NULL/<1 funcionam mas nenhum teste os fixa.
- minor (deferred): M17 — 9 das 12 colunas de vw_indicadores nao sao asseveradas.
- minor (deferred): M18 — COMMIT explicito dentro de V6 impede o Flyway de desfazer a migracao como unidade. E necessario para o caminho "rodar a mao".
- minor (deferred): M1 — IllegalArgumentException devolve a mensagem crua, entao um token sem sub da 400 "Cannot parse null string". Nao alcancavel sem o segredo.
- minor (deferred): M2 — 403 por falta de autoria diz "nao permitida para o seu perfil", perdendo a mensagem real do servico.
- minor (deferred): M4 — OffsetDateTime depende de o fuso da JVM ser igual ao do banco; o Compose fixa os dois. E a M12 da Fase 1 chegando ao Java.
- minor (deferred): M5 — /actuator/health devolve detalhes (caminho e espaco em disco) a qualquer usuario autenticado.
- minor (deferred): M6 — SELECT * nos envelopes externos da paginacao, embora a projecao interna seja explicita.
- minor (deferred): M7 — Pagination.lastRow() pode transbordar int; mascarado porque a pagina vazia e a resposta certa.
- minor (deferred): M8 — POST /ajuda/encerrar-antigos sem corpo da 400, enquanto o plano diz que o padrao 7 se aplica.
- minor (deferred): M9 — com dois campos invalidos, a mensagem escolhida e nao-deterministica.
- minor (deferred): M10/M11 — comentario e texto do plano desatualizados em pontos menores.
- minor (deferred): M12 restante — IndicatorIT nao exercita o ramo de zero abrigos, e CheckinIT nao distingue 400 da validacao de 400 do banco (este ultimo foi resolvido por tabela em ValidationIT).
