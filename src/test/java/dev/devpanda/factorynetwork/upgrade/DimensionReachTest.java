package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Across a dimension boundary only the Infinity card reaches.
 *
 * <p><b>Why distance does not count there:</b> between two dimensions there
 * is no distance one could measure. The Nether does not lie a hundred blocks
 * from the Overworld — it lies alongside and at the same time nowhere. A
 * calculation with coordinates from two worlds would yield a number without
 * meaning.
 */
class DimensionReachTest {

    private static final Loadout BARE = Loadout.of(List.of());
    private static final Loadout MANY_CARDS =
            Loadout.of(List.of(Card.RANGE, Card.RANGE, Card.RANGE, Card.RANGE));
    private static final Loadout INFINITE = Loadout.of(List.of(Card.INFINITY));

    @Test
    @DisplayName("Within the same dimension distance still decides")
    void withinOneWorldDistanceStillRules() {
        assertTrue(Range.covers(BARE, BARE, true, 5));
        assertFalse(Range.covers(BARE, BARE, true, 5000));
    }

    @Test
    @DisplayName("Without the Infinity card the network ends at the dimension boundary")
    void withoutTheCardTheWorldEnds() {
        // Even four cards do not help: range is a distance, and a dimension
        // boundary is none.
        assertFalse(Range.covers(MANY_CARDS, MANY_CARDS, false, 0));
        assertFalse(Range.covers(MANY_CARDS, MANY_CARDS, false, 1));
    }

    @Test
    @DisplayName("With it, it holds everywhere")
    void withTheCardItReachesEverywhere() {
        assertTrue(Range.covers(INFINITE, BARE, false, 0));
        assertTrue(Range.covers(INFINITE, BARE, false, 1_000_000));
    }

    @Test
    @DisplayName("The card must sit in the mast, not in the device")
    void theCardBelongsInTheMast() {
        // It is the most expensive card in the game and lifts the limit for
        // the whole network. If it sat in the device, every player would have
        // their own — and the mast would no longer be the decision.
        assertFalse(Range.covers(BARE, INFINITE, false, 0));
    }
}
