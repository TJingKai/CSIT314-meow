#!/usr/bin/env sh
# Shared by start-demo.sh and run-tests.sh (same steps as prepare-demo.bat).
set -eu
cd "$(dirname "$0")/.."
SEED=${1:-20261108}
PORT=${MEOW_PORT:-8000}
URL="http://127.0.0.1:$PORT"

command -v java >/dev/null 2>&1 || { echo 'Install a Java 17 or newer JDK and add it to PATH.'; exit 1; }
command -v javac >/dev/null 2>&1 || { echo 'The test tools need a JDK (javac), Java 17 or newer.'; exit 1; }
[ -f meow.jar ] || { echo 'meow.jar is missing. Run sh build.sh first.'; exit 1; }

echo "[1/6] Stopping any MEOW server on port $PORT ..."
pkill -f "java -jar meow.jar" 2>/dev/null || true
sleep 2

echo "[2/6] Clearing the old database ..."
rm -rf instance

echo "[3/6] Starting MEOW with the demo accounts (log: server.log) ..."
nohup java -jar meow.jar --meow.seed-demo=true --server.port="$PORT" > server.log 2>&1 &

cd test
echo "[4/6] Generating test data (seed $SEED) ..."
java TestDataGenerator.java "$SEED"
echo "[5/6] Checking the generated CSV files ..."
java DataCheck.java csv
echo "[6/6] Loading the test data and checking the database ..."
java TestDataLoader.java "$URL"
java DataCheck.java db
