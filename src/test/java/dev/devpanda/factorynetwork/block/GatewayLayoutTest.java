package dev.devpanda.factorynetwork.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht darüber, dass der Torbogen im Modell und in der Trefferfläche
 * derselbe ist.
 *
 * <p>Dieselbe Aufgabe wie {@link CableLayoutTest}, nur für den Block, bei dem
 * es zuerst auffiele: Ein Gateway ist zu zwei Dritteln Luft. Wer neben die
 * Ecksäule greift und trotzdem den Block trifft, merkt das sofort — und wer
 * hindurchgreifen will und am Nichts hängenbleibt, auch.
 */
class GatewayLayoutTest {

    private static final Path MODEL = Path.of(
            "src/main/resources/assets/factorynetwork/models/block/gateway.json");

    private static String model() throws IOException {
        assertTrue(Files.exists(MODEL), "gateway.json fehlt — tools/assets.py laufen lassen");
        return Files.readString(MODEL, StandardCharsets.UTF_8).replaceAll("[ \t\r\n]+", "");
    }

    @Test
    @DisplayName("Jeder Kasten der Trefferfläche steht so auch im Modell")
    void everyBoxIsInTheModel() throws IOException {
        String json = model();
        for (int[] box : GatewayLayout.boxes()) {
            String pair = "\"from\":[%d,%d,%d],\"to\":[%d,%d,%d]"
                    .formatted(box[0], box[1], box[2], box[3], box[4], box[5]);
            assertTrue(json.contains(pair),
                    "gateway.json kennt diesen Kasten nicht: " + pair);
        }
    }

    @Test
    @DisplayName("Und das Modell hat keinen Kasten darüber hinaus")
    void theModelHasNoExtraBox() throws IOException {
        String json = model();
        int found = json.split("\"from\":", -1).length - 1;
        assertEquals(GatewayLayout.boxes().size(), found,
                "Modell und GatewayLayout zählen verschieden viele Kästen");
    }

    @Test
    @DisplayName("Das Modell hat eigene Kästen und erbt keinen Würfel mehr")
    void theModelIsNoLongerACube() throws IOException {
        String json = model();
        assertTrue(json.contains("\"elements\":["), "gateway.json hat keine Kästen");
        assertFalse(json.contains("cube_all"),
                "gateway.json hängt wieder an einem Würfel");
    }

    @Test
    @DisplayName("Kein Kasten ragt aus dem Block heraus")
    void everyBoxStaysInsideTheBlock() {
        for (int[] box : GatewayLayout.boxes()) {
            for (int i = 0; i < 3; i++) {
                assertTrue(box[i] >= 0 && box[i] < box[i + 3] && box[i + 3] <= 16,
                        "Kasten außerhalb von 0 bis 16: Achse " + i);
            }
        }
    }

    @Test
    @DisplayName("Der Durchgang bleibt offen")
    void thePassageStaysOpen() {
        // Sockel, Durchgang, Sturz — in dieser Reihenfolge und mit Luft
        // dazwischen. Ohne diese Probe könnte jemand FOOT und HEAD
        // aneinanderschieben und hätte wieder einen vollen Würfel.
        assertTrue(GatewayLayout.FOOT < GatewayLayout.SHOULDER,
                "die Schultern fangen unter dem Sockel an");
        assertTrue(GatewayLayout.SHOULDER < GatewayLayout.HEAD,
                "die Schultern reichen über den Sturz hinaus");
        assertTrue(GatewayLayout.POST < GatewayLayout.REACH,
                "die Schultern ragen nicht über die Ecksäulen hinaus");
        assertTrue(2 * GatewayLayout.REACH < 16,
                "die Schultern zweier Seiten treffen sich in der Mitte");
        assertTrue(GatewayLayout.FOOT + GatewayLayout.GLOW_HIGH
                        < GatewayLayout.HEAD - GatewayLayout.GLOW_HIGH,
                "die beiden Leuchtstreifen liegen aufeinander");
    }

    @Test
    @DisplayName("Die Leuchtstreifen reichen weiter nach außen als die Ecksäulen")
    void theGlowReachesPastThePosts() {
        // Genau daran ist der erste Entwurf gescheitert: Eine Säule in der
        // Mitte war so breit wie die Ecksäule davor und deshalb aus der
        // Richtung, aus der man einen Block zuerst sieht, nie zu sehen.
        List<int[]> boxes = GatewayLayout.boxes();
        assertFalse(boxes.isEmpty());
        int span = (16 - GatewayLayout.GLOW) - GatewayLayout.GLOW;
        assertTrue(span > GatewayLayout.POST,
                "der Leuchtstreifen verschwindet hinter der Ecksäule");
    }
}
