-- t1_schema: prova tabelas, DEFAULTs, dominios (CHECK) e ON DELETE CASCADE.
--
-- Os casos negativos conferem o SQLCODE, nao apenas "deu algum erro": uma
-- asercao que aceita qualquer excecao passaria tambem por um NOT NULL novo,
-- uma coluna renomeada ou um e-mail duplicado -- isto e, pelo motivo errado.
--
-- Nao deixa residuo: termina em ROLLBACK, e as diretivas WHENEVER tambem
-- desfazem em caso de falha.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
  v_qtd        NUMBER;
  v_id_usuario NUMBER;
  v_id_outro   NUMBER;
  v_id_ocor    NUMBER;
  v_id_abrigo  NUMBER;
  v_status     VARCHAR2(20);
  v_prior      VARCHAR2(10);
  v_relatos    NUMBER;
  v_valor      NUMBER;
  v_codigo     NUMBER;
  v_texto      VARCHAR2(4000);
  v_chars      NUMBER;

  PROCEDURE afirmar(p_ok BOOLEAN, p_msg VARCHAR2) IS
  BEGIN
    IF NOT NVL(p_ok, FALSE) THEN
      RAISE_APPLICATION_ERROR(-20900, 'FALHA: ' || p_msg);
    END IF;
  END;
BEGIN
  -- 1. as 6 tabelas existem
  SELECT COUNT(*) INTO v_qtd FROM user_tables
   WHERE table_name IN ('USUARIO','OCORRENCIA','ABRIGO','PEDIDO_AJUDA','CHECKIN','CONFIRMACAO_OCORRENCIA');
  afirmar(v_qtd = 6, 'esperava as 6 tabelas, achei ' || v_qtd);

  -- 2. id gerado pelo banco (sequence + trigger), sem informar id no INSERT
  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES ('Teste Um', 't1.um@alertabairro.test', '$2a$10$hashfalsoparateste', 'Centro', 'morador')
    RETURNING id INTO v_id_usuario;
  afirmar(v_id_usuario > 0, 'usuario.id deveria ser gerado pelo banco, veio ' || NVL(TO_CHAR(v_id_usuario), 'NULL'));

  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES ('Teste Dois', 't1.dois@alertabairro.test', '$2a$10$hashfalsoparateste', 'Centro', 'voluntario')
    RETURNING id INTO v_id_outro;
  afirmar(v_id_outro <> v_id_usuario, 'dois usuarios nao podem receber o mesmo id');

  -- 3. DEFAULTs da ocorrencia
  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro)
       VALUES (v_id_usuario, 'Alagamento', 'Teste de default', -23.548900, -46.638800, 'Centro')
    RETURNING id INTO v_id_ocor;
  SELECT status, prioridade, relatos INTO v_status, v_prior, v_relatos FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_status  = 'aberta', 'status default deveria ser aberta, veio ' || v_status);
  afirmar(v_prior   = 'normal', 'prioridade default deveria ser normal, veio ' || v_prior);
  afirmar(v_relatos = 1,        'relatos default deveria ser 1, veio ' || v_relatos);

  -- 4. CHECK de perfil recusa valor fora do dominio (ORA-02290)
  v_codigo := 0;
  BEGIN
    INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
         VALUES ('Sindico', 't1.sindico@alertabairro.test', '$2a$10$hashfalso', 'Centro', 'sindico');
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -2290, 'perfil=sindico deveria violar CHECK (-2290), veio ' || v_codigo);

  -- 5. CHECK de status recusa maiusculo (quebraria as cores do app)
  v_codigo := 0;
  BEGIN
    INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro, status)
         VALUES (v_id_usuario, 'Sem luz', 'Teste maiusculo', -23.5, -46.6, 'Centro', 'ABERTA');
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -2290, 'status=ABERTA deveria violar CHECK (-2290), veio ' || v_codigo);

  -- 6. CHECK do abrigo recusa ocupacao acima da capacidade
  v_codigo := 0;
  BEGIN
    INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
         VALUES ('Abrigo Teste', 'Rua Teste, 1', -23.5, -46.6, 10, 11);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -2290, 'ocupacao acima da capacidade deveria violar CHECK (-2290), veio ' || v_codigo);

  -- 7. confirmacao: uma por (ocorrencia, usuario) -> ORA-00001 na repetida
  INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario) VALUES (v_id_ocor, v_id_outro);
  v_codigo := 0;
  BEGIN
    INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario) VALUES (v_id_ocor, v_id_outro);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -1, 'confirmacao repetida deveria dar ORA-00001, veio ' || v_codigo);

  -- 8. ON DELETE CASCADE: apagar a ocorrencia leva as confirmacoes junto
  DELETE FROM ocorrencia WHERE id = v_id_ocor;
  SELECT COUNT(*) INTO v_qtd FROM confirmacao_ocorrencia WHERE id_ocorrencia = v_id_ocor;
  afirmar(v_qtd = 0, 'confirmacoes filhas deveriam sumir com ON DELETE CASCADE, sobraram ' || v_qtd);

  -- ---------------------------------------------------------------------
  -- 9. VARCHAR2 em CARACTERES, nao em bytes.
  --    O banco e AL32UTF8 e NLS_LENGTH_SEMANTICS e BYTE por padrao, entao
  --    VARCHAR2(500) comporta apenas 500 BYTES: uma descricao de 300
  --    caracteres acentuados ocupa 600 e seria recusada com ORA-12899.
  --    Texto em portugues com acento e entrada normal, nao caso extremo.
  -- ---------------------------------------------------------------------
  v_texto := RPAD('ç', 300, 'ç');
  SELECT LENGTH(v_texto) INTO v_chars FROM dual;
  afirmar(v_chars = 300, 'o proprio texto de teste deveria ter 300 caracteres, tem ' || v_chars);

  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro)
       VALUES (v_id_usuario, 'Alagamento', v_texto, -23.5, -46.6, 'Centro')
    RETURNING id INTO v_id_ocor;
  SELECT LENGTH(descricao) INTO v_chars FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_chars = 300, 'descricao acentuada de 300 caracteres deveria caber, voltou com ' || v_chars);

  -- nome de usuario tambem: 60 caracteres acentuados = 120 bytes
  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES (RPAD('á', 60, 'á'), 't1.acentos@alertabairro.test', '$2a$10$hashfalso', 'Centro', 'morador');

  -- ---------------------------------------------------------------------
  -- 10. E-mail unico ignorando caixa.
  --     Sem isto, "Maria@x" e "maria@x" convivem: a pessoa se cadastra com
  --     um, tenta entrar com o outro e recebe "credenciais invalidas" sem
  --     entender por que.
  -- ---------------------------------------------------------------------
  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES ('Caixa Baixa', 't1.caixa@alertabairro.test', '$2a$10$hashfalso', 'Centro', 'morador');
  v_codigo := 0;
  BEGIN
    INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
         VALUES ('Caixa Alta', 'T1.CAIXA@alertabairro.test', '$2a$10$hashfalso', 'Centro', 'morador');
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -1, 'o mesmo e-mail em outra caixa deveria dar ORA-00001, veio ' || v_codigo);

  -- ---------------------------------------------------------------------
  -- 11. Coordenada fora de faixa recusada pelo banco.
  --     A spec 7.2 promete que o banco recusa valor fora do dominio "mesmo
  --     que alguem insira por fora da API". Valia para os dominios de texto
  --     e nao valia para os numericos.
  -- ---------------------------------------------------------------------
  v_codigo := 0;
  BEGIN
    INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro)
         VALUES (v_id_usuario, 'Sem luz', 'Latitude impossivel', 91, -46.6, 'Centro');
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -2290, 'latitude 91 deveria violar CHECK (-2290), veio ' || v_codigo);

  v_codigo := 0;
  BEGIN
    INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
         VALUES ('Longitude impossivel', 'Rua X', -23.5, -181, 10, 0);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -2290, 'longitude -181 deveria violar CHECK (-2290), veio ' || v_codigo);

  -- ---------------------------------------------------------------------
  -- 12. Contadores de PESSOAS sao inteiros.
  --     Sem escala definida, vagas_ocupadas aceitava 68.5 -- meia pessoa
  --     abrigada. Com NUMBER de escala 0 o banco nao tem onde guardar a
  --     fracao.
  -- ---------------------------------------------------------------------
  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('Abrigo Fracao', 'Rua Y', -23.5, -46.6, 10, 0) RETURNING id INTO v_id_abrigo;
  UPDATE abrigo SET vagas_ocupadas = 5.5 WHERE id = v_id_abrigo;
  SELECT vagas_ocupadas INTO v_valor FROM abrigo WHERE id = v_id_abrigo;
  afirmar(v_valor = TRUNC(v_valor), 'vagas_ocupadas nao deveria guardar fracao, guardou ' || v_valor);

  UPDATE ocorrencia SET relatos = 2.5 WHERE id = v_id_ocor;
  SELECT relatos INTO v_valor FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_valor = TRUNC(v_valor), 'relatos nao deveria guardar fracao, guardou ' || v_valor);

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK t1_schema');
END;
/
EXIT ROLLBACK
