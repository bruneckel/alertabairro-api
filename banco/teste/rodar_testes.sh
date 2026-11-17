#!/usr/bin/env bash
# Executa os scripts de teste SQL dentro do container Oracle.
#
#   ./banco/teste/rodar_testes.sh                  roda todos os t*.sql em ordem
#   ./banco/teste/rodar_testes.sh t2_ocorrencia.sql roda apenas o indicado
#
# Cada script de teste usa WHENEVER SQLERROR EXIT FAILURE, entao qualquer
# RAISE_APPLICATION_ERROR faz o sqlplus sair com codigo != 0. Este runner
# propaga o primeiro codigo de saida nao-zero que encontrar.
set -uo pipefail
shopt -s nullglob

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"

if [ -f .env ]; then set -a; . ./.env; set +a; fi

USUARIO="${APP_USER:-alertabairro}"
SENHA="${APP_USER_PASSWORD:-alertabairro_dev}"
SERVICO="${ORACLE_SERVICO:-FREEPDB1}"

if [ "$#" -gt 0 ]; then
  ARQUIVOS=("$@")
else
  ARQUIVOS=()
  for f in banco/teste/t*.sql; do ARQUIVOS+=("$(basename "$f")"); done
fi

if [ "${#ARQUIVOS[@]}" -eq 0 ]; then
  echo "Nenhum script de teste encontrado em banco/teste/"
  exit 1
fi

primeiro_erro=0
for arquivo in "${ARQUIVOS[@]}"; do
  arquivo="$(basename "$arquivo")"
  printf '\n=== %s ===\n' "$arquivo"
  if docker compose exec -T oracle sqlplus -S -L "${USUARIO}/${SENHA}@//localhost:1521/${SERVICO}" "@/teste/${arquivo}"; then
    echo "-- passou: ${arquivo}"
  else
    codigo=$?
    echo "-- FALHOU: ${arquivo} (codigo ${codigo})"
    [ "$primeiro_erro" -eq 0 ] && primeiro_erro="$codigo"
  fi
done

echo ""
if [ "$primeiro_erro" -ne 0 ]; then
  echo "RESULTADO: houve falha (primeiro codigo ${primeiro_erro})"
  exit "$primeiro_erro"
fi
echo "RESULTADO: todos passaram"
