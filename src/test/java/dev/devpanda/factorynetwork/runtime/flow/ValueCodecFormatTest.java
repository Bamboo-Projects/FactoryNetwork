package dev.devpanda.factorynetwork.runtime.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The names on disk.
 *
 * <p>A waiting flow lies in the world, and its variables lie within it as
 * NBT. Whoever rebuilds the value model easily changes these names along the
 * way — and then an old world no longer reads its flows. They are fixed here.
 *
 * <p><b>Built by hand and not via a round-trip through the writer.</b> A
 * round-trip always agrees with itself, even when both sides have drifted
 * together. It is therefore tested the other way around: a tag, as it lies in
 * a world today, goes in — and the same tag comes back out.
 *
 * <p>Items and fluids are missing here because their identifiers are resolved
 * against the registry, and without a running game that registry is none. For
 * them the same check lives in the game test.
 */
class ValueCodecFormatTest {

    @Test
    @DisplayName("A stored chemical is still called chem")
    void asavedChemicalIsStillCalledChem() {
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chem");
        stored.putString("v", "mekanism:hydrogen");

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }

    @Test
    @DisplayName("A stored chemical selection is still called chemsel")
    void asavedChemicalSelectionIsStillCalledChemsel() {
        ListTag ids = new ListTag();
        ids.add(StringTag.valueOf("mekanism:hydrogen"));
        ids.add(StringTag.valueOf("mekanism:oxygen"));
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chemsel");
        stored.put("i", ids);
        stored.putLong("a", 500);

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }

    @Test
    @DisplayName("A selection without an amount keeps the -1")
    void aselectionWithoutAnamountKeepsTheMinusOne() {
        // -1 means "everything that is there". If the number turned to 0
        // during a rebuild, "everything" would silently have become "nothing"
        // — and after a restart the flow would move not a drop more.
        ListTag ids = new ListTag();
        ids.add(StringTag.valueOf("mekanism:hydrogen"));
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chemsel");
        stored.put("i", ids);
        stored.putLong("a", -1);

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }
}
