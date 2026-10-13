// Midpoints of the closest approach between continents (nominal ellipses): node tools/preview/seampoints.js [maxGap=1500]
const fs = require('fs'), path = require('path');
const shapes = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'continent-shapes-v2.json'), 'utf8')); const max = +process.argv[2] || 1500;
const outline = e => { const a = e.angle * Math.PI / 180, r = []; for (let i = 0; i < 180; i++) { const t = i / 180 * Math.PI * 2, u = e.major * Math.cos(t), v = e.minor * Math.sin(t); r.push([e.x + u * Math.cos(a) - v * Math.sin(a), e.z + u * Math.sin(a) + v * Math.cos(a)]); } return r; };
const names = { 1: 'purple', 2: 'green', 3: 'blue', 4: 'brown', 5: 'red' }; const out = [];
for (let a = 1; a <= 5; a++) for (let b = a + 1; b <= 5; b++) {
  let best = 1e9, mid = null;
  for (const p of shapes.filter(e => e.continent === a)) for (const q of shapes.filter(e => e.continent === b)) { if (Math.hypot(p.x - q.x, p.z - q.z) > p.major + q.major + best + 10) continue; for (const u of outline(p)) for (const v of outline(q)) { const d = Math.hypot(u[0] - v[0], u[1] - v[1]); if (d < best) { best = d; mid = [Math.round((u[0] + v[0]) / 2), Math.round((u[1] + v[1]) / 2)]; } } }
  if (best < max) out.push({ a: names[a], b: names[b], gap: Math.round(best), x: mid[0], z: mid[1] });
}
console.log(out.map(o => `${o.a}-${o.b}: gap ${o.gap} m at (${o.x}, ${o.z})`).join('\n'));
fs.writeFileSync(path.join(__dirname, 'out', 'continent', 'seam_pairs.json'), JSON.stringify(out));
