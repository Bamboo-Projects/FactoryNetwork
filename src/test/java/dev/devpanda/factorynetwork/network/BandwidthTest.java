package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was ein Kabel trägt — in einer Einheit, die man kennt.
 *
 * <p><b>„64 je Tick" ist eine Zahl ohne Anker.</b> Ist das viel? Wofür
 * reicht es? Man lernt es durch Ausprobieren. „1 KB/s" trägt sein Gefühl
 * mit: Jeder weiß, dass das langsam ist und dass zehn davon mehr sind.
 */
class BandwidthTest {

    /** Zwanzig Ticks sind eine Sekunde. */
    private static final int TICKS = 20;

    @Test
    @DisplayName("Ein gewöhnliches Kabel trägt zwei Stapel je Tick")
    void aPlainCableCarriesTwoStacks() {
        // In Gegenständen gerechnet, denn das ist, was ein Worker bewegt:
        // 128 je Tick, also zwei Stapel.
        assertEquals(128, Bandwidth.THIN / Bandwidth.PER_ITEM);
    }

    @Test
    @DisplayName("Ein dichtes zehnmal so viel")
    void aDenseCableCarriesTenTimes() {
        assertEquals(10 * Bandwidth.THIN, Bandwidth.DENSE);
        assertEquals(1280, Bandwidth.DENSE / Bandwidth.PER_ITEM);
    }

    @Test
    @DisplayName("Und die Anzeige sagt es in Megabyte")
    void theLabelReadsAsMegabytes() {
        // „2560000 B/s" ist keine Auskunft, „2,6 MB/s" schon — und die
        // Größenordnung klingt nach Netzwerk, weil sie eine ist.
        assertEquals("2,6 MB/s", Bandwidth.perSecond(Bandwidth.THIN));
        assertEquals("25,6 MB/s", Bandwidth.perSecond(Bandwidth.DENSE));
    }

    @Test
    @DisplayName("Ein Gegenstand wiegt ein Kilobyte")
    void anItemWeighsAKilobyte() {
        // <b>Nicht ein Byte.</b> Mit einem Byte je Gegenstand trüge ein
        // Kabel 1 KB/s — eine Zahl, die neben „Glasfaser" lächerlich wirkt.
        // Mit echten Glasfaserzahlen wiederum wäre die Grenze nie
        // erreichbar: Ein sehr großes Netz bewegt sechstausend Gegenstände
        // je Tick, echte Glasfaser trüge zweiundsechzig Millionen.
        assertEquals(1000, Bandwidth.PER_ITEM);
    }

    @Test
    @DisplayName("Kleine Mengen bleiben in Byte")
    void smallAmountsStayInBytes() {
        // „0 KB/s" für einen Worker, der etwas bewegt, wäre eine Lüge durch
        // Rundung. Unterhalb eines Kilobyte zählt die Anzeige in Byte.
        assertEquals("100 B/s", Bandwidth.perSecond(5));
        assertEquals("0 B/s", Bandwidth.perSecond(0));
    }

    @Test
    @DisplayName("Dazwischen mit einer Nachkommastelle")
    void inBetweenGetsOneDecimal() {
        // 30 je Tick sind 600 B/s — aber 75 sind 1500, und „1 KB/s" verlöre
        // die Hälfte der Auskunft.
        assertEquals("1,5 KB/s", Bandwidth.perSecond(75));
    }

    @Test
    @DisplayName("Eine Gesamtmenge steigt durch die Einheiten")
    void totalsClimbThroughTheUnits() {
        // Ein Netz, das eine Woche läuft, bewegt Gigabyte — und
        // "14603219 B" ist keine Auskunft, sondern eine Zahlenreihe.
        assertEquals("340 B", Bandwidth.total(340));
        assertEquals("12,4 KB", Bandwidth.total(12_400));
        assertEquals("3,1 MB", Bandwidth.total(3_140_000));
        assertEquals("2,0 GB", Bandwidth.total(2_000_000_000L));
        assertEquals("1,5 TB", Bandwidth.total(1_500_000_000_000L));
    }

    @Test
    @DisplayName("Und hört bei Terabyte auf")
    void itStopsAtTerabytes() {
        // Petabyte wäre eine Einheit für eine Zahl, die niemand erreicht —
        // und eine Stufe, die niemand je gesehen hat, verwirrt mehr, als
        // eine große Zahl es täte.
        assertTrue(Bandwidth.total(9_000_000_000_000_000L).endsWith("TB"));
    }

    @Test
    @DisplayName("Null ist null, nicht 0,0 B")
    void zeroIsZero() {
        assertEquals("0 B", Bandwidth.total(0));
    }

    @Test
    @DisplayName("Was nicht leitet, begrenzt auch nichts")
    void whatDoesNotCarryDoesNotLimit() {
        assertEquals(Integer.MAX_VALUE, Bandwidth.UNLIMITED);
        assertTrue(Bandwidth.DENSE > Bandwidth.THIN,
                "das dichte Kabel muss mehr tragen");
    }
}
