package dev.devpanda.factorynetwork.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a loadout can do, and how high its values are.
 *
 * <p>Two questions, no more: <i>do I have this ability?</i> and <i>how high is
 * this value?</i> Everything that has slots asks them — the mast, the devices,
 * the display.
 */
public record Loadout(List<Upgrade> installed) {

    public Loadout {
        installed = List.copyOf(installed);
    }

    public static Loadout of(List<? extends Upgrade> installed) {
        // The detour through ArrayList is necessary: List.copyOf of a list of
        // subtypes stays a list of subtypes, and the record wants one of
        // Upgrade.
        return new Loadout(new ArrayList<Upgrade>(installed));
    }

    /**
     * The same calculation from counts instead of from a list.
     *
     * <p><b>Why.</b> A slot holds a stack, and every item in it counts: three
     * range cards in one slot act like three cards. This rule deserves to be
     * tested, and for that it should not sit inside a container that no
     * ordinary test can touch — {@code ItemStack} requires a booted-up
     * Minecraft.
     */
    public static Loadout ofCounts(Map<? extends Upgrade, Integer> counts) {
        List<Upgrade> found = new ArrayList<>();
        counts.forEach((upgrade, count) -> {
            for (int i = 0; i < count; i++) {
                found.add(upgrade);
            }
        });
        return new Loadout(found);
    }

    /**
     * How many times this upgrade is installed.
     *
     * <p>Whoever needs counts — the power calculation does — should not have to
     * derive them back from a value. That would only work as long as there is
     * exactly one kind of card per stat.
     */
    public int count(Upgrade upgrade) {
        int found = 0;
        for (Upgrade one : installed) {
            if (one == upgrade) {
                found++;
            }
        }
        return found;
    }

    /** Is a module of this kind installed in it? */
    public boolean has(Ability ability) {
        return installed.contains(ability);
    }

    /**
     * The sum of all cards on this stat.
     *
     * <p>Without the Infinity card: its step is zero, and whoever installs it
     * asks {@link #unlimited} instead of this number.
     */
    public int value(Stat stat) {
        int sum = 0;
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat) {
                sum += card.step();
            }
        }
        return sum;
    }

    /** Does one of the cards lift the limit of this stat? */
    public boolean unlimited(Stat stat) {
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat
                    && card.unlimited()) {
                return true;
            }
        }
        return false;
    }
}
