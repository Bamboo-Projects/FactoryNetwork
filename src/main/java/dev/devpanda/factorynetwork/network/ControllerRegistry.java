package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Welche Controller es in einer Welt gibt.
 *
 * <p>Gebraucht für die umgekehrte Frage: Ein Kabel weiß nicht, zu welchem
 * Netz es gehört — die Kanalzahlen liegen im Graphen des Controllers. Wer
 * ein Kabel ansieht, muss also erst den Controller finden, der es kennt.
 *
 * <p>Die Alternative wäre, jedem Kabel seinen Controller einzuschreiben.
 * Das hieße, bei jedem Neuaufbau des Netzes jede BlockEntity anzufassen —
 * hundertmal mehr Schreibarbeit für dieselbe Auskunft.
 *
 * <p>Schwache Verweise auf die Welten, damit ein entladenes Level nicht
 * hängenbleibt.
 */
public final class ControllerRegistry {

    private static final Map<LevelAccessor, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> positions = BY_LEVEL.get(level);
        if (positions != null) {
            positions.remove(pos);
        }
    }

    public static synchronized Set<BlockPos> in(Level level) {
        return Collections.unmodifiableSet(
                BY_LEVEL.getOrDefault(level, Collections.emptySet()));
    }

    /**
     * Der Controller, dessen Netz diese Stelle enthält.
     *
     * <p>Gehört sie zu mehreren, gewinnt der erste — für eine Anzeige reicht
     * das. Für die Ausführung spielt es keine Rolle: Dort fragt immer ein
     * bestimmter Controller.
     */
    public static Optional<ControllerBlockEntity> owning(Level level, BlockPos pos) {
        for (BlockPos candidate : in(level)) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && controller.graph().contains(pos)) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    /**
     * Baut die Netze neu auf, die diese Stelle berühren.
     *
     * <p>Gebraucht, wenn sich etwas geändert hat, das der Graph nicht von
     * selbst bemerkt — die Bahnzuordnung eines Routers etwa steht in einer
     * BlockEntity und löst keine Nachbarschaftsmeldung aus.
     *
     * <p>Auch die sechs Nachbarn zählen: Ein Block, der eben erst
     * angeschlossen wurde, steht noch in keinem Graphen. Ohne die Nachbarn
     * wäre genau der Fall nicht abgedeckt, für den man das hier braucht.
     */
    public static void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos candidate : Set.copyOf(in(level))) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && touches(controller.graph(), pos)) {
                controller.rebuildNetwork();
            }
        }
    }

    private static boolean touches(FactoryGraph graph, BlockPos pos) {
        if (graph.contains(pos)) {
            return true;
        }
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (graph.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private ControllerRegistry() {
    }
}
