package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ein Profil, das über die Leitung geht, muss drüben dasselbe sein.
 *
 * <p>Geprüft wird die Umrechnung in die flache Form und zurück — ohne
 * Netzwerkpuffer, damit der Test ohne Minecraft läuft.
 */
class DeviceProfileCodecTest {

    @Test
    @DisplayName("Ein Profil übersteht Hin- und Rückweg unverändert")
    void aProfileSurvivesTheRoundTrip() {
        DeviceProfile before = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.UP, Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        DeviceProfile after = DeviceProfileCodec.fromFlat(DeviceProfileCodec.toFlat(before));

        assertEquals(before, after);
    }

    @Test
    @DisplayName("Ein unbekanntes Gerät bleibt unbekannt")
    void anUnreachableProfileStaysUnreachable() {
        DeviceProfile before = DeviceProfile.unreachable();

        DeviceProfile after = DeviceProfileCodec.fromFlat(DeviceProfileCodec.toFlat(before));

        assertEquals(before, after);
    }

    @Test
    @DisplayName("Der seitenlose Zugang übersteht den Weg genauso")
    void theSidelessAccessSurvivesToo() {
        DeviceProfile before = new DeviceProfile("block.create.depot", "create",
                Side.NORTH, Map.of(Side.ANY, new DeviceProfile.Access(1, 0, false)));

        DeviceProfile after = DeviceProfileCodec.fromFlat(DeviceProfileCodec.toFlat(before));

        assertEquals(before, after);
        assertEquals(1, after.accessAt(Side.WEST).slots(),
                "was ohne Seite angeboten wird, gilt für jede");
    }
}
