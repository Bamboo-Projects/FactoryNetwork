package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile is pure information: it does not compute, it only reports what is there.
 */
class DeviceProfileTest {

    private static DeviceProfile crusher() {
        return new DeviceProfile("block.mekanism.crusher", "mekanism", Side.UP,
                Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.SOUTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));
    }

    @Test
    @DisplayName("A side without an entry can do nothing")
    void anUnlistedSideCanNothing() {
        DeviceProfile profile = crusher();

        assertFalse(profile.hasItems(Side.DOWN));
        assertFalse(profile.hasFluids(Side.DOWN));
        assertFalse(profile.hasEnergy(Side.DOWN));
    }

    @Test
    @DisplayName("The connected side is tracked separately")
    void theConnectedSideIsKeptApart() {
        DeviceProfile profile = crusher();

        assertEquals(Side.UP, profile.connectedSide());
        assertTrue(profile.hasEnergy(Side.UP));
        assertFalse(profile.hasItems(Side.UP),
                "oben nimmt die Maschine Strom und keine Gegenstände");
    }

    @Test
    @DisplayName("Asking for items names the sides that have them")
    void sidesWithItemsAreListed() {
        List<Side> sides = crusher().sidesWith(DeviceProfile.Access.Ability.ITEMS);

        assertEquals(2, sides.size(), () -> "erwartet Norden und Süden, war " + sides);
        assertTrue(sides.contains(Side.NORTH));
        assertTrue(sides.contains(Side.SOUTH));
    }

    @Test
    @DisplayName("A device in an unloaded area says nothing about itself")
    void anUnloadedDeviceSaysNothing() {
        DeviceProfile unknown = DeviceProfile.unreachable();

        assertFalse(unknown.reachable());
        assertFalse(unknown.hasItems(Side.NORTH));
        assertTrue(unknown.sidesWith(DeviceProfile.Access.Ability.ITEMS).isEmpty());
    }

    @Test
    @DisplayName("Sides with the same access are grouped together")
    void sidesWithTheSameAccessAreGrouped() {
        DeviceProfile.Access threeSlots = new DeviceProfile.Access(3, 0, false);
        DeviceProfile machine = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.UP, Map.of(
                        Side.NORTH, threeSlots,
                        Side.SOUTH, threeSlots,
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        List<DeviceProfile.Group> groups = machine.grouped();

        assertEquals(2, groups.size(),
                () -> "erwartet zwei Gruppen, war " + groups);
        DeviceProfile.Group items = groups.stream()
                .filter(group -> group.access().slots() == 3)
                .findFirst().orElseThrow();
        assertEquals(List.of(Side.NORTH, Side.SOUTH), items.sides(),
                "die Seiten stehen in der Reihenfolge der Aufzählung");
    }
}
