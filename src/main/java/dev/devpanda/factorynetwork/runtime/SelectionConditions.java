package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * What a stack must carry to belong to a selection: an enchantment, damage,
 * a potion, a name, a data component.
 *
 * <p>The kinds of a selection still come from {@link ItemSelection}, and the
 * store keeps its index by kind. Only the stacks of those kinds are asked
 * here, one by one — few in practice, because a kind seldom has many
 * variants in a store.
 */
public final class SelectionConditions {

    private SelectionConditions() {
    }

    /** The test for a written selection, or {@code null} when it has no conditions. */
    public static @Nullable Predicate<ItemStack> of(Expr selection) {
        return of(Interpreter.conditionsOf(selection));
    }

    /** The test for these conditions, or {@code null} when there are none. */
    public static @Nullable Predicate<ItemStack> of(List<Value.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        Predicate<ItemStack> all = stack -> true;
        for (Value.Condition condition : conditions) {
            all = all.and(one(condition));
        }
        return all;
    }

    /** True for every stack when there is no test. */
    public static boolean accepts(@Nullable Predicate<ItemStack> test, ItemStack stack) {
        return test == null || test.test(stack);
    }

    private static Predicate<ItemStack> one(Value.Condition condition) {
        return switch (condition.kind()) {
            case "enchanted" -> stack -> enchanted(stack, condition.id());
            case "damaged" -> stack -> damaged(stack, condition.percent(), condition.above());
            case "potion" -> stack -> potion(stack, condition.id());
            case "named" -> stack -> named(stack, condition.text());
            case "component" -> stack -> component(stack, condition.id());
            default -> stack -> false;
        };
    }

    private static boolean enchanted(ItemStack stack, @Nullable String id) {
        ItemEnchantments own = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (id == null) {
            return !own.isEmpty() || !stored.isEmpty();
        }
        Identifier wanted = idOf(id);
        return has(own, wanted) || has(stored, wanted);
    }

    private static boolean has(ItemEnchantments enchantments, @Nullable Identifier wanted) {
        for (Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchantments.keySet()) {
            if (wanted != null && holder.is(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Damaged at all, or more (less) than this share of its durability used. */
    private static boolean damaged(ItemStack stack, @Nullable Double percent, boolean above) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        if (percent == null) {
            return stack.isDamaged();
        }
        double used = 100.0 * stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
        return above ? used > percent : used < percent;
    }

    private static boolean potion(ItemStack stack, @Nullable String id) {
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) {
            return false;
        }
        Identifier wanted = id == null ? null : idOf(id);
        return wanted == null || contents.potion().get().is(wanted);
    }

    private static boolean named(ItemStack stack, @Nullable String text) {
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            return false;
        }
        return text == null || stack.getHoverName().getString().equals(text);
    }

    private static boolean component(ItemStack stack, @Nullable String id) {
        Identifier wanted = id == null ? null : idOf(id);
        if (wanted == null) {
            return false;
        }
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(wanted);
        return type != null && stack.has(type);
    }

    /**
     * The selection that picks out exactly this kind of stack, as one writes
     * it: {@code item:diamond_sword enchanted:sharpness named "Klinge"}.
     * Ctrl+click in the terminal copies it, so nobody has to know component
     * names by heart. Ids in the minecraft namespace are written short.
     */
    public static String selectionFor(ItemStack stack) {
        StringBuilder out = new StringBuilder("item:")
                .append(shortId(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        ItemEnchantments own = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (ItemEnchantments enchantments : List.of(own, stored)) {
            for (Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchantments.keySet()) {
                holder.unwrapKey().ifPresent(key ->
                        out.append(" enchanted:").append(shortId(key.identifier())));
            }
        }
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents != null) {
            contents.potion().flatMap(Holder::unwrapKey).ifPresent(key ->
                    out.append(" potion:").append(shortId(key.identifier())));
        }
        if (stack.isDamaged()) {
            out.append(" damaged");
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            out.append(" named \"").append(stack.getHoverName().getString().replace("\"", "")).append('"');
        }
        return out.toString();
    }

    private static String shortId(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.getNamespace() + "/" + id.getPath();
    }

    /**
     * An id as written: {@code mending}, {@code minecraft:mending} or
     * {@code mekanism/upgrade}. Without a namespace it is {@code minecraft}.
     */
    static @Nullable Identifier idOf(String written) {
        String text = written.toLowerCase(Locale.ROOT);
        int mark = text.indexOf(':');
        if (mark < 0) {
            mark = text.indexOf('/');
        }
        return mark < 0 ? Identifier.tryBuild("minecraft", text)
                : Identifier.tryBuild(text.substring(0, mark), text.substring(mark + 1));
    }
}
