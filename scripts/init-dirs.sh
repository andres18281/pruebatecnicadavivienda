#!/bin/sh
set -eu
mkdir -p data/minio
echo "Directorio data/minio listo."
echo "En Linux, Elasticsearch necesita: sudo sysctl -w vm.max_map_count=262144"
