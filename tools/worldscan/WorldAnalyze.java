import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**
 * Reads a dump from WorldScanPlugin (real generated blocks) and reports.
 *   java WorldAnalyze.java <scan.bin> [baseScan.bin]
 * With a second dump of the same region from a world generated without the feature (before the caves, or before the ore) it also lists every block that changed.
 * Block classes: Empty = air; "covered" air = air with a solid block somewhere above it in its column; sky air = air above the column's highest solid block.
 * An ore block is "exposed" when one of its 6 neighbours is air. Ore rules measured: sky-exposed (a neighbour is sky air), near an opening
 * (a neighbour is within 10 steps through air of sky air), buried (more than 3 steps of solid from the nearest air), and the deposits.
 */
public class WorldAnalyze {
    static final int BASE = 80;
    static int nx, nz, ny, minX, minZ;
    static String[] palette;

    static short[] load(String file) throws IOException {
        try (FileChannel ch = FileChannel.open(Paths.get(file), StandardOpenOption.READ)) {
            ByteBuffer hb = ByteBuffer.allocate(1 << 20); ch.read(hb, 0); hb.flip();
            DataInputStream d = new DataInputStream(new ByteArrayInputStream(hb.array(), 0, hb.limit()));
            byte[] magic = new byte[4]; d.readFully(magic);
            minX = d.readInt(); minZ = d.readInt(); nx = d.readInt(); nz = d.readInt(); ny = d.readInt(); int ps = d.readInt();
            palette = new String[ps]; for (int i = 0; i < ps; i++) palette[i] = d.readUTF();
            short[] a = new short[nx * nz * ny];
            ByteBuffer bb = ByteBuffer.allocate(ny * 2 * 1024);
            long pos = 1L << 20; int col = 0;
            while (col < nx * nz) { int n = Math.min(1024, nx * nz - col); bb.clear().limit(n * ny * 2); ch.read(bb, pos); bb.flip(); bb.asShortBuffer().get(a, col * ny, n * ny); col += n; pos += (long) n * ny * 2; }
            return a;
        }
    }

    static int at(int x, int y, int z) { return (z * nx + x) * ny + y; }

    public static void main(String[] args) throws Exception {
        short[] a = load(args[0]);
        String[] pal = palette;
        System.out.printf("region (%d, %d) %d x %d blocks, Y 0..%d, palette %s%n", minX, minZ, nx, nz, ny - 1, Arrays.toString(pal));
        Map<String, Long> counts = new TreeMap<>(); long[] byId = new long[pal.length];
        for (short s : a) byId[s]++;
        for (int i = 0; i < pal.length; i++) counts.put(pal[i], byId[i]);
        System.out.println("blocks: " + counts);
        boolean[] ore = new boolean[pal.length]; int emptyId = -1;
        for (int i = 0; i < pal.length; i++) { if (pal[i].startsWith("Ore_")) ore[i] = true; if (pal[i].equals("Empty")) emptyId = i; }
        final int E = emptyId;

        if (args.length > 1) compare(a, args[1]);

        // heights of the highest solid block per column, sky air, covered air
        int[] top = new int[nx * nz];
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) { int t = -1; for (int y = ny - 1; y >= 0; y--) if (a[at(x, y, z)] != E) { t = y; break; } top[z * nx + x] = t; }
        long covered = 0, rock = 0, sandstone = 0;
        int sandstoneId = -1; for (int i = 0; i < pal.length; i++) if (pal[i].equals("Rock_Sandstone")) sandstoneId = i;
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) for (int y = 0; y < top[z * nx + x]; y++) { short s = a[at(x, y, z)]; if (s == E) covered++; }
        for (short s : a) if (s == sandstoneId) sandstone++;
        System.out.printf("covered air %d m3, Rock_Sandstone %d m3 (%.1f%% as much as the air)%n", covered, sandstone, 100.0 * covered / Math.max(1, covered + sandstone));

        // a deep chamber: the covered air voxel at Y 8..45 nearest the centre of the region that has a 7 x 7 x 7 block of air round it
        { double cxr = minX + nx / 2.0, czr = minZ + nz / 2.0, bd = 1e18; int[] best = null;
          for (int z = 3; z < nz - 3; z++) for (int x = 3; x < nx - 3; x++) { double dd = Math.hypot(minX + x - cxr, minZ + z - czr); if (dd >= bd) continue;
              for (int y = 8; y <= 45; y++) { if (a[at(x, y, z)] != E) continue; boolean ok = true;
                  for (int dx = -3; dx <= 3 && ok; dx++) for (int dz = -3; dz <= 3 && ok; dz++) for (int dy = -3; dy <= 3; dy++) if (a[at(x + dx, y + dy, z + dz)] != E) { ok = false; break; }
                  if (ok) { bd = dd; best = new int[]{minX + x, y, minZ + z}; break; } } }
          System.out.println(best == null ? "deep chamber: none in this region" : String.format("deep chamber (a 7-block cube of air, Y 8..45) nearest the centre: (%d, %d, %d), %.0f m from the centre", best[0], best[1], best[2], bd)); }
        // geodesic distance through air from sky air, up to 11 steps (0 = sky air itself)
        byte[] dist = new byte[a.length]; Arrays.fill(dist, (byte) 127);
        int[] queue = new int[1 << 22]; int qh = 0, qt = 0;
        long skyCount = 0;
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) { int t = top[z * nx + x]; for (int y = t + 1; y < ny; y++) { int v = at(x, y, z); dist[v] = 0; skyCount++; } }
        // seed the BFS with the sky voxels that touch non-sky air (everything else cannot spread)
        int[][] d6 = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        List<Integer> frontier = new ArrayList<>();
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) { int t = top[z * nx + x]; for (int y = Math.max(0, t + 1); y < ny; y++) {
            boolean edge = false; for (int[] e : d6) { int xx = x + e[0], yy = y + e[1], zz = z + e[2]; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; int u = at(xx, yy, zz); if (a[u] == E && dist[u] != 0) { edge = true; break; } }
            if (edge) frontier.add(at(x, y, z)); } }
        int[] cur = frontier.stream().mapToInt(i -> i).toArray(); int curN = cur.length;
        for (int step = 1; step <= 11 && curN > 0; step++) { int[] next = new int[Math.max(16, curN * 4)]; int nn = 0;
            for (int q = 0; q < curN; q++) { int v = cur[q]; int y = v % ny, x = (v / ny) % nx, z = v / (ny * nx);
                for (int[] e : d6) { int xx = x + e[0], yy = y + e[1], zz = z + e[2]; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; int u = at(xx, yy, zz);
                    if (a[u] == E && dist[u] == 127) { dist[u] = (byte) step; if (nn == next.length) next = Arrays.copyOf(next, nn * 2); next[nn++] = u; } } }
            cur = next; curN = nn; }

        // a cave mouth: covered air 1-3 steps from open sky, at Y 82 or more, with a 3 x 3 x 3 block of air round it (a passage, not a crack), nearest the centre of the region
        { double cxr = minX + nx / 2.0, czr = minZ + nz / 2.0, bd = 1e18; int[] best = null;
          for (int z = 2; z < nz - 2; z++) for (int x = 2; x < nx - 2; x++) { double dd = Math.hypot(minX + x - cxr, minZ + z - czr); if (dd >= bd) continue;
              for (int y = 82; y < Math.min(ny - 2, top[z * nx + x]); y++) { int dv = dist[at(x, y, z)]; if (dv < 1 || dv > 3) continue; boolean ok = true;
                  for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1 && ok; dz++) for (int dy = -1; dy <= 1; dy++) if (a[at(x + dx, y + dy, z + dz)] != E) { ok = false; break; }
                  if (ok) { bd = dd; best = new int[]{minX + x, y, minZ + z}; break; } } }
          System.out.println(best == null ? "cave mouth: none in this region" : String.format("cave mouth nearest the centre: (%d, %d, %d), %.0f m from the centre", best[0], best[1], best[2], bd)); }
        long oreTotal = 0; long[] oreBy = new long[pal.length];
        for (int i = 0; i < a.length; i++) if (ore[a[i]]) { oreTotal++; oreBy[a[i]]++; }
        if (oreTotal == 0) { System.out.println("ore: none"); return; }

        // per ore voxel: exposed, sky-exposed, near an opening, distance to air
        long exposed = 0, skyExposed = 0, nearOpening = 0, buried = 0;
        // distance from air through solid, up to 6 steps
        byte[] sd = new byte[a.length]; Arrays.fill(sd, (byte) 127);
        int[] fr = new int[1 << 20]; int fn = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != E) { int y = i % ny, x = (i / ny) % nx, z = i / (ny * nx);
            for (int[] e : d6) { int xx = x + e[0], yy = y + e[1], zz = z + e[2]; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; if (a[at(xx, yy, zz)] == E) { sd[i] = 1; if (fn == fr.length) fr = Arrays.copyOf(fr, fn * 2); fr[fn++] = i; break; } } }
        for (int step = 2; step <= 6 && fn > 0; step++) { int[] nx2 = new int[Math.max(16, fn * 2)]; int nn = 0;
            for (int q = 0; q < fn; q++) { int v = fr[q]; int y = v % ny, x = (v / ny) % nx, z = v / (ny * nx);
                for (int[] e : d6) { int xx = x + e[0], yy = y + e[1], zz = z + e[2]; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; int u = at(xx, yy, zz);
                    if (a[u] != E && sd[u] == 127) { sd[u] = (byte) step; if (nn == nx2.length) nx2 = Arrays.copyOf(nx2, nn * 2); nx2[nn++] = u; } } }
            fr = nx2; fn = nn; }
        List<int[]> violators = new ArrayList<>();
        for (int i = 0; i < a.length; i++) { if (!ore[a[i]]) continue; int y = i % ny, x = (i / ny) % nx, z = i / (ny * nx);
            boolean ex = false, sky = false, near = false;
            for (int[] e : d6) { int xx = x + e[0], yy = y + e[1], zz = z + e[2]; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; int u = at(xx, yy, zz);
                if (a[u] == E) { ex = true; if (dist[u] == 0) sky = true; if (dist[u] <= 10) near = true; } }
            if (ex) exposed++; if (sky) skyExposed++; if (near) { nearOpening++; if (violators.size() < 8) violators.add(new int[]{minX + x, y, minZ + z}); }
            if (sd[i] > 3) buried++; }
        System.out.printf("ore blocks: %d (%s)%n", oreTotal, oreSummary(oreBy));
        System.out.printf("  touching air: %d (%.1f%%); with a sky-air neighbour: %d; within 10 steps of an opening: %d (e.g. %s); buried more than 3 blocks from any air: %d%n",
            exposed, 100.0 * exposed / oreTotal, skyExposed, nearOpening, formatList(violators), buried);

        // deposits: 26-connected components of one ore type; sizes and heights
        boolean[] seen = new boolean[a.length];
        Map<String, List<int[]>> deposits = new TreeMap<>(); // name -> list of {size, minY, maxY, touchesAir(0/1), centroidX, centroidY, centroidZ}
        int[] stack = new int[1 << 20];
        for (int i = 0; i < a.length; i++) { if (!ore[a[i]] || seen[i]) continue; short type = a[i]; int sp = 0; stack[sp++] = i; seen[i] = true; int size = 0, lo = ny, hi = 0, air = 0; long sx = 0, sy = 0, sz = 0;
            while (sp > 0) { int v = stack[--sp]; size++; int y = v % ny, x = (v / ny) % nx, z = v / (ny * nx); lo = Math.min(lo, y); hi = Math.max(hi, y); if (sd[v] == 1) air = 1; sx += x; sy += y; sz += z;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) { int xx = x + dx, yy = y + dy, zz = z + dz; if (xx < 0 || zz < 0 || yy < 0 || xx >= nx || zz >= nz || yy >= ny) continue; int u = at(xx, yy, zz);
                    if (a[u] == type && !seen[u]) { seen[u] = true; if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } } }
            deposits.computeIfAbsent(pal[type], k -> new ArrayList<>()).add(new int[]{size, lo, hi, air, (int) (minX + sx / size), (int) (sy / size), (int) (minZ + sz / size)}); }
        System.out.println("deposits per ore (blocks per deposit: smallest / median / largest; deposits not touching air; height of the lowest and highest block, relative to Base " + BASE + "):");
        for (Map.Entry<String, List<int[]>> en : deposits.entrySet()) { List<int[]> l = en.getValue(); int[] sizes = l.stream().mapToInt(d -> d[0]).sorted().toArray();
            int lowest = l.stream().mapToInt(d -> d[1]).min().getAsInt(), highest = l.stream().mapToInt(d -> d[2]).max().getAsInt(); long noAir = l.stream().filter(d -> d[3] == 0).count();
            System.out.printf("  %s: %d deposits, %d / %d / %d blocks, %d not touching air, Y %d .. %d (Base %+d .. %+d)%n", en.getKey(), l.size(), sizes[0], sizes[sizes.length / 2], sizes[sizes.length - 1], noAir, lowest, highest, lowest - BASE, highest - BASE); }
        // samples: for each ore the deposit of 5 or more blocks nearest to the centre of the region; and a deep chamber
        double cxr = minX + nx / 2.0, czr = minZ + nz / 2.0;
        System.out.println("samples: (x, y, z) of a deposit of 5 or more blocks nearest to the centre of the region (" + (int) cxr + ", " + (int) czr + "):");
        for (Map.Entry<String, List<int[]>> en : deposits.entrySet()) { int[] best = null; double bd = 1e18;
            for (int[] d : en.getValue()) { if (d[0] < 5) continue; double dd = Math.hypot(d[4] - cxr, d[6] - czr); if (dd < bd) { bd = dd; best = d; } }
            if (best != null) System.out.printf("  %s: (%d, %d, %d), %d blocks, %.0f m from the centre%n", en.getKey(), best[4], best[5], best[6], best[0], bd); }
        // depth histogram: 10-block bands relative to Base
        System.out.println("depth histogram, ore blocks per 10-block band (Y - Base):");
        for (int i = 0; i < pal.length; i++) { if (!ore[i]) continue; long[] band = new long[30]; // -100 .. +200
            for (int j = 0; j < a.length; j++) if (a[j] == i) { int y = j % ny; int b = Math.floorDiv(y - BASE + 100, 10); if (b >= 0 && b < 30) band[b]++; }
            StringBuilder sb = new StringBuilder(); for (int b = 0; b < 30; b++) if (band[b] > 0) sb.append(String.format(" %+d:%d", b * 10 - 100, band[b])); System.out.println("  " + pal[i] + sb); }
    }

    static String oreSummary(long[] by) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < by.length; i++) if (by[i] > 0 && palette[i].startsWith("Ore_")) sb.append(palette[i]).append('=').append(by[i]).append(' '); return sb.toString().trim(); }
    static String formatList(List<int[]> l) { StringBuilder sb = new StringBuilder(); for (int[] p : l) sb.append(String.format("(%d, %d, %d) ", p[0], p[1], p[2])); return sb.toString().trim(); }

    /** Blocks that differ from the base dump (same region). */
    static void compare(short[] a, String baseFile) throws IOException {
        String[] palA = palette; int ax = nx, az = nz, ay = ny, mx = minX, mz = minZ;
        short[] b = load(baseFile); String[] palB = palette;
        if (ax != nx || az != nz || ay != ny || mx != minX || mz != minZ) { System.out.println("compare: regions differ"); palette = palA; return; }
        Map<String, Long> changes = new TreeMap<>(); long same = 0;
        for (int i = 0; i < a.length; i++) { String p = palB[b[i]], q = palA[a[i]]; if (p.equals(q)) { same++; continue; } changes.merge(p + " -> " + q, 1L, Long::sum); }
        System.out.println("changed blocks against " + baseFile + ": " + changes);
        palette = palA;
    }
}
