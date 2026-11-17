-- AlertaBairro - V4: automacao dos abrigos.

-- ---------------------------------------------------------------------------
-- fn_abrigo_mais_proximo  (GET /abrigos/proximo)
--
-- Por que no banco: a decisao e sobre o CONJUNTO de abrigos. Resolver aqui
-- devolve um id, enquanto resolver na aplicacao exigiria trafegar os abrigos
-- para comparar fora. Operacao de conjunto pertence a quem guarda o conjunto.
--
-- Haversine, raio da Terra 6371 km, graus para radianos por graus*ACOS(-1)/180.
-- So considera abrigo com vaga (vagas_ocupadas < capacidade).
-- Sem nenhum abrigo com vaga, devolve NULL: a API responde null e o app
-- mostra "Nenhum abrigo com vagas no momento".
--
-- Nao faz DML, pois e usada dentro de SELECT (DML daria ORA-14551).
-- Usa ROWNUM em vez de FETCH FIRST, que exige 12c+ (portabilidade, spec 6.1).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_abrigo_mais_proximo(
  p_latitude  IN NUMBER,
  p_longitude IN NUMBER
) RETURN NUMBER IS
  v_id      NUMBER;
  k_raio_km CONSTANT NUMBER := 6371;
  k_rad     CONSTANT NUMBER := ACOS(-1) / 180;
BEGIN
  SELECT id INTO v_id FROM (
    SELECT a.id,
           2 * k_raio_km * ASIN(SQRT(
             POWER(SIN(((a.latitude  - p_latitude)  * k_rad) / 2), 2) +
             COS(p_latitude * k_rad) * COS(a.latitude * k_rad) *
             POWER(SIN(((a.longitude - p_longitude) * k_rad) / 2), 2)
           )) AS distancia_km
      FROM abrigo a
     WHERE a.vagas_ocupadas < a.capacidade
     ORDER BY distancia_km
  ) WHERE ROWNUM = 1;

  RETURN v_id;
EXCEPTION
  WHEN NO_DATA_FOUND THEN
    RETURN NULL;
END;
/

-- ---------------------------------------------------------------------------
-- prc_controle_vagas  (PATCH /abrigos/:id/vagas, so perfil voluntario)
--
-- Por que no banco: o SELECT ... FOR UPDATE trava a linha do abrigo. Dois
-- voluntarios registrando entrada ao mesmo tempo nao conseguem passar da
-- capacidade. O lock pertence a quem e dono do dado.
--
-- p_quantidade positivo = pessoas entrando, negativo = saindo.
--
-- A validacao vem ANTES do UPDATE de proposito: assim o erro sai como
-- -2000x e a API o traduz em HTTP 422 com mensagem legivel. Se o UPDATE
-- rodasse primeiro, a CHECK ck_abrigo_vagas dispararia ORA-02290, que a API
-- nao sabe traduzir e viraria 500. A CHECK fica como rede de seguranca para
-- escrita que nao passe por aqui.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE prc_controle_vagas(
  p_id_abrigo  IN NUMBER,
  p_quantidade IN NUMBER
) IS
  v_capacidade abrigo.capacidade%TYPE;
  v_ocupadas   abrigo.vagas_ocupadas%TYPE;
  v_novo       NUMBER;
BEGIN
  IF p_quantidade IS NULL OR p_quantidade = 0 THEN
    RAISE_APPLICATION_ERROR(-20005, 'Dados invalidos');
  END IF;

  BEGIN
    SELECT capacidade, vagas_ocupadas
      INTO v_capacidade, v_ocupadas
      FROM abrigo
     WHERE id = p_id_abrigo
       FOR UPDATE;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20003, 'Abrigo nao encontrado');
  END;

  v_novo := v_ocupadas + p_quantidade;

  IF v_novo > v_capacidade THEN
    RAISE_APPLICATION_ERROR(-20002, 'Abrigo sem vagas suficientes');
  END IF;

  IF v_novo < 0 THEN
    RAISE_APPLICATION_ERROR(-20004, 'Quantidade de saida maior que as vagas ocupadas');
  END IF;

  UPDATE abrigo SET vagas_ocupadas = v_novo WHERE id = p_id_abrigo;
END;
/
