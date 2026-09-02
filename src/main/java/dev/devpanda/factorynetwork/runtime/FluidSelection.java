package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves fluid selections.
 *
 * <p>The same rules as for items: without a namespace, {@code minecraft} is
 * meant, whereas a pattern searches across all namespaces.
 *
 * <p>One difference stands out: <b>only source fluids count.</b> The registry
 * lists water and flowing water as two entries; a pattern like
 * {@code fluid:*water*} would otherwise find both, and the player would be
 * offered one type twice, one of which cannot be stored anywhere.
 */
public final class FluidSelection {

    private static final Map<String, List<Fluid>> CACHE = new HashMap<>();

    /** After a datapack reload the tags are no longer correct. */
    public static void invalidate() {
        CACHE.clear();
    }

    public static List<Fluid> resolve(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> cached(selector);
            case Expr.Except except -> {
                Set<Fluid> fluids = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(fluids::remove);
                }
                yield List.copyOf(fluids);
            }
            case Expr.Amount amount -> resolve(amount.selection());
            default -> List.of();
        };
    }

    private static List<Fluid> cached(Expr.Selector selector) {
        String key = selector.kind() + "|" + selector.namespace() + "|" + selector.path();
        List<Fluid> known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        List<Fluid> resolved = compute(selector);
        CACHE.put(key, resolved);
        return resolved;
    }

    private static List<Fluid> compute(Expr.Selector selector) {
        // tag: stays silent here: an item tag matches no fluid, and
        // fluidtag: exists for exactly that reason.
        if (selector.kind() == Expr.Selector.Kind.FLUIDTAG) {
            return fromTag(selector);
        }
        if (selector.kind() != Expr.Selector.Kind.FLUID) {
            return List.of();
        }
        if (!selector.hasPattern()) {
            return single(selector);
        }
        return byPattern(selector);
    }

    private static List<Fluid> single(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
            return List.of();
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return isSource(fluid) ? List.of(fluid) : List.of();
    }

    private static List<Fluid> byPattern(Expr.Selector selector) {
        Pattern pattern = toPattern(selector.path());
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;

        List<Fluid> found = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.FLUID.keySet()) {
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (!pattern.matcher(id.getPath()).matches()) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            if (isSource(fluid)) {
                found.add(fluid);
            }
        }
        return List.copyOf(found);
    }

    private static List<Fluid> fromTag(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null) {
            return List.of();
        }
        TagKey<Fluid> tag = FluidTags.create(id);
        List<Fluid> found = new ArrayList<>();
        BuiltInRegistries.FLUID.getTag(tag).ifPresent(holders ->
                holders.forEach(holder -> {
                    if (isSource(holder.value())) {
                        found.add(holder.value());
                    }
                }));
        return List.copyOf(found);
    }

    /** Flowing water is not a type that could be stored. */
    private static boolean isSource(Fluid fluid) {
        return fluid != net.minecraft.world.level.material.Fluids.EMPTY
                && fluid.isSource(fluid.defaultFluidState());
    }

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

    private FluidSelection() {
    }
}
