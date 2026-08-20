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
 * Eine Gruppe von Geräten, aufgelöst gegen das Netz.
 *
 * <p><b>Muster über Connectoren lösen sich zur Laufzeit auf</b>, anders als
 * Muster über Gegenstände. Wer einen weiteren Ofen aufstellt und ihn
 * {@code furnace_9} nennt, soll ihn nicht auch noch im Code eintragen müssen.
 * Bezahlbar ist das, weil es Connectoren dutzendweise gibt und nicht zu
 * Tausenden — genau der Unterschied, der bei Gegenständen dagegen sprach.
 */
public final class DeviceGroup {

    private final String name;
    private final List<String> members;
    private final Strategy strategy;
    /** Wandert weiter, damit round_robin nicht immer beim ersten anfängt. */
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
     * Löst eine Deklaration gegen das Netz auf.
     *
     * <p>Namen ohne Muster kommen mit, auch wenn sie gerade nicht im Netz
     * hängen: Ein Gerät, dessen Chunk nicht geladen ist, gehört weiter zur
     * Gruppe. Muster dagegen können nur treffen, was da ist.
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

    /** Übersetzt ein Namensmuster; nur {@code *} zählt. */
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
     * Wie eine Gruppe auf ihre Mitglieder verteilt.
     *
     * <p>Fünf Verfahren, wie im Konzept festgelegt. {@code balanced} ist
     * gestrichen: Seine Bedeutung ließ sich nicht von {@code least_filled}
     * unterscheiden, und was niemand erklären kann, wählt auch niemand
     * bewusst.
     */
    public enum Strategy {

        /** Reihum, gleichmäßig. */
        ROUND_ROBIN("round_robin"),
        /** Das erste, das kann. */
        FIRST_AVAILABLE("first_available"),
        /** Dorthin, wo am wenigsten liegt. */
        LEAST_FILLED("least_filled"),
        /** Zufällig. */
        RANDOM("random"),
        /** In der Reihenfolge der Mitglieder. */
        PRIORITY("priority");

        private final String key;

        Strategy(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** Ohne Angabe reihum — das gleichmäßigste Verfahren. */
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
     * Die Reihenfolge, in der die Mitglieder für einen Transfer versucht
     * werden.
     *
     * <p>Alle Verfahren liefern eine vollständige Reihenfolge, keine einzelne
     * Wahl: Ist das erste Gerät voll, muss der Transfer beim nächsten
     * weitermachen können, statt aufzugeben. Nur so ist eine Gruppe mehr als
     * eine umständliche Schreibweise für ein Gerät.
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
                // Beide nehmen die Reihenfolge, in der sie dastehen. Der
                // Unterschied liegt in der Absicht, nicht im Ergebnis: Wer
                // priority schreibt, meint eine Rangfolge; wer
                // first_available schreibt, meint „irgendeines, das kann".
            }
        }
        return order;
    }

    private static java.util.Random toJavaRandom(java.util.random.RandomGenerator random) {
        return new java.util.Random(random.nextLong());
    }
}
