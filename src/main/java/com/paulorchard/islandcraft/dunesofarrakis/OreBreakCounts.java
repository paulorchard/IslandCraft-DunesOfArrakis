package com.paulorchard.islandcraft.dunesofarrakis;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.Map;

/**
 * Reads and writes the free-break counts, which live in an {@link OreBreakSection} on the chunk section that holds the block. Every method
 * runs on the world's thread. A count is only ever read back for the block it was written for; a mismatch is treated as no count and
 * the entry is dropped, so a block that was replaced can never pass its count on to what replaces it.
 */
final class OreBreakCounts {

    private static ComponentType<ChunkStore, OreBreakSection> type;

    private OreBreakCounts() {
    }

    static void setType(ComponentType<ChunkStore, OreBreakSection> componentType) {
        type = componentType;
    }

    static ComponentType<ChunkStore, OreBreakSection> type() {
        return type;
    }

    static Ref<ChunkStore> section(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** The id of the block at this position, or null when the section is not loaded. */
    static String blockAt(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        return ref == null ? null : blockAt(world.getChunkStore().getStore(), ref, ChunkUtil.indexBlock(x, y, z));
    }

    private static String blockAt(Store<ChunkStore> store, Ref<ChunkStore> section, int index) {
        BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return null;
        }
        BlockType block = BlockType.getAssetMap().getAsset(blocks.get(ChunkUtil.xFromIndex(index), ChunkUtil.yFromIndex(index), ChunkUtil.zFromIndex(index)));
        return block == null ? null : block.getId();
    }

    /** Free breaks this block has had so far, 0 when it has none (or when the entry was for another block). */
    static int get(World world, int x, int y, int z, String block) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (ref == null) {
            return 0;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        OreBreakSection counts = store.getComponent(ref, type);
        if (counts == null) {
            return 0;
        }
        int index = ChunkUtil.indexBlock(x, y, z);
        OreBreakSection.Entry entry = counts.get(index);
        if (entry == null) {
            return 0;
        }
        if (!entry.block().equals(block)) {
            counts.remove(index);
            markSave(store, ref);
            return 0;
        }
        return entry.count();
    }

    static void set(World world, int x, int y, int z, String block, int count) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (ref == null) {
            return;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        OreBreakSection counts = store.getComponent(ref, type);
        if (counts == null) {
            counts = new OreBreakSection();
            store.putComponent(ref, type, counts);
        }
        counts.put(ChunkUtil.indexBlock(x, y, z), block, count);
        markSave(store, ref);
    }

    /** Drops the entry for this position, if there is one. */
    static boolean forget(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (ref == null) {
            return false;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        OreBreakSection counts = store.getComponent(ref, type);
        if (counts == null || !counts.remove(ChunkUtil.indexBlock(x, y, z))) {
            return false;
        }
        markSave(store, ref);
        return true;
    }

    /**
     * Forgets the entry once the block is no longer the one it was for. Called after a break that may or may not go through (another
     * system can still cancel it): the entry only goes when the block is really gone.
     */
    static void forgetIfGone(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (ref == null) {
            return;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        OreBreakSection counts = store.getComponent(ref, type);
        if (counts == null) {
            return;
        }
        int index = ChunkUtil.indexBlock(x, y, z);
        OreBreakSection.Entry entry = counts.get(index);
        if (entry != null && !entry.block().equals(blockAt(store, ref, index))) {
            counts.remove(index);
            markSave(store, ref);
        }
    }

    /** Removes every entry of a section whose block is no longer there. Returns how many went. */
    static int sweep(Store<ChunkStore> store, Ref<ChunkStore> section) {
        OreBreakSection counts = store.getComponent(section, type);
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<Integer, OreBreakSection.Entry> e : counts.snapshot().entrySet()) {
            if (!e.getValue().block().equals(blockAt(store, section, e.getKey()))) {
                counts.remove(e.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            markSave(store, section);
        }
        return removed;
    }

    private static void markSave(Store<ChunkStore> store, Ref<ChunkStore> section) {
        ChunkSection chunkSection = store.getComponent(section, ChunkSection.getComponentType());
        if (chunkSection != null) {
            chunkSection.markNeedsSaving();
        }
    }
}
