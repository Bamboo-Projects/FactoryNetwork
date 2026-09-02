package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a cable carries — in a unit one knows.
 *
 * <p><b>"64 per tick" is a number without an anchor.</b> Is that a lot? What
 * does it suffice for? One learns it by trying it out. „1 KB/s" carries its
 * feel along: everyone knows that this is slow and that ten of them are more.
 */
class BandwidthTest {

    /** Twenty ticks are one second. */
    private static final int TICKS = 20;

    @Test
    @DisplayName("A cable carries twenty stacks per tick")
    void aCableCarriesTwentyStacks() {
        // Counted in items, because that is what a worker moves: 1280 per
        // tick. Up to 30.08. it was 128, and next to it stood a dense cable
        // with ten times that. The larger number stayed — they are
        // fibre-optic cables.
        assertEquals(1280, Bandwidth.CABLE / Bandwidth.PER_ITEM);
    }

    @Test
    @DisplayName("The controller carries one cable, the extension half of one")
    void theControllerCarriesOneCable() {
        // The limit that divides everything: it does not stand as its own
        // number, but is derived from the cable. A number maintained in two
        // places drifts.
        assertEquals(Bandwidth.CABLE, Bandwidth.CONTROLLER);
        assertEquals(Bandwidth.CABLE / 2, Bandwidth.EXTENSION);
        assertEquals(Bandwidth.CONTROLLER + 2 * Bandwidth.EXTENSION,
                Bandwidth.ofController(2));
    }

    @Test
    @DisplayName("And the display says it in megabytes")
    void theLabelReadsAsMegabytes() {
        // „25600000 B/s" is no help, „25,6 MB/s" is — and the order of
        // magnitude sounds like networking, because it is one.
        assertEquals("25,6 MB/s", Bandwidth.perSecond(Bandwidth.CABLE));
        assertEquals("12,8 MB/s", Bandwidth.perSecond(Bandwidth.EXTENSION));
    }

    @Test
    @DisplayName("An item weighs a kilobyte")
    void anItemWeighsAKilobyte() {
        // <b>Not one byte.</b> With one byte per item a cable would carry
        // 1 KB/s — a number that looks ridiculous next to "fibre optic".
        // With real fibre-optic numbers, in turn, the limit would never be
        // reachable: a very large network moves six thousand items per tick,
        // real fibre optic would carry sixty-two million.
        assertEquals(1000, Bandwidth.PER_ITEM);
    }

    @Test
    @DisplayName("Small amounts stay in bytes")
    void smallAmountsStayInBytes() {
        // „0 KB/s" for a worker that moves something would be a lie through
        // rounding. Below a kilobyte the display counts in bytes.
        assertEquals("100 B/s", Bandwidth.perSecond(5));
        assertEquals("0 B/s", Bandwidth.perSecond(0));
    }

    @Test
    @DisplayName("In between with one decimal place")
    void inBetweenGetsOneDecimal() {
        // 30 per tick are 600 B/s — but 75 are 1500, and „1 KB/s" would lose
        // half of the information.
        assertEquals("1,5 KB/s", Bandwidth.perSecond(75));
    }

    @Test
    @DisplayName("A total climbs through the units")
    void totalsClimbThroughTheUnits() {
        // A network that runs for a week moves gigabytes — and
        // "14603219 B" is no help, but a string of digits.
        assertEquals("340 B", Bandwidth.total(340));
        assertEquals("12,4 KB", Bandwidth.total(12_400));
        assertEquals("3,1 MB", Bandwidth.total(3_140_000));
        assertEquals("2,0 GB", Bandwidth.total(2_000_000_000L));
        assertEquals("1,5 TB", Bandwidth.total(1_500_000_000_000L));
    }

    @Test
    @DisplayName("And stops at terabytes")
    void itStopsAtTerabytes() {
        // A petabyte would be a unit for a number no one reaches — and a step
        // no one has ever seen confuses more than a large number would.
        assertTrue(Bandwidth.total(9_000_000_000_000_000L).endsWith("TB"));
    }

    @Test
    @DisplayName("Zero is zero, not 0,0 B")
    void zeroIsZero() {
        assertEquals("0 B", Bandwidth.total(0));
    }

    @Test
    @DisplayName("What does not carry also limits nothing")
    void whatDoesNotCarryDoesNotLimit() {
        assertEquals(Integer.MAX_VALUE, Bandwidth.UNLIMITED);
        // And the cap takes hold instead of running into the negative range:
        // without it the largest bandwidth in the world would be a number
        // below zero.
        assertEquals(Bandwidth.UNLIMITED, Bandwidth.ofController(Integer.MAX_VALUE));
    }
}
