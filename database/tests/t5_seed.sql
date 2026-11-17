-- t5_seed: prova a carga inicial de abrigos (V6).
--
-- Somente leitura: a carga e commitada pelas migracoes, e este teste confere
-- que ela chegou inteira e utilizavel.
WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v_total      NUMBER;
  v_com_vaga   NUMBER;
  v_lotados    NUMBER;
  v_achado     NUMBER;
  v_tamanho    NUMBER;

  PROCEDURE afirmar(p_ok BOOLEAN, p_msg VARCHAR2) IS
  BEGIN
    IF NOT NVL(p_ok, FALSE) THEN
      RAISE_APPLICATION_ERROR(-20900, 'FALHA: ' || p_msg);
    END IF;
  END;
BEGIN
  SELECT COUNT(*) INTO v_total FROM abrigo;
  afirmar(v_total >= 3, 'a carga deveria ter ao menos 3 abrigos, achei ' || v_total);

  -- A function precisa dos dois casos para ser demonstravel: abrigo com vaga
  -- e abrigo lotado.
  SELECT COUNT(*) INTO v_com_vaga FROM abrigo WHERE vagas_ocupadas < capacidade;
  afirmar(v_com_vaga >= 1, 'a carga deveria ter ao menos 1 abrigo com vaga, achei ' || v_com_vaga);

  SELECT COUNT(*) INTO v_lotados FROM abrigo WHERE vagas_ocupadas = capacidade;
  afirmar(v_lotados >= 1, 'a carga deveria ter ao menos 1 abrigo lotado, achei ' || v_lotados);

  -- com a carga no lugar, a busca pelo mais proximo responde de verdade
  v_achado := fn_abrigo_mais_proximo(-23.550000, -46.630000);
  afirmar(v_achado IS NOT NULL, 'com a carga aplicada deveria achar um abrigo com vaga');

  -- Acento atravessando o banco sem mojibake.
  -- Comparar o texto nao bastaria: se a gravacao e a leitura deturpassem do
  -- mesmo jeito, a comparacao passaria com o dado errado. O COMPRIMENTO
  -- delata: 'Ginasio do Bairro Alto' com acento tem 22 caracteres, e viraria
  -- 23 se o 'a' acentuado tivesse sido gravado como dois caracteres.
  SELECT LENGTH(nome) INTO v_tamanho FROM abrigo WHERE nome LIKE 'Gin%sio do Bairro Alto';
  afirmar(v_tamanho = 22, 'o nome acentuado deveria ter 22 caracteres, veio ' || v_tamanho);

  DBMS_OUTPUT.PUT_LINE('OK t5_seed');
END;
/
EXIT
