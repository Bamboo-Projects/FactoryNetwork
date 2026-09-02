package dev.devpanda.factorynetwork.press;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Assigns demands to slots — each demand gets its own.
 *
 * <p><b>Why this is not the greedy loop.</b> Whoever takes the first matching
 * slot in turn boxes themselves in: a recipe of <i>any metal</i> and
 * <i>copper</i> lies in a press with copper and iron — if the first demand
 * takes the copper, the second finds nothing left, even though the assignment
 * obviously works out. This computation backtracks and keeps trying.
 *
 * <p><b>Without Minecraft types</b>, like everything worth testing: what
 * "matches" means is decided by the caller. The press asks an
 * {@code Ingredient}, the test run compares letters.
 *
 * <p>The search is backtracking and thus in the worst case as expensive as the
 * number of arrangements. That does not matter here: a press has three
 * material slots, so at most six arrangements — and it only asks when its
 * contents have changed.
 */
public final class Assignment {

    private Assignment() {
    }

    /**
     * Can every demand be placed on a slot of its own?
     *
     * @param demands what the recipe requires
     * @param slots   what is in the machine
     * @param matches whether this demand is satisfied by this slot
     */
    public static <D, S> boolean fits(List<D> demands, List<S> slots,
                                      BiPredicate<D, S> matches) {
        return assign(demands, slots, matches) != null;
    }

    /**
     * The same search, but it also says <b>where to</b>.
     *
     * <p>Needed when consuming: whoever draws off three ingredients must know
     * which slot each comes from — otherwise they draw twice from the same one.
     *
     * @return per demand the slot that satisfies it, or {@code null} when
     *         there is no assignment
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
            // Backtrack: this slot was free for this demand, but a later one
            // cannot manage with it.
            used.remove(used.size() - 1);
        }
        return false;
    }
}
