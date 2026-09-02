package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.util.NameDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Checks whether an {@code on} block can ever run.
 *
 * <p><b>The occasion is a block that does nothing and says nothing.</b> An
 * {@code on} needs no declaration — the compiler accepts any name, because it
 * cannot know which events another file declares.
 * {@code on inventory_changed(kiste) { … }} compiles cleanly, is adopted, and
 * never runs: nobody triggers this event.
 *
 * <p>Unlike a typo in a connector name, this shows up not on the first run but
 * not at all. There is no first run.
 *
 * <p><b>Warnings, not errors.</b> The same rationale as with
 * {@link NetworkCheck}: a program that only becomes complete with the next file
 * should already be adoptable today.
 */
public final class EventCheck {

    private EventCheck() {
    }

    /**
     * The events this file declares — name and number of values.
     *
     * <p>Separate from {@link #run}, because all files share one namespace:
     * first all declarations are collected, then checked. Otherwise the first
     * file would object to what the second declares.
     */
    public static Map<String, Integer> declaredEvents(Program program) {
        Map<String, Integer> events = new HashMap<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Event event) {
                events.put(event.name(), event.parameters().size());
            }
        }
        return events;
    }

    /**
     * Searches for {@code on} blocks that nobody calls.
     *
     * @param program  the compiled program of a file
     * @param declared all {@code event} declarations of the project
     */
    public static List<Diagnostic> run(Program program, Map<String, Integer> declared) {
        List<Diagnostic> problems = new ArrayList<>();
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.On handler)) {
                continue;
            }
            Integer arity = BuiltinEvents.ARITY.get(handler.name());
            if (arity == null) {
                arity = declared.get(handler.name());
            }
            if (arity == null) {
                problems.add(new Diagnostic(Diagnostic.Severity.WARNING, handler.span(),
                        "Das Ereignis " + handler.name() + " löst niemand aus.",
                        unknownHint(handler.name(), declared)));
                continue;
            }
            if (handler.parameters().size() > arity) {
                problems.add(new Diagnostic(Diagnostic.Severity.WARNING, handler.span(),
                        handler.name() + " übergibt "
                                + (arity == 1 ? "einen Wert" : arity + " Werte") + ", hier stehen "
                                + handler.parameters().size() + " Namen.",
                        "Die überzähligen bleiben für immer leer. "
                                + tooManyHint(handler.name())));
            }
        }
        return problems;
    }

    /**
     * The hint under an unknown event.
     *
     * <p>A similar name is the best information, the whole list the second best.
     * Both beat "unknown" with nothing added, because otherwise the player
     * cannot see whether they made a typo or whether the event does not exist at
     * all.
     */
    private static String unknownHint(String wanted, Map<String, Integer> declared) {
        // First search among everything that really exists — built-in as well as declared.
        List<String> known = new ArrayList<>(BuiltinEvents.ARITY.keySet());
        known.addAll(declared.keySet());
        Optional<String> closest = closest(wanted, known);
        if (closest.isPresent()) {
            return "Meinst du " + closest.get() + "?";
        }
        return "Das Netz löst " + String.join(", ", new TreeSet<>(BuiltinEvents.ARITY.keySet()))
                + " aus. Eigene Ereignisse brauchen ein event und ein emit.";
    }

    /** Which values an event actually brings along. */
    private static String tooManyHint(String event) {
        if (BuiltinEvents.REDSTONE_CHANGED.equals(event)) {
            return "Es sind das Gerät und die Stärke.";
        }
        if (BuiltinEvents.DEVICE_OFFLINE.equals(event)) {
            return "Es ist der Name des Geräts — das Gerät selbst ist ja fort.";
        }
        if (BuiltinEvents.ARITY.containsKey(event)) {
            return "Es ist das Gerät. Zwei Werte hat nur redstone_changed.";
        }
        return "Die Zahl steht in der event-Zeile.";
    }

    /** The same measure as for connector names: close enough is a typo. */
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
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }
}
