#!/usr/bin/env bash
# t6_lock: prova que o SELECT ... FOR UPDATE de prc_controle_vagas realmente
# serializa dois voluntarios simultaneos.
#
# POR QUE ESTE TESTE EXISTE
# A spec 9.3 e o README vendem esse lock como a correcao do defeito herdado
# 5.3(e). Nenhum teste de sessao unica prova isso: se alguem apagasse o
# FOR UPDATE, todos os outros testes continuariam verdes.
#
# O QUE DISCRIMINA
# Abrigo com capacidade 10 e 9 ocupadas. Duas entradas de 1 pessoa ao mesmo
# tempo:
#   COM FOR UPDATE  -> B espera no SELECT, rele 10 ao acordar, calcula 11 e
#                      recebe ORA-20002. Final 10, correto.
#   SEM FOR UPDATE  -> B le 9 (pre-imagem), calcula 10 e grava. Nenhum erro,
#                      final 10 -- mas duas pessoas entraram e so uma foi
#                      contada. Lost update silencioso.
# A asercao decisiva, portanto, e "B recebeu ORA-20002".
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"
[ -f .env ] && { set -a; . ./.env; set +a; }

USUARIO="${APP_USER:-alertabairro}"
SENHA="${APP_USER_PASSWORD:-alertabairro_dev}"
SERVICO="${ORACLE_SERVICO:-FREEPDB1}"
NOME_ABRIGO="T6 Lock Concorrencia"

sql() { docker compose exec -T oracle sqlplus -S -L "${USUARIO}/${SENHA}@//localhost:1521/${SERVICO}"; }

falhar() { echo "FALHA: $1"; limpar; exit 1; }

limpar() {
  sql >/dev/null 2>&1 <<SQL
DELETE FROM abrigo WHERE nome = '${NOME_ABRIGO}';
COMMIT;
EXIT
SQL
}

limpar

# As duas sessoes precisam ver o abrigo, entao a preparacao e commitada.
id_abrigo=$(sql <<SQL | tr -d '[:space:]'
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 VERIFY OFF
INSERT INTO abrigo (nome, endereco, latitude, longitude, capacidade, vagas_ocupadas)
VALUES ('${NOME_ABRIGO}', 'Rua do Lock, 1', -23.55, -46.63, 10, 9);
COMMIT;
SELECT id FROM abrigo WHERE nome = '${NOME_ABRIGO}';
EXIT
SQL
)
[[ "$id_abrigo" =~ ^[0-9]+$ ]] || falhar "nao consegui criar o abrigo de teste (veio '${id_abrigo}')"

saida_a=$(mktemp); saida_b=$(mktemp)
trap 'rm -f "$saida_a" "$saida_b"' EXIT

# Sessao A: entra 1 pessoa (9 -> 10), segura a transacao e so depois commita.
( sql > "$saida_a" 2>&1 <<SQL
SET SERVEROUTPUT ON
BEGIN
  prc_controle_vagas(${id_abrigo}, 1);
  DBMS_OUTPUT.PUT_LINE('A aplicou');
END;
/
BEGIN DBMS_SESSION.SLEEP(6); END;
/
COMMIT;
EXIT
SQL
) &
pid_a=$!

# Sessao B: entra no meio da transacao de A.
( sql > "$saida_b" 2>&1 <<SQL
SET SERVEROUTPUT ON
BEGIN DBMS_SESSION.SLEEP(2); END;
/
BEGIN
  prc_controle_vagas(${id_abrigo}, 1);
  DBMS_OUTPUT.PUT_LINE('B aplicou');
END;
/
COMMIT;
EXIT
SQL
) &
pid_b=$!

wait $pid_a; wait $pid_b

grep -q "A aplicou" "$saida_a" || falhar "a sessao A deveria ter aplicado a entrada. Saida: $(cat "$saida_a")"

if ! grep -q "ORA-20002" "$saida_b"; then
  echo "--- saida da sessao B ---"; cat "$saida_b"
  falhar "a sessao B deveria receber ORA-20002. Sem isso, as duas entradas foram aceitas e uma foi perdida: o FOR UPDATE nao esta serializando."
fi

final=$(sql <<SQL | tr -d '[:space:]'
SET HEADING OFF FEEDBACK OFF PAGESIZE 0
SELECT vagas_ocupadas FROM abrigo WHERE id = ${id_abrigo};
EXIT
SQL
)
[ "$final" = "10" ] || falhar "a ocupacao final deveria ser 10 (a capacidade), veio '${final}'"

limpar
echo "OK t6_lock"
