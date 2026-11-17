-- AlertaBairro - V5: rede de ajuda e indicadores.

-- ---------------------------------------------------------------------------
-- prc_encerrar_pedidos_antigos  (POST /ajuda/encerrar-antigos, so voluntario)
--
-- Por que no banco: e uma operacao de CONJUNTO. Um UPDATE em lote resolve em
-- uma ida ao banco o que na aplicacao seria um laco com uma ida por linha.
-- SQL%ROWCOUNT ja devolve quantas linhas mudaram, sem contagem extra.
--
-- O alvo e 'atendido', nao 'resolvido': 'resolvido' significa "o autor
-- resolveu", e um encerramento automatico por tempo nao e o autor agindo.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE prc_encerrar_pedidos_antigos(
  p_dias           IN  NUMBER,
  p_qtd_encerrados OUT NUMBER
) IS
BEGIN
  IF p_dias IS NULL OR p_dias < 1 THEN
    RAISE_APPLICATION_ERROR(-20005, 'Dados invalidos');
  END IF;

  UPDATE pedido_ajuda
     SET status = 'atendido'
   WHERE status = 'aguardando'
     AND criado_em < SYSTIMESTAMP - NUMTODSINTERVAL(p_dias, 'DAY');

  p_qtd_encerrados := SQL%ROWCOUNT;
END;
/

-- ---------------------------------------------------------------------------
-- fn_ocupacao_abrigos
--
-- Isolada para que a view e a API nao dupliquem a mesma divisao -- e para que
-- o caso "nenhum abrigo cadastrado" tenha UM lugar onde a divisao por zero e
-- tratada, em vez de dois.
--
-- Funcao sem argumento: no Oracle chama-se sem parenteses.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_ocupacao_abrigos RETURN NUMBER IS
  v_capacidade NUMBER;
  v_ocupadas   NUMBER;
BEGIN
  SELECT NVL(SUM(capacidade), 0), NVL(SUM(vagas_ocupadas), 0)
    INTO v_capacidade, v_ocupadas
    FROM abrigo;

  IF v_capacidade = 0 THEN
    RETURN 0;
  END IF;

  RETURN ROUND((v_ocupadas / v_capacidade) * 100, 1);
END;
/

-- ---------------------------------------------------------------------------
-- vw_indicadores  (GET /indicadores)
--
-- Por que no banco: 11 agregacoes sobre 4 tabelas. Agregar aqui devolve UMA
-- linha, enquanto agregar na aplicacao exigiria trazer as linhas para contar fora.
--
-- Nomes de coluna em snake_case: o repositorio da API faz o apelido para
-- camelCase na projecao, que e onde o contrato com o app e decidido.
--
-- ocorrencias_criticas conta apenas as NAO resolvidas: uma ocorrencia critica
-- ja resolvida nao e um problema em aberto, e o painel mostra o que exige acao.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_indicadores AS
SELECT
  (SELECT COUNT(*) FROM ocorrencia WHERE status = 'aberta')         AS ocorrencias_abertas,
  (SELECT COUNT(*) FROM ocorrencia WHERE status = 'em_atendimento') AS ocorrencias_em_atendimento,
  (SELECT COUNT(*) FROM ocorrencia WHERE status = 'resolvida')      AS ocorrencias_resolvidas,
  (SELECT COUNT(*) FROM ocorrencia
    WHERE prioridade = 'critica' AND status <> 'resolvida')         AS ocorrencias_criticas,
  (SELECT NVL(SUM(capacidade), 0)     FROM abrigo)                  AS capacidade_abrigos,
  (SELECT NVL(SUM(vagas_ocupadas), 0) FROM abrigo)                  AS vagas_ocupadas,
  fn_ocupacao_abrigos                                               AS ocupacao_abrigos_pct,
  (SELECT COUNT(*) FROM pedido_ajuda WHERE status = 'aguardando')   AS pedidos_aguardando,
  (SELECT COUNT(*) FROM pedido_ajuda WHERE status = 'atendido')     AS pedidos_atendidos,
  (SELECT COUNT(*) FROM pedido_ajuda WHERE status = 'resolvido')    AS pedidos_resolvidos,
  (SELECT COUNT(*) FROM checkin
    WHERE status = 'estou_bem'
      AND criado_em > SYSTIMESTAMP - INTERVAL '1' DAY)              AS checkins_estou_bem_24h,
  (SELECT COUNT(*) FROM checkin
    WHERE status = 'preciso_de_ajuda'
      AND criado_em > SYSTIMESTAMP - INTERVAL '1' DAY)              AS checkins_preciso_ajuda_24h
FROM dual;
