// Lobe and bay showcase: nine tiles of two ovals each, all in rock variant Arrakis_V1, each tile with different outline noise.
//   node tools/preview/showcase_lobes.js [packRoot]
// Writes into the pack: Biomes/Arrakis_LobeTest/Arrakis_LobeTest.json, Density/LT_Field.json, WorldStructures/Arrakis_LobeTest.json.
// The biome is a copy of the pre-round Arrakis biome with every export renamed LT_*, so it can sit next to the real one. Its rock is one
// land-mass body (variant lowered 8 blocks, coast rule, root rule) over the test field. Use it with tools/showcase/Use-LobeTest.ps1.
//
// Tiles (1 at the origin, 2..9 clockwise from north on a 4 km ring). Metres everywhere.
//   lobes: noise scale and amplitude of the broad outline distortion; bays: the same, smaller; coast: fine edge detail.
//   The distortion is only applied near the shore (it fades to nothing deeper than the sum of the amplitudes plus 100 m), so the inside stays solid.
//   Tile 9 is the method used up to now: amplitudes relative to the oval's size, no fade.
const fs = require('fs');
const path = require('path');

const TILES = [
  { n: 1, name: 'raw oval, no noise, flat top (no terrain noise inside)', raw: true, flat: true },
  { n: 2, name: 'coast detail only (+/-40 m at 60 m)', lobes: [1, 0], bays: [1, 0], coast: true },
  { n: 3, name: 'gentle lobes: scale 360 +/-90, bays 105 +/-25', lobes: [360, 90], bays: [105, 25], coast: true },
  { n: 4, name: 'gentle, finer lobes: scale 210 +/-90, bays 75 +/-25', lobes: [210, 90], bays: [75, 25], coast: true },
  { n: 5, name: 'medium lobes: scale 360 +/-180, bays 120 +/-45', lobes: [360, 180], bays: [120, 45], coast: true },
  { n: 6, name: 'medium, finer lobes: scale 210 +/-180, bays 75 +/-45', lobes: [210, 180], bays: [75, 45], coast: true },
  { n: 7, name: 'strong lobes: scale 300 +/-300, bays 90 +/-75', lobes: [300, 300], bays: [90, 75], coast: true },
  { n: 8, name: 'ragged edge: lobes 360 +/-90, bays 45 +/-90', lobes: [360, 90], bays: [45, 90], coast: true },
  { n: 9, name: 'the previous method: relative to size (0.45 / 0.14 of the half-axis, class scales), no fade', previous: true }
];
// the oval of every tile: semi-major, semi-minor, spin (degrees), centre offset from the tile centre
const OVALS = [
  { major: 800, minor: 400, spin: 25, dx: 0, dz: 0 }
];
const RING = 4000, FIX = { none: -9999, coastMetres: 40, coastScale: 60, coastRise: 0.013, footprintMargin: 3, fadeExtra: 100, pick: -0.9 };
// previous method: noise scales by size class (the values used in the stage 1 build) and strengths relative to the half-axis
const PREV = { A: { lobe: 480, bay: 150 }, B: { lobe: 900, bay: 270 }, C: { lobe: 1300, bay: 380 }, D: { lobe: 2100, bay: 600 }, lobeAmp: 0.45, bayAmp: 0.14 };

const here = __dirname, root = path.join(here, '..', '..');
const pack = path.resolve(process.argv[2] || path.join(root, 'src', 'main', 'resources'));
const baselineFile = path.join(here, 'out', 'Arrakis_Terrain.pre-caves.json');
const write = (rel, obj) => { const f = path.join(pack, 'Server', 'HytaleGenerator', rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n'); };

const c = v => ({ Type: 'Constant', Skip: false, Value: v });
const imported = name => ({ Type: 'Imported', Skip: false, Name: name });
const curve = pts => ({ Type: 'Manual', Points: pts.map(([i, o]) => ({ In: +i.toFixed(4), Out: +o.toFixed(5) })) });
const node = (type, inputs, extra, comment) => Object.assign(comment ? { $Comment: comment } : {}, { Type: type }, extra || {}, { Skip: false, Inputs: inputs });
const noise2d = (scale, octaves, seed) => ({ Type: 'SimplexNoise2D', Scale: scale, Octaves: octaves, Lacunarity: 2.0, Persistence: 0.5, Seed: seed, Skip: false });
const norm = (min, max, input) => node('Normalizer', [input], { FromMin: -1, FromMax: 1, ToMin: min, ToMax: max });
const cache2d = (n, capacity = 3) => node('YOverride', [node('Cache', [n], { Capacity: capacity })], { Value: 0 });
const mul = (k, x) => node('Multiplier', [c(k), x]);
const sum = (...xs) => node('Sum', xs);
const clamp01 = x => ({ Type: 'Clamp', WallA: 0, WallB: 1, Skip: false, Inputs: [x] });
const copy = o => JSON.parse(JSON.stringify(o));

// ---------- the test field ----------
const tileCentre = t => t.n === 1 ? [0, 0] : [Math.round(RING * Math.sin((t.n - 2) * Math.PI / 4)), Math.round(-RING * Math.cos((t.n - 2) * Math.PI / 4))];
const box = (cx, cz, h) => node('Min', [
  node('CurveMapper', [{ Type: 'XValue', Skip: false }], { Curve: curve([[cx - h - 1, 0], [cx - h, 1], [cx + h, 1], [cx + h + 1, 0]]) }),
  node('CurveMapper', [{ Type: 'ZValue', Skip: false }], { Curve: curve([[cz - h - 1, 0], [cz - h, 1], [cz + h, 1], [cz + h + 1, 0]]) })]);
const slider = (o, x, z) => node('Slider', [{ Type: 'Ellipsoid', Skip: false, Spin: o.spin, Curve: curve([[0, 1], [1, 0], [2, -1]]), Scale: { X: o.major, Y: 1, Z: o.minor }, NewYAxis: { X: 0, Y: 1, Z: 0 } }],
  { SlideX: x, SlideY: 0, SlideZ: z });
const sizeClass = o => { const d = o.major + o.minor; return d < 4000 ? 'A' : d < 6500 ? 'B' : d < 9500 ? 'C' : 'D'; };
const ovalField = (t, o) => {
  const [tx, tz] = tileCentre(t), x = tx + o.dx, z = tz + o.dz, mean = Math.round(Math.sqrt(o.major * o.minor));
  const h = o.major + (t.raw ? 0 : (t.previous ? mean * 0.7 : t.lobes[1] + t.bays[1])) + 300;
  let field;
  if (t.raw) field = mul(mean, slider(o, x, z));
  else if (t.previous) {
    const k = sizeClass(o), s = PREV[k];
    field = sum(mul(mean, sum(slider(o, x, z), norm(-PREV.lobeAmp, PREV.lobeAmp, noise2d(s.lobe, 2, 'LT_Prev_Lobes_' + k)), norm(-PREV.bayAmp, PREV.bayAmp, noise2d(s.bay, 2, 'LT_Prev_Bays_' + k)))),
      norm(-FIX.coastMetres, FIX.coastMetres, noise2d(FIX.coastScale, 2, 'LT_Coast')));
  } else {
    const e0 = () => mul(mean, slider(o, x, z));
    const depth = t.lobes[1] + t.bays[1] + FIX.fadeExtra;
    const parts = [];
    if (t.lobes[1] > 0) parts.push(norm(-t.lobes[1], t.lobes[1], noise2d(t.lobes[0], 2, `LT_Lobes_${t.n}`)));
    if (t.bays[1] > 0) parts.push(norm(-t.bays[1], t.bays[1], noise2d(t.bays[0], 2, `LT_Bays_${t.n}`)));
    const fade = clamp01(sum(c(1), mul(-1 / depth, e0())));
    field = sum(e0(), parts.length ? node('Multiplier', [parts.length > 1 ? sum(...parts) : parts[0], fade]) : c(0),
      node('Multiplier', [norm(-FIX.coastMetres, FIX.coastMetres, noise2d(FIX.coastScale, 2, 'LT_Coast')), fade]));
  }
  return node('Mix', [c(FIX.none), field, box(x, z, h)], null, `Tile ${t.n}, oval ${o.major} x ${o.minor} spin ${o.spin}: ${t.name}`);
};
const fieldFor = pick => TILES.filter(pick).flatMap(t => OVALS.map(o => ovalField(t, o)));
const all = fieldFor(() => true);
write('Density/LT_Field.json', { $Comment: 'Lobe and bay showcase: metres inside the shore of the nearest test oval; ' + FIX.none + ' where there is none.', Type: 'Exported', ExportAs: 'LT_Field', SingleInstance: true, Skip: false,
  Inputs: [cache2d(node('Max', all))] });
// the same field split in two: the flat-topped tiles and the tiles that carry the rock variant
const splitField = (name, pick) => write(`Density/${name}.json`, { $Comment: 'Part of LT_Field.', Type: 'Exported', ExportAs: name, SingleInstance: true, Skip: false, Inputs: [cache2d(node('Max', fieldFor(pick)))] });
splitField('LT_Field_Flat', t => t.flat);
splitField('LT_Field_V1', t => !t.flat);

// ---------- the biome: the pre-round biome, every export renamed LT_*, with its rock replaced ----------
const biome = JSON.parse(fs.readFileSync(baselineFile, 'utf8'));
const defined = new Set();
(function collect(n) { if (!n || typeof n !== 'object') return; if (typeof n.ExportAs === 'string') defined.add(n.ExportAs); for (const v of Object.values(n)) collect(v); })(biome);
const ren = s => 'LT_' + s.replace(/^Arrakis_/, '');
(function rename(n) {
  if (!n || typeof n !== 'object') return;
  if (typeof n.ExportAs === 'string' && defined.has(n.ExportAs)) n.ExportAs = ren(n.ExportAs);
  if (n.Type === 'Imported' && defined.has(n.Name)) n.Name = ren(n.Name);
  for (const v of Object.values(n)) rename(v);
})(biome);
biome.Name = 'Arrakis_LobeTest';
const find = (name) => { let r = null; (function f(n) { if (r || !n || typeof n !== 'object') return; if (n.ExportAs === name) { r = n; return; } for (const v of Object.values(n)) f(v); })(biome); return r; };
const oldRock = find('LT_Rock');
if (!oldRock) throw new Error('no rock export');
const bodies = oldRock.Inputs[0].Inputs[0].Inputs[0];
if (bodies.Type !== 'Max' || !/^Land mass/.test(bodies.Inputs[3].$Comment || '')) throw new Error('the Land mass body is not where it was expected');
// the new rock bodies: copies of the Land mass body over one part of the test field each.
// The V1 body carries Arrakis_V1 lowered 8 blocks. The flat body has no rock variant at all: the top is a plain plateau FLAT_H blocks above the sand level.
const FLAT_H = 24;
const makeBody = (fieldName, flat) => {
  const body = copy(bodies.Inputs[3]);
  body.$Comment = flat ? 'Lobe test, flat tiles: plateau, no terrain noise, inside the footprint of the flat test ovals only.' : 'Lobe test: Arrakis_V1 lowered 8 blocks, inside the footprint of the test ovals only.';
  const variants = body.Inputs[1].Inputs[0];
  if (variants.Type !== 'Max') throw new Error('unexpected body layout');
  variants.Inputs = [variants.Inputs[0]]; // V1 only
  (function swapPick(n) { if (!n || typeof n !== 'object') return; for (const [k, v] of Object.entries(n)) { if (v && v.Type === 'Imported' && v.Name === 'LT_Pick') n[k] = c(FIX.pick); else if (Array.isArray(v)) v.forEach((x, i) => { if (x && x.Type === 'Imported' && x.Name === 'LT_Pick') v[i] = c(FIX.pick); else swapPick(x); }); else swapPick(v); } })(body);
  const min = body.Inputs[1];
  if (flat) {
    // plateau: positive below FLAT_H above the sand level (BaseHeight distance is Y - Base); the root rule is not needed
    min.Inputs[0] = node('CurveMapper', [{ Type: 'BaseHeight', BaseHeightName: 'Base', Distance: true, Skip: false }], { Curve: curve([[FLAT_H - 2, 1], [FLAT_H, 0], [FLAT_H + 2, -1]]) }, 'Flat top ' + FLAT_H + ' blocks above the sand level.');
    min.Inputs[2] = c(5);
  }
  const coast = min.Inputs[1];
  coast.Inputs[0].Inputs[0].Value = FIX.coastRise;
  coast.Inputs[0].Inputs[1] = cache2d(imported(fieldName));
  const foot = body.Inputs[2];
  foot.Inputs[0].Inputs[1].Inputs[0] = cache2d(imported(fieldName));
  foot.Inputs[0].Inputs[1].Inputs[1] = c(FIX.footprintMargin);
  return body;
};
const body = node('Max', [makeBody('LT_Field_V1', false), makeBody('LT_Field_Flat', true)]);
oldRock.ExportAs = 'LT_RockOld';
const newRock = { $Comment: 'Rock: > 0 is rock. Only the lobe test land mass body.', Type: 'Exported', ExportAs: 'LT_Rock', SingleInstance: true, Skip: false, Inputs: [body] };
// the old rock stays in the tree (it defines the rock variants) but is never evaluated: Mix with influence 0
const terrainMax = biome.Terrain.Density;
const at = terrainMax.Inputs.indexOf(oldRock);
if (at < 0) throw new Error('old rock export is not a direct input of the terrain density');
terrainMax.Inputs[at] = node('Mix', [c(-5), oldRock, c(0)]);
terrainMax.Inputs.push(newRock);
// Arrakis_Inside (renamed LT_Inside): how far inside the rock a column is; here the test field
const inside = find('LT_Inside');
if (!inside) throw new Error('no inside export');
inside.Inputs = [cache2d(imported('LT_Field'))];
write('Biomes/Arrakis_LobeTest/Arrakis_LobeTest.json', biome);

// ---------- the world structure ----------
const ws = JSON.parse(fs.readFileSync(path.join(root, 'src', 'main', 'resources', 'Server', 'HytaleGenerator', 'WorldStructures', 'Dunes_of_Arrakis.json'), 'utf8'));
ws.DefaultBiome = 'Arrakis_LobeTest';
write('WorldStructures/Arrakis_LobeTest.json', ws);
console.log(`lobe test: ${TILES.length} tiles x ${OVALS.length} ovals; ${defined.size} exports renamed; wrote biome, LT_Field and world structure into ${pack}`);
console.log(TILES.map(t => `  tile ${t.n} at (${tileCentre(t)}): ${t.name}`).join('\n'));
