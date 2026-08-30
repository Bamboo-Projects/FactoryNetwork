package dev.devpanda.factorynetwork.press;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Ordnet Forderungen Plätzen zu — jede Forderung bekommt ihren eigenen.
 *
 * <p><b>Warum das nicht die gierige Schleife ist.</b> Wer der Reihe nach den
 * ersten passenden Platz nimmt, verbaut sich: Ein Rezept aus <i>irgendein
 * Metall</i> und <i>Kupfer</i> liegt in einer Presse mit Kupfer und Eisen —
 * nimmt die erste Forderung das Kupfer, findet die zweite nichts mehr,
 * obwohl die Zuordnung offensichtlich aufgeht. Diese Rechnung nimmt zurück
 * und probiert weiter.
 *
 * <p><b>Ohne Minecraft-Typen</b>, wie alles, was sich lohnt zu prüfen: Was
 * „passt" heißt, entscheidet der Aufrufer. Die Presse fragt ein
 * {@code Ingredient}, der Prüflauf vergleicht Buchstaben.
 *
 * <p>Die Suche ist Backtracking und damit im schlechtesten Fall so teuer wie
 * die Zahl der Anordnungen. Das ist hier gleichgültig: Eine Presse hat drei
 * Materialplätze, also höchstens sechs Anordnungen — und sie fragt nur, wenn
 * sich ihr Inhalt geändert hat.
 */
public final class Assignment {

    private Assignment() {
    }

    /**
     * Lässt sich jede Forderung auf einen eigenen Platz legen?
     *
     * @param demands was das Rezept verlangt
     * @param slots   was in der Maschine liegt
     * @param matches ob diese Forderung von diesem Platz erfüllt wird
     */
    public static <D, S> boolean fits(List<D> demands, List<S> slots,
                                      BiPredicate<D, S> matches) {
        return assign(demands, slots, matches) != null;
    }

    /**
     * Dieselbe Suche, aber sie sagt auch, <b>wohin</b>.
     *
     * <p>Gebraucht beim Verbrauchen: Wer drei Zutaten abzieht, muss wissen,
     * aus welchem Platz jede kommt — sonst zieht er zweimal aus demselben.
     *
     * @return je Forderung der Platz, der sie erfüllt, oder {@code null},
     *         wenn es keine Zuordnung gibt
     */
    public static <D, S> int[] assign(List<D> demands, List<S> slots,
                                      BiPredicate<D, S> matches) {
        if (demands.size() > slots.size()) {
            return null;
        }
        List<Integer> used = new ArrayList<>(demands.size());
        if (!search(demands, slots, matches, 0, used)) {
            return null;
        }
        int[] found = new int[used.size()];
        for (int i = 0; i < found.length; i++) {
            found[i] = used.get(i);
        }
        return found;
    }

    private static <D, S> boolean search(List<D> demands, List<S> slots,
                                         BiPredicate<D, S> matches,
                                         int demand, List<Integer> used) {
        if (demand >= demands.size()) {
            return true;
        }
        for (int slot = 0; slot < slots.size(); slot++) {
            if (used.contains(slot) || !matches.test(demands.get(demand), slots.get(slot))) {
                continue;
            }
            used.add(slot);
            if (search(demands, slots, matches, demand + 1, used)) {
                return true;
            }
            // Zurücknehmen: Dieser Platz war für diese Forderung frei, aber
            // eine spätere kommt damit nicht aus.
            used.remove(used.size() - 1);
        }
        return false;
    }
}
