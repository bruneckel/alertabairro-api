-- ============================================================================
-- AVISO: ESTE ARQUIVO APAGA DADOS. USE SO NO ORACLE LOCAL DO DOCKER.
--
-- Nao rode no Oracle da FIAP nem em qualquer schema compartilhado: um schema
-- de aluno costuma ter tabelas de outras disciplinas, e USUARIO e um nome
-- extremamente colidivel.
--
-- Este e o UNICO arquivo do projeto com DROP. As migracoes de banco/migracao/
-- sao create-only de proposito, para que nenhum caminho automatico (Flyway,
-- subida da API, execucao de teste) consiga derrubar tabela nenhuma.
-- ============================================================================

-- Nao existe DROP ... IF EXISTS antes do 23ai, entao cada drop vai em um bloco
-- que engole o erro de "objeto nao existe".
BEGIN EXECUTE IMMEDIATE 'DROP TABLE confirmacao_ocorrencia CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE checkin CASCADE CONSTRAINTS PURGE';               EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE pedido_ajuda CASCADE CONSTRAINTS PURGE';          EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE ocorrencia CASCADE CONSTRAINTS PURGE';            EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE abrigo CASCADE CONSTRAINTS PURGE';                EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE usuario CASCADE CONSTRAINTS PURGE';               EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE seq_usuario';      EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE seq_abrigo';       EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE seq_ocorrencia';   EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE seq_pedido_ajuda'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE seq_checkin';      EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP VIEW vw_indicadores';       EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- A tabela de historico do Flyway, para o proximo "from scratch" recomecar do zero.
BEGIN EXECUTE IMMEDIATE 'DROP TABLE "flyway_schema_history" PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
