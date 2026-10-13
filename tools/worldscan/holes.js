// Cutouts in a scan: columns with no rock at all (no block named Rock_* other than bedrock) that are completely surrounded by columns with rock.
//   node tools/worldscan/holes.js <scan.bin> [maxAreaM2=20000]
const fs = require('fs');
const buf = fs.readFileSync(process.argv[2]); const maxArea = +process.argv[3] || 20000;
let o = 4; if (buf.toString('latin1', 0, 4) !== 'SCN1') throw new Error('not a scan');
const minX = buf.readInt32BE(o), minZ = buf.readInt32BE(o + 4), nx = buf.readInt32BE(o + 8), nz = buf.readInt32BE(o + 12), ny = buf.readInt32BE(o + 16), np = buf.readInt32BE(o + 20); o += 24;
const pal = []; for (let i = 0; i < np; i++) { const len = buf.readUInt16BE(o); o += 2; pal.push(buf.toString('utf8', o, o + len)); o += len; }
const isRock = pal.map(n => /Rock_/.test(n) && !/Bedrock/.test(n));
const rock = new Uint8Array(nx * nz), topY = new Int16Array(nx * nz), solidDepth = new Int16Array(nx * nz);
for (let j = 0; j < nz; j++) for (let i = 0; i < nx; i++) {
  const base = (1 << 20) + (j * nx + i) * ny * 2; let r = 0, top = -1;
  for (let y = 0; y < ny; y++) { const p = buf.readUInt16BE(base + y * 2); if (isRock[p]) { r = 1; top = y; } }
  rock[j * nx + i] = r; topY[j * nx + i] = top;
}
const seen = new Uint8Array(nx * nz); const cuts = [];
for (let s = 0; s < nx * nz; s++) {
  if (rock[s] || seen[s]) continue; const st = [s]; seen[s] = 1; let n = 0, edge = false, sx = 0, sz = 0;
  while (st.length) { const k = st.pop(); n++; const i = k % nx, j = (k - i) / nx; sx += i; sz += j; if (i === 0 || j === 0 || i === nx - 1 || j === nz - 1) edge = true;
    for (const [di, dj] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const ii = i + di, jj = j + dj; if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue; const kk = jj * nx + ii; if (!rock[kk] && !seen[kk]) { seen[kk] = 1; st.push(kk); } } }
  if (!edge) cuts.push({ n, x: minX + sx / n, z: minZ + sz / n });
}
cuts.sort((a, b) => a.n - b.n);
const rockCols = rock.reduce((a, b) => a + b, 0);
console.log(`scan ${nx} x ${nz} columns, ${rockCols} with rock; enclosed cutouts: ${cuts.length} (${cuts.filter(c => c.n < maxArea).length} under ${maxArea} m2), total ${cuts.reduce((a, c) => a + c.n, 0)} columns`);
console.log('largest few: ' + cuts.slice(-5).map(c => `${c.n} cols at (${Math.round(c.x)}, ${Math.round(c.z)})`).join('; '));
fs.writeFileSync(process.argv[2] + '.cutouts.json', JSON.stringify(cuts));
