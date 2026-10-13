// Continent round, version 2: five continents from the hand-drawn shapes (tools/continent-shapes-v2.json, 104 rotated ellipses, one fill colour per continent),
// one rock variant per continent, edge noise that blends between a gentle and a strong setting, a seam rule for the continents that nearly touch,
// and the random-island frequency rules (stage 3 of the first version).
//   node tools/preview/continent.js [packRoot]
// Reads the biome as it was before this round (tools/preview/out/Arrakis_Terrain.pre-caves.json) and writes
//   - tools/preview/out/Arrakis_Terrain.pre-caves.continent.json: the same biome with five continent rock bodies added next to the land-mass body;
//     tools/preview/caves.js reads that file when it exists (build order: continent.js, then caves.js);
//   - Density/Arrakis_Continent*.json, Density/Continent/*.json in the pack.
// Nothing here is a new terrain recipe: each continent body is a copy of the existing "Land mass" body (rock variant lowered 8 blocks, coast rule, root rule)
// with one rock variant kept, whose footprint and coast fields are the continent's own.
//
// Edge noise (decided from the lobe test): tile 3 (gentle) and tile 7 (strong) of the 800 x 400 test oval, both defined relative to the ellipse they act on
// and blended along the coast by a slow noise, so a coastline runs from smooth to ragged. Applied in metres and faded out with depth, so the inside stays solid.
const fs = require('fs');
const path = require('path');

const VARIANT = { 1: 10, 2: 1, 3: 5, 4: 8, 5: 4 };  // continent number (= fill colour order in the drawing: purple, green, blue, brown, red) -> Arrakis_V<n>
const NAME = { 1: 'purple', 2: 'green', 3: 'blue', 4: 'brown', 5: 'red' };
const P = {
  // size classes by the geometric mean of the half-axes (m): representative mean for each class
  classLimits: [350, 700, 1400], classMean: [250, 500, 1000, 2000],
  // tile 3 (gentle) and tile 7 (strong), relative to the minor half-axis for the amplitudes and to the mean half-axis for the scales; amplitudes capped (m)
  gentle: { lobeAmp: 0.225, lobeCap: 250, bayAmp: 0.0625, bayCap: 70, lobeScale: 0.64, bayScale: 0.19 },
  strong: { lobeAmp: 0.75, lobeCap: 600, bayAmp: 0.1875, bayCap: 180, lobeScale: 0.53, bayScale: 0.16 },
  blendScale: 6000,    // the slow noise that blends gentle and strong along a coast
  coastMetres: 30, coastScale: 60,
  fadeExtra: 100,      // the edge noise fades out this far below the sum of its amplitudes
  minScale: 40,
  coastRise: 0.013,    // rock may rise 1.3 m for every metre in from the shore, as in the existing coast term
  footprintMargin: 3,
  none: -9999,
  // seams between continents that nearly touch (closest nominal approach under seamPairMax metres)
  seamPairMax: 1500, seamReach: 900, seamMin: -80, seamMax: 40, seamScale: 600,
  seamSlopeMin: 0.325, seamSlopeMax: 0.87, seamRise: 130
};

const here = __dirname;
const root = path.join(here, '..', '..');
const pack = path.resolve(process.argv[2] || path.join(root, 'src', 'main', 'resources'));
const shapes = JSON.parse(fs.readFileSync(path.join(root, 'tools', 'continent-shapes-v2.json'), 'utf8'));
const baselineFile = path.join(here, 'out', 'Arrakis_Terrain.pre-caves.json');
const outFile = path.join(here, 'out', 'Arrakis_Terrain.pre-caves.continent.json');
const gen = path.join(pack, 'Server', 'HytaleGenerator');
const writeAsset = (rel, obj) => { const f = path.join(gen, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n'); };

// remove what earlier versions of this script wrote
for (const f of fs.existsSync(path.join(gen, 'Density')) ? fs.readdirSync(path.join(gen, 'Density')) : []) if (/^Arrakis_(Continent|Island_Frequency|Outcrop_Frequency)/.test(f)) fs.rmSync(path.join(gen, 'Density', f));
for (const d of ['Continent_Regions', 'Continent']) fs.rmSync(path.join(gen, 'Density', d), { recursive: true, force: true });

const c = v => ({ Type: 'Constant', Skip: false, Value: v });
const imported = name => ({ Type: 'Imported', Skip: false, Name: name });
const curve = pts => ({ Type: 'Manual', Points: pts.map(([i, o]) => ({ In: +i.toFixed(4), Out: +o.toFixed(5) })) });
const node = (type, inputs, extra, comment) => Object.assign(comment ? { $Comment: comment } : {}, { Type: type }, extra || {}, { Skip: false, Inputs: inputs });
const noise2d = (scale, octaves, seed) => ({ Type: 'SimplexNoise2D', Scale: Math.max(P.minScale, Math.round(scale)), Octaves: octaves, Lacunarity: 2.0, Persistence: 0.5, Seed: seed, Skip: false });
const norm = (min, max, input) => node('Normalizer', [input], { FromMin: -1, FromMax: 1, ToMin: min, ToMax: max });
const cache2d = (n, capacity = 3) => node('YOverride', [node('Cache', [n], { Capacity: capacity })], { Value: 0 });
const cacheBig = n => cache2d(n, 512); // for fields that are asked at many different candidate positions (the island grids)
const copy = o => JSON.parse(JSON.stringify(o));
const exported = (name, inputs, comment) => Object.assign(comment ? { $Comment: comment } : {}, { Type: 'Exported', ExportAs: name, SingleInstance: true, Skip: false, Inputs: inputs });
const mul = (k, x) => node('Multiplier', [c(k), x]);
const sum = (...xs) => node('Sum', xs);
const clamp01 = x => ({ Type: 'Clamp', WallA: 0, WallB: 1, Skip: false, Inputs: [x] });

const inE = (e, x, z) => { const a = e.angle * Math.PI / 180, dx = x - e.x, dz = z - e.z, u = (dx * Math.cos(a) + dz * Math.sin(a)) / e.major, v = (-dx * Math.sin(a) + dz * Math.cos(a)) / e.minor; return u * u + v * v < 1; };
const meanOf = e => Math.sqrt(e.major * e.minor);
const classOf = e => { const m = meanOf(e); return m < P.classLimits[0] ? 0 : m < P.classLimits[1] ? 1 : m < P.classLimits[2] ? 2 : 3; };
const box = (cx, cz, h) => node('Min', [
  node('CurveMapper', [{ Type: 'XValue', Skip: false }], { Curve: curve([[cx - h - 1, 0], [cx - h, 1], [cx + h, 1], [cx + h + 1, 0]]) }),
  node('CurveMapper', [{ Type: 'ZValue', Skip: false }], { Curve: curve([[cz - h - 1, 0], [cz - h, 1], [cz + h, 1], [cz + h + 1, 0]]) })]);
const slider = e => node('Slider', [{ Type: 'Ellipsoid', Skip: false, Spin: e.angle, Curve: curve([[0, 1], [1, 0], [10, -9]]), Scale: { X: e.major, Y: 1, Z: e.minor }, NewYAxis: { X: 0, Y: 1, Z: 0 } }],
  { SlideX: e.x, SlideY: 0, SlideZ: e.z });
const conts = [...new Set(shapes.map(e => e.continent))].sort((a, b) => a - b);
const of = k => shapes.filter(e => e.continent === k);

// ---------- shared unit noise (+/-1), one set per size class, and the blend mask ----------
for (let k = 0; k < 4; k++) {
  const m = P.classMean[k];
  for (const [tag, s] of [['G', P.gentle], ['S', P.strong]]) {
    writeAsset(`Density/Continent/Arrakis_Cont_L${tag}_${k}.json`, exported(`Arrakis_Cont_L${tag}_${k}`, [cache2d(norm(-1, 1, noise2d(s.lobeScale * m, 2, `Arrakis_Cont_Lobes${tag}_${k}`)))],
      `Unit lobe noise (${tag === 'G' ? 'gentle' : 'strong'}), size class ${k}: scale ${Math.round(s.lobeScale * m)} m.`));
    writeAsset(`Density/Continent/Arrakis_Cont_B${tag}_${k}.json`, exported(`Arrakis_Cont_B${tag}_${k}`, [cache2d(norm(-1, 1, noise2d(s.bayScale * m, 2, `Arrakis_Cont_Bays${tag}_${k}`)))],
      `Unit bay noise (${tag === 'G' ? 'gentle' : 'strong'}), size class ${k}: scale ${Math.max(P.minScale, Math.round(s.bayScale * m))} m.`));
  }
}
writeAsset('Density/Continent/Arrakis_Cont_Blend.json', exported('Arrakis_Cont_Blend', [cache2d(norm(0, 1, noise2d(P.blendScale, 1, 'Arrakis_Cont_Blend')))],
  `0..1, scale ${P.blendScale} m: 0 = gentle edge noise, 1 = strong.`));
writeAsset('Density/Continent/Arrakis_Cont_CoastUnit.json', exported('Arrakis_Cont_CoastUnit', [cache2d(norm(-1, 1, noise2d(P.coastScale, 2, 'Arrakis_Cont_Coast')))], `Unit coast detail noise, scale ${P.coastScale} m.`));

// ---------- one field per ellipse, one per continent ----------
const amplitudes = e => ({
  aG: Math.min(P.gentle.lobeAmp * e.minor, P.gentle.lobeCap), bG: Math.min(P.gentle.bayAmp * e.minor, P.gentle.bayCap),
  aS: Math.min(P.strong.lobeAmp * e.minor, P.strong.lobeCap), bS: Math.min(P.strong.bayAmp * e.minor, P.strong.bayCap)
});
const ellipseField = e => {
  const k = classOf(e), a = amplitudes(e), mean = Math.round(meanOf(e));
  const depth = a.aS + a.bS + P.coastMetres + P.fadeExtra, e0 = () => mul(mean, slider(e));
  const lobes = node('Mix', [mul(+a.aG.toFixed(1), imported(`Arrakis_Cont_LG_${k}`)), mul(+a.aS.toFixed(1), imported(`Arrakis_Cont_LS_${k}`)), imported('Arrakis_Cont_Blend')]);
  const bays = node('Mix', [mul(+a.bG.toFixed(1), imported(`Arrakis_Cont_BG_${k}`)), mul(+a.bS.toFixed(1), imported(`Arrakis_Cont_BS_${k}`)), imported('Arrakis_Cont_Blend')]);
  const fade = clamp01(sum(c(1), mul(-1 / depth, e0())));
  const field = sum(e0(), node('Multiplier', [sum(lobes, bays, mul(P.coastMetres, imported('Arrakis_Cont_CoastUnit'))), fade]));
  return node('Mix', [c(P.none), field, box(e.x, e.z, e.major + a.aS + a.bS + P.coastMetres + 300)], null, `Ellipse ${e.id} (continent ${e.continent}, class ${k}): centre (${e.x}, ${e.z}), ${e.major} x ${e.minor}, angle ${e.angle}.`);
};
for (const k of conts) {
  const es = of(k);
  writeAsset(`Density/Continent/Arrakis_Cont_${k}.json`, exported(`Arrakis_Cont_${k}`, [cache2d(node('Max', es.map(ellipseField)))],
    `Continent ${k} (${NAME[k]}, Arrakis_V${VARIANT[k]}): metres inside its shore, ${es.length} ellipses with blended edge noise; ${P.none} where there is none.`));
}

// ---------- seams between continents that nearly touch ----------
// Each continent that takes part gets a single-node distance field (distance to the nearest point of a lattice of points inside its ellipses): asset loading expands every
// import, and a field built from the ellipses (one per import site) made loading 12 s slower. The line where the two continents are equally far away runs through the middle of the gap.
const outline = e => { const a = e.angle * Math.PI / 180, r = []; for (let i = 0; i < 90; i++) { const t = i / 90 * Math.PI * 2, u = e.major * Math.cos(t), v = e.minor * Math.sin(t); r.push([e.x + u * Math.cos(a) - v * Math.sin(a), e.z + u * Math.sin(a) + v * Math.cos(a)]); } return r; };
const gapOf = (a, b) => { let best = 1e9; for (const p of of(a)) for (const q of of(b)) { if (Math.hypot(p.x - q.x, p.z - q.z) > p.major + q.major + best + 10) continue; const op = outline(p), oq = outline(q); for (const u of op) for (const v of oq) { const d = Math.hypot(u[0] - v[0], u[1] - v[1]); if (d < best) best = d; } } return best; };
const pairs = [];
for (const a of conts) for (const b of conts) if (a < b) { const g = gapOf(a, b); if (g < P.seamPairMax) pairs.push([a, b, Math.round(g)]); }
const seamConts = new Set(pairs.flatMap(p => [p[0], p[1]]));
const SEAM_MAX = 2500, SEAM_STEP = 300;
const latticeOf = k => {
  const es = of(k), pts = [], seen = new Set(), add = (x, z) => { const key = Math.round(x) + ',' + Math.round(z); if (!seen.has(key)) { seen.add(key); pts.push({ X: Math.round(x), Y: 0, Z: Math.round(z) }); } };
  const x0 = Math.min(...es.map(e => e.x - e.major)), x1 = Math.max(...es.map(e => e.x + e.major)), z0 = Math.min(...es.map(e => e.z - e.major)), z1 = Math.max(...es.map(e => e.z + e.major));
  for (let x = Math.floor(x0 / SEAM_STEP) * SEAM_STEP; x <= x1; x += SEAM_STEP) for (let z = Math.floor(z0 / SEAM_STEP) * SEAM_STEP; z <= z1; z += SEAM_STEP) if (es.some(e => inE(e, x, z))) add(x, z);
  for (const e of es) if (e.minor < SEAM_STEP) { const a = e.angle * Math.PI / 180, n = Math.max(1, Math.floor(e.major / 150)); for (let i = -n; i <= n; i++) add(e.x + Math.cos(a) * i * e.major / n * 0.95, e.z + Math.sin(a) * i * e.major / n * 0.95); }
  return pts;
};
for (const k of seamConts) { const pts = latticeOf(k); writeAsset(`Density/Continent/Arrakis_Cont_D_${k}.json`, exported(`Arrakis_Cont_D_${k}`, [cache2d({ Type: 'PositionsCellNoise', Skip: false, MaxDistance: SEAM_MAX,
  Positions: { Type: 'List', Skip: false, Positions: pts },
  ReturnType: { Type: 'Curve', Curve: curve([[0, 0], [SEAM_MAX - 1, SEAM_MAX - 1], [SEAM_MAX, SEAM_MAX]]) }, DistanceFunction: { Type: 'Euclidean' } })],
  `Continent ${k}: distance in metres to the nearest of ${pts.length} points of a ${SEAM_STEP} m lattice inside its ellipses (no edge noise); ${SEAM_MAX} beyond ${SEAM_MAX} m. Only used to find where continents nearly meet.`)); }
const D = k => imported(`Arrakis_Cont_D_${k}`);
// join: |distance to a - distance to b|, 10000 unless the two distances add up to less than 2 x seamReach (ramping over 100 m); 0 along the middle of the gap
const joinNode = ([a, b]) => node('Mix', [c(10000), node('CurveMapper', [sum(D(a), mul(-1, D(b)))], { Curve: curve([[-SEAM_MAX, SEAM_MAX], [0, 0], [SEAM_MAX, SEAM_MAX]]) }),
  clamp01(mul(1 / 100, sum(c(2 * P.seamReach), mul(-1, sum(D(a), D(b))))))]);
const cmin = node('Normalizer', [noise2d(P.seamScale, 2, 'Arrakis_Continent_Seam_Depth')], { FromMin: -1, FromMax: 1, ToMin: P.seamMin, ToMax: P.seamMax });
const slope = node('Normalizer', [noise2d(P.seamScale * 0.7, 2, 'Arrakis_Continent_Seam_Width')], { FromMin: -1, FromMax: 1, ToMin: P.seamSlopeMin, ToMax: P.seamSlopeMax });
writeAsset('Density/Continent/Arrakis_Cont_Seam.json', exported('Arrakis_Cont_Seam', [cache2d(sum(cmin, node('CurveMapper', [node('Multiplier', [pairs.length === 1 ? joinNode(pairs[0]) : node('Min', pairs.map(joinNode)), slope])],
  { Curve: curve([[0, 0], [P.seamRise, P.seamRise], [P.seamRise * 2, 1500]]) })))],
  `Cap on the continent fields along the lines where two continents nearly meet (pairs: ${pairs.map(p => p.join(' ')).join(', ')} [continent, continent, nominal gap in m]): ${P.seamMin} to ${P.seamMax} m at the middle of the gap, rising across a band of 150-400 m; large (no cap) elsewhere.`));
const capped = k => seamConts.has(k) ? `Arrakis_Cont_Cap_${k}` : `Arrakis_Cont_${k}`;
for (const k of seamConts) writeAsset(`Density/Continent/Arrakis_Cont_Cap_${k}.json`, exported(`Arrakis_Cont_Cap_${k}`, [cache2d(node('Min', [imported(`Arrakis_Cont_${k}`), imported('Arrakis_Cont_Seam')]))],
  `Continent ${k} with the seam cap.`));
// the union of the continents, in metres inside the shore (this is what the recipes and the caves read as "inside a large land mass"; without the seam cap, which is only applied to the rock bodies,
// because every recipe imports this field and each import is expanded at load time)
const all = { minX: Math.min(...shapes.map(e => e.x - e.major)) - 1500, maxX: Math.max(...shapes.map(e => e.x + e.major)) + 1500, minZ: Math.min(...shapes.map(e => e.z - e.major)) - 1500, maxZ: Math.max(...shapes.map(e => e.z + e.major)) + 1500 };
const wholeBox = () => node('Min', [
  node('CurveMapper', [{ Type: 'XValue', Skip: false }], { Curve: curve([[all.minX - 1, 0], [all.minX, 1], [all.maxX, 1], [all.maxX + 1, 0]]) }),
  node('CurveMapper', [{ Type: 'ZValue', Skip: false }], { Curve: curve([[all.minZ - 1, 0], [all.minZ, 1], [all.maxZ, 1], [all.maxZ + 1, 0]]) })]);
writeAsset('Density/Arrakis_Continent.json', exported('Arrakis_Continent', [cache2d(node('Mix', [c(P.none), node('Max', conts.map(k => imported(`Arrakis_Cont_${k}`))), wholeBox()]))],
  `Metres inside the shore of the nearest of the five continents; ${P.none} where there is none.`));

// ---------- the biome ----------
const biome = JSON.parse(fs.readFileSync(baselineFile, 'utf8'));
let rock = null;
(function find(n) { if (rock || !n || typeof n !== 'object') return; if (n.Type === 'Exported' && n.ExportAs === 'Arrakis_Rock') { rock = n; return; } for (const v of Object.values(n)) find(v); })(biome.Terrain);
const bodies = rock.Inputs[0].Inputs[0].Inputs[0]; // Sum > Max(everything, start) > Max(bodies)
if (bodies.Type !== 'Max' || bodies.Inputs.length !== 4 || !/^Land mass/.test(bodies.Inputs[3].$Comment || '')) throw new Error('the Land mass body is not where it was expected');
const land = bodies.Inputs[3];
const pickOf = v => -1 + 0.2 * (v - 1) + 0.1; // the middle of variant v's range of the pick (V1 -1..-0.8, ... V10 0.8..1)
const makeBody = k => {
  const v = VARIANT[k], body = copy(land);
  body.$Comment = `Continent ${k} (${NAME[k]}): Arrakis_V${v} lowered 8 blocks (as the land masses), inside its footprint only.`;
  const prune = max => { if (max.Type !== 'Max') throw new Error('unexpected body layout'); max.Inputs = [max.Inputs[v - 1]]; };
  const min = body.Inputs[1];
  if (min.Type !== 'Min') throw new Error('unexpected body layout');
  prune(min.Inputs[0]);
  let rootMax = null; (function f(n) { if (rootMax || !n || typeof n !== 'object') return; if (n.Type === 'Max' && n.Inputs.length === 10 && n.Inputs[0].Type === 'Mix') { rootMax = n; return; } for (const x of Object.values(n)) f(x); })(min.Inputs[2]);
  if (rootMax) prune(rootMax);
  (function swapPick(n) { if (!n || typeof n !== 'object') return; for (const [key, x] of Object.entries(n)) { if (x && x.Type === 'Imported' && x.Name === 'Arrakis_Pick') n[key] = c(pickOf(v)); else if (Array.isArray(x)) x.forEach((y, i) => { if (y && y.Type === 'Imported' && y.Name === 'Arrakis_Pick') x[i] = c(pickOf(v)); else swapPick(y); }); else swapPick(x); } })(body);
  const coast = min.Inputs[1];
  coast.Inputs[0].Inputs[0].Value = P.coastRise;
  coast.Inputs[0].Inputs[1] = cache2d(imported(capped(k)));
  coast.$Comment = 'Coast: rock may rise about 1.3 m for every metre in from the shore (the continent field is in metres).';
  const foot = body.Inputs[2];
  foot.Inputs[0].Inputs[1].Inputs[0] = cache2d(imported(capped(k)));
  foot.Inputs[0].Inputs[1].Inputs[1] = c(P.footprintMargin);
  foot.$Comment = `1 inside continent ${k}.`;
  return body;
};
for (const k of conts) bodies.Inputs.push(makeBody(k));
// recipes: how far inside its body a column is, now also for the continents
let inside = null;
(function find(n) { if (inside || !n || typeof n !== 'object') return; if (n.Type === 'Exported' && n.ExportAs === 'Arrakis_Inside') { inside = n; return; } for (const v of Object.values(n)) find(v); })(biome.Terrain);
const insideMax = inside.Inputs[0].Inputs[0].Inputs[0];
if (insideMax.Type !== 'Max' || insideMax.Inputs.length !== 5) throw new Error('unexpected Arrakis_Inside layout');
insideMax.Inputs.push(imported('Arrakis_Continent'));

// the old guaranteed island at (640, -480) and the starting outcrop at the origin: the continent has rock at the origin now (a brown islet), so they go
let guaranteed = 0, starts = 0;
(function drop(n) {
  if (!n || typeof n !== 'object') return;
  if (n.Type === 'List' && Array.isArray(n.Positions) && n.Positions.length === 1 && n.Positions[0].X === 640 && n.Positions[0].Z === -480) { n.Positions = []; guaranteed++; }
  for (const [k, v] of Object.entries(n)) { if (v && v.Type === 'Sum' && /^Starting outcrop field/.test(v.$Comment || '')) { n[k] = c(-1); starts++; } else if (Array.isArray(v)) v.forEach((x, i) => { if (x && x.Type === 'Sum' && /^Starting outcrop field/.test(x.$Comment || '')) { v[i] = c(-1); starts++; } else drop(x); }); else drop(v); }
})(biome);
if (guaranteed === 0 || starts === 0) throw new Error(`found ${guaranteed} guaranteed-island lists and ${starts} starting outcrop fields`);
console.log(`origin: ${guaranteed} copies of the guaranteed island emptied, ${starts} starting outcrop fields switched off`);

// ================= random islands outside the continents =================
// Frequency factor F(x, z): 0 inside the continent footprint (no centre inside it), 1 within nearCoast metres of the footprint, falling to deepLevel by farCoast metres,
// and deepLevel beyond the 50 km square. It multiplies the keep chance of every island grid and thins the free-standing outcrops.
const I = {
  nearCoast: 3000, farCoast: 10000, deepLevel: 0.1, outcropDeepLevel: 0.8, // the island grids thin to 0.1 of their near-coast chance, the free-standing outcrops only to 0.8 (they were already sparse)
  squareHalf: 25000, squareFade: 2000,
  margin: 11000,          // the distance field reaches this far outside the footprint
  lattice: 600, thinStep: 300, thinMinor: 350, // footprint lattice spacing; ellipses thinner than thinMinor get extra points along their axis every thinStep
  lat: {
    Arrakis_Islands_Drop: [[-27000, 0.1], [-21000, 0.6], [-12000, 0.5], [-1000, 0.7], [3000, 0.6], [12000, 0.3], [20000, 0.1]],
    Large: [[-27000, 0.05], [-21000, 0.3], [-12000, 0.5], [-5000, 0.3], [-1500, 0.12], [4000, 0.08], [12000, 0.04], [20000, 0.02]],
    Huge: [[-22000, 0], [-17000, 0.35], [-12000, 0.5], [-6000, 0.45], [-3000, 0]]
  }
};
// Distance outside the footprint: ONE cell-noise node over a lattice of points inside the ellipses (before edge noise). This field is imported by every copy of every island
// grid, and asset loading expands each import, so it has to be a single node. Distance to the nearest lattice point minus 0.7 of a cell: about 0 at the footprint edge, negative inside.
const lattice = [], seenPt = new Set(), addPt = (x, z) => { const key = Math.round(x) + ',' + Math.round(z); if (!seenPt.has(key)) { seenPt.add(key); lattice.push({ X: Math.round(x), Y: 0, Z: Math.round(z) }); } };
for (let x = -26400; x <= 26400; x += I.lattice) for (let z = -26400; z <= 10200; z += I.lattice) if (shapes.some(e => inE(e, x, z))) addPt(x, z);
for (const e of shapes) if (e.minor < I.thinMinor) { const a = e.angle * Math.PI / 180, n = Math.max(1, Math.floor(e.major / I.thinStep)); for (let i = -n; i <= n; i++) addPt(e.x + Math.cos(a) * i * e.major / n * 0.9, e.z + Math.sin(a) * i * e.major / n * 0.9); }
const off = I.lattice * 0.7;
writeAsset('Density/Arrakis_Continent_Dist.json', exported('Arrakis_Continent_Dist', [cacheBig({ Type: 'PositionsCellNoise', Skip: false, MaxDistance: I.margin,
  Positions: { Type: 'List', Skip: false, Positions: lattice },
  ReturnType: { Type: 'Curve', Curve: curve([[0, -off], [I.margin - 1, I.margin - 1 - off], [I.margin, 9999]]) }, DistanceFunction: { Type: 'Euclidean' } })],
  `Metres outside the continent footprint (a ${I.lattice} m lattice of points inside the ellipses, before edge noise); about -${Math.round(off)} m inside; 9999 beyond ${I.margin} m. ${lattice.length} points.`));
const sq = (axis, deep) => node('CurveMapper', [{ Type: axis, Skip: false }], { Curve: curve([[-(I.squareHalf + I.squareFade), deep], [-I.squareHalf, 1], [I.squareHalf, 1], [I.squareHalf + I.squareFade, deep]]) });
const frequency = (name, deep, what) => writeAsset(`Density/${name}.json`, exported(name, [cacheBig(node('Min', [
  node('CurveMapper', [imported('Arrakis_Continent_Dist')], { Curve: curve([[-100000, 0], [-1, 0], [0, 1], [I.nearCoast, 1], [I.farCoast, deep], [100000, deep]]) }),
  sq('XValue', deep), sq('ZValue', deep)]))],
  `${what} frequency factor, 0..1: 0 inside the continent footprint, 1 within ${I.nearCoast} m of it, ${deep} from ${I.farCoast} m and beyond the ${2 * I.squareHalf} m square.`));
frequency('Arrakis_Island_Frequency', I.deepLevel, 'Island grid');
frequency('Arrakis_Outcrop_Frequency', I.outcropDeepLevel, 'Free-standing outcrop');
let occ = 0, thinned = 0;
const freq = () => imported('Arrakis_Island_Frequency'), freqOut = () => imported('Arrakis_Outcrop_Frequency');
(function patch(n) {
  if (!n || typeof n !== 'object') return;
  if (n.Type === 'Occurrence' && n.FieldFunction && n.FieldFunction.Type === 'CurveMapper') {
    const seed = n.Seed || '';
    const key = seed === 'Arrakis_Islands_Drop' ? 'Arrakis_Islands_Drop' : /^Arrakis_Mass_Drop_Large/.test(seed) ? 'Large' : /^Arrakis_Mass_Drop_Huge/.test(seed) ? 'Huge' : null;
    if (key) { n.FieldFunction.Curve = curve(I.lat[key]); n.FieldFunction = { Type: 'Multiplier', Skip: false, Inputs: [n.FieldFunction, freq()] }; occ++; }
  }
  if (n.Type === 'PositionsCellNoise' && n.ReturnType && n.ReturnType.Type === 'CellValue' && n.ReturnType.Density && n.ReturnType.Density.Type === 'Max') {
    const m = JSON.stringify(n.ReturnType.Density).match(/Arrakis_Outcrops_Choice_(Medium|Small)/);
    if (m && !n.ReturnType.Density.$thinned) {
      n.ReturnType.Density.Inputs.push(node('CurveMapper', [node('Sum', [node('Normalizer', [{ Type: 'WhiteNoise', Seed: `Arrakis_Outcrops_Thin_${m[1]}`, Skip: false }], { FromMin: -1, FromMax: 1, ToMin: 0, ToMax: 1 }),
        node('Multiplier', [c(-1), freqOut()])])], { Curve: curve([[0, 0], [0.001, 10]]) }));
      n.ReturnType.Density.$thinned = true; thinned++;
    }
  }
  for (const v of Object.values(n)) patch(v);
})(biome);
(function clean(n) { if (!n || typeof n !== 'object') return; delete n.$thinned; for (const v of Object.values(n)) clean(v); })(biome);
if (occ === 0 || thinned === 0) throw new Error(`islands: found ${occ} grids and ${thinned} outcrop layers to patch`);
console.log(`islands: ${occ} grid copies re-curved and multiplied by the frequency factor, ${thinned} outcrop cell densities thinned; footprint lattice ${lattice.length} points`);

fs.writeFileSync(outFile, JSON.stringify(biome, null, 2) + '\n');
// outlines for the preview render (rotated ellipses)
fs.mkdirSync(path.join(here, 'out', 'continent'), { recursive: true });
fs.writeFileSync(path.join(here, 'out', 'continent', 'shapes_draw.json'), shapes.map(e => JSON.stringify({ id: e.id, x: e.x, z: e.z, halfEW: e.major, halfNS: e.minor, angle: e.angle, continent: e.continent })).join('\n'));
console.log(`continent: ${shapes.length} ellipses in ${conts.length} continents (${conts.map(k => `${NAME[k]} V${VARIANT[k]}`).join(', ')}); seam pairs: ${pairs.map(p => p.join(' ')).join('; ') || 'none'}; wrote ${path.relative(root, outFile)} and the Density files into ${pack}`);
