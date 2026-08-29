package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Was ein Kabel trägt — in Byte je Sekunde.
 *
 * <p><b>Ein Gegenstand ist ein Byte.</b> Nicht weil ein Eisenbarren ein Byte
 * wäre, sondern weil es die einzige Zuordnung ist, die man sich merken kann.
 *
 * <p><b>Warum eine echte Einheit besser ist als eine Zahl.</b> „64 je Tick"
 * ist eine Zahl ohne Anker: Ist das viel? Wofür reicht es? Man lernt es durch
 * Ausprobieren. „1 KB/s" trägt sein Gefühl mit — jeder weiß, dass das langsam
 * ist und dass zehn davon mehr sind. Die Mod muss nichts erklären, was die
 * Welt schon erklärt hat.
 *
 * <p><b>Gerechnet wird je Tick, angezeigt je Sekunde.</b> Das Spiel läuft in
 * Ticks; ein Mensch denkt in Sekunden. Die Umrechnung steht an einer Stelle,
 * damit sie nicht an fünfen leicht verschieden passiert.
 */
public final class Bandwidth {

    /** Zwanzig Ticks sind eine Sekunde — die eine Stelle, an der das steht. */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * Ein gewöhnliches Kabel: ein Kilobyte je Sekunde.
     *
     * <p>Knapp ein Stapel je Tick — genug für jede einzelne Leitung, zu wenig
     * für eine Hauptader, an der zehn Worker ziehen.
     */
    public static final int THIN = 1000 / TICKS_PER_SECOND;

    /**
     * Ein dichtes: zehn Kilobyte.
     *
     * <p>Der Unterschied, für den man es baut. Wäre er klein, wäre das dichte
     * Kabel ein teureres Kabel, das dicker aussieht.
     */
    public static final int DENSE = 10_000 / TICKS_PER_SECOND;

    /**
     * Was nicht leitet, begrenzt auch nichts.
     *
     * <p>Der Controller, ein Laufwerk, ein Schrank: Sie sind Ziel und nicht
     * Strecke. Eine Grenze dort wäre eine zweite Grenze am selben Weg.
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * Was an dieser Stelle je Tick hindurchgeht.
     *
     * <p>Router, Gateway und Quantum-Brücke gehören zum dichten Kabel und
     * tragen so viel wie eines: Sie sind Leitung und kein Vermehrer.
     */
    public static int at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.RouterBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.GatewayBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
            return DENSE;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.CableBlock cable) {
            return cable.bandwidth();
        }
        return UNLIMITED;
    }

    /**
     * Eine Menge je Tick, lesbar je Sekunde.
     *
     * <p>Drei Stufen, und jede fängt einen Fall, den man im Spiel sieht:
     *
     * <ul>
     *   <li><b>Unter einem Kilobyte in Byte.</b> „0 KB/s" für einen Worker,
     *       der etwas bewegt, wäre eine Lüge durch Rundung.</li>
     *   <li><b>Krumme Kilobyte mit einer Stelle.</b> 1500 B/s als „1 KB/s"
     *       verlöre die Hälfte der Auskunft.</li>
     *   <li><b>Glatte ohne.</b> „10,0 KB/s" ist Rauschen.</li>
     * </ul>
     */
    public static String perSecond(int perTick) {
        long bytes = (long) perTick * TICKS_PER_SECOND;
        if (bytes < 1000) {
            return bytes + " B/s";
        }
        if (bytes % 1000 == 0) {
            return bytes / 1000 + " KB/s";
        }
        return String.format(Locale.GERMANY, "%.1f KB/s", bytes / 1000.0);
    }

    /** Was von einer Kapazität schon verbraucht ist: „0,4 von 1 KB/s". */
    /** Die Stufen, in denen eine Gesamtmenge gelesen wird. */
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    /**
     * Eine Gesamtmenge, lesbar: 340 B, 12,4 KB, 3,1 MB.
     *
     * <p><b>Nicht je Sekunde, sondern insgesamt.</b> Ein Netz, das eine Woche
     * läuft, bewegt Gigabyte — und "14603219 B" ist keine Auskunft, sondern
     * eine Zahlenreihe.
     *
     * <p>Tausend und nicht 1024: Wir sind bei Byte je Sekunde, und dort
     * rechnet die Welt dezimal. Ein KB/s ist tausend Byte, sonst wäre schon
     * die Umrechnung aus dem Tick krumm.
     */
    public static String total(long bytes) {
        double wert = bytes;
        int stufe = 0;
        while (wert >= 1000 && stufe < UNITS.length - 1) {
            wert /= 1000;
            stufe++;
        }
        if (stufe == 0) {
            return (long) wert + " " + UNITS[0];
        }
        // Eine Nachkommastelle: "12,4 MB" sagt genug, "12,437 MB" ist
        // Rauschen an einer Zahl, die ohnehin weiterläuft.
        return String.format(Locale.GERMANY, "%.1f %s", wert, UNITS[stufe]);
    }

    public static String usage(int usedPerTick, int capacityPerTick) {
        if (capacityPerTick >= UNLIMITED) {
            return perSecond(usedPerTick);
        }
        return perSecond(usedPerTick) + " von " + perSecond(capacityPerTick);
    }

    private Bandwidth() {
    }
}
