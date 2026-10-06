package dev.devpanda.factorynetwork.block;

import dev.devpanda.factorynetwork.block.entity.SaveVersion;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveVersionTest {

    @Test
    @DisplayName("A save without the number is version 0")
    void legacySaveIsVersionZero() {
        assertEquals(0, SaveVersion.read(new CompoundTag()));
    }

    @Test
    @DisplayName("What is written is read back")
    void roundTrip() {
        CompoundTag tag = new CompoundTag();
        SaveVersion.write(tag);
        assertEquals(SaveVersion.CURRENT, SaveVersion.read(tag));
    }

    @Test
    @DisplayName("A number of the wrong type counts as missing")
    void wrongTypeIsIgnored() {
        CompoundTag tag = new CompoundTag();
        tag.putString("DataVersion", "7");
        assertEquals(0, SaveVersion.read(tag));
    }

    @Test
    @DisplayName("Only a higher number is newer")
    void newerMeansHigher() {
        assertFalse(SaveVersion.isNewer(0));
        assertFalse(SaveVersion.isNewer(SaveVersion.CURRENT));
        assertTrue(SaveVersion.isNewer(SaveVersion.CURRENT + 1));
    }
}
