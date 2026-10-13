#!/usr/bin/env bash
# Cave probe with the game's generator classes.
# usage: tools/preview/probe.sh <packRoot> <seed> <centreX> <centreZ> <sizeMetres> <outPrefix>     (env: DUMP, CMP, YMAX, LARGE_MIN)
set -e
here="$(cd "$(dirname "$0")" && pwd)"
JAVA="${JAVA_HOME:-$HOME/.jdks/loom-ea-25-loom+1-11}/bin/java"
JAR="$APPDATA/Hytale/install/release/package/game/latest/Server/HytaleServer.jar"
pack="$1"; seed="$2"; cx="$3"; cz="$4"; size="$5"; prefix="$6"
"$JAVA" -Xmx8g -cp "$(cygpath -w "$JAR")" "$(cygpath -w "$here/CaveProbe.java")" "$(cygpath -w "$pack")" "$seed" "$cx" "$cz" "$size" "$(cygpath -w "$prefix")" >"$prefix.stdout" 2>&1 || { tail -15 "$prefix.txt"; exit 1; }
cat "$prefix.txt"
