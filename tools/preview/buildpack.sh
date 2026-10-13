#!/usr/bin/env bash
# buildpack.sh <packRoot>: a scratch pack from the pre-round assets with the continent and the caves applied (what src/main/resources will hold)
here="$(cd "$(dirname "$0")" && pwd)"; pack="$1"
rm -rf "$pack"; mkdir -p "$pack/Server"
cp -r "$here/out/pre-caves-HytaleGenerator-backup" "$pack/Server/HytaleGenerator"
if [ -z "$SKIP_CONTINENT" ]; then node "$here/continent.js" "$(cygpath -w "$pack")" || exit 1; else export NO_CONTINENT=1; fi
node "$here/caves.js" "$(cygpath -w "$pack")" | tail -1
