#!/usr/bin/env bash
# Real-engine scan: boots a headless server in a scratch folder with the Dunes of Arrakis generator, generates a block of the world with the
# game's own generator (blocks, materials, props), dumps every block to a file, stops the server.
# usage: tools/worldscan/scan.sh <name> <seed> <minX> <minZ> <nx> <nz> [modJar]      (blocks; minX, minZ, nx, nz multiples of 32)
# env: YMAX (default 200), PORT (default 5599), SCAN_BATCH
# output: tools/worldscan/out/<name>/scan.bin (+ server.log, scan.bin.done)
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
name="$1"; seed="$2"; minX="$3"; minZ="$4"; nx="$5"; nz="$6"; modjar="${7:-$root/build/libs/IslandCraft-DunesOfArrakis-0.1.0.jar}"
JAVA_BIN="${JAVA_HOME:-$HOME/.jdks/loom-ea-25-loom+1-11}/bin"
GAME="$APPDATA/Hytale/install/release/package/game/latest"
JAR="$GAME/Server/HytaleServer.jar"
work="$here/out"; srv="$work/$name"; mkdir -p "$work/classes"
if [ ! -f "$work/WorldScan.jar" ] || [ "$here/WorldScanPlugin.java" -nt "$work/WorldScan.jar" ]; then
  rm -rf "$work/classes"; mkdir -p "$work/classes"
  "$JAVA_BIN/javac" -cp "$(cygpath -w "$JAR")" -d "$(cygpath -w "$work/classes")" "$(cygpath -w "$here/WorldScanPlugin.java")"
  cp "$here/manifest.json" "$work/classes/manifest.json"
  (cd "$work/classes" && "$JAVA_BIN/jar" cf "$(cygpath -w "$work/WorldScan.jar")" .)
fi
rm -rf "$srv"; mkdir -p "$srv/mods" "$srv/universe/worlds/default" "$srv/logs"
cp "$work/WorldScan.jar" "$srv/mods/"; cp "$modjar" "$srv/mods/"
SAVE="${SAVE_TEMPLATE:-$APPDATA/Hytale/UserData/Saves/Arrakis v1}"
node -e "
const fs=require('fs');
const w=JSON.parse(fs.readFileSync(process.argv[1]+'/universe/worlds/default/config.json','utf8'));
w.Seed=Number(process.argv[3]); w.WorldGen=process.env.WS?{Type:'Dunes_of_Arrakis',WorldStructure:process.env.WS}:{Type:'Dunes_of_Arrakis'}; w.IsSpawningNPC=false; w.IsSpawnMarkersEnabled=false; w.IsBlockSpawnersEnabled=false;
fs.writeFileSync(process.argv[2]+'/universe/worlds/default/config.json',JSON.stringify(w,null,2));
const c=JSON.parse(fs.readFileSync(process.argv[1]+'/config.json','utf8')); c.Mods={}; fs.writeFileSync(process.argv[2]+'/config.json',JSON.stringify(c,null,2));
" "$SAVE" "$srv" "$seed"
rm -f "$srv/scan.bin" "$srv/scan.bin.done"
cd "$srv"
"$JAVA_BIN/java" -Xmx6g -Dscan.minX="$minX" -Dscan.minZ="$minZ" -Dscan.nx="$nx" -Dscan.nz="$nz" -Dscan.yMax="${YMAX:-200}" ${SPAWN:+-Dscan.spawn=1} -Dscan.out="$(cygpath -w "$srv/scan.bin")" ${SCAN_BATCH:+-Dscan.batch=$SCAN_BATCH} \
  -jar "$(cygpath -w "$JAR")" --assets "$(cygpath -w "$GAME/Assets.zip")" --auth-mode offline --bind "127.0.0.1:${PORT:-5599}" --disable-sentry --disable-file-watcher --universe "$(cygpath -w "$srv/universe")" >"$srv/server.log" 2>&1 &
pid=$!
until [ -f "$srv/scan.bin.done" ] || ! kill -0 $pid 2>/dev/null; do sleep 3; done
cat "$srv/scan.bin.done" 2>/dev/null || { echo "server exited without a result"; tail -20 "$srv/server.log"; }
kill $pid 2>/dev/null; sleep 2; kill -9 $pid 2>/dev/null || true
grep -E "WorldScan|SEVERE|Exception" "$srv/server.log" | grep -v Reallocate | tail -12
