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
 * Breaks an order into steps.
 *
 * <p>The single-stage fabricator could build a chest if planks were on hand.
 * If none were, it said so — even when a stack of logs sat in the drive and
 * the path there was a single recipe. The planner walks that path: for each
 * missing ingredient it asks whether the network can make it itself, and does
 * so until it arrives at something someone has to put down.
 *
 * <p><b>It knows no items.</b> What a target is is decided by the caller via
 * {@code T} — in the game an {@code Item}, in the test a string. This is not
 * an end in itself: the cases where such a recursion fails — a cycle, a chain
 * too deep, a surplus that goes to waste, an ingredient with several allowed
 * options — can thus be demonstrated without a loaded world.
 *
 * <p><b>The plan is not kept.</b> The controller recomputes it every crafting
 * tick. A stored plan would be wrong from the moment a worker stores
 * something — and that is exactly what workers do all day. See
 * {@code entscheidungen.md}, „Der Plan wird gerechnet, nicht gemerkt".
 */
public final class CraftingPlanner<T> {

    /**
     * An ingredient: this much of one of these options.
     *
     * <p><b>Options in the plural</b>, because an ingredient in Minecraft is a
     * choice and not a type: "any plank". Whoever commits to one in advance
     * tells a player with a drive full of spruce logs that they are missing
     * oak planks.
     */
    public record Need<T>(List<T> options, int count) {
    }

    /**
     * A recipe: what comes out, how much per run, and what goes in.
     *
     * <p>Identical ingredients belong together: eight planks are one need for
     * eight and not eight needs for one.
     *
     * <p>{@code station} names the recipe type when a machine is needed —
     * {@code minecraft:smelting} for instance. Empty means: at the fabricator,
     * in one go.
     */
    public record Recipe<T>(T result, int perCraft, List<Need<T>> needs, String station) {

        /**
         * A recipe without a station — it runs at the fabricator.
         *
         * <p>The more common case, and the one that existed first.
         */
        public Recipe(T result, int perCraft, List<Need<T>> needs) {
            this(result, perCraft, needs, "");
        }
    }

    /**
     * A step: this recipe, this many times, with this calculation.
     *
     * <p>{@code consumed} is the finished withdrawal list — the choice is
     * made. The executor withdraws what is listed here and does not choose
     * again; otherwise it could decide differently from the plan, and the step
     * above it would not find what it expects.
     *
     * <p>{@code station} says <b>where</b> it runs: empty for the fabricator,
     * otherwise the id of a recipe type like {@code minecraft:smelting}. The
     * planner does not understand it — it passes through what the recipe source
     * attached. The difference only matters at execution: what runs at the
     * fabricator is done in one go; what needs a machine also needs time.
     */
    public record Step<T>(T result, int perCraft, long runs, Map<T, Long> consumed,
                         String station) {

        /** What the step yields. */
        public long yield() {
            return runs * (long) perCraft;
        }
    }

    /**
     * Where the recipes come from.
     *
     * <p>The stock is part of the question, because for an item there are
     * often several recipes and one of them fits what is on hand. And it is
     * <b>not</b> the network's stock but the state of the planning: planks
     * that an earlier step only produces count too.
     */
    @FunctionalInterface
    public interface Recipes<T> {

        /** The recipe for this target, or {@code null}. */
        Recipe<T> find(T target, ToLongFunction<T> available);
    }

    /**
     * What is to be done, and what is missing for it.
     *
     * @param steps   from bottom to top — the first step is the one that can
     *                run immediately
     * @param missing what no one can make and someone has to put down
     */
    public record Plan<T>(List<Step<T>> steps, Map<T, Long> missing) {

        /** Whether the plan works out. */
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    /** How a sub-need turned out. */
    private enum Outcome {
        /** Covered — from stock or by steps. */
        COVERED,
        /** Not covered, and the reason is already in {@code missing}. */
        REPORTED,
        /**
         * Not covered, and no one has recorded it.
         *
         * <p>The case is the cycle: "ingot from block" and "block from ingot".
         * To record there what is currently in progress above would mean
         * telling the player they are missing what they ordered. Instead the
         * level above records itself — and that is something they can actually
         * put down.
         */
        UNREPORTED
    }

    /** The state of the planning, so an attempt can be undone. */
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
     * The plan for an order.
     *
     * <p><b>The target itself is built</b>, even when it is in storage.
     * Whoever orders 8 chests wants 8 built; a job that helps itself to its
     * own stock would already be done when created and never happen. Only the
     * ingredients come from the stock — that is what it is for.
     *
     * @param amount    how much of the target
     * @param maxDepth  how many recipes deep the search goes
     * @param maxVisits how many needs may be considered in total — the limit
     *                  against a recipe tree that branches until the server
     *                  stalls
     */
    public static <T> Plan<T> plan(Recipes<T> recipes, ToLongFunction<T> stock,
                                   T target, long amount, int maxDepth, int maxVisits) {
        CraftingPlanner<T> planner = new CraftingPlanner<>(recipes, stock, maxDepth, maxVisits);
        planner.request(target, amount, 0, false);
        return new Plan<>(List.copyOf(planner.steps),
                Collections.unmodifiableMap(new LinkedHashMap<>(planner.missing)));
    }

    /**
     * Whether the stock yields a recipe's ingredients.
     *
     * <p>Roughly reckoned: two needs that allow the same option both count it.
     * That suffices for the question it is meant for — which of several
     * recipes comes closest to the stock. How much is really covered the
     * planner computes itself, and it computes it exactly.
     *
     * <p>Here and not in the recipe source, because there are now several
     * sources and each asks the same question.
     */
    public static <T> boolean covers(Recipe<T> recipe, ToLongFunction<T> available) {
        for (Need<T> need : recipe.needs()) {
            long have = 0;
            for (T option : need.options()) {
                have += available.applyAsLong(option);
            }
            if (have < need.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Several recipe sources as one.
     *
     * <p>Crafting table and furnace can make the same thing — nine ingots from
     * one block, one ingot from raw ore. <b>The stock decides</b>, as already
     * within a single source: the first recipe whose ingredients are on hand
     * is taken, and otherwise the first one at all.
     */
    @SafeVarargs
    public static <T> Recipes<T> anyOf(Recipes<T>... sources) {
        List<Recipes<T>> all = List.of(sources);
        return (target, available) -> {
            Recipe<T> first = null;
            for (Recipes<T> source : all) {
                Recipe<T> found = source.find(target, available);
                if (found == null) {
                    continue;
                }
                if (first == null) {
                    first = found;
                }
                if (covers(found, available)) {
                    return found;
                }
            }
            return first;
        };
    }

    /**
     * Covers a need — from stock, otherwise by a recipe.
     *
     * @param useStock whether the stock counts; for the target itself it does not
     */
    private Outcome request(T item, long needed, int depth, boolean useStock) {
        if (needed <= 0) {
            return Outcome.COVERED;
        }
        // Even the looking-up costs: a tree that branches into ten options at
        // every ingredient is, after eight levels, large enough to stall a
        // server without ever yielding a single step.
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
        // Too deep: no further search happens here, and what stands here is
        // something someone can put down. That is why it is in the list —
        // unlike with the cycle.
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
            // What the half attempt touched comes back: otherwise a base
            // material counts as consumed that in truth no one received, and
            // the next need falsely reports that it is covered.
            restore(before, false);
            if (!reported) {
                // All ingredients failed at the cycle: then this item is what
                // is missing.
                missing.merge(item, needed, Long::sum);
            }
            return Outcome.REPORTED;
        }
        // What a run delivers in excess stays and helps cover the next need.
        // Without that the base material would run several times: once for
        // each branch that needs it.
        available.put(item, available(item) + runs * recipe.perCraft() - needed);
        steps.add(new Step<>(item, recipe.perCraft(), runs, Map.copyOf(consumed),
                recipe.station()));
        return Outcome.COVERED;
    }

    /**
     * Covers an ingredient that allows several options.
     *
     * <p>First the stock — the richest option first, and <b>mixed</b> if none
     * suffices alone: the game allows oak next to spruce in the same chest,
     * and a network that insists on taking everything from one option refuses
     * work that would go by hand.
     *
     * <p>If something remains open, it is built — the first option that works
     * out. An attempt that fails is fully undone, together with its error
     * reports; otherwise it would withhold the base material from the option
     * that succeeds.
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
                // The first option goes in the error line if none works. It
                // has to be some one, and the first is the one a player would
                // point to as well.
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

    /** The state of the planning for an item; at first glance the stock. */
    private long available(T item) {
        return available.computeIfAbsent(item, stock::applyAsLong);
    }
}
