package dev.devpanda.factorynetwork.analyser;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The network as the analyser shows it.
 *
 * <p>Nodes and links, each with a rating. Computed on the server, drawn on the
 * client — the same split as the displays, and for the same reason: the graph
 * is server state, and mirroring it would mean maintaining it twice.
 */
public record AnalyserData(List<Node> nodes, List<Link> links, Summary summary) {

    /**
     * How a node is doing.
     *
     * <p>The first six are ordered by urgency. After them comes what is merely
     * informational — appended and not sorted in, because the order travels
     * over the wire: the state is sent as a running number, and inserting one
     * in the middle would mean that an old client reads something other than
     * what a new server means.
     */
    public enum NodeState {
        /** The controller itself. */
        CONTROLLER,
        /** A device with a channel — all is well. */
        DEVICE,
        /** A display. It needs no channel. */
        DISPLAY,
        /** Attached to the network, but has no name. */
        UNNAMED,
        /** The name is used more than once — all of them are unusable. */
        DUPLICATE,
        /** On the network, but with no free channel on the way to the controller. */
        /**
         * A device whose path is tight.
         *
         * <p>Was called {@code STARVED} until 29 Aug and meant: no channel, and
         * so silent. That state no longer exists — what is tight runs more
         * slowly, rather than not at all.
         */
        CONGESTED,
        /** A drive. It provides space instead of needing any. */
        DRIVE,
        /** A server rack. Without it the network does not compute. */
        RACK,
        /** A router. It forwards traffic and costs no channel of its own. */
        ROUTER,
        /** An extension of the controller. It brings sides of its own instead of taking any up. */
        EXTENSION
    }

    /** How a cable link is doing. */
    public enum LinkState {
        /** Still room. */
        FREE,
        /** Three quarters or more used. */
        TIGHT,
        /** No channel free any more — this is where it jams. */
        FULL
    }

    public record Node(BlockPos pos, NodeState state, String label) {}

    /** {@code load} and {@code capacity} show the channels of this link. */
    public record Link(BlockPos from, BlockPos to, LinkState state, int load, int capacity) {}

    /**
     * What the summary says when you hold the tool up in the air.
     *
     * <p>Deliberately numbers and not sentences: the client builds its lines
     * from them and translates them, instead of receiving finished text.
     */
    public record Summary(int devices, int cables, int starved, int unnamed,
                          int duplicates, int tightLinks, int fullLinks) {

        public boolean isHealthy() {
            return starved == 0 && duplicates == 0 && fullLinks == 0;
        }
    }

    public static AnalyserData empty() {
        return new AnalyserData(List.of(), List.of(),
                new Summary(0, 0, 0, 0, 0, 0, 0));
    }
}
