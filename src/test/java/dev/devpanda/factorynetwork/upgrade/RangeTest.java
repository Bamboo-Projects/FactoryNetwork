package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers from fernzugriff.md §3, recomputed in one place.
 */
class RangeTest {

    private static final Loadout EMPTY = Loadout.of(List.of());

    private static Loadout cards(int howMany) {
        return Loadout.ofCounts(Map.of(Card.RANGE, howMany));
    }

    @Test
    @DisplayName("A mast without cards reaches sixteen blocks")
    void aBareMastReachesSixteen() {
        assertEquals(16, Range.reach(EMPTY, EMPTY));
    }

    @Test
    @DisplayName("In the mast a card counts double")
    void theMastAmplifies() {
        // The mast is an amplifier: the doubling is a property of the place,
        // not of the card. Otherwise the same card would have two values in
        // two places, and the rule from the upgrade system would be broken.
        assertEquals(16 + 16, Range.reach(cards(1), EMPTY));
        assertEquals(16 + 64, Range.reach(cards(4), EMPTY));
    }

    @Test
    @DisplayName("In the device it counts single")
    void theDeviceCountsPlain() {
        assertEquals(16 + 8, Range.reach(EMPTY, cards(1)));
        assertEquals(16 + 32, Range.reach(EMPTY, cards(4)));
    }

    @Test
    @DisplayName("Fully built it is the 112 from the spec")
    void fullyBuiltMatchesTheSpec() {
        assertEquals(112, Range.reach(cards(4), cards(4)));
        // The Wireless Terminal has only two slots.
        assertEquals(96, Range.reach(cards(4), cards(2)));
        // The display board one, because the other carries the module.
        assertEquals(88, Range.reach(cards(4), cards(1)));
    }

    @Test
    @DisplayName("The Infinity card in the mast removes all limits")
    void infinityInTheMastLiftsEverything() {
        Loadout endgame = Loadout.of(List.of(Card.INFINITY));
        assertEquals(Range.UNLIMITED, Range.reach(endgame, EMPTY));
        assertTrue(Range.covers(endgame, EMPTY, 1_000_000.0));
    }

    @Test
    @DisplayName("In the device it removes nothing")
    void infinityInTheDeviceDoesNothing() {
        // It is infrastructure and belongs in the mast. If it sat in the
        // device, on a server every player would need it individually — that
        // is how it stands in fernzugriff.md §3.
        Loadout inTheDevice = Loadout.of(List.of(Card.INFINITY));
        assertEquals(16, Range.reach(EMPTY, inTheDevice));
        assertFalse(Range.covers(EMPTY, inTheDevice, 1000.0));
    }

    @Test
    @DisplayName("The edge is the edge")
    void theEdgeIsTheEdge() {
        assertTrue(Range.covers(EMPTY, EMPTY, 16.0));
        assertFalse(Range.covers(EMPTY, EMPTY, 16.01));
    }
}
