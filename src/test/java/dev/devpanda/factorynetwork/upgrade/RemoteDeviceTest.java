package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was die beiden Geräte dürfen — und was den Laptop teurer macht.
 */
class RemoteDeviceTest {

    @Test
    @DisplayName("Das Terminal kann alles außer Code")
    void theTerminalHasEverythingButCode() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertEquals(tab != TerminalTab.CODE,
                    RemoteDevice.TERMINAL.allows(tab),
                    tab + " am Wireless Terminal");
        }
    }

    @Test
    @DisplayName("Der Laptop kann alles")
    void theLaptopHasEverything() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertTrue(RemoteDevice.LAPTOP.allows(tab), tab + " am Laptop");
        }
    }

    @Test
    @DisplayName("Und er hat mehr Plätze — das ist der zweite Grund für ihn")
    void theLaptopHasMoreSlots() {
        assertEquals(2, RemoteDevice.TERMINAL.slots());
        assertEquals(4, RemoteDevice.LAPTOP.slots());
        assertTrue(RemoteDevice.LAPTOP.slots() > RemoteDevice.TERMINAL.slots());
    }

    @Test
    @DisplayName("Genau ein Gerät kann den Code")
    void codeIsTheDividingLine() {
        // Die Trennung ist der Sinn der ganzen Sache: Unterwegs kommt man
        // ans Lager, aber nicht an den Code.
        long withCode = Arrays.stream(RemoteDevice.values())
                .filter(device -> device.allows(TerminalTab.CODE))
                .count();
        assertEquals(1, withCode);
        assertFalse(RemoteDevice.TERMINAL.allows(TerminalTab.CODE));
    }

    @Test
    @DisplayName("Ein Gerät ohne Reichweitenkarten kommt trotzdem ein Stück weit")
    void everyDeviceReachesSomething() {
        // Sonst wäre ein frisch gebautes Gerät wertlos, und der Spieler
        // müsste erst Karten bauen, um zu sehen, dass es überhaupt geht.
        assertTrue(Range.reach(Loadout.of(java.util.List.of()),
                Loadout.of(java.util.List.of())) > 0);
    }
}
