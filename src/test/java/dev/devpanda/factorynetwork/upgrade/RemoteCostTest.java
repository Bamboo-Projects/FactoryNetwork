package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.network.Power;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What remote access costs — and that it does not annoy.
 *
 * <p>A battery that is empty in the middle of the work turns a tool into a
 * chore. These values are the line in between, and they stand here so that a
 * later reach for the numbers is noticed.
 */
class RemoteCostTest {

    /** Twenty minutes are one session; that long a battery must last. */
    private static final int SESSION_TICKS = 20 * 60 * 20;

    @Test
    @DisplayName("The smaller device lasts a session")
    void theSmallDeviceLastsASession() {
        // 200_000 FE is the capacity of the Wireless Terminal.
        int lasts = 200_000 / Math.max(1, Power.REMOTE_TICK);
        assertTrue(lasts > SESSION_TICKS,
                "der Akku hält nur " + lasts / 20 / 60 + " Minuten offenes Fenster");
    }

    @Test
    @DisplayName("Opening costs noticeably more than one tick")
    void openingCostsMoreThanATick() {
        // Otherwise it would pay to fold the window open and shut constantly
        // instead of leaving it open — and no one should be rewarded for
        // clicking.
        assertTrue(Power.REMOTE_OPEN > Power.REMOTE_TICK * 20,
                "Öffnen ist billiger als eine Sekunde offen lassen");
    }

    @Test
    @DisplayName("An action costs more than watching")
    void doingCostsMoreThanWatching() {
        assertTrue(Power.REMOTE_ACTION > Power.REMOTE_TICK,
                "eine Handlung kostet nicht mehr als ein Tick Zuschauen");
    }

    @Test
    @DisplayName("But a hundred actions do not empty a full battery")
    void aHundredActionsDoNotEmptyIt() {
        // Whoever tidies up from afar moves many stacks. If the battery runs
        // empty in the process, remote access is useless for exactly what one
        // builds it for.
        assertTrue(100 * Power.REMOTE_ACTION < 200_000 / 4,
                "hundert Handlungen fressen mehr als ein Viertel des Akkus");
    }
}
