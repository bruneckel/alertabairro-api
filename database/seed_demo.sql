-- ============================================================================
-- AlertaBairro - DADOS DE DEMONSTRACAO (opt-in)
--
-- ESTE ARQUIVO NAO E UMA MIGRACAO. Fica fora de database/migrations/ de
-- proposito: nenhum caminho automatico (Flyway, subida da API, teste) o
-- executa, e por isso ele nao tem como cair no Oracle da FIAP.
--
-- Para que serve: num banco recem-criado o Mapa e a aba Ajuda nascem vazios,
-- o que torna a gravacao do video pobre. Isto popula o minimo para as tres
-- funcionalidades aparecerem com conteudo.
--
-- Como rodar:
--     docker compose exec -T oracle sqlplus -S alertabairro/alertabairro_dev@//localhost:1521/FREEPDB1 @/database/seed_demo.sql
--
-- CONTAS DE DEMONSTRACAO -- senha unica: alertabairro123
--     maria@alertabairro.demo   morador     (autora da ocorrencia critica)
--     joao@alertabairro.demo    idoso       (ja confirmou a ocorrencia da Maria)
--     ana@alertabairro.demo     voluntario  (pode atender, resolver e mexer em vagas)
--
-- As senhas vao como hash bcrypt (custo 10), nunca em texto. Cada usuario tem
-- seu proprio salt.
--
-- RECEITA PARA DEMONSTRAR O TRIGGER DE ESCALONAMENTO NO VIDEO:
-- a ocorrencia "Rua alagada" ja esta com 2 relatos e prioridade alta.
-- Entre com a conta da ANA e toque em "Tambem vejo isso aqui": o terceiro
-- relato dispara trg_ocorrencia_escalona e a prioridade vira critica na tela.
-- Precisa ser a Ana porque ninguem confirma a propria ocorrencia (Maria) e
-- ninguem confirma duas vezes (Joao ja confirmou).
-- ============================================================================

-- Uma falha no meio deste arquivo deixaria o banco parcialmente populado e
-- COMMITADO, com o sqlplus saindo 0: WHENEVER ... EXIT FAILURE tem COMMIT como
-- padrao. As diretivas abaixo trocam isso por ROLLBACK. Ruling 3 (manter
-- diretivas do SQL*Plus fora dos arquivos) nao se aplica aqui: este arquivo
-- nunca passa pelo Flyway, de proposito.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK

INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
SELECT 'Maria Souza', 'maria@alertabairro.demo', '$2y$10$56qnoXmAwdP.NGQq4OsQCOaXuo28EdG.394875vKhWQLs7dez2JUa', 'Centro', 'morador' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'maria@alertabairro.demo');

INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
SELECT 'João Pereira', 'joao@alertabairro.demo', '$2y$10$Z7Z1bE68TITlE87tmDaUWue7nHINbRmByYU8/eHwW20rTMJwnG5aW', 'Centro', 'idoso' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'joao@alertabairro.demo');

INSERT INTO usuario (nome, email, senha_hash, bairro, perfil)
SELECT 'Ana Lima', 'ana@alertabairro.demo', '$2y$10$E..ySZKaFvcyqg9Mo1wRlOBmCW12wbD5djbZMk1n4qeEKuQZPQTOi', 'Centro', 'voluntario' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'ana@alertabairro.demo');

-- Ocorrencia a 2 relatos: o proximo "tambem vejo isso aqui" escala para critica.
INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, status, prioridade, relatos, bairro)
SELECT (SELECT id FROM usuario WHERE email = 'maria@alertabairro.demo'),
       'Alagamento', 'Rua alagada, água na altura do joelho.',
       -23.548900, -46.638800, 'aberta', 'alta', 2, 'Centro' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM ocorrencia WHERE descricao = 'Rua alagada, água na altura do joelho.');

-- relatos = 2 significa "autora + 1 confirmacao", entao a confirmacao do Joao
-- precisa existir, senao ele poderia confirmar e o contador ficaria incoerente.
INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario)
SELECT (SELECT id FROM ocorrencia WHERE descricao = 'Rua alagada, água na altura do joelho.'),
       (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo') FROM dual
 WHERE NOT EXISTS (
   SELECT 1 FROM confirmacao_ocorrencia
    WHERE id_ocorrencia = (SELECT id FROM ocorrencia WHERE descricao = 'Rua alagada, água na altura do joelho.')
      AND id_usuario    = (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo'));

INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, status, prioridade, relatos, bairro)
SELECT (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo'),
       'Árvore caída', 'Árvore bloqueando a faixa da direita.',
       -23.553200, -46.630100, 'em_atendimento', 'alta', 1, 'Centro' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM ocorrencia WHERE descricao = 'Árvore bloqueando a faixa da direita.');

INSERT INTO ocorrencia (id_usuario, tipo, descricao, latitude, longitude, status, prioridade, relatos, bairro)
SELECT (SELECT id FROM usuario WHERE email = 'ana@alertabairro.demo'),
       'Sem luz', 'Poste apagado na esquina.',
       -23.546100, -46.635200, 'resolvida', 'normal', 1, 'Centro' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM ocorrencia WHERE descricao = 'Poste apagado na esquina.');

INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, latitude, longitude)
SELECT (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo'),
       'pedido', 'Remédio', 'Preciso buscar um remédio para minha mãe idosa.',
       'alta', 'aguardando', -23.549500, -46.637000 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM pedido_ajuda WHERE descricao = 'Preciso buscar um remédio para minha mãe idosa.');

INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, latitude, longitude)
SELECT (SELECT id FROM usuario WHERE email = 'maria@alertabairro.demo'),
       'pedido', 'Transporte', 'Transporte até o abrigo para uma família.',
       'media', 'atendido', -23.551000, -46.639000 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM pedido_ajuda WHERE descricao = 'Transporte até o abrigo para uma família.');

INSERT INTO pedido_ajuda (id_usuario, modo, categoria, descricao, urgencia, status, latitude, longitude)
SELECT (SELECT id FROM usuario WHERE email = 'ana@alertabairro.demo'),
       'oferta', 'Abrigo', 'Tenho dois quartos livres para receber vizinhos.',
       'baixa', 'aguardando', -23.552000, -46.636000 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM pedido_ajuda WHERE descricao = 'Tenho dois quartos livres para receber vizinhos.');

-- Check-ins recentes: alimentam os indicadores de 24h e dao ao servidor uma
-- ultima posicao conhecida, usada como fallback do calculo de distancia.
INSERT INTO checkin (id_usuario, status, latitude, longitude)
SELECT (SELECT id FROM usuario WHERE email = 'maria@alertabairro.demo'),
       'estou_bem', -23.549000, -46.638000 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM checkin WHERE id_usuario = (SELECT id FROM usuario WHERE email = 'maria@alertabairro.demo'));

INSERT INTO checkin (id_usuario, status, latitude, longitude)
SELECT (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo'),
       'preciso_de_ajuda', -23.549500, -46.637000 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM checkin WHERE id_usuario = (SELECT id FROM usuario WHERE email = 'joao@alertabairro.demo'));

COMMIT;
