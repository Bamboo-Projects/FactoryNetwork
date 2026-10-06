package dev.devpanda.factorynetwork.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellEntriesTest {

    /** Names that this "game" knows; everything else is a mod that is gone. */
    private static final Set<String> KNOWN = Set.of("stone", "iron");

    private static final CellFormat.Entry<String> ENTRY = new CellFormat.Entry<>() {

        @Override
        public String read(CompoundTag tag, HolderLookup.Provider registries) {
            String id = tag.getString("Item");
            return KNOWN.contains(id) ? id : null;
        }

        @Override
        public void write(CompoundTag tag, String key, HolderLookup.Provider registries) {
            tag.putString("Item", key);
        }
    };

    private static CellFormat.Loaded<String> load(ListTag list) {
        return CellEntries.load(list, "Count", ENTRY, null);
    }

    private static CompoundTag entry(String item, long count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", item);
        tag.putLong("Count", count);
        return tag;
    }

    private static ListTag stored() {
        ListTag list = new ListTag();
        list.add(entry("stone", 64));
        CompoundTag foreign = entry("othermod:gear", 12);
        foreign.putString("components", "something this game cannot parse");
        list.add(foreign);
        list.add(entry("iron", 5));
        return list;
    }

    @Test
    @DisplayName("An unreadable entry is not handed out as contents")
    void unreadableIsNotContents() {
        CellFormat.Loaded<String> loaded = load(stored());
        assertEquals(Map.of("stone", 64L, "iron", 5L), loaded.contents());
    }

    @Test
    @DisplayName("It is kept exactly as it was stored")
    void unreadableIsKeptVerbatim() {
        ListTag before = stored();
        CellFormat.Loaded<String> loaded = load(before);
        assertEquals(List.of(before.getCompound(1)), loaded.unreadable());
        assertEquals(12, CellEntries.amountOf(loaded.unreadable(), "Count"));
    }

    @Test
    @DisplayName("Writing back after a change does not lose it")
    void survivesAWrite() {
        CellFormat.Loaded<String> loaded = load(stored());
        Map<String, Long> changed = new LinkedHashMap<>(loaded.contents());
        changed.remove("iron");
        changed.put("stone", 10L);

        ListTag written = CellEntries.save(changed, loaded.unreadable(), "Count", ENTRY, null);

        CellFormat.Loaded<String> again = load(written);
        assertEquals(Map.of("stone", 10L), again.contents());
        assertEquals(1, again.unreadable().size());
        CompoundTag kept = again.unreadable().get(0);
        assertEquals("othermod:gear", kept.getString("Item"));
        assertEquals(12, kept.getLong("Count"));
        assertTrue(kept.contains("components"), "the extra data must come through untouched");
    }

    @Test
    @DisplayName("An entry with no amount is dropped, readable or not")
    void emptyEntriesAreDropped() {
        ListTag list = new ListTag();
        list.add(entry("stone", 0));
        list.add(entry("othermod:gear", 0));
        CellFormat.Loaded<String> loaded = load(list);
        assertTrue(loaded.contents().isEmpty());
        assertTrue(loaded.unreadable().isEmpty());
    }
}
