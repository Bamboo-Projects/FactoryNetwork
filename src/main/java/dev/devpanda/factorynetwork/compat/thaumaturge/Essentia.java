package dev.devpanda.factorynetwork.compat.thaumaturge;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.runtime.ResourceKind;
import dev.devpanda.factorynetwork.runtime.ResourceKinds;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Essentia from Thaumaturge as a resource kind: {@code essentia:ignis}.
 *
 * <p>Built like Source from Ars Nouveau (see {@code ArsSource}), but with
 * varieties: one per aspect. Without a namespace, {@code thaumaturge} is
 * meant; {@code essentia:*} matches every aspect. Keys are aspect
 * identifiers as strings, so nothing here loads a Thaumaturge class.
 */
public final class Essentia implements ResourceKind {

    public static final Essentia INSTANCE = new Essentia();

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(FnThaumaturge.MOD_ID, "essentia");

    private Essentia() {
    }

    /** Registers the kind at load time, with or without the mod; see {@code ArsSource.register}. */
    public static void register() {
        ResourceKinds.register(INSTANCE);
        // The aura as measured values: kessel.measure("vis") and so on.
        EssentiaBridge bridge = FnThaumaturge.bridge();
        if (bridge != null) {
            dev.devpanda.factorynetwork.runtime.DeviceMeasures.register("vis", bridge::vis);
            dev.devpanda.factorynetwork.runtime.DeviceMeasures.register("flux", bridge::flux);
            dev.devpanda.factorynetwork.runtime.DeviceMeasures.register("aura", bridge::auraBase);
        }
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String prefix() {
        return "essentia";
    }

    @Override
    public String plural() {
        return "Essenz";
    }

    @Override
    public Class<?> type() {
        return String.class;
    }

    @Override
    public String tag() {
        return "ess";
    }

    @Override
    public String selectionTag() {
        return "esssel";
    }

    @Override
    public String idOf(Object key) {
        return (String) key;
    }

    @Override
    public Object fromId(String id) {
        return id;
    }

    @Override
    public String nameOf(Object key) {
        EssentiaBridge bridge = FnThaumaturge.bridge();
        return bridge == null ? (String) key : bridge.name((String) key);
    }

    @Override
    public List<?> resolve(Expr selector) {
        EssentiaBridge bridge = FnThaumaturge.bridge();
        if (bridge == null) {
            return List.of();
        }
        return switch (selector) {
            case Expr.Selector one -> ofSelector(one, bridge);
            case Expr.Amount amount -> resolve(amount.selection());
            case Expr.Except except -> {
                Set<Object> ids = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(ids::remove);
                }
                yield List.copyOf(ids);
            }
            case null, default -> List.of();
        };
    }

    private static List<String> ofSelector(Expr.Selector selector, EssentiaBridge bridge) {
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;
        if (!selector.hasPattern()) {
            String id = (namespace == null ? FnThaumaturge.MOD_ID : namespace) + ":"
                    + selector.path().toLowerCase(Locale.ROOT);
            return bridge.aspects().contains(id) ? List.of(id) : List.of();
        }
        Pattern pattern = toPattern(selector.path().toLowerCase(Locale.ROOT));
        List<String> found = new ArrayList<>();
        for (String id : bridge.aspects()) {
            int colon = id.indexOf(':');
            if (namespace != null && !id.substring(0, colon).equals(namespace)) {
                continue;
            }
            if (pattern.matcher(id.substring(colon + 1)).matches()) {
                found.add(id);
            }
        }
        return List.copyOf(found);
    }

    /** Only {@code *} is a wildcard, as for items and chemicals. */
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

    @Override
    public ResourceStore newStore() {
        return new EssentiaBuffer();
    }

    @Override
    public MachineAccess machine() {
        EssentiaBridge bridge = FnThaumaturge.bridge();
        return bridge != null ? bridge : MachineAccess.NONE;
    }

    @Override
    public Problem unavailable() {
        if (FnThaumaturge.installed()) {
            return null;
        }
        return new Problem("essentia: spricht die Essenz von Thaumaturge an, "
                + "und die Mod ist in diesem Pack nicht installiert.",
                "Ohne Thaumaturge gibt es keine Essenz zu bewegen.");
    }

    @Override
    public Problem noMatch(String written) {
        Problem missing = unavailable();
        if (missing != null) {
            return missing;
        }
        return new Problem(written == null
                ? "Die Auswahl trifft keinen Aspekt."
                : "Die Auswahl " + written + " trifft keinen Aspekt.",
                "Aspekte heißen wie in Thaumaturge, zum Beispiel essentia:ignis oder essentia:aqua.");
    }
}
