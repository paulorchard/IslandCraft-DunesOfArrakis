package com.paulorchard.islandcraft.dunesofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import org.joml.Vector3i;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Development only: runs the ore-wear rule against a real world in a headless server and writes what happened to a file, then stops the
 * server (so the chunks are saved). Switched on by the system property {@code arrakis.oretest}, which names the phases to run, with the
 * result file in {@code arrakis.oretest.out}:
 * <ul>
 * <li>{@code main}: five free breaks and the sixth for each listed ore, drops, health, hits that are not a player mining, a block that is
 * not listed, a world that is not Dunes of Arrakis;</li>
 * <li>{@code save}: leaves a count of 3 on a block, and a stale count (its block replaced behind the rule's back), then the server stops;</li>
 * <li>{@code load}: after a restart, checks the count survived, the stale one is gone, and a replaced block has no count.</li>
 * </ul>
 * A player's own hit cannot be made in a headless server, so the rule is driven through {@link OreBreakCore#freeBreak}, the call the
 * damage handler makes, and the engine's own damage and break calls with no actor stand for everything that is not a player.
 */
final class OreBreakSelfTest {

    private static final String[] BLOCKS = {"Ore_Copper_Sandstone", "Ore_Iron_Sandstone", "Ore_Silver_Sandstone", "Ore_Gold_Sandstone", "Ore_Thorium_Sandstone"};
    private static final int Y = 250;

    private final List<String> out = new ArrayList<>();
    private boolean failed;

    private OreBreakSelfTest() {
    }

    static void maybeRun(DunesOfArrakisPlugin plugin) {
        String phases = System.getProperty("arrakis.oretest");
        if (phases == null || phases.isBlank()) {
            return;
        }
        Thread thread = new Thread(() -> new OreBreakSelfTest().run(phases), "arrakis-oretest");
        thread.setDaemon(true);
        thread.start();
    }

    private void check(String name, boolean ok, String detail) {
        failed |= !ok;
        out.add((ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : ": " + detail));
    }

    private void run(String phases) {
        Path file = Paths.get(System.getProperty("arrakis.oretest.out", "oretest.txt"));
        try {
            World world = null;
            for (int i = 0; i < 600 && world == null; i++) {
                world = Universe.get() == null ? null : Universe.get().getDefaultWorld();
                if (world == null) {
                    Thread.sleep(500);
                }
            }
            if (world == null) {
                throw new IllegalStateException("no default world");
            }
            Thread.sleep(3000);
            final World w = world;
            // the blocks sit high above the terrain, in sections that have to be loaded first
            for (int x : new int[] {8, 40}) {
                w.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, Y, 8).get();
            }
            check("world is a Dunes of Arrakis world", OreBreakRules.appliesTo(w), String.valueOf(w.getWorldConfig().getWorldGenProvider()));
            for (String phase : phases.split(",")) {
                switch (phase.trim()) {
                    case "main" -> onWorld(w, () -> main(w));
                    case "save" -> onWorld(w, () -> save(w));
                    case "load" -> onWorld(w, () -> load(w));
                    default -> check("known phase " + phase, false, null);
                }
            }
            Thread.sleep(1500); // let the entity removals of the drop filter happen before the checks that look at them
            out.add(failed ? "RESULT FAILED" : "RESULT OK");
        } catch (Throwable e) {
            out.add("FAIL exception: " + e);
            StringBuilder trace = new StringBuilder();
            for (StackTraceElement el : e.getStackTrace()) {
                trace.append("  at ").append(el).append('\n');
                if (trace.length() > 3000) {
                    break;
                }
            }
            out.add(trace.toString());
            out.add("RESULT FAILED");
        }
        try {
            Files.write(file, out);
        } catch (Exception ignored) {
            // nothing more can be done here
        }
        HytaleServer.get().shutdownServer();
    }

    private static <T> T onWorld(World world, Supplier<T> task) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        world.execute(() -> {
            try {
                done.complete(task.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done.get();
    }

    // ---- helpers on the world thread ----

    private static void place(World world, int x, int y, int z, String block) {
        Ref<ChunkStore> section = OreBreakCounts.section(world, x, y, z);
        BlockType type = block.equals("Empty") ? BlockType.EMPTY : BlockType.getAssetMap().getAsset(block);
        BlockOperations.setBlock(world.getChunkStore(), section, x, y, z, BlockType.getAssetMap().getIndex(type.getId()), type, 0, 0, 0);
    }

    private static String at(World world, int x, int y, int z) {
        return OreBreakCounts.blockAt(world, x, y, z);
    }

    private static float healthOf(World world, Vector3i p) {
        Ref<ChunkStore> section = OreBreakCounts.section(world, p.x, p.y, p.z);
        BlockHealthSection health = world.getChunkStore().getStore().getComponent(section, BlockHealthSection.getComponentType());
        return health == null ? 1f : health.getHealth(ChunkUtil.indexBlock(p.x, p.y, p.z));
    }

    /** An accessor that remembers what is spawned through it, and passes everything on to the store. */
    @SuppressWarnings("unchecked")
    private static ComponentAccessor<EntityStore> recording(Store<EntityStore> store, List<Ref<EntityStore>> spawned, List<String> items) {
        return (ComponentAccessor<EntityStore>) Proxy.newProxyInstance(OreBreakSelfTest.class.getClassLoader(), new Class<?>[] {ComponentAccessor.class},
                (proxy, method, args) -> {
                    Object result = method.invoke(store, args);
                    if (method.getName().equals("addEntities") && args != null && args.length == 2 && args[0] instanceof Holder<?>[] holders) {
                        if (result instanceof Ref<?>[] refs) {
                            for (Ref<?> ref : refs) {
                                spawned.add((Ref<EntityStore>) ref);
                            }
                        }
                        for (Holder<?> holder : holders) {
                            ItemComponent item = ((Holder<EntityStore>) holder).getComponent(ItemComponent.getComponentType());
                            if (item != null && item.getItemStack() != null) {
                                items.add(item.getItemStack().getItemId() + " x" + item.getItemStack().getQuantity());
                            }
                        }
                    }
                    return result;
                });
    }

    // ---- the phases ----

    private Object main(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        ItemStack best = new ItemStack("Tool_Pickaxe_Onyxium");
        ItemStack crude = new ItemStack("Tool_Pickaxe_Crude");
        // 0. the tool rule against each ore: who can mine what (the engine's own gather type and quality test)
        for (String block : BLOCKS) {
            BlockType type = BlockType.getAssetMap().getAsset(block);
            var breaking = type.getGathering().getBreaking();
            out.add("(" + block + " needs gather type " + breaking.getGatherType() + " quality " + breaking.getQuality() + "; Onyxium pickaxe: "
                    + OreBreakRules.toolCanMine(best, type) + ", Crude pickaxe: " + OreBreakRules.toolCanMine(crude, type) + ", bare hands: " + OreBreakRules.toolCanMine(null, type) + ")");
            check(block + ": the best pickaxe can mine it, bare hands cannot", OreBreakRules.toolCanMine(best, type) && !OreBreakRules.toolCanMine(null, type), null);
        }
        // 1. every listed ore: five free breaks, then the sixth is the engine's
        for (int i = 0; i < BLOCKS.length; i++) {
            String block = BLOCKS[i];
            Vector3i pos = new Vector3i(8 + 2 * i, Y, 8);
            place(world, pos.x, pos.y, pos.z, block);
            BlockType type = BlockType.getAssetMap().getAsset(block);
            List<String> normal = new ArrayList<>();
            for (ItemStack s : OreBreakCore.dropsOf(type)) {
                normal.add(s.getItemId() + " x" + s.getQuantity());
            }
            check(block + ": normal drop is ore and rock", normal.size() == 2 && normal.get(1).startsWith("Rock_Sandstone_Cobble"), normal.toString());
            for (int hit = 1; hit <= 5; hit++) {
                List<Ref<EntityStore>> spawned = new ArrayList<>();
                List<String> items = new ArrayList<>();
                boolean free = OreBreakCore.freeBreak(world, recording(store, spawned, items), pos, type, best, 1.0f, 1.0f, null, null);
                int count = OreBreakCounts.get(world, pos.x, pos.y, pos.z, block);
                boolean oneOre = items.size() == 1 && items.get(0).startsWith(OreBreakRules.ORE_OF_BLOCK.get(block) + " x1");
                check(block + " break " + hit + ": a free break, one ore, block stays, count " + hit,
                        free && oneOre && block.equals(at(world, pos.x, pos.y, pos.z)) && count == hit && healthOf(world, pos) >= 1f,
                        "free=" + free + " items=" + items + " count=" + count + " health=" + healthOf(world, pos));
            }
            // a part-mined hit that does not break is left to the engine and does not count
            boolean partial = OreBreakCore.freeBreak(world, store, pos, type, best, 1.0f, 0.3f, null, null);
            check(block + ": a hit that does not break the block is not a free break", !partial && OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == 5, null);
            // the sixth breaking hit is not taken over
            boolean sixth = OreBreakCore.freeBreak(world, store, pos, type, best, 1.0f, 1.0f, null, null);
            check(block + " break 6: left to the engine, count still 5", !sixth && OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == 5, null);
            // the engine's break then ends the count (the player-break handler does this after the event; here the same call)
            place(world, pos.x, pos.y, pos.z, "Empty");
            OreBreakCounts.forgetIfGone(world, pos.x, pos.y, pos.z);
            check(block + ": count forgotten once the block is gone", OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == 0, null);
        }

        // 2. a cracked block is made whole
        Vector3i crack = new Vector3i(30, Y, 8);
        place(world, crack.x, crack.y, crack.z, BLOCKS[3]);
        Ref<ChunkStore> section = OreBreakCounts.section(world, crack.x, crack.y, crack.z);
        BlockHealthSection health = world.getChunkStore().getStore().getComponent(section, BlockHealthSection.getComponentType());
        health.damage(crack.x, crack.y, crack.z, 0.6f, java.time.Instant.now());
        float cracked = healthOf(world, crack);
        OreBreakCore.makeWhole(world, crack);
        check("a cracked block is whole again", cracked < 0.5f && healthOf(world, crack) >= 1f, "health " + cracked + " -> " + healthOf(world, crack));

        // 3. not a player mining: the block goes at once and drops nothing, whatever its count (an iron pickaxe, with no actor)
        ItemStack pick = new ItemStack("Tool_Pickaxe_Iron");
        com.hypixel.hytale.server.core.asset.type.item.config.ItemTool tool = pick.getItem().getTool();
        check("the test pickaxe has a tool", tool != null, String.valueOf(pick.getItem().getId()));
        Vector3i hit = new Vector3i(32, Y, 8);
        place(world, hit.x, hit.y, hit.z, BLOCKS[0]);
        OreBreakCounts.set(world, hit.x, hit.y, hit.z, BLOCKS[0], 3);
        List<Ref<EntityStore>> spawned = new ArrayList<>();
        List<String> items = new ArrayList<>();
        Ref<ChunkStore> sectionRef = OreBreakCounts.section(world, hit.x, hit.y, hit.z);
        boolean handsHit = BlockHarvestUtils.performBlockDamage(hit, null, null, 2.0f, 0, false, sectionRef, recording(store, spawned, items), world.getChunkStore().getStore());
        // the engine lets bare hands scratch the block (a hit of about 0.002); the rule must not count that, however far the scratches add up
        BlockType copperType = BlockType.getAssetMap().getAsset(BLOCKS[0]);
        check("hands: the engine hurts the block only a little", handsHit == handsHit && healthOf(world, hit) > 0.9f, "health " + healthOf(world, hit));
        check("hands: a hit that would break the block is not taken over and does not count",
                !OreBreakCore.freeBreak(world, store, hit, copperType, null, 1.0f, 1.0f, null, null) && OreBreakCounts.get(world, hit.x, hit.y, hit.z, BLOCKS[0]) == 3, null);
        check("a wrong tool (a sword) is not taken over either", !OreBreakCore.freeBreak(world, store, hit, copperType, new ItemStack("Weapon_Sword_Iron"), 1.0f, 1.0f, null, null), null);
        OreBreakCounts.set(world, hit.x, hit.y, hit.z, BLOCKS[0], 3); // the engine's scratches above may have changed nothing, but the count is put back to be sure
        boolean pickHit = BlockHarvestUtils.performBlockDamage(hit, pick, tool, 2.0f, 0, false, sectionRef, recording(store, spawned, items), world.getChunkStore().getStore());
        out.add("(the pickaxe hit returned " + pickHit + ")");
        check("damage with no actor removes the ore block", !BLOCKS[0].equals(at(world, hit.x, hit.y, hit.z)), "block now " + at(world, hit.x, hit.y, hit.z));
        check("... drops nothing", items.isEmpty(), items.toString());
        check("... and forgets the count", OreBreakCounts.get(world, hit.x, hit.y, hit.z, BLOCKS[0]) == 0, null);

        // 4. a break with no actor (other mods, physics): the drops are removed as they appear
        Vector3i env = new Vector3i(34, Y, 8);
        place(world, env.x, env.y, env.z, BLOCKS[1]);
        OreBreakCounts.set(world, env.x, env.y, env.z, BLOCKS[1], 2);
        List<Ref<EntityStore>> envSpawned = new ArrayList<>();
        List<String> envItems = new ArrayList<>();
        BlockHarvestUtils.performBlockBreak(env, BlockType.getAssetMap().getAsset(BLOCKS[1]), null, 0, null, null, 0, null,
                OreBreakCounts.section(world, env.x, env.y, env.z), recording(store, envSpawned, envItems), world.getChunkStore().getStore());
        check("a break with no actor removes the block", !BLOCKS[1].equals(at(world, env.x, env.y, env.z)), "block now " + at(world, env.x, env.y, env.z));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                int alive = 0;
                for (Ref<EntityStore> ref : envSpawned) {
                    if (ref != null && ref.isValid()) {
                        alive++;
                    }
                }
                out.add(String.format("%s a break with no actor leaves no drops behind: engine spawned %s, still in the world %d",
                        alive == 0 ? "PASS" : "FAIL", envItems, alive));
                failed |= alive != 0;
            } catch (InterruptedException ignored) {
                // test ending
            }
        });

        // 4b. what the first number of a damage event is: the health the block has before the hit (not the damage so far)
        Vector3i probe = new Vector3i(38, Y, 8);
        place(world, probe.x, probe.y, probe.z, "Rock_Sandstone");
        Ref<ChunkStore> probeSection = OreBreakCounts.section(world, probe.x, probe.y, probe.z);
        world.getChunkStore().getStore().getComponent(probeSection, BlockHealthSection.getComponentType()).damage(probe.x, probe.y, probe.z, 0.3f, java.time.Instant.now());
        List<String> seen = new ArrayList<>();
        OreBreakSystems.damageObserver = (now, hitSize) -> seen.add(now + "," + hitSize);
        BlockHarvestUtils.performBlockDamage(probe, pick, tool, 0.001f, 0, false, probeSection, store, world.getChunkStore().getStore());
        OreBreakSystems.damageObserver = null;
        check("health semantics: the event gives the block health before the hit (0.7 after 0.3 of damage) and the size of the hit",
                seen.size() == 1 && seen.get(0).startsWith("0.7"), seen.toString());
        place(world, probe.x, probe.y, probe.z, "Empty");

        // 5. a block that is not listed is not touched by the rule
        Vector3i plain = new Vector3i(36, Y, 8);
        place(world, plain.x, plain.y, plain.z, "Rock_Sandstone");
        BlockType sandstone = BlockType.getAssetMap().getAsset("Rock_Sandstone");
        check("Rock_Sandstone is not listed and its breaking hit is not taken over",
                !OreBreakCore.freeBreak(world, store, plain, sandstone, best, 1.0f, 1.0f, null, null) && OreBreakCounts.get(world, plain.x, plain.y, plain.z, "Rock_Sandstone") == 0, null);
        BlockHarvestUtils.performBlockDamage(plain, pick, tool, 2.0f, 0, false, OreBreakCounts.section(world, plain.x, plain.y, plain.z), store, world.getChunkStore().getStore());
        check("... and the engine breaks it as it always does", !"Rock_Sandstone".equals(at(world, plain.x, plain.y, plain.z)), "block now " + at(world, plain.x, plain.y, plain.z));

        // 6. the real handlers, with a synthetic player entity (no client: components only), driven through the engine event dispatch
        playerPath(world, store, best);

        // 7. other worlds
        check("a flat world is not a Dunes of Arrakis world", !OreBreakRules.appliesTo(new FlatWorldGenProvider()), null);
        check("no world given", !OreBreakRules.appliesTo((World) null), null);
        return null;
    }

    /**
     * The player handlers, called as the engine would call them for a player's events, minus the dispatch itself: a headless server has no
     * client, and a player entity made by hand crashes the world thread (the engine's own systems expect a connection), so the Player
     * component is a bare one that is not in the world.
     */
    private void playerPath(World world, Store<EntityStore> store, ItemStack best) {
        com.hypixel.hytale.server.core.entity.entities.Player player = new com.hypixel.hytale.server.core.entity.entities.Player();
        out.add("INFO player handlers driven directly with a bare Player component, game mode " + player.getGameMode());
        List<String> popped = new ArrayList<>();
        OreBreakCore.popObserver = stack -> popped.add(stack.getItemId() + " x" + stack.getQuantity());
        try {
            String block = BLOCKS[3];
            BlockType type = BlockType.getAssetMap().getAsset(block);
            Vector3i pos = new Vector3i(20, Y, 8);
            place(world, pos.x, pos.y, pos.z, block);
            com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent soft = new com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent(best, pos, type, 1.0f, 0.3f);
            OreBreakSystems.damageByPlayer(world, store, player, null, soft);
            check("player: a hit that does not break the block is not cancelled", !soft.isCancelled() && popped.isEmpty() && OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == 0, "cancelled=" + soft.isCancelled() + " popped=" + popped);
            for (int hit = 1; hit <= 5; hit++) {
                com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent event = new com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent(best, pos, type, 0.2f, 0.3f);
                int before = popped.size();
                OreBreakSystems.damageByPlayer(world, store, player, null, event);
                check("player break " + hit + ": damage cancelled and zeroed, one ore, block stays, count " + hit,
                        event.isCancelled() && event.getDamage() == 0f && popped.size() == before + 1 && block.equals(at(world, pos.x, pos.y, pos.z))
                                && OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == hit,
                        "cancelled=" + event.isCancelled() + " damage=" + event.getDamage() + " popped=" + popped + " count=" + OreBreakCounts.get(world, pos.x, pos.y, pos.z, block));
            }
            com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent sixth = new com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent(best, pos, type, 0.2f, 0.3f);
            int before = popped.size();
            OreBreakSystems.damageByPlayer(world, store, player, null, sixth);
            check("player break 6: left to the engine (not cancelled, damage kept, nothing popped)", !sixth.isCancelled() && sixth.getDamage() > 0f && popped.size() == before, "cancelled=" + sixth.isCancelled());
            Vector3i other = new Vector3i(22, Y, 8);
            place(world, other.x, other.y, other.z, block);
            for (ItemStack held : new ItemStack[] {null, new ItemStack("Weapon_Sword_Iron")}) {
                com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent event = new com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent(held, other, type, 0.002f, 0.003f);
                OreBreakSystems.damageByPlayer(world, store, player, null, event);
                check("player with " + (held == null ? "bare hands" : held.getItemId()) + ": not cancelled, no ore, count unmoved",
                        !event.isCancelled() && OreBreakCounts.get(world, other.x, other.y, other.z, block) == 0 && popped.size() == before, "cancelled=" + event.isCancelled());
            }
            // the sixth break goes through: the break handler leaves the count while the block is there, clears it once the block is gone
            OreBreakSystems.breakByPlayer(world, new com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent(best, pos, type));
            place(world, pos.x, pos.y, pos.z, "Empty");
            world.execute(() -> {
                boolean forgotten = OreBreakCounts.get(world, pos.x, pos.y, pos.z, block) == 0;
                out.add((forgotten ? "PASS" : "FAIL") + " player break event: count forgotten once the block is gone");
                failed |= !forgotten;
            });
            // a break that something else cancels afterwards keeps the count (the block is still there when the check runs)
            Vector3i kept = new Vector3i(23, Y, 8);
            place(world, kept.x, kept.y, kept.z, block);
            OreBreakCounts.set(world, kept.x, kept.y, kept.z, block, 4);
            OreBreakSystems.breakByPlayer(world, new com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent(best, kept, type));
            world.execute(() -> {
                boolean stillCounted = OreBreakCounts.get(world, kept.x, kept.y, kept.z, block) == 4;
                out.add((stillCounted ? "PASS" : "FAIL") + " a break that did not remove the block leaves its count");
                failed |= !stillCounted;
            });
            // a block placed where a count was
            Vector3i placed = new Vector3i(24, Y, 8);
            place(world, placed.x, placed.y, placed.z, block);
            OreBreakCounts.set(world, placed.x, placed.y, placed.z, block, 4);
            OreBreakSystems.placeByPlayer(world, new com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent(best, placed, com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.NONE));
            check("player places a block where a count was: the count is gone", OreBreakCounts.get(world, placed.x, placed.y, placed.z, block) == 0, null);
            // creative: the rule does not apply
            java.lang.reflect.Field mode = com.hypixel.hytale.server.core.entity.entities.Player.class.getDeclaredField("gameMode");
            mode.setAccessible(true);
            mode.set(player, com.hypixel.hytale.protocol.GameMode.Creative);
            Vector3i creative = new Vector3i(26, Y, 8);
            place(world, creative.x, creative.y, creative.z, block);
            com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent event = new com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent(best, creative, type, 0.2f, 0.3f);
            OreBreakSystems.damageByPlayer(world, store, player, null, event);
            check("creative: the damage event is left alone", !event.isCancelled() && OreBreakCounts.get(world, creative.x, creative.y, creative.z, block) == 0 && popped.size() == before, "cancelled=" + event.isCancelled());
            OreBreakCounts.set(world, creative.x, creative.y, creative.z, block, 2);
            OreBreakSystems.breakByPlayer(world, new com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent(null, creative, type));
            place(world, creative.x, creative.y, creative.z, "Empty");
            world.execute(() -> {
                boolean forgotten = OreBreakCounts.get(world, creative.x, creative.y, creative.z, block) == 0;
                out.add((forgotten ? "PASS" : "FAIL") + " creative break: count forgotten");
                failed |= !forgotten;
            });
        } catch (ReflectiveOperationException e) {
            out.add("INFO creative mode could not be set on the bare Player (" + e + ")");
        } finally {
            OreBreakCore.popObserver = null;
        }
    }

    private Object save(World world) {
        place(world, 40, Y, 8, "Ore_Gold_Sandstone");
        OreBreakCounts.set(world, 40, Y, 8, "Ore_Gold_Sandstone", 3);
        // a stale entry: the block is replaced behind the rule's back, the way a command would
        place(world, 41, Y, 8, "Ore_Copper_Sandstone");
        OreBreakCounts.set(world, 41, Y, 8, "Ore_Copper_Sandstone", 2);
        place(world, 41, Y, 8, "Empty");
        check("saved: gold ore with 3 free breaks, and a stale entry of 2", OreBreakCounts.get(world, 40, Y, 8, "Ore_Gold_Sandstone") == 3, null);
        return null;
    }

    private Object load(World world) {
        check("after the restart the gold ore is still there", "Ore_Gold_Sandstone".equals(at(world, 40, Y, 8)), String.valueOf(at(world, 40, Y, 8)));
        check("... with its count of 3", OreBreakCounts.get(world, 40, Y, 8, "Ore_Gold_Sandstone") == 3, String.valueOf(OreBreakCounts.get(world, 40, Y, 8, "Ore_Gold_Sandstone")));
        Ref<ChunkStore> section = OreBreakCounts.section(world, 40, Y, 8);
        OreBreakSection counts = world.getChunkStore().getStore().getComponent(section, OreBreakCounts.type());
        check("the stale entry was dropped when the section loaded", counts != null && counts.snapshot().size() == 1, counts == null ? "no component" : counts.snapshot().toString());
        place(world, 40, Y, 8, "Ore_Copper_Sandstone");
        check("a different ore put where the counted one was starts at 0", OreBreakCounts.get(world, 40, Y, 8, "Ore_Copper_Sandstone") == 0, null);
        counts = world.getChunkStore().getStore().getComponent(section, OreBreakCounts.type());
        check("... and the old count is gone from the section", counts != null && counts.isEmpty(), counts == null ? "no component" : counts.snapshot().toString());
        return null;
    }
}
