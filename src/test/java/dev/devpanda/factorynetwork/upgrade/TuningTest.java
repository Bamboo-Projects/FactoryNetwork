package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What upgrades make of a machine.
 *
 * <p>The calculation stands without Minecraft types and can therefore be
 * tested here instead of in the game. That is no accident, but the reason
 * the {@code upgrade} package is cut the way it is.
 */
class TuningTest {

    private static Loadout with(Card card, int count) {
        return Loadout.ofCounts(Map.of(card, count));
    }

    @Test
    @DisplayName("Without cards everything stays as the recipe says")
    void plainRecipeIsUntouched() {
        Tuned tuned = Tuning.of(Loadout.of(java.util.List.of()), 60, 1200);
        assertEquals(60, tuned.ticks());
        assertEquals(1200, tuned.energy());
        assertEquals(1, tuned.batch());
    }

    @Test
    @DisplayName("Acceleration costs time and puts power on the bill")
    void accelerationTradesEnergyForTime() {
        Tuned one = Tuning.of(with(Card.ACCELERATION, 1), 100, 1000);
        assertEquals(80, one.ticks(), "eine Karte nimmt ein Fünftel der Zeit");
        assertEquals(1500, one.energy(), "und legt die Hälfte auf den Strom");

        Tuned four = Tuning.of(with(Card.ACCELERATION, 4), 100, 1000);
        assertEquals(41, four.ticks());
        assertEquals(3000, four.energy());
        // The actual point: the speed grows slower than the price.
        assertTrue(100.0 / four.ticks() < four.energy() / 1000.0,
                "vier Karten müssen teurer sein, als sie schnell machen");
    }

    @Test
    @DisplayName("The batch brings amount, no time — and costs per piece")
    void batchBringsAmountAtFullPrice() {
        Tuned tuned = Tuning.of(with(Card.BATCH, 2), 60, 1200);
        assertEquals(3, tuned.batch(), "zwei Karten machen drei Werkstücke");
        assertEquals(60, tuned.ticks(), "die Zeit bleibt, das ist der Gewinn");
        assertEquals(3600, tuned.energy(), "je Werkstück derselbe Strom");
    }

    @Test
    @DisplayName("Both together: time from the one, amount from the other")
    void bothCardsCombine() {
        Tuned tuned = Tuning.of(
                Loadout.ofCounts(Map.of(Card.ACCELERATION, 2, Card.BATCH, 1)),
                100, 1000);
        assertEquals(64, tuned.ticks());
        assertEquals(2, tuned.batch());
        // Twice Acceleration: double the power. Twice amount: doubled once
        // more. The factors multiply, they do not add.
        assertEquals(4000, tuned.energy());
    }

    @Test
    @DisplayName("Beyond eight cards none has any effect")
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
    @DisplayName("A very short recipe does not drop below one tick")
    void shortRecipesKeepOneTick() {
        assertEquals(1, Tuning.of(with(Card.ACCELERATION, 8), 2, 100).ticks());
    }
}
