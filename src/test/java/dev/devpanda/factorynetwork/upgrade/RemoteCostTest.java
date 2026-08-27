package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.network.Power;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was der Fernzugriff kostet — und dass er nicht nervt.
 *
 * <p>Ein Akku, der mitten in der Arbeit leer ist, macht aus einem Werkzeug
 * eine Pflicht. Diese Werte sind die Grenze dazwischen, und sie stehen hier,
 * damit ein späterer Griff an die Zahlen auffällt.
 */
class RemoteCostTest {

    /** Zwanzig Minuten sind eine Sitzung; so lange muss ein Akku halten. */
    private static final int SESSION_TICKS = 20 * 60 * 20;

    @Test
    @DisplayName("Das kleinere Gerät hält eine Sitzung durch")
    void theSmallDeviceLastsASession() {
        // 200_000 FE ist die Kapazität des Wireless Terminals.
        int lasts = 200_000 / Math.max(1, Power.REMOTE_TICK);
        assertTrue(lasts > SESSION_TICKS,
                "der Akku hält nur " + lasts / 20 / 60 + " Minuten offenes Fenster");
    }

    @Test
    @DisplayName("Öffnen kostet spürbar mehr als ein Tick")
    void openingCostsMoreThanATick() {
        // Sonst lohnte es, das Fenster ständig auf- und zuzuklappen, statt
        // es offen zu lassen — und niemand soll fürs Klicken belohnt werden.
        assertTrue(Power.REMOTE_OPEN > Power.REMOTE_TICK * 20,
                "Öffnen ist billiger als eine Sekunde offen lassen");
    }

    @Test
    @DisplayName("Eine Handlung kostet mehr als Zuschauen")
    void doingCostsMoreThanWatching() {
        assertTrue(Power.REMOTE_ACTION > Power.REMOTE_TICK,
                "eine Handlung kostet nicht mehr als ein Tick Zuschauen");
    }

    @Test
    @DisplayName("Aber hundert Handlungen leeren keinen vollen Akku")
    void aHundredActionsDoNotEmptyIt() {
        // Wer aus der Ferne aufräumt, bewegt viele Stapel. Wenn dabei der
        // Akku leerläuft, ist der Fernzugriff für genau das unbrauchbar,
        // wofür man ihn baut.
        assertTrue(100 * Power.REMOTE_ACTION < 200_000 / 4,
                "hundert Handlungen fressen mehr als ein Viertel des Akkus");
    }
}
