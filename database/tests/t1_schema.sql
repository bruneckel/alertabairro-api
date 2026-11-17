-- t1_schema: prova as tabelas, os DEFAULTs, os dominios (CHECK) e o ON DELETE CASCADE.
-- Nao deixa residuo: termina em ROLLBACK.
WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v_qtd        NUMBER;
  v_id_usuario NUMBER;
  v_id_outro   NUMBER;
  v_id_ocor    NUMBER;
  v_status     VARCHAR2(20);
  v_prior      VARCHAR2(10);
  v_relatos    NUMBER;
  v_recusou    BOOLEAN;

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

  -- 4. CHECK de perfil recusa valor fora do dominio
  v_recusou := FALSE;
  BEGIN
    INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
         VALUES ('Sindico', 't1.sindico@alertabairro.test', '$2a$10$hashfalsoparateste', 'Centro', 'sindico');
  EXCEPTION WHEN OTHERS THEN v_recusou := TRUE;
  END;
  afirmar(v_recusou, 'perfil=sindico deveria ser recusado pela CHECK');

  -- 5. CHECK de status recusa maiusculo (quebraria as cores do app)
  v_recusou := FALSE;
  BEGIN
    INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro, status)
         VALUES (v_id_usuario, 'Sem luz', 'Teste maiusculo', -23.5, -46.6, 'Centro', 'ABERTA');
  EXCEPTION WHEN OTHERS THEN v_recusou := TRUE;
  END;
  afirmar(v_recusou, 'status=ABERTA deveria ser recusado pela CHECK');

  -- 6. CHECK do abrigo recusa ocupacao acima da capacidade
  v_recusou := FALSE;
  BEGIN
    INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
         VALUES ('Abrigo Teste', 'Rua Teste, 1', -23.5, -46.6, 10, 11);
  EXCEPTION WHEN OTHERS THEN v_recusou := TRUE;
  END;
  afirmar(v_recusou, 'vagas_ocupadas acima da capacidade deveria ser recusado pela CHECK');

  -- 7. confirmacao: uma por (ocorrencia, usuario) -> ORA-00001 na repetida
  INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario) VALUES (v_id_ocor, v_id_outro);
  v_recusou := FALSE;
  BEGIN
    INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario) VALUES (v_id_ocor, v_id_outro);
  EXCEPTION WHEN DUP_VAL_ON_INDEX THEN v_recusou := TRUE;
  END;
  afirmar(v_recusou, 'confirmacao repetida deveria dar ORA-00001');

  -- 8. ON DELETE CASCADE: apagar a ocorrencia leva as confirmacoes junto
  DELETE FROM ocorrencia WHERE id = v_id_ocor;
  SELECT COUNT(*) INTO v_qtd FROM confirmacao_ocorrencia WHERE id_ocorrencia = v_id_ocor;
  afirmar(v_qtd = 0, 'confirmacoes filhas deveriam sumir com ON DELETE CASCADE, sobraram ' || v_qtd);

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK t1_schema');
END;
/
EXIT
