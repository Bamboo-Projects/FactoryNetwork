package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * Wie Source an einer fremden Maschine gelesen und geschrieben wird.
 *
 * <p><b>Der Beweis für die zweite Achse.</b> Ars Nouveau meldet eine
 * gewöhnliche Block-Capability an — {@code BlockCapability<ISourceCap,
 * Direction>} —, genau in der Form, in der auch Strom und Flüssigkeit
 * danebenliegen. Was hier steht, ist die Übersetzung von deren Wortschatz in
 * unseren, und keine Zeile davon steht im Kern.
 *
 * <p><b>Erst fragen, dann ziehen.</b> Die Regel aus {@link MachineAccess}:
 * Was der Netzspeicher nicht nimmt, darf gar nicht erst aus der Maschine
 * kommen. Source lässt sich nicht auf den Boden legen.
 */
public final class SourceAccess implements MachineAccess {

    /** Der eine Schlüssel dieser Art — es gibt nur eine Sorte Source. */
    static final String KEY = "source";

    public static final SourceAccess INSTANCE = new SourceAccess();

    private SourceAccess() {
    }

    @Override
    public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
        if (!wanted(keys)) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        return cap == null ? 0 : cap.getSource();
    }

    @Override
    public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                     Collection<?> keys, long limit) {
        if (!wanted(keys) || limit <= 0) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        int room = cap.receiveSource(clamp(limit), true);
        if (room <= 0) {
            return 0;
        }
        long taken = from.extract(KEY, room);
        if (taken <= 0) {
            return 0;
        }
        int arrived = cap.receiveSource(clamp(taken), false);
        if (arrived < taken) {
            // Zurück in den Speicher: Zwischen Probe und Tat kann sich die
            // Maschine anders entschieden haben.
            from.insert(KEY, taken - arrived);
        }
        return arrived;
    }

    @Override
    public long drain(Level level, BlockPos pos, Direction side, Collection<?> keys,
                      ResourceStore into, long limit) {
        if (!wanted(keys) || limit <= 0) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        if (cap == null || !cap.canExtract()) {
            return 0;
        }
        long room = into.room(KEY, limit);
        if (room <= 0) {
            return 0;
        }
        int available = cap.extractSource(clamp(room), true);
        if (available <= 0) {
            return 0;
        }
        int pulled = cap.extractSource(available, false);
        if (pulled <= 0) {
            return 0;
        }
        long stranded = into.insert(KEY, pulled);
        if (stranded > 0) {
            // Was nicht hineinpasst, geht zurück in die Maschine. Passt es
            // auch dort nicht mehr, ist es verloren — deshalb steht die
            // Probe oben.
            cap.receiveSource(clamp(stranded), false);
        }
        return pulled - stranded;
    }

    /**
     * Die Capability an dieser Seite, oder {@code null}.
     *
     * <p>Der Zugriff auf die Klassen von Ars Nouveau steht in einer eigenen
     * Methode: So lädt die JVM sie erst, wenn wirklich jemand fragt.
     */
    private static com.hollingsworth.arsnouveau.api.source.ISourceCap capAt(
            Level level, BlockPos pos, Direction side) {
        if (!FnArs.installed() || level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
                pos, side);
    }

    /**
     * Ist Source überhaupt gemeint?
     *
     * <p>Eine leere Auswahl heißt nicht „alles" — dieselbe Regel wie in
     * {@link MachineAccess#count}, und sie hat am 26.08. schon einmal eine
     * Kiste leergeräumt.
     */
    private static boolean wanted(Collection<?> keys) {
        return keys != null && keys.contains(KEY);
    }

    /** Ars Nouveau rechnet in {@code int}; unsere Mengen sind {@code long}. */
    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
