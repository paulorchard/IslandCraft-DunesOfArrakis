#!/usr/bin/env bash
# cost.sh <seed> <jarLabel...>: ms per 32x32 column for open desert, a random island and the class D mass, one jar after another.
# jars are tools/worldscan/out/jars/<label>.jar; the places are for seed 1791436655886 (the Arrakis v20 seed) and the continent ellipse file.
here="$(cd "$(dirname "$0")" && pwd)"; seed="$1"; shift
for label in "$@"; do
  for place in "desert 1984 15968" "island 22912 -6176" "classD 17216 -17216"; do
    set -- $place
    r=$(bash "$here/scan.sh" "cost_${label}_$1" "$seed" "$2" "$3" 256 256 "$here/out/jars/$label.jar" 2>&1 | grep -E "load_ms" )
    echo "$label $1 (x $2, z $3, 256 x 256): $r"
  done
done
