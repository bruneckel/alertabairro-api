-- t3_shelter: prova fn_abrigo_mais_proximo e prc_controle_vagas.
--
-- O teste NAO assume a tabela abrigo vazia: a primeira coisa que faz e lotar
-- todos os abrigos pre-existentes, para que a carga inicial da V6 nao
-- interfira nas asercoes. Tudo volta no ROLLBACK.
WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v_a       NUMBER;   -- pertinho, LOTADO
  v_b       NUMBER;   -- medio, com vaga
  v_c       NUMBER;   -- longe, com vaga
  v_achado  NUMBER;
  v_ocupadas NUMBER;
  v_codigo  NUMBER;

  -- ponto de referencia das consultas
  k_lat CONSTANT NUMBER := -23.550000;
  k_lng CONSTANT NUMBER := -46.630000;

  PROCEDURE afirmar(p_ok BOOLEAN, p_msg VARCHAR2) IS
  BEGIN
    IF NOT NVL(p_ok, FALSE) THEN
      RAISE_APPLICATION_ERROR(-20900, 'FALHA: ' || p_msg);
    END IF;
  END;
BEGIN
  -- isola o teste da carga inicial
  UPDATE abrigo SET vagas_ocupadas = capacidade;

  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('T3 Perto Lotado', 'Rua A, 1', -23.550500, -46.630500, 10, 10) RETURNING id INTO v_a;
  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('T3 Medio Com Vaga', 'Rua B, 2', -23.552000, -46.632000, 20, 5) RETURNING id INTO v_b;
  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('T3 Longe Com Vaga', 'Rua C, 3', -23.560000, -46.640000, 30, 0) RETURNING id INTO v_c;

  -- 1. devolve o mais proximo COM VAGA, ignorando o lotado que esta mais perto
  v_achado := fn_abrigo_mais_proximo(k_lat, k_lng);
  afirmar(v_achado = v_b, 'deveria achar o medio com vaga (' || v_b || '), veio ' || NVL(TO_CHAR(v_achado), 'NULL'));

  -- 2. chamavel dentro de SELECT: se a function fizesse DML, daria ORA-14551
  SELECT fn_abrigo_mais_proximo(k_lat, k_lng) INTO v_achado FROM dual;
  afirmar(v_achado = v_b, 'chamada via SELECT deveria dar o mesmo id, veio ' || NVL(TO_CHAR(v_achado), 'NULL'));

  -- 3. todos lotados -> NULL, sem estourar NO_DATA_FOUND
  UPDATE abrigo SET vagas_ocupadas = capacidade WHERE id IN (v_b, v_c);
  v_achado := fn_abrigo_mais_proximo(k_lat, k_lng);
  afirmar(v_achado IS NULL, 'com todos lotados deveria voltar NULL, veio ' || TO_CHAR(v_achado));

  -- 4. entrada e saida de pessoas
  UPDATE abrigo SET vagas_ocupadas = 5 WHERE id = v_b;
  prc_controle_vagas(v_b, 2);
  SELECT vagas_ocupadas INTO v_ocupadas FROM abrigo WHERE id = v_b;
  afirmar(v_ocupadas = 7, 'entrada de 2 deveria levar a 7, veio ' || v_ocupadas);

  prc_controle_vagas(v_b, -1);
  SELECT vagas_ocupadas INTO v_ocupadas FROM abrigo WHERE id = v_b;
  afirmar(v_ocupadas = 6, 'saida de 1 deveria levar a 6, veio ' || v_ocupadas);

  -- 5. estourar a capacidade -> -20002
  v_codigo := 0;
  BEGIN
    prc_controle_vagas(v_b, 100);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -20002, 'estourar a capacidade deveria dar -20002, veio ' || v_codigo);

  -- 6. abrigo inexistente -> -20003
  v_codigo := 0;
  BEGIN
    prc_controle_vagas(-999, 1);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -20003, 'abrigo inexistente deveria dar -20003, veio ' || v_codigo);

  -- 7. saida maior que a ocupacao -> -20004
  v_codigo := 0;
  BEGIN
    prc_controle_vagas(v_b, -99);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -20004, 'saida maior que a ocupacao deveria dar -20004, veio ' || v_codigo);

  -- a ocupacao nao mudou nas tentativas recusadas
  SELECT vagas_ocupadas INTO v_ocupadas FROM abrigo WHERE id = v_b;
  afirmar(v_ocupadas = 6, 'tentativa recusada nao pode alterar a ocupacao, veio ' || v_ocupadas);

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK t3_shelter');
END;
/
EXIT
