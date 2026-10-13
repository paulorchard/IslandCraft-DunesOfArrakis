// Island statistics from a coarse render dump (RealPreview COARSE=1 DUMP=...; 1000 x 1000 columns of 50 m over the 50 km square, rock top / 2 per byte).
//   node tools/preview/islandstats.js <dump.bin> [size=50000] [res=50]
// A "land mass" here is a connected piece of rock (8-neighbour) at least 4 columns big, at Base + 4 or more. Pieces that touch the nominal continent
// footprint belong to the continent and are not counted (so a random island merged with the coast counts as the continent).
// Class by area: outcrop (< 0.03 km2: small outcrops), medium (0.03-0.15: medium outcrops), island (0.15-0.8), large (0.8-4), huge (> 4).
// Distance from the coast is measured from the nominal ellipses (before lobes) to the piece's centroid.
const fs = require('fs');
const path = require('path');
const dump = process.argv[2];
const size = +process.argv[3] || 50000, res = +process.argv[4] || 50;
const w = Math.round(size / res);
const buf = fs.readFileSync(dump);
if (buf.length !== w * w) throw new Error(`dump is ${buf.length} bytes, expected ${w * w}`);
const ells = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'continent-shapes-v2.json'), 'utf8')); // rotated ellipses: x, z, major, minor, angle (degrees, major axis from +X towards +Z)
const inE = (e, x, z) => { const a = e.angle * Math.PI / 180, dx = x - e.x, dz = z - e.z, u = (dx * Math.cos(a) + dz * Math.sin(a)) / e.major, v = (-dx * Math.sin(a) + dz * Math.cos(a)) / e.minor; return u * u + v * v; };
const inCont = (x, z) => ells.some(e => inE(e, x, z) < 1);
const bpts = [];
for (const e of ells) for (let k = 0; k < 360; k++) { const t = k / 360 * Math.PI * 2, a = e.angle * Math.PI / 180, u = e.major * Math.cos(t), v = e.minor * Math.sin(t); bpts.push([e.x + u * Math.cos(a) - v * Math.sin(a), e.z + u * Math.sin(a) + v * Math.cos(a)]); }
// distance outside the footprint: to the nearest boundary point of an ellipse, ignoring points inside another ellipse (the union's outline)
const outline = bpts.filter(([x, z]) => !ells.some(e => inE(e, x, z) < 0.97));
const dist = (x, z) => { if (inCont(x, z)) return -1; let m = 1e9; for (const [px, pz] of outline) { const d = (px - x) ** 2 + (pz - z) ** 2; if (d < m) m = d; } return Math.sqrt(m); };
const X = i => -size / 2 + (i + 0.5) * res;
const rock = new Uint8Array(w * w);
for (let k = 0; k < w * w; k++) rock[k] = buf[k] * 2 >= 84 ? 1 : 0;
const seen = new Uint8Array(w * w);
const comps = [];
for (let s = 0; s < w * w; s++) {
  if (!rock[s] || seen[s]) continue;
  const stack = [s]; seen[s] = 1; let n = 0, sx = 0, sz = 0, touches = false;
  while (stack.length) {
    const k = stack.pop(); const i = k % w, j = (k - i) / w; n++; sx += i; sz += j;
    if (!touches && inCont(X(i), X(j))) touches = true;
    for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
      const ii = i + di, jj = j + dj; if (ii < 0 || jj < 0 || ii >= w || jj >= w) continue;
      const kk = jj * w + ii; if (rock[kk] && !seen[kk]) { seen[kk] = 1; stack.push(kk); }
    }
  }
  if (n < 4) continue;
  comps.push({ n, area: n * res * res / 1e6, x: X(sx / n - 0.5), z: X(sz / n - 0.5), touches });
}
const cls = a => a < 0.03 ? 'outcrop' : a < 0.15 ? 'medium' : a < 0.8 ? 'island' : a < 4 ? 'large' : 'huge';
const classes = ['outcrop', 'medium', 'island', 'large', 'huge'];
const free = comps.filter(c => !c.touches);
for (const c of free) { c.cls = cls(c.area); c.d = dist(c.x, c.z); }
// the area of each distance band, from a 250 m grid
const bandOf = d => d < 3000 ? 0 : d < 10000 ? 1 : 2;
const bandArea = [0, 0, 0];
for (let j = 0; j < 200; j++) for (let i = 0; i < 200; i++) { const x = -25000 + (i + 0.5) * 250, z = -25000 + (j + 0.5) * 250; const d = dist(x, z); if (d >= 0) bandArea[bandOf(d)] += 0.0625; }
const bandName = ['0-3 km', '3-10 km', 'beyond 10 km'];
const fmt = (v, p = 0) => v.toFixed(p);
console.log(`pieces: ${comps.length}, of them touching the continent ${comps.length - free.length}; counted ${free.length}`);
console.log('\nBy distance from the coast (counts; area of band in km2; one land mass per ... km2)');
console.log('band | area km2 | ' + classes.join(' | ') + ' | all | per km2 | spacing km');
for (let b = 0; b < 3; b++) {
  const cs = free.filter(c => bandOf(c.d) === b);
  const row = classes.map(k => cs.filter(c => c.cls === k).length);
  console.log(`${bandName[b]} | ${fmt(bandArea[b])} | ${row.join(' | ')} | ${cs.length} | ${(cs.length / bandArea[b]).toFixed(3)} | ${cs.length ? fmt(Math.sqrt(bandArea[b] / cs.length), 1) : '-'}`);
}
console.log('\nBy latitude (Z, north negative), 5 km bands, islands outside the continent: counts per class');
console.log('Z band | ' + classes.join(' | ') + ' | all');
for (let z0 = -25000; z0 < 25000; z0 += 5000) {
  const cs = free.filter(c => c.z >= z0 && c.z < z0 + 5000);
  console.log(`${z0 / 1000} .. ${(z0 + 5000) / 1000} km | ${classes.map(k => cs.filter(c => c.cls === k).length).join(' | ')} | ${cs.length}`);
}
const big = free.filter(c => c.cls === 'large' || c.cls === 'huge').sort((a, b) => b.area - a.area);
console.log('\nLargest counted pieces: ' + big.slice(0, 8).map(c => `${fmt(c.area, 1)} km2 at (${fmt(c.x)}, ${fmt(c.z)}), ${fmt(c.d / 1000, 1)} km from the coast`).join('; '));
// pieces whose centroid is within 1.5 km of the footprint but which do not touch it: nearly merged
const near = free.filter(c => c.d < 1500 && c.area >= 0.15);
console.log(`Pieces of 0.15 km2 or more within 1.5 km of the footprint but not touching: ${near.length}` + (near.length ? ' e.g. ' + near.slice(0, 5).map(c => `(${fmt(c.x)}, ${fmt(c.z)}) ${fmt(c.area, 2)} km2`).join('; ') : ''));
if (size > 50000) {
  // beyond the square: land masses per km2 in slabs 0-5, 5-10 km outside the square edge, east and west; 'inside' = the 10 km just inside the edge for comparison
  const slab = (lo, hi, sideSign) => { const cs = free.filter(c => c.x * sideSign >= lo && c.x * sideSign < hi && Math.abs(c.z) < 25000); const area = (hi - lo) / 1000 * 50; return `${cs.length} (${(cs.length / area).toFixed(3)} per km2)`; };
  console.log('\nBeyond the square, |Z| < 25 km, land masses (excluding pieces touching the continent):');
  for (const s of [-1, 1]) console.log(`${s < 0 ? 'west' : 'east'}: 20-25 km from the centre line ${slab(20000, 25000, s)}; 25-27 km ${slab(25000, 27000, s)}; 27-30 km ${slab(27000, 30000, s)}; 30-35 km ${slab(30000, 35000, s)}`);
}
