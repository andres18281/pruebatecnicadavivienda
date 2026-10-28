#!/bin/sh
set -e
ES="http://elasticsearch:9200"
AUTH="elastic:${ELASTIC_PASSWORD}"

if curl -sf -u "$AUTH" -o /dev/null "$ES/_alias/documents"; then
  echo "Alias documents ya existe. Nada que hacer."
  exit 0
fi

curl -sf -u "$AUTH" -X PUT "$ES/documents_v1" \
  -H 'Content-Type: application/json' -d @/init/mapping.json

curl -sf -u "$AUTH" -X POST "$ES/_aliases" \
  -H 'Content-Type: application/json' \
  -d '{"actions":[{"add":{"index":"documents_v1","alias":"documents"}}]}'

echo "Indice y alias creados."
