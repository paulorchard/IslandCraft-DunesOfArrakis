#!/usr/bin/env bash
# Whole-world render with the game's generator: coarse.sh <packRoot> <seed> <out.png> [sizeMetres=50000] [metresPerPixel=100] [centreX=0] [centreZ=0]
# env: ELLIPSES (outline json), PICK (density export to colour by)
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
JAVA="${JAVA_HOME:-$HOME/.jdks/loom-ea-25-loom+1-11}/bin/java"
JAR="$APPDATA/Hytale/install/release/package/game/latest/Server/HytaleServer.jar"
pack="$1"; seed="$2"; out="$3"; size="${4:-50000}"; res="${5:-100}"; cx="${6:-0}"; cz="${7:-0}"
COARSE=1 "$JAVA" -Xmx8g -cp "$(cygpath -w "$JAR")" "$(cygpath -w "$here/RealPreview.java")" "$(cygpath -w "$pack")" "$seed" "$(cygpath -w "$out")" "$cx" "$cz" "$size" "$res" >"$out.stdout" 2>&1 || { tail -8 "$out.log"; exit 1; }
tail -3 "$out.log"
