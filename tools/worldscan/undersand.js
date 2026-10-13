// Caves under sand: air below the sand surface in columns that have no rock (Rock_* other than bedrock) at all. Expected 0.
//   node tools/worldscan/undersand.js <scan.bin>
const fs = require('fs'); const buf = fs.readFileSync(process.argv[2]); let o = 4;
const nx = buf.readInt32BE(o + 8), nz = buf.readInt32BE(o + 12), ny = buf.readInt32BE(o + 16), np = buf.readInt32BE(o + 20); o += 24;
const pal = []; for (let i = 0; i < np; i++) { const l = buf.readUInt16BE(o); o += 2; pal.push(buf.toString('utf8', o, o + l)); o += l; }
const rock = pal.map(n => /Rock_/.test(n) && !/Bedrock/.test(n)), empty = pal.map(n => n === 'Empty');
let sandCols = 0, badCols = 0, badVox = 0, rockCols = 0, rockColsWithVoidBelowSand = 0;
for (let j = 0; j < nz; j++) for (let i = 0; i < nx; i++) {
  const base = (1 << 20) + (j * nx + i) * ny * 2; let hasRock = false, top = -1; const col = [];
  for (let y = 0; y < ny; y++) { const p = buf.readUInt16BE(base + y * 2); col.push(p); if (rock[p]) hasRock = true; if (!empty[p]) top = y; }
  if (hasRock) { rockCols++; continue; }
  sandCols++; let bad = 0; for (let y = 1; y < top; y++) if (empty[col[y]]) bad++;
  if (bad) { badCols++; badVox += bad; }
}
console.log(`columns with rock: ${rockCols}; columns with no rock (sand): ${sandCols}; sand columns with air below the surface: ${badCols} (${badVox} voxels)`);
