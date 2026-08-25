package dev.devpanda.factorynetwork.crafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Zerlegt eine Bestellung in Schritte.
 *
 * <p>Der einstufige Fabricator konnte eine Truhe bauen, wenn Bretter dalagen.
 * Lagen keine da, sagte er das — auch dann, wenn ein Stapel Stämme im
 * Laufwerk lag und der Weg dahin aus einem einzigen Rezept bestand. Der
 * Planner geht diesen Weg: Er fragt für jede fehlende Zutat, ob das Netz sie
 * selbst herstellen kann, und tut das so lange, bis er bei etwas ankommt, das
 * jemand hinlegen muss.
 *
 * <p><b>Er kennt keine Gegenstände.</b> Was ein Ziel ist, entscheidet der
 * Aufrufer über {@code T} — im Spiel ein {@code Item}, in der Prüfung ein
 * Text. Das ist kein Selbstzweck: Die Fälle, an denen eine solche Rekursion
 * scheitert — ein Kreis, eine zu tiefe Kette, ein Überschuss, der verfällt,
 * eine Zutat mit mehreren erlaubten Sorten — lassen sich so ohne geladene
 * Welt vorführen.
 *
 * <p><b>Der Plan wird nicht aufbewahrt.</b> Der Controller rechnet ihn bei
 * jedem Fertigungstakt neu. Ein gespeicherter Plan wäre ab dem Moment falsch,
 * in dem ein Worker etwas einlagert — und genau das tun Worker den ganzen Tag.
 * Siehe {@code entscheidungen.md}, „Der Plan wird gerechnet, nicht gemerkt".
 */
public final class CraftingPlanner<T> {

    /**
     * Eine Zutat: so viel von einer dieser Sorten.
     *
     * <p><b>Sorten in der Mehrzahl</b>, weil eine Zutat in Minecraft eine
     * Auswahl ist und keine Art: „irgendein Brett". Wer sich vorher auf eine
     * festlegt, meldet einem Spieler mit einem Laufwerk voll Fichtenstämmen,
     * es fehlten ihm Eichenbretter.
     */
    public record Need<T>(List<T> options, int count) {
    }

    /**
     * Ein Rezept: was herauskommt, wie viel je Durchlauf, und was hineingeht.
     *
     * <p>Gleiche Zutaten gehören zusammen: Acht Bretter sind ein Bedarf über
     * acht und nicht acht Bedarfe über eines.
     */
    public record Recipe<T>(T result, int perCraft, List<Need<T>> needs) {
    }

    /**
     * Ein Schritt: dieses Rezept, so oft, mit dieser Rechnung.
     *
     * <p>{@code consumed} ist die fertige Entnahmeliste — die Auswahl ist
     * getroffen. Der Ausführende entnimmt, was hier steht, und wählt nicht
     * noch einmal; sonst könnte er sich anders entscheiden als der Plan, und
     * der Schritt darüber fände nicht vor, was er erwartet.
     */
    public record Step<T>(T result, int perCraft, long runs, Map<T, Long> consumed) {

        /** Was der Schritt liefert. */
        public long yield() {
            return runs * (long) perCraft;
        }
    }

    /**
     * Woher die Rezepte kommen.
     *
     * <p>Der Bestand steht mit in der Frage, weil es für einen Gegenstand
     * oft mehrere Rezepte gibt und eines davon zu dem passt, was dasteht. Und
     * es ist <b>nicht</b> der Bestand des Netzes, sondern der Stand der
     * Planung: Bretter, die ein früherer Schritt erst herstellt, zählen mit.
     */
    @FunctionalInterface
    public interface Recipes<T> {

        /** Das Rezept für dieses Ziel, oder {@code null}. */
        Recipe<T> find(T target, ToLongFunction<T> available);
    }

    /**
     * Was zu tun ist, und was dafür fehlt.
     *
     * @param steps   von unten nach oben — der erste Schritt ist der, der
     *                sofort laufen kann
     * @param missing was niemand herstellen kann und jemand hinlegen muss
     */
    public record Plan<T>(List<Step<T>> steps, Map<T, Long> missing) {

        /** Ob der Plan aufgeht. */
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    /** Wie ein Teilbedarf ausgegangen ist. */
    private enum Outcome {
        /** Gedeckt — aus dem Bestand oder durch Schritte. */
        COVERED,
        /** Nicht gedeckt, und der Grund steht schon in {@code missing}. */
        REPORTED,
        /**
         * Nicht gedeckt, und niemand hat es eingetragen.
         *
         * <p>Der Fall ist der Kreis: „Barren aus Block" und „Block aus
         * Barren". Dort einzutragen, was gerade oben in Arbeit ist, hieße dem
         * Spieler zu sagen, es fehle ihm das, was er bestellt hat. Statt­
         * dessen trägt die Ebene darüber sich selbst ein — und die ist etwas,
         * das er wirklich hinlegen kann.
         */
        UNREPORTED
    }

    /** Der Stand der Planung, um einen Versuch zurücknehmen zu können. */
    private record Snapshot<T>(Map<T, Long> available, int steps, Map<T, Long> missing) {
    }

    private final Recipes<T> recipes;
    private final ToLongFunction<T> stock;
    private final int maxDepth;
    private final int maxVisits;

    private final Map<T, Long> available = new LinkedHashMap<>();
    private final Map<T, Long> missing = new LinkedHashMap<>();
    private final List<Step<T>> steps = new ArrayList<>();
    private final Deque<T> path = new ArrayDeque<>();
    private int visits;

    private CraftingPlanner(Recipes<T> recipes, ToLongFunction<T> stock,
                            int maxDepth, int maxVisits) {
        this.recipes = recipes;
        this.stock = stock;
        this.maxDepth = maxDepth;
        this.maxVisits = maxVisits;
    }

    /**
     * Der Plan für eine Bestellung.
     *
     * <p><b>Das Ziel selbst wird gebaut</b>, auch wenn es im Speicher liegt.
     * Wer 8 Truhen bestellt, will 8 gebaut haben; ein Auftrag, der sich am
     * eigenen Bestand bedient, wäre beim Anlegen schon fertig und nie
     * geschehen. Nur die Zutaten kommen aus dem Bestand — dafür ist er da.
     *
     * @param amount    wie viel vom Ziel
     * @param maxDepth  wie viele Rezepte tief gesucht wird
     * @param maxVisits wie viele Bedarfe insgesamt betrachtet werden dürfen —
     *                  die Grenze gegen einen Rezeptbaum, der sich verzweigt,
     *                  bis der Server steht
     */
    public static <T> Plan<T> plan(Recipes<T> recipes, ToLongFunction<T> stock,
                                   T target, long amount, int maxDepth, int maxVisits) {
        CraftingPlanner<T> planner = new CraftingPlanner<>(recipes, stock, maxDepth, maxVisits);
        planner.request(target, amount, 0, false);
        return new Plan<>(List.copyOf(planner.steps),
                Collections.unmodifiableMap(new LinkedHashMap<>(planner.missing)));
    }

    /**
     * Deckt einen Bedarf — aus dem Bestand, sonst durch ein Rezept.
     *
     * @param useStock ob der Bestand zählt; beim Ziel selbst tut er es nicht
     */
    private Outcome request(T item, long needed, int depth, boolean useStock) {
        if (needed <= 0) {
            return Outcome.COVERED;
        }
        // Auch das Nachsehen kostet: Ein Baum, der sich bei jeder Zutat in
        // zehn Sorten verzweigt, ist nach acht Ebenen groß genug, um einen
        // Server anzuhalten, ohne je einen Schritt zu ergeben.
        if (++visits > maxVisits) {
            missing.merge(item, needed, Long::sum);
            return Outcome.REPORTED;
        }
        if (useStock) {
            long have = available(item);
            long used = Math.min(have, needed);
            if (used > 0) {
                available.put(item, have - used);
                needed -= used;
            }
            if (needed <= 0) {
                return Outcome.COVERED;
            }
        }
        // Zu tief: Hier wird nicht weitergesucht, und was hier steht, ist
        // etwas, das jemand hinlegen kann. Deshalb steht es in der Liste —
        // anders als beim Kreis.
        if (depth >= maxDepth) {
            missing.merge(item, needed, Long::sum);
            return Outcome.REPORTED;
        }
        if (path.contains(item)) {
            return Outcome.UNREPORTED;
        }
        Recipe<T> recipe = recipes.find(item, this::available);
        if (recipe == null || recipe.perCraft() <= 0 || recipe.needs().isEmpty()) {
            missing.merge(item, needed, Long::sum);
            return Outcome.REPORTED;
        }
        long runs = (needed + recipe.perCraft() - 1) / recipe.perCraft();
        Map<T, Long> consumed = new LinkedHashMap<>();
        Snapshot<T> before = snapshot();
        boolean complete = true;
        boolean reported = false;
        path.push(item);
        try {
            for (Need<T> need : recipe.needs()) {
                Outcome outcome = cover(need, need.count() * runs, depth + 1, consumed);
                if (outcome != Outcome.COVERED) {
                    complete = false;
                    reported |= outcome == Outcome.REPORTED;
                }
            }
        } finally {
            path.pop();
        }
        if (!complete) {
            // Was der halbe Versuch angefasst hat, kommt zurück: Sonst gilt
            // ein Grundstoff als verbraucht, den in Wahrheit niemand bekommen
            // hat, und der nächste Bedarf meldet fälschlich, er sei gedeckt.
            restore(before, false);
            if (!reported) {
                // Alle Zutaten scheiterten am Kreis: Dann ist dieser
                // Gegenstand das, was fehlt.
                missing.merge(item, needed, Long::sum);
            }
            return Outcome.REPORTED;
        }
        // Was ein Durchlauf zu viel liefert, bleibt liegen und deckt den
        // nächsten Bedarf mit. Ohne das liefe der Grundstoff mehrfach: einmal
        // für jeden Zweig, der ihn braucht.
        available.put(item, available(item) + runs * recipe.perCraft() - needed);
        steps.add(new Step<>(item, recipe.perCraft(), runs, Map.copyOf(consumed)));
        return Outcome.COVERED;
    }

    /**
     * Deckt eine Zutat, die mehrere Sorten zulässt.
     *
     * <p>Erst der Bestand — die reichste Sorte zuerst, und <b>gemischt</b>,
     * wenn keine allein reicht: Das Spiel erlaubt Eiche neben Fichte in
     * derselben Truhe, und ein Netz, das darauf besteht, alles aus einer
     * Sorte zu nehmen, verweigert eine Arbeit, die von Hand ginge.
     *
     * <p>Bleibt etwas offen, wird gebaut — die erste Sorte, die aufgeht. Ein
     * Versuch, der scheitert, wird vollständig zurückgenommen, samt seiner
     * Fehlmeldungen; sonst hielte er der Sorte, die gelingt, den Grundstoff
     * vor.
     */
    private Outcome cover(Need<T> need, long amount, int depth, Map<T, Long> consumed) {
        List<T> options = new ArrayList<>(need.options());
        options.sort(Comparator.comparingLong(this::available).reversed());
        long left = amount;
        for (T option : options) {
            if (left <= 0) {
                break;
            }
            long have = available(option);
            long used = Math.min(have, left);
            if (used > 0) {
                available.put(option, have - used);
                consumed.merge(option, used, Long::sum);
                left -= used;
            }
        }
        if (left <= 0) {
            return Outcome.COVERED;
        }
        Snapshot<T> before = snapshot();
        Map<T, Long> consumedBefore = new LinkedHashMap<>(consumed);
        Outcome firstOutcome = null;
        Map<T, Long> firstMissing = null;
        for (T option : options) {
            Outcome outcome = request(option, left, depth, false);
            if (outcome == Outcome.COVERED) {
                consumed.merge(option, left, Long::sum);
                return Outcome.COVERED;
            }
            if (firstOutcome == null) {
                // Die erste Sorte steht in der Fehlzeile, wenn keine geht.
                // Irgendeine muss es sein, und die erste ist die, auf die auch
                // ein Spieler zeigen würde.
                firstOutcome = outcome;
                firstMissing = new LinkedHashMap<>(missing);
            }
            restore(before, true);
            consumed.clear();
            consumed.putAll(consumedBefore);
        }
        missing.clear();
        missing.putAll(firstMissing);
        return firstOutcome;
    }

    private Snapshot<T> snapshot() {
        return new Snapshot<>(new LinkedHashMap<>(available), steps.size(),
                new LinkedHashMap<>(missing));
    }

    private void restore(Snapshot<T> before, boolean withMissing) {
        available.clear();
        available.putAll(before.available());
        while (steps.size() > before.steps()) {
            steps.remove(steps.size() - 1);
        }
        if (withMissing) {
            missing.clear();
            missing.putAll(before.missing());
        }
    }

    /** Der Stand der Planung für einen Gegenstand; beim ersten Blick der Bestand. */
    private long available(T item) {
        return available.computeIfAbsent(item, stock::applyAsLong);
    }
}
