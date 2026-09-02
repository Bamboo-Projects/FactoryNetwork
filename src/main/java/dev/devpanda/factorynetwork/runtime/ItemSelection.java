package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves a selection expression to item kinds.
 *
 * <p>This is where the case lands that the selection rules were designed for
 * in the first place. AllTheOres generates several hundred entries from 31
 * material sets, and their names are not built uniformly: the form comes last
 * ({@code aluminum_ingot}), stone type and state come first
 * ({@code deepslate_aluminum_ore}, {@code raw_aluminum}), intermediate products
 * carry both ({@code dirty_aluminum_dust}). That is why the wildcard may stand
 * at any position.
 *
 * <p><b>Resolved once and then remembered.</b> A pattern over twenty thousand
 * entries must not keep the server busy every tick — the same calculation as
 * for the storage.
 */
public final class ItemSelection {

    private static final Map<String, List<Item>> CACHE = new HashMap<>();

    /** The cache is only valid as long as the registry is unchanged. */
    public static void invalidate() {
        CACHE.clear();
    }

    public static List<Item> resolve(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> cached(selector);
            case Expr.Except except -> {
                Set<Item> items = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(items::remove);
                }
                yield List.copyOf(items);
            }
            case Expr.Amount amount -> resolve(amount.selection());
            default -> List.of();
        };
    }

    private static List<Item> cached(Expr.Selector selector) {
        String key = key(selector);
        List<Item> known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        List<Item> resolved = compute(selector);
        CACHE.put(key, resolved);
        return resolved;
    }

    private static String key(Expr.Selector selector) {
        return selector.kind() + "|" + selector.namespace() + "|" + selector.path();
    }

    private static List<Item> compute(Expr.Selector selector) {
        if (selector.kind() == Expr.Selector.Kind.TAG) {
            return fromTag(selector);
        }
        // "all" is every item — the same as item:*, and through the same
        // cache.
        //
        // <p><b>Only here, where resolution is required.</b> A plain
        // move all does not even ask: there, an empty list means "no
        // filter", and the chest is cleared out without anyone walking the
        // registry. This resolution is only needed for all except …, and
        // there it costs as much as item:* except …, which the language
        // allows anyway.
        if (selector.kind() == Expr.Selector.Kind.ALL) {
            return List.copyOf(BuiltInRegistries.ITEM.stream().toList());
        }
        if (selector.kind() != Expr.Selector.Kind.ITEM) {
            // Fluids and chemicals are written, but not yet wired up. Treating
            // them silently as empty here would be worse than the hint in the
            // runtime.
            return List.of();
        }
        if (!selector.hasPattern()) {
            return single(selector);
        }
        return byPattern(selector);
    }

    private static List<Item> single(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return List.of();
        }
        return List.of(BuiltInRegistries.ITEM.get(id));
    }

    /**
     * Patterns over names.
     *
     * <p><b>Without a namespace, all of them are searched</b>, unlike with a
     * literal name. A pattern is a search, and a search that silently stops
     * at vanilla finds almost nothing in a large pack. If a namespace is
     * given, only that one counts.
     */
    private static List<Item> byPattern(Expr.Selector selector) {
        Pattern pattern = toPattern(selector.path());
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;

        List<Item> found = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (pattern.matcher(id.getPath()).matches()) {
                found.add(BuiltInRegistries.ITEM.get(id));
            }
        }
        return List.copyOf(found);
    }

    /** Translates a pattern into a regular expression; only {@code *} counts. */
    private static Pattern toPattern(String path) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static List<Item> fromTag(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null) {
            return List.of();
        }
        TagKey<Item> tag = ItemTags.create(id);
        List<Item> found = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders ->
                holders.forEach(holder -> found.add(holder.value())));
        return List.copyOf(found);
    }

    private ItemSelection() {
    }
}
