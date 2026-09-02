package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves chemical selections — when Mekanism is present.
 *
 * <p>The same rules as for items and fluids: with no namespace,
 * {@code mekanism} is meant; a pattern searches across all namespaces. The
 * namespace here is <b>not</b> {@code minecraft} — chemicals don't exist in
 * Minecraft, and whoever writes {@code chemical:hydrogen} means the one from
 * Mekanism.
 *
 * <p><b>The identifiers are strings, not chemicals.</b> This is the seam this
 * integration hangs on: everything outside {@code compat/mekanism} talks in
 * terms of {@code "mekanism:hydrogen"} and never a Mekanism type. Otherwise
 * the class carrying such a signature would load third-party classes on
 * initialization — and the mod would no longer start without Mekanism.
 *
 * <p>Without Mekanism every answer is empty. That is not an error but the
 * truth about a pack without the mod; the message for it lives in
 * {@link FnMekanism}.
 *
 * <p><b>No Mekanism type in a signature of this class.</b> Java resolves the
 * classes of a signature at load time; if one stood here, this class could no
 * longer be loaded without Mekanism. Everything that references Mekanism lives
 * in {@link MekRegistry} and is entered only when the mod is present.
 */
public final class Chemicals {

    /** The namespace {@code chemical:} means when none is given. */
    public static final String DEFAULT_NAMESPACE = "mekanism";

    private Chemicals() {
    }

    /**
     * The identifiers behind a selection, as strings.
     *
     * <p>Empty when Mekanism is missing or the selection matches nothing. For
     * the caller the difference is none — both mean "there is nothing here" —
     * and for the message it asks {@link FnMekanism}.
     */
    public static List<String> resolve(Expr expr) {
        if (!FnMekanism.installed()) {
            return List.of();
        }
        return switch (expr) {
            case Expr.Selector selector -> ofSelector(selector);
            case Expr.Except except -> {
                Set<String> ids = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(ids::remove);
                }
                yield List.copyOf(ids);
            }
            case Expr.Amount amount -> resolve(amount.selection());
            case null, default -> List.of();
        };
    }

    private static List<String> ofSelector(Expr.Selector selector) {
        if (selector.kind() != Expr.Selector.Kind.CHEMICAL) {
            return List.of();
        }
        return selector.hasPattern() ? byPattern(selector) : single(selector);
    }

    private static List<String> single(Expr.Selector selector) {
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : DEFAULT_NAMESPACE;
        ResourceLocation id = ResourceLocation.tryBuild(namespace, selector.path());
        if (id == null) {
            return List.of();
        }
        return MekRegistry.has(id) ? List.of(id.toString()) : List.of();
    }

    private static List<String> byPattern(Expr.Selector selector) {
        Pattern pattern = toPattern(selector.path());
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : MekRegistry.keys()) {
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (pattern.matcher(id.getPath()).matches()) {
                found.add(id.toString());
            }
        }
        return List.copyOf(found);
    }

    /**
     * A pattern as a regular expression.
     *
     * <p>Only {@code *} is a wildcard; everything else is taken literally. The
     * same rule as for items — a dot in an identifier is a dot and not "any
     * character".
     */
    private static Pattern toPattern(String path) {
        StringBuilder out = new StringBuilder();
        for (String piece : path.split("\\*", -1)) {
            if (out.length() > 0) {
                out.append(".*");
            }
            out.append(Pattern.quote(piece));
        }
        return Pattern.compile(out.toString());
    }

    /** The plain-text name of a chemical, or its identifier. */
    public static String nameOf(String id) {
        if (!FnMekanism.installed()) {
            return id;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !MekRegistry.has(key)) {
            return id;
        }
        return MekRegistry.name(key);
    }

    /** Whether this chemical exists. */
    public static boolean known(String id) {
        if (!FnMekanism.installed()) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && MekRegistry.has(key);
    }
}
