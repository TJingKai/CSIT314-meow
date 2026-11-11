#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
SEED=${1:-20261108}
sh test/prepare-demo.sh "$SEED"
PORT=${MEOW_PORT:-8000}
echo
echo "Running the test cases (data seed $SEED) ..."
cd test
set +e
java TestRunner.java "http://127.0.0.1:$PORT"
RESULT=$?
set -e
echo "Results: test/testdata/test_results.csv"
exit $RESULT
