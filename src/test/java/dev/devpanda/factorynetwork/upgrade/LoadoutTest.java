package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that carries the whole upgrade system: a module gives an ability,
 * a card raises a value.
 */
class LoadoutTest {

    @Test
    @DisplayName("Without a loadout a device can do nothing and has no value")
    void emptyMeansNothing() {
        Loadout empty = Loadout.of(List.of());
        assertFalse(empty.has(Ability.WIRELESS));
        assertEquals(0, empty.value(Stat.RANGE));
        assertFalse(empty.unlimited(Stat.RANGE));
    }

    @Test
    @DisplayName("A module unlocks its ability and no other")
    void aModuleUnlocksItsOwnAbility() {
        Loadout one = Loadout.of(List.of(Ability.WIRELESS));
        assertTrue(one.has(Ability.WIRELESS));
        // And it raises no value: that is what cards are for.
        assertEquals(0, one.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Equal cards add up")
    void equalCardsAddUp() {
        assertEquals(8, Loadout.of(List.of(Card.RANGE)).value(Stat.RANGE));
        assertEquals(24, Loadout.of(
                List.of(Card.RANGE, Card.RANGE, Card.RANGE)).value(Stat.RANGE));
    }

    @Test
    @DisplayName("The Infinity card removes the limit instead of raising it")
    void infinityLiftsTheLimit() {
        Loadout endgame = Loadout.of(List.of(Card.RANGE, Card.INFINITY));
        assertTrue(endgame.unlimited(Stat.RANGE));
        // The numeric value stays beside it and is not needed — whoever asks
        // unlimited no longer asks value.
        assertEquals(8, endgame.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Every card acts on exactly one stat")
    void everyCardHitsExactlyOneStat() {
        // Without this check a second set of rules grows here over time: a
        // card that does three things at once, and no one knows any more what
        // a slot costs.
        for (Card card : Card.values()) {
            assertTrue(card.stat() != null, card + " hebt keinen Wert");
        }
    }

    @Test
    @DisplayName("A stack in one slot counts piece by piece")
    void aStackCountsEveryPiece() {
        // Three Range cards in one slot act like three cards. That is the
        // consequence of equal cards adding up — and the only rule of the
        // container that can be checked without spinning up Minecraft.
        Loadout stacked = Loadout.ofCounts(java.util.Map.of(Card.RANGE, 3));
        assertEquals(24, stacked.value(Stat.RANGE));
        assertEquals(3, stacked.installed().size());
    }

    @Test
    @DisplayName("An empty stack contributes nothing")
    void anEmptyStackAddsNothing() {
        assertEquals(0, Loadout.ofCounts(java.util.Map.of(Card.RANGE, 0))
                .value(Stat.RANGE));
    }

    @Test
    @DisplayName("And one can count how often one is inserted")
    void countingIsPossible() {
        Loadout mixed = Loadout.of(List.of(Card.RANGE, Card.RANGE, Ability.WIRELESS));
        assertEquals(2, mixed.count(Card.RANGE));
        assertEquals(1, mixed.count(Ability.WIRELESS));
        assertEquals(0, mixed.count(Card.INFINITY));
    }

    @Test
    @DisplayName("Modules and cards share the slots")
    void bothKindsShareTheSlots() {
        Loadout mixed = Loadout.of(List.of(Ability.WIRELESS, Card.RANGE, Card.RANGE));
        assertTrue(mixed.has(Ability.WIRELESS));
        assertEquals(16, mixed.value(Stat.RANGE));
        assertEquals(3, mixed.installed().size());
    }
}
