#!/usr/bin/env bash
# Runs the ore-wear self test (OreBreakSelfTest) in a headless server: first run "main,save", then the server is restarted for "load".
# usage: tools/worldscan/oretest.sh [modJar]        output: tools/worldscan/out/oretest/result_1.txt, result_2.txt, server_1.log, server_2.log
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
modjar="${1:-$root/build/libs/IslandCraft-DunesOfArrakis-0.1.0.jar}"
JAVA_BIN="${JAVA_HOME:-$HOME/.jdks/loom-ea-25-loom+1-11}/bin"
GAME="$APPDATA/Hytale/install/release/package/game/latest"
JAR="$GAME/Server/HytaleServer.jar"
SAVE="${SAVE_TEMPLATE:-$APPDATA/Hytale/UserData/Saves/Arrakis v1}"
srv="$here/out/oretest"; rm -rf "$srv"; mkdir -p "$srv/mods" "$srv/universe/worlds/default" "$srv/logs"
cp "$modjar" "$srv/mods/"
node -e "
const fs=require('fs');
const w=JSON.parse(fs.readFileSync(process.argv[1]+'/universe/worlds/default/config.json','utf8'));
w.Seed=42; w.WorldGen={Type:'Dunes_of_Arrakis'}; w.IsSpawningNPC=false; w.IsSpawnMarkersEnabled=false; w.IsBlockSpawnersEnabled=false;
fs.writeFileSync(process.argv[2]+'/universe/worlds/default/config.json',JSON.stringify(w,null,2));
const c=JSON.parse(fs.readFileSync(process.argv[1]+'/config.json','utf8')); c.Mods={}; fs.writeFileSync(process.argv[2]+'/config.json',JSON.stringify(c,null,2));
" "$SAVE" "$srv"
cd "$srv"
run() { # run number, phases
  "$JAVA_BIN/java" -Xmx4g -Darrakis.oretest="$2" -Darrakis.oretest.out="$(cygpath -w "$srv/result_$1.txt")" \
    -jar "$(cygpath -w "$JAR")" --assets "$(cygpath -w "$GAME/Assets.zip")" --auth-mode offline --bind "127.0.0.1:${PORT:-5598}" --disable-sentry --disable-file-watcher \
    --universe "$(cygpath -w "$srv/universe")" >"$srv/server_$1.log" 2>&1 &
  pid=$!
  for i in $(seq 1 240); do [ -f "$srv/result_$1.txt" ] && break; kill -0 $pid 2>/dev/null || break; sleep 2; done
  # the server stops itself after the test; give it time to save, then make sure it is gone
  for i in $(seq 1 60); do kill -0 $pid 2>/dev/null || break; sleep 2; done
  kill $pid 2>/dev/null || true; sleep 1; kill -9 $pid 2>/dev/null || true
  echo "---- run $1 ($2)"; cat "$srv/result_$1.txt" 2>/dev/null || { echo "no result"; tail -20 "$srv/server_$1.log"; }
}
run 1 "main,save"
run 2 "load"
echo "---- warnings or errors from the rule in the logs:"
sed 's/\x1b\[[0-9;]*m//g' server_1.log server_2.log | grep -E "SEVERE|OreBreak|Arrakis_OreBreaks|Exception" | grep -v Reallocate | head -20
