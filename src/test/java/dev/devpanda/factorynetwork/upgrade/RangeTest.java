package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Zahlen aus fernzugriff.md §3, an einer Stelle nachgerechnet.
 */
class RangeTest {

    private static final Loadout EMPTY = Loadout.of(List.of());

    private static Loadout cards(int howMany) {
        return Loadout.ofCounts(Map.of(Card.RANGE, howMany));
    }

    @Test
    @DisplayName("Ein Mast ohne Karten reicht sechzehn Blöcke weit")
    void aBareMastReachesSixteen() {
        assertEquals(16, Range.reach(EMPTY, EMPTY));
    }

    @Test
    @DisplayName("Im Mast zählt eine Karte doppelt")
    void theMastAmplifies() {
        // Der Mast ist ein Verstärker: Das Verdoppeln ist eine Eigenschaft
        // des Orts, nicht der Karte. Sonst hätte dieselbe Karte an zwei
        // Orten zwei Werte, und die Regel aus dem Ausbausystem wäre hin.
        assertEquals(16 + 16, Range.reach(cards(1), EMPTY));
        assertEquals(16 + 64, Range.reach(cards(4), EMPTY));
    }

    @Test
    @DisplayName("Im Gerät zählt sie einfach")
    void theDeviceCountsPlain() {
        assertEquals(16 + 8, Range.reach(EMPTY, cards(1)));
        assertEquals(16 + 32, Range.reach(EMPTY, cards(4)));
    }

    @Test
    @DisplayName("Voll ausgebaut sind es die 112 aus dem Entwurf")
    void fullyBuiltMatchesTheSpec() {
        assertEquals(112, Range.reach(cards(4), cards(4)));
        // Das Wireless Terminal hat nur zwei Plätze.
        assertEquals(96, Range.reach(cards(4), cards(2)));
        // Die Anzeigetafel einen, weil der andere das Modul trägt.
        assertEquals(88, Range.reach(cards(4), cards(1)));
    }

    @Test
    @DisplayName("Die Grenzenlos-Karte im Mast hebt alles auf")
    void infinityInTheMastLiftsEverything() {
        Loadout endgame = Loadout.of(List.of(Card.INFINITY));
        assertEquals(Range.UNLIMITED, Range.reach(endgame, EMPTY));
        assertTrue(Range.covers(endgame, EMPTY, 1_000_000.0));
    }

    @Test
    @DisplayName("Im Gerät hebt sie nichts auf")
    void infinityInTheDeviceDoesNothing() {
        // Sie ist Infrastruktur und gehört in den Mast. Steckte sie im
        // Gerät, bräuchte sie auf einem Server jeder Spieler einzeln — so
        // steht es in fernzugriff.md §3.
        Loadout inTheDevice = Loadout.of(List.of(Card.INFINITY));
        assertEquals(16, Range.reach(EMPTY, inTheDevice));
        assertFalse(Range.covers(EMPTY, inTheDevice, 1000.0));
    }

    @Test
    @DisplayName("Die Grenze ist die Grenze")
    void theEdgeIsTheEdge() {
        assertTrue(Range.covers(EMPTY, EMPTY, 16.0));
        assertFalse(Range.covers(EMPTY, EMPTY, 16.01));
    }
}
