-- AlertaBairro - V6: carga inicial de abrigos.
--
-- O app nao cadastra abrigos, entao sem esta carga a tela de Abrigos nasce
-- vazia e fn_abrigo_mais_proximo nao tem o que responder.
--
-- As coordenadas sao as mesmas que o mock do app ja usava (regiao central de
-- Sao Paulo), para o mapa cair no lugar certo ao trocar os dados falsos pela
-- API real.
--
-- A Igreja Sao Jose entra LOTADA de proposito: sem um abrigo lotado nao da
-- para demonstrar que fn_abrigo_mais_proximo ignora quem nao tem vaga.
--
-- WHERE NOT EXISTS: migracao versionada roda uma vez, mas este arquivo
-- tambem e executado a mao na entrega da FIAP, e rodar duas vezes nao deve
-- duplicar a carga.

INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
SELECT 'Escola Municipal Central', 'Rua da Escola, 100', -23.551200, -46.634500, 80, 68 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM abrigo WHERE nome = 'Escola Municipal Central');

INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
SELECT 'Ginásio do Bairro Alto', 'Av. Principal, 450', -23.557500, -46.641900, 120, 80 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM abrigo WHERE nome = 'Ginásio do Bairro Alto');

INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
SELECT 'Igreja São José', 'Praça da Matriz, s/n', -23.544100, -46.627700, 50, 50 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM abrigo WHERE nome = 'Igreja São José');

COMMIT;
