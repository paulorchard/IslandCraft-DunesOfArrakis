package com.paulorchard.islandcraft.dunesofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Spawns players on exposed rock near the origin.
 *
 * The first time a spawn point is asked for, columns are searched outwards from the
 * origin for a surface block that is rock rather than sand. The result is kept for
 * as long as the world stays loaded. If no rock is found the player is put on the
 * surface at the origin, whatever it is.
 *
 * Chunks are requested asynchronously, one square ring of chunks at a time, and the
 * search runs on the world thread over whatever has loaded, so nothing ever blocks
 * waiting for chunk generation.
 */
public class DunesOfArrakisSpawnProvider implements ISpawnProvider {

    /** How far from the origin to look, in blocks. */
    static final int SEARCH_RADIUS = 320;
    /** Once any rock has been seen, how much further to look for a level spot before settling for it. */
    static final int LEVEL_SPOT_EXTRA = 24;
    private static final String ROCK_PREFIX = "Rock_";
    /** Same load flags vanilla's FitToHeightMapSpawnProvider passes to getChunkReferenceAsync. */
    private static final int CHUNK_LOAD_FLAGS = 32;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private volatile Transform found;
    private CompletableFuture<Transform> pending;

    @Override
    public CompletableFuture<Transform> getSpawnPointAsync(World world, UUID uuid) {
        Transform done = found;
        if (done != null) {
            return CompletableFuture.completedFuture(new Transform(done));
        }
        CompletableFuture<Transform> result;
        boolean start = false;
        synchronized (this) {
            if (found != null) {
                return CompletableFuture.completedFuture(new Transform(found));
            }
            if (pending == null) {
                pending = new CompletableFuture<>();
                start = true;
            }
            result = pending;
        }
        if (start) {
            long began = System.nanoTime();
            result.whenComplete((t, e) -> {
                synchronized (this) {
                    if (t != null) {
                        found = t;
                    }
                    pending = null;
                }
                if (t != null) {
                    LOGGER.at(Level.INFO).log("Dunes of Arrakis spawn search took %d ms",
                            (System.nanoTime() - began) / 1_000_000L);
                }
            });
            try {
                new Search(world, result).loadStage(1);
            } catch (Throwable e) {
                fail(world, result, e);
            }
        }
        return result;
    }

    @Override
    public Transform[] getSpawnPoints() {
        Transform f = found;
        return new Transform[] {f != null ? new Transform(f) : new Transform(0.5, 0.0, 0.5)};
    }

    @Override
    public boolean isWithinSpawnDistance(Vector3d position, double distance) {
        Transform f = found;
        Vector3d spawn = f != null ? f.getPosition() : new Vector3d(0.5, position.y(), 0.5);
        double dx = position.x() - spawn.x();
        double dz = position.z() - spawn.z();
        return dx * dx + dz * dz < distance * distance;
    }

    /** Warn and complete with the origin surface spawn so a joining player is never failed. */
    private static void fail(World world, CompletableFuture<Transform> result, Throwable cause) {
        LOGGER.at(Level.WARNING).log("Dunes of Arrakis spawn search failed (%s); spawning on the surface at the origin", cause);
        try {
            world.execute(() -> {
                try {
                    ChunkStore store = world.getChunkStore();
                    int top = columnHeight(store, store.getChunkReference(ChunkUtil.indexChunkFromBlock(0, 0)), 0, 0);
                    result.complete(new Transform(0.5, top == HeightmapColumn.NO_HEIGHT ? 1.0 : top + 1, 0.5));
                } catch (Throwable t) {
                    result.complete(new Transform(0.5, 1.0, 0.5));
                }
            });
        } catch (Throwable t) {
            result.complete(new Transform(0.5, 1.0, 0.5));
        }
    }

    private static int columnHeight(ChunkStore store, Ref<ChunkStore> ref, int x, int z) {
        if (ref == null || !ref.isValid()) {
            return HeightmapColumn.NO_HEIGHT;
        }
        HeightmapColumn column = store.getStore().getComponentConcurrent(ref, HeightmapColumn.getComponentType());
        return column != null ? column.getHeight(x, z) : HeightmapColumn.NO_HEIGHT;
    }

    /** One spawn search: loads chunks in growing square rings and scans block rings as they become readable. */
    private static final class Search {
        private final World world;
        private final ChunkStore store;
        private final CompletableFuture<Transform> result;
        private final Long2ObjectOpenHashMap<Ref<ChunkStore>> columns = new Long2ObjectOpenHashMap<>();

        private SectionReader reader;
        private int ring = 0;
        private int anyX;
        private int anyZ;
        private int anyRing = -1;

        Search(World world, CompletableFuture<Transform> result) {
            this.world = world;
            this.store = world.getChunkStore();
            this.result = result;
        }

        /** Requests the chunks at {@code stage} chunks from the origin (all within it for stage 1), then scans. */
        void loadStage(int stage) {
            List<CompletableFuture<Void>> loads = new ArrayList<>();
            for (int cx = -stage; cx <= stage; cx++) {
                for (int cz = -stage; cz <= stage; cz++) {
                    if (stage != 1 && Math.max(Math.abs(cx), Math.abs(cz)) != stage) {
                        continue;
                    }
                    long index = ChunkUtil.indexChunk(cx, cz);
                    loads.add(store.getChunkReferenceAsync(index, CHUNK_LOAD_FLAGS).thenAccept(ref -> {
                        synchronized (columns) {
                            columns.put(index, ref);
                        }
                    }));
                }
            }
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, e) -> {
                if (e != null) {
                    fail(world, result, e);
                    return;
                }
                try {
                    world.execute(() -> scan(stage));
                } catch (Throwable t) {
                    fail(world, result, t);
                }
            });
        }

        /** World thread: scan every block ring whose columns and their neighbours are loaded. */
        private void scan(int stage) {
            try {
                if (reader == null) {
                    reader = new SectionReader(store);
                }
                // Chunks -stage..stage cover blocks -32*stage .. 32*stage+31; a ring needs one block more.
                int limit = Math.min(SEARCH_RADIUS, ChunkUtil.SIZE * stage - 1);
                Transform done = run(limit);
                if (done != null) {
                    result.complete(done);
                } else {
                    loadStage(stage + 1);
                }
            } catch (Throwable t) {
                fail(world, result, t);
            }
        }

        /** Continues the ring walk up to {@code limit}; returns the spawn once decided, else null. */
        private Transform run(int limit) {
            for (; ring <= SEARCH_RADIUS; ring++) {
                if (anyRing >= 0 && ring > anyRing + LEVEL_SPOT_EXTRA) {
                    break;
                }
                if (ring > limit) {
                    return null;
                }
                // Columns at this ring distance from the origin, walked as the edge of a square.
                for (int i = -ring; i <= ring; i++) {
                    for (int side = 0; side < 4; side++) {
                        if (ring == 0 && side > 0) {
                            break;
                        }
                        if (side >= 2 && (i == -ring || i == ring)) {
                            continue;
                        }
                        int x = side == 0 ? i : side == 1 ? i : side == 2 ? -ring : ring;
                        int z = side == 0 ? -ring : side == 1 ? ring : i;
                        int top = rockTop(x, z);
                        if (top < 0) {
                            continue;
                        }
                        if (anyRing < 0) {
                            anyRing = ring;
                            anyX = x;
                            anyZ = z;
                        }
                        if (isLevel(x, z, top)) {
                            return report(x, top, z, "level rock");
                        }
                    }
                }
            }
            if (anyRing >= 0) {
                return report(anyX, rockTop(anyX, anyZ), anyZ, "rock, not level");
            }
            int top = columnHeight(store, columns.get(ChunkUtil.indexChunkFromBlock(0, 0)), 0, 0);
            if (top == HeightmapColumn.NO_HEIGHT) {
                top = 0;
            }
            LOGGER.at(Level.WARNING).log(
                    "No exposed rock within %d blocks of the origin; spawning on the surface at the origin (Y %d)",
                    SEARCH_RADIUS, top + 1);
            return new Transform(0.5, top + 1, 0.5);
        }

        /** The height of the top block of this column if it is rock, otherwise -1. */
        private int rockTop(int x, int z) {
            Ref<ChunkStore> ref = columns.get(ChunkUtil.indexChunkFromBlock(x, z));
            int top = columnHeight(store, ref, x, z);
            if (top == HeightmapColumn.NO_HEIGHT) {
                return -1;
            }
            int id = reader.getBlock(x, top, z);
            if (id == 0) {
                return -1;
            }
            BlockType type = BlockType.getAssetMap().getAsset(id);
            return type != null && type.getId() != null && type.getId().startsWith(ROCK_PREFIX) ? top : -1;
        }

        /** True when the four neighbouring columns are rock too, each within one block of this height. */
        private boolean isLevel(int x, int z, int top) {
            int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] n : neighbours) {
                int other = rockTop(x + n[0], z + n[1]);
                if (other < 0 || Math.abs(other - top) > 1) {
                    return false;
                }
            }
            return true;
        }

        private static Transform report(int x, int top, int z, String kind) {
            LOGGER.at(Level.INFO).log("Dunes of Arrakis spawn set on %s at (%d, %d, %d)", kind, x, top + 1, z);
            return new Transform(x + 0.5, top + 1, z + 0.5);
        }
    }
}
