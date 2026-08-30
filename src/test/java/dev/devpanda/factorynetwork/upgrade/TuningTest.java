package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was Ausbauten aus einer Maschine machen.
 *
 * <p>Die Rechnung steht ohne Minecraft-Typen da und lässt sich deshalb hier
 * prüfen statt im Spiel. Das ist kein Zufall, sondern der Grund, warum das
 * Paket {@code upgrade} so geschnitten ist.
 */
class TuningTest {

    private static Loadout with(Card card, int count) {
        return Loadout.ofCounts(Map.of(card, count));
    }

    @Test
    @DisplayName("Ohne Karten bleibt alles, wie das Rezept es sagt")
    void plainRecipeIsUntouched() {
        Tuned tuned = Tuning.of(Loadout.of(java.util.List.of()), 60, 1200);
        assertEquals(60, tuned.ticks());
        assertEquals(1200, tuned.energy());
        assertEquals(1, tuned.batch());
    }

    @Test
    @DisplayName("Beschleunigung kostet Zeit und bringt Strom auf die Rechnung")
    void accelerationTradesEnergyForTime() {
        Tuned one = Tuning.of(with(Card.ACCELERATION, 1), 100, 1000);
        assertEquals(80, one.ticks(), "eine Karte nimmt ein Fünftel der Zeit");
        assertEquals(1500, one.energy(), "und legt die Hälfte auf den Strom");

        Tuned four = Tuning.of(with(Card.ACCELERATION, 4), 100, 1000);
        assertEquals(41, four.ticks());
        assertEquals(3000, four.energy());
        // Der eigentliche Punkt: Das Tempo wächst langsamer als der Preis.
        assertTrue(100.0 / four.ticks() < four.energy() / 1000.0,
                "vier Karten müssen teurer sein, als sie schnell machen");
    }

    @Test
    @DisplayName("Der Stapel bringt Menge, keine Zeit — und kostet je Stück")
    void batchBringsAmountAtFullPrice() {
        Tuned tuned = Tuning.of(with(Card.BATCH, 2), 60, 1200);
        assertEquals(3, tuned.batch(), "zwei Karten machen drei Werkstücke");
        assertEquals(60, tuned.ticks(), "die Zeit bleibt, das ist der Gewinn");
        assertEquals(3600, tuned.energy(), "je Werkstück derselbe Strom");
    }

    @Test
    @DisplayName("Beides zusammen: Zeit von der einen, Menge von der anderen")
    void bothCardsCombine() {
        Tuned tuned = Tuning.of(
                Loadout.ofCounts(Map.of(Card.ACCELERATION, 2, Card.BATCH, 1)),
                100, 1000);
        assertEquals(64, tuned.ticks());
        assertEquals(2, tuned.batch());
        // Zweimal Beschleunigung: doppelter Strom. Zweimal Menge: noch einmal
        // doppelt. Die Faktoren multiplizieren sich, sie addieren sich nicht.
        assertEquals(4000, tuned.energy());
    }

    @Test
    @DisplayName("Über acht Karten wirkt keine mehr")
    void tooManyCardsStopCounting() {
        Tuned eight = Tuning.of(with(Card.ACCELERATION, 8), 100, 1000);
        Tuned sixty = Tuning.of(with(Card.ACCELERATION, 64), 100, 1000);
        assertEquals(eight.ticks(), sixty.ticks(),
                "sonst arbeitet ein voller Stapel Karten in null Ticks");
        assertEquals(eight.energy(), sixty.energy(),
                "und der Deckel muss auch für den Preis gelten");
        assertTrue(eight.ticks() >= 1, "kein Rezept läuft in null Ticks");
    }

    @Test
    @DisplayName("Ein sehr kurzes Rezept fällt nicht unter einen Tick")
    void shortRecipesKeepOneTick() {
        assertEquals(1, Tuning.of(with(Card.ACCELERATION, 8), 2, 100).ticks());
    }
}
