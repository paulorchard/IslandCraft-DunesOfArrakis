package com.paulorchard.islandcraft.dunesofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.world.BlockDamageCmd;
import com.hypixel.hytale.protocol.packets.world.UpdateBlockDamages;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;

/**
 * What a hit on a listed ore block does. The engine calls are the ones the Compactor in the Equipment mod makes (drops, popping an item out
 * of a face, block health), moved to the 0.7.0 section API where that changed.
 */
final class OreBreakCore {

    /** Test seam: told about every ore a free break pops (the self test cannot see entities spawned through the engine command buffer). */
    static volatile java.util.function.Consumer<ItemStack> popObserver;

    private OreBreakCore() {
    }

    /**
     * A player in survival hits a listed ore block with a tool that can mine it. Returns true when the hit is turned into a free break: the
     * caller then cancels the engine's damage, so the block is not hurt, does not break and gives nothing of its own. False leaves the
     * hit to the engine (not the breaking hit, or the sixth break, or not a listed block).
     *
     * @param held   the item in the player hand; only a tool that can mine the block counts
     * @param health the block's health before the hit (1 = whole)
     * @param damage the health this hit takes off
     * @param popAt  where the ore appears, null for just above the block
     * @param push   the push it gets, null for straight up
     */
    static boolean freeBreak(World world, ComponentAccessor<EntityStore> accessor, Vector3i pos, BlockType block, ItemStack held, float health, float damage,
            Vector3d popAt, Vector3d push) {
        String id = block == null ? null : block.getId();
        if (!OreBreakRules.isListed(id) || !OreBreakRules.toolCanMine(held, block) || !OreBreakRules.wouldBreak(health, damage)) {
            return false;
        }
        int count = OreBreakCounts.get(world, pos.x, pos.y, pos.z, id);
        if (count >= OreBreakRules.FREE_BREAKS) {
            return false;
        }
        ItemStack ore = oreOf(block);
        if (ore == null) {
            return false; // a block with no ore in its drops cannot be worn down; let it break
        }
        java.util.function.Consumer<ItemStack> watcher = popObserver;
        if (watcher != null) {
            watcher.accept(ore);
        }
        Vector3d at = popAt != null ? popAt : new Vector3d(pos.x + 0.5, pos.y + 1.15, pos.z + 0.5);
        Vector3d p = push != null ? push : new Vector3d(0, 1.5, 0);
        Holder<EntityStore> drop = ItemComponent.generateItemDrop(accessor, ore, at, new Rotation3f(), (float) p.x, (float) p.y, (float) p.z);
        if (drop != null) {
            @SuppressWarnings("unchecked")
            Holder<EntityStore>[] holders = new Holder[] {drop};
            accessor.addEntities(holders, AddReason.SPAWN);
        }
        makeWhole(world, pos);
        OreBreakCounts.set(world, pos.x, pos.y, pos.z, id, count + 1);
        return true;
    }

    /** One of the ore item for a free break, from the block's own resolved drop list, never the rock. Null when the block drops no ore. */
    static ItemStack oreOf(BlockType block) {
        for (ItemStack stack : dropsOf(block)) {
            if (OreBreakRules.isOreItem(stack.getItemId())) {
                return new ItemStack(stack.getItemId(), 1);
            }
        }
        return null;
    }

    /** What breaking this block would give, from the engine's own drop generation (loot tables included). Copied from the Compactor. */
    static List<ItemStack> dropsOf(BlockType block) {
        BlockGathering gathering = block.getGathering();
        if (gathering != null) {
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null) {
                return BlockHarvestUtils.getDrops(block, breaking.getQuantity(), breaking.getItemId(), breaking.getDropListId());
            }
            if (gathering.isSoft()) {
                SoftBlockDropType soft = gathering.getSoft();
                return BlockHarvestUtils.getDrops(block, 1, soft.getItemId(), soft.getDropListId());
            }
        }
        return BlockHarvestUtils.getDrops(block, 1, null, null);
    }

    /**
     * Gives the block back its full health and tells the players near it, so no crack is left. The engine's own repair path
     * (BlockHealthSection.damage with a negative amount) clears the record but skips the update to the clients, so the packet the engine
     * sends for any health change is sent here.
     */
    static void makeWhole(World world, Vector3i pos) {
        Ref<ChunkStore> ref = OreBreakCounts.section(world, pos.x, pos.y, pos.z);
        if (ref == null) {
            return;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        BlockHealthSection health = store.getComponent(ref, BlockHealthSection.getComponentType());
        ChunkSection section = store.getComponent(ref, ChunkSection.getComponentType());
        if (health == null || section == null) {
            return;
        }
        int index = ChunkUtil.indexBlock(pos.x, pos.y, pos.z);
        float before = health.getHealth(index);
        if (!health.clearHealth(index) && before >= 1f) {
            return;
        }
        section.markNeedsSaving();
        UpdateBlockDamages packet = new UpdateBlockDamages(section.getX(), section.getY(), section.getZ(),
                new BlockDamageCmd[] {new BlockDamageCmd((short) index, 1.0f, 1.0f - before)});
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> entity = player.getReference();
            if (entity != null && entity.isValid() && player.getChunkTracker().isLoaded(section.getX(), section.getY(), section.getZ())) {
                player.getPacketHandler().writeNoCache(packet);
            }
        }
    }

    /** Removes the block without a drop, a sound or a break effect, as a command would, and forgets its count. */
    static void removeQuietly(World world, Vector3i pos) {
        Ref<ChunkStore> ref = OreBreakCounts.section(world, pos.x, pos.y, pos.z);
        if (ref != null) {
            BlockOperations.setBlock(world.getChunkStore(), ref, pos.x, pos.y, pos.z, 0, BlockType.EMPTY, 0, 0, 0);
        }
        OreBreakCounts.forget(world, pos.x, pos.y, pos.z);
    }
}
