// Cave round: builds the Arrakis cave network and patches the biome.
//   node tools/preview/caves.js            writes the pack in src/main/resources
//   node tools/preview/caves.js <packRoot> writes into another pack root (scratch copy for experiments)
// Input is tools/preview/out/Arrakis_Terrain.pre-caves.json (the biome as it was before this round). graph.js cannot regenerate the
// rock any more: 0.7.0 removed the vanilla Plains1_Mountains biome it copies from. So this script patches the finished biome instead.
// Everything vanilla that the caves use is copied under an Arrakis_ name; nothing is imported from the game by name.
const fs = require('fs');
const path = require('path');

const PARAMS = {
  base: 80,            // Base (sand level) in WorldStructures/Dunes_of_Arrakis.json
  floorY: 4,           // no carving below this height: Bedrock is Y 0..1, a few blocks of rock stay under every cave
  ceilingAboveBase: 75, // no carving higher than this above Base, whatever the nodes do
  cover: 3,            // a voxel is carved only if the voxel this many blocks straight above it is rock too, so cave air never reaches the top of the island; flanks and cliffs still open
  largeInsideMin: 2,   // cave nodes only where Arrakis_Large_Inside (about metres inside the shore of a large island) is above this
  // Node sheets, from the top down. Each is a jittered grid of nodes laid at Base and moved up or down by a slow noise:
  // height = Base + centre + noise * amp. kind: tube = the vanilla "surface" content (radius 1-8), chamber = the vanilla "volcanic" content (radius up to 24).
  sheets: [
    { name: 'Upper',  kind: 'tube',    centre: 38,  amp: 14, grid: 32, jitter: 8, noise: 192, invert: false },
    { name: 'Middle', kind: 'tube',    centre: 0,   amp: 16, grid: 32, jitter: 8, noise: 96,  invert: false },
    { name: 'Deep',   kind: 'chamber', centre: -38, amp: 14, grid: 48, jitter: 12, noise: 192, invert: true },
    { name: 'Floor',  kind: 'chamber', centre: -64, amp: 8,  grid: 48, jitter: 12, noise: 96,  invert: true }
  ],
  // Connections. Inside each sheet a node links to `cap` neighbours within grid * linkFactor; allShort is the vanilla all-nodes pass (any sheet) that ties the sheets together.
  // Vanilla: short 25 cap 1, chambers 35, orphans 25 and 40.
  tubeCore: 1,         // vanilla: hollow within 1 m of a tunnel edge, shaped by noise out to 8 m
  ySample: 3,
  jitterFactor: 0.4,     // if set, node jitter = grid * this (overrides each sheet's jitter)
  cap: 1, linkFactor: 1.3, allShort: { range: 40, cap: 3 }, orphanRange: 40, chamberOrphanRange: 55,
  // Extra links between sheets, so the network is one piece. chance = share of nodes of the upper sheet that look for a partner below.
  crossLinks: null,
  // Ore (stage 3). null = no ore. Ore sits on the walls and ceilings of cave nodes that are deep inside a large island.
  ore: {
    scan: 10,          // the scanner looks this far above and below the node
    range: 12,         // ore props of a node reach this far from it
    deepInside: 40,    // only nodes this far inside the shore (Arrakis_Large_Inside) carry ore
    deepCover: 24,     // and with rock this many blocks straight above them
    deepSide: 32,      // and with rock this far away to the north, south, east and west, at their own height
    // minY / maxY are absolute Y (Base 80, Bedrock 0). blob: which vanilla blob shape. grid: metres between deposit positions (vanilla: iron and thorium 22 on a Mesh2D with jitter 0.5; gold 32 with jitter 16).
    types: [
      { name: 'Copper',  block: 'Ore_Copper_Sandstone',  blob: 'copper', minY: 65, maxY: 140, grid: 12 },
      { name: 'Iron',    block: 'Ore_Iron_Sandstone',    blob: 'iron',   minY: 45, maxY: 80,  grid: 22 },
      { name: 'Silver',  block: 'Ore_Silver_Sandstone',  blob: 'gold',   minY: 25, maxY: 55,  grid: 32, bedrockGrid: true, range: 24 },
      { name: 'Gold',    block: 'Ore_Gold_Sandstone',    blob: 'gold',   minY: 10, maxY: 40,  grid: 32, bedrockGrid: true, range: 24 },
      { name: 'Thorium', block: 'Ore_Thorium_Sandstone', blob: 'iron',   minY: 5,  maxY: 20,  grid: 22 }
    ]
  }
};
const P = Object.assign({}, PARAMS, process.env.CAVE_PARAMS ? JSON.parse(process.env.CAVE_PARAMS) : {});

const here = __dirname;
const pack = path.resolve(process.argv[2] || path.join(here, '..', '..', 'src', 'main', 'resources'));
const gen = path.join(pack, 'Server', 'HytaleGenerator');
const baselineFile = path.join(here, 'out', 'Arrakis_Terrain.pre-caves.json');
const write = (rel, obj) => { const f = path.join(gen, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n'); };

const c = v => ({ Type: 'Constant', Skip: false, Value: v });
const imported = name => ({ Type: 'Imported', Skip: false, Name: name });
const curve = pts => ({ Type: 'Manual', Points: pts.map(([i, o]) => ({ In: i, Out: o })) });
const node = (type, inputs, extra) => Object.assign({ Type: type }, extra || {}, { Skip: false, Inputs: inputs });
const copy = o => JSON.parse(JSON.stringify(o));

// ---- Copied vanilla simplex densities (Base-Simplex-N: 2D simplex noise, one octave, scale N metres) ----
for (const n of [12, 24, 48, 96, 192]) {
  write(`Density/Arrakis_Simplex_${n}.json`, { Type: 'Exported', ExportAs: `Arrakis_Simplex_${n}`, SingleInstance: true, Skip: false,
    Inputs: [{ Type: 'Cache', Skip: false, Capacity: 3, Inputs: [{ Type: 'SimplexNoise2D', Skip: false, Lacunarity: 2, Persistence: 0.5, Octaves: 1, Scale: n, Seed: `Arrakis_Simplex_${n}` }] }] });
}

// ---- Large-island footprint, copied out of the biome's own Arrakis_Inside: about metres inside the shore of a regular island or land mass ----
const biome = JSON.parse(fs.readFileSync(baselineFile, 'utf8'));
let inside = null;
(function find(n) { if (inside || !n || typeof n !== 'object') return; if (n.Type === 'Exported' && n.ExportAs === 'Arrakis_Inside') { inside = n; return; } for (const v of Object.values(n)) find(v); })(biome.Terrain);
if (!inside) throw new Error('Arrakis_Inside not found in the baseline biome');
const insideMax = inside.Inputs[0].Inputs[0].Inputs[0]; // YOverride > Cache > Max
if (insideMax.Type !== 'Max' || insideMax.Inputs.length !== 5) throw new Error('unexpected Arrakis_Inside layout');
const comment = n => (n.Inputs[1].$Comment || '');
const large = [insideMax.Inputs[0], insideMax.Inputs[4]];
if (!/Regular island/.test(comment(large[0])) || !/land-mass/.test(comment(large[1]))) throw new Error('Arrakis_Inside inputs are not in the expected order');
write('Density/Arrakis_Large_Inside.json', { $Comment: 'About how far inside the shore of a large island or land mass a column is (regular islands: metres; huge land masses: less than a metre per metre). Medium and small outcrops are left out.',
  Type: 'Exported', ExportAs: 'Arrakis_Large_Inside', SingleInstance: true, Skip: false,
  Inputs: [node('YOverride', [node('Cache', [node('Max', copy(large))], { Capacity: 3 })], { Value: 0 })] });

// ---- The cave graph ----
const contentSet = tags => ({ Type: 'Content', Skip: false, ContentPredicate: { Type: 'Set', Skip: false, ContentTags: tags } });
const tagContent = (tags, extra) => ({ Type: 'Content', Skip: false, Content: { Type: 'Constant', Skip: false, Content: Object.assign({ ContentTags: tags }, extra || {}) } });
const selectorPass = (selector, action) => ({ Type: 'NodeAction', Skip: false, NodeAction: { Type: 'Selector', Skip: false, NodeSelector: selector, NodeAction: action } });
const connector = (range, seed, selector, cap) => ({ Type: 'ProximityConnector', Skip: false, Range: range, Cap: cap || 1, Seed: seed, NodeSelector: selector });
const all = () => ({ Type: 'All', Skip: false });
const orSel = list => list.length === 1 ? list[0] : ({ Type: 'Or', Skip: false, NodeSelectors: list });
const tagOf = s => 'Arrakis_Caves_' + s.name;
const keys = [{ Value: 0, DensityIndex: 0 }, { Value: 1, DensityIndex: 1 }, { Value: 2, DensityIndex: 2 }];
const distanceCurve = pts => ({ Type: 'CurveMapper', Skip: false, Curve: curve(pts), Inputs: [{ Type: 'DistanceToGraphEdge', Skip: false }] });
// Vanilla "surface" content: a tunnel, solid beyond 8 m of an edge, hollow within 1 m, shaped by simplex-24 in between.
const tubeDensity = () => ({ Type: 'MultiMix', Skip: false, Inputs: [c(-3), imported('Arrakis_Simplex_24'), c(1), distanceCurve([[P.tubeCore, 0], [P.tubeCore + 1.5, 1], [P.tubeCore + 7, 2]])], Keys: keys });
// Vanilla "volcanic" content: a chamber, hollow within the edge, solid beyond 24 m, flat floor below the node.
const chamberDensity = () => ({ Type: 'MultiMix', Skip: false, Inputs: [c(-2),
  { Type: 'Mix', Skip: false, Inputs: [
    { Type: 'Normalizer', Skip: false, FromMin: -1, FromMax: 0, ToMin: -1, ToMax: 1, Inputs: [{ Type: 'Inverter', Skip: false, Inputs: [{ Type: 'Abs', Skip: false, Inputs: [imported('Arrakis_Simplex_24')] }] }] },
    imported('Arrakis_Simplex_48'),
    { Type: 'CurveMapper', Skip: false, Curve: curve([[10, 0], [-10, 1]]), Inputs: [{ Type: 'Anchor', Skip: false, Reversed: false, Inputs: [{ Type: 'YValue', Skip: false }] }] }] },
  c(1), distanceCurve([[0, 0], [7, 1], [24, 2]])], Keys: keys });
const contentFor = s => s.kind === 'tube'
  ? { DensityContent: [{ Range: 25, ContentLayer: 'Priority0', Density: tubeDensity() }], MaterialContent: [], PropDistributionContent: [], PositionsContent: [] }
  : { DensityContent: [{ Range: 30, ContentLayer: 'Priority0', Density: chamberDensity() }], MaterialContent: [], PropDistributionContent: [], PositionsContent: [] };


// ---- Ore ----
const material = name => ({ Solid: name, SolidBottomUp: false });
const noise3 = (lo, hi, from) => ({ Type: 'Normalizer', Skip: false, FromMin: from, FromMax: 1, ToMin: lo, ToMax: hi, Inputs: [{ Type: 'SimplexNoise3D', Skip: false, Lacunarity: 2, Persistence: 0.5, Octaves: 1, ScaleXZ: 4, ScaleY: 4, Seed: 'A' }] });
const cube = (lo, hi, reach) => ({ Type: 'Normalizer', Skip: false, FromMin: -1, FromMax: 1, ToMin: lo, ToMax: hi, Inputs: [{ Type: 'Anchor', Skip: false, Reversed: false, Inputs: [{ Type: 'Cube', Skip: false, Curve: curve([[0, 1], [reach, -2]]) }] }] });
const oreBlob = (ore, block) => {
  const solid = { Type: 'Solidity', Solid: { Type: 'Constant', Material: material(block) } };
  if (ore.blob === 'iron')   // Desert1_Iron and Desert1_Thorium: simplex-3D +/-0.3 plus a cube falloff +/-0.7 reaching 3, range 2
    return { Type: 'Density', Skip: false, Range: { X: 2, Y: 2, Z: 2 }, Material: solid, Density: { Type: 'Sum', Skip: false, Inputs: [noise3(-0.3, 0.3, -1), cube(-0.7, 0.7, 3)] } };
  if (ore.blob === 'gold')   // Taiga1_Gold: bounds +/-3, simplex-3D 0..1 -> -0.5..0 plus a cube falloff -0.5..1 reaching 5
    return { Type: 'Density', Skip: false, Range: { X: 3, Y: 3, Z: 3 }, Material: solid, Density: { Type: 'Sum', Skip: false, Inputs: [noise3(-0.5, 0, 0), cube(-0.5, 1, 5)] } };
  // copper: Plains1_Copper. Simplex-12 +/-0.3 plus a distance falloff +/-0.7 reaching 10; ore where the sum is above 0.25 (vanilla puts that threshold in a FieldFunction material, here it is a -0.25 term).
  return { Type: 'Density', Skip: false, Range: { X: 2, Y: 2, Z: 2 }, Material: solid, Density: { Type: 'Sum', Skip: false, Inputs: [
    { Type: 'Normalizer', Skip: false, FromMin: -1, FromMax: 1, ToMin: -0.3, ToMax: 0.3, Inputs: [imported('Arrakis_Simplex_12')] },
    { Type: 'Normalizer', Skip: false, FromMin: -1, FromMax: 1, ToMin: -0.7, ToMax: 0.7, Inputs: [{ Type: 'Anchor', Skip: false, Reversed: false, Inputs: [{ Type: 'Distance', Skip: false, Curve: curve([[0, 1], [10, -2]]) }] }] },
    c(-0.25)] } };
};
const blockSet = names => ({ Type: 'BlockSet', Skip: false, BlockSet: { Inclusive: true, Materials: names.map(material) } });
// One placement rule for all five: the origin is an air block that has Rock_Sandstone above it (ceiling) or beside it (wall).
const oreTier = ore => ({ Type: 'FieldFunction', Skip: false, FieldFunction: { Type: 'BaseHeight', Skip: false, BaseHeightName: 'Bedrock', Distance: true }, Delimiters: [{ Min: ore.minY, Max: ore.maxY + 1 }] });
const orePattern = () => ({ Type: 'Or', Skip: false, Patterns: [
  { Type: 'Wall', Skip: false, Wall: blockSet(['Rock_Sandstone']), Origin: { Type: 'BlockType', Skip: false, Material: material('Empty') }, Directions: ['N', 'E', 'S', 'W'], RequireAllDirections: false },
  { Type: 'Ceiling', Skip: false, Ceiling: blockSet(['Rock_Sandstone']), Origin: { Type: 'BlockType', Skip: false, Material: material('Empty') } }] });
const oreMask = () => ({ DontPlace: { Inclusive: true, Materials: [material('Empty')] }, DontReplace: { Inclusive: false, Materials: [material('Rock_Sandstone')] } });
// The scanner looks only within `scan` blocks above and below the node the position belongs to; the tier is a height test in the pattern.
const oreScanner = ore => ({ Type: 'ColumnLinear', Skip: false, MaxY: P.ore.scan, MinY: -P.ore.scan, RelativeToPosition: true, BaseHeightName: 'Bedrock', TopDownOrder: false, ResultCap: 1 });
// Every ore is one Density prop that carries its own Pattern, Scanner and PlacementMask, as vanilla Desert1_Iron does. (The vanilla copper and gold recipes use a
// Locator and bounds instead; in this engine build the Locator and Bounds forms placed nothing when attached to a graph node, so their blobs are rebuilt as Range props.)
const oreAssignment = ore => ({ Type: 'Constant', ExportAs: 'Arrakis_Ore_' + ore.name, Skip: false,
  Prop: Object.assign(oreBlob(ore, ore.block), { Pattern: { Type: 'And', Skip: false, Patterns: [orePattern(), oreTier(ore)] }, Scanner: oreScanner(ore), PlacementMask: oreMask() }) });
// Positions are in the frame of the node (Anchor), so their Y is the node's Y and they fall inside the node's prop range.
const orePositions = ore => ore.bedrockGrid
    ? { Type: 'Jitter2d', Skip: false, Magnitude: ore.grid / 2, Seed: 'Arrakis_Ore_' + ore.name, Positions: { Type: 'Scaler', Skip: false, Scale: { X: ore.grid, Y: ore.grid, Z: ore.grid }, Positions: { Type: 'SquareGrid2d', Skip: false } } }
    : { Type: 'Mesh2D', Skip: false, PointsY: 0, PointGenerator: { Type: 'Mesh', Skip: false, Jitter: 0.5, ScaleX: ore.grid, ScaleY: ore.grid, ScaleZ: ore.grid, Seed: 'Arrakis_Ore_' + ore.name } };
// One entry per prop range. The positions are in the frame of the node, so the pattern of positions round every node is the same for the whole world (it depends only on the seed);
// a position farther from the node than the range is never used. Silver and gold have one position per 32 m cell, jittered by up to 16 m (22 m on the diagonal), so their range has to be 24
// or a seed whose jitter puts the position more than 12 m away gets no silver or gold anywhere.
const oreRanges = () => [...new Set(P.ore.types.map(o => o.range || P.ore.range))].sort((x, y) => x - y);
// A node keeps only one prop content per layer, so each range gets its own layer (Priority0, Priority1, ...); two ranges on one layer cancelled each other out (the thorium that vanished).
const oreProps = types => [...new Set(types.map(o => o.range || P.ore.range))].map(range => ({ ContentLayer: 'Priority' + oreRanges().indexOf(range), Range: range, PropDistribution: { Type: 'Union', Skip: false, PropDistributions: types.filter(o => (o.range || P.ore.range) === range).map(o => ({ Type: 'Assigned', Skip: false, OverrideAllProps: false,
  PropDistribution: { Type: 'Positions', Skip: false, Positions: { Type: 'Anchor', Skip: false, Reversed: false, Positions: orePositions(o) } }, Assignments: { Type: 'Imported', Skip: false, Name: 'Arrakis_Ore_' + o.name } })) } }));
if (P.ore) for (const o of P.ore.types) write('Assignments/Arrakis_Ore_' + o.name + '.json', oreAssignment(o));
// A node only gets the ores whose height tier (widened by the scan distance) reaches its height, so most nodes carry one or two ores, not five.
const oreBands = () => {
  const lo = o => o.minY - P.ore.scan, hi = o => o.maxY + P.ore.scan + 1;
  const cuts = [...new Set(P.ore.types.flatMap(o => [lo(o), hi(o)]))].sort((x, y) => x - y);
  const bands = [];
  for (let i = 0; i + 1 < cuts.length; i++) { const types = P.ore.types.filter(o => lo(o) <= cuts[i] && hi(o) >= cuts[i + 1]); if (types.length) bands.push({ from: cuts[i], to: cuts[i + 1], types }); }
  return bands;
};
// A node is "deep" when it is far from the shore and has rock well above it; only deep nodes carry ore, so ore stays away from the openings.
const rockAt = (dx, dy, dz) => node('CurveMapper', [node('Slider', [imported('Arrakis_Rock')], { SlideX: -dx, SlideY: -dy, SlideZ: -dz })], { Curve: curve([[-0.0001, 0], [0.0001, 1]]) });
const deepDensity = () => node('Min', [
  node('CurveMapper', [imported('Arrakis_Large_Inside')], { Curve: curve([[P.ore.deepInside - 1, 0], [P.ore.deepInside, 1]]) }),
  rockAt(0, P.ore.deepCover, 0), rockAt(P.ore.deepSide, 0, 0), rockAt(-P.ore.deepSide, 0, 0), rockAt(0, 0, P.ore.deepSide), rockAt(0, 0, -P.ore.deepSide)]);

const passes = [];
P.sheets.forEach((s, k) => {
  const tag = tagOf(s);
  // 1. the nodes of this sheet, on a jittered grid, only under large islands, at Base
  passes.push({ Type: 'Positions', Skip: false, Positions: { Type: 'BaseHeight', Skip: false, BedName: 'Base',
    Positions: { Type: 'FieldFunction', Skip: false, FieldFunction: imported('Arrakis_Large_Inside'), Delimiters: [{ Min: P.largeInsideMin, Max: 1000000 }],
      Positions: { Type: 'Jitter2d', Skip: false, Magnitude: P.jitterFactor ? Math.round(s.grid * P.jitterFactor) : s.jitter, Seed: 'Arrakis_Caves_' + s.name,
        Positions: { Type: 'Scaler', Skip: false, Scale: { X: s.grid, Y: s.grid, Z: s.grid }, Positions: { Type: 'SquareGrid2d', Skip: false } } } } } });
  // 2. tag the nodes that have no tag yet
  const earlier = P.sheets.slice(0, k).map(tagOf);
  if (earlier.length === 0) passes.push({ Type: 'NodeAction', Skip: false, NodeAction: tagContent([tag]) });
  else passes.push(selectorPass(earlier.length === 1 ? { Type: 'Not', Skip: false, NodeSelector: contentSet(earlier) }
    : { Type: 'And', Skip: false, NodeSelectors: earlier.map(t => ({ Type: 'Not', Skip: false, NodeSelector: contentSet([t]) })) }, tagContent([tag])));
  // 3. move them up or down by a slow noise: height = Base + centre + noise * amp
  const noise = imported('Arrakis_Simplex_' + s.noise);
  const scalar = node('Sum', [c(s.centre), { Type: 'Normalizer', Skip: false, FromMin: -1, FromMax: 1, ToMin: -s.amp, ToMax: s.amp, Inputs: [s.invert ? node('Inverter', [noise]) : noise] }]);
  passes.push(selectorPass(contentSet([tag]), { Type: 'Move', Skip: false, MaxDistance: Math.ceil(Math.abs(s.centre) + s.amp) + 5,
    Vector: { Type: 'ScalarMultiplier', Vector: { Type: 'Constant', Skip: false, Vector: { X: 0, Y: 1, Z: 0 } }, Scalar: scalar } }));
});
const chamberTags = P.sheets.filter(s => s.kind === 'chamber').map(tagOf);
const chamberSel = () => orSel(chamberTags.map(t => contentSet([t])));
// connections. Vanilla: short links between any nodes, longer links between chambers, then orphans. Here the links are made inside each sheet (so the tunnels run
// sideways along the sheet) and only a share of nodes also link down to the sheet below (the shafts); orphans may link to anything near.
P.sheets.forEach(s => passes.push(selectorPass(contentSet([tagOf(s)]), connector(s.link || Math.round(s.grid * P.linkFactor), 'Arrakis_Caves_Link_' + s.name, contentSet([tagOf(s)]), s.cap || P.cap))));
if (P.allShort) passes.push(selectorPass(all(), connector(P.allShort.range, 'Arrakis_Caves_Short', all(), P.allShort.cap)));
passes.push(selectorPass({ Type: 'And', Skip: false, NodeSelectors: [{ Type: 'ConnectionCount', Skip: false, Counts: [1, 0] }] },
  connector(P.orphanRange, 'Arrakis_Caves_Orphans', { Type: 'And', Skip: false, NodeSelectors: [{ Type: 'ConnectionCount', Skip: false, Counts: [1, 2] }] })));
if (chamberTags.length) passes.push(selectorPass({ Type: 'And', Skip: false, NodeSelectors: [chamberSel(), { Type: 'ConnectionCount', Skip: false, Counts: [1, 0] }] },
  connector(P.chamberOrphanRange, 'Arrakis_Caves_Orphans', { Type: 'And', Skip: false, NodeSelectors: [chamberSel(), { Type: 'ConnectionCount', Skip: false, Counts: [1, 2] }] })));
// links from each sheet down to the one below it
if (P.crossLinks) P.sheets.slice(0, -1).forEach((s, k) => {
  const below = P.sheets[k + 1];
  passes.push(selectorPass({ Type: 'And', Skip: false, NodeSelectors: [contentSet([tagOf(s)]), { Type: 'Random', Skip: false, Chance: P.crossLinks.chance, Seed: 'Arrakis_Caves_Cross_' + s.name }] },
    connector(P.crossLinks.range, 'Arrakis_Caves_Cross_' + s.name, contentSet([tagOf(below)]))));
});
// content: what each node carves
P.sheets.forEach(s => passes.push(selectorPass(contentSet([tagOf(s)]), tagContent([tagOf(s)], contentFor(s)))));
// Ore: deep nodes are given their ores in later passes, which replace the plain content of the pass above.
if (P.ore) P.sheets.forEach(s => oreBands().forEach(b => {
  const deep = { Type: 'DensityDelimited', Skip: false, Density: deepDensity(), Delimiters: [{ MinInclusive: 0.5, MaxExclusive: 10 }] };
  const band = { Type: 'DensityDelimited', Skip: false, Density: { Type: 'BaseHeight', Skip: false, BaseHeightName: 'Bedrock', Distance: true }, Delimiters: [{ MinInclusive: b.from, MaxExclusive: b.to }] };
  passes.push(selectorPass({ Type: 'And', Skip: false, NodeSelectors: [contentSet([tagOf(s)]), band, deep] }, tagContent([tagOf(s)], Object.assign(contentFor(s), { PropDistributionContent: oreProps(b.types) }))));
}));

write('GraphGenerators/Arrakis_Caves.json', { ExportName: 'Arrakis_Caves', CacheCellSize: { X: 320, Y: 320, Z: 320 }, CacheCapacity: 50, PrintStats: false, StatsPrintInterval: 50, StatsLabel: 'UNNAMED', Passes: passes });

// ---- The biome ----
const graphDensity = () => ({ Type: 'Graph', Skip: false, ContentLayer: 'Priority0', BackgroundValue: 1, TransitionSlope: 2.5, TransitionOffset: 0, TransitionSmooth: 0.2, QueryBufferCapacity: 3,
  GraphGenerator: { ImportName: 'Arrakis_Caves', ExportName: '', CacheCapacity: 50, PrintStats: false, StatsPrintInterval: 50, StatsLabel: 'UNNAMED', Passes: [] } });

// 1. The vanilla Plains1 cave term inside the copied recipes (YSampled > Mix > Graph "Plains1_Caves") becomes a constant 1 (no cave).
let removed = 0;
const isPlainsCave = x => x && x.Type === 'YSampled' && x.Inputs && x.Inputs[0] && x.Inputs[0].Type === 'Mix' && x.Inputs[0].Inputs[1] && x.Inputs[0].Inputs[1].Type === 'Graph'
  && x.Inputs[0].Inputs[1].GraphGenerator && x.Inputs[0].Inputs[1].GraphGenerator.ImportName === 'Plains1_Caves';
(function strip(n) {
  if (!n || typeof n !== 'object') return;
  for (const [k, v] of Object.entries(n)) {
    if (Array.isArray(v)) v.forEach((x, i) => { if (isPlainsCave(x)) { v[i] = c(1); removed++; } else strip(x); });
    else if (isPlainsCave(v)) { n[k] = c(1); removed++; }
    else strip(v);
  }
})(biome.Terrain);
if (JSON.stringify(biome).includes('Plains1_Caves')) throw new Error('a Plains1_Caves reference is left in the biome');

// 2. Carving. Final density = Min(old density, K). K is 1000 outside the carve zone, so nothing changes there. Inside it K is the cave graph (1 in solid rock, below 0 in a cave).
//    The zone is the rock body of a large island, Y 4 .. Base + 75. Because the zone is the rock body only, cave air replaces sand only where the rock body already stood (rock wins over the sand floor there), never outside it.
const rel = y => y - P.base; // BaseHeight "distance" is Y - Base
// The zone is a product of 0/1 tests. Reading Arrakis_Rock a second time (and again 3 blocks higher for the cover) re-evaluates the whole rock recipe, which is what costs
// generation time, so those are the last tests: every Mix skips its second input wherever its influence is 0, and the rock tests only run inside a cave of the graph
// (inCave) in a large footprint within the height range.
const inRock = node('CurveMapper', [imported('Arrakis_Rock')], { Curve: curve([[-0.0001, 0], [0.0001, 1]]) });
const covered = node('CurveMapper', [node('Slider', [imported('Arrakis_Rock')], { SlideX: 0, SlideY: -P.cover, SlideZ: 0 })], { Curve: curve([[-0.0001, 0], [0.0001, 1]]) });
const caveGraphDef = () => ({ Type: 'Exported', ExportAs: 'Arrakis_Cave_Graph', SingleInstance: true, Skip: false, Inputs: [node('YSampled', [graphDensity()], { SampleDistance: P.ySample, SampleOffset: 0, Interpolate: true })] });
const inCave = node('CurveMapper', [imported('Arrakis_Cave_Graph')], { Curve: curve([[-0.0001, 1], [0.0001, 0]]) });
const cheap = node('Min', [
  node('CurveMapper', [imported('Arrakis_Large_Inside')], { Curve: curve([[P.largeInsideMin - 1, 0], [P.largeInsideMin, 1]]) }),
  node('CurveMapper', [{ Type: 'BaseHeight', BaseHeightName: 'Base', Distance: true, Skip: false }], { Curve: curve([[rel(P.floorY) - 1, 0], [rel(P.floorY), 1], [P.ceilingAboveBase, 1], [P.ceilingAboveBase + 1, 0]]) }),
  inCave]);
const zoneFinal = node('Mix', [c(0), P.cover > 0 ? node('Min', [inRock, covered]) : inRock, cheap]);
const carve = node('Mix', [c(1000), caveGraphDef(), zoneFinal]); // the graph is built first (Mix inputs are built in order); the zone imports it
carve.$Comment = 'Caves: K = 1000 outside the zone (large-island rock body, Y 4 to Base + 75, rock ' + P.cover + ' blocks above), the cave graph inside it.';
const oldRoot = biome.Terrain.Density;
biome.Terrain.Density = node('Min', [oldRoot, carve]);
biome.Terrain.Density.$Comment = 'Terrain = Min(sand floor, dunes and rock; caves).';

// 3. All rock is Rock_Sandstone (the sand-level split into sandstone and red sandstone is gone).
let rockEntry = null;
(function find(n) { if (rockEntry || !n || typeof n !== 'object') return; if (n.Type === 'FieldFunction' && n.FieldFunction && n.FieldFunction.Name === 'Arrakis_Rock') { rockEntry = n; return; } for (const v of Object.values(n)) find(v); })(biome.MaterialProvider);
if (!rockEntry) throw new Error('rock material entry not found');
rockEntry.Delimiters[0].Material = { Type: 'Constant', Skip: false, Material: { Solid: 'Rock_Sandstone' } };

// 4. Ore: the nodes carry prop distributions; a biome Props entry of type Graph is what asks the graph for them.
biome.Props = P.ore ? oreRanges().map((r, i) => ({ Skip: false, Runtime: i, ContextPrecision: 'Biome', PropDistribution: { Type: 'Union', Skip: false, PropDistributions: [
  { Type: 'Graph', Skip: false, ContentLayer: 'Priority' + i, GraphGenerator: { ImportName: 'Arrakis_Caves', CacheCapacity: 50, PrintStats: false, StatsPrintInterval: 50, StatsLabel: 'UNNAMED', Passes: [] } }] } })) : [];

write('Biomes/Dunes_of_Arrakis/Arrakis_Terrain.json', biome);
console.log(`wrote pack ${pack}: ${removed} Plains1 cave terms removed, ${passes.length} cave passes, sheets ${P.sheets.map(s => s.name).join('/')}`);
