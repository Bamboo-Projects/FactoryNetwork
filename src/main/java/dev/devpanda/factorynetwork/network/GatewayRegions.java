package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.GatewayBlock;
import dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which device hangs behind which gateway.
 *
 * <p>A gateway gives its surroundings an installation name: whatever is
 * reachable from it over cable belongs to its installation. This is the second
 * answer to the same question that {@code werk_1/eingang} answers — and it
 * starts from what an installation really is in the game: <b>something
 * connected</b>.
 *
 * <p><b>Another gateway is the boundary.</b> Whoever builds two installations
 * side by side puts down two gateways, and between them each one ends. Without
 * this rule the search would run across the whole network and every
 * installation would be named after the last gateway found.
 *
 * <p><b>The controller is a boundary too.</b> Otherwise an installation would
 * stretch across the controller into every other strand, and a gateway would
 * turn into a statement about the whole network.
 *
 * <p><b>Two gateways on the same device cancel each other out.</b> Then it is
 * not settled which installation is meant, and nothing is guessed — the device
 * then belongs to none, as if no gateway stood there. It is reported in the
 * <i>Network</i> tab; silently taking the first would hinge on the search
 * order.
 */
public final class GatewayRegions {

    /** Nothing found — no gateway in the network. */
    public static final GatewayRegions EMPTY =
            new GatewayRegions(Map.of(), Set.of());

    private final Map<BlockPos, String> byDevice;
    private final Set<BlockPos> contested;

    private GatewayRegions(Map<BlockPos, String> byDevice, Set<BlockPos> contested) {
        this.byDevice = byDevice;
        this.contested = contested;
    }

    /** The installation of a device, or {@code null}. */
    public String instanceAt(BlockPos device) {
        return byDevice.get(device);
    }

    /** Devices claimed by two gateways. */
    public Set<BlockPos> contested() {
        return contested;
    }

    public boolean isEmpty() {
        return byDevice.isEmpty() && contested.isEmpty();
    }

    /**
     * Searches from each gateway for what belongs to it.
     *
     * <p>Walked over the cables, not over the network graph: that one knows the
     * paths to the controller, and here what counts is the neighbourhood of the
     * gateway.
     *
     * @param gateways the spots where a gateway stands
     */
    public static GatewayRegions of(Level level, Iterable<BlockPos> gateways,
            int maxNodes) {
        Map<BlockPos, String> byDevice = new LinkedHashMap<>();
        Set<BlockPos> contested = new HashSet<>();
        for (BlockPos gateway : gateways) {
            String name = nameAt(level, gateway);
            if (name.isEmpty()) {
                continue;
            }
            for (BlockPos device : reach(level, gateway, maxNodes)) {
                String taken = byDevice.get(device);
                if (taken == null) {
                    byDevice.put(device, name);
                } else if (!taken.equals(name)) {
                    contested.add(device);
                }
            }
        }
        // A contested device belongs to no installation: the answer "the
        // first one found" would hinge on the order of the search.
        contested.forEach(byDevice::remove);
        return byDevice.isEmpty() && contested.isEmpty()
                ? EMPTY : new GatewayRegions(Map.copyOf(byDevice), Set.copyOf(contested));
    }

    private static String nameAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GatewayBlockEntity gateway
                ? gateway.instance() : "";
    }

    /**
     * The connectors reachable from this gateway over cable.
     *
     * <p>It stops at another gateway, at the controller and at everything that
     * is not a cable. A device itself does not conduct onward — whoever puts
     * two connectors next to each other means two connectors and no line.
     */
    private static Set<BlockPos> reach(Level level, BlockPos gateway, int maxNodes) {
        Set<BlockPos> found = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(gateway);
        seen.add(gateway);
        while (!queue.isEmpty() && seen.size() <= maxNodes) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.isLoaded(next) || !seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CableBlock
                        && dev.devpanda.factorynetwork.block.CableBlock.carries(state)) {
                    queue.add(next);
                    // A cable conducts onward while also carrying connectors
                    // on its faces. Both hold: the strand runs through, and
                    // what hangs on it belongs to this installation.
                    if (dev.devpanda.factorynetwork.block.entity.Connectors.any(level, next)) {
                        found.add(next);
                    }
                } else if (dev.devpanda.factorynetwork.block.entity.Connectors
                        .any(level, next)) {
                    found.add(next);
                }
                // Everything else ends here — a second gateway included.
            }
        }
        return found;
    }
}
