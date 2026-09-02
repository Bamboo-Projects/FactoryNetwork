package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * The one way to find a connector.
 *
 * <p><b>It used to appear thirty-one times:</b>
 * {@code level.getBlockEntity(pos) instanceof ConnectorBlockEntity} — in the
 * graph, in device detection, in the runtime, in the packets, in the editor.
 * As long as a block carried exactly one connector, that was right. Since a
 * cable block carries up to six, it is the wrong question: it has no answer
 * without a side.
 *
 * <p><b>There is only one form left</b> (26.08.): the connector on a face of
 * a cable. The dedicated connector block is gone — it could do the same and
 * needed one block more.
 *
 * <p>{@link #at(BlockGetter, BlockPos)} without a side stays all the same:
 * whoever has only a point in space — the analyzer, the labeling gun, the
 * naming screen — gets the connector if exactly one sits there. If two sit
 * there, there is no answer; nothing is guessed.
 */
public final class Connectors {

    private Connectors() {
    }

    /**
     * The connector on this face, or {@code null}.
     *
     * <p>At the connector block it applies only if it faces that way anyway:
     * it has one facing, not six.
     */
    public static @Nullable ConnectorPart at(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                ? bus.partAt(side) : null;
    }

    /**
     * The connector at this spot, if there is <b>exactly one</b>.
     *
     * <p>For the call sites that still make do with a position alone. If two
     * sit on one block, this returns {@code null} — nothing is guessed, and
     * the call site ought to be switched over to position and side.
     */
    public static @Nullable ConnectorPart at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                && bus.parts().size() == 1
                ? bus.parts().values().iterator().next() : null;
    }

    /** Whether any connector sits at this spot at all. */
    public static boolean any(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                && bus.hasParts();
    }

    /** How many connectors sit at this spot. */
    public static int count(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBusBlockEntity bus
                ? bus.parts().size() : 0;
    }
}
