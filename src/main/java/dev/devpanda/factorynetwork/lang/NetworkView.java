package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.util.NameDistance;

import java.util.List;
import java.util.Optional;

/**
 * What stands in the world, from the compiler's point of view.
 *
 * <p><b>The compiler alone cannot check a name.</b> {@code display test} is
 * grammatically flawless even when no board is called that — and then the wall
 * stays black without anything turning red anywhere. This is the error you
 * search for the longest: it looks like a broken network and is a typo.
 *
 * <p>That is why the compiler optionally gets a look at the real network.
 * Optionally, because there are two callers: the client has the names from the
 * network state, the server has the graph — and a test has nothing, and should
 * still be able to compile.
 *
 * <p>What comes out of it are <b>warnings and not errors</b>. A wall you only
 * build tomorrow, you may already write into the program today.
 */
public interface NetworkView {

    /** A view that knows nothing — then nothing is checked either. */
    NetworkView NONE = new NetworkView() {
        @Override
        public boolean knowsNetwork() {
            return false;
        }

        @Override
        public List<String> connectors() {
            return List.of();
        }

        @Override
        public List<String> displays() {
            return List.of();
        }
    };

    /**
     * Whether anything is known at all.
     *
     * <p>Not the same as "the lists are empty": a network without connectors is
     * a known network without connectors, and then every name in it is wrong. An
     * unknown network says nothing about any name.
     */
    default boolean knowsNetwork() {
        return true;
    }

    /** The names of the connectors in the network. */
    List<String> connectors();

    /** The names of the display walls in the network. */
    List<String> displays();

    /**
     * What stands behind a connector.
     *
     * <p>Unknown by default: a test has no network, and the server knew only
     * names up to here. Whoever knows nothing says nothing about any device —
     * that is something other than declaring a device empty.
     */
    default DeviceProfile profile(String connector) {
        return DeviceProfile.unreachable();
    }

    /** The most similar connector name, for "did you mean". */
    default Optional<String> closestConnector(String wanted) {
        return closest(wanted, connectors());
    }

    /** And the same for displays. */
    default Optional<String> closestDisplay(String wanted) {
        return closest(wanted, displays());
    }

    /**
     * The most similar name from a list.
     *
     * <p>A third of the length in distance, at most three characters: further
     * away is no longer a typo but a different name — and a wrong suggestion is
     * worse than none.
     */
    private static Optional<String> closest(String wanted, List<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = NameDistance.between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // The threshold lives in NameDistance and not here: it also applies to
        // "did you mean" during compilation, and two thresholds would drift
        // apart.
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }
}
