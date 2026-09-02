package dev.devpanda.factorynetwork.lang;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The items a program is currently talking about.
 *
 * <p><b>What for:</b> whether a machine accepts a particular item cannot be
 * enumerated — {@code IItemHandler} has no API for it. One can only simulate an
 * insertion attempt with a concrete item. For that you need candidates, and
 * those stand in the program: whoever types {@code item:iron_ore} is asking
 * something about iron ore, not about the twenty thousand other kinds.
 *
 * <p><b>Over the text and not over the tree</b> — the same decision as with
 * {@link Definitions#references}: a selector expression stands in filters,
 * arguments, conditions, and statements, and finding it everywhere in the tree
 * would mean handling each kind of expression separately. The text search, in
 * return, occasionally finds a hit in a comment. That is harmless here: one
 * candidate too many costs one simulated insertion attempt.
 *
 * <p>Tags are left out. They stand for many kinds, and which those are only the
 * registry knows — the probe would thereby become as expensive as the registry
 * variant that the design rejects.
 */
public final class ItemCandidates {

    /**
     * {@code item:mekanism/iron_ore} or {@code item:iron_ore}.
     *
     * <p>Without a wildcard: {@code item:*_dust} stands for many kinds and is
     * thereby a tag in a different spelling.
     */
    private static final Pattern ITEM_SELECTOR =
            Pattern.compile("\\bitem:([a-z0-9_.-]+(?:/[a-z0-9_./-]+)?)");

    /** More candidates cost more without saying more. */
    private static final int MAX = 24;

    private ItemCandidates() {
    }

    /**
     * The same for {@code fluid:}.
     *
     * <p>Collected separately and not in one pot: a container is probed with
     * fluids and a compartment with items — whoever mixed the two would get two
     * lists full of candidates that can fit nowhere.
     */
    private static final Pattern FLUID_SELECTOR =
            Pattern.compile("\\bfluid:([a-z0-9_.-]+(?:/[a-z0-9_./-]+)?)");

    /** What {@code item:} literals stand in the whole project. */
    public static Set<String> of(Project project) {
        return collectAll(project, ITEM_SELECTOR);
    }

    /** And what {@code fluid:} literals. */
    public static Set<String> fluidsOf(Project project) {
        return collectAll(project, FLUID_SELECTOR);
    }

    private static Set<String> collectAll(Project project, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        for (String name : project.names()) {
            collect(project.source(name), found, pattern);
            if (found.size() >= MAX) {
                break;
            }
        }
        return found;
    }

    private static void collect(String source, Set<String> into, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find() && into.size() < MAX) {
            into.add(matcher.group(1));
        }
    }
}
