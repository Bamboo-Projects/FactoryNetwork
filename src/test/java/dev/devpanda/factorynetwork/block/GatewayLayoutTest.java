package dev.devpanda.factorynetwork.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was am Torbogen eigen ist.
 *
 * <p>Dass Modell und Trefferfläche dieselben Kästen haben, prüft
 * {@link MachineLayoutTest} für alle umgebauten Blöcke zusammen. Hier steht
 * nur, was diesen einen ausmacht — und woran zwei Entwürfe gescheitert sind.
 */
class GatewayLayoutTest {

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
        assertTrue(GatewayLayout.FOOT < GatewayLayout.HEAD,
                "Sockel und Sturz stoßen aneinander");
        assertTrue(GatewayLayout.GLOW < GatewayLayout.FOOT,
                "das Leuchtband ist dicker als der Sockel, der es trägt");
    }

    @Test
    @DisplayName("Die Leuchtbänder liegen außen und nicht im Durchgang")
    void theGlowSitsOnTheHull() {
        // Daran sind zwei Entwürfe gescheitert: eine Säule in der Mitte, so
        // breit wie die Ecksäule davor, und ein Streifen auf dem Sockel, der
        // gerade einen Blockpixel darüber hinausragte. Beide waren aus der
        // Richtung, aus der man einen Block zuerst sieht, nicht zu sehen.
        // Was außen liegt, sieht man immer — und außen heißt: von 0 bis 16.
        List<int[]> boxes = GatewayLayout.boxes();
        assertFalse(boxes.isEmpty());
        int[] lower = boxes.get(1);
        assertEquals(0, lower[0], "das untere Leuchtband fängt nicht an der Blockkante an");
        assertEquals(0, lower[2], "das untere Leuchtband fängt nicht an der Blockkante an");
        assertEquals(16, lower[3], "das untere Leuchtband reicht nicht bis zur Blockkante");
        assertEquals(16, lower[5], "das untere Leuchtband reicht nicht bis zur Blockkante");
    }
}
