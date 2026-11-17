-- AlertaBairro - V2: geracao de chave primaria.
--
-- Por que sequence + trigger e nao GENERATED AS IDENTITY:
-- IDENTITY exige Oracle 12c+. A versao do Oracle da FIAP e desconhecida, e
-- sequence+trigger funciona de 11g a 23ai. Custo: 5 sequences e 5 triggers.
--
-- Sao 5, nao 6: confirmacao_ocorrencia tem PK composta (id_ocorrencia, id_usuario)
-- e nao precisa de id surrogate.
--
-- O trigger so age quando o INSERT nao informa id, entao carga com id explicito
-- continua possivel.

CREATE SEQUENCE seq_usuario      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_abrigo       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_ocorrencia   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_pedido_ajuda START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_checkin      START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE OR REPLACE TRIGGER trg_usuario_bi
BEFORE INSERT ON usuario FOR EACH ROW WHEN (NEW.id IS NULL)
BEGIN
  :NEW.id := seq_usuario.NEXTVAL;
END;
/

CREATE OR REPLACE TRIGGER trg_abrigo_bi
BEFORE INSERT ON abrigo FOR EACH ROW WHEN (NEW.id IS NULL)
BEGIN
  :NEW.id := seq_abrigo.NEXTVAL;
END;
/

CREATE OR REPLACE TRIGGER trg_ocorrencia_bi
BEFORE INSERT ON ocorrencia FOR EACH ROW WHEN (NEW.id IS NULL)
BEGIN
  :NEW.id := seq_ocorrencia.NEXTVAL;
END;
/

CREATE OR REPLACE TRIGGER trg_pedido_ajuda_bi
BEFORE INSERT ON pedido_ajuda FOR EACH ROW WHEN (NEW.id IS NULL)
BEGIN
  :NEW.id := seq_pedido_ajuda.NEXTVAL;
END;
/

CREATE OR REPLACE TRIGGER trg_checkin_bi
BEFORE INSERT ON checkin FOR EACH ROW WHEN (NEW.id IS NULL)
BEGIN
  :NEW.id := seq_checkin.NEXTVAL;
END;
/
