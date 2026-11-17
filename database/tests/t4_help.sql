-- t4_help: prova prc_encerrar_pedidos_antigos, fn_ocupacao_abrigos e vw_indicadores.
--
-- Como a view agrega as 4 tabelas, o teste limpa os dados transacionais antes
-- de asseverar numeros exatos. Assim ele nao depende da carga inicial da V6
-- nem do seed de demonstracao. Tudo volta no ROLLBACK.
WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v_usuario NUMBER;
  v_p1      NUMBER;
  v_p2      NUMBER;
  v_p3      NUMBER;
  v_qtd     NUMBER;
  v_status  VARCHAR2(20);
  v_pct     NUMBER;
  v_linhas  NUMBER;
  v_criticas NUMBER;
  v_bem24   NUMBER;

  PROCEDURE afirmar(p_ok BOOLEAN, p_msg VARCHAR2) IS
  BEGIN
    IF NOT NVL(p_ok, FALSE) THEN
      RAISE_APPLICATION_ERROR(-20900, 'FALHA: ' || p_msg);
    END IF;
  END;
BEGIN
  -- isolamento: zera o transacional (filho antes do pai)
  DELETE FROM confirmacao_ocorrencia;
  DELETE FROM ocorrencia;
  DELETE FROM pedido_ajuda;
  DELETE FROM checkin;
  DELETE FROM abrigo;

  INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
       VALUES ('Morador T4', 't4.morador@alertabairro.test', '$2a$10$hashfalso', 'Centro', 'morador')
    RETURNING id INTO v_usuario;

  -- ------------------------------------------------------------------
  -- prc_encerrar_pedidos_antigos
  -- ------------------------------------------------------------------
  -- antigo e aguardando -> deve encerrar
  INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, criado_em)
       VALUES (v_usuario, 'pedido', 'Remedio', 'Antigo aguardando', 'alta', 'aguardando',
               SYSTIMESTAMP - NUMTODSINTERVAL(10, 'DAY')) RETURNING id INTO v_p1;
  -- recente e aguardando -> NAO deve encerrar
  INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, criado_em)
       VALUES (v_usuario, 'pedido', 'Transporte', 'Recente aguardando', 'media', 'aguardando',
               SYSTIMESTAMP - NUMTODSINTERVAL(1, 'DAY')) RETURNING id INTO v_p2;
  -- antigo mas ja resolvido pelo autor -> NAO deve ser tocado
  INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, criado_em)
       VALUES (v_usuario, 'oferta', 'Abrigo', 'Antigo resolvido', 'baixa', 'resolvido',
               SYSTIMESTAMP - NUMTODSINTERVAL(10, 'DAY')) RETURNING id INTO v_p3;

  prc_encerrar_pedidos_antigos(7, v_qtd);
  afirmar(v_qtd = 1, 'deveria encerrar exatamente 1 pedido, encerrou ' || NVL(TO_CHAR(v_qtd), 'NULL'));

  SELECT status INTO v_status FROM pedido_ajuda WHERE id = v_p1;
  afirmar(v_status = 'atendido', 'o antigo aguardando deveria virar atendido, veio ' || v_status);
  SELECT status INTO v_status FROM pedido_ajuda WHERE id = v_p2;
  afirmar(v_status = 'aguardando', 'o recente deveria seguir aguardando, veio ' || v_status);
  SELECT status INTO v_status FROM pedido_ajuda WHERE id = v_p3;
  afirmar(v_status = 'resolvido', 'o resolvido nao deveria ser tocado, veio ' || v_status);

  -- segunda chamada: nada a encerrar -> 0, nunca NULL
  prc_encerrar_pedidos_antigos(7, v_qtd);
  afirmar(v_qtd IS NOT NULL, 'p_qtd_encerrados nunca pode voltar NULL');
  afirmar(v_qtd = 0, 'sem nada a encerrar deveria devolver 0, veio ' || v_qtd);

  -- ------------------------------------------------------------------
  -- fn_ocupacao_abrigos
  -- ------------------------------------------------------------------
  v_pct := fn_ocupacao_abrigos;
  afirmar(v_pct = 0, 'sem abrigo deveria devolver 0 (sem divisao por zero), veio ' || NVL(TO_CHAR(v_pct), 'NULL'));

  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('T4 Um', 'Rua 1', -23.55, -46.63, 50, 40);
  INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
       VALUES ('T4 Dois', 'Rua 2', -23.56, -46.64, 30, 28);
  -- 68 de 80 = 85 por cento
  v_pct := fn_ocupacao_abrigos;
  afirmar(v_pct = 85, 'ocupacao de 68 em 80 deveria dar 85, veio ' || v_pct);

  -- ------------------------------------------------------------------
  -- vw_indicadores
  -- ------------------------------------------------------------------
  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro, status, prioridade)
       VALUES (v_usuario, 'Alagamento', 'Aberta critica', -23.55, -46.63, 'Centro', 'aberta', 'critica');
  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro, status, prioridade)
       VALUES (v_usuario, 'Alagamento', 'Resolvida critica', -23.55, -46.63, 'Centro', 'resolvida', 'critica');
  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, bairro, status, prioridade)
       VALUES (v_usuario, 'Sem luz', 'Em atendimento', -23.55, -46.63, 'Centro', 'em_atendimento', 'normal');

  INSERT INTO checkin (id_usuario, status, criado_em)
       VALUES (v_usuario, 'estou_bem', SYSTIMESTAMP);
  INSERT INTO checkin (id_usuario, status, criado_em)
       VALUES (v_usuario, 'estou_bem', SYSTIMESTAMP - NUMTODSINTERVAL(2, 'DAY'));

  SELECT COUNT(*) INTO v_linhas FROM vw_indicadores;
  afirmar(v_linhas = 1, 'a view deveria devolver exatamente 1 linha, veio ' || v_linhas);

  SELECT ocorrencias_criticas, checkins_estou_bem_24h
    INTO v_criticas, v_bem24
    FROM vw_indicadores;
  afirmar(v_criticas = 1, 'criticas deveria contar so as NAO resolvidas (1), veio ' || v_criticas);
  afirmar(v_bem24 = 1,    'estou_bem nas ultimas 24h deveria ser 1, veio ' || v_bem24);

  SELECT ocupacao_abrigos_pct INTO v_pct FROM vw_indicadores;
  afirmar(v_pct = 85, 'a view deveria reaproveitar a function (85), veio ' || v_pct);

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK t4_help');
END;
/
EXIT
