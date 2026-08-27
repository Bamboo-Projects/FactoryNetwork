package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.network.Power;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was ein Mast kostet — und dass Reichweite eine Entscheidung bleibt.
 */
class MastCostTest {

    @Test
    @DisplayName("Ein Mast ohne Karten zieht wenig")
    void aBareMastIsCheap() {
        assertEquals(Power.MAST_BASE, Power.mast(Loadout.of(List.of())));
    }

    @Test
    @DisplayName("Jede Karte kostet Strom")
    void everyCardCosts() {
        // Ohne das wäre Reichweite ein Häkchen und keine Entscheidung: Wer
        // vier Karten hat, steckt vier hinein, und niemand überlegt.
        int bare = Power.mast(Loadout.of(List.of()));
        int full = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        assertTrue(full > bare, "vier Karten kosten nicht mehr als keine");
        assertEquals(bare + 4 * Power.MAST_PER_CARD, full);
    }

    @Test
    @DisplayName("Die Grenzenlos-Karte kostet am meisten")
    void infinityCostsMost() {
        int full = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        int endgame = Power.mast(Loadout.of(List.of(Card.INFINITY)));
        assertTrue(endgame > full,
                "unbegrenzt kostet weniger als vier Karten — dann nimmt sie jeder");
    }

    @Test
    @DisplayName("Ein Modul kostet nichts")
    void aModuleIsFree() {
        // Es hebt keinen Wert, also treibt es auch keinen Verbrauch. Was
        // Strom kostet, ist Reichweite — und die kommt aus Karten.
        assertEquals(Power.MAST_BASE,
                Power.mast(Loadout.of(List.of(Ability.WIRELESS))));
    }
}
