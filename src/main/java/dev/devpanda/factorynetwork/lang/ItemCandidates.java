package dev.devpanda.factorynetwork.lang;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Gegenstände, über die ein Programm gerade spricht.
 *
 * <p><b>Wofür:</b> Ob eine Maschine einen bestimmten Gegenstand annimmt,
 * lässt sich nicht aufzählen — {@code IItemHandler} hat keine API dafür. Man
 * kann nur mit einem konkreten Gegenstand einen Einfügeversuch simulieren.
 * Dafür braucht es Kandidaten, und die stehen im Programm: Wer
 * {@code item:iron_ore} tippt, fragt sich über Eisenerz etwas, nicht über die
 * zwanzigtausend anderen Arten.
 *
 * <p><b>Über den Text und nicht über den Baum</b> — dieselbe Entscheidung wie
 * bei {@link Definitions#references}: Ein Auswahlausdruck steht in Filtern,
 * Argumenten, Bedingungen und Anweisungen, und ihn im Baum überall zu finden
 * hieße, jede Ausdrucksart einzeln zu behandeln. Die Textsuche findet dafür
 * gelegentlich einen Treffer in einem Kommentar. Das ist hier folgenlos: Ein
 * Kandidat zu viel kostet einen simulierten Einfügeversuch.
 *
 * <p>Tags bleiben außen vor. Sie stehen für viele Arten, und welche das sind,
 * weiß erst die Registry — die Probe würde damit so teuer wie die
 * Registry-Variante, die der Entwurf verwirft.
 */
public final class ItemCandidates {

    /**
     * {@code item:mekanism/iron_ore} oder {@code item:iron_ore}.
     *
     * <p>Ohne Platzhalter: {@code item:*_dust} steht für viele Arten und ist
     * damit ein Tag in anderer Schreibweise.
     */
    private static final Pattern ITEM_SELECTOR =
            Pattern.compile("\\bitem:([a-z0-9_.-]+(?:/[a-z0-9_./-]+)?)");

    /** Mehr Kandidaten kosten mehr, ohne mehr zu sagen. */
    private static final int MAX = 24;

    private ItemCandidates() {
    }

    /** Was im ganzen Projekt an {@code item:}-Literalen steht. */
    public static Set<String> of(Project project) {
        Set<String> found = new LinkedHashSet<>();
        for (String name : project.names()) {
            collect(project.source(name), found);
            if (found.size() >= MAX) {
                break;
            }
        }
        return found;
    }

    private static void collect(String source, Set<String> into) {
        Matcher matcher = ITEM_SELECTOR.matcher(source);
        while (matcher.find() && into.size() < MAX) {
            into.add(matcher.group(1));
        }
    }
}
