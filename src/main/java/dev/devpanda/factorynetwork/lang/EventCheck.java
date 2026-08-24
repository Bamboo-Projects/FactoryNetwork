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
 * Prüft, ob ein {@code on}-Block je laufen kann.
 *
 * <p><b>Der Anlass ist ein Block, der nichts tut und nichts sagt.</b> Ein
 * {@code on} braucht keine Deklaration — der Übersetzer nimmt jeden Namen an,
 * weil er nicht wissen kann, welche Ereignisse eine andere Datei erklärt.
 * {@code on inventory_changed(kiste) { … }} übersetzt sauber, wird übernommen
 * und läuft nie: Dieses Ereignis löst niemand aus.
 *
 * <p>Anders als bei einem Tippfehler in einem Connectornamen fällt das nicht
 * beim ersten Lauf auf, sondern gar nicht. Es gibt keinen ersten Lauf.
 *
 * <p><b>Warnungen, keine Fehler.</b> Dieselbe Begründung wie bei
 * {@link NetworkCheck}: Ein Programm, das erst mit der nächsten Datei
 * vollständig wird, soll sich heute schon übernehmen lassen.
 */
public final class EventCheck {

    private EventCheck() {
    }

    /**
     * Die Ereignisse, die diese Datei erklärt — Name und Zahl der Werte.
     *
     * <p>Getrennt von {@link #run}, weil alle Dateien einen Namensraum teilen:
     * Erst werden alle Deklarationen eingesammelt, dann geprüft. Sonst
     * beanstandete die erste Datei, was die zweite erklärt.
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
     * Sucht {@code on}-Blöcke, die niemand aufruft.
     *
     * @param program  das übersetzte Programm einer Datei
     * @param declared alle {@code event}-Deklarationen des Projekts
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
     * Der Hinweis unter einem unbekannten Ereignis.
     *
     * <p>Ein ähnlicher Name ist die beste Auskunft, die ganze Liste die
     * zweitbeste. Beides schlägt „unbekannt" ohne Zusatz, weil der Spieler
     * sonst nicht sehen kann, ob er sich vertippt hat oder ob es das
     * Ereignis überhaupt nicht gibt.
     */
    private static String unknownHint(String wanted, Map<String, Integer> declared) {
        // Erst unter allem suchen, was es wirklich gibt — eingebaut wie erklärt.
        List<String> known = new ArrayList<>(BuiltinEvents.ARITY.keySet());
        known.addAll(declared.keySet());
        Optional<String> closest = closest(wanted, known);
        if (closest.isPresent()) {
            return "Meinst du " + closest.get() + "?";
        }
        return "Das Netz löst " + String.join(", ", new TreeSet<>(BuiltinEvents.ARITY.keySet()))
                + " aus. Eigene Ereignisse brauchen ein event und ein emit.";
    }

    /** Welche Werte ein Ereignis wirklich mitbringt. */
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

    /** Derselbe Maßstab wie bei Connectornamen: nah genug ist ein Vertipper. */
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
