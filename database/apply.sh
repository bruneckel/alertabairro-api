#!/usr/bin/env bash
# Aplica as migracoes de database/migrations/ no Oracle local, em ordem de versao.
#
#   ./database/apply.sh            aplica todas as migracoes
#   ./database/apply.sh --reset    apaga tudo antes (SO no Docker local)
#
# A partir da Fase 2 quem aplica as migracoes e o Flyway, na subida da API.
# Este script existe porque as Tarefas 2 a 6 acontecem antes de a API existir
# -- e continua util como o caminho "rodar a mao", que e exatamente o que a
# entrega na FIAP precisa.
#
# Os arquivos de migracao sao SQL puro, sem diretivas do SQL*Plus: o Flyway
# Community nao interpreta WHENEVER SQLERROR. Por isso a diretiva e injetada
# aqui, na sessao, e nao dentro dos arquivos.
set -uo pipefail
shopt -s nullglob

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

if [ -f .env ]; then set -a; . ./.env; set +a; fi
export NLS_LANG="${NLS_LANG:-.AL32UTF8}"
USUARIO="${APP_USER:-alertabairro}"
SENHA="${APP_USER_PASSWORD:-alertabairro_dev}"
SERVICO="${ORACLE_SERVICO:-FREEPDB1}"

executar() {
  docker compose exec -T oracle sqlplus -S -L "${USUARIO}/${SENHA}@//localhost:1521/${SERVICO}" <<SQL
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK
SET SERVEROUTPUT ON
@${1}
EXIT
SQL
}

if [ "${1:-}" = "--reset" ]; then
  echo "=== reset_environment.sql (apagando objetos) ==="
  executar /database/reset_environment.sql || { echo "FALHOU o reset"; exit 1; }
fi

for arquivo in database/migrations/V*.sql; do
  nome="$(basename "$arquivo")"
  echo "=== ${nome} ==="
  if ! executar "/database/migrations/${nome}"; then
    echo "FALHOU: ${nome}"
    exit 1
  fi
done

echo ""
echo "migracoes aplicadas"
