package dev.devpanda.factorynetwork.network;

import net.minecraft.world.level.Level;

import java.util.List;

/**
 * How long it takes until the first item arrives.
 *
 * <p><b>Not per block, but per device along the path.</b> Light needs sixty
 * nanoseconds for twenty blocks; a Minecraft tick is fifty milliseconds — a
 * million times longer. Latency from distance would be pure invention in this
 * mod.
 *
 * <p><b>What really costs time in a real network is the processing.</b> Every
 * switch along the way unpacks the packet, looks at it, and packs it up
 * again. Over fiber across continents that is the larger part of the latency,
 * not the distance.
 *
 * <p><b>And it delays the start, not the cadence.</b> A worker behind three
 * routers begins three ticks later — after that it runs as fast as any other.
 * Anything else would be a covert bandwidth penalty: whoever separates things
 * cleanly would get less throughput, and that is exactly what latency is not
 * meant to be.
 */
public final class Latency {

    /**
     * What a device along the path costs: one tick.
     *
     * <p>A bridge pair therefore costs two — one half packs it in, the other
     * unpacks it. The plan of 30.08. named one; two is the more honest number
     * and needs no special rule.
     *
     * <p><b>Whoever raises this number should check the language files:</b>
     * there it reads "%s tick" in the singular, and with two that would be
     * wrong.
     */
    public static final int PER_HOP = 1;

    /** How many ticks this path needs until the first grab arrives. */
    public static int of(Level level, List<FactoryGraph.Node> path) {
        int ticks = 0;
        for (FactoryGraph.Node node : path) {
            var block = level.getBlockState(node.pos()).getBlock();
            // Cables and the controller cost nothing: the cable is fiber, and
            // the controller is the start of the path, not a stop along the
            // way.
            if (block instanceof dev.devpanda.factorynetwork.block.RouterBlock
                    || block instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
                ticks += PER_HOP;
            }
        }
        return ticks;
    }

    private Latency() {
    }
}
