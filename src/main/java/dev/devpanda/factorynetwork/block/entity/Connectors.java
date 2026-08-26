package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Der eine Weg, einen Anschluss zu finden.
 *
 * <p><b>Vorher stand er einunddreißigmal da:</b>
 * {@code level.getBlockEntity(pos) instanceof ConnectorBlockEntity} — im
 * Graphen, in der Geräteerkennung, in der Laufzeit, in den Paketen, im Editor.
 * Solange ein Block genau einen Anschluss trug, war das richtig. Seit ein
 * Kabelblock bis zu sechs trägt, ist es die falsche Frage: Sie hat keine
 * Antwort ohne eine Seite.
 *
 * <p><b>Es gibt nur noch eine Bauform</b> (26.08.): den Anschluss an einer
 * Fläche eines Kabels. Der eigene Connectorblock ist weg — er konnte
 * dasselbe und brauchte einen Platz mehr.
 *
 * <p>{@link #at(BlockGetter, BlockPos)} ohne Seite bleibt trotzdem: Wer nur
 * einen Punkt im Raum hat — der Analysator, die Beschriftungspistole, das
 * Namensfenster —, bekommt den Anschluss, wenn dort genau einer sitzt.
 * Sitzen zwei, gibt es keine Antwort; geraten wird nicht.
 */
public final class Connectors {

    private Connectors() {
    }

    /**
     * Der Anschluss an dieser Fläche, oder {@code null}.
     *
     * <p>Am Connectorblock gilt sie nur, wenn er ohnehin dorthin zeigt: Er hat
     * eine Blickrichtung und keine sechs.
     */
    public static @Nullable ConnectorPart at(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                ? bus.partAt(side) : null;
    }

    /**
     * Der Anschluss an dieser Stelle, wenn es <b>genau einen</b> gibt.
     *
     * <p>Für die Stellen, die noch mit einem Ort allein auskommen. Sitzen
     * zwei an einem Block, gibt es hier {@code null} — geraten wird nicht,
     * und die Stelle gehört auf Ort und Seite umgestellt.
     */
    public static @Nullable ConnectorPart at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                && bus.parts().size() == 1
                ? bus.parts().values().iterator().next() : null;
    }

    /** Ob an dieser Stelle überhaupt ein Anschluss sitzt. */
    public static boolean any(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                && bus.hasParts();
    }

    /** Wie viele Anschlüsse an dieser Stelle sitzen. */
    public static int count(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                ? bus.parts().size() : 0;
    }
}
