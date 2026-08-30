package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Was ein Kabel trägt — in Byte je Sekunde.
 *
 * <p><b>Ein Gegenstand ist ein Kilobyte.</b> Nicht weil ein Eisenbarren ein
 * Kilobyte wäre, sondern weil die Zahlen dann stimmen: Ein gewöhnliches Kabel
 * trägt 25,6 MB/s — eine Größenordnung, die nach Glasfaser klingt und
 * trotzdem spürbar ist.
 *
 * <p><b>Warum nicht die echten Zahlen.</b> Glasfaser trägt 10 Gbit/s, also
 * 62,5 Millionen Byte je Tick. Ein sehr großes Netz bewegt sechstausend
 * Gegenstände je Tick. Mit echten Zahlen wäre die Grenze nie erreichbar —
 * eine Grenze, die niemand spürt, ist Dekoration.
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

    /** Was ein Gegenstand wiegt: ein Kilobyte. */
    public static final int PER_ITEM = 1000;

    /**
     * Was ein Kabel trägt: 1280 Gegenstände je Tick, also 25,6 MB/s.
     *
     * <p><b>Eine Sorte, eine Zahl.</b> Bis zum 30.08. gab es zwei Kabel:
     * ein gewöhnliches mit 2,56 MB/s und ein dichtes mit dem Zehnfachen. Das
     * dichte war die Antwort auf eine Frage, die es nicht mehr gibt — es
     * bündelte Kanäle, und Kanäle gibt es seit dem 29.08. nicht mehr.
     *
     * <p>Geblieben ist die größere Zahl. Es sind Glasfaserkabel; eines, das
     * zwei Stapel je Tick trägt, wäre keine.
     */
    public static final int CABLE = 1280 * PER_ITEM;

    /**
     * Was nicht leitet, begrenzt auch nichts.
     *
     * <p>Ein Laufwerk, ein Schrank, ein Mast: Sie sind Ziel und nicht
     * Strecke. Eine Grenze dort wäre eine zweite Grenze am selben Weg.
     *
     * <p><b>Der Controller stand bis zum 30.08. in dieser Liste.</b> Er
     * gehört nicht hinein: Alles im Netz geht durch ihn, und damit ist er
     * Strecke — die eine, die jeder Weg gemeinsam hat.
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * Was ein Controller ohne Anbau trägt: so viel wie ein Kabel.
     *
     * <p><b>Er ist die Backplane.</b> In einem echten Netz kann jeder Port
     * Gigabit, aber der Switch trägt nur, was er trägt — wer mehr will,
     * kauft einen größeren oder steckt ein Modul dazu.
     *
     * <p><b>Warum genau ein Kabel.</b> Ein Netz mit einer Hauptader
     * läuft ohne Anbau vollständig. Wer verzweigt, merkt die Grenze — und das
     * ist der Moment, in dem man dazubaut. Eine Grenze, die man vom ersten
     * Tag an spürt, ist Schikane; eine, die man nie spürt, ist Dekoration.
     */
    public static final int CONTROLLER = CABLE;

    /**
     * Was ein Anbau dazulegt: ein halbes Kabel.
     *
     * <p>Weniger als ein ganzes, damit der zweite Anbau sich noch lohnt und
     * der sechste nicht albern wird.
     */
    public static final int EXTENSION = CABLE / 2;

    /**
     * Was ein Controller mit so vielen Anbauten trägt.
     *
     * <p>Die Deckelung ist keine Spielregel, sondern Arithmetik: Sehr viele
     * Anbauten liefen sonst über den Zahlenbereich, und aus der größten
     * Bandbreite der Welt würde eine negative.
     */
    public static int ofController(int extensions) {
        long total = (long) CONTROLLER + (long) EXTENSION * Math.max(0, extensions);
        return (int) Math.min(total, UNLIMITED);
    }

    /**
     * Was an dieser Stelle je Tick hindurchgeht.
     *
     * <p>Router, Gateway und Quantum-Brücke tragen so viel wie ein Kabel:
     * Sie sind Leitung und kein Vermehrer.
     */
    public static int at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.ControllerBlock) {
            // Die Zahl steht in der BlockEntity, weil sie von den Anbauten
            // abhängt — und die zählt der Graph beim Neuaufbau, nicht dieser
            // Aufruf: Er läuft je Fach je Worker je Tick.
            return level.getBlockEntity(pos)
                    instanceof dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity controller
                    ? controller.bandwidth() : CONTROLLER;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.RouterBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.GatewayBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
            return CABLE;
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
        double wert = bytes;
        int stufe = 0;
        while (wert >= 1000 && stufe < UNITS.length - 1) {
            wert /= 1000;
            stufe++;
        }
        if (stufe == 0) {
            return (long) wert + " B/s";
        }
        if (wert == Math.floor(wert)) {
            return (long) wert + " " + UNITS[stufe] + "/s";
        }
        return String.format(Locale.GERMANY, "%.1f %s/s", wert, UNITS[stufe]);
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
