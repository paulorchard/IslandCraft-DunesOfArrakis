#!/usr/bin/env bash
# try.sh <name> '<json params>' [seed cx cz]   -> prints the headline measurements
cd "$(dirname "$0")/../.."
name="$1"; cfg="$2"; seed="${3:-1791436655886}"; cx="${4:-640}"; cz="${5:--480}"
echo "=== $name $cfg"
CAVE_PARAMS="$cfg" bash tools/preview/runcave.sh "$name" "$seed" "$cx" "$cz" 2>&1 | grep -E "^rules|rock body|networks:|deepest|natural openings|tops:|networks with|evaluated"
