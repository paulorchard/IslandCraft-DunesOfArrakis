#!/usr/bin/env bash
# islands.sh <packRoot> <seed> <centreX> <centreZ> <sizeMetres> <outPrefix>   measures every large island found in the square (env: LARGE_MIN, MIN_HA, YMAX)
cd "$(dirname "$0")/../.."
LARGE_MIN="${LARGE_MIN:-8}" ISLANDS=1 bash tools/preview/probe.sh "$@"
