# IslandCraft - Dunes of Arrakis

## Naming convention

- Family name `IslandCraft - <mod name>`; manifest group `IslandCraft`.
- Identifier form `<Mod_Name_With_Underscores>` (here `Dunes_of_Arrakis`) for generator types, world structures and the mod's asset folders.
- Code form `<ModNameNoSpaces>` (here `DunesOfArrakis`); package `com.paulorchard.islandcraft.<modnamelowercase>`; jar and repository `IslandCraft-<ModNameNoSpaces>`.
- In-world content is prefixed `Arrakis_` (blocks, biomes, exported densities); environments are `Env_Arrakis...`; no abbreviated asset prefixes.
- Seed keys are not asset IDs. They are left as first written, because renaming one changes the terrain.
- Projects live under `C:\Apps\IslandCraft\`.

Entries further down are left as written and use the old names. Look them up in the table below.

## Rename table (commit 973ff2f)

| Old | New | Where |
| --- | --- | --- |
| Manifest Group `Arrakis` | `IslandCraft` | `manifest.json` |
| Manifest Name `Arrakis` | `IslandCraft - Dunes of Arrakis` | `manifest.json` |
| Mod identifier `Arrakis:Arrakis` | `IslandCraft:IslandCraft - Dunes of Arrakis` | logs, save configs |
| Generator type `Arrakis` | `Dunes_of_Arrakis` | provider class, each world's config |
| World structure `Arrakis` | `Dunes_of_Arrakis` | `WorldStructures/Dunes_of_Arrakis.json`, provider default, preview tools |
| World structures `Arrakis_Mix`, `Arrakis_Showcase` | deleted | were in `WorldStructures/` |
| Biomes `AM_*` (12), `AS_*` (13) and their exports | deleted | were in `Biomes/Arrakis_Mix/`, `Biomes/Arrakis_Showcase/` |
| `tools/showcase/` | deleted | build scripts, `Use-Showcase.ps1`, two images |
| Exports `ArrakisV1` .. `ArrakisV10` | `Arrakis_V1` .. `Arrakis_V10` | main biome, `graph.js` |
| Exports `ArrakisV<n><letter>_...` | `Arrakis_V<n><letter>_...` | main biome, `graph.js` |
| Exports with an empty name (copied from vanilla recipes) | `Arrakis_V<n><letter>_Unnamed<k>` | main biome, `graph.js` |
| Folder `Biomes/Arrakis/` | `Biomes/Dunes_of_Arrakis/` | assets |
| Folder `Environments/Arrakis/` | `Environments/Dunes_of_Arrakis/` | assets |
| `Env_Arrakis` had no tags | tag `Arrakis` added | `Env_Arrakis.json` |
| Package `com.paulorchard.arrakis` | `com.paulorchard.islandcraft.dunesofarrakis` | Java |
| `ArrakisPlugin`, `ArrakisWorldGenProvider`, `ArrakisWorldGen`, `ArrakisSpawnProvider` | `DunesOfArrakisPlugin`, `DunesOfArrakisWorldGenProvider`, `DunesOfArrakisWorldGen`, `DunesOfArrakisSpawnProvider` | Java |
| Gradle group `com.paulorchard` | `com.paulorchard.islandcraft` | `build.gradle.kts` |
| Project name `DuneCraft-WorldGen` | `IslandCraft-DunesOfArrakis` | `settings.gradle.kts` |
| Jar `Arrakis-0.1.0.jar` | `IslandCraft-DunesOfArrakis-0.1.0.jar` | `build.gradle.kts`; the deploy task also removes old `Arrakis-*` files |

Unchanged: `Arrakis_Sand`, `Arrakis_Terrain`, `Env_Arrakis`, the exports `Arrakis_Rock`, `Arrakis_Pick`, `Arrakis_Pick_Start` and `Arrakis_Inside`, the language key `items.Arrakis_Sand.name`, every seed key (34 start with `Arrakis_`; 9 are `ArrakisV<n>_Patch<k>`; 23 are copied from vanilla recipes), and the vanilla export `Plains1_Caves_Terrain` that the rock recipes import.

Checked after the rename:

- Two real-generator renders (seed 42 round spawn; seed 1791463709213 at 6 km north) are byte-identical to renders made before it.
- Clean build and deploy: one jar for this mod in the Mods folder.
- Three "Duplicate export name" warnings remain. They come from the vanilla Gorges recipe using one export name twice.

Not yet checked in game: the mod-list name. It was chosen from the client's list template (`ModItem.ui`), which has a name label and an authors label and no group label, so the name alone has to carry "IslandCraft - ". Also unchecked: how worlds made before the rename fail to load.

# Experiment log (entries use the old names)

Biome file: `src/main/resources/Server/HytaleGenerator/Biomes/Arrakis/Arrakis_Terrain.json`
Server version: 0.6.8

## Iteration loop

- `gradlew deployMod`, then create a fresh world from the Create World screen (plugin from prompt 01 makes it Arrakis).
- The game locks the jar in the Mods folder while a world is open. Exit to the main menu (or quit) before `deployMod`.
- Each new world has a random seed, so island positions differ per world. Positions below are ranges, not exact.
- Live editing with `/viewport --radius 3`: not tried yet. The pack now ships as a plugin jar, not a folder in the save's mods directory.
- Preview tool (round 2): `tools/preview` is a small Node model of the density nodes this biome uses. `graph.js` builds the rock graph from parameters, `render.js` draws a shaded top-down PNG of it, `write.js` writes the same graph into the biome JSON. The noise hash differs from the game's, so exact shapes differ, but scales, ranges and graph logic are the same. Use it to judge shape before deploying: `node tools/preview/render.js out.png 900 900 1200 1.5`, then `node tools/preview/write.js <biome json>`.
- Offline preview (loading the biome with the server jar outside the game): dead end. The generator's asset stores need a live `HytaleServer` instance.

## Engine facts confirmed from the 0.6.8 server jar

- `Manual` curves clamp outside their point range (first/last `Out` is returned). No extrapolation.
- `PositionsCellNoise` with no position inside `MaxDistance` passes a huge distance to the return type, so a `Curve` return type yields the curve's last `Out`. The last curve point must be the "nothing here" value.
- `Occurrence` keeps a position when `FieldFunction` value > random [0,1). The value is a keep probability: 0.7 keeps 70%.
- `Mesh2D` / `Mesh` point generator: one point per grid cell at `cell * Scale`, displaced by a random vector times `Jitter * Scale`. With `Jitter` 0 there is a point exactly at the origin.
- `PositionsCellNoise` `Density` return type: `ChoiceDensity` is sampled at the cell position; the first delimiter with `From <= value < To` wins; otherwise `DefaultValue`. The chosen density runs with the cell position as its anchor.
- `WhiteNoise` is uniform in [-1, 1).
- `Anchor` gives its input the position relative to the cell position.
- `Ellipsoid` = `Rotator(Scale(Distance(Curve)))`: `Scale` is the semi-axis length in metres, the curve input is 1.0 at the surface, `Spin` is in degrees.
- `Distance` outside an `Anchor` measures from the world origin.
- Material `Queue` falls through to the next entry when a `FieldFunction` material has no matching delimiter.

## Stage 1 — large islands only, plain Rock_Sandstone_Red

Status: worked first time. Log clean (no Arrakis warnings, no `Took too long`).

Setup:

- Terrain density is now `Max(sand floor, dunes, Rock)`. The two dune branches are unchanged.
- `Rock = Exported "Arrakis_Rock" [ Sum(A, B) ]`
  - `A = Clamp(-3, 1) [ 8 * F ]`
  - `F = YOverride 0 > Cache 3 > Sum(radial, coast noise)`
    - radial: `PositionsCellNoise`, `MaxDistance` 450, `Curve` return 0 m -> 1.0, 450 m -> -0.5 (crosses 0 at 300 m), Euclidean.
    - positions: `Offset(900, 0, 900)` > `Occurrence(0.7)` > `Mesh2D` scale 2500, jitter 0.2.
    - coast noise: `SimplexNoise2D` scale 300, 3 octaves, normalised to +/-0.3 (shore moves about +/-90 m).
  - `B = CurveMapper(BaseHeight distance)`: -60 -> 1, 0 -> 0, 240 -> -4 (so -1 at 60 m above Base).
  - Result: plateau 60 m above Base where `F >= 0.125`, sloping to Base level at the coastline `F = 0`.
- Material: `FieldFunction(Imported "Arrakis_Rock")`, delimiter 0..100 -> `Rock_Sandstone_Red`, placed after bedrock and before sand in the `Solid` queue.

Numbers:

| Setting | Value |
| --- | --- |
| Island grid spacing | 2500 m |
| Jitter | 0.2 (centres move up to 500 m) |
| Drop rate | 30% |
| Nominal radius | 300 m (about 210–390 m with coast noise) |
| Plateau height (stage 1 only) | 60 m above Base (y = 140) |
| Grid offset | (900, 900) |

Where to look: island centres fall within 500 m of (900 + 2500 i, 900 + 2500 j). Nearest candidates to spawn: (900, 900), (-1600, 900), (900, -1600), (-1600, -1600). Each has a 30% chance of being dropped.

Spawn: the offset keeps the nearest possible island edge at least ~380 m from the origin.

Feedback (world "Arrakis Test"): plateau found and looks as described: flat top, steep sides, irregular outline, dunes untouched. No comment yet on size or spacing. User expected small outcrops already.

## Stages 2 + 3 — cliffs, uneven tops, small outcrops everywhere

Status: built, not yet deployed or seen in game. Done together because the user asked for outcrops straight after stage 1.

Setup (replaces the stage 1 `A` and `B`; density 1.0 now means 90 m):

- `Rock = Exported "Arrakis_Rock" [ Sum(A, B) ]`
- `A = YOverride 0 > Cache 3 > Min(spawnMask, Sum(Max(island, outcrop), duneHeight))`
  - `spawnMask`: `Distance` curve 0..70 m -> -3, 90 m -> 5. Keeps rock off the fixed spawn.
  - `duneHeight`: copy of the dune noise (`SimplexNoise2D` scale 400, 4 octaves, seed "A"), normalised to -0.356..0.533 and clamped 0..0.45. Rock heights are therefore measured from the local sand surface, not from Base. Assumes the same seed key gives the same noise as the dune branch — check in game: low outcrops should sit a few metres above the sand everywhere.
  - `island = Min(Clamp(-3, 2)[25 * F], top)`. `F` is unchanged from stage 1. Full height is reached about 12 m inside the coastline.
    - `top = CurveMapper(SimplexNoise2D scale 220, 2 octaves) + rough`. Stepped curve: -1 -> 0.35, -0.3 -> 0.38, -0.2 -> 0.58, 0.15 -> 0.62, 0.25 -> 0.85, 0.5 -> 0.9, 0.6 -> 1.0. Shelves at about 32, 54, 79 and 90 m.
    - `rough`: `SimplexNoise2D` scale 35, 2 octaves, +/-0.03 (about +/-3 m).
  - `outcrop`: `PositionsCellNoise`, `MaxDistance` 130, `Mesh2D` scale 200 jitter 0.4, `Density` return type, `ChoiceDensity` `WhiteNoise`, `DefaultValue` -3.
    - each variant: `Min(Clamp(-3, 2)[4 * (Anchor[Ellipsoid] + edge noise)], top + rough)`
    - `Ellipsoid` curve 0 -> 1, 1 -> 0, 4 -> -3; `Scale` (semi-major, 1, semi-minor); `NewYAxis` (0, 1, 0); `Spin` per variant.
    - edge noise: `SimplexNoise2D` scale 40, 2 octaves, +/-0.25.
- `B = CurveMapper(BaseHeight distance)`: -90 -> 1, 0 -> 0, 360 -> -4.

Outcrop variants (65% of cells get nothing):

| White noise range | Size (m) | Spin | Height above sand |
| --- | --- | --- | --- |
| -1.00 .. -0.90 | 140 x 44 | 0 | 4 m |
| -0.90 .. -0.80 | 170 x 56 | 60 | 6 m |
| -0.80 .. -0.72 | 110 x 36 | 120 | 3 m |
| -0.72 .. -0.64 | 150 x 50 | 30 | 18 m |
| -0.64 .. -0.56 | 180 x 60 | 100 | 25 m |
| -0.56 .. -0.48 | 120 x 48 | 150 | 14 m |
| -0.48 .. -0.42 | 130 x 44 | 80 | 40 m |
| -0.42 .. -0.36 | 160 x 52 | 15 | 54 m |
| -0.36 .. -0.30 | 60 x 40 | 135 | 60 m |

Where to look: outcrops are everywhere at this stage, on a 200 m grid with about one cell in three filled, so several should be visible from spawn. Large islands as in stage 1.

Feedback (world "New World"): pipeline works (positions, export/import, material, dunes untouched, spawn clear, log clean). Shape rejected: the large island is one flat-topped slab with vertical walls all round, cannot be climbed, nothing on top. Round 2 replaces the shapes.

# Round 2 — terraced clusters (targets: Dune Awakening maps)

The eight target points: 1 cluster not blob, 2 terraced, 3 lopsided wedges with a shared lean, 4 full size range fading with distance, 5 varied summit heights, 6 spire groups, 7 sand and rock interleave, 8 layered and weathered up close.

Density 1.0 in the A terms is now 120 m.

## Round 2, stage 1 — large island as a terraced cluster

Status: accepted. Log clean (world "Arrakis v2").

Setup (island branch only; outcrops, spawn mask, dune-height add and material are unchanged apart from the unit rescale):

- `F = Sum(radial, lobes, detail)`
  - radial: `PositionsCellNoise`, `MaxDistance` 600, `Curve` 0 m -> 1, 300 m -> 0, 600 m -> -1. Same island positions as round 1.
  - lobes: `SimplexNoise2D` scale 230, 2 octaves, +/-0.62. This is what breaks the outline into bays, peninsulas and detached pieces.
  - detail: `SimplexNoise2D` scale 60, 2 octaves, +/-0.05.
- `edge = F * 110 m`: height grows gently inland instead of jumping to a plateau.
- `cap = CurveMapper(SimplexNoise2D scale 210, 3 octaves)`: -1 -> 10 m, -0.35 -> 14, -0.1 -> 30, 0.15 -> 54, 0.45 -> 90, 0.7 -> 110. Low benches and separate summits.
- `preStep = Min(edge, cap) + noise(scale 28, +/-1 m)`
- `island = CurveMapper(preStep)` with a stair-step curve. Shelves at 0, 3, 8, 14, 20, 29, 35, 44, 50, 58, 67, 73, 82, 90, 98, 106, 112, 120 m. Each tread rises 12% of its step, then a riser takes the last 15% of the input range. Intervals 0-3, 35-44 and 73-82 m are left as plain slopes.

What did not work in the preview:

- `edge = F * 260 m` with lobes scale 170 +/-0.45 and detail scale 45 +/-0.12: slope about 2 m per m, so treads were about 3 m wide and the terraces vanished; the coast was a ragged fringe. Fix was a much gentler edge (110 m) and broader, stronger lobes with almost no fine detail.

Against the eight points:

1. Cluster: partly. Outline is deeply indented with peninsulas and a few detached pieces; no sand channels through the mass yet (stage 2).
2. Terraced: yes in the preview. Shelves 3-9 m high, treads wide on gentle ground and narrow on steep ground.
3. Wedges with a shared lean: no (stage 3).
4. Size range and fade with distance: no (stage 3). Outcrops are still the round 1 ellipses, everywhere.
5. Summit heights: yes in the preview. Several summits up to about 105 m with lower benches.
6. Spires: no (stage 4).
7. Sand and rock interleave: partly. Rock starts flush with the sand and dunes bank against it; no interior sand basins yet.
8. Layered and weathered: no (stage 5). Still plain Rock_Sandstone_Red.

Play requirements: access not guaranteed yet (shelf risers are 3-9 m; the three plain-slope bands are about 45 degrees). Building room: likely, wide treads on the benches. Shelter: no. Stage 2 covers these.

Feedback (world "Arrakis v2"): "the large islands are really good". The small outcrops were still the round 1 flat ellipses and read as plateaus, so stage 3 was done next, ahead of stage 2.

## Round 2, stage 3 — wedge outcrops with a shared lean, fading with distance

Status: accepted (world "Arrakis v3", log clean). Done before stage 2 (channels, basins, access) because of the feedback above.

Engine facts used (from the 0.6.8 jar):

- `XValue` returns the context position's X. Inside an `Anchor` that is the X offset from the cell position.
- `Rotator` takes `NewYAxis` and `SpinAngle` (degrees) and rotates the position passed to its input. `Ellipsoid` uses the same rotator internally, so one `Rotator` around the whole wedge keeps footprint and ramp aligned.

Setup:

- Round 1 ellipse outcrops removed. Two new `PositionsCellNoise` layers with `Density` return type:
  - Medium: `Mesh2D` scale 320, jitter 0.35, `MaxDistance` 180.
  - Small: `Mesh2D` scale 130, jitter 0.4, `MaxDistance` 80.
- Each variant: `Anchor > Rotator(SpinAngle = 30 + offset) > Min(rim, ramp)`
  - rim: `3 * crest * (Ellipsoid + edge noise)`. `Ellipsoid` curve 0 -> 1, 1 -> 0, 3 -> -2; `Scale` (semi-length, 1, semi-width). Edge noise `SimplexNoise2D` scale 0.45 * semi-length, +/-0.28. Note: this noise is sampled in the wedge's local coordinates, so two wedges of the same variant have the same outline wobble.
  - ramp: `CurveMapper(XValue)`: -1.1 * semi-length -> 0, +0.55 * semi-length -> crest. Gentle rise along local +X, then the rim cuts the far end off as a cliff.
- Shared lean: world-wide angle 30 degrees, each variant offset by -25..+25.
- Fade and chains: `ChoiceDensity = WhiteNoise + shift`, where shift is a second `PositionsCellNoise` on the island positions with a `Curve` return: 0 inside `fadeStart`, rising to the layer's total fill at `fadeEnd`, then 3. Variant ranges start at -1 with the biggest first, so as the shift grows the biggest wedges drop out first and nothing survives past `fadeEnd`. Medium: 380 -> 820 m from the island centre. Small: 350 -> 900 m.
- Terracing is now shared: `CurveMapper(stairs)[ Max(island, medium, small) + jitter ]`, so wedges get the same shelves as the island.

Variants (share of cells near the island, length x width, lean offset, crest above sand):

| Layer | Share | Size (m) | Lean offset | Crest |
| --- | --- | --- | --- | --- |
| Medium | 12% | 250 x 140 | -10 | 45 m |
| Medium | 12% | 220 x 110 | +20 | 30 m |
| Medium | 10% | 200 x 160 | 0 | 60 m |
| Medium | 10% | 180 x 90 | -25 | 22 m |
| Medium | 8% | 150 x 100 | +10 | 14 m |
| Small | 10% | 120 x 60 | +15 | 25 m |
| Small | 9% | 100 x 70 | -20 | 12 m |
| Small | 8% | 90 x 44 | 0 | 35 m |
| Small | 6% | 50 x 36 | +25 | 55 m (stack) |
| Small | 9% | 110 x 56 | -10 | 5 m |
| Small | 7% | 70 x 40 | +10 | 8 m |
| Small | 6% | 40 x 28 | 0 | 4 m |

What did not work in the preview:

- First pass wrote the variant ranges as if white noise spanned 1.0; it spans 2.0 (-1..1), so only half the intended cells were filled. Ranges are now generated from shares.
- Edge noise +/-0.15 left the outlines as clean ellipses; +/-0.28 reads as rock.

Against the eight points:

1. Cluster: partly (unchanged; wedges that land on the island add lobes).
2. Terraced: yes for islands and wedges in the preview.
3. Wedges with a shared lean: yes in the preview. WITHDRAWN in round 4: in game the wedges read as repeated single tilted planes with parallel stripes; round 4 part B replaces them with scaled-down islands and no shared lean.
4. Size range and fade: yes in the preview: 600 m island, 150-250 m medium, 40-120 m small, none beyond about 600 m from the shore.
5. Summit heights: yes for the island; wedge crests 4-60 m, including a tall narrow stack on a small footprint.
6. Spires: no (stage 4).
7. Sand and rock interleave: partly. Low wedges (4-8 m) sit just above the sand and dunes cover their low ends.
8. Layered and weathered: no (stage 5).

Play requirements: most wedges have a walk-up side in principle, but the ramp is terraced with 3-9 m risers, so true walk-up access is still stage 2 work. Hops: sand gaps in the chain are mostly under 200 m in the preview.

Feedback (world "Arrakis v3"): islands and outcrops look good above ground. Round 2 stages 2, 4 and 5 (channels, spires, strata) were not done; round 3 replaces the strata plan.

# Round 3 — rock below the sand, and what the rock is made of

No shape above the sand may change in this round.

## Round 3, part A — rock stays isolated underground

Status: accepted (world "Arrakis v4", log clean, no `Took too long`).

Confirmed cause. `Rock = A + B`. `A = terraced height + dune height` (units of 120 m); `B` is 0 at Base and keeps rising below it, to +0.67 at bedrock. Underground the rock boundary is therefore wherever `A > -B`, i.e. wherever the height field is above minus the depth. The stair-step curve passed negative heights straight through, and the height field falls away gently outside each body (island about 0.4-0.9 m per m, wedge rims 0.5-2 m per m), so every metre of depth moved the boundary 1-3 m outward and neighbouring bodies merged.

Why flattening `B` alone is not enough. Dune height is added inside `A` so that rock heights are measured from the sand. With `B` flat below Base, rock below Base would be wherever `terraced + dune > 0`, which under a 40 m dune still reaches out to where the height field is -40 m: up to about 100 m beyond the visible footprint.

Fix. One change, on the negative side of the stair-step `CurveMapper`: heights from 0 down to -4 m pass through as before; anything lower maps to -3 units, which no depth can bring back above zero. Points: `(-4.05 m, -360 m), (-4 m, -4 m), (0, 0)`, then the unchanged shelves. `B` is unchanged.

Result:

- Above the sand: identical (the curve is untouched for heights above 0).
- A body's root is the area where the height field is above -4 m, carried straight down to bedrock. Its sides are vertical, so it does not widen with depth at all.
- That area is wider than the visible footprint by 4 m divided by the local slope: about 2-8 m on wedge rims, up to about 13 m on the gentlest island shores. The strip between is a shelf buried 0-4 m under the sand.
- Bodies 40 m apart at the surface stay at least about 14 m apart underground.

Slice measurements (preview tool, 2400 x 2400 m around one island and its chain, 3 m cells):

| | Before | After |
| --- | --- | --- |
| Rock showing above the sand | 48.2 ha | 48.2 ha |
| Rock at Y = 40 | 111.3 ha (2.31 x) | 54.0 ha (1.12 x) |
| Rock at Y = 10 | 198.8 ha (4.12 x) | 54.0 ha (1.12 x) |

Images: `tools/preview/round3-underground-{before,after}_{y40,y10,vertical}.png`. Tool: `node tools/preview/slices.js <prefix> [rootCut|none]`.

Feedback: root fix accepted. Round 3 part B (white shell darkening inward) was dropped before anything was built; no `Rock_Sandstone_White` was ever added.

# Round 4 — materials by sand level, and outcrops as small islands

Accepted and frozen: large islands, dunes, island positions, chain fade, spawn mask, round 3 root fix.

## Round 4, part A — materials by sand level

Status: accepted with one change (world "Arrakis v5", log clean): the upper block was switched from `Rock_Sandstone` to `Rock_Sandstone_White` on request.

Engine fact (0.6.8 jar): `SimpleHorizontal` applies its material where `BottomY <= block Y < TopY`, each measured from its named base height. Outside that range it returns nothing and a surrounding `Queue` falls through.

Setup. The rock entry of the `Solid` queue is still `FieldFunction(Imported "Arrakis_Rock")` with delimiter 0..100, but its material is now a `Queue`:

1. `SimpleHorizontal`, `BottomY` -3 and `TopY` 1000, both on base height `Base` -> `Rock_Sandstone_White` (first deployed as `Rock_Sandstone`)
2. `Constant` -> `Rock_Sandstone_Red`

The sand floor's top block is Base - 1, so sandstone covers that block and the two below it (Y = 79, 78, 77 with Base = 80) and red starts at Y = 76. No number is hard-coded except the offsets; the rule follows `Base`.

Removed: nothing. The single `Rock_Sandstone_Red` constant was replaced by the queue above.

Feedback (world "Arrakis v5"): materials look good; change the upper sandstone to white sandstone. Done.

## Round 4, part B (medium layer) — outcrops as small islands

Status: FAILED in game (world "Arrakis v6"). Came out as 360 m discs filled edge to edge and cut along straight cell boundaries. Cause and repair are in round 5. The small layer still uses the round 2 wedges.

Problem seen in game (Screenshots/repettive outcrops.png): wedges are single tilted planes with parallel terrace stripes, same-variant wedges are identical, and the shared lean reads as a pattern.

Recipe. Same as the large island, scaled down, with the radius set per cell. No `Anchor`, no `Density` return type, no variants: every noise is sampled in world coordinates, so no two outcrops share an outline.

- Positions: `Mesh2D` scale 320 (unchanged), jitter lowered 0.35 -> 0.25 so neighbours are at least about 160 m apart and rarely clip each other along the straight cell boundary. `MaxDistance` 180.
- `v = WhiteNoise(cell) + shift(distance from island centre at the cell)`, shift 0 inside 380 m rising to 1.0 at 820 m, then 3 (same fade as round 2).
- `invRadius = CellValue[ CurveMapper(v) ]`: v from -1 to 0 maps radius 100 m down to 55 m (as 1/radius); above 0 it is 10, which empties the cell. Default 10. Half the cells near an island are filled; the biggest drop out first with distance.
- `distance`: `PositionsCellNoise` on the same positions, `Distance` return type. WRONG: this return type is not metres, see round 5.
- `F = 1 - invRadius * (distance + 2) + lobes + detail`. The +2 m stops an empty cell leaving a one-block spike at its centre.
  - lobes: `SimplexNoise2D` scale 75, 2 octaves, +/-0.4 (island: scale 230 for radius 300, ratio 0.77; here about 0.75-1.4 across the radius range).
  - detail: scale 22, +/-0.04.
- `cap = CurveMapper(SimplexNoise2D scale 60, 2 octaves)`: -1 -> 0.2, -0.2 -> 0.35, 0.2 -> 0.7, 0.6 -> 1.1. Gives one to three high points.
- `height = CellValue[ 0.36 * radius(v) * variety ]`, variety from a second white noise: -1 -> 0.4, 0 -> 0.9, 1 -> 1.5. So crests run from about 8 m to about 54 m.
- `outcrop = Min( height * Min(F, cap), 200 m * F )`. The second term only matters where `F < 0`: it makes the height fall away fast outside the footprint whatever the outcrop's height, so the round 3 root cut (-4 m) still leaves only a narrow buried shelf. Without it a low outcrop would have a shelf reaching far out.
- Then the shared `Max`, jitter and stair-step curve as before.

What did not work in the preview:

- Lobes scale 62 +/-0.5 with detail scale 16 +/-0.06: outlines crumbled into fragments. Scale 75 +/-0.4 with detail 22 +/-0.04 holds each outcrop together as one body with bays.

Limits of this recipe:

- Noise scale cannot follow the radius continuously (a noise node has one fixed scale), so it is set once per layer for the middle of the layer's radius range.
- Elongation comes only from the lobe noise; there is no deliberate stretch.
- Two filled neighbouring cells that reach each other meet along a straight cell boundary. The lower jitter makes this uncommon, not impossible.

Checks:

- Large island: the island branch of the biome JSON is byte-identical to the previous build. Outcrops that overlap the island shore differ, as they always have.
- Slices (2400 x 2400 m, 3 m cells): rock above the sand 43.7 ha, at Y = 40 49.2 ha (1.12 x), at Y = 10 49.2 ha (1.12 x).

Feedback (world "Arrakis v6"): medium outcrops are big circular discs about 350 m across with clean circular edges, some cut along dead-straight lines, filled with a busy terrace texture, crowding and overlapping the large island.

# Round 5 — outcrop repair, then a starting outcrop and a guaranteed island

Accepted and frozen: large islands, dunes away from spawn, the sand-level material rule, the underground root fix.

## Round 5, stage 1 — diagnose the discs, fix the preview, repair the medium layer

Status: loaded in game as "Arrakis v7" (log clean, no `Took too long`). No comment on the medium outcrops themselves; the feedback was that the spawn outcrop and nearby island were missing, which were later stages.

### Confirmed cause of the discs

It was not `CellValue`. From the 0.6.8 jar:

- `CellValue` does what round 4 assumed: it evaluates its `Density` with the position set to the closest cell position and returns that; with no position inside `MaxDistance` it returns `DefaultValue`.
- The `Distance` return type does NOT return metres. It returns `distance / MaxDistance * 2 - 1`, i.e. -1 at the cell position and +1 at `MaxDistance`, and 1 when there is no position.
- A `Curve` return type is the one that receives the raw distance in metres.

Round 4 computed `F = 1 - invRadius * (distance + 2)` with that normalised value, so the falloff term was about 0.01-0.05 instead of 0-2. `F` stayed near 1 across the whole lookup, and rock stopped only where the lookup stopped: a disc of radius `MaxDistance` (180 m), cut where a neighbouring cell was closer.

### Preview correction

- The Node model had the same wrong assumption; `engine.js` now normalises the `Distance` return type like the game.
- New: `tools/preview/RealPreview.java` runs the game's own generator classes from `HytaleServer.jar` outside the game. It loads the pack's biome and world structure through the real asset codecs, builds the terrain density with a real world seed, and renders a shaded top-down PNG plus underground rock areas. Run it with `tools/preview/real.sh <seed> <out.png> [centreX centreZ size metresPerPixel]`; it writes the current `graph.js` into a scratch copy of the pack first. About 2 seconds for 800 x 800 columns.
  - How it gets round the round 1 dead end: it sets the parsed options and a bare `HytaleServer` object with an event bus by reflection, which is all the asset stores need. Materials are skipped (block types are not loaded), so it follows `BasicWorldStructureAsset.build` and `BiomeAsset.build` by hand for the framework and terrain only.
  - Seed: the world's `Seed` from `universe/worlds/default/config.json`, cast to int, into `SeedBox`.
- Comparison done before asking for a look:
  - Seed of "Arrakis v6" with the round 4 biome reproduces the discs and straight cuts (`round5-discs-reproduced-v6.png`).
  - Seed of "Arrakis v4" reproduces the island in Screenshots/repettive outcrops.png feature for feature, same orientation: two holes, west peninsula, round islet to the south-west, south finger (`round5-seed-check-v4-island.png`, island centre near (1350, -1450)).
- The Node model is now only a rough sketch tool. Decisions in this round were made on the real preview.

### Repaired medium recipe

Still `CellValue` for per-cell values (it was never the fault), still all noise in world coordinates.

- Positions: `Mesh2D` scale 400, jitter 0.14, `MaxDistance` 200. Neighbouring positions are at least 288 m apart, so each cell reaches at least 144 m from its position.
- Survival: `v = WhiteNoise(cell) + shift`, shift from the distance to the nearest island centre: 3 inside 450 m (no outcrop cells on the island or against its shore), 0 at 450 m rising to 1.8 at 1000 m, then 3. A cell is filled when `v < 0.8`: 90% at 450 m, none beyond about 1000 m (about 600 m from the shore).
- Size is separate from survival (in the first repair attempt both came from `v`, so the big sizes only existed right at the inner edge): `1 / radius = CellValue[ Max(gate(v), sizeCurve(WhiteNoise "Size") * shrink) ]`, radius 50 m .. 72 m .. 97 m across the white noise range, shrinking to 0.8 x by 1000 m from the island.
- `distance`: `PositionsCellNoise` with a `Curve` return type, identity curve 0 -> 0, 200 -> 200. Metres.
- `F = Min( 1 - (distance + 2) / radius + lobes + detail, safety )`
  - lobes: `SimplexNoise2D` scale 75, 2 octaves, persistence 0.35, +/-0.45.
  - detail: scale 22, +/-0.03.
  - safety fade: `PositionsCellNoise` `Curve` return 0..128 m -> +5, 140 m -> -5. 140 m is 70% of `MaxDistance` and inside every cell, so no radius value can produce a disc or reach a cell boundary. The largest footprint the recipe itself can make is 97 * 1.48 = 144 m, so the fade only trims the extreme case.
- cap: `SimplexNoise2D` scale 85, 2 octaves, persistence 0.3, curve -1 -> 0.3, -0.2 -> 0.45, 0.2 -> 0.8, 0.6 -> 1.1.
- height: `CellValue[ 0.4 * radius * variety ]`, variety 0.4 .. 0.9 .. 1.4 from a third white noise. Crests about 8-54 m.
- `outcrop = Min( height * Min(F, cap), 200 m * F )`, then the shared `Max`, jitter and stair-step curve.

What did not work on the way (real preview):

- Radius 28-75 m with grid 320: pieces came out about 50 m across and nearly flat.
- Size and survival both from `v`: only six small pieces per island.
- Cap noise scale 45 and lobes scale 60: the busy, cramped terrace texture from the feedback. Smoother cap (scale 85, persistence 0.3) and lobes (scale 75, persistence 0.35) with height 0.4 x radius read as calm as the island.

Result on the "Arrakis v6" seed (`round5-medium-v6-wide.png`, `round5-medium-v6-zoom.png`): about 6-8 medium outcrops per island, roughly 90-190 m across, irregular outlines, closed contours, no circular arcs or straight edges seen across the two chains inspected. Underground (real generator, 2400 x 2400 m, 3 m cells): rock above the sand 44.1 ha, at Y = 40 47.9 ha (1.09 x), at Y = 10 47.9 ha (1.09 x).

Changed from what was asked to be kept: the medium position set went from scale 320 jitter 0.35 to scale 400 jitter 0.14. That is what guarantees an outcrop of up to about 250 m fits inside its own cell.

Feedback (world "Arrakis v7"): no small island at spawn and no large island close by. Correct for that build: in v7 the nearest islands were about 1.8-2.5 km west and north-west. Stages 2, 3, 4 and 5 were then done together.

## Round 5, stages 2-5 — small layer, starting outcrop and pad, routes, guaranteed island

Status: deployed, checked with the real-generator preview on seeds 1791406138722 ("Arrakis v7"), 1791405124027 ("Arrakis v6"), 42 and 7. Not yet seen in game.

### Small layer (stage 2)

Same repaired recipe as the medium layer; the round 2 wedges are no longer used.

- Positions: `Mesh2D` scale 150, jitter 0.14, `MaxDistance` 77. Safety fade to no rock by 54 m.
- Radius 14 .. 22 .. 36 m (about 30-100 m across), shrinking to 0.8 x by 1000 m from the island.
- Filled when `v < 0`: half the cells at 400 m from an island centre, none beyond 1000 m. No cells inside 400 m.
- Lobes scale 28 +/-0.45 (persistence 0.35), detail scale 9 +/-0.03, cap scale 32 (persistence 0.3).
- Height 0.45 x radius x variety; variety 0.3 .. 0.9 .. 1.3 for most cells, 3.5-4.5 for the top 7% (tall stacks, up to about 60 m on a small footprint).

### Guaranteed island (stage 5)

- Island centres are now `Union[ List[(640, 0, -480)], FieldFunction[ grid ] ]`.
  - The guaranteed centre is 800 m from spawn, to the north-east (east is +X, north is -Z). With the 300 m nominal radius its near shore is about 500 m from spawn. Fixed direction for now.
  - The grid (`Offset 900 > Occurrence 0.7 > Mesh2D 2500 jitter 0.2`, unchanged) is filtered by a `FieldFunction` position provider: a `PositionsCellNoise` on the guaranteed centre with a `Curve` return 0 below 1200 m and 1 from 1200 m, delimiter 0.5..2. Grid centres within 1200 m of the guaranteed island are dropped.
- Every use of the island positions (radial falloff, both outcrop fades) uses the union, so the guaranteed island gets a normal chain.
- Stepping stones: near spawn the small layer's survival shift is capped, so about 30% of small cells within 280 m of the origin are filled whatever the island distance. The medium layer has no cells within 350 m of spawn.

### Dune fade at spawn (stage 3)

- Dune branch noise term is now `(Clamp(noise) + 1) * t - 1`, where `t = CurveMapper(distance from origin + wobble)`, wobble `SimplexNoise2D` scale 160, +/-45 m, and the curve is 0 at 165 m, 1 at 275 m. So `t` is 0 within 120 m of the origin and exactly 1 from 320 m outwards: dunes beyond 320 m are unchanged. This is wider than the 250 m asked for; see "did not work".
- The copy of the dune height inside the rock graph uses the same nodes, so rock heights stay sand-relative.

### Starting outcrop and landing pad (stages 3 and 4)

- Not part of any position set: it is built from `Distance` (distance from the world origin), so it exists in every world.
- `F = 1 - d / 100 + lobes + detail`, lobes `SimplexNoise2D` scale 75 +/-0.3, world coordinates, so the outline differs per seed. About 140-260 m across.
- Natural height: `Min(30 m * F, cap) + lookout`
  - cap: noise scale 70 mapped to 6 .. 10 .. 15 m.
  - lookout: up to +20 m where `Abs(SimplexNoise2D scale 110)` is above 0.3, limited to where `F` is above 0.25. Measured high points on four seeds: 31-33 m above sand level, each in a different place 45-65 m from the origin.
- Not terraced. The stair-step curve has 3-9 m risers, which no route could cross; a smooth height field quantised to blocks gives steps of 1 block wherever the slope is below 1, which is nearly everywhere here.
- Pad: `height = 10 m + (natural - 10 m) * w`, `w` = 0 within 16 m of the origin and 1 from 45 m. So the pad is exactly Base + 10 in every world (top block Y = 89, standing at Y = 90), nothing inside it is higher, and the natural shape takes over smoothly.
- Other rock: islands and outcrop layers are masked out within 30 m of the origin (replaces the old 70-90 m spawn mask), then `Max` with the starting outcrop.
- Root cut applied through an identity `CurveMapper` with the same -4 m cut.
- Spawn in `WorldStructures/Arrakis.json`: (0.5, 91, 0.5), i.e. Base + pad height + 1.

Real-generator checks (`WALK=1 tools/preview/real.sh <seed> out.png 0 0 800 1` adds a flood fill from the origin that only crosses height changes of 1 block or less):

- Pad top block Y = 89 across the whole 15 m radius on all four seeds.
- Sand reached from the pad without a step over 1 block in all eight compass directions on all four seeds; about 32,000-34,000 m2 of rock is walkable from the pad.
- Underground: rock above the sand 43.9 ha, at Y = 40 and Y = 10 46.9 ha (1.07 x).
- 4-6 small outcrops within about 300 m of spawn on the v7 seed, between spawn and the guaranteed island.

What did not work:

- Dune fade by clipping the dune noise against a cone (`Min(noise, ramp)`): a clean circular scarp about 190 m from spawn. Scaling instead of clipping removed the crease but the edge was still a circle. Perturbing the distance with noise before the fade curve makes the edge wander; that needs room on both sides, hence 320 m rather than 250 m. A dune front is still visible in places as a steep bank; it is no longer an arc.
- Pad by clamping the natural height between two cones (floor and ceiling easing away from the pad): a visible disc with radial facets. The weighted blend above has no crease.

Against the play requirements: starts on rock (yes), fixed pad height (yes), two walkable routes (yes, eight), flat bench about 30 x 30 m besides the pad (not measured; the low cap gives broad gentle ground but nothing forces a flat bench), island shore about 500 m away (yes, fixed direction), 3-6 small outcrops within 300 m (yes on the seed checked).

Feedback: _pending_

# Reset to the round 5 state

Rounds 6-8 (cave networks and entrances, softened terrace profile, alcoves) were set aside. That work, with its log entries and the preview tool's cave, face and pocket surveys, is on the local branch `caves-experiment` (commit 46bd112). `main` went back to commit 2d58ca6 so caves and openings can be approached differently.

Two fixes made on top of that state (world "Arrakis V1", log clean, no `Took too long`):

- Materials: the upper rock block is `Rock_Sandstone` again instead of `Rock_Sandstone_White`. The sand-level rule is otherwise unchanged (`Rock_Sandstone` from 3 blocks below Base upwards, `Rock_Sandstone_Red` deeper).
- Spawn bug: players were appearing at (0, 140, 0), 50 blocks above the landing pad, and had been since round 5. Cause: the generator takes spawn points from a top-level `SpawnPositions` key in the world structure (vanilla: `"SpawnPositions": { "Type": "Imported", "Name": "Spawns" }`). Ours only had the framework `Positions` entry named "Spawns", which nothing reads on its own. With no spawn positions, `Handle.getSpawnPoints` falls back to a hard-coded (0, 140, 0); the server log shows "joined world 'default' at location (0, 140, 0)". Fix: added `"SpawnPositions": { "Type": "List", "Positions": [{ "X": 0.5, "Y": 91, "Z": 0.5 }] }` inline (not imported, because vanilla also exports the name "Spawns"). `write.js` now writes it too. Not yet confirmed in game.

Confirmed in game: world "Arrakis v2" log shows the player joining at (0.5, 91, 0.5). Log clean.

# Vanilla recipe showcase

Why: our rock is a height field pushed through a stair-step curve (about 470 nodes). Hytale's own World Gen V2 terrain biomes are 45-150 nodes and work differently. Before building more, look at theirs in game, in sandstone. The real Arrakis biome and world structure are untouched in this round.

## How the vanilla biomes build terrain

All of them are true 3D densities: a vertical gradient (a `CurveMapper` on `BaseHeight` distance, positive low down, negative high up) summed with noise, so the surface is wherever the sum crosses zero. None is a height field through a curve. Most end with the same wrapper: `Mix(terrain, "solid below Base", gate on height)`, which forces solid ground below Base and open air above a ceiling.

Node counts are for the terrain density only.

| Biome | Nodes | Main ingredients | Cliffs | Ledges | Meets neighbours by |
| --- | --- | --- | --- | --- | --- |
| Plains1_Mountains | 76 | Height gradient + base noise (scale 200) + texture noise (scale 50). `Max(peaks, hills)`. | A second, steeper formula (`Max` of two sums) is `Min`-ed in, and a "cliff mixer" noise (scale 150) blends between that and a constant 1 (no effect). So cliffs come in patches. | A sawtooth `CurveMapper` on (noise scale 350 + height ramp), added at +/-0.075. They wander with the noise. | `DistanceToBiomeEdge` curve 0 -> -0.75, 64 -> 0 added to the sum: height drops towards the border. |
| Taiga1_Mountains | 73 | Height gradient + base noise (scale 400) + 2D cell noise "boulders". Evaluated through `YSampled` (sample every 8 blocks vertically, interpolate). | 3D cell noise (two rotated `CellNoise3D`) multiplied in as a cliff texture: craggy, fractured faces. | None as such; the cell-noise texture gives blocky breaks. | Cliff texture switched off within about 20 m of the border. |
| Experimental/Mountains | 75 | Height gradient + squared noise (scale 500). | Slope-aware: `Angle` of the `DensityGradient` of the base terrain (steepness in degrees), normalised 30-70 degrees, mixes in the ledge and cell-noise detail only where the ground is steep. | Sawtooth curve on (height + noise scale 700), 10 teeth, limited by a Manhattan cell noise. | Nothing: no border fade at all. |
| Experimental/Plateaus | 75 | Several stacked "top noise + height gradient" layers combined with `Min`/`Max`: flat tops at a few levels. | Driven by `DistanceToBiomeEdge` (curve 0 -> -1, 48 -> 1): the mesa wall IS the biome border. | Stacked levels from the separate layers. | The cliff itself. |
| Experimental/Taiga1_Redwood_2dCliffs | 77 | Base noise (scale 500) + 2D cell noise + height gradient. | A banded texture: four copies of one height curve at different offsets, picked by `MultiMix` with a noise, applied only where a `Gradient` (slope) node says the ground is steep. | The height bands of that curve. Fixed heights, like our rejected undercuts. | No border term. |
| Plains1_Gorges | 152 | Many `PositionsCellNoise` (`Distance2Div`) layers with `AmplitudeConstant`: a network of cracks between rounded blocks. Not read in full detail. | The crack walls. | Not identified. | Not checked. |
| Experimental/Dunes | 65 | Two dune fields (noise scale 600 + 200) with a slip-face term from a stretched noise applied where a `Gradient` node finds steep ground. Heights are measured from `Bedrock` (100-180), i.e. it assumes Base = 100. | Slip faces only. | None. | None. |
| Experimental/Arches | 49 | 5 m cell lattice (`CellValue`) + noise; reads a "World-Continent-Map" export that no shipped asset provides, so it is a constant 0 in practice. | n/a | n/a | n/a. In the preview it is a low maze-like texture, not arches. |
| Generative/Generative_Arches | 43 | Anchored shapes on a regular 30 m and 60 m grid with 3D noise, `SmoothMin`. | n/a | n/a | n/a. Small repeated objects on a grid. |
| Generative/Generaitve_Boulders_Sandstone | 43 | Anchored `Distance` + tilted `Cuboid` + 3D noise on a regular 60 m grid, at absolute heights (Y 100 and 125). | n/a | n/a | n/a. Identical boulders in rows: a test pattern. |
| Desert1_Stacks | 73 | Dunes plus rock stacks from `PositionsCellNoise` with a `Curve` return. | Stack sides, via the distance curve. | 3D noise texture. | Stack positions filtered by `DistanceToBiomeEdge`. |
| Desert1_Rocky | 331 | The big one: cliffs, boulders, twists (`PositionsTwist`), many cell-noise layers. | Several mechanisms. | Several. | Biome interpolation section. Not read in full. |

Corrections to the brief's reading of Plains1_Mountains:

- "True 3D density, vertical gradient plus 2D noise": correct.
- "Ledges from a sawtooth `CurveMapper` on noise plus height at small strength": correct. The curve alternates +1/-1 about every 0.2 of input, and the result is scaled to +/-0.075.
- "A cliff mixer noise blends between a cliffy and a smooth version": nearly. The mixer blends between a cliff-limiting term and a constant 1, and the result is `Min`-ed with the smooth terrain. Where the mixer is high the limit is 1 and does nothing; where it is low the steeper formula cuts in. Same effect: cliffs in patches.
- "Fades at its border with `DistanceToBiomeEdge`": correct, over 64 m in the curve. But the vanilla zone world structures set `MaxBiomeEdgeDistance` to 32, so only the first half of that curve is ever reached.
- "Caves are one `Min` against an imported cave density": correct (`Plains1_Caves_Terrain`).
- Node count: 76 for the terrain, about 110 with materials. Not 60, but far below our 470.

## Showcase world

- World structure `Arrakis_Showcase` (`NoiseRange`). Its biome map is `CurveMapper(XValue) + CurveMapper(ZValue)`: a column index from X plus 10 x a row index from Z inside each 500 m square, a large negative number in the 200 m gaps. Each biome owns a range of width 1 around its value; the gaps fall through to the default biome `AS_Flat` (flat sand). Base 100 (the vanilla value these recipes assume), spawn (0.5, 102, 0.5), `MaxBiomeEdgeDistance` 32 and `DefaultTransitionDistance` 32 as in the vanilla zones.
- Biomes `AS_*` in `Biomes/Arrakis_Showcase/`, generated by `tools/showcase/build.js` from an extracted copy of the game's assets. Terrain densities are copied unchanged except:
  - every `ExportAs` name and the imports that refer to it get an `AS_` prefix, because export names are global across packs;
  - the "World-Continent-Map" import in Arches is replaced by the constant 0 it already resolves to;
  - node-editor metadata is dropped.
  `Plains1_Caves_Terrain` is still imported from the game, so the five biomes that use it keep their caves.
- Skin: all solid blocks `Rock_Sandstone` over a bedrock layer; Dunes and the flat filler are `Soil_Sand_White`. No soil-on-rock distinction was attempted: the vanilla material providers were dropped whole. No props, tint or creatures; environment `Env_Arrakis`.

Layout (east is +X, north is -Z; spawn at the origin on flat sand between Gorges and Dunes):

| | x -1050 (far west) | x -350 (west) | x +350 (east) | x +1050 (far east) |
| --- | --- | --- | --- | --- |
| z -700 (north) | Plains1_Mountains | Taiga1_Mountains | Experimental Mountains | Plateaus |
| z 0 | Taiga1_Redwood_2dCliffs | Plains1_Gorges | Dunes | Arches |
| z +700 (south) | Generative_Arches | Boulders_Sandstone | Desert1_Stacks | Desert1_Rocky |

How to create it: make a new world in the game with the Arrakis mod on, exit to the main menu, run `tools\showcase\Use-Showcase.ps1 -WorldName "<name>"`, load the world. The script adds `"WorldStructure": "Arrakis_Showcase"` to the world's `WorldGen` (our provider already reads it), deletes the chunks already generated and the saved player positions.

Checks:

- Real Arrakis: `Arrakis_Terrain.json` and `WorldStructures/Arrakis.json` are unchanged (no diff).
- All 13 showcase biomes and the world structure load through the game's asset codecs with no failures, and each of the 12 terrain densities builds and renders in the real-generator preview (`tools/showcase/showcase-preview.png`, each tile 500 m, previewed as if deep inside the biome). The preview can now render any biome: `BIOME=<id> WS=<structure> EXTRA_DENSITY=<game Density folder> NO_WRITE=1 tools/preview/real.sh <seed> out.png 0 0 500 1.25`.
- Not checked outside the game: the biome map itself, the borders between squares, and the log. The preview builds one biome at a time and does not run the biome-map and biome-distance stages.

Expect in game: square outlines. Plateaus in particular draws its cliff along the biome border, so it will be a square mesa. Mountain squares will slope down to their edges over about 32 m.

## Options for using a vanilla recipe on Arrakis (part 3, for after the look)

A. Rock and sand as separate biomes, our placement as the biome map.
- The world structure's `Density` is an ordinary density, so the island and outcrop footprint field could be the map: rock biome where it is positive, sand biome elsewhere. The starting outcrop and guaranteed island would move into that map.
- Gain: vanilla recipes drop in unchanged, including their `DistanceToBiomeEdge` edge behaviour, and rock and sand get separate, simple material providers.
- Risk, not verified: how the engine blends terrain across a border (`DefaultTransitionDistance` 32) and how it measures `DistanceToBiomeEdge` were not read from the jar. Vanilla mountains fade over 32-64 m from the border, so a rock body 40 m across would be all border and never reach its height. Small outcrops probably cannot be biomes of their own.
- Cost: dunes, the dune fade at spawn, the landing pad and the "rock heights measured from the sand" trick all currently live in one density and would have to be split across two biomes. The sand-level material rule stays easy (it only needs Y).

B. Keep one biome; transplant only the vanilla mountain density in place of the terraced height field.
- Keep everything that works: sand floor, dunes, positions, fade, pad, spawn, material rule on the exported rock density.
- Replace `terraced(height)` with the vanilla 3D formula, and feed it our own footprint field wherever the vanilla recipe uses `DistanceToBiomeEdge` (ours is already "how far inside the body", per body, at any size).
- Gain: works for bodies of any size including 40 m outcrops; no dependence on unverified biome-border behaviour.
- Cost: the rock is no longer a height per column, so "rock top per column" tricks (the round 3 root cut, the pad blend) need restating for a 3D density. Heights have to be rescaled per body, since vanilla mountains assume one fixed height range.

C. Hybrid: large islands as a biome (A), outcrops and the starting outcrop inside the sand biome as now (B-style or current). More moving parts; only worth it if A's border behaviour turns out to look much better than a transplanted formula.

Recommendation before seeing it in game: B, because isolated bodies down to 40 m are the core of this world and A's border blending is the unverified part. Worth reading the biome-distance stage in the jar before deciding.

## Showcase feedback (world "Arrakis shocse")

Loaded cleanly on `Arrakis_Showcase`: no `Took too long`, no load failures, spawn (0.5, 102, 0.5), 13.5 ms per chunk over 1000 chunks. One warning from our files, inherited from vanilla: "Duplicate export name for asset: AS_Plains1-Gorges-BaseTerrain" (the vanilla Gorges file uses that export name twice).

Numbering agreed: #1 at the north-west corner, counting east along each row, then down. So #1 Plains1_Mountains, #2 Taiga1_Mountains, #3 Experimental Mountains, #4 Plateaus, #5 Redwood_2dCliffs, #6 Plains1_Gorges, #7 Dunes, #8 Arches, #9 Generative_Arches, #10 Boulders_Sandstone, #11 Desert1_Stacks, #12 Desert1_Rocky.

Picks and why:

- #1 Plains1_Mountains: the sharp jagged mountains that protrude.
- #3 Experimental Mountains: the crevices and caves.
- #5 Redwood_2dCliffs: the carved look of the cliff faces.
- #6 Plains1_Gorges: the deep canyons.

Direction: give each generated island its own recipe, chosen at random from mixes of these four.

# Mixed-island showcase

Status: deployed, all twelve islands rendered with the real-generator preview (`tools/showcase/mix-preview.png`). Not yet seen in game.

World structure `Arrakis_Mix`, biomes `AM_*` in `Biomes/Arrakis_Mix/`, generated by `tools/showcase/build-mix.js`. Each biome is a 1100 m tile of flat sand with one island at its centre. Base 100, spawn (0.5, 102, 0.5).

## How an island is built

- Footprint field `F`: the Arrakis large-island recipe with the same numbers (radial falloff 0 at 300 m, lobe noise scale 230 +/-0.62, detail scale 60 +/-0.05), centred on the tile with a `List` position, different noise seeds per island.
- Vanilla terrain: the root density of each chosen biome is copied unchanged, with two substitutions: export names get a per-copy prefix, and every `DistanceToBiomeEdge` node is replaced by `Clamp(0, 2000)[300 * F]`, roughly metres inside the shore. That is the option B transplant from the section above: the recipe thinks the island shore is its biome border.
- Merge, when an island has more than one recipe: by patches. `Mix(A, B, mask)`, mask from a `SimplexNoise2D` scale 240 through a curve that gives each recipe a similar share with a blend zone 0.6 of noise range wide. Three or four recipes are chained.
- Island limit: `rock = Min(merged, coast, footprint)`.
  - coast: `3.9 * F` plus a height term falling 0.01 per metre, i.e. rock may rise 1.3 m per metre in from the shore.
  - footprint: -5 where `F < -0.012`, +5 from -0.01, so nothing exists outside the footprint at any depth and the root goes straight down.
- Terrain: `Max(flat sand floor at Base, rock)`. No dunes in this showcase.
- Material: the sand-level rule on the exported rock density (`Rock_Sandstone` from 3 blocks below Base up, `Rock_Sandstone_Red` deeper), sand elsewhere.

## Layout (east is +X, north is -Z; island centres)

| | x -1650 | x -550 | x +550 | x +1650 |
| --- | --- | --- | --- | --- |
| z -1100 | M1: recipe 1 | M2: recipe 3 | M3: recipe 5 | M4: recipe 6 |
| z 0 | M5: 1 + 3 | M6: 1 + 5 | M7: 1 + 6 | M8: 3 + 5 |
| z +1100 | M9: 3 + 6 | M10: 5 + 6 | M11: 1 + 3 + 5 | M12: 1 + 3 + 5 + 6 |

Node counts for the rock density: single recipes 108-185, pairs 188-306, the four-way mix 464.

## Seen in the preview

- All twelve build and come out island-sized with the Arrakis outline character.
- Recipe 5 (2dCliffs) leaves large sand holes and a few straight edges where its own ground sits at or below Base; islands with a lot of it (M3, M8) are thin and broken up.
- Recipe 6 (Gorges) gives the densest, most solid islands, cracked all over.
- Recipes 1 and 3 give lobed mountains with a few sand gaps.
- First attempt at the merge used a blend zone 0.24 of noise range wide and showed hard seam lines between patches; widened to 0.6.

## Known limits

- Caves from the vanilla cave density that fall below Base inside an island fill with sand, because the sand floor is solid there (the "cave air must win over sand inside rock" problem from the set-aside round 6).
- The four recipes use different density scales (their final multipliers are 2, 0.2, 0.5 and 1), so in a blend zone the surface leans towards the larger-scale recipe.
- Heights are whatever the vanilla recipe gives (up to about 150-200 m above Base); nothing ties height to island size yet.
- Preview is of one biome at a time; tile borders and the log are untested until a load.

How to create it: `tools\showcase\Use-Showcase.ps1 -WorldName "<name>" -WorldStructure Arrakis_Mix` on a fresh world.

## Mixed-island showcase, first load (world "Arrakis shocse" on Arrakis_Mix)

- Loaded: no Took too long, no load failures, no unused keys. Five "Duplicate export name" warnings, one per copy of the Gorges recipe (AM4a, AM7b, AM9b, AM10b, AM12d), inherited from the vanilla file.
- Generation time: 25.5-31.3 ms per chunk in the 100-, 500- and 1000-chunk reports (27.3 ms at 1000). That is about twice the Arrakis world (13.5-15 ms). The first chunk took 250 ms.
- An exception at shutdown ("World thread is not accepting tasks") appears after the client disconnected; it is from the world closing, not from generation.
- Feedback: the peaks are great, but too much of each island is above ground. Lower the rock 20 blocks, so the sand ocean is in effect 20 blocks higher on it.

Change: the merged vanilla terrain is wrapped in `Slider` with `SlideY` -20 (`SINK` in `build-mix.js`). `Slider` evaluates its input at position minus the slide, so the terrain is read 20 blocks higher up and the whole shape moves down 20. The coast limit, footprint, sand floor and material rule are not shifted: sand level stays at Base.

Heights after lowering (real-generator preview, sand level Y 100):

| Island | Highest top block | Average rock top |
| --- | --- | --- |
| M1 (#1) | Y 200 | Y 137 |
| M2 (#3) | Y 189 | Y 126 |
| M3 (#5) | Y 130 | Y 112 |
| M4 (#6) | Y 140 | Y 116 |
| M5 (#1 + #3) | Y 225 | Y 136 |
| M6 (#1 + #5) | Y 207 | Y 130 |
| M7 (#1 + #6) | Y 196 | Y 126 |
| M8 (#3 + #5) | Y 234 | Y 122 |
| M9 (#3 + #6) | Y 205 | Y 123 |
| M10 (#5 + #6) | Y 141 | Y 114 |
| M11 (#1 + #3 + #5) | Y 205 | Y 132 |
| M12 (all four) | Y 198 | Y 125 |

Heights before lowering were not recorded. Deployed; the world's chunks were cleared again.

## Mixed-island showcase, second pass: three sizes and the root rule

Feedback on the lowered islands: all twelve are right for large islands and should each be a possible variant; #12 (M12) has floating clusters of blocks above the main body. Next: scale the variants down for medium and small islands by lowering them further (another 20 blocks for medium, another 10 for small) and keeping what stays above the sand. And the standing rule again: once rock is below the sand surface it must fall away steeply to bedrock, so islands and their caves are not joined under the sand.

Status: deployed, rendered with the real-generator preview (seed 4242), not yet seen in game.

Each tile (1500 x 1100 m) now shows one variant as six shapes, all from the same recipe:

| Size | Lowered by | Footprint radius | Offset from tile centre |
| --- | --- | --- | --- |
| Large | 20 blocks | 300 m | (-300, 0) |
| Medium | 40 | 90 m and 60 m | (380, -230) and (400, 200) |
| Small | 50 | 36, 25 and 18 m | (610, -330), (630, -20), (600, 340) |

Tile centres: x -2250, -750, 750, 2250; z -1100, 0, 1100. Same variant order as before (M1-M4 single recipes #1 #3 #5 #6; M5 1+3, M6 1+5, M7 1+6, M8 3+5; M9 3+6, M10 5+6, M11 1+3+5, M12 all four). Spawn (0.5, 102, 550.5).

How it is built:

- The variant's recipe is exported once per tile with `SingleInstance: false`; each use is `Slider(SlideY = -sink)[Imported recipe]`, so the three size classes are the same recipe read 20, 40 or 50 blocks higher up.
- Each size class is `Mix(-5, body, inside-footprint)`. `Mix` evaluates only its first input where the influence is 0, so the recipe is not evaluated outside the footprints.
- `body = Min(lowered recipe, coast, root)`.
  - coast: rock may rise 1.3 m per metre in from the shore of its own footprint.
  - root: `CurveMapper( YOverride(Base - 4)[ Cache[ lowered recipe ] ] )`, -5 where that is not positive, +5 where it is. A column keeps rock, at every depth, only if the recipe is solid 4 blocks below sand level there. So every mass that breaks the sand has its own vertical-sided root and nothing joins up underneath.
- Biome-edge substitute: the recipes are told how far inside their shape a column is on the scale of a full-size island (`300 * F` for every shape, whatever its radius).

Measurements (1400 m view per tile, 3.5 m columns):

| Tile | Rock above the sand | Rock at Y = 40 | Rock at Y = 10 |
| --- | --- | --- | --- |
| M1 | 30.3 ha | 0.94 x | 1.01 x |
| M2 | 14.6 ha | 0.87 x | 1.05 x |
| M3 | 11.0 ha | 1.12 x | 1.12 x |
| M4 | 25.5 ha | 0.97 x | 1.09 x |
| M5 | 25.5 ha | 0.89 x | 1.03 x |
| M6 | 21.9 ha | 0.99 x | 1.02 x |
| M7 | 29.1 ha | 0.92 x | 1.04 x |
| M8 | 13.1 ha | 1.06 x | 1.07 x |
| M9 | 23.4 ha | 0.93 x | 1.06 x |
| M10 | 18.7 ha | 0.97 x | 1.11 x |
| M11 | 21.6 ha | 0.92 x | 1.04 x |
| M12 | 21.7 ha | 0.98 x | 1.06 x |

Underground area never exceeds 1.12 x what shows above the sand. Values under 1 at Y = 40 are the recipes' own caves.

Floating rock on the large island of each variant (new `FLOAT=1` mode: 2 m voxels from 4 below sand level up, flood fill from the lowest layer; pieces joined only diagonally count as floating, so this overstates):

| Variant | Pieces | Total | Biggest |
| --- | --- | --- | --- |
| M1 (#1) | 1 | 8 m3 | 8 m3 |
| M2 (#3) | 1 | 8 | 8 |
| M3 (#5) | 3 | 24 | 8 |
| M4 (#6) | 11 | 152 | 40 |
| M5 (1+3) | 14 | 168 | 24 |
| M6 (1+5) | 7 | 72 | 24 |
| M7 (1+6) | 14 | 1224 | 712 |
| M8 (3+5) | 6 | 48 | 8 |
| M9 (3+6) | 16 | 616 | 424 |
| M10 (5+6) | 13 | 200 | 72 |
| M11 (1+3+5) | 9 | 304 | 152 |
| M12 (all four) | 8 | 112 | 40 |

So floating rock is not special to M12. Single recipes #1, #3 and #5 are nearly clean; every merge and anything containing #6 (Gorges) produces some, and on this seed the worst are 1+6 and 3+6. It depends on the seed. Nothing has been done about it yet: a density cannot test whether a piece is attached, so it has to be prevented at the source (the patch blend between recipes of different scale is the likely culprit for merges; Gorges makes some on its own).

What did not work:

- Feeding the recipes real metres inside a small footprint (`radius * F`): their own border fade over 32-64 m flattened the medium outcrops to a few fragments and the small ones to nothing. Scaling every shape as a full-size island fixed that.

What the preview shows: large islands as before; medium footprints give one or two masses of 40-100 m; small footprints give a rock of 15-40 m or sometimes nothing. Outcrop peaks are about 45 m above the sand on the 1+3 tile.

Not done: variants are not yet picked at random per island in the real Arrakis world. This is still the showcase.

# Variants in the real Arrakis world

Decisions from the three-size showcase: remove the worst merges for floating rock; empty small outcrops are acceptable; put the variants into the real build and test a world; one of the island types must sit at spawn so players start on rock.

Status: deployed, checked with the real-generator preview on seeds 1791406847363, 42, 7 and 99. Not yet seen in game.

## What changed in Arrakis_Terrain

The terraced height-field rock (rounds 2-5) is replaced. Dunes, the dune fade at spawn, the sand floor, island and outcrop positions and footprints, the chain fade, the guaranteed island and the sand-level material rule are kept. `graph.js` builds the new rock when `PARAMS.variants` is set; `write.js` needs `VANILLA_GEN` pointing at an extracted copy of the game's `Server/HytaleGenerator`.

- Ten variants (1+6 and 3+6 dropped): #1, #3, #5, #6, 1+3, 1+5, 3+5, 5+6, 1+3+5, 1+3+5+6. Each is exported once as `ArrakisV<n>`, not single-instance.
- Pick: one white-noise value per large island (`CellValue` on the island positions, exported as `Arrakis_Pick`, cached per column). Medium and small outcrops read the same value, so a chain uses its island's variant. The starting island has its own pick from the origin (`Arrakis_Pick_Start`).
- Only the picked variant is evaluated: `Max` over `Mix(-5, Slider(-sink)[variant], pick-is-this-one)`; `Mix` skips its second input when the influence is 0.
- Size classes, each `Mix(-5, Min(picked variant, coast, root), inside-footprint)`:

| Class | Footprint | Lowered by |
| --- | --- | --- |
| Large island | island field, 300 m nominal radius | 20 blocks |
| Medium outcrop | medium layer field (radius 50-97 m per cell) | 40 |
| Small outcrop | small layer field | 50 |
| Starting island | radius 100 m at the origin | 30 |

- Coast: about 1.3 m of rise per metre in from the shore (nominal radius per class).
- Root: a column keeps rock at any depth only if the picked variant is solid 4 blocks below sand level (Base - 4).
- Biome-edge substitute: `Arrakis_Inside` = 300 x the largest footprint field at that column, exported single-instance and imported at every `DistanceToBiomeEdge` site.
- Islands and outcrops are cleared within 30 m of the origin.
- Landing pad: two cones around the origin. Inside 16 m both sit at Base + 10, so the pad is flat and open to the sky; outside, the lower one falls and the upper one rises at 0.6, so the ground next to the pad is walkable either way. The lower cone stops at a vertical edge 35 m out (root rule). Spawn stays (0.5, 91, 0.5).

## Checks (real generator)

- Asset loads and builds; biome file about 2.1 MB, rock graph about 3,200 nodes.
- Pad: top block Y 90 across the whole 15 m radius on all four seeds. (Round 5's pad measured Y 89; the cone formula lands exactly on zero at Y 90, so the block there is solid. Spawn Y 91 is standing height on it.)
- Routes: sand reachable from the pad without a step over 1 block in all eight directions on three seeds and in six of eight on the fourth.
- Underground, 2600 m view round spawn: rock above the sand 38.7 ha, at Y = 40 46.2 ha (1.19 x), at Y = 10 42.1 ha (1.09 x). The Y = 40 figure is above the old 1.15 target; rock hidden under dunes just above sand level and the pad's base count as "not showing".
- 7 km view (`tools/preview/variants-world-7km.png`): every large-island site has rock, but islands differ a lot in how much. The guaranteed island on that seed is a full, solid island (`variants-main-island.png`); several others are a scatter of fragments.

## Problems found

- Curves: `curve()` in `graph.js` rounds inputs to 5 decimals, so 1e-6 offsets collapsed onto their neighbours and the asset failed validation ("More than one point with Y value"). Use 1e-4.
- `Distance` is a 3D distance from the world origin. The pad cones and the 30 m clearing are evaluated per voxel, not under a `YOverride`, so at Y 85 the "distance" was already 85 and there was no pad at all. They are now wrapped in `YOverride(0)`.
- A `String.replace` with a replacement containing `$'` silently mangled the patch (JavaScript replacement pattern). Use a function replacement.

## Known weaknesses going into the game test

- Thin islands: variants built on #5 alone or 5+6 are low (30-40 m in the showcase), so lowered 20 blocks little is left, and their medium and small outcrops are mostly empty. About three of the ten variants give weak islands.
- The pad sits in a round bowl or on a round mound where the variant's ground differs from Base + 10; the cones are visibly conical.
- Floating rock was reduced by dropping two merges, not eliminated.
- Caves from the vanilla cave density that fall below sand level inside rock fill with sand.
- Generation time is unmeasured. The showcase ran at about twice the old Arrakis time, but there every column was inside a tile of recipe; here most of the world is sand and skips the recipes.

## First game test of the variants (world "Arrakis v2 1"): whole world painted sandstone

Report: the whole world was sandstone instead of the white-sand ocean.

Cause: mine, in the integration step. To keep the definition of `Arrakis_Inside` in the tree I added `Multiplier[0, definition]` as a third input of the `Max` that forms `Arrakis_Rock`. That input is a constant 0, so outside every rock body the rock density became `Max(-5, -5, 0) = 0` instead of -5. The terrain shape did not change (0 is not solid), which is why the preview images looked right, but the material rule paints rock wherever the exported rock density is between 0 and 100, and 0 qualifies. Every solid block in the world passed that test, including all the sand. The sand, dunes and material rule themselves were not changed.

Fix: the holder is now added (`Sum(Max(others, start), 0 * definition)`) instead of being a `Max` input, so the density outside rock is -5 again.

New preview check, because the old ones could not see this: `materials:` counts sand-surface columns where the rock density at the surface block is >= 0. Deployed build: 140,359 of 140,359. Fixed build: 0 of 140,359 (seed 42, 1600 m view).

From that world's log: no `Took too long`, no load failures; 21.5 ms per chunk over 1000 chunks (24.3 at 500, 36.6 at 100), against 13.5-15 ms for the old world.

## Second game test of the variants (world "Arakis v3", seed 1791432644696)

Sand ocean confirmed back in the preview for that seed (0 of 618,723 sand columns painted as rock). Log clean; spawn (0.5, 91, 0.5); 21-25 ms per chunk in the 500- and 1000-chunk reports. On that seed the guaranteed island drew a thin variant (a branching ridge). Verdict: thin islands look fine; keep all ten variants.

# Islands by latitude

Request: the spacing between large islands should vary north to south, as in Dune: bigger land in the north, open desert in the south. Answers to my questions:

1. About 10 km north the islands should form east-west ("latitudinal") land; by 20 km north back to the spacing at spawn, giving a belt of land. Going south over 20 km, down to medium islands only, 600-2500 m apart, and it stays like that. (The answer said "0 to 20 km closer, 20 km to 10 km further"; read as: peak at 10 km north, back to normal at 20 km north.)
2. Far north: dense large islands with sand channels, not solid continents.
3. Far south: as in 1.
4. Northern islands bigger and wider east-west.
5. One rock variant per land mass.
6. No east-west variation.
7. Spawn and the guaranteed island stay as they are.

Status: deployed, rendered with the real-generator preview on seed 1791432644696. Not yet seen in game.

## How it is built (north is -Z)

All curves are `CurveMapper(ZValue)` and hold their end values beyond the last point.

- Regular large islands (2500 m grid, 300 m radius): the keep-chance in `Occurrence` is now a curve instead of 0.7: 0.7 at 20 km north, 0.15 at 10 km north, 0.7 from 2.5 km north to spawn, falling to 0 at 20 km south.
- Belt islands, new: their own `Mesh2D` grid, 4000 m east-west by 1800 m north-south, jitter 0.12, kept with a chance of 0 at 2.5 km north, 0.9 at 10 km north, 0 at 20 km north.
  - Footprint: `PositionsCellNoise` with a `Density` return type; the choice is the belt strength at the island centre (0 at the belt's edges, 1 at its core) and picks one of four anchored `Ellipsoid` sizes, half-axes east-west x north-south: 500 x 330, 750 x 400, 1050 x 470, 1350 x 520 m.
  - Plus broad lobes (`SimplexNoise2D` scale 520, +/-0.45), smaller bays (scale 150, +/-0.14) and the usual coast detail. The lobes are what cut sand channels into them.
- Island field = `Max(regular field, belt field)`. Island positions = `Union(guaranteed, filtered regular grid, belt)`, so chains, the variant pick and the biome-edge substitute all see belt islands.
- South: the medium outcrop layer's survival shift gets a second term by latitude, so cells fill on their own whatever the island distance: none at spawn, 15% of cells by 20 km south (400 m grid, so of the order of 1 km apart). The small layer has no such term, so with no large islands there are no small outcrops.
- Variant pick: `Mix(white noise at the medium cell, white noise at the nearest island centre, near-an-island)`, where near-an-island is 1 on a large-island footprint or within 1200 m of an island centre. So a land mass and its chain share one variant, and a free-standing southern medium island picks its own.

## Preview, 10 km views down the centre line

| Centre | Rock share of the view |
| --- | --- |
| 20 km north | 2.5% |
| 15 km north | 8.2% |
| 10 km north | 12.9% |
| 5 km north | 7.6% |
| spawn | 1.9% |
| 5 km south | 1.5% |
| 10 km south | 1.1% |
| 20 km south | 0.2% |

Images: `tools/preview/latitude-series.png` (the eight views) and `latitude-belt-zoom.png` (3.6 km at the belt core: one belt island about 2.7 km by 1 km, laced with sand channels, and one that drew a thin variant and is a scatter of pieces).

Other checks: pad top block Y 90 and sand reachable in all eight directions (unchanged); 0 sand columns painted as rock. Biome file about 2.5 MB.

## Known weaknesses

- First belt grid was 5000 x 2500 m with islands up to 1500 x 550: rows 1.4 km apart and 2 km gaps, more "big islands" than "belt". Tightened to 4000 x 1800 with islands up to 1350 x 520.
- Underground at the belt core: rock above the sand 169 ha, at Y = 40 240 ha (1.42 x). Well over the 1.15 target. Belt islands have a lot of rock just under dune level that counts as "not showing"; roots are still vertical per mass, but one belt island is one mass.
- Southern medium islands are small: at 20 km south the view has about 23 ha of rock in total. Medium outcrops are the recipe lowered 40 blocks, and for the lower variants little survives.
- Outcrop chains are placed 450-1000 m from an island centre, which for a belt island is inside the island, so belt islands have almost no chain round them.
- Belt islands and regular islands can overlap where both exist; they then have different variants and the rock changes along the line between their centres.
- Generation time in the belt is unmeasured and will be higher: rock chunks cost about twice sand chunks.

## Islands by latitude, second pass: mixed-size land masses

Feedback on the belt: a sketch drawn over the preview series (`tools/preview/Suggested Island spacing and size by Z axis.png`). Read from it, each panel being 10 km across:

- North (20 km to 5 km north): large, medium and small islands mixed at every latitude, placed irregularly, no rows. A few very large ovals of about 4-5 km by 2-2.5 km, many of 1.5-2.5 km by 1 km, smaller ones of 0.5-1 km, and scattered dots.
- Spawn and south: a couple of 1.5 km ovals near spawn, one of about 2 km at 5 km south, only 1 km ovals and dots at 10 km south, one 1.5-2 km outline and dots at 20 km south.

Answers: each outline is a solid land mass with sand channels and bays; north of 20 km it thins out to the same as the far south; all the sizes read off the sketch are to be halved, so the island at spawn stays 400-700 m; the dots are free-standing outcrops, several small ones or one or two medium ones.

Status: deployed, rendered with the real-generator preview on seed 1791432644696. Not yet seen in game.

### What replaced the belt

The single belt grid is gone. Land masses now come from sub-grids: each size class has two sparse grids offset from each other, with jitter, and a keep-chance by latitude. Two sparse offset grids look irregular together and can sit side by side or overlap, while each grid's cells stay large enough that no land mass is ever cut at a cell boundary. The island field is the `Max` of the regular island field and every sub-grid's field.

| Class | Sub-grid cell | Sizes (east-west x north-south) | Keep-chance |
| --- | --- | --- | --- |
| Regular island | 2500 m grid | about 600 m | 0.1 beyond 27 km north, 0.6-0.7 from 21 km north to 3 km south, 0.3 at 12 km south, 0.1 from 20 km south |
| Large | 2800 x 1900 m, two grids | 750 x 500 to 1250 x 640 m, turned -35 to +25 degrees | 0.05 beyond 27 km north, 0.45-0.5 from 21 km to 5 km north, 0.12 round spawn, 0.07 at 12 km south, 0.05 from 20 km south |
| Huge | 5500 x 3500 m, two grids | 2000 x 1000 to 2500 x 1240 m, turned -10 to +20 degrees | 0 beyond 22 km north, 0.35 at 17 km, 0.5 at 12 km, 0.45 at 6 km, 0 from 3 km north southwards |

- Each land mass is one anchored `Ellipsoid` (size and turn picked per land mass by white noise) plus broad lobes and bays at its own scale (Large: scales 300 and 90; Huge: 600 and 170).
- Land masses are a rock class of their own, lowered 8 blocks instead of 20, so they stay solid. First render with them at 20 was full of sand holes on most variants.
- Free-standing outcrops everywhere: 3% of medium cells and 0.4% of small cells fill on their own whatever the island distance (replaces the south-only rule).
- Variant pick, one per land mass: regular islands and their chains use the nearest regular centre within 1200 m (a lone outcrop picks for itself); then each land-mass sub-grid claims its own footprint plus a margin, larger classes overriding smaller ones.

### Preview, 10 km views down the centre line

| Centre | Rock share |
| --- | --- |
| 25 km north | 2.0% |
| 15 km north | 13.0% |
| 10 km north | 17.3% |
| 5 km north | 14.6% |
| spawn | 5.9% |
| 5 km south | 4.5% |
| 10 km south | 2.3% |
| 20 km south | 2.1% |

Images: `latitude-series-v2.png`, and `latitude-landmass-zoom-v2.png` (one Huge land mass about 3 km by 1.3 km, solid, with bays; underground 1.04 x at Y = 40 and 1.10 x at Y = 10 in that 3.6 km view).

Other checks: pad top block Y 90 on three seeds, sand reachable in eight, eight and six of eight directions; 0 sand columns painted as rock. Biome file about 3.4 MB.

### Differences from the request, and limits

- Rock round spawn went up (5.9% of the 10 km view against 1.9% before), because Large land masses now exist round spawn at a 12% chance per cell.
- The series has no view at 20 km north; the thinning there is by the curves (regular 0.6 at 21 km falling to 0.1 at 27 km; Large 0.45 to 0.05; Huge 0 by 22 km), so the north is still fairly busy at 20 km and thin by 25-27 km.
- Where two land masses overlap they join smoothly in shape, but the smaller class's variant is overridden only inside the larger one's margin; a change of rock along the join is still possible.
- Outcrop chains are still placed by distance from a centre, so Huge land masses have almost no chain.
- Generation time in the north is unmeasured.

## Land masses accepted; spawn terrain removed

World "Arrakis v20" (seed 1791436655886): placement accepted as "perfect". A 10 km by 50 km map of that seed was rendered at 5 blocks per pixel (`tools/preview/arrakis-v20-map-10km-x-50km.png`, not committed, 18 MB). Rock share by band on that seed: 25-15 km north 10.7%, 15-5 km north 11.5%, 5 km north to 5 km south 6.8%, 5-15 km south 2.2%, 15-25 km south 0.0%. Log clean; about 27 ms per chunk over 1000 chunks round spawn.

Request: remove the "sphere" generated under spawn (the round mound or bowl from the landing-pad cones); an island nearby is enough, players may spawn on sand. And can spawn height be dynamic, at the first solid block, so new players take no fall damage?

Status: deployed, spawn area rendered with the real-generator preview. The spawn height itself can only be confirmed in game.

### Dynamic spawn height

From the 0.6.8 jar: when a world has no spawn provider of its own, `IWorldGen.getDefaultSpawnProvider` returns `FitToHeightMapSpawnProvider(IndividualSpawnProvider(spawn points from the world structure))`. `FitToHeightMap.getSpawnPoint` takes the point and, if its Y is below 0 and the chunk at that column is available (`World.getNonTickingChunk`), sets Y to `chunk.getHeight(x, z) + 1`. A Y of 0 or more is used as given, which is why the fixed spawn Y values never moved.

So spawn is now (0.5, -1, 0.5) in both `SpawnPositions` and the framework entry. Not verified: whether `getNonTickingChunk` always has the spawn chunk at that moment. If it ever returns nothing the point keeps Y = -1 and the player would start below the world.

### Removed

`start.enabled: false` in `graph.js` switches off, together: the starting island and its variant pick, the two landing-pad cones, the 30 m clearing of other rock round the origin, and the dune fade at spawn (the dune branch is back to the plain clamped noise). The guaranteed island 500 m north-east stays. The stepping-stone rule for small outcrops near spawn and the "no medium cells within 350 m of spawn" rule were left as they are.

## Spawn snap confirmed; starting island back, without the pad

World "Arrakis v21" (seed 1791438024806): with spawn Y = -1 the player joined at (0.5, 95, 0.5), i.e. the game fitted the spawn point to the surface. Confirms the reading of `FitToHeightMapSpawnProvider`. Log clean; about 16 ms per chunk round spawn (less rock there without the starting island).

Feedback: bring the starting island back, but not the landing pad; the island that generated round spawn was good, and with the snap-to-surface rule it should be fine for most spawns.

Change: `start.island: true`, `start.enabled: false`. The starting island (footprint radius 100 m at the origin, its own variant pick, recipe lowered 30 blocks) is back as one more rock body. Still off: the landing-pad cones, the 30 m clearing round the origin, and the dune clearing. Spawn stays (0.5, -1, 0.5).

Preview, 500 m round spawn: seed 1791436655886 has 4.1 ha of rock with its top up to 58 m above sand level; seed 42 has 2.7 ha, up to 87 m; seed 7 has 1.0 ha, up to 60 m. So how much island there is at spawn varies a lot with the variant drawn, and the player lands on top of whatever is at the origin, rock or dune.

Not checked: whether the surface block at the origin can be a spot the player cannot leave without a fall (the top of a spire, or a pit).

## Arrakis_Sand: sand that cannot be dug

Goal: the desert must never be dug away. A custom block that behaves like bedrock and looks and sounds like white sand, generated wherever the Arrakis world generated `Soil_Sand_White`. Assets only.

Status: deployed. The generator builds with it in the real-generator preview (0 errors), but the preview does not load item or block assets, so the block itself is unchecked until a world is loaded. None of the in-game checks have been run.

### What makes a block unbreakable in 0.6.8

Read from `HytaleServer.jar`, `BlockHarvestUtils`:

- Every damage path ends in `damageSingleBlock`, which returns false straight away when `BlockType.getGathering()` is null. No damage is recorded and no break follows. There is no unbreakable flag and no hardness value on the block type; the missing `Gathering` section is the whole mechanism.
- Explosions (`ExplosionUtils`) go through the same `performBlockDamage`, so they hit the same check.
- Creative: `BreakBlockInteraction` tests `GameMode.Creative` and calls `performBlockBreak` directly, skipping the gathering check. That is why bedrock breaks in Creative.
- Physics: `BlockPhysicsUtil` removes a block when its `Support` requirement is no longer met. No `Support` section, nothing to fail.
- Paths that skip the check for any block, bedrock included: `DestroyBlockInteraction`, `CarryBlockInteraction`, `BlockPlaceUtils`, and anything that sets blocks directly (commands, builder tools). Not traced further.

### The block

`Server/Item/Items/Soil/Sand/Arrakis_Sand.json`: a copy of vanilla `Soil_Sand_White.json` with three things removed, `BlockType.Gathering`, `BlockType.Support` and `ResourceTypes`, and the translation key changed to `server.items.Arrakis_Sand.name`. Everything else is as white sand: textures and transition texture referenced from vanilla, icon `Icons/ItemsGenerated/Soil_Sand_White.png`, `Group: Sand`, `TransitionToGroups`, `BlockSoundSetId: Sand`, `BlockParticleSetId: Sand`, `ParticleColor`, `PhysicalMaterialId: Dirt`, `TextureComputedColor`, `ItemSoundSetId: ISS_Blocks_Gravel`, category `Blocks.Soils` / `GrassAndDirt`, tags Soil and Sand.

### Translation

`I18nModule.loadMessagesFromPack` runs once per asset pack and merges `Server/Languages/<locale>/*.lang`; the file name is the key prefix. So the mod ships its own `Server/Languages/en-US/server.lang` with one line, `items.Arrakis_Sand.name = Arrakis Sand`, and the vanilla file is untouched. Another installed mod (BetterMap) does the same. English only.

### Files changed

- added `src/main/resources/Server/Item/Items/Soil/Sand/Arrakis_Sand.json`
- added `src/main/resources/Server/Languages/en-US/server.lang`
- `src/main/resources/Server/HytaleGenerator/Biomes/Arrakis/Arrakis_Terrain.json`: the one `Soil_Sand_White` (the fallback material of the terrain material provider) is now `Arrakis_Sand`. It is the only biome the Arrakis world structure uses. The showcase biomes keep white sand.

### Expected, not tested

- Survival: hits do nothing on the server. What the client shows while trying (crack overlay, particles) is unknown.
- A world created before this change keeps white sand in chunks already generated; chunks generated afterwards get `Arrakis_Sand`. The two look the same, so there should be no visible seam, but old chunks stay diggable.
- Shovelling for sand without removing the block: nothing asset-only found. Every gathering route (`Breaking`, `Soft`, `Harvest`) ends by removing the block.

## Starting island missing (world "Arrakis v23")

World "Arrakis v23" (seed 1791462514960): no rock island at spawn; player joined at (11.2, 105.4, -9.2) on a dune. Log: no `Took too long`, and no warnings or errors naming `Arrakis_Sand`, an unknown block or a missing asset.

Reproduced in the real-generator preview: 0 rock columns within 250 m of spawn on that seed.

Two causes, both from removing the landing pad:

- The starting island was only a vanilla recipe lowered 30 blocks inside a 100 m footprint. The root rule keeps it only where the recipe is solid 4 blocks below sand level, and on some variant draws the recipe's ground is below the sand across the whole footprint. Lowering it less did not help on this seed (rock in a 300 m view at 2 m per pixel, of 22500 pixels: 0 at 30 blocks, 203 at 15, 344 at 8).
- The dune clearing round spawn was switched off with the pad, so dunes up to about 25 m high buried whatever low rock there was.

Fix:

- Starting island floor: the old natural starting outcrop height (low walkable top 6-15 blocks above sand level, lookout rise up to 20, shaped by the lobed starting footprint, no pad and no cones) is added as a rock body, limited to the starting footprint at every depth so its root goes straight down. Rock = `Max(everything else, variant starting island, floor)`.
- The dune clearing round spawn is back on (dunes fade out between 320 m and 120 m from the origin).

Rock pixels in the same 300 m view on eight seeds, before then after: 0 / 3267, 261 / 4235, 5144 / 9192, 6285 / 7325, 2441 / 6086, 2110 / 5367, 4513 / 5739, 2474 / 5650.

On the v23 seed: 1.3 ha of rock above the sand, top block Y 81-89, sand reachable in all eight directions without a step over one block; 0 sand columns painted as rock. Underground the footprint is 3.4 ha (2.6 times what shows), a single isolated column.

Status: deployed, not yet seen in game.

## Spawn found by the plugin, on a plain medium island at the origin

Direction: stop forcing rock under spawn. Generate an ordinary medium island at (0, 0) and have the game find exposed rock nearby to spawn on.

Status: deployed and compiles; the generator builds in the real-generator preview. The spawn search itself has not been run in game.

### Terrain

`start: { island: true, cls: 'medium', floor: false, duneClear: false, radius: 75 }`. The island at the origin is now built as the medium class (recipe lowered 40 blocks, 75 m footprint, its own variant pick). The guaranteed rock base and the dune clearing from the previous section are off again. Medium grid cells still stay 350 m away from the origin so they do not overlap it.

Exposed rock in a 320 m view round the origin, at 2 m per pixel, of 25600 pixels, on ten seeds: 0, 109, 5167, 3687, 728, 433, 2071, 1163, 1643, 266. So about one seed in ten has no rock showing within 160 m of the origin, and several have very little.

### Plugin

- `WorldConfig` holds a transient default spawn provider taken from `IWorldGen.getDefaultSpawnProvider(seed)`; it is not saved in the world's config.
- `ArrakisWorldGen` wraps the built-in generator and returns `ArrakisSpawnProvider` from that method. Everything else is passed straight through.
- `ArrakisSpawnProvider`, on the first spawn request of a world load: walks columns outwards from the origin in square rings, up to 320 blocks. A column counts when its top block's id starts with `Rock_`. It takes the first such column whose four neighbours are also rock within one block of its height; if none turns up within 24 blocks beyond the first rock seen, it takes that first rock. Spawn is one block above the top block. With no rock at all it uses the surface at the origin and logs a warning. The result is logged ("Arrakis spawn set on ...") and kept until the world unloads.
- Side effect of the wrapper: the built-in generator plugin's `RemoveWorldEvent` handler checks for its own handle type and will not recognise the wrapped generator. `shutdown()` is still forwarded.

Not checked: how long the search takes when it has to go far (each new chunk it touches is generated on the spot; 320 blocks out is up to about 440 chunks), and whether a long search on first join causes any trouble.

## Dead ends

- Offline preview harness using server classes: `AssetManager` static init registers asset stores on `HytaleServer.get().getEventBus()`, which is null outside a running server. SOLVED in round 5 by supplying a bare server object with an event bus (see `RealPreview.java`).
- Trusting the Node model for node semantics: it shared my wrong assumption about the `Distance` return type, so preview and game disagreed (round 4 discs).

## Docs vs vanilla field names

- None recorded yet. All field names so far were copied from vanilla `Desert1_Stacks.json`, `Desert1_Rocky.json` and `Example_PositionCell_Density_Return.json`.

# Round 6 — caves and cave ore (0.7.0)

Server 0.7.0. Stage 1 (sandstone throughout, cave network, rock-only rule). Stage 2 (all large islands, openings) and stage 3 (ore) follow.

## Tools, and what 0.7.0 broke

- `tools/preview/RealPreview.java` ported to 0.7.0: `Density.process` is now `evaluate`, and the density builds need a `HytaleGenerator` plugin object (concurrency) that the fake server now creates.
- `graph.js` / `write.js` can no longer regenerate the biome: 0.7.0 removed the vanilla `Plains1/Plains1_Mountains` biome the recipes were copied from (and the shared `Plains1_Caves_Terrain` density). The deployed `Arrakis_Terrain.json` is now the source of truth, and this round patches it with `tools/preview/caves.js` from the pre-round copy `tools/preview/out/Arrakis_Terrain.pre-caves.json` (the full pre-round pack is in `tools/preview/out/pre-caves-HytaleGenerator-backup`). `node tools/preview/caves.js [packRoot]` rebuilds everything this round adds; `CAVE_PARAMS='{...}'` overrides parameters.
- `tools/preview/CaveProbe.java` with `probe.sh`, `runcave.sh`, `try.sh`: classify 2 m voxels (open air / rock / sand / cave air) with the game's own density classes, measure the caves and write slices. The same region before and after is compared for the rules.
- After the 0.7.0 cave fix (13 inlined vanilla cave nodes) the recipes still carved with the vanilla Plains1 cave graph in every size class. This round removes those and puts our own caves in.

## How the vanilla graph cave works (`GraphGenerators/Plains1_Caves.json`, as found)

- A graph generator builds nodes in 320 m cells, then edges, then gives each node content.
- Nodes come from `Positions` passes: a 20 m square grid (jitter 5) laid at the `Base` height (the "surface" set), and a 30 m grid (jitter 10) only where `Base-Simplex-192` > -0.5 (the "volcanic" set). `Move` passes shift each set up or down along Y by a noise-driven amount (surface -60..+50 m, volcanic -90..-30 m). So each set is a smooth sheet of nodes through the world.
- `ProximityConnector`: every selected node links to up to `Cap` random neighbours within `Range` (cap 1: one link each). Vanilla runs: all nodes 25 m; volcanic nodes 35 m; nodes with 0 or 1 links to nodes with 1 or 2 links (25 m; volcanic 40 m).
- Content (`Constant` content with tags): a density of `Range` 25 (surface) or 30 (volcanic) around the node, a material and prop distributions (decoration). The density is a `MultiMix` keyed by `CurveMapper(DistanceToGraphEdge)`: hollow (-3 / -2) near an edge, simplex noise at a middle distance, solid (1) beyond. Surface: radius 1-8 m (tunnels). Volcanic: up to 24 m (chambers, with a flat floor from a `YValue` curve).
- Biomes use it through a `Graph` density node (`ImportName` of the graph, background 1) inside `Min(cave, terrain)`; `YSampled` reads it every 3 m.
- It imports `World-River-Map`, `Base-Simplex-12/24/48/192`, and decoration assignments.

## What was copied and changed

All new assets are ours; the pack imports nothing from the game by name (audit: every `Imported` and `ImportName` in the pack resolves to an export in the pack).

- `Density/Arrakis_Simplex_24/48/96/192.json`: copies of `Base-Simplex-N` (2D simplex, 1 octave, scale N metres). 96 is the one vanilla size not used by the cave itself; it is used for the sheet noise.
- `Density/Arrakis_Large_Inside.json`: the large-island part of the biome's own `Arrakis_Inside` (regular islands and land masses; medium and small outcrops and the start island left out). About how many metres inside the shore a column is. This is what the generator already uses to tell a large land mass from a medium or small one.
- `GraphGenerators/Arrakis_Caves.json`: four node sheets laid at `Base` and moved to `Base + centre + noise * amp`:

| Sheet | Content | Centre (Base +) | Amp | Grid | Noise |
| --- | --- | --- | --- | --- | --- |
| Upper | tube (vanilla surface content) | +38 | 14 | 32 m | 192 |
| Middle | tube | 0 | 16 | 32 m | 96 |
| Deep | chamber (vanilla volcanic content) | -38 | 14 | 48 m | 192 inverted |
| Floor | chamber | -64 | 8 | 48 m | 96 inverted |

  Node jitter is 0.4 x the grid. Nodes only exist where `Arrakis_Large_Inside` > 8. `World-River-Map` is dropped. Links: inside each sheet 1 neighbour within 1.3 x grid, plus the vanilla all-nodes pass (range 40, cap 3) tying the sheets together, then the vanilla orphan passes (40; chambers 55). Decoration and material content are empty. Nodes run from about Base + 52 down to about Y 8.
- Material: `Rock_Sandstone` everywhere (the sand-level split into sandstone and red is gone; nothing else used it).
- Biome terrain: `Min(old terrain, carve)` with `carve = Mix(1000, YSampled(3)[Graph Arrakis_Caves], zone)`.

## How cave air is kept out of sand

Terrain is `Max(sand floor, dunes, Arrakis_Rock)`, so below Base sand fills everything that is not rock, and cutting a cave from the rock term alone would fill it with sand. The carve is applied to the final density instead (`Min`), and only inside `zone`, which is 1 only where all of these hold: `Arrakis_Rock` > 0 (inside the original rock body), `Arrakis_Large_Inside` > 8, Y between 4 and Base + 75, and the voxel 3 blocks straight above is rock too (`Slider`, "cover"). Outside the zone the carve value is 1000 and `Min` leaves the density as it was. So cave air wins inside the original rock body and sand is never touched; where a cave meets the edge of a body it ends at the sand wall. Y < 4 is never carved, so bedrock (Y 0-1) and a few blocks above it stay. `Mix` skips its second input where the zone is 0, so the graph is only read inside large-island rock.

## Preview measurements, one island (seed 1791436655886, island at (640, -480), 1000 m square, 2 m voxels, before and after in the same region)

- Rules: sand voxels before 8,008,471, changed 0. Open air that became solid 0. Rock carved 390,394 voxels, none in a column outside the large footprint.
- Rock body 35.1 million m3; cave air 3.12 million m3: 8.9% of the body, 9.5% inside the large footprint.
- Networks: 201 separate pieces, 19 of 4000 m3 or more. The largest holds 82.7% of the cave volume. Deepest cave air Y 5.
- Natural openings: 137 clusters, 30 with 6 or more boundary voxels. 8 networks have an opening; they hold 89.4% of the cave volume.
- Tops: of 67,653 rock columns in the large footprint, 0 are holes in the surface; 6.4% have cave air within 6 m below the surface.
- Slices: `tools/preview/out/caves/stage1_island_vertical_{x,z}.png` and `..._y{p,m}<n>.png` (horizontal, at Base + n or Base - n; the red line in the vertical ones is Base).
- Cost, preview only: the 22.5 million voxel region took 9.1 s before and 16.7 s after (+83%; caves are only read inside large-island rock). Real chunk times still to be measured in a world.

## Tried and did not work (the numbers above are not the first guess)

- Cover 12 (carve only where 12 blocks above is rock): kept the tops intact but sealed the network (1 opening cluster of 6+ voxels). Cover 6: 5 openings, 0.5% of the volume reachable. Cover 3 keeps 0 holes and still gives openings; cover 0 gives 124 openings but 3.2% of the top columns become holes.
- Fewer or smaller nodes (grid 28-32 tubes, 44-48 chambers, vanilla cap 1) cut the hollow share to 4-6%, but the network broke into hundreds of pieces (largest 5-9%). Cap 2-3 helps; the vanilla all-nodes pass with cap 3 over 40 m works best.
- Links only inside each sheet (no all-nodes pass) fragment it (largest 17-37%); extra random links down to the next sheet made vertical shafts and more hollow rather than a connected net.
- A tube core wider than vanilla (2 m instead of 1) and a 2 m `YSampled` made no real difference to connectivity.
- Square grids with jitter 8 give straight rectilinear tunnels; jitter 0.4 x grid looks organic.

## Not checked yet

- A real world: generation time, "Unused key" or "Couldn't find" warnings (the preview harness does not print asset-store warnings), what the openings look like from outside, spawn on the changed rock.
- Only one island measured; stage 2 measures three and all large islands, including whether each one is enterable.

## Stage 2 — caves on every large island, openings and connection checked

Status: deployed (jar of 15:12). Preview only; not seen in a world yet.

The caves were already generic (nodes appear wherever `Arrakis_Large_Inside` is high, so every regular island and land mass gets them); stage 2 is the measurement, plus one change.

### Tool

`tools/preview/islands.sh <pack> <seed> <cx> <cz> <size> <prefix>` (CaveProbe `ISLANDS=1`) finds every large-island footprint in a square (columns with `Arrakis_Large_Inside` above the threshold, 8-connected on a 20 m grid, `MIN_HA` hectares or more), voxelises each one at 2 m, and reports caves, networks, openings and whether the networks can be entered. `isl_try.sh` does the same on a scratch pack built with `CAVE_PARAMS`.

An "opening" is a cluster of 6 or more cave-air voxels that touch open air. "Enterable" here means: the largest network has at least one opening. "Reachable" is the share of cave volume in networks that have an opening.

### The one change: `largeInsideMin` 8 -> 2

With 8 (nodes and carving start 8 "metres" inside the shore), the guaranteed island on seed 42 at (650, -460), a low island with 10 Mm3 of rock, came out sealed: its largest network had no opening (12.8% of the cave volume reachable, 5 openings, all at Y 81-91). With 2 its largest network has one (about 39% reachable). Moving the Upper sheet down (centre +26 instead of +38) did not help more, so it stayed at +38. No entrance system was added.

### Results, 2 m voxels, `largeInsideMin` 2, every large island in a 4 km square round the origin on three seeds, and three huge land masses 10-14 km north on one

| Seed, island centre | Footprint | Rock Mm3 | Cave % of rock | Largest network | Openings (6+ voxels), Y | Networks of 20000 m3 or more, with opening | Reachable |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1791436655886 (-1760, -1740) | 19 ha | 18.7 | 10.1 | 58.5% | 19, 81-129 | 7, 2 | 82.0% |
| 1791436655886 (-1090, -1060) | 43 | 31.9 | 10.1 | 20.7% | 43, 81-125 | 23, 10 | 71.8% |
| 1791436655886 (660, -470) | 29 | 33.4 | 9.6 | 81.4% | 34, 83-131 | 8, 3 | 88.6% |
| 1791436655886 (-1790, 720) | 18 | 10.9 | 10.3 | 37.7% | 10, 85-125 | 10, 2 | 51.1% |
| 1791432644696 (-1640, -1090) | 27 | 27.2 | 9.5 | 76.5% | 29, 81-129 | 11, 2 | 79.8% |
| 1791432644696 (640, -440) | 29 | 24.0 | 9.7 | 66.5% | 22, 81-133 | 8, 2 | 80.4% |
| 1791432644696 (-1370, 580) | 44 | 56.6 | 8.9 | 87.5% | 25, 81-129 | 10, 3 | 91.7% |
| 42 (330, -1610) | 55 | 55.3 | 9.6 | 78.7% | 58, 81-131 | 18, 3 | 81.6% |
| 42 (-1560, -1330) | 28 | 33.1 | 9.5 | 89.3% | 22, 81-129 | 5, 2 | 90.3% |
| 42 (650, -460) | 29 | 10.0 | 10.1 | 27.1% | 6, 81-91 | 10, 3 | 38.9% |
| 42 (480, 980) | 29 | 23.1 | 9.7 | 37.0% | 31, 81-133 | 17, 4 | 64.7% |
| north, seed 1791436655886 (1010, -13800) | 252 | 265.1 | 9.4 | 34.1% | 274, 79-131 | 71, 16 | 82.4% |
| north (-1300, -11930) | 208 | 227.1 | 9.3 | 71.2% | 210, 81-133 | 71, 15 | 82.9% |
| north (170, -10380) | 148 | 154.3 | 10.0 | 80.4% | 217, 81-129 | 31, 6 | 88.8% |

- All 14 islands: the largest network has an opening. Deepest cave air Y 5 everywhere.
- The weakest are thin islands: (42, (650, -460)) 39% reachable, (1791436655886, (-1790, 720)) 51%, (42, (480, 980)) 65%. Sealed networks of 20000 m3 or more are common (the 43 ha island at (-1090, -1060) has 13 of 23 sealed), mostly chambers on the Deep and Floor sheets with no tunnel to the rest. Sealed volume per island: 0.09 to 2.8 Mm3.
- Many openings are at Y 81-85: tunnel mouths at the foot of a cliff where the Middle sheet (Base +/- 16) meets the shore. The sand around them is not carved.
- Rules (seed 42 at (650, -460) and seed 1791432644696 at (-1370, 580), same region before and after): sand voxels changed 0; open air that became solid 0; rock carved outside the large footprint 0.
- Tops: 0 or 1 holes in the surface per 26,000-105,000 rock columns; 5-7% of columns have cave air within 6 m below the surface (the cover is 3 blocks).
- Hollow share: 8.7-10.3% of the rock body on every island. Cost: real chunk time in the v40 world was about 24 ms per chunk at 1000 chunks (reports 28.3 at 100, 25.1 at 500, 24.4 at 1000), against about 19 ms in the same generator without caves.

### Tried, and not kept

- Densifying links (all-nodes pass cap 4 range 44, orphans 50): on the thin island at (-1790, 720) the largest network went from 26% to 43% of the cave volume but the hollow share rose from 10.2% to 12%. Not kept: the connected share depends mostly on how much rock the island has at the sheet heights, not on the links.
- Lowering the Upper sheet by 12: no better than `largeInsideMin` alone.

### Not checked

- Medium and small bodies are covered only by the "no carving outside a large footprint" count (0). A medium outcrop whose footprint overlaps a large island's would be carved where they overlap.
- No world yet: warnings, real timings of a first join near a cave entrance, spawn on a carved island.

## Stage 3 — ore on cave surfaces

Status: deployed (jar of 17:03). Measured with the game's own generator, not the density preview, because ore is placed by the prop stage and written into real blocks. Not seen in a world by eye yet.

### The real-engine harness (new, `tools/worldscan/`)

`scan.sh <name> <seed> <minX> <minZ> <nx> <nz> [modJar]` starts a headless server in a scratch folder (`--auth-mode offline`, the Dunes of Arrakis generator, the given seed), with a small test-only plugin (`WorldScanPlugin`, not part of the mod). The plugin generates the block of the world, reads every block with the engine's `SectionReader` and writes a dump; `WorldAnalyze.java` reads the dump. `SPAWN=1` also asks the world's spawn provider for a spawn point and reads the blocks under it. Facts learned:

- Chunks must be read right after they are generated; unloaded ones read back as air (first runs of 400 columns came out empty).
- Generation time per 32 x 32 column in this harness is not comparable with the in-game reports, but it is comparable between builds.
- Two dumps of the same region from two builds can be compared block by block (`WorldAnalyze a.bin b.bin`).

### How ore is attached to the cave graph, and what the engine does with it

- A biome `Props` entry of type `Graph` (ContentLayer Priority0, `GraphGenerator.ImportName` Arrakis_Caves) is what asks the graph for the prop distributions in its nodes. Without that entry the nodes' `PropDistributionContent` is ignored. The Arrakis biome had `Props: []`; it now has one entry.
- Each node's content carries `PropDistributionContent`: a `Union` of five `Assigned` distributions (one per ore the node's height can host), `Range` 12. The positions are wrapped in `Anchor`, so they are in the frame of the node: their Y is the node's Y and they fall inside its range. (Positions in the world frame are at Y 0, outside every node's range: nothing was placed.)
- Only deep nodes carry ore. A node is deep when `Arrakis_Large_Inside` > 40 (about 40 m inside the shore) and `Arrakis_Rock` is positive 24 blocks above it and 32 blocks away to the north, south, east and west at its own height (`DensityDelimited` node selector with `Slider` reads of the rock density). So ore stays away from the flanks and the openings. A node gets only the ores whose height tier, widened by the scan distance of 10, reaches its height (up to 10 height bands per sheet, so most nodes carry one to three ores).
- Each assignment (`Assignments/Arrakis_Ore_<Name>.json`) is one `Density` prop with its own pattern, scanner and mask, like vanilla `Desert1_Iron`:
  - Pattern: `And[ Or[ Wall (Rock_Sandstone beside an air block), Ceiling (Rock_Sandstone above an air block) ], FieldFunction on BaseHeight "Bedrock" (the tier, absolute Y) ]`. The origin is therefore an air block touching a sandstone wall or ceiling: ore starts at a cave surface. (The pattern type for a density test is `FieldFunction`, not `Density`: the asset failed to decode with `Density`.)
  - Scanner: `ColumnLinear`, relative to the position, 10 blocks below to 10 above, first match from the bottom, result cap 1.
  - Mask: `DontPlace` Empty, `DontReplace` anything that is not `Rock_Sandstone` (`Inclusive: false`). Ore never replaces air, sand, bedrock or other ore.
  - Blob: copied from the vanilla recipe (below).
- Floors are not used as a start surface: a bottom-up scan would always find the floor first. Walls and ceilings only.

### Vanilla numbers kept

| Asset | Block | Copied from | Blob (vanilla) | Range | Positions in a node | Tier (Y; Base 80, Bedrock 0) |
| --- | --- | --- | --- | --- | --- | --- |
| Arrakis_Ore_Copper | Ore_Copper_Sandstone | Plains1_Copper | simplex-12 +/-0.3 plus a distance falloff +/-0.7 reaching 10, ore above 0.25 | 2 (vanilla bounds -1..+2) | Mesh2D 12 m, jitter 0.5 (stand-in) | 65-140 (Base -15 up) |
| Arrakis_Ore_Iron | Ore_Iron_Sandstone | Desert1_Iron | simplex-3D scale 4 +/-0.3 plus a cube falloff +/-0.7 reaching 3 | 2 | Mesh2D 22 m, jitter 0.5 | 45-80 (Base -35 to Base) |
| Arrakis_Ore_Silver | Ore_Silver_Sandstone | Taiga1_Gold | simplex-3D 0..1 -> -0.5..0 plus a cube falloff -0.5..1 reaching 5 | 3 (vanilla bounds +/-3) | grid 32 m, jitter 16 | 25-55 (Base -55 to -25) |
| Arrakis_Ore_Gold | Ore_Gold_Sandstone | Taiga1_Gold | same | 3 | grid 32 m, jitter 16 | 10-40 (Base -70 to -40) |
| Arrakis_Ore_Thorium | Ore_Thorium_Sandstone | Desert1_Thorium | same blob as iron | 2 | Mesh2D 22 m, jitter 0.5 | 5-20 (above Bedrock to Base -60) |

Differences from vanilla, all forced by the engine:

- Copper and gold/silver were meant to keep the vanilla `Bounds` and `Locator` form. Attached to a graph node, both placed nothing in this build (tested alone: the same blobs as `Range` props, with the pattern and scanner inside the prop, work). So their blobs are `Range` props. Copper's vanilla bounds are -1..+2 (a 4 x 4 x 4 block); a symmetric range of 2 gives a 5 x 5 x 5 block, so a copper deposit can be a little larger than vanilla's (biggest seen 58 blocks, vanilla's cap 64 in a 4 cube).
- Vanilla copper's spacing depends on a bed called `Copper_Count` that Arrakis does not have. Copper positions are a stand-in (a 12 m Mesh2D), so copper is by far the most common ore. Vanilla iron and thorium use 22 m and gold 32 m, kept.
- Each vanilla recipe's own placement test was replaced by the one rule above, as asked. The pattern is tested at the origin; blob blocks reach up to 2-3 blocks beyond it, so a few ore blocks lie 2 above or below the tier edges (Y 4-21 for thorium against 5-20).

### Measurements (real engine, 640 x 640 block regions, Y 0-200)

Three regions on three seeds: A seed 1791432644696, x -1696..-1056, z 256..896 (a 44 ha island); B seed 42, x 0..640, z -1920..-1280 (north end of a 55 ha island); C seed 1791436655886, x 352..992, z -800..-160 (a 29 ha island). Ore compared with the same pack without ore, caves compared with the same pack without caves.

| Rule | A | B | C |
| --- | --- | --- | --- |
| Blocks changed by the caves, other than Rock_Sandstone to air | 0 | 0 | 0 |
| Cave air (Rock_Sandstone to Empty) | 4.21 Mm3 | 3.39 Mm3 | 3.12 Mm3 |
| Blocks changed by the ore, other than Rock_Sandstone to ore | 0 | 0 | 0 |
| Ore blocks | 11,108 | 5,717 | 4,468 |
| Ore touching cave air | 65.0% | 64.9% | 65.7% |
| Ore with a sky-air neighbour | 0 | 0 | 0 |
| Ore within 10 steps of an opening | 0 | 4 | 0 |
| Ore more than 3 blocks from any air | 111 | 58 | 40 |
| Deposits not touching air | 24 of 1,026 | 14 of 547 | 9 of 445 |

Per ore (deposits; blocks per deposit smallest / typical / largest; lowest and highest block, Base 80):

| Ore | Region A | Region B | Region C |
| --- | --- | --- | --- |
| Copper | 546; 1 / 14 / 53; Y 63-132 | 274; 1 / 14 / 47; Y 63-129 | 219; 1 / 14 / 48; Y 63-130 |
| Iron | 139; 1 / 4 / 12; Y 44-81 | 114; 1 / 4 / 11; Y 44-81 | 87; 1 / 5 / 13; Y 45-81 |
| Silver | 127; 1 / 5 / 14; Y 23-57 | 100; 1 / 6 / 15; Y 23-56 | none (no rock that low in the footprint) |
| Gold | 134; 1 / 5 / 15; Y 8-41 | none | 98; 1 / 5 / 15; Y 8-42 |
| Thorium | 80; 1 / 4 / 12; Y 4-21 | 59; 1 / 6 / 13; Y 4-21 | 41; 1 / 5 / 14; Y 4-21 |

- Depth histograms put every ore inside its tier (see the Y ranges); copper is spread over Base -20 to +50, iron Base -40 to 0, silver Base -60 to -30, gold Base -80 to -40, thorium Base -80 to -60.
- Deposit sizes: only copper reaches the 7-20 block target as a typical size; iron, silver, gold and thorium deposits are typically 4-6 blocks (the largest 11-15), because half the blob around an air origin is in air. Not tuned, as asked.
- The 4 ore blocks near an opening in region B are one deposit at (140, 88, -1771), at Base + 8 on the foot of the island.
- Spawn (real engine, `SPAWN=1`, five seeds): the spawn provider found rock every time, with solid sandstone under the feet and three air blocks above; in 1.4 to 2.4 s: seed 1791432644696 (1.5, 90, 1.5); 42 (0.5, 99, 0.5); 1791436655886 (20.5, 118, 24.5); 1791831155772 (9.5, 80, 9.5), the same as in the Arrakis v40 log; 1791826253952 (-72.5, 81, -1.5), the same as in the Arrakis v32 log.
- Warnings in the server log with the final pack: none from this mod beyond the two known ones (the `Dunes_of_Arrakis` key format; the three duplicate export names of the Gorges recipe). No `Unused key` or `Couldn't find` for the new assets.

### Cost (real engine, ms per 32 x 32 column in the harness; same machine, one build after another)

| Build | A | B | C |
| --- | --- | --- | --- |
| Before this round (vanilla Plains caves) | 53.1 | 74.3 | 34.1 |
| Caves, no ore | 53.5 | 73.6 | 35.8 |
| Caves and ore | 71.4 | 88.1 | 44.7 |

- Caves cost about 0 to 5% over the vanilla caves they replace; ore adds 20 to 33%. This was not what the first version of the carve cost: it re-read the rock recipe at every voxel of a large island (once for the zone, once 3 blocks up), +40% over the old build. The zone tests are now ordered so the rock reads only happen inside a hollow of the cave graph; the output is the same (region A: the first reordering moved 635 edge voxels of 4.2 million, the second none).
- No `Took too long` line in any of the scan logs. In the game, the log's "Performance Report" is the number to compare (the v40 report with the stage 1 caves was 24.4 ms per chunk over 1000 chunks, against 18.9 before).

### Not what was asked, or not working

- Ore within 10 blocks of an opening is not strictly zero (4 blocks in region B, 0 in A and C). The node-level "deep" test makes it rare, not impossible.
- A third of the ore blocks do not touch air: the blob extends up to 2-3 blocks into the wall behind the exposed face, as vanilla blobs do (65% touch air); 1% are more than 3 blocks from any air (a blob whose centre air block was a thin crack).
- Medium and small bodies: the carve is zero outside the large footprint (preview rule check), and in the real engine all changed blocks of a caves-vs-no-caves comparison are Rock_Sandstone to air, but I did not separately count them by body size.
- Sealed islands (a correction to stage 2). The 14 islands in the stage 2 table were all enterable. On a fourth seed, 1791831155772 (the Arrakis v40 world), two of six large islands in a 4 km square are not: the one at (580, 1100) is a buried mound whose rock only rises to Y 105 over 4.6 ha (1 opening, 0.2% of the cave volume reachable) and the spire island at (1260, -1710) has a largest network with no opening (14%; its rock is mostly buried, with a spire to Y 175; 8 openings in smaller pieces). Lowering `largeInsideMin` to -15 or -30 and raising the Middle sheet to Base +10..+14 changed nothing for them. Denser links (all-nodes pass range 50, cap 3, orphans 55) make the spire island enterable (56% reachable) but raise the hollow share from 10% to 12.6%; the buried mound stays sealed. Not applied; the choice is yours.
- Seed 1791831155772, the Arrakis v40 world: the island nearest the origin that is enterable is the one at (640, -430), about 770 m from the spawn at (9.5, 80, 9.5). Real-engine scan of x 352..992, z -800..-160 on that seed: cave mouth (611, 115, -423); deep chamber (a 7-block cube of air) at (672, 16, -480); deposits of 5 or more blocks nearest the middle of the region: copper (679, 90, -467), iron (732, 61, -498), silver (720, 38, -447), gold (675, 18, -522), thorium (622, 15, -491).

### Imports left

None from the game by name. Every `Imported` / `ImportName` in the pack (`Arrakis_Simplex_12/24/48/96/192`, `Arrakis_Large_Inside`, `Arrakis_Rock`, `Arrakis_Cave_Graph`, `Arrakis_Inside`, `Arrakis_Pick*`, the `Arrakis_V*` recipe exports, `Arrakis_Caves` as the graph, `Arrakis_Ore_*` as the assignments) resolves to an export in this pack; checked by walking the JSON.

### Tried and did not work

- Ore props with positions in the world frame (Y 0): nothing placed.
- Biome-level ore (no graph) with a `Locator`/`Mask` wrapper placed nothing either; the vanilla `Desert1_Iron` imported by name did, and so did a `Density` prop with its own pattern and scanner. So the wrapper, not the pipeline, was the problem.
- Scanner over the whole height tier (`RelativeToPosition` false): a scan from a deep node could find a ceiling anywhere in the column, including near the openings; 1,363 ore blocks had a sky-air neighbour. Scanning only 10 blocks around the node and moving the tier into the pattern brought it to 19, then deep-node tests (inside 40, rock 24 above, 32 to the sides) to 0.
- Making the zone tests cheaper by ordering them: the carve is identical, the cost came back to the vanilla level.

# Round 7 — ore that wears down (plugin)

Rule: in a Dunes of Arrakis world, the first five breaks of a listed ore block (Ore_Copper/Iron/Silver/Gold/Thorium_Sandstone) pop one ore and leave the block whole; the sixth breaks it normally. Code in `OreBreakRules` (the list, the world test, the tool test), `OreBreakSection` (the saved counts), `OreBreakCounts` (reading and writing them), `OreBreakCore` (the engine calls), `OreBreakSystems` (the event hooks), `OreBreakSelfTest` (development only), wired in `DunesOfArrakisPlugin`. No assets changed.

## What the 0.7.0 jar does (read from the bytecode of `BlockHarvestUtils`, `BreakBlockInteraction`, the block health module)

- **Survival mining** is `BreakBlockInteraction` -> `performBlockDamage` -> `damageSingleBlock`. That method first drops out (returns) for a tool that cannot mine the block, then raises `DamageBlockEvent(item, block, type, health, damage)`. The first number is the block's health before the hit, 1 = whole, not damage so far (verified: a block damaged by 0.3 reports 0.7); the second is the size of the hit after the tool's power. If the event is cancelled the engine calls `invalidateBlock` (re-sends the block to the clients) and returns. Otherwise it takes `damage` off the health (`BlockHealthSection.damage`) and, when the new health is 0 or below (`BlockHealth.isDestroyed`), calls `performBlockBreak`.
- **`performBlockBreak`** raises `BreakBlockEvent` only when there is an actor (a ref); with no actor it raises `EnvironmentBreakBlockEvent`, which is not cancellable. A cancelled `BreakBlockEvent` also calls `invalidateBlock`. After either, the engine removes the block and spawns the drops.
- **Creative** is told apart in `BreakBlockInteraction`: `Player.getGameMode() == GameMode.Creative` makes it call `performBlockBreak` directly, so a Creative break never raises a `DamageBlockEvent`; it raises a `BreakBlockEvent` with the player as the actor.
- **Tool rule**: the engine itself looks for a tool spec of the block's gather type and at least its quality (`getSpecPowerDamageBlock`); each ore has its own gather type (OreCopper, OreIron, OreSilver, OreGold, OreThorium, quality 0). With no matching spec a hit only scratches the block: bare hands do about 0.002 per hit (measured), so the damage event still fires, with a tiny hit.
- **Health** moved: `BlockHealthChunk.damageBlock` of 0.6.8 is gone; health is `BlockHealthSection` on the section entity. `BlockHealthSection.damage` with a negative amount repairs, but when the block is made whole it clears the record and the pending update, so the clients are never told; the engine sends `UpdateBlockDamages(section x, y, z, [BlockDamageCmd(index, health, delta)])` for every other change, and the rule sends the same packet itself.
- Handler order: an `EntityEventSystem` can ask to run last (`getDependencies()` returning `RootDependency.lastSet()`, as the engine's own replication system does), and `EventSystem.shouldProcessEvent` skips events that are already cancelled. So a break or hit that another mod has cancelled (Subfiefs in a claim) never reaches the rule: no ore, no count.

## Which event is hooked, and why

The **damage event**, not the break event. At the damage event the block still has health left, so cancelling the final hit means the engine never starts a break: no `BreakBlockEvent`, no removal, no drops, and the engine re-sends the block. Cancelling at the break event would come after the health is already 0 and the clients have been told. On the final hit the rule cancels the event, sets its damage to 0 (as Subfiefs does), pops the ore, resets the block's health to whole and sends the clients that reset (`OreBreakCore.makeWhole`). The hit that breaks is the only one replaced, so mining time, animation, sound and particles of everything before it are the engine's. The next hits start from a whole block, so a free break takes a full mining time like a real one.

## How survival mining is told apart from everything else

- Player in survival: a `DamageBlockEvent` raised for an entity with a `PlayerRef`, `Player.getGameMode()` not Creative, and a held item that is a tool that can mine the block (same gather type and quality test as the engine, `OreBreakRules.toolCanMine`). Only then does a breaking hit become a free break.
- Player in Creative: no damage event; the `BreakBlockEvent` is let through and the count is forgotten once the block is gone.
- Anything else damaging the block (an entity that is not a player, or no actor at all: explosions, scripts): if the hit would break the block, the event is cancelled and the block is removed without a drop, sound or effect (`BlockOperations.setBlock` with air, as a command would) and its count forgotten. A `BreakBlockEvent` from a non-player is cancelled and the block removed the same way. A break with no actor (`EnvironmentBreakBlockEvent`) cannot be cancelled, so the items it spawns at the block (the ore and the rock of that block's own drop list, within 1.6 blocks, for one second) are removed as they appear.
- A command or mod that sets the block to something else directly has no event; that is what the stored block id and the sweep (below) are for.

## Where the count is stored, and when it is written

On the chunk itself: `OreBreakSection`, a component of the section entity, registered through the chunk store registry with a codec (id `Arrakis_OreBreaks`), the same way the engine keeps `BlockHealthSection`. It is a list of strings `"<index in section>:<count>:<block id>"`; only blocks mined at least once have an entry, and an empty section writes nothing. It is saved with the chunk: every change marks the `ChunkSection` as needing saving, so the engine's own saver writes it (on its schedule and at shutdown) and unloads it with the chunk. Counts are per world because chunks are. Two players share one count. Growth is bounded: an entry stores which block it is for and is only ever read back for that block; a mismatch is dropped on read; when a section loads, `SectionSweep` drops every entry whose block is not there any more; the sixth break, a Creative break, any other removal, and placing a block there all remove it. A break that another system cancels leaves it (the check runs a tick after the event and only removes the entry when the block is really gone).

## What was copied from the Compactor

`dropsOf` (the engine's drop generation from the resolved block type, including drop lists; both are the same call) and the idea of popping an item out of the face under the crosshair (`ItemComponent.generateItemDrop` with a push along the face normal; the ray against the block is a reduced copy of `BeamMath.entry`/`offFace` as `OreFace`). Block health: not copied. The Compactor's `BlockHealthChunk` calls do not exist in 0.7.0; the section API is used instead. The ore popped is the first non-rock item of the block's drop list (one of it), so the list can change without touching the rule; if a block's drops contain no ore it is not worn down.

## Flicker

I cannot see this: there is no client in a headless server, so no screen to watch. What I can say from the engine code is why this should not flicker: the damage event is cancelled before any health is taken, the engine re-sends the block on a cancelled damage event, the block was never broken or removed on the server, and the health reset is sent explicitly. If the block still looks gone or cracked for a moment in the game, tell me what you see and I will move the hook or send the crack reset differently.

## Checks (headless server, `tools/worldscan/oretest.sh`, which runs `OreBreakSelfTest` in a Dunes of Arrakis world, stops the server, starts it again and runs a second phase)

A player's own hit cannot be made without a client, and a player entity built by hand crashes the world thread (the engine's own systems expect a connection), so the player handlers are called directly as static methods with a bare `Player` component (`OreBreakSystems.damageByPlayer`, `breakByPlayer`, `placeByPlayer`) and the same engine objects (world, sections, health, items). The event dispatch itself (registration, last-in-order, query) is not exercised; the server starts with all nine systems registered and no warning. Results of the final run, all passing:

- Each of the five ores: its normal drop is the ore and `Rock_Sandstone_Cobble`; five breaking hits each leave the block in place, pop exactly one ore (`Ore_Copper` ... `Ore_Thorium`, quantity 1, never the cobble), leave full health and give a count of 1 to 5; a hit that does not break the block is not taken over and does not count; the sixth breaking hit is left to the engine with the count still 5; once the block is gone the count is forgotten.
- The player handler with a bare Player: five cancelled-and-zeroed hits with one ore each, the sixth not cancelled with its damage kept; bare hands and a sword are not taken over and do not count; Creative is left alone; a block placed where a count was clears it; a break event keeps the count while the block is there and clears it once it is gone; a break that did not remove the block keeps it.
- Block health: a block cracked to 0.4 is whole again after `makeWhole`.
- Not a player: the engine's own damage call with an iron pickaxe and no actor removes the ore block, drops nothing and forgets the count; bare hands do not (the engine only scratches it, 0.998); a break with no actor removes the block and the drops it spawned are gone a second later.
- Not listed: `Rock_Sandstone` is not taken over and the engine breaks it as usual. Not Dunes of Arrakis: a flat-world provider and no world both fail the world test (a second world could not be created in the harness; the test is the provider check the rule uses).
- Save and reload: a gold ore with 3 free breaks is saved; after a restart of the server the block is there with a count of 3; a stale entry (its block replaced behind the rule's back) is gone after the section loads; an ore of another kind put where the counted block was starts at 0 and the old entry leaves the section.
- The server logs of both runs: no SEVERE line, no exception, nothing from the rule. (One WARN, "Task took 55 ms", is the self test's own task on the world thread.)
- Linkage scan of the jar against the 0.7.0 server jar: empty.

Not checked and left for the game: the break animation and sound, flicker, a crack left on screen, Subfiefs claims (the order is arranged, not run), bare hands and the wrong tool on a real client, the look-based pop position (`TargetUtil.getLook`, wrapped so a failure puts the ore above the block), two real players on one block, and a real chunk unload with a player holding a count. The self test is dormant unless the server is started with `-Darrakis.oretest=<phases>`.
