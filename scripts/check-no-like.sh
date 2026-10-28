#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
if grep -RInE --include='*.java' --include='*.sql' '\bILIKE\b|\bLIKE\b' "$root/services" "$root/libs" "$root/infra/postgres"; then
  echo "Se encontro LIKE o ILIKE. La busqueda no puede usar ese operador."
  exit 1
fi
echo "Sin LIKE ni ILIKE en el codigo de busqueda."
