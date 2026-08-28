package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Über eine Dimensionsgrenze reicht nur die Grenzenlos-Karte.
 *
 * <p><b>Warum die Entfernung dort nicht zählt:</b> Zwischen zwei Dimensionen
 * gibt es keinen Abstand, den man messen könnte. Der Nether liegt nicht
 * hundert Blöcke von der Oberwelt entfernt — er liegt daneben und zugleich
 * nirgends. Eine Rechnung mit Koordinaten aus zwei Welten ergäbe eine Zahl
 * ohne Bedeutung.
 */
class DimensionReachTest {

    private static final Loadout BARE = Loadout.of(List.of());
    private static final Loadout MANY_CARDS =
            Loadout.of(List.of(Card.RANGE, Card.RANGE, Card.RANGE, Card.RANGE));
    private static final Loadout INFINITE = Loadout.of(List.of(Card.INFINITY));

    @Test
    @DisplayName("In derselben Dimension entscheidet weiter die Entfernung")
    void withinOneWorldDistanceStillRules() {
        assertTrue(Range.covers(BARE, BARE, true, 5));
        assertFalse(Range.covers(BARE, BARE, true, 5000));
    }

    @Test
    @DisplayName("Ohne Grenzenlos-Karte endet das Netz an der Dimensionsgrenze")
    void withoutTheCardTheWorldEnds() {
        // Auch vier Karten helfen nicht: Reichweite ist eine Strecke, und
        // eine Dimensionsgrenze ist keine.
        assertFalse(Range.covers(MANY_CARDS, MANY_CARDS, false, 0));
        assertFalse(Range.covers(MANY_CARDS, MANY_CARDS, false, 1));
    }

    @Test
    @DisplayName("Mit ihr gilt sie überall")
    void withTheCardItReachesEverywhere() {
        assertTrue(Range.covers(INFINITE, BARE, false, 0));
        assertTrue(Range.covers(INFINITE, BARE, false, 1_000_000));
    }

    @Test
    @DisplayName("Die Karte muss im Mast stecken, nicht im Gerät")
    void theCardBelongsInTheMast() {
        // Sie ist die teuerste Karte im Spiel und hebt die Grenze für das
        // ganze Netz. Steckte sie im Gerät, hätte jeder Spieler seine eigene
        // — und der Mast wäre nicht mehr die Entscheidung.
        assertFalse(Range.covers(BARE, INFINITE, false, 0));
    }
}
