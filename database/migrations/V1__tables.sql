-- AlertaBairro - V1: tabelas, dominios e relacionamentos.
--
-- Portabilidade (spec 6.1): nada exclusivo do Oracle 23ai e nada que exija DBA.
-- Sem CREATE USER, sem TABLESPACE, sem GRANT: tudo no schema corrente.
-- Sem GENERATED AS IDENTITY (so 12c+) -- a PK e preenchida em V2 por sequence+trigger.
-- Arquivo em SQL puro, sem diretivas do SQL*Plus, para rodar tanto pelo Flyway
-- quanto aberto a mao no SQL Developer.

CREATE TABLE usuario (
  id          NUMBER                              NOT NULL,
  nome        VARCHAR2(100 CHAR)                       NOT NULL,
  email       VARCHAR2(150 CHAR)                       NOT NULL,
  -- bcrypt gera 60 caracteres, e 100 deixa folga para outro algoritmo no futuro.
  senha_hash  VARCHAR2(100 CHAR)                       NOT NULL,
  bairro      VARCHAR2(100 CHAR)                       NOT NULL,
  perfil      VARCHAR2(20 CHAR)                        NOT NULL,
  criado_em   TIMESTAMP DEFAULT SYSTIMESTAMP      NOT NULL,
  CONSTRAINT pk_usuario     PRIMARY KEY (id),
  CONSTRAINT uk_usuario_email UNIQUE (email),
  CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('morador', 'idoso', 'voluntario'))
);

CREATE TABLE abrigo (
  id             NUMBER                           NOT NULL,
  nome           VARCHAR2(100 CHAR)                    NOT NULL,
  endereco       VARCHAR2(200 CHAR)                    NOT NULL,
  latitude       NUMBER(9,6)                      NOT NULL,
  longitude      NUMBER(9,6)                      NOT NULL,
  capacidade     NUMBER(6)                        NOT NULL,
  vagas_ocupadas NUMBER(6) DEFAULT 0              NOT NULL,
  CONSTRAINT pk_abrigo PRIMARY KEY (id),
  CONSTRAINT ck_abrigo_capacidade CHECK (capacidade > 0),
  CONSTRAINT ck_abrigo_latitude   CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_abrigo_longitude  CHECK (longitude BETWEEN -180 AND 180),
  -- Rede de seguranca para escrita que nao passe por prc_controle_vagas.
  -- A procedure valida antes e levanta -20002/-20004, para o app receber 422
  -- com mensagem legivel em vez de 500.
  CONSTRAINT ck_abrigo_vagas CHECK (vagas_ocupadas BETWEEN 0 AND capacidade)
);

CREATE TABLE ocorrencia (
  id         NUMBER                               NOT NULL,
  id_usuario NUMBER                               NOT NULL,
  tipo       VARCHAR2(50 CHAR)                         NOT NULL,
  descricao  VARCHAR2(500 CHAR)                        NOT NULL,
  latitude   NUMBER(9,6)                          NOT NULL,
  longitude  NUMBER(9,6)                          NOT NULL,
  status     VARCHAR2(20 CHAR)  DEFAULT 'aberta'       NOT NULL,
  prioridade VARCHAR2(10 CHAR)  DEFAULT 'normal'       NOT NULL,
  relatos    NUMBER(6)     DEFAULT 1              NOT NULL,
  bairro     VARCHAR2(100 CHAR),
  -- Caminho da foto no aparelho. Limite conhecido: a API guarda o texto,
  -- nao recebe o arquivo (spec 7.5).
  foto_uri   VARCHAR2(500 CHAR),
  criada_em  TIMESTAMP DEFAULT SYSTIMESTAMP       NOT NULL,
  CONSTRAINT pk_ocorrencia PRIMARY KEY (id),
  CONSTRAINT fk_ocorrencia_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id),
  -- Minusculo e sem acento: estes valores atravessam ate o switch de cores do app.
  CONSTRAINT ck_ocorrencia_status     CHECK (status IN ('aberta', 'em_atendimento', 'resolvida')),
  CONSTRAINT ck_ocorrencia_prioridade CHECK (prioridade IN ('normal', 'alta', 'critica')),
  CONSTRAINT ck_ocorrencia_relatos    CHECK (relatos >= 1),
  CONSTRAINT ck_ocorrencia_latitude   CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_ocorrencia_longitude  CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE pedido_ajuda (
  id         NUMBER                               NOT NULL,
  id_usuario NUMBER                               NOT NULL,
  modo       VARCHAR2(10 CHAR)                         NOT NULL,
  categoria  VARCHAR2(50 CHAR)                         NOT NULL,
  descricao  VARCHAR2(500 CHAR)                        NOT NULL,
  urgencia   VARCHAR2(10 CHAR)                         NOT NULL,
  status     VARCHAR2(20 CHAR) DEFAULT 'aguardando'    NOT NULL,
  latitude   NUMBER(9,6),
  longitude  NUMBER(9,6),
  criado_em  TIMESTAMP DEFAULT SYSTIMESTAMP       NOT NULL,
  CONSTRAINT pk_pedido_ajuda PRIMARY KEY (id),
  CONSTRAINT fk_pedido_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id),
  CONSTRAINT ck_pedido_modo     CHECK (modo IN ('pedido', 'oferta')),
  CONSTRAINT ck_pedido_urgencia CHECK (urgencia IN ('baixa', 'media', 'alta')),
  -- 'resolvido' e o que alimenta o Historico. O app v1 so conhece dois valores,
  -- entao a normalizacao fica no api.ts (spec 7.3) e o banco guarda a verdade completa.
  CONSTRAINT ck_pedido_status   CHECK (status IN ('aguardando', 'atendido', 'resolvido')),
  -- NULL e permitido: a coordenada do solicitante e opcional.
  CONSTRAINT ck_pedido_latitude  CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_pedido_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE checkin (
  id         NUMBER                               NOT NULL,
  id_usuario NUMBER                               NOT NULL,
  status     VARCHAR2(20 CHAR)                         NOT NULL,
  latitude   NUMBER(9,6),
  longitude  NUMBER(9,6),
  criado_em  TIMESTAMP DEFAULT SYSTIMESTAMP       NOT NULL,
  CONSTRAINT pk_checkin PRIMARY KEY (id),
  CONSTRAINT fk_checkin_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id),
  CONSTRAINT ck_checkin_status    CHECK (status IN ('estou_bem', 'preciso_de_ajuda')),
  CONSTRAINT ck_checkin_latitude  CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_checkin_longitude CHECK (longitude BETWEEN -180 AND 180)
);

-- "Tambem vejo isso aqui": uma confirmacao por usuario por ocorrencia.
-- PK composta, sem id surrogate -- por isso nao leva sequence em V2.
CREATE TABLE confirmacao_ocorrencia (
  id_ocorrencia NUMBER                            NOT NULL,
  id_usuario    NUMBER                            NOT NULL,
  criada_em     TIMESTAMP DEFAULT SYSTIMESTAMP    NOT NULL,
  CONSTRAINT pk_confirmacao PRIMARY KEY (id_ocorrencia, id_usuario),
  -- CASCADE: excluir a ocorrencia (so o autor pode) leva as confirmacoes junto.
  CONSTRAINT fk_confirmacao_ocorrencia FOREIGN KEY (id_ocorrencia) REFERENCES ocorrencia (id) ON DELETE CASCADE,
  CONSTRAINT fk_confirmacao_usuario    FOREIGN KEY (id_usuario)    REFERENCES usuario (id)
);

-- Unicidade de e-mail ignorando a caixa. A constraint uk_usuario_email ja
-- impede o e-mail identico, mas nao impede 'Maria@x' ao lado de 'maria@x':
-- a pessoa se cadastraria com um, tentaria entrar com o outro e receberia
-- "credenciais invalidas" sem entender por que. A API tambem normaliza para
-- minusculo, e este indice e a rede para escrita que nao passe por ela.
CREATE UNIQUE INDEX uk_usuario_email_lower ON usuario (LOWER(email));

-- Indices de FK: sem eles, um DELETE no pai pega lock de tabela no filho.
CREATE INDEX ix_ocorrencia_usuario   ON ocorrencia (id_usuario);
CREATE INDEX ix_pedido_usuario       ON pedido_ajuda (id_usuario);
CREATE INDEX ix_checkin_usuario      ON checkin (id_usuario);
CREATE INDEX ix_confirmacao_usuario  ON confirmacao_ocorrencia (id_usuario);

-- Indices que sustentam a paginacao documentada (spec 9.5): a ordenacao das
-- listagens le o indice em vez de ordenar o conjunto inteiro.
CREATE INDEX ix_ocorrencia_recentes ON ocorrencia (criada_em DESC, id DESC);
CREATE INDEX ix_pedido_recentes     ON pedido_ajuda (status, criado_em DESC);
CREATE INDEX ix_checkin_recentes    ON checkin (id_usuario, criado_em DESC);
