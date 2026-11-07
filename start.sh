#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
command -v java >/dev/null 2>&1 || { echo 'Install Java 17 or newer and add it to PATH.'; exit 1; }
if [ ! -f meow.jar ]; then sh build.sh; fi
exec java -jar meow.jar --meow.seed-demo=true
