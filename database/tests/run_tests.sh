#!/usr/bin/env bash
# Executa os scripts de teste SQL dentro do container Oracle.
#
#   ./database/tests/run_tests.sh                   roda todos os t*.sql em ordem
#   ./database/tests/run_tests.sh t2_occurrence.sql roda apenas o indicado
#
# Um script so conta como aprovado quando as DUAS coisas valem:
#   1. o sqlplus saiu com codigo 0
#   2. a saida contem a linha "OK <nome>", que cada script imprime no fim
#
# A segunda checagem existe porque as diretivas WHENEVER moram DENTRO do
# arquivo de teste: se o arquivo nao for encontrado (renomeado, fora do mount),
# o sqlplus imprime SP2-0310 e sai 0 -- e sem essa checagem o runner
# anunciaria "passou" para um teste que nunca rodou.
set -uo pipefail
shopt -s nullglob

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"

if [ -f .env ]; then set -a; . ./.env; set +a; fi

USUARIO="${APP_USER:-alertabairro}"
SENHA="${APP_USER_PASSWORD:-alertabairro_dev}"
SERVICO="${ORACLE_SERVICO:-FREEPDB1}"
# Garante que o cliente fala UTF-8: com um charset de cliente diferente, os
# literais acentuados dos scripts chegariam deturpados ao banco.
export NLS_LANG="${NLS_LANG:-.AL32UTF8}"

if [ "$#" -gt 0 ]; then
  ARQUIVOS=("$@")
else
  ARQUIVOS=()
  for f in database/tests/t*.sql database/tests/t*.sh; do ARQUIVOS+=("$(basename "$f")"); done
fi

if [ "${#ARQUIVOS[@]}" -eq 0 ]; then
  echo "Nenhum script de teste encontrado em database/tests/"
  exit 1
fi

primeiro_erro=0
registrar_erro() { [ "$primeiro_erro" -eq 0 ] && primeiro_erro="${1:-1}"; return 0; }

for arquivo in "${ARQUIVOS[@]}"; do
  arquivo="$(basename "$arquivo")"
  printf '\n=== %s ===\n' "$arquivo"

  if [ ! -f "database/tests/${arquivo}" ]; then
    echo "-- FALHOU: ${arquivo} (arquivo nao existe em database/tests/)"
    registrar_erro 1
    continue
  fi

  # Testes de concorrencia precisam de DUAS sessoes simultaneas, o que nao cabe
  # em um script SQL executado por uma sessao. Esses vem como .sh e valem pelo
  # codigo de saida.
  if [ "${arquivo##*.}" = "sh" ]; then
    if bash "database/tests/${arquivo}"; then
      echo "-- passou: ${arquivo}"
    else
      codigo=$?
      echo "-- FALHOU: ${arquivo} (codigo ${codigo})"
      registrar_erro "$codigo"
    fi
    continue
  fi

  saida=$(docker compose exec -T -e NLS_LANG="$NLS_LANG" oracle \
            sqlplus -S -L "${USUARIO}/${SENHA}@//localhost:1521/${SERVICO}" \
            "@/tests/${arquivo}" 2>&1)
  codigo=$?
  printf '%s\n' "$saida"

  esperado="OK ${arquivo%.sql}"
  if [ "$codigo" -ne 0 ]; then
    echo "-- FALHOU: ${arquivo} (sqlplus saiu com ${codigo})"
    registrar_erro "$codigo"
  elif ! printf '%s' "$saida" | tr -d '\r' | grep -q "^${esperado}\$"; then
    echo "-- FALHOU: ${arquivo} (saiu 0, mas nao imprimiu \"${esperado}\" -- o script chegou ao fim?)"
    registrar_erro 1
  else
    echo "-- passou: ${arquivo}"
  fi
done

echo ""
if [ "$primeiro_erro" -ne 0 ]; then
  echo "RESULTADO: houve falha (primeiro codigo ${primeiro_erro})"
  exit "$primeiro_erro"
fi
echo "RESULTADO: todos passaram"
