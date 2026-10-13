#!/usr/bin/env bash
# Stage 3 renders and statistics for one seed: stage3_renders.sh <packRoot> <seed> <outDir>
here="$(cd "$(dirname "$0")" && pwd)"; pack="$1"; seed="$2"; out="$3"; mkdir -p "$out"
E="$(cygpath -w "$here/../continent-ellipses.json")"
DUMP="$(cygpath -w "$out/s3_50m.bin")" bash "$here/coarse.sh" "$pack" "$seed" "$out/s3_50m.png" 50000 50 | grep coarse
node "$here/islandstats.js" "$out/s3_50m.bin" > "$out/islandstats.txt"
DUMP="$(cygpath -w "$out/s3_70km.bin")" ELLIPSES="$E" bash "$here/coarse.sh" "$pack" "$seed" "$out/s3_wide70km.png" 70000 50 | grep coarse
node "$here/islandstats.js" "$out/s3_70km.bin" 70000 50 | tail -4 > "$out/beyond_square.txt"
bash "$here/coarse.sh" "$pack" "$seed" "$out/arrakis-world-50km-5000px.png" 50000 10 | grep coarse
echo done
