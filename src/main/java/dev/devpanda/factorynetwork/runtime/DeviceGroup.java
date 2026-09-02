package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.FactoryGraph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A group of devices, resolved against the network.
 *
 * <p><b>Patterns over connectors resolve at runtime</b>, unlike patterns over
 * items. Whoever places another furnace and names it {@code furnace_9} should
 * not have to enter it in the code as well. That is affordable because
 * connectors come by the dozen and not by the thousand — exactly the
 * difference that argued against it for items.
 */
public final class DeviceGroup {

    private final String name;
    private final List<String> members;
    private final Strategy strategy;
    /** Advances so that round_robin does not always start at the first one. */
    private int cursor;

    public DeviceGroup(String name, List<String> members, Strategy strategy) {
        this.name = name;
        this.members = members;
        this.strategy = strategy;
    }

    public String name() {
        return name;
    }

    public List<String> members() {
        return members;
    }

    public Strategy strategy() {
        return strategy;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * Resolves a declaration against the network.
     *
     * <p>Names without a pattern are included even if they are not currently
     * attached to the network: a device whose chunk is not loaded still
     * belongs to the group. Patterns, by contrast, can only match what is
     * there.
     */
    public static DeviceGroup resolve(Decl.Group declaration, FactoryGraph graph) {
        Set<String> found = new LinkedHashSet<>();
        for (Expr member : declaration.members()) {
            if (member instanceof Expr.Name name) {
                found.add(name.value());
            } else if (member instanceof Expr.NamePattern pattern) {
                Pattern compiled = toPattern(pattern.pattern());
                graph.connectorNames().stream()
                        .filter(candidate -> compiled.matcher(candidate).matches())
                        .sorted()
                        .forEach(found::add);
            }
        }
        Strategy strategy = Strategy.of(declaration.strategy());
        return new DeviceGroup(declaration.name(), List.copyOf(found), strategy);
    }

    /** Translates a name pattern; only {@code *} counts. */
    private static Pattern toPattern(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * How a group distributes across its members.
     *
     * <p>Five strategies, as laid down in the concept. {@code balanced} is
     * dropped: its meaning could not be distinguished from
     * {@code least_filled}, and what nobody can explain, nobody chooses
     * deliberately either.
     */
    public enum Strategy {

        /** In turn, evenly. */
        ROUND_ROBIN("round_robin"),
        /** The first one that can. */
        FIRST_AVAILABLE("first_available"),
        /** To wherever the least is stored. */
        LEAST_FILLED("least_filled"),
        /** Random. */
        RANDOM("random"),
        /** In the order of the members. */
        PRIORITY("priority");

        private final String key;

        Strategy(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** Round robin when unspecified — the most even strategy. */
        public static Strategy of(String name) {
            if (name == null) {
                return ROUND_ROBIN;
            }
            for (Strategy strategy : values()) {
                if (strategy.key.equals(name.toLowerCase(Locale.ROOT))) {
                    return strategy;
                }
            }
            return ROUND_ROBIN;
        }
    }

    /**
     * The order in which the members are tried for a transfer.
     *
     * <p>All strategies return a complete order, not a single choice: if the
     * first device is full, the transfer must be able to continue with the
     * next one instead of giving up. Only then is a group more than a
     * roundabout way of writing a single device.
     */
    public List<String> order(java.util.function.ToLongFunction<String> fillLevel,
                              java.util.random.RandomGenerator random) {
        List<String> order = new ArrayList<>(members);
        switch (strategy) {
            case ROUND_ROBIN -> {
                if (!order.isEmpty()) {
                    java.util.Collections.rotate(order, -(cursor % order.size()));
                    cursor = (cursor + 1) % order.size();
                }
            }
            case LEAST_FILLED -> order.sort(
                    java.util.Comparator.comparingLong(fillLevel));
            case RANDOM -> java.util.Collections.shuffle(order, toJavaRandom(random));
            case FIRST_AVAILABLE, PRIORITY -> {
                // Both take the order in which they are listed. The
                // difference lies in the intent, not in the result: whoever
                // writes priority means a ranking; whoever writes
                // first_available means "any one that can".
            }
        }
        return order;
    }

    private static java.util.Random toJavaRandom(java.util.random.RandomGenerator random) {
        return new java.util.Random(random.nextLong());
    }
}
