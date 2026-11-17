-- AlertaBairro - V3: automacao das ocorrencias.
--
-- Criterio para uma rotina morar no banco (spec 8): fica aqui o que e mais
-- barato ou mais correto no banco. Nao entra procedure decorativa.
--
-- Objetos sem aspas no nome: com aspas o Oracle passa a diferenciar
-- maiusculas e a API nao encontra a rotina.

-- ---------------------------------------------------------------------------
-- prc_registrar_ocorrencia  (POST /ocorrencias)
--
-- Por que no banco: deriva o bairro do USUARIO, define a prioridade inicial,
-- insere e devolve o id em UMA ida ao banco e UMA transacao. Garante que o
-- bairro gravado e o do usuario sem uma segunda consulta da aplicacao.
--
-- A ordem dos parametros e contrato com a API (SimpleJdbcCall): mudar a ordem
-- quebra a chamada com ORA-06550.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE prc_registrar_ocorrencia(
  p_id_usuario    IN  NUMBER,
  p_tipo          IN  VARCHAR2,
  p_descricao     IN  VARCHAR2,
  p_latitude      IN  NUMBER,
  p_longitude     IN  NUMBER,
  p_foto_uri      IN  VARCHAR2,
  p_id_ocorrencia OUT NUMBER
) IS
  v_bairro     usuario.bairro%TYPE;
  v_prioridade ocorrencia.prioridade%TYPE;
BEGIN
  BEGIN
    SELECT bairro INTO v_bairro FROM usuario WHERE id = p_id_usuario;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Usuario nao encontrado');
  END;

  -- Tipos que o app envia, com acento e exatamente assim:
  -- 'Alagamento', 'Arvore caida', 'Sem luz', 'Deslizamento'.
  -- Os dois que ameacam vida nascem com prioridade alta.
  v_prioridade := CASE
                    WHEN p_tipo IN ('Alagamento', 'Deslizamento') THEN 'alta'
                    ELSE 'normal'
                  END;

  INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude,
                          status, prioridade, relatos, bairro, foto_uri)
       VALUES (p_id_usuario, p_tipo, p_descricao, p_latitude, p_longitude,
               'aberta', v_prioridade, 1, v_bairro, p_foto_uri)
    RETURNING id INTO p_id_ocorrencia;
END;
/

-- ---------------------------------------------------------------------------
-- trg_ocorrencia_escalona  (PATCH /ocorrencias/:id  {acao:"confirmar"})
--
-- Por que no banco: garantia estrutural. A API nao chama o trigger, so faz
-- UPDATE ocorrencia SET relatos = relatos + 1. Qualquer caminho de escrita
-- que incremente relatos passa por esta regra, inclusive carga manual.
--
-- ORA-04091 (table is mutating): um trigger FOR EACH ROW na OCORRENCIA nao
-- pode fazer SELECT na propria OCORRENCIA. Esta regra olha apenas :OLD e :NEW,
-- entao nao ha consulta e nao ha erro.
--
-- Dispara UMA VEZ, no terceiro relato. Sem a guarda OLD.relatos < 3 qualquer
-- ocorrencia popular subiria ate critica sozinha a cada confirmacao.
-- Ocorrencia resolvida nao escala.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_ocorrencia_escalona
BEFORE UPDATE OF relatos ON ocorrencia
FOR EACH ROW
WHEN (OLD.relatos < 3 AND NEW.relatos >= 3 AND NEW.status <> 'resolvida')
BEGIN
  :NEW.prioridade := CASE :OLD.prioridade
                       WHEN 'normal' THEN 'alta'
                       WHEN 'alta'   THEN 'critica'
                       ELSE 'critica'
                     END;
END;
/
