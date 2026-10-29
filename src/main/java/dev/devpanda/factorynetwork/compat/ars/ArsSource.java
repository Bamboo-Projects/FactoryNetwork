package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.runtime.ResourceKind;
import dev.devpanda.factorynetwork.runtime.ResourceKinds;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Source from Ars Nouveau as a resource kind.
 *
 * <p><b>The proof the open registry was built for.</b> This kind comes from a
 * third-party mod, and the core knows nothing of it: not a line in
 * {@code ResourceKinds}, none in the parser, none in the value model. What it
 * costs sits entirely in this folder — four classes and one call at load time.
 *
 * <p><b>It is written as {@code source:source}.</b> That reads redundantly and
 * is still correct: the prefix form is the only one a third-party mod can get.
 * The bare word — as with {@code power} — requires a keyword in the lexer, and
 * keywords belong to the language: they live in the grammar for VS Code, in the
 * EBNF and in the manual, and none of these places can a mod extend at load
 * time. On top of that, a bare {@code source} would collide with a filter
 * template of that name, which is allowed to exist today.
 *
 * <p><b>One variety, one key.</b> Source is a single undivided quantity like
 * power, not an assortment like items. The key is therefore the string
 * {@code "source"} and not a type from Ars Nouveau: a signature with a
 * third-party class would resolve it at load time, even in a pack without the
 * mod. The same caution as with the chemicals.
 */
public final class ArsSource implements ResourceKind {

    public static final ArsSource INSTANCE = new ArsSource();

    /** The one variety of Source, as its key. */
    public static final String KEY = "source";

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(FnArs.MOD_ID, "source");

    private ArsSource() {
    }

    /**
     * Registers the kind — once, at load time.
     *
     * <p>Even without Ars Nouveau: otherwise {@code source:source} in a pack
     * without the mod would mean "no such resource kind" instead of "this mod
     * is missing", and the player would go hunting for the typo. What is
     * missing without the mod is access to the machines — and that reports
     * itself.
     */
    public static void register() {
        ResourceKinds.register(INSTANCE);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String prefix() {
        return "source";
    }

    @Override
    public String plural() {
        return "Source";
    }

    @Override
    public Class<?> type() {
        return String.class;
    }

    @Override
    public String tag() {
        return "src";
    }

    @Override
    public String selectionTag() {
        return "srcsel";
    }

    @Override
    public String idOf(Object key) {
        return (String) key;
    }

    @Override
    public Object fromId(String id) {
        return KEY;
    }

    @Override
    public String nameOf(Object key) {
        return "Source";
    }

    /**
     * There is exactly one variety, and it shares its kind's name.
     *
     * <p>{@code source:source} matches it. Anything else after the colon
     * matches nothing — and an empty list is the honest answer to that, not a
     * silent reinterpretation as the one variety.
     */
    @Override
    public List<?> resolve(Expr selector) {
        return switch (selector) {
            case Expr.Selector one -> KEY.equals(one.path())
                    ? List.of(KEY) : List.of();
            case Expr.Amount amount -> resolve(amount.selection());
            case Expr.Except except -> resolve(except.base());
            case null, default -> List.of();
        };
    }

    @Override
    public ResourceStore newStore() {
        return new SourceBuffer();
    }

    @Override
    public MachineAccess machine() {
        SourceBridge bridge = FnArs.bridge();
        return bridge != null ? bridge : WITHOUT_ARS;
    }

    /**
     * Machines are reachable in principle, there is just no Source in them.
     *
     * <p>Deliberately not {@link MachineAccess#NONE}: that one means "this kind
     * reaches no machine" and earns its own message. Without Ars Nouveau the
     * kind's {@code unavailable()} says what is missing, and every machine
     * answers zero.
     */
    private static final MachineAccess WITHOUT_ARS = new MachineAccess() {

        @Override
        public long count(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                          net.minecraft.core.Direction side, java.util.Collection<?> keys) {
            return 0;
        }

        @Override
        public long fill(ResourceStore from, net.minecraft.world.level.Level level,
                         net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                         java.util.Collection<?> keys, long limit) {
            return 0;
        }

        @Override
        public long drain(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                          net.minecraft.core.Direction side, java.util.Collection<?> keys,
                          ResourceStore into, long limit) {
            return 0;
        }
    };
}
