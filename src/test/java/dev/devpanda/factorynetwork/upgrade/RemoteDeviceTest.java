package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the two devices may do — and what makes the laptop more expensive.
 */
class RemoteDeviceTest {

    @Test
    @DisplayName("The terminal can do everything except code")
    void theTerminalHasEverythingButCode() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertEquals(tab != TerminalTab.CODE,
                    RemoteDevice.TERMINAL.allows(tab),
                    tab + " am Wireless Terminal");
        }
    }

    @Test
    @DisplayName("The laptop can do everything")
    void theLaptopHasEverything() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertTrue(RemoteDevice.LAPTOP.allows(tab), tab + " am Laptop");
        }
    }

    @Test
    @DisplayName("And it has more slots — that is the second reason for it")
    void theLaptopHasMoreSlots() {
        assertEquals(2, RemoteDevice.TERMINAL.slots());
        assertEquals(4, RemoteDevice.LAPTOP.slots());
        assertTrue(RemoteDevice.LAPTOP.slots() > RemoteDevice.TERMINAL.slots());
    }

    @Test
    @DisplayName("Exactly one device can do the code")
    void codeIsTheDividingLine() {
        // The separation is the whole point: on the go one reaches the
        // storage, but not the code.
        long withCode = Arrays.stream(RemoteDevice.values())
                .filter(device -> device.allows(TerminalTab.CODE))
                .count();
        assertEquals(1, withCode);
        assertFalse(RemoteDevice.TERMINAL.allows(TerminalTab.CODE));
    }

    @Test
    @DisplayName("A device without Range cards still reaches some distance")
    void everyDeviceReachesSomething() {
        // Otherwise a freshly built device would be worthless, and the player
        // would have to build cards first just to see that it works at all.
        assertTrue(Range.reach(Loadout.of(java.util.List.of()),
                Loadout.of(java.util.List.of())) > 0);
    }
}
