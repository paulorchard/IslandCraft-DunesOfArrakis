#!/usr/bin/env bash
# isl_try.sh <name> '<json params>' <seed> <cx> <cz> <size>   builds a scratch cave pack with CAVE_PARAMS and measures every large island in the square
here="$(cd "$(dirname "$0")" && pwd)"; cd "$here/../.."
name="$1"; cfg="$2"; seed="$3"; cx="$4"; cz="$5"; size="$6"
scratch="${TMPDIR:-$TEMP}/pk_$name"; rm -rf "$scratch"; mkdir -p "$scratch/Server"
cp -r "$here/out/pre-caves-HytaleGenerator-backup" "$scratch/Server/HytaleGenerator"
CAVE_PARAMS="$cfg" node "$here/caves.js" "$(cygpath -w "$scratch")" >/dev/null || exit 1
echo "=== $name $cfg  seed $seed  ($cx, $cz) $size"
LARGE_MIN="${LARGE_MIN:-8}" ISLANDS=1 bash "$here/probe.sh" "$scratch" "$seed" "$cx" "$cz" "$size" "$here/out/caves/isl_$name" | grep -vE "^loaded|^built|^large-island"
