package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the network has moved over time.
 *
 * <p><b>Measured on the server, not in the window.</b> A history that the
 * client records itself has gaps as soon as someone closes the window — and
 * that is exactly when one later cares about why nothing ran at night.
 */
class TrafficHistoryTest {

    @Test
    @DisplayName("Fresh, everything is empty")
    void freshIsEmpty() {
        TrafficHistory history = new TrafficHistory();
        assertEquals(0, history.total());
        assertTrue(history.perSecond().isEmpty(), "ein frischer Verlauf hat schon Werte");
    }

    @Test
    @DisplayName("What is recorded counts toward the total")
    void whatIsRecordedCounts() {
        TrafficHistory history = new TrafficHistory();
        history.record("brecher", 30);
        history.record("ofen", 12);
        history.record("brecher", 8);
        assertEquals(50, history.total());
        assertEquals(38, history.byDevice().get("brecher"),
                "die beiden Buchungen desselben Geräts zählen nicht zusammen");
    }

    @Test
    @DisplayName("One point per second, not per tick")
    void oneSampleEachSecond() {
        // Twenty points per second would be twelve hundred in a minute — for
        // a chart two hundred pixels wide pointless, and expensive over the
        // wire.
        TrafficHistory history = new TrafficHistory();
        for (int tick = 0; tick < Bandwidth.TICKS_PER_SECOND; tick++) {
            history.record("brecher", 5);
            history.tick();
        }
        assertEquals(1, history.perSecond().size(), "nach zwanzig Ticks ein Punkt");
        assertEquals(100, history.perSecond().get(0), "der Punkt trägt die Summe der Sekunde");
    }

    @Test
    @DisplayName("The history has an end")
    void theHistoryHasAnEnd() {
        // A history that grows forever is a memory leak with a chart.
        TrafficHistory history = new TrafficHistory();
        for (int sekunde = 0; sekunde < TrafficHistory.SAMPLES + 30; sekunde++) {
            history.record("brecher", 1);
            for (int tick = 0; tick < Bandwidth.TICKS_PER_SECOND; tick++) {
                history.tick();
            }
        }
        assertEquals(TrafficHistory.SAMPLES, history.perSecond().size());
    }

    @Test
    @DisplayName("And the ranking is at the top")
    void theTopConsumersComeFirst() {
        // The question is "what eats the most", not "what is there".
        TrafficHistory history = new TrafficHistory();
        history.record("klein", 5);
        history.record("gross", 500);
        history.record("mittel", 50);
        var rang = history.top(2);
        assertEquals(2, rang.size(), "die Rangliste ist nicht begrenzt");
        assertEquals("gross", rang.get(0).name());
        assertEquals("mittel", rang.get(1).name());
    }
}
