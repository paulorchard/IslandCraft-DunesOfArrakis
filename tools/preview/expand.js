// How many nodes does asset loading expand? Imports are expanded as copies, so the cost of a field is its size times the number of places that import it.
//   node tools/preview/expand.js [packRoot]   (default src/main/resources)
const fs = require('fs'), path = require('path');
const pack = path.resolve(process.argv[2] || path.join(__dirname, '..', '..', 'src', 'main', 'resources'));
const gen = path.join(pack, 'Server', 'HytaleGenerator');
const defs = new Map(), roots = [];
const walkDir = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walkDir(path.join(d, e.name)) : [path.join(d, e.name)]);
function register(n) { if (!n || typeof n !== 'object') return; if (typeof n.ExportAs === 'string' && !defs.has(n.ExportAs)) defs.set(n.ExportAs, n); for (const v of Object.values(n)) register(v); }
for (const f of walkDir(gen).filter(f => f.endsWith('.json'))) { const j = JSON.parse(fs.readFileSync(f, 'utf8')); register(j); if (f.includes('Biomes')) roots.push(j); }
const memo = new Map(), uses = new Map();
function size(n, top) {
  if (!n || typeof n !== 'object') return 0;
  let s = n.Type ? 1 : 0;
  if (n.Type === 'Imported') { const d = defs.get(n.Name); if (!d) return 1; uses.set(n.Name, (uses.get(n.Name) || 0) + 1); if (!memo.has(n.Name)) { memo.set(n.Name, -1); memo.set(n.Name, sizeOf(d)); } return 1 + memo.get(n.Name); }
  for (const v of Object.values(n)) s += size(v);
  return s;
}
function sizeOf(d) { let s = 0; for (const v of Object.values(d)) s += size(v); return s + 1; }
let total = 0; for (const r of roots) total += size(r);
console.log('expanded nodes in the biomes:', total);
const rows = [...memo.entries()].map(([name, s]) => ({ name, s, uses: uses.get(name) || 0 })).map(r => ({ ...r, cost: r.s * r.uses })).sort((a, b) => b.cost - a.cost);
console.log('largest (size x number of import sites; sites counted once per expansion of the importing tree is not included):');
for (const r of rows.slice(0, 12)) console.log(`  ${r.name}: size ${r.s}, imported ${r.uses}x`);
if (process.env.SHOW) for (const [name, s] of [...memo.entries()].filter(([n]) => new RegExp(process.env.SHOW).test(n)).sort((a, b) => b[1] - a[1])) console.log(`  ${name}: size ${s}, imported ${uses.get(name) || 0}x`);
