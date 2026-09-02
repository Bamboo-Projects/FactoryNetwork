package dev.devpanda.factorynetwork.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is peculiar to the gateway arch.
 *
 * <p>That model and hitbox have the same boxes is checked by
 * {@link MachineLayoutTest} for all reworked blocks together. Here stands
 * only what makes this one special — and what two designs failed at.
 */
class GatewayLayoutTest {

    @Test
    @DisplayName("The passage stays open")
    void thePassageStaysOpen() {
        // Base, passage, lintel — in this order and with air in between.
        // Without this check someone could push FOOT and HEAD together and
        // would have a full cube again.
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
    @DisplayName("The glow bands lie on the outside and not in the passage")
    void theGlowSitsOnTheHull() {
        // Two designs failed at this: a column in the middle, as wide as the
        // corner column in front of it, and a strip on the base that stuck out
        // just one block pixel above it. Both were invisible from the
        // direction from which one first sees a block. What lies on the
        // outside is always visible — and outside means: from 0 to 16.
        List<int[]> boxes = GatewayLayout.boxes();
        assertFalse(boxes.isEmpty());
        int[] lower = boxes.get(1);
        assertEquals(0, lower[0], "das untere Leuchtband fängt nicht an der Blockkante an");
        assertEquals(0, lower[2], "das untere Leuchtband fängt nicht an der Blockkante an");
        assertEquals(16, lower[3], "das untere Leuchtband reicht nicht bis zur Blockkante");
        assertEquals(16, lower[5], "das untere Leuchtband reicht nicht bis zur Blockkante");
    }
}
