import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Test harness, not part of the mod. Generates a block of the default world with the world's own generator, reads every block
 * with the engine's section reader, writes them to a file and stops the server.
 *
 * System properties: scan.minX, scan.minZ (block coordinates, multiples of 32), scan.nx, scan.nz (blocks, multiples of 32),
 * scan.yMax (default 200), scan.out (dump file). Dump format, all big-endian:
 *   "SCN1", int minX, int minZ, int nx, int nz, int ny, int paletteSize, paletteSize x (UTF name), then per column (z-major, then x)
 *   ny unsigned shorts (palette index, Y 0 .. ny-1). A "done" marker file is written next to the dump.
 */
public class WorldScanPlugin extends JavaPlugin {

    public WorldScanPlugin(JavaPluginInit init) { super(init); }

    @Override
    protected void start() {
        Thread t = new Thread(this::run, "world-scan");
        t.setDaemon(true);
        t.start();
    }

    private void log(String s) { getLogger().at(Level.INFO).log("[WorldScan] %s", s); }

    private void run() {
        try {
            int minX = Integer.getInteger("scan.minX"), minZ = Integer.getInteger("scan.minZ"), nx = Integer.getInteger("scan.nx"), nz = Integer.getInteger("scan.nz"), ny = Integer.getInteger("scan.yMax", 200) + 1;
            Path out = Paths.get(System.getProperty("scan.out", "scan.bin"));
            World world = null;
            for (int i = 0; i < 600 && world == null; i++) { world = Universe.get() == null ? null : Universe.get().getDefaultWorld(); if (world == null) Thread.sleep(500); }
            if (world == null) throw new IllegalStateException("no default world");
            Thread.sleep(2000);
            final World w = world;
            if (System.getProperty("scan.spawn") != null) {
                try {
                    long ts = System.nanoTime();
                    var tr = w.getWorldConfig().getSpawnProvider().getSpawnPointAsync(w, UUID.randomUUID()).get();
                    long ms = (System.nanoTime() - ts) / 1_000_000;
                    var p = tr.getPosition();
                    int sx = (int) Math.floor(p.x()), sy = (int) Math.floor(p.y()), sz = (int) Math.floor(p.z());
                    CompletableFuture<String> res = new CompletableFuture<>();
                    w.execute(() -> {
                        try {
                            SectionReader r = new SectionReader(w.getChunkStore()); StringBuilder sb = new StringBuilder();
                            for (int dy = -3; dy <= 2; dy++) { BlockType bt = BlockType.getAssetMap().getAsset(r.getBlock(sx, sy + dy, sz)); sb.append(String.format(" y%+d=%s", dy, bt == null ? "?" : bt.getId())); }
                            sb.append(" | under neighbours:");
                            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { BlockType bt = BlockType.getAssetMap().getAsset(r.getBlock(sx + d[0], sy - 1, sz + d[1])); sb.append(' ').append(bt == null ? "?" : bt.getId()); }
                            res.complete(sb.toString());
                        } catch (Throwable e) { res.completeExceptionally(e); }
                    });
                    log(String.format("SPAWN (%.1f, %.1f, %.1f) found in %d ms;%s", p.x(), p.y(), p.z(), ms, res.get()));
                } catch (Throwable e) { log("SPAWN failed: " + e); }
            }
            ChunkStore store = w.getChunkStore();
            int cx0 = Math.floorDiv(minX, 32), cz0 = Math.floorDiv(minZ, 32), cxs = nx / 32, czs = nz / 32;
            log("loading " + (cxs * czs) + " chunk columns");
            long t0 = System.nanoTime();
            List<long[]> order = new ArrayList<>();
            for (int cz = 0; cz < czs; cz++) for (int cx = 0; cx < cxs; cx++) order.add(new long[]{cx0 + cx, cz0 + cz});
            int batch = Integer.getInteger("scan.batch", 32), failed = 0; long loadNs = 0, readNs = 0;
            Map<Integer, Integer> paletteIndex = new HashMap<>(); List<String> palette = new ArrayList<>();
            palette.add("Empty"); paletteIndex.put(0, 0);
            try (FileChannel ch = FileChannel.open(out, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                long dataStart = 1L << 20; // header is written last, in the first MiB
                for (int s0 = 0; s0 < order.size(); s0 += batch) {
                    List<long[]> part = order.subList(s0, Math.min(order.size(), s0 + batch));
                    long tl = System.nanoTime();
                    List<CompletableFuture<?>> fs = new ArrayList<>();
                    for (long[] cc : part) fs.add(store.getChunkReferenceAsync(com.hypixel.hytale.math.util.ChunkUtil.indexChunk((int) cc[0], (int) cc[1]), 32));
                    try { CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).get(); } catch (Exception e) { failed++; log("batch failed: " + e); }
                    loadNs += System.nanoTime() - tl;
                    long tr = System.nanoTime();
                    for (long[] cc : part) {
                        final int cxx = (int) cc[0], czz = (int) cc[1];
                        CompletableFuture<short[]> col = new CompletableFuture<>();
                        w.execute(() -> {
                            try {
                                SectionReader reader = new SectionReader(store); SectionReader.SectionCursor cur = reader.sectionCursor();
                                short[] data = new short[32 * 32 * ny];
                                for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) for (int y = 0; y < ny; y++) {
                                    int id = cur.moveTo(cxx * 32 + x, y, czz * 32 + z) ? cur.block() : 0;
                                    Integer p = paletteIndex.get(id);
                                    if (p == null) { BlockType bt = BlockType.getAssetMap().getAsset(id); p = palette.size(); palette.add(bt == null ? "Unknown_" + id : bt.getId()); paletteIndex.put(id, p); }
                                    data[(z * 32 + x) * ny + y] = (short) (int) p;
                                }
                                col.complete(data);
                            } catch (Throwable e) { col.completeExceptionally(e); }
                        });
                        short[] data = col.get();
                        ByteBuffer bb = ByteBuffer.allocate(ny * 2);
                        for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                            bb.clear(); for (int y = 0; y < ny; y++) bb.putShort(data[(z * 32 + x) * ny + y]); bb.flip();
                            long col2 = (long) ((czz - cz0) * 32 + z) * nx + ((cxx - cx0) * 32 + x);
                            ch.write(bb, dataStart + col2 * ny * 2L);
                        }
                    }
                    readNs += System.nanoTime() - tr;
                    if ((s0 / batch) % 5 == 0) log("done " + (s0 + part.size()) + " / " + order.size());
                }
                long loadMs = loadNs / 1_000_000;
                log(String.format("chunk columns generated: %d in %d ms (%.1f ms per column), blocks read in %d ms, failed batches %d, palette %d", order.size(), loadMs, (double) loadMs / order.size(), readNs / 1_000_000, failed, palette.size()));
                ByteArrayOutputStream hb = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(hb);
                d.writeBytes("SCN1"); d.writeInt(minX); d.writeInt(minZ); d.writeInt(nx); d.writeInt(nz); d.writeInt(ny); d.writeInt(palette.size()); for (String s : palette) d.writeUTF(s);
                if (hb.size() > dataStart) throw new IllegalStateException("palette too large");
                ch.write(ByteBuffer.wrap(hb.toByteArray()), 0);
            }
            long loadMs = loadNs / 1_000_000;
            Files.writeString(Paths.get(out + ".done"), "load_ms=" + loadMs + " columns=" + order.size() + "\n");
            log("done");
        } catch (Throwable e) {
            getLogger().at(Level.SEVERE).log("[WorldScan] failed: %s", e);
            e.printStackTrace();
            try { Files.writeString(Paths.get(System.getProperty("scan.out", "scan.bin") + ".done"), "FAILED " + e + "\n"); } catch (IOException ignored) { }
        }
    }
}
