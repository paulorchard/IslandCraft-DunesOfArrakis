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
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Renders a top-down map of the Arrakis terrain using the game's own generator classes, for a given world seed.
 * The server jar must be on the classpath:
 *   java -cp HytaleServer.jar RealPreview.java <packRoot> <seed> <out.png> [centreX centreZ sizeMetres metresPerPixel]
 * packRoot is the folder containing Server/HytaleGenerator (src/main/resources).
 */
public class RealPreview {
    static PrintStream log;

    public static void main(String[] a) throws Exception {
        Path packRoot = Paths.get(a[0]);
        long seed = Long.parseLong(a[1]);
        String out = a[2];
        double cx = a.length > 3 ? Double.parseDouble(a[3]) : 0, cz = a.length > 4 ? Double.parseDouble(a[4]) : 0;
        double size = a.length > 5 ? Double.parseDouble(a[5]) : 2400, res = a.length > 6 ? Double.parseDouble(a[6]) : 3;
        // The server's logger takes over stdout/stderr, so write our own log file next to the image.
        log = new PrintStream(new FileOutputStream(out + ".log"), true);
        try {
            run(packRoot, seed, out, cx, cz, size, res);
        } catch (Throwable t) {
            t.printStackTrace(log);
            Runtime.getRuntime().halt(1);
        }
        Runtime.getRuntime().halt(0);
    }

    static void run(Path packRoot, long seed, String out, double cx, double cz, double size, double res) throws Exception {
        bootFakeServer();
        Class.forName("com.hypixel.hytale.builtin.hytalegenerator.assets.AssetManager");
        log.println("asset stores registered: " + AssetRegistry.getStoreMap().size());

        // Load every generator asset type that has files in the pack.
        Path genRoot = packRoot.resolve("Server");
        for (AssetStore<?, ?, ?> store : List.copyOf(AssetRegistry.getStoreMap().values())) {
            Path dir = genRoot.resolve(store.getPath());
            if (!Files.isDirectory(dir)) continue;
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) { files = s.filter(p -> p.toString().endsWith(".json")).toList(); }
            if (files.isEmpty()) continue;
            var r = store.loadAssetsFromPaths("IslandCraft:IslandCraft - Dunes of Arrakis", files);
            log.println("loaded " + store.getPath() + ": " + r.getLoadedAssets().keySet() + " failed=" + r.getFailedToLoadPaths());
        }

        WorldStructureAsset wsAsset = AssetRegistry.getAssetStore(WorldStructureAsset.class).getAssetMap().getAsset(env("WS", "Dunes_of_Arrakis"));
        if (wsAsset == null) throw new IllegalStateException("world structure " + env("WS", "Dunes_of_Arrakis") + " not loaded");
        // Same steps as BasicWorldStructureAsset.build and BiomeAsset.build, minus materials (block types are not loaded here).
        SeedBox seedBox = new SeedBox((int) seed);
        WorldStructureAsset.Argument arg = new WorldStructureAsset.Argument(null, seedBox, WorkerIndexer.Id.MAIN, new ThreadBridge());
        ReferenceBundle bundle = new ReferenceBundle();
        Field fw = wsAsset.getClass().getDeclaredField("frameworkAssets"); fw.setAccessible(true);
        for (FrameworkAsset fa : (FrameworkAsset[]) fw.get(wsAsset)) fa.build(arg, bundle);
        BiomeAsset biomeAsset = BiomeAsset.getAssetStore().getAssetMap().getAsset(env("BIOME", "Arrakis_Terrain"));
        if (biomeAsset == null) throw new IllegalStateException("biome " + env("BIOME", "Arrakis_Terrain") + " not loaded");
        Field ta = BiomeAsset.class.getDeclaredField("terrainAsset"); ta.setAccessible(true);
        Density terrain = ((TerrainAsset) ta.get(biomeAsset)).buildDensity(arg.parentSeed, bundle, arg.workerId, arg.threadBridge);
        DensityAsset.Exported rockExport = DensityAsset.getExportedAsset(env("ROCK", "Arrakis_Rock"));
        Density rock = rockExport == null ? null : rockExport.threadInstances.get(WorkerIndexer.Id.MAIN);
        log.println("biome built; rock export " + (rock != null ? "found" : "NOT found (rock will not be coloured)"));

        if (System.getenv("FLOAT") != null) { floatReport(rock, cx, cz, size, Integer.parseInt(env("SAND_Y", "100"))); return; }

        if (System.getenv("SEAMS") != null) { seams(rock, System.getenv("SEAMS")); return; }
        if (System.getenv("COARSE") != null) { coarse(terrain, rock, cx, cz, size, res, out); return; }

        int w = (int) Math.round(size / res);
        float[] height = new float[w * w];
        boolean[] isRock = new boolean[w * w];
        Density.Context ctx = new Density.Context();
        ctx.position = new Vector3d();
        ctx.distanceToBiomeEdge = 1000; // previewing one biome on its own: treat every point as deep inside it
        boolean allRock = System.getenv("BIOME") != null;
        long t0 = System.nanoTime();
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                double x = cx - size / 2 + i * res, z = cz - size / 2 + j * res;
                int top = 0;
                for (int y = 260; y > 0; y -= 4) { // coarse scan down, then refine
                    ctx.position.set(x, y, z);
                    if (terrain.evaluate(ctx) > 0) {
                        top = y;
                        for (int yy = y + 3; yy > y; yy--) { ctx.position.set(x, yy, z); if (terrain.evaluate(ctx) > 0) { top = yy; break; } }
                        break;
                    }
                }
                height[j * w + i] = top;
                if (rock != null) { ctx.position.set(x, top, z); isRock[j * w + i] = rock.evaluate(ctx) > 0; } else isRock[j * w + i] = allRock;
            }
        }
        if (rock != null) { // underground check: rock area at two depths against rock showing at the surface
            long surface = 0, y40 = 0, y10 = 0;
            for (int j = 0; j < w; j++) for (int i = 0; i < w; i++) {
                double x = cx - size / 2 + i * res, z = cz - size / 2 + j * res;
                if (isRock[j * w + i]) surface++;
                ctx.position.set(x, 40, z); if (rock.evaluate(ctx) > 0) y40++;
                ctx.position.set(x, 10, z); if (rock.evaluate(ctx) > 0) y10++;
            }
            double ha = res * res / 10000.0;
            log.printf("rock showing above the sand %.1f ha; at Y=40 %.1f ha (%.2f x); at Y=10 %.1f ha (%.2f x)%n", surface * ha, y40 * ha, (double) y40 / Math.max(1, surface), y10 * ha, (double) y10 / Math.max(1, surface));
        }
        log.println("evaluated " + (w * w) + " columns in " + (System.nanoTime() - t0) / 1_000_000 + " ms");

        if (System.getenv("WALK") != null && res == 1.0) walkReport(height, isRock, w, cx, cz, size);

        if (rock != null) { long wrong = 0, sandCols = 0; for (int jj = 0; jj < w; jj++) for (int ii = 0; ii < w; ii++) { if (isRock[jj * w + ii]) continue; sandCols++;
              ctx.position.set(cx - size / 2 + ii * res, height[jj * w + ii], cz - size / 2 + jj * res); if (rock.evaluate(ctx) >= 0) wrong++; }
          log.printf("materials: %d of %d sand-surface columns would be painted as rock (rock density >= 0 at the surface block)%n", wrong, sandCols); }
        { float maxTop = 0; long rc = 0; double sum = 0; for (int k = 0; k < w * w; k++) if (isRock[k]) { rc++; sum += height[k]; maxTop = Math.max(maxTop, height[k]); }
          log.printf("heights: %d rock columns, highest top block Y %.0f, average rock top Y %.1f%n", rc, maxTop, rc == 0 ? 0 : sum / rc); }
        BufferedImage img = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        long rockPx = 0;
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                float h = at(height, w, i, j);
                double dx = (at(height, w, i + 1, j) - at(height, w, i - 1, j)) / (2 * res), dz = (at(height, w, i, j + 1) - at(height, w, i, j - 1)) / (2 * res);
                boolean r = isRock[j * w + i];
                double k = r ? 0.9 : 0.35;
                double shade = Math.max(0.3, Math.min(1.25, (1 + k * dx + k * dz) / Math.sqrt(1 + dx * dx + dz * dz)));
                double[] col;
                if (r) { rockPx++; double t = Math.min(1, Math.max(0, (h - 80) / 160.0)); col = new double[]{150 + 60 * t, 95 + 55 * t, 60 + 45 * t}; }
                else col = new double[]{222, 190, 128};
                int rgb = 0;
                for (double c : col) rgb = (rgb << 8) | (int) Math.max(0, Math.min(255, c * shade));
                img.setRGB(i, j, rgb);
            }
        }
        ImageIO.write(img, "png", new File(out));
        log.printf("wrote %s: %dx%d px, %.1f m/px, rock %.1f%% of view%n", out, w, w, res, 100.0 * rockPx / (w * w));
    }

    /**
     * Seam statistics. For every pair of overlapping ellipses of different regions: samples a grid (SEAM_RES m, default 25) over the overlap of the
     * two nominal ellipses; a point is on the join when Arrakis_Continent_Join (best minus second-best region field) is under JOIN_MAX (default 60)
     * and the second best is more than 100 m inside its shore. For those points the rock top is found as in the coarse render and classed as
     * sand (no rock above Base + 4), low rock (below Base + 60) or full-height rock. Prints one line per pair.
     */
    static void seams(Density rock, String ellipseFile) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(ellipseFile)));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":\\s*(\\d+),\\s*\"x\":\\s*(-?\\d+),\\s*\"z\":\\s*(-?\\d+),\\s*\"halfEW\":\\s*(\\d+),\\s*\"halfNS\":\\s*(\\d+),\\s*\"class\":\\s*\"([A-D])\",\\s*\"region\":\\s*(\\d+)").matcher(json);
        java.util.List<double[]> el = new java.util.ArrayList<>();
        while (m.find()) el.add(new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5)), Double.parseDouble(m.group(7))});
        double res = Double.parseDouble(env("SEAM_RES", "25")), joinMax = Double.parseDouble(env("JOIN_MAX", "60"));
        Density join = DensityAsset.getExportedAsset("Arrakis_Continent_Join").threadInstances.get(WorkerIndexer.Id.MAIN);
        Density.Context ctx = new Density.Context();
        ctx.position = new Vector3d();
        ctx.distanceToBiomeEdge = 1000;
        long tot = 0, totSand = 0, totLow = 0, totFull = 0;
        log.println("pair | join points | sand % | low rock % | full-height %");
        for (int a = 0; a < el.size(); a++) for (int b = a + 1; b < el.size(); b++) {
            double[] A = el.get(a), B = el.get(b);
            if (A[5] == B[5]) continue;
            double x0 = Math.max(A[1] - A[3], B[1] - B[3]), x1 = Math.min(A[1] + A[3], B[1] + B[3]), z0 = Math.max(A[2] - A[4], B[2] - B[4]), z1 = Math.min(A[2] + A[4], B[2] + B[4]);
            if (x0 >= x1 || z0 >= z1) continue;
            long n = 0, sand = 0, low = 0, full = 0, lens = 0;
            for (double z = z0; z < z1; z += res) for (double x = x0; x < x1; x += res) {
                double ua = Math.pow((x - A[1]) / A[3], 2) + Math.pow((z - A[2]) / A[4], 2), ub = Math.pow((x - B[1]) / B[3], 2) + Math.pow((z - B[2]) / B[4], 2);
                if (ua >= 1 || ub >= 1) continue;
                lens++;
                ctx.position.set(x, 100, z);
                if (join.evaluate(ctx) > joinMax) continue;
                float h = 0;
                for (int y = 260; y > 70; y -= 8) { ctx.position.set(x, y, z); if (rock.evaluate(ctx) > 0) { h = y; break; } }
                n++;
                if (h < 84) sand++; else if (h < 140) low++; else full++;
            }
            if (lens == 0) continue;
            log.printf("%02d-%02d (regions %d,%d) | %d (lens %d) | %.0f | %.0f | %.0f%n", (int) A[0], (int) B[0], (int) A[5], (int) B[5], n, lens,
                n == 0 ? 0 : 100.0 * sand / n, n == 0 ? 0 : 100.0 * low / n, n == 0 ? 0 : 100.0 * full / n);
            tot += n; totSand += sand; totLow += low; totFull += full;
        }
        log.printf("all pairs: %d points; sand %.0f%%, low rock %.0f%%, full-height %.0f%%%n", tot, 100.0 * totSand / tot, 100.0 * totLow / tot, 100.0 * totFull / tot);
    }

    /**
     * Whole-world render. Per column: the rock density is read from Y 260 down in steps of 8 until it is positive (the top of the rock, to
     * within 8 blocks); a column counts as rock when that top is at least Base + 4. Colour is the height. Env: ELLIPSES = path of the
     * continent ellipse json (outlines drawn in red), PICK = name of a density export to colour by instead (value -1..1 mapped to a hue),
     * and the usual output log. Prints the rock share.
     */
    static void coarse(Density terrain, Density rock, double cx, double cz, double size, double res, String out) throws Exception {
        int w = (int) Math.round(size / res);
        float[] top = new float[w * w];
        Density pick = null;
        String pickName = System.getenv("PICK");
        if (pickName != null && !pickName.isEmpty()) {
            DensityAsset.Exported e = DensityAsset.getExportedAsset(pickName);
            pick = e == null ? null : e.threadInstances.get(WorkerIndexer.Id.MAIN);
        }
        float[] pv = pick == null ? null : new float[w * w];
        Density.Context ctx = new Density.Context();
        ctx.position = new Vector3d();
        ctx.distanceToBiomeEdge = 1000;
        long t0 = System.nanoTime(), rockCols = 0;
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                double x = cx - size / 2 + i * res, z = cz - size / 2 + j * res;
                float h = 0;
                for (int y = 260; y > 70; y -= 8) {
                    ctx.position.set(x, y, z);
                    if (rock.evaluate(ctx) > 0) { h = y; break; }
                }
                top[j * w + i] = h;
                if (h >= 84) rockCols++;
                if (pv != null) { ctx.position.set(x, 100, z); pv[j * w + i] = (float) pick.evaluate(ctx); }
            }
            if (j % 50 == 0) log.printf("row %d of %d, %d ms%n", j, w, (System.nanoTime() - t0) / 1_000_000);
        }
        log.printf("coarse: %d x %d columns of %.0f m in %d ms; rock %.2f%% of the square%n", w, w, res, (System.nanoTime() - t0) / 1_000_000, 100.0 * rockCols / ((double) w * w));
        String valDump = System.getenv("VALDUMP");
        if (pv != null && valDump != null && !valDump.isEmpty()) { // the PICK density's value at every column, as little-endian floats
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(w * w * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int k = 0; k < w * w; k++) bb.putFloat(pv[k]);
            Files.write(Paths.get(valDump), bb.array());
        }
        String dump = System.getenv("DUMP");
        if (dump != null && !dump.isEmpty()) { // rock top / 2 as one unsigned byte per column, row by row from the north-west corner (stats scripts read this)
            byte[] bytes = new byte[w * w];
            for (int k = 0; k < w * w; k++) bytes[k] = (byte) Math.min(255, Math.round(top[k] / 2));
            Files.write(Paths.get(dump), bytes);
        }
        BufferedImage img = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < w; j++) for (int i = 0; i < w; i++) {
            float h = top[j * w + i];
            int rgb;
            if (h >= 84) {
                if (pv != null) { java.awt.Color col = java.awt.Color.getHSBColor((float) ((pv[j * w + i] + 1) / 2.0 * 0.85), 0.75f, 0.35f + 0.65f * Math.min(1f, (h - 84) / 140f)); rgb = col.getRGB(); }
                else { double t = Math.min(1, (h - 84) / 160.0); rgb = ((int) (150 + 60 * t) << 16) | ((int) (95 + 55 * t) << 8) | (int) (60 + 45 * t); }
            } else rgb = (222 << 16) | (190 << 8) | 128;
            img.setRGB(i, j, rgb);
        }
        String ell = System.getenv("ELLIPSES");
        if (ell != null && !ell.isEmpty()) {
            String json = new String(Files.readAllBytes(Paths.get(ell)));
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":\\s*(\\d+),\\s*\"x\":\\s*(-?\\d+),\\s*\"z\":\\s*(-?\\d+),\\s*\"halfEW\":\\s*(\\d+),\\s*\"halfNS\":\\s*(\\d+)(?:,\\s*\"angle\":\\s*(-?[\\d.]+))?").matcher(json);
            java.awt.Graphics2D g = img.createGraphics();
            boolean labels = System.getenv("NOLABELS") == null;
            g.setColor(new java.awt.Color(230, 20, 20));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(10, w / 70)));
            while (m.find()) {
                double ex = Double.parseDouble(m.group(2)), ez = Double.parseDouble(m.group(3)), hx = Double.parseDouble(m.group(4)), hz = Double.parseDouble(m.group(5));
                double th = m.group(6) == null ? 0 : Math.toRadians(Double.parseDouble(m.group(6))), ct = Math.cos(th), st = Math.sin(th);
                int px = -1, pz = -1;
                for (int k = 0; k <= 120; k++) {
                    double a = k / 120.0 * Math.PI * 2, lx = hx * Math.cos(a), lz = hz * Math.sin(a);
                    int qx = (int) ((ex + lx * ct - lz * st - (cx - size / 2)) / res), qz = (int) ((ez + lx * st + lz * ct - (cz - size / 2)) / res);
                    if (k > 0) g.drawLine(px, pz, qx, qz);
                    px = qx; pz = qz;
                }
                if (labels) g.drawString(m.group(1), (int) ((ex - (cx - size / 2)) / res) - 6, (int) ((ez - (cz - size / 2)) / res) + 4);
            }
            // the origin
            g.setColor(java.awt.Color.BLACK);
            int ox = (int) ((0 - (cx - size / 2)) / res), oz = (int) ((0 - (cz - size / 2)) / res);
            g.drawLine(ox - 8, oz, ox + 8, oz); g.drawLine(ox, oz - 8, ox, oz + 8);
            g.dispose();
        }
        ImageIO.write(img, "png", new File(out));
        log.println("wrote " + out);
    }

    /** Flood fill from the origin over 1 m columns, moving only where the height changes by at most 1 block. */
    static void walkReport(float[] height, boolean[] isRock, int w, double cx, double cz, double size) {
        int oi = (int) Math.round(-(cx - size / 2)), oj = (int) Math.round(-(cz - size / 2));
        if (oi < 0 || oj < 0 || oi >= w || oj >= w) { log.println("walk: origin not in view"); return; }
        boolean[] seen = new boolean[w * w];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{oi, oj}); seen[oj * w + oi] = true;
        boolean[] sector = new boolean[8];
        int rockReached = 0, padCells = 0; float padMin = 1e9f, padMax = -1e9f, maxRock = 0; int maxAtI = 0, maxAtJ = 0;
        for (int j = 0; j < w; j++) for (int i = 0; i < w; i++) {
            double d = Math.hypot(i - oi, j - oj);
            if (d <= 15) { padCells++; padMin = Math.min(padMin, height[j * w + i]); padMax = Math.max(padMax, height[j * w + i]); }
            if (d <= 130 && isRock[j * w + i] && height[j * w + i] > maxRock) { maxRock = height[j * w + i]; maxAtI = i - oi; maxAtJ = j - oj; }
        }
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll(); int k = p[1] * w + p[0];
            if (isRock[k]) rockReached++;
            else if (Math.hypot(p[0] - oi, p[1] - oj) > 40) { // reached sand: note the compass sector, do not walk on across the sand
                double ang = Math.toDegrees(Math.atan2(p[0] - oi, -(p[1] - oj))); if (ang < 0) ang += 360;
                sector[(int) Math.floor(((ang + 22.5) % 360) / 45)] = true; continue;
            }
            for (int[] n : nb) { int ni = p[0] + n[0], nj = p[1] + n[1]; if (ni < 0 || nj < 0 || ni >= w || nj >= w) continue; int nk = nj * w + ni;
                if (!seen[nk] && Math.abs(height[nk] - height[k]) <= 1) { seen[nk] = true; q.add(new int[]{ni, nj}); } }
        }
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"}; StringBuilder sb = new StringBuilder();
        for (int s = 0; s < 8; s++) if (sector[s]) sb.append(names[s]).append(' ');
        log.printf("walk: pad (15 m) top block Y %.0f..%.0f; walkable rock from the pad %d m2; sand reached without a step over 1 block towards: %s%n", padMin, padMax, rockReached, sb.length() == 0 ? "NONE" : sb.toString().trim());
        log.printf("walk: highest rock within 130 m of spawn: top block Y %.0f, %d m east and %d m south of the origin%n", maxRock, maxAtI, maxAtJ);
    }

    /** Finds rock that is not connected to the ground: 2 m voxels from just below sand level upwards, flood fill from the lowest layer. */
    static void floatReport(Density rock, double cx, double cz, double size, int sandY) {
        final int step = 2, n = (int) (size / step), y0 = sandY - 4, ny = (264 - y0) / step;
        boolean[] solid = new boolean[n * n * ny], grounded = new boolean[n * n * ny];
        Density.Context ctx = new Density.Context(); ctx.position = new Vector3d(); ctx.distanceToBiomeEdge = 1000;
        long total = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) for (int k = 0; k < ny; k++) {
            ctx.position.set(cx - size / 2 + i * step, y0 + k * step, cz - size / 2 + j * step);
            if (rock.evaluate(ctx) > 0) { solid[(k * n + j) * n + i] = true; total++; }
        }
        int[] stack = new int[1 << 20]; int sp = 0;
        for (int s = 0; s < n * n; s++) if (solid[s]) { grounded[s] = true; if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2); stack[sp++] = s; }
        while (sp > 0) { int v = stack[--sp]; int i = v % n, jj = (v / n) % n, k = v / (n * n);
            int[] nb = {i > 0 ? v - 1 : -1, i < n - 1 ? v + 1 : -1, jj > 0 ? v - n : -1, jj < n - 1 ? v + n : -1, k > 0 ? v - n * n : -1, k < ny - 1 ? v + n * n : -1};
            for (int u : nb) if (u >= 0 && solid[u] && !grounded[u]) { grounded[u] = true; if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } }
        long floating = 0; int pieces = 0, biggest = 0; boolean[] seen = new boolean[solid.length];
        for (int s = 0; s < solid.length; s++) { if (!solid[s] || grounded[s]) continue; floating++; if (seen[s]) continue;
            int count = 0; sp = 0; stack[sp++] = s; seen[s] = true;
            while (sp > 0) { int v = stack[--sp]; count++; int i = v % n, jj = (v / n) % n, k = v / (n * n);
                int[] nb = {i > 0 ? v - 1 : -1, i < n - 1 ? v + 1 : -1, jj > 0 ? v - n : -1, jj < n - 1 ? v + n : -1, k > 0 ? v - n * n : -1, k < ny - 1 ? v + n * n : -1};
                for (int u : nb) if (u >= 0 && solid[u] && !grounded[u] && !seen[u]) { seen[u] = true; if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2); stack[sp++] = u; } }
            pieces++; biggest = Math.max(biggest, count); }
        log.printf("floating: %d separate pieces, %d m3 in all (%.3f%% of rock above sand level), biggest %d m3%n", pieces, floating * 8, 100.0 * floating / Math.max(1, total), biggest * 8);
    }

    static String env(String name, String fallback) { String v = System.getenv(name); return v == null || v.isEmpty() ? fallback : v; }

    static float at(float[] h, int w, int i, int j) { return h[Math.max(0, Math.min(w - 1, j)) * w + Math.max(0, Math.min(w - 1, i))]; }

    /** The asset stores expect a running server; give them parsed options and a server object with an event bus. */
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
        // 0.7.0: density builds ask the HytaleGenerator plugin for its concurrency (worker count); give it one worker per level.
        Class<?> hg = Class.forName("com.hypixel.hytale.builtin.hytalegenerator.plugin.HytaleGenerator");
        Object plugin = unsafe.allocateInstance(hg);
        Class<?> conc = Class.forName("com.hypixel.hytale.builtin.hytalegenerator.plugin.HytaleGenerator$Concurrency");
        Object c = conc.getDeclaredConstructor(int.class, int.class, int.class).newInstance(1, 1, 1);
        Field cf = hg.getDeclaredField("concurrency"); unsafe.putObject(plugin, unsafe.objectFieldOffset(cf), c);
        Field inst = hg.getDeclaredField("INSTANCE"); unsafe.putObject(unsafe.staticFieldBase(inst), unsafe.staticFieldOffset(inst), plugin);
        log.println("fake server ready");
    }
}
