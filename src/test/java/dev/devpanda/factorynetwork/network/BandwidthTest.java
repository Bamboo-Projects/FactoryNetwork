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
    @DisplayName("Ein gewöhnliches Kabel trägt ein Kilobyte je Sekunde")
    void aPlainCableCarriesOneKilobyte() {
        assertEquals(1000, Bandwidth.THIN * TICKS,
                "ein gewöhnliches Kabel trägt " + Bandwidth.THIN * TICKS + " B/s");
    }

    @Test
    @DisplayName("Ein dichtes zehn")
    void aDenseCableCarriesTen() {
        assertEquals(10_000, Bandwidth.DENSE * TICKS);
    }

    @Test
    @DisplayName("Und die Anzeige sagt es in ganzen Kilobyte")
    void theLabelReadsAsKilobytes() {
        // Wer „500 B je Tick" liest, muss rechnen. Wer „10 KB/s" liest,
        // nicht — und genau dafür gibt es die Einheit.
        assertEquals("1 KB/s", Bandwidth.perSecond(Bandwidth.THIN));
        assertEquals("10 KB/s", Bandwidth.perSecond(Bandwidth.DENSE));
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
    @DisplayName("Was nicht leitet, begrenzt auch nichts")
    void whatDoesNotCarryDoesNotLimit() {
        assertEquals(Integer.MAX_VALUE, Bandwidth.UNLIMITED);
        assertTrue(Bandwidth.DENSE > Bandwidth.THIN,
                "das dichte Kabel muss mehr tragen");
    }
}
