#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh test/prepare-demo.sh 20261108
PORT=${MEOW_PORT:-8000}
echo
echo "MEOW is ready in demo mode: http://127.0.0.1:$PORT"
echo "Admin admin@meow.demo / MEOWDemo!2026 (manager, designer, customer @meow.demo use the same password)"
echo "Generated users and passwords: test/testdata/users.csv"
echo "Server log: server.log. Stop with: pkill -f meow.jar"
