package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Regel, die das ganze Ausbausystem trägt: Ein Modul gibt eine
 * Fähigkeit, eine Karte hebt einen Wert.
 */
class LoadoutTest {

    @Test
    @DisplayName("Ohne Bestückung kann ein Gerät nichts und hat keinen Wert")
    void emptyMeansNothing() {
        Loadout empty = Loadout.of(List.of());
        assertFalse(empty.has(Ability.WIRELESS));
        assertEquals(0, empty.value(Stat.RANGE));
        assertFalse(empty.unlimited(Stat.RANGE));
    }

    @Test
    @DisplayName("Ein Modul schaltet seine Fähigkeit frei und sonst keine")
    void aModuleUnlocksItsOwnAbility() {
        Loadout one = Loadout.of(List.of(Ability.WIRELESS));
        assertTrue(one.has(Ability.WIRELESS));
        // Und es hebt keinen Wert: Dafür sind Karten da.
        assertEquals(0, one.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Gleiche Karten addieren sich")
    void equalCardsAddUp() {
        assertEquals(8, Loadout.of(List.of(Card.RANGE)).value(Stat.RANGE));
        assertEquals(24, Loadout.of(
                List.of(Card.RANGE, Card.RANGE, Card.RANGE)).value(Stat.RANGE));
    }

    @Test
    @DisplayName("Die Grenzenlos-Karte hebt die Grenze auf, statt sie zu heben")
    void infinityLiftsTheLimit() {
        Loadout endgame = Loadout.of(List.of(Card.RANGE, Card.INFINITY));
        assertTrue(endgame.unlimited(Stat.RANGE));
        // Der Zahlenwert bleibt daneben stehen und wird nicht gebraucht —
        // wer unlimited fragt, fragt value nicht mehr.
        assertEquals(8, endgame.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Jede Karte wirkt in genau einem Punkt")
    void everyCardHitsExactlyOneStat() {
        // Ohne diese Probe wächst hier über die Zeit ein zweites Regelwerk:
        // eine Karte, die drei Dinge zugleich tut, und niemand weiß mehr,
        // was ein Steckplatz kostet.
        for (Card card : Card.values()) {
            assertTrue(card.stat() != null, card + " hebt keinen Wert");
        }
    }

    @Test
    @DisplayName("Module und Karten teilen sich die Steckplätze")
    void bothKindsShareTheSlots() {
        Loadout mixed = Loadout.of(List.of(Ability.WIRELESS, Card.RANGE, Card.RANGE));
        assertTrue(mixed.has(Ability.WIRELESS));
        assertEquals(16, mixed.value(Stat.RANGE));
        assertEquals(3, mixed.installed().size());
    }
}
