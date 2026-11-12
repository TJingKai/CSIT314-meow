#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh mvnw clean verify
cp target/meow.jar meow.jar
printf '%s\n' 'Build complete. Run sh start.sh to launch the new version.'
