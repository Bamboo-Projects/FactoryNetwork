package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Welche Quantum-Brücken es in einer Welt gibt, nach ihrer Nummer.
 *
 * <p><b>Gesucht wird nicht, angemeldet wird.</b> Eine Brücke, die ihren
 * Partner über die Welt suchen müsste, durchsuchte bei jeder Frage Millionen
 * Blöcke — und die Frage fällt bei jedem Neuaufbau des Netzes. Sie trägt sich
 * stattdessen beim Laden ein, wie es der Controller vormacht.
 *
 * <p>Schwache Verweise auf die Welten, damit ein entladenes Level nicht
 * hängenbleibt — dieselbe Vorsicht wie in {@link ControllerRegistry}.
 */
public final class BridgeRegistry {

    private static final Map<LevelAccessor, Map<UUID, Set<BlockPos>>> BY_LEVEL =
            new WeakHashMap<>();

    public static synchronized void add(Level level, UUID pair, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, key -> new java.util.HashMap<>())
                .computeIfAbsent(pair, key -> new LinkedHashSet<>())
                .add(pos.immutable());
    }

    public static synchronized void remove(Level level, UUID pair, BlockPos pos) {
        Map<UUID, Set<BlockPos>> pairs = BY_LEVEL.get(level);
        if (pairs == null) {
            return;
        }
        Set<BlockPos> found = pairs.get(pair);
        if (found != null) {
            found.remove(pos);
            if (found.isEmpty()) {
                pairs.remove(pair);
            }
        }
    }

    /**
     * Die Gegenstelle dieser Brücke, oder {@code null}.
     *
     * <p>Drei Regeln, und jede fängt einen Fall, den es im Spiel gibt:
     *
     * <ol>
     *   <li><b>Ohne Hälfte gehört eine Brücke zu niemandem.</b></li>
     *   <li><b>Eine Brücke ist nie ihr eigener Partner</b> — sonst zeigte
     *       ein Netz durch sich hindurch auf sich selbst. Das braucht keine
     *       eigene Regel: Gefragt wird nach der <i>anderen</i> Stelle.</li>
     *   <li><b>Drei Brücken mit derselben Nummer verbinden gar nichts.</b>
     *       Im Kreativmodus lässt sich eine Hälfte vervielfachen, und „zwei
     *       von dreien, aber welche" ist keine Regel, die jemand erraten
     *       kann. Lieber sichtbar tot als heimlich falsch.</li>
     * </ol>
     */
    public static synchronized @Nullable BlockPos partnerOf(Level level, BlockPos pos) {
        Map<UUID, Set<BlockPos>> pairs = BY_LEVEL.get(level);
        if (pairs == null) {
            return null;
        }
        for (Set<BlockPos> places : pairs.values()) {
            if (!places.contains(pos)) {
                continue;
            }
            if (places.size() != 2) {
                return null;
            }
            for (BlockPos other : places) {
                if (!other.equals(pos)) {
                    return other;
                }
            }
        }
        return null;
    }

    private BridgeRegistry() {
    }
}
