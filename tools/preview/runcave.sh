#!/usr/bin/env bash
# One experiment: rebuild the cave pack in a scratch folder with CAVE_PARAMS, then probe one island.
# usage: [CAVE_PARAMS='{...}'] tools/preview/runcave.sh <name> <seed> <cx> <cz> [size]
here="$(cd "$(dirname "$0")" && pwd)"; name="$1"; seed="$2"; cx="$3"; cz="$4"; size="${5:-1000}"
scratch="${TMPDIR:-$TEMP}/pk_$name"; rm -rf "$scratch"; mkdir -p "$scratch/Server"
cp -r "$here/out/pre-caves-HytaleGenerator-backup" "$scratch/Server/HytaleGenerator"
node "$here/caves.js" "$(cygpath -w "$scratch")" >/dev/null || exit 1
export LARGE_MIN=$(node -e "const p=Object.assign({largeInsideMin:2},JSON.parse(process.env.CAVE_PARAMS||'{}'));console.log(p.largeInsideMin)")
dump="${TMPDIR:-$TEMP}/base_${seed}_${cx}_${cz}_${size}.bin"
if [ ! -f "$dump" ]; then
  base="${TMPDIR:-$TEMP}/pk_base"; if [ ! -d "$base" ]; then mkdir -p "$base/Server"; cp -r "$here/out/pre-caves-HytaleGenerator-backup" "$base/Server/HytaleGenerator"; fi
  DUMP="$(cygpath -w "$dump")" bash "$here/probe.sh" "$base" "$seed" "$cx" "$cz" "$size" "$here/out/caves/base_${seed}_${cx}_${cz}" >/dev/null
fi
CMP="$(cygpath -w "$dump")" bash "$here/probe.sh" "$scratch" "$seed" "$cx" "$cz" "$size" "$here/out/caves/${name}"
