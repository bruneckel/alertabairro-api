-- t2_occurrence: prova prc_registrar_ocorrencia e o trigger de escalonamento.
-- Termina em ROLLBACK, sem deixar residuo.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
  v_id_usuario NUMBER;
  v_id_ocor    NUMBER;
  v_id_luz     NUMBER;
  v_id_resolv  NUMBER;
  v_status     VARCHAR2(20);
  v_prior      VARCHAR2(10);
  v_relatos    NUMBER;
  v_bairro     VARCHAR2(100);
  v_codigo     NUMBER;

  PROCEDURE afirmar(p_ok BOOLEAN, p_msg VARCHAR2) IS
  BEGIN
    IF NOT NVL(p_ok, FALSE) THEN
      RAISE_APPLICATION_ERROR(-20900, 'FALHA: ' || p_msg);
    END IF;
  END;
BEGIN
  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES ('Morador T2', 't2.morador@alertabairro.test', '$2a$10$hashfalso', 'Vila Teste', 'morador')
    RETURNING id INTO v_id_usuario;

  -- 1. registro: devolve o id, grava os defaults e herda o bairro DO USUARIO
  prc_registrar_ocorrencia(v_id_usuario, 'Alagamento', 'Rua alagada no teste',
                           -23.548900, -46.638800, NULL, v_id_ocor);
  afirmar(v_id_ocor IS NOT NULL, 'p_id_ocorrencia nao pode voltar NULL');
  SELECT status, prioridade, relatos, bairro
    INTO v_status, v_prior, v_relatos, v_bairro
    FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_status  = 'aberta',     'status deveria ser aberta, veio ' || v_status);
  afirmar(v_relatos = 1,            'relatos deveria ser 1, veio ' || v_relatos);
  afirmar(v_bairro  = 'Vila Teste', 'bairro deveria vir do usuario, veio ' || NVL(v_bairro, 'NULL'));

  -- 2. prioridade inicial por tipo
  afirmar(v_prior = 'alta', 'Alagamento deveria nascer alta, veio ' || v_prior);
  prc_registrar_ocorrencia(v_id_usuario, 'Sem luz', 'Poste apagado',
                           -23.546100, -46.635200, NULL, v_id_luz);
  SELECT prioridade INTO v_prior FROM ocorrencia WHERE id = v_id_luz;
  afirmar(v_prior = 'normal', 'Sem luz deveria nascer normal, veio ' || v_prior);

  -- 3. usuario inexistente -> -20001
  v_codigo := 0;
  BEGIN
    prc_registrar_ocorrencia(-999, 'Alagamento', 'Usuario que nao existe',
                             -23.5, -46.6, NULL, v_id_ocor);
  EXCEPTION WHEN OTHERS THEN v_codigo := SQLCODE;
  END;
  afirmar(v_codigo = -20001, 'usuario inexistente deveria dar -20001, veio ' || v_codigo);

  -- 4. escalonamento no 3o relato: alta -> critica, e NAO sobe de novo depois
  --    (o UPDATE nao pode dar ORA-04091, pois o trigger nao le a propria tabela)
  SELECT id INTO v_id_ocor FROM ocorrencia WHERE id_usuario = v_id_usuario AND tipo = 'Alagamento' AND ROWNUM = 1;
  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_ocor;   -- relatos 2
  SELECT prioridade INTO v_prior FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_prior = 'alta', 'com 2 relatos ainda deveria ser alta, veio ' || v_prior);

  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_ocor;   -- relatos 3 -> escala
  SELECT prioridade, relatos INTO v_prior, v_relatos FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_relatos = 3,          'relatos deveria ser 3, veio ' || v_relatos);
  afirmar(v_prior = 'critica',    'no 3o relato alta deveria virar critica, veio ' || v_prior);

  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_ocor;   -- relatos 4
  SELECT prioridade INTO v_prior FROM ocorrencia WHERE id = v_id_ocor;
  afirmar(v_prior = 'critica', 'o escalonamento e uma vez so, deveria seguir critica, veio ' || v_prior);

  -- 5. normal tambem sobe um nivel no 3o relato
  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_luz;    -- 2
  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_luz;    -- 3 -> escala
  SELECT prioridade INTO v_prior FROM ocorrencia WHERE id = v_id_luz;
  afirmar(v_prior = 'alta', 'normal deveria virar alta no 3o relato, veio ' || v_prior);

  -- 6. ocorrencia resolvida nao escala
  prc_registrar_ocorrencia(v_id_usuario, 'Alagamento', 'Ja resolvida',
                           -23.55, -46.63, NULL, v_id_resolv);
  UPDATE ocorrencia SET status = 'resolvida' WHERE id = v_id_resolv;
  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_resolv; -- 2
  UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = v_id_resolv; -- 3
  SELECT prioridade INTO v_prior FROM ocorrencia WHERE id = v_id_resolv;
  afirmar(v_prior = 'alta', 'resolvida nao deveria escalar, continuaria alta, veio ' || v_prior);

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK t2_occurrence');
END;
/
EXIT ROLLBACK
