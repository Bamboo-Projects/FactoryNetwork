package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.ReadOnly;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.runtime.ItemSelection;
import dev.devpanda.factorynetwork.runtime.SelectionConditions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The terminal search in the language's own words: whatever stands after
 * {@code filter} in a worker also works in the search field —
 * {@code tag:c/ingots except item:*_nugget}, {@code item:diamond_sword enchanted:sharpness}.
 *
 * <p>A search counts as the language as soon as it holds a colon; plain text
 * and {@code @mod} search by name and mod as before. Resolved on the client,
 * where tags are known as well.
 */
public final class StorageQuery {

    private final Set<Item> items;
    private final Predicate<ItemStack> condition;
    private final String fluid;

    private StorageQuery(Set<Item> items, Predicate<ItemStack> condition, String fluid) {
        this.items = items;
        this.condition = condition;
        this.fluid = fluid;
    }

    /** Whether this search is meant as the language and not as text. */
    public static boolean isLanguage(String query) {
        return query != null && query.indexOf(':') >= 0;
    }

    /** The search, or {@code null} if it is not a valid selection. */
    public static StorageQuery of(String query) {
        Expr expr = ReadOnly.parse(query);
        if (expr == null || !isSelection(expr)) {
            return null;
        }
        String fluid = null;
        if (expr instanceof Expr.Selector selector
                && selector.kind() == Expr.Selector.Kind.FLUID && !selector.hasPattern()) {
            fluid = (selector.hasNamespace() ? selector.namespace() : "minecraft")
                    + ":" + selector.path();
        }
        return new StorageQuery(new HashSet<>(ItemSelection.resolve(expr)),
                SelectionConditions.of(expr), fluid);
    }

    /** Only what may stand after {@code filter}; a number or a call is no search. */
    private static boolean isSelection(Expr expr) {
        return switch (expr) {
            case Expr.Selector ignored -> true;
            case Expr.Except except -> isSelection(except.base());
            case Expr.Condition condition -> isSelection(condition.base());
            default -> false;
        };
    }

    public boolean matches(ItemStack stack) {
        return items.contains(stack.getItem()) && SelectionConditions.accepts(condition, stack);
    }

    public boolean matches(Fluid candidate) {
        return fluid != null && fluid.equals(BuiltInRegistries.FLUID.getKey(candidate).toString());
    }
}
