package com.paulorchard.islandcraft.dunesofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How many free breaks the part-mined ore blocks of one chunk section have had. It is a component of the section entity, like the
 * engine's own block health, so it is saved and unloaded with the chunk. Only blocks that have been mined at least once have an entry.
 * Each entry knows which block it is for: a count never applies to any other block.
 *
 * <p>Stored as strings {@code "<index in section>:<count>:<block id>"}, e.g. {@code "4129:3:Ore_Gold_Sandstone"}.
 */
public final class OreBreakSection implements Component<ChunkStore> {

    public static final String ID = "Arrakis_OreBreaks";

    public static final BuilderCodec<OreBreakSection> CODEC =
            BuilderCodec.builder(OreBreakSection.class, OreBreakSection::new)
                    .append(new KeyedCodec<>("Counts", Codec.STRING_ARRAY, false),
                            (section, value) -> section.decode(value),
                            section -> section.encode())
                    .add()
                    .build();

    /** One part-mined block. */
    record Entry(String block, int count) {
    }

    private final Map<Integer, Entry> entries = new HashMap<>();

    public OreBreakSection() {
    }

    int count(int index, String block) {
        Entry entry = entries.get(index);
        return entry != null && entry.block().equals(block) ? entry.count() : 0;
    }

    Entry get(int index) {
        return entries.get(index);
    }

    void put(int index, String block, int count) {
        entries.put(index, new Entry(block, count));
    }

    boolean remove(int index) {
        return entries.remove(index) != null;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** A copy of the entries, safe to walk while removing. */
    Map<Integer, Entry> snapshot() {
        return new HashMap<>(entries);
    }

    private String[] encode() {
        if (entries.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, Entry> e : entries.entrySet()) {
            out.add(e.getKey() + ":" + e.getValue().count() + ":" + e.getValue().block());
        }
        return out.toArray(new String[0]);
    }

    private void decode(String[] value) {
        entries.clear();
        if (value == null) {
            return;
        }
        for (String text : value) {
            String[] part = text.split(":", 3);
            if (part.length != 3) {
                continue;
            }
            try {
                entries.put(Integer.parseInt(part[0]), new Entry(part[2], Integer.parseInt(part[1])));
            } catch (NumberFormatException ignored) {
                // a damaged entry is dropped, which only costs the player a few extra free breaks
            }
        }
    }

    @Override
    public Component<ChunkStore> clone() {
        OreBreakSection copy = new OreBreakSection();
        copy.entries.putAll(entries);
        return copy;
    }
}
