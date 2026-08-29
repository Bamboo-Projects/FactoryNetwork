package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was das Netz über die Zeit bewegt hat.
 *
 * <p><b>Auf dem Server gemessen, nicht im Fenster.</b> Ein Verlauf, den der
 * Client selbst mitschreibt, hat Lücken, sobald jemand das Fenster schließt —
 * und genau dann interessiert er sich später dafür, warum nachts nichts lief.
 */
class TrafficHistoryTest {

    @Test
    @DisplayName("Frisch ist alles leer")
    void freshIsEmpty() {
        TrafficHistory history = new TrafficHistory();
        assertEquals(0, history.total());
        assertTrue(history.perSecond().isEmpty(), "ein frischer Verlauf hat schon Werte");
    }

    @Test
    @DisplayName("Was gebucht wird, zählt zur Gesamtmenge")
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
    @DisplayName("Je Sekunde ein Punkt, nicht je Tick")
    void oneSampleEachSecond() {
        // Zwanzig Punkte je Sekunde wären in einer Minute zwölfhundert — für
        // ein Diagramm von zweihundert Pixeln Breite sinnlos, und über die
        // Leitung teuer.
        TrafficHistory history = new TrafficHistory();
        for (int tick = 0; tick < Bandwidth.TICKS_PER_SECOND; tick++) {
            history.record("brecher", 5);
            history.tick();
        }
        assertEquals(1, history.perSecond().size(), "nach zwanzig Ticks ein Punkt");
        assertEquals(100, history.perSecond().get(0), "der Punkt trägt die Summe der Sekunde");
    }

    @Test
    @DisplayName("Der Verlauf hat ein Ende")
    void theHistoryHasAnEnd() {
        // Ein Verlauf, der ewig wächst, ist ein Speicherleck mit Diagramm.
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
    @DisplayName("Und die Rangliste steht oben")
    void theTopConsumersComeFirst() {
        // Die Frage lautet „was frisst am meisten", nicht „was gibt es".
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
