package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.network.Power;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a mast costs — and that range stays a decision.
 */
class MastCostTest {

    @Test
    @DisplayName("A mast without cards draws little")
    void aBareMastIsCheap() {
        assertEquals(Power.MAST_BASE, Power.mast(Loadout.of(List.of())));
    }

    @Test
    @DisplayName("Every card costs power")
    void everyCardCosts() {
        // Without this, range would be a checkbox and not a decision: whoever
        // has four cards plugs in four, and no one thinks about it.
        int bare = Power.mast(Loadout.of(List.of()));
        int full = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        assertTrue(full > bare, "vier Karten kosten nicht mehr als keine");
        assertEquals(bare + 4 * Power.MAST_PER_CARD, full);
    }

    @Test
    @DisplayName("The Infinity card costs the most")
    void infinityCostsMost() {
        int full = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        int endgame = Power.mast(Loadout.of(List.of(Card.INFINITY)));
        assertTrue(endgame > full,
                "unbegrenzt kostet weniger als vier Karten — dann nimmt sie jeder");
    }

    @Test
    @DisplayName("A module costs nothing")
    void aModuleIsFree() {
        // It raises no value, so it drives no consumption either. What costs
        // power is range — and that comes from cards.
        assertEquals(Power.MAST_BASE,
                Power.mast(Loadout.of(List.of(Ability.WIRELESS))));
    }
}
