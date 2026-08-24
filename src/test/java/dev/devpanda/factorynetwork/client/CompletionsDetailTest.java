package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.client.screen.Completions;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was hinter einem Connector in der Vorschlagsliste steht.
 *
 * <p>Der Übersetzungsschlüssel wird hier nicht aufgelöst — das tut der
 * Client mit seiner Sprachdatei. Geprüft wird der Teil dahinter.
 */
class CompletionsDetailTest {

    @Test
    @DisplayName("Ein Gerät nennt, was es kann")
    void aDeviceNamesWhatItCanDo() {
        DeviceProfile crusher = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.NORTH, Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        String detail = Completions.abilities(crusher);

        assertTrue(detail.contains("Gegenstände"), () -> "war: " + detail);
        assertTrue(detail.contains("Strom"), () -> "war: " + detail);
    }

    @Test
    @DisplayName("Ein unbekanntes Gerät behauptet nichts")
    void anUnknownDeviceClaimsNothing() {
        assertEquals("", Completions.abilities(DeviceProfile.unreachable()));
    }

    @Test
    @DisplayName("Ein Gerät, das nichts kann, sagt das auch")
    void aDeviceWithoutAbilitiesSaysSo() {
        DeviceProfile stone = new DeviceProfile("block.minecraft.stone", "minecraft",
                Side.NORTH, Map.of());

        assertEquals("nichts anzuschließen", Completions.abilities(stone));
    }

    @Test
    @DisplayName("Jede Fähigkeit steht nur einmal da")
    void everyAbilityIsNamedOnce() {
        DeviceProfile.Access slots = new DeviceProfile.Access(3, 0, false);
        DeviceProfile machine = new DeviceProfile("block.minecraft.chest", "minecraft",
                Side.NORTH, Map.of(
                        Side.NORTH, slots, Side.SOUTH, slots,
                        Side.EAST, slots, Side.WEST, slots));

        String detail = Completions.abilities(machine);

        assertEquals("Gegenstände", detail,
                "vier Seiten mit Fächern sind trotzdem eine Fähigkeit");
    }
}
