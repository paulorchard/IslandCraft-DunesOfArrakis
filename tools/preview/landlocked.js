// Landlocked sand from a coarse render dump (RealPreview COARSE=1 DUMP=...): connected regions of sand (top below Base + 4) that never reach the edge of the square.
//   node tools/preview/landlocked.js <dump.bin> [size=50000] [res=50] [erode=0]
// erode = number of columns the sand is eroded by first, so channels narrower than 2 x erode x res count as closed (a worm needs room to pass).
const fs = require('fs');
const dump = process.argv[2], size = +process.argv[3] || 50000, res = +process.argv[4] || 50, erode = +process.argv[5] || 0;
const w = Math.round(size / res), buf = fs.readFileSync(dump);
let sand = new Uint8Array(w * w); for (let k = 0; k < w * w; k++) sand[k] = buf[k] * 2 < 84 ? 1 : 0;
for (let e = 0; e < erode; e++) { const n = new Uint8Array(w * w); for (let j = 1; j < w - 1; j++) for (let i = 1; i < w - 1; i++) { const k = j * w + i; n[k] = sand[k] && sand[k - 1] && sand[k + 1] && sand[k - w] && sand[k + w] ? 1 : 0; } for (let i = 0; i < w; i++) { n[i] = sand[i]; n[(w - 1) * w + i] = sand[(w - 1) * w + i]; n[i * w] = sand[i * w]; n[i * w + w - 1] = sand[i * w + w - 1]; } sand = n; }
const seen = new Uint8Array(w * w), pockets = []; let open = 0;
for (let s = 0; s < w * w; s++) {
  if (!sand[s] || seen[s]) continue; const st = [s]; seen[s] = 1; let n = 0, edge = false, sx = 0, sz = 0;
  while (st.length) { const k = st.pop(); n++; const i = k % w, j = (k - i) / w; sx += i; sz += j; if (i === 0 || j === 0 || i === w - 1 || j === w - 1) edge = true;
    for (const [di, dj] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const ii = i + di, jj = j + dj; if (ii < 0 || jj < 0 || ii >= w || jj >= w) continue; const kk = jj * w + ii; if (sand[kk] && !seen[kk]) { seen[kk] = 1; st.push(kk); } } }
  if (edge) open += n * res * res / 1e6; else pockets.push({ km2: n * res * res / 1e6, x: -size / 2 + (sx / n) * res, z: -size / 2 + (sz / n) * res });
}
pockets.sort((a, b) => b.km2 - a.km2);
const bins = [[0, 0.01], [0.01, 0.1], [0.1, 1], [1, 1e9]];
console.log(`erode ${erode}: open sand ${open.toFixed(0)} km2; landlocked pockets: ${pockets.length}, ${pockets.reduce((a, p) => a + p.km2, 0).toFixed(1)} km2 in all`);
for (const [lo, hi] of bins) { const ps = pockets.filter(p => p.km2 >= lo && p.km2 < hi); console.log(`  ${lo}-${hi === 1e9 ? '' : hi} km2: ${ps.length} pockets, ${ps.reduce((a, p) => a + p.km2, 0).toFixed(2)} km2`); }
console.log('  largest: ' + pockets.slice(0, 5).map(p => `${p.km2.toFixed(2)} km2 at (${Math.round(p.x)}, ${Math.round(p.z)})`).join('; '));
