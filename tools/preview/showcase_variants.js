// Variant showcase: ten raw ovals, one per rock variant (Arrakis_V1 .. Arrakis_V10), packed about 75 m apart. No outline noise, so the shape is the plain oval and
// everything inside it is the rock variant's own terrain.
//   node tools/preview/showcase_variants.js [packRoot]
// Writes into the pack: Biomes/Arrakis_VariantTest/Arrakis_VariantTest.json, Density/VT_Field_<n>.json, WorldStructures/Arrakis_VariantTest.json.
// The biome is a copy of the pre-round Arrakis biome with every export renamed VT_*, with its rock replaced by ten land-mass bodies (one per variant,
// the variant lowered 8 blocks as in the real land masses, coast rule, root rule). Use it with tools/showcase/Use-LobeTest.ps1 -WorldStructure Arrakis_VariantTest.
//
// Layout: the ovals (800 x 400 semi-axes, spun 25 degrees) sit on a grid aligned with their own axes, GAP metres between neighbours: 2 columns along the major
// axis, 5 rows along the minor axis. Variant 1 is at the origin (spawn).
const fs = require('fs');
const path = require('path');

const OVAL = { major: 800, minor: 400, spin: 25 };
const GAP = 75;
const SLOTS = [[0, 0], [1, 0], [0, 1], [1, 1], [0, -1], [1, -1], [0, 2], [1, 2], [0, -2], [1, -2]]; // [column along the major axis, row along the minor axis] for V1 .. V10
const FIX = { none: -9999, coastRise: 0.013, footprintMargin: 3 };

const here = __dirname, root = path.join(here, '..', '..');
const pack = path.resolve(process.argv[2] || path.join(root, 'src', 'main', 'resources'));
const baselineFile = path.join(here, 'out', 'Arrakis_Terrain.pre-caves.json');
const write = (rel, obj) => { const f = path.join(pack, 'Server', 'HytaleGenerator', rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n'); };

const c = v => ({ Type: 'Constant', Skip: false, Value: v });
const imported = name => ({ Type: 'Imported', Skip: false, Name: name });
const curve = pts => ({ Type: 'Manual', Points: pts.map(([i, o]) => ({ In: +i.toFixed(4), Out: +o.toFixed(5) })) });
const node = (type, inputs, extra, comment) => Object.assign(comment ? { $Comment: comment } : {}, { Type: type }, extra || {}, { Skip: false, Inputs: inputs });
const cache2d = (n, capacity = 3) => node('YOverride', [node('Cache', [n], { Capacity: capacity })], { Value: 0 });
const mul = (k, x) => node('Multiplier', [c(k), x]);
const copy = o => JSON.parse(JSON.stringify(o));

// ---------- the ovals ----------
const a = OVAL.spin * Math.PI / 180, major = [Math.cos(a), Math.sin(a)], minor = [-Math.sin(a), Math.cos(a)];
const stepMajor = 2 * OVAL.major + GAP, stepMinor = 2 * OVAL.minor + GAP;
const centre = ([i, j]) => [Math.round(i * stepMajor * major[0] + j * stepMinor * minor[0]), Math.round(i * stepMajor * major[1] + j * stepMinor * minor[1])];
const mean = Math.round(Math.sqrt(OVAL.major * OVAL.minor));
const h = OVAL.major + 200;
const box = (cx, cz) => node('Min', [
  node('CurveMapper', [{ Type: 'XValue', Skip: false }], { Curve: curve([[cx - h - 1, 0], [cx - h, 1], [cx + h, 1], [cx + h + 1, 0]]) }),
  node('CurveMapper', [{ Type: 'ZValue', Skip: false }], { Curve: curve([[cz - h - 1, 0], [cz - h, 1], [cz + h, 1], [cz + h + 1, 0]]) })]);
const ovalField = (n, [x, z]) => node('Mix', [c(FIX.none), mul(mean, node('Slider', [{ Type: 'Ellipsoid', Skip: false, Spin: OVAL.spin, Curve: curve([[0, 1], [1, 0], [2, -1]]), Scale: { X: OVAL.major, Y: 1, Z: OVAL.minor }, NewYAxis: { X: 0, Y: 1, Z: 0 } }],
  { SlideX: x, SlideY: 0, SlideZ: z })), box(x, z)], null, `Oval for Arrakis_V${n}: centre (${x}, ${z}).`);
const centres = SLOTS.map(centre);
centres.forEach((p, k) => write(`Density/VT_Field_${k + 1}.json`, { $Comment: `Variant showcase: metres inside the shore of the Arrakis_V${k + 1} oval; ${FIX.none} where there is none.`, Type: 'Exported', ExportAs: `VT_Field_${k + 1}`, SingleInstance: true, Skip: false,
  Inputs: [cache2d(ovalField(k + 1, p))] }));
write('Density/VT_Field.json', { $Comment: 'All ten variant ovals.', Type: 'Exported', ExportAs: 'VT_Field', SingleInstance: true, Skip: false, Inputs: [cache2d(node('Max', centres.map((p, k) => imported(`VT_Field_${k + 1}`))))] });

// ---------- the biome ----------
const biome = JSON.parse(fs.readFileSync(baselineFile, 'utf8'));
const defined = new Set();
(function collect(n) { if (!n || typeof n !== 'object') return; if (typeof n.ExportAs === 'string') defined.add(n.ExportAs); for (const v of Object.values(n)) collect(v); })(biome);
const ren = s => 'VT_' + s.replace(/^Arrakis_/, '');
(function rename(n) {
  if (!n || typeof n !== 'object') return;
  if (typeof n.ExportAs === 'string' && defined.has(n.ExportAs)) n.ExportAs = ren(n.ExportAs);
  if (n.Type === 'Imported' && defined.has(n.Name)) n.Name = ren(n.Name);
  for (const v of Object.values(n)) rename(v);
})(biome);
biome.Name = 'Arrakis_VariantTest';
// The recipes carry the vanilla Plains1 cave graph (Graph node, ImportName Plains1_Caves). With the root rule it removes whole columns of rock down to bedrock
// (118 cutouts in a 768 x 768 scan of one oval against 4 without it; see the log), so the showcase switches it off. The real build already replaces it with its own caves.
let graphs = 0;
(function dropGraphs(n) { if (!n || typeof n !== 'object') return; for (const [k, v] of Object.entries(n)) { if (v && v.Type === 'Graph') { n[k] = c(1); graphs++; } else if (Array.isArray(v)) v.forEach((x, i) => { if (x && x.Type === 'Graph') { v[i] = c(1); graphs++; } else dropGraphs(x); }); else dropGraphs(v); } })(biome);
console.log('vanilla cave graphs switched off:', graphs);
const find = name => { let r = null; (function f(n) { if (r || !n || typeof n !== 'object') return; if (n.ExportAs === name) { r = n; return; } for (const v of Object.values(n)) f(v); })(biome); return r; };
const oldRock = find('VT_Rock');
if (!oldRock) throw new Error('no rock export');
const bodies = oldRock.Inputs[0].Inputs[0].Inputs[0];
if (bodies.Type !== 'Max' || !/^Land mass/.test(bodies.Inputs[3].$Comment || '')) throw new Error('the Land mass body is not where it was expected');
const land = bodies.Inputs[3];
// variant k's pick value: the middle of its range (V1 -1..-0.8, V2 -0.8..-0.6, ... V10 0.8..1)
const pickOf = k => -1 + 0.2 * (k - 1) + 0.1;
const makeBody = k => {
  const body = copy(land);
  body.$Comment = `Variant test: Arrakis_V${k} lowered 8 blocks, inside its oval only.`;
  const prune = max => { if (max.Type !== 'Max') throw new Error('unexpected body layout'); max.Inputs = [max.Inputs[k - 1]]; };
  const min = body.Inputs[1];
  prune(min.Inputs[0]);
  // the root rule reads the same variant chain at Y 76
  let root = null; (function f(n) { if (root || !n || typeof n !== 'object') return; if (n.Type === 'Max' && n.Inputs.length === 10 && n.Inputs[0].Type === 'Mix') { root = n; return; } for (const v of Object.values(n)) f(v); })(min.Inputs[2]);
  if (root) prune(root);
  (function swapPick(n) { if (!n || typeof n !== 'object') return; for (const [key, v] of Object.entries(n)) { if (v && v.Type === 'Imported' && v.Name === 'VT_Pick') n[key] = c(pickOf(k)); else if (Array.isArray(v)) v.forEach((x, i) => { if (x && x.Type === 'Imported' && x.Name === 'VT_Pick') v[i] = c(pickOf(k)); else swapPick(x); }); else swapPick(v); } })(body);
  const coast = min.Inputs[1];
  coast.Inputs[0].Inputs[0].Value = FIX.coastRise;
  coast.Inputs[0].Inputs[1] = cache2d(imported(`VT_Field_${k}`));
  const foot = body.Inputs[2];
  foot.Inputs[0].Inputs[1].Inputs[0] = cache2d(imported(`VT_Field_${k}`));
  foot.Inputs[0].Inputs[1].Inputs[1] = c(FIX.footprintMargin);
  return body;
};
oldRock.ExportAs = 'VT_RockOld';
const newRock = { $Comment: 'Rock: > 0 is rock. Ten variant ovals.', Type: 'Exported', ExportAs: 'VT_Rock', SingleInstance: true, Skip: false, Inputs: [node('Max', SLOTS.map((s, i) => makeBody(i + 1)))] };
const terrainMax = biome.Terrain.Density;
const at = terrainMax.Inputs.indexOf(oldRock);
if (at < 0) throw new Error('old rock export is not a direct input of the terrain density');
terrainMax.Inputs[at] = node('Mix', [c(-5), oldRock, c(0)]); // keeps the variant definitions in the tree, never evaluated
terrainMax.Inputs.push(newRock);
const inside = find('VT_Inside');
if (!inside) throw new Error('no inside export');
inside.Inputs = [cache2d(imported('VT_Field'))];
write('Biomes/Arrakis_VariantTest/Arrakis_VariantTest.json', biome);

const ws = JSON.parse(fs.readFileSync(path.join(root, 'src', 'main', 'resources', 'Server', 'HytaleGenerator', 'WorldStructures', 'Dunes_of_Arrakis.json'), 'utf8'));
ws.DefaultBiome = 'Arrakis_VariantTest';
write('WorldStructures/Arrakis_VariantTest.json', ws);
console.log(`variant test: ${SLOTS.length} ovals ${OVAL.major} x ${OVAL.minor} spun ${OVAL.spin}, ${GAP} m apart; wrote biome, VT_Field_* and world structure into ${pack}`);
console.log(centres.map((p, k) => `  Arrakis_V${k + 1}: centre (${p[0]}, ${p[1]})`).join('\n'));
fs.writeFileSync(path.join(here, 'out', 'continent', 'varianttest_ovals.json'), centres.map((p, k) => JSON.stringify({ id: k + 1, x: p[0], z: p[1], halfEW: 60, halfNS: 60 })).join('\n'));
