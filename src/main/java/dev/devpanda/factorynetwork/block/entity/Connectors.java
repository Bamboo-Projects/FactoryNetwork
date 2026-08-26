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
 * <p>Diese Klasse ist die richtige Frage, und sie beantwortet daneben die
 * alte, solange sie noch gestellt wird: {@link #at(BlockGetter, BlockPos)}
 * gibt den Anschluss, wenn es genau einen gibt.
 *
 * <p>Beide Bauformen stehen nebeneinander — der Connectorblock mit einem
 * Anschluss und der Kabelblock mit bis zu sechs. Das ist keine Unentschiedenheit,
 * sondern der Weg: Jeder Schnitt bleibt grün, und die alten Welten verlieren
 * ihre Connectoren erst, wenn der alte Block wirklich verschwindet.
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
        var entity = level.getBlockEntity(pos);
        if (entity instanceof CableBusBlockEntity bus) {
            return bus.partAt(side);
        }
        if (entity instanceof ConnectorBlockEntity single) {
            return single.part().facing() == side ? single.part() : null;
        }
        return null;
    }

    /**
     * Der Anschluss an dieser Stelle, wenn es <b>genau einen</b> gibt.
     *
     * <p>Für die Stellen, die noch mit einem Ort allein auskommen. Sitzen
     * zwei an einem Block, gibt es hier {@code null} — geraten wird nicht,
     * und die Stelle gehört auf Ort und Seite umgestellt.
     */
    public static @Nullable ConnectorPart at(BlockGetter level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        if (entity instanceof ConnectorBlockEntity single) {
            return single.part();
        }
        if (entity instanceof CableBusBlockEntity bus && bus.parts().size() == 1) {
            return bus.parts().values().iterator().next();
        }
        return null;
    }

    /** Ob an dieser Stelle überhaupt ein Anschluss sitzt. */
    public static boolean any(BlockGetter level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity instanceof ConnectorBlockEntity
                || (entity instanceof CableBusBlockEntity bus && bus.hasParts());
    }

    /** Wie viele Anschlüsse an dieser Stelle sitzen. */
    public static int count(BlockGetter level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        if (entity instanceof ConnectorBlockEntity) {
            return 1;
        }
        return entity instanceof CableBusBlockEntity bus ? bus.parts().size() : 0;
    }
}
