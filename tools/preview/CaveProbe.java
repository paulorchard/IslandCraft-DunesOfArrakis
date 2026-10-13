import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.builtin.hytalegenerator.assets.ThreadBridge;
import com.hypixel.hytale.builtin.hytalegenerator.assets.biomes.BiomeAsset;
import com.hypixel.hytale.builtin.hytalegenerator.assets.density.DensityAsset;
import com.hypixel.hytale.builtin.hytalegenerator.assets.worldstructures.WorldStructureAsset;
import com.hypixel.hytale.builtin.hytalegenerator.assets.framework.FrameworkAsset;
import com.hypixel.hytale.builtin.hytalegenerator.assets.terrains.TerrainAsset;
import com.hypixel.hytale.builtin.hytalegenerator.referencebundle.ReferenceBundle;
import com.hypixel.hytale.builtin.hytalegenerator.density.Density;
import com.hypixel.hytale.builtin.hytalegenerator.rng.SeedBox;
import com.hypixel.hytale.builtin.hytalegenerator.workerindexer.WorkerIndexer;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Cave measurements with the game's own generator classes.
 *   java -cp HytaleServer.jar CaveProbe.java <packRoot> <seed> <centreX> <centreZ> <sizeMetres> <outPrefix>
 * Voxels are 2 m cubes from Y 1 up to YMAX (default 180). Each voxel is classified from two densities:
 *   0 open air (not solid, outside every rock body)   1 rock (solid, inside a rock body)
 *   2 sand (solid, outside every rock body)           3 cave air (not solid, inside a rock body)
 * "Inside a rock body" is Arrakis_Rock > 0 (before carving, solid wins there, so a body voxel that is not solid is carved).
 * Env: DUMP=<file> writes the class array; CMP=<file> compares against such a dump (the same region, taken before the caves).
 */
public class CaveProbe {
    static PrintStream out;
    static final int STEP = 2;

    public static void main(String[] a) throws Exception {
        String prefix = a[5];
        out = new PrintStream(new FileOutputStream(prefix + ".txt"), true);
        try { run(Paths.get(a[0]), Long.parseLong(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]), prefix); }
        catch (Throwable t) { t.printStackTrace(out); Runtime.getRuntime().halt(1); }
        Runtime.getRuntime().halt(0);
    }

    static String env(String n, String d) { String v = System.getenv(n); return v == null || v.isEmpty() ? d : v; }

    static void run(Path packRoot, long seed, double cx, double cz, double size, String prefix) throws Exception {
        bootFakeServer();
        Class.forName("com.hypixel.hytale.builtin.hytalegenerator.assets.AssetManager");
        Path genRoot = packRoot.resolve("Server");
        // Density assets first, so imports by name resolve; the loader order of the stores is otherwise the registry's.
        List<AssetStore<?, ?, ?>> stores = new ArrayList<>(AssetRegistry.getStoreMap().values());
        for (AssetStore<?, ?, ?> store : stores) {
            Path dir = genRoot.resolve(store.getPath());
            if (!Files.isDirectory(dir)) continue;
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) { files = s.filter(p -> p.toString().endsWith(".json")).toList(); }
            if (files.isEmpty()) continue;
            var r = store.loadAssetsFromPaths("IslandCraft:IslandCraft - Dunes of Arrakis", files);
            out.println("loaded " + store.getPath() + ": " + r.getLoadedAssets().size() + " failed=" + r.getFailedToLoadPaths());
        }
        WorldStructureAsset ws = AssetRegistry.getAssetStore(WorldStructureAsset.class).getAssetMap().getAsset("Dunes_of_Arrakis");
        SeedBox seedBox = new SeedBox((int) seed);
        WorldStructureAsset.Argument arg = new WorldStructureAsset.Argument(null, seedBox, WorkerIndexer.Id.MAIN, new ThreadBridge());
        ReferenceBundle bundle = new ReferenceBundle();
        Field fw = ws.getClass().getDeclaredField("frameworkAssets"); fw.setAccessible(true);
        for (FrameworkAsset fa : (FrameworkAsset[]) fw.get(ws)) fa.build(arg, bundle);
        BiomeAsset biome = BiomeAsset.getAssetStore().getAssetMap().getAsset("Arrakis_Terrain");
        Field ta = BiomeAsset.class.getDeclaredField("terrainAsset"); ta.setAccessible(true);
        Density terrain = ((TerrainAsset) ta.get(biome)).buildDensity(arg.parentSeed, bundle, arg.workerId, arg.threadBridge);
        DensityAsset.Exported rockExport = DensityAsset.getExportedAsset("Arrakis_Rock");
        Density rock = rockExport.threadInstances.get(WorkerIndexer.Id.MAIN);
        DensityAsset.Exported largeExport = DensityAsset.getExportedAsset("Arrakis_Large_Inside");
        Density large = largeExport == null ? null : largeExport.threadInstances.get(WorkerIndexer.Id.MAIN);
        out.println("built; large footprint export " + (large != null));

        if (!env("ISLANDS", "").isEmpty()) { islands(terrain, rock, large, cx, cz, size); return; }

        final int yMax = Integer.parseInt(env("YMAX", "180")), base = 80;
        final int nx = (int) (size / STEP), nz = nx, ny = (yMax - 1) / STEP + 1;
        byte[] cls = new byte[nx * nz * ny];
        boolean[] largeCol = new boolean[nx * nz];
        Density.Context ctx = new Density.Context(); ctx.position = new Vector3d(); ctx.distanceToBiomeEdge = 1000;
        long t0 = System.nanoTime();
        double x0 = cx - size / 2, z0 = cz - size / 2;
        for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) {
            double x = x0 + i * STEP, z = z0 + k * STEP;
            if (large != null) { ctx.position.set(x, 0, z); largeCol[k * nx + i] = large.evaluate(ctx) > Double.parseDouble(env("LARGE_MIN", "20")); }
            for (int j = 0; j < ny; j++) {
                ctx.position.set(x, 1 + j * STEP, z);
                boolean solid = terrain.evaluate(ctx) > 0;
                ctx.position.set(x, 1 + j * STEP, z);
                boolean body = rock.evaluate(ctx) > 0;
                cls[(j * nz + k) * nx + i] = (byte) (solid ? (body ? 1 : 2) : (body ? 3 : 0));
            }
        }
        long evalMs = (System.nanoTime() - t0) / 1_000_000;
        out.printf("evaluated %d voxels (%d x %d x %d, %d m) in %d ms%n", (long) nx * nz * ny, nx, nz, ny, STEP, evalMs);

        String dump = env("DUMP", "");
        if (!dump.isEmpty()) try (OutputStream o = new BufferedOutputStream(new FileOutputStream(dump))) { o.write(cls); }
        String cmp = env("CMP", "");
        if (!cmp.isEmpty()) compare(Files.readAllBytes(Paths.get(cmp)), cls, largeCol, nx, nz, ny);

        measure(cls, nx, nz, ny, x0, z0, largeCol);
        slices(cls, nx, nz, ny, x0, z0, cx, cz, prefix, base);
    }


    /** Finds every large island in a square of the world (columns with Arrakis_Large_Inside above LARGE_MIN, 8-connected on a 20 m grid), then measures each one. */
    static void islands(Density terrain, Density rock, Density large, double cx, double cz, double size) {
        final double thr = Double.parseDouble(env("LARGE_MIN", "8")), minHa = Double.parseDouble(env("MIN_HA", "8"));
        final int grid = 20, n = (int) (size / grid), yMax = Integer.parseInt(env("YMAX", "180")), ny = (yMax - 1) / STEP + 1;
        Density.Context ctx = new Density.Context(); ctx.position = new Vector3d(); ctx.distanceToBiomeEdge = 1000;
        double x0 = cx - size / 2, z0 = cz - size / 2;
        boolean[] on = new boolean[n * n];
        for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) { ctx.position.set(x0 + i * grid, 0, z0 + k * grid); on[k * n + i] = large.evaluate(ctx) > thr; }
        int[] lab = new int[n * n]; int labels = 0; List<int[]> boxes = new ArrayList<>();
        int[] stack = new int[n * n];
        for (int s0 = 0; s0 < n * n; s0++) { if (!on[s0] || lab[s0] != 0) continue; int id = ++labels, sp = 0; stack[sp++] = s0; lab[s0] = id; int minI = n, maxI = 0, minK = n, maxK = 0, cnt = 0;
            while (sp > 0) { int v = stack[--sp]; int i = v % n, k = v / n; cnt++; minI = Math.min(minI, i); maxI = Math.max(maxI, i); minK = Math.min(minK, k); maxK = Math.max(maxK, k);
                for (int di = -1; di <= 1; di++) for (int dk = -1; dk <= 1; dk++) { int ii = i + di, kk = k + dk; if (ii < 0 || kk < 0 || ii >= n || kk >= n) continue; int u = kk * n + ii; if (on[u] && lab[u] == 0) { lab[u] = id; stack[sp++] = u; } } }
            double ha = cnt * grid * grid / 10000.0; if (ha >= minHa) boxes.add(new int[]{minI, maxI, minK, maxK, cnt}); }
        out.printf("large-island footprints of %.0f ha or more in the %.0f m square round (%.0f, %.0f): %d%n", minHa, size, cx, cz, boxes.size());
        int idx = 0;
        for (int[] b : boxes) { idx++;
            double bx0 = x0 + (b[0] - 3) * grid, bz0 = z0 + (b[2] - 3) * grid, w = (b[1] - b[0] + 7) * grid, h = (b[3] - b[2] + 7) * grid;
            int nx = (int) (w / STEP), nz = (int) (h / STEP);
            if ((long) nx * nz * ny > 400_000_000L) { out.printf("island %d: skipped, region too big (%.0f x %.0f m)%n", idx, w, h); continue; }
            byte[] cls = new byte[nx * nz * ny]; boolean[] lc = new boolean[nx * nz];
            for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) { double x = bx0 + i * STEP, z = bz0 + k * STEP;
                ctx.position.set(x, 0, z); lc[k * nx + i] = large.evaluate(ctx) > thr;
                for (int j = 0; j < ny; j++) { ctx.position.set(x, 1 + j * STEP, z); boolean solid = terrain.evaluate(ctx) > 0; ctx.position.set(x, 1 + j * STEP, z); boolean body = rock.evaluate(ctx) > 0;
                    cls[(j * nz + k) * nx + i] = (byte) (solid ? (body ? 1 : 2) : (body ? 3 : 0)); } }
            out.printf("island %d: centre (%.0f, %.0f), footprint %.0f ha, region %.0f x %.0f m%n", idx, bx0 + w / 2, bz0 + h / 2, b[4] * grid * grid / 10000.0, w, h);
            summary(cls, nx, nz, ny, bx0, bz0, lc);
            out.flush(); }
    }

    /** One island, a few lines of numbers: caves, networks, openings, and whether the big networks can be entered. */
    static void summary(byte[] cls, int nx, int nz, int ny, double x0, double z0, boolean[] lc) {
        double vox = STEP * STEP * STEP; long body = 0, cave = 0;
        for (int j = 0; j < ny; j++) for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) { if (!lc[k * nx + i]) continue; byte c = cls[idx(i, j, k, nx, nz)]; if (c == 1 || c == 3) body++; if (c == 3) cave++; }
        int[] comp = new int[cls.length]; List<long[]> comps = new ArrayList<>(); int[] stack = new int[1 << 20]; int[][] d6 = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int st = 0; st < cls.length; st++) { if (cls[st] != 3 || comp[st] != 0) continue; int id = comps.size() + 1, sp = 0; stack[sp++] = st; comp[st] = id; long count = 0, lo = ny;
            while (sp > 0) { int v = stack[--sp]; count++; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz); lo = Math.min(lo, j);
                for (int[] e : d6) { int ii = i + e[0], kk = k + e[1], jj = j + e[2]; if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue; int u = idx(ii, jj, kk, nx, nz);
                    if (cls[u] == 3 && comp[u] == 0) { comp[u] = id; if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } } }
            comps.add(new long[]{count, lo}); }
        boolean[] open = new boolean[cls.length];
        for (int v = 0; v < cls.length; v++) { if (cls[v] != 3) continue; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz);
            for (int[] e : d6) { int ii = i + e[0], kk = k + e[1], jj = j + e[2]; if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue; if (cls[idx(ii, jj, kk, nx, nz)] == 0) { open[v] = true; break; } } }
        boolean[] seen = new boolean[cls.length]; Set<Integer> entered = new HashSet<>(); int openings = 0; int minY = 999, maxY = 0; List<String> where = new ArrayList<>();
        for (int st = 0; st < cls.length; st++) { if (!open[st] || seen[st]) continue; int sp = 0; stack[sp++] = st; seen[st] = true; long count = 0; int lo = ny, hi = 0; double sx = 0, sz = 0;
            while (sp > 0) { int v = stack[--sp]; count++; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz); lo = Math.min(lo, j); hi = Math.max(hi, j); sx += i; sz += k;
                for (int di = -1; di <= 1; di++) for (int dk = -1; dk <= 1; dk++) for (int dj = -1; dj <= 1; dj++) { int ii = i + di, kk = k + dk, jj = j + dj; if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue; int u = idx(ii, jj, kk, nx, nz);
                    if (open[u] && !seen[u]) { seen[u] = true; if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } } }
            if (count >= 6) { openings++; entered.add(comp[st]); minY = Math.min(minY, 1 + lo * STEP); maxY = Math.max(maxY, 1 + hi * STEP); if (where.size() < 3) where.add(String.format("(%.0f, %d, %.0f)", x0 + sx / count * STEP, 1 + lo * STEP, z0 + sz / count * STEP)); } }
        long[] big = comps.stream().mapToLong(c -> c[0]).sorted().toArray(); long largest = big.length == 0 ? 0 : big[big.length - 1];
        int bigN = 0, bigEntered = 0; long volEntered = 0; boolean largestEntered = false; long sealedVol = 0;
        for (int q = 0; q < comps.size(); q++) { long c = comps.get(q)[0]; if (c * vox >= 20000) { bigN++; if (entered.contains(q + 1)) bigEntered++; else sealedVol += c; } if (entered.contains(q + 1)) { volEntered += c; if (c == largest) largestEntered = true; } }
        long exposed = 0; int highest = 0;
        for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) { if (!lc[k * nx + i]) continue; int top = -1;
            for (int j = ny - 1; j >= 0; j--) { byte c = cls[idx(i, j, k, nx, nz)]; if (c == 1 || c == 3) { top = j; break; } }
            if (top >= 0 && 1 + top * STEP >= 84) { exposed++; highest = Math.max(highest, 1 + top * STEP); } }
        out.printf("  exposed rock (top above Y 84): %.1f ha, highest top Y %d%n", exposed * STEP * STEP / 10000.0, highest);
        out.printf("  rock %.1f Mm3 (large footprint), cave %.2f Mm3 = %.1f%%; %d pieces, %d of 4000 m3 or more; largest %.1f%% of cave volume, deepest Y %d%n", body * vox / 1e6, cave * vox / 1e6, 100.0 * cave / Math.max(1, body), comps.size(),
            comps.stream().filter(c -> c[0] * vox >= 4000).count(), cave == 0 ? 0 : 100.0 * largest / cave, comps.isEmpty() ? -1 : 1 + comps.stream().mapToLong(c -> c[1]).min().getAsLong() * STEP);
        out.printf("  openings (6+ voxels): %d, Y %d..%d, e.g. %s; networks of 20000 m3 or more: %d, %d with an opening, sealed volume %.0f m3; largest network has an opening: %s; volume reachable from outside %.1f%%%n", openings, openings == 0 ? 0 : minY, maxY, where,
            bigN, bigEntered, sealedVol * vox, largestEntered ? "yes" : "NO", cave == 0 ? 0 : 100.0 * volEntered / cave);
    }

    static int idx(int i, int j, int k, int nx, int nz) { return (j * nz + k) * nx + i; }

    /** Rules: sand never changed, nothing becomes solid in open air, medium and small bodies untouched. */
    static void compare(byte[] before, byte[] after, boolean[] largeCol, int nx, int nz, int ny) {
        long sandChanged = 0, airFilled = 0, rockToAirOutsideLarge = 0, rockToAir = 0, sandBefore = 0;
        for (int j = 0; j < ny; j++) for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) {
            int n = idx(i, j, k, nx, nz); int b = before[n], a = after[n];
            if (b == 2) { sandBefore++; if (a != 2) sandChanged++; }
            if (b == 0 && a != 0) airFilled++;
            if (b == 1 && a == 3) { rockToAir++; if (!largeCol[k * nx + i]) rockToAirOutsideLarge++; }
        }
        out.printf("rules: sand voxels before %d, changed %d (must be 0); open air that became solid %d (must be 0); rock carved %d, of which in columns outside the large footprint %d (must be 0)%n",
                sandBefore, sandChanged, airFilled, rockToAir, rockToAirOutsideLarge);
    }

    static void measure(byte[] cls, int nx, int nz, int ny, double x0, double z0, boolean[] largeCol) {
        long body = 0, cave = 0, minJ = ny;
        for (int n = 0; n < cls.length; n++) { if (cls[n] == 1 || cls[n] == 3) body++; if (cls[n] == 3) cave++; }
        // restrict the share to the large-island columns, where caves are allowed
        long bodyLarge = 0, caveLarge = 0;
        for (int j = 0; j < ny; j++) for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) {
            if (!largeCol[k * nx + i]) continue; byte c = cls[idx(i, j, k, nx, nz)];
            if (c == 1 || c == 3) bodyLarge++; if (c == 3) caveLarge++; }
        double vox = STEP * STEP * STEP;
        out.printf("rock body %.0f m3, cave air %.0f m3 (%.2f%% of the body); inside the large footprint: body %.0f m3, cave %.0f m3 (%.2f%%)%n",
                body * vox, cave * vox, 100.0 * cave / Math.max(1, body), bodyLarge * vox, caveLarge * vox, 100.0 * caveLarge / Math.max(1, bodyLarge));
        // components of cave air (6-connected)
        int[] comp = new int[cls.length]; List<long[]> comps = new ArrayList<>(); int[] stack = new int[1 << 20];
        for (int start = 0; start < cls.length; start++) {
            if (cls[start] != 3 || comp[start] != 0) continue;
            int id = comps.size() + 1; int sp = 0; stack[sp++] = start; comp[start] = id; long count = 0, lo = ny, hi = 0;
            while (sp > 0) {
                int v = stack[--sp]; count++; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz); lo = Math.min(lo, j); hi = Math.max(hi, j);
                int[][] d = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
                for (int[] e : d) { int ii = i + e[0], kk = k + e[1], jj = j + e[2]; if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue;
                    int u = idx(ii, jj, kk, nx, nz); if (cls[u] == 3 && comp[u] == 0) { comp[u] = id; if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } }
            }
            comps.add(new long[]{count, lo, hi});
            minJ = Math.min(minJ, lo);
        }
        long[][] sorted = comps.toArray(new long[0][]); Arrays.sort(sorted, (p, q) -> Long.compare(q[0], p[0]));
        int big = 0; for (long[] c : sorted) if (c[0] * vox >= 4000) big++; // 4000 m3 and up
        out.printf("cave networks: %d separate pieces in all, %d of 4000 m3 or more; largest holds %.1f%% of the cave volume%n", comps.size(), big, cave == 0 ? 0 : 100.0 * sorted[0][0] / cave);
        for (int q = 0; q < Math.min(8, sorted.length); q++) out.printf("  piece %d: %.0f m3, Y %d .. %d%n", q + 1, sorted[q][0] * vox, 1 + sorted[q][1] * STEP, 1 + sorted[q][2] * STEP);
        out.printf("deepest cave air: Y %d%n", cave == 0 ? -1 : 1 + minJ * STEP);
        // openings: cave air voxels that touch open air; clusters by 26-connectivity among those voxels
        boolean[] open = new boolean[cls.length]; long openVox = 0;
        for (int v = 0; v < cls.length; v++) { if (cls[v] != 3) continue; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz);
            int[][] d = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int[] e : d) { int ii = i + e[0], kk = k + e[1], jj = j + e[2]; if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue;
                if (cls[idx(ii, jj, kk, nx, nz)] == 0) { open[v] = true; openVox++; break; } } }
        boolean[] seen = new boolean[cls.length]; List<double[]> openings = new ArrayList<>();
        for (int s = 0; s < cls.length; s++) { if (!open[s] || seen[s]) continue;
            int sp = 0; stack[sp++] = s; seen[s] = true; long count = 0; double sx = 0, sz = 0, sy = 0; int lo = ny, hi = 0; int cid = comp[s];
            while (sp > 0) { int v = stack[--sp]; count++; int i = v % nx, k = (v / nx) % nz, j = v / (nx * nz); sx += i; sz += k; sy += j; lo = Math.min(lo, j); hi = Math.max(hi, j);
                for (int di = -1; di <= 1; di++) for (int dk = -1; dk <= 1; dk++) for (int dj = -1; dj <= 1; dj++) { int ii = i + di, kk = k + dk, jj = j + dj;
                    if (ii < 0 || kk < 0 || jj < 0 || ii >= nx || kk >= nz || jj >= ny) continue; int u = idx(ii, jj, kk, nx, nz);
                    if (open[u] && !seen[u]) { seen[u] = true; if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } } }
            openings.add(new double[]{count, x0 + sx / count * STEP, z0 + sz / count * STEP, 1 + lo * STEP, 1 + hi * STEP, cid}); }
        openings.sort((p, q) -> Double.compare(q[0], p[0]));
        long real = openings.stream().filter(o -> o[0] >= 6).count();
        out.printf("natural openings: %d clusters (%d with 6 or more boundary voxels); %d boundary voxels%n", openings.size(), real, openVox);
        for (int q = 0; q < Math.min(20, openings.size()); q++) { double[] o = openings.get(q); out.printf("  opening %d: %.0f voxels, near (%.0f, %.0f), Y %.0f..%.0f, network %d%n", q + 1, o[0], o[1], o[2], o[3], o[4], (int) o[5]); }
        // the tops: columns of the large footprint whose highest rock-body voxel is cave air (a hole in the surface), or has cave air within 6 m below it
        long topCols = 0, holes = 0, thin = 0;
        for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) { if (!largeCol[k * nx + i]) continue; int top = -1;
            for (int j = ny - 1; j >= 0; j--) { byte c = cls[idx(i, j, k, nx, nz)]; if (c == 1 || c == 3) { top = j; break; } }
            if (top < 0) continue; topCols++;
            if (cls[idx(i, top, k, nx, nz)] == 3) { holes++; continue; }
            for (int j = top - 1; j >= Math.max(0, top - 3); j--) if (cls[idx(i, j, k, nx, nz)] == 3) { thin++; break; } }
        out.printf("tops: %d rock columns in the large footprint; %d are holes in the surface (%.2f%%); %d more have cave air within 6 m below the surface (%.2f%%)%n", topCols, holes, 100.0 * holes / Math.max(1, topCols), thin, 100.0 * thin / Math.max(1, topCols));
        // which networks have an opening
        Set<Integer> withOpening = new HashSet<>(); for (double[] o : openings) if (o[0] >= 6) withOpening.add((int) o[5]);
        long volWith = 0; for (int id : withOpening) volWith += comps.get(id - 1)[0];
        out.printf("networks with a natural opening: %d of %d; they hold %.1f%% of the cave volume%n", withOpening.size(), comps.size(), cave == 0 ? 0 : 100.0 * volWith / cave);
    }

    static final int[] AIR = {200, 225, 245}, ROCK = {168, 108, 70}, SAND = {232, 208, 152}, CAVE = {28, 24, 26};
    static int[] color(int c) { return c == 0 ? AIR : c == 1 ? ROCK : c == 2 ? SAND : CAVE; }

    static void slices(byte[] cls, int nx, int nz, int ny, double x0, double z0, double cx, double cz, String prefix, int base) throws IOException {
        // vertical slices through the centre, along X and along Z, 2 px per voxel
        for (int dir = 0; dir < 2; dir++) {
            int w = dir == 0 ? nx : nz; BufferedImage img = new BufferedImage(w * 2, ny * 2, BufferedImage.TYPE_INT_RGB);
            int fix = (dir == 0 ? nz : nx) / 2;
            for (int j = 0; j < ny; j++) for (int t = 0; t < w; t++) {
                int c = cls[dir == 0 ? idx(t, j, fix, nx, nz) : idx(fix, j, t, nx, nz)]; int[] col = color(c);
                if (1 + j * STEP == base || 1 + j * STEP == base + 1) col = new int[]{255, 0, 0};
                int rgb = (col[0] << 16) | (col[1] << 8) | col[2];
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) img.setRGB(t * 2 + dx, (ny - 1 - j) * 2 + dy, rgb);
            }
            ImageIO.write(img, "png", new File(prefix + (dir == 0 ? "_vertical_x.png" : "_vertical_z.png")));
        }
        // horizontal slices
        for (int dy : new int[]{50, 30, 10, 0, -15, -30, -45, -60, -72}) {
            int j = (base + dy - 1) / STEP; if (j < 0 || j >= ny) continue;
            BufferedImage img = new BufferedImage(nx, nz, BufferedImage.TYPE_INT_RGB);
            for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) { int[] col = color(cls[idx(i, j, k, nx, nz)]); img.setRGB(i, k, (col[0] << 16) | (col[1] << 8) | col[2]); }
            ImageIO.write(img, "png", new File(prefix + "_y" + (dy >= 0 ? "p" : "m") + Math.abs(dy) + ".png"));
        }
    }

    static void bootFakeServer() throws Exception {
        Field parser = Options.class.getDeclaredField("PARSER"); parser.setAccessible(true);
        Field optionSet = Options.class.getDeclaredField("optionSet"); optionSet.setAccessible(true);
        optionSet.set(null, ((joptsimple.OptionParser) parser.get(null)).parse(new String[0]));
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        Object server = unsafe.allocateInstance(HytaleServer.class);
        Field bus = HytaleServer.class.getDeclaredField("eventBus");
        unsafe.putObject(server, unsafe.objectFieldOffset(bus), new EventBus(false));
        Field instance = HytaleServer.class.getDeclaredField("instance");
        unsafe.putObject(unsafe.staticFieldBase(instance), unsafe.staticFieldOffset(instance), server);
        Class<?> hg = Class.forName("com.hypixel.hytale.builtin.hytalegenerator.plugin.HytaleGenerator");
        Object plugin = unsafe.allocateInstance(hg);
        Class<?> conc = Class.forName("com.hypixel.hytale.builtin.hytalegenerator.plugin.HytaleGenerator$Concurrency");
        Object c = conc.getDeclaredConstructor(int.class, int.class, int.class).newInstance(1, 1, 1);
        Field cf = hg.getDeclaredField("concurrency"); unsafe.putObject(plugin, unsafe.objectFieldOffset(cf), c);
        Field inst = hg.getDeclaredField("INSTANCE"); unsafe.putObject(unsafe.staticFieldBase(inst), unsafe.staticFieldOffset(inst), plugin);
    }
}
