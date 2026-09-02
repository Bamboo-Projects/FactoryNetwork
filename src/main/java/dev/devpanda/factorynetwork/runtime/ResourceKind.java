package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What kind of resource a value refers to.
 *
 * <p>Previously this information lived nowhere: there were three value pairs —
 * {@code ItemValue}/{@code Selection}, {@code FluidValue}/{@code FluidSelection},
 * {@code ChemicalValue}/{@code ChemicalSelection} — and every place that
 * handles values carried all three side by side. Measured, that came to ten
 * places for a single new kind, nine of them copies of one another
 * (see {@code ressourcenarten.md}, section 2). Copies drift apart, and that is
 * exactly what had happened: {@code move} chose its path by kind and knew the
 * resolved fluid selection, but not the resolved chemical selection.
 *
 * <p>Now the value carries its kind as a field, and whatever differs per kind
 * lives here — in one place and complete.
 *
 * <p><b>And the list of kinds is open.</b> Since 26 August it has been decided
 * that third-party mods may extend the language: whoever brings their own
 * kind implements this interface and registers it with {@link ResourceKinds}.
 * The core does not need to be touched for that. The reasoning, and what is
 * irreversible about it, is in {@code entscheidungen.md}, "Fremde Mods dürfen
 * die Sprache erweitern" (third-party mods may extend the language).
 *
 * <p><b>The key is an {@code Object}.</b> A common supertype would only exist
 * if all kinds came from the same hand — an item is an {@code Item}, a
 * chemical is a {@link String}, because a signature with a Mekanism type would
 * force the class to resolve it at load time. Which form belongs to this kind
 * is stated by {@link #type()}, and the value checks it on construction.
 *
 * <p><b>A kind is a single thing.</b> It is registered once, and equality is
 * identity — {@code ==} rather than {@code equals}. The registry rejects two
 * entries with the same prefix instead of shadowing one of them.
 */
public interface ResourceKind {

    /** The identifier of this kind, for example {@code arsnouveau:source}. */
    ResourceLocation id();

    /**
     * What stands before the colon — and how the entry is queried afterwards.
     *
     * <p>The same word in both places, and that is not thrift: whoever writes
     * {@code chemical:mekanism/hydrogen} reads the entry with
     * {@code it.chemical}. Two words for the same kind would be two things to
     * remember.
     */
    String prefix();

    /** How a selection of this kind is counted in the log. */
    String plural();

    /** What a resource of this kind is recognised by. */
    Class<?> type();

    /**
     * What this kind is called on disk.
     *
     * <p>For the built-in three the name is <b>irregular</b>, and it stays
     * that way: {@code item} versus {@code sel}, but {@code chem} versus
     * {@code chemsel} — grown, not designed. A waiting flow sits in the world
     * with these names; straightening them out would mean letting old worlds
     * lose their flows. Pinned down in {@code ValueCodecFormatTest}.
     *
     * <p>A third-party kind chooses its own. It must stay the same across
     * restarts and must not equal any other — {@link ResourceKinds} checks
     * both.
     */
    String tag();

    /** The same for a selection. */
    String selectionTag();

    /** The identifier of a resource as it appears on disk. */
    String idOf(Object key);

    /**
     * The resource behind an identifier.
     *
     * <p>Whether this is checked against a registry is up to the kind. Items
     * and fluids fail with a message when they no longer exist: a variable
     * that calculations continue with must not secretly turn into something
     * else. Chemicals do not — their registry belongs to Mekanism, and
     * without the mod it does not exist.
     */
    Object fromId(String id);

    /** How a single resource appears in the log. */
    String nameOf(Object key);

    /**
     * What a selection expression of this kind resolves to.
     *
     * <p>The resolvers stay separate — they are not twins but different
     * registries with different caches. What comes together is solely the
     * question of which of them a given place needs.
     */
    List<?> resolve(Expr selector);

    /**
     * The store in which this kind lives in the network.
     *
     * <p><b>None by default.</b> A kind may be movable without being storable
     * — and {@link ResourceStore#NONE} is the honest answer to that: it
     * accepts nothing and hands out nothing. Whoever wants to store supplies
     * a new one per network here.
     */
    default ResourceStore newStore() {
        return ResourceStore.NONE;
    }

    /**
     * How this kind is read from and written to a third-party machine.
     *
     * <p><b>The second axis.</b> {@link #newStore()} says where the kind lives
     * in the network; this says how it gets there and back out. A kind needs
     * both to be useful in a program.
     *
     * <p><b>None by default.</b> A kind may live in the network without any
     * machine knowing it — just as it may move without being storable.
     * {@code move} then says so and does not return a silent zero.
     *
     * <p>The built-in three also return nothing here: their path currently
     * lives in {@code WorldHost} and only moves here with slice 3. See
     * {@code maschinenzugriff.md}.
     */
    default dev.devpanda.factorynetwork.network.MachineAccess machine() {
        return dev.devpanda.factorynetwork.network.MachineAccess.NONE;
    }

    /**
     * The kind behind a written form, or {@code null}.
     *
     * <p>{@code null} means "no resource kind" and not "items": {@code power}
     * and {@code all} carry no type, and whoever read them as items would
     * move the wrong thing for {@code power}.
     */
    static ResourceKind of(Expr.Selector.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ResourceKinds.ITEM;
            case FLUID, FLUIDTAG -> ResourceKinds.FLUID;
            case CHEMICAL -> ResourceKinds.CHEMICAL;
            // A registered third-party kind cannot be determined from the
            // written form alone — which one it is stands in the prefix.
            // Whoever needs it uses of(Expr.Selector) rather than this one.
            case CUSTOM, POWER, ALL -> null;
        };
    }

    /**
     * The kind behind a selection expression, or {@code null}.
     *
     * <p>The version that also covers third-party kinds: for them the answer
     * stands in the prefix and not in the enum.
     */
    static ResourceKind of(Expr.Selector selector) {
        if (selector == null) {
            return null;
        }
        return selector.kind() == Expr.Selector.Kind.CUSTOM
                ? ResourceKinds.byPrefix(selector.prefix())
                : of(selector.kind());
    }

    /** The same question for a selection that is still present as text. */
    static ResourceKind of(Value.Request.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ResourceKinds.ITEM;
            case FLUID, FLUIDTAG -> ResourceKinds.FLUID;
            case CHEMICAL -> ResourceKinds.CHEMICAL;
            case ALL, UNKNOWN -> null;
        };
    }

    /**
     * The same question where an answer is required and {@code null} would
     * not be one.
     *
     * <p>Without a kind, <b>items</b> are meant: {@code all} says so
     * explicitly, a worker without a filter has always done so, and a written
     * form nobody knows is caught during resolution, not here.
     */
    static ResourceKind orItems(Expr.Selector.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ResourceKinds.ITEM : kind;
    }

    /** The same for a selection that is still present as text. */
    static ResourceKind orItems(Value.Request.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ResourceKinds.ITEM : kind;
    }

    /**
     * The kind a value stands for, or {@code null}.
     *
     * <p><b>The place where {@code move} chooses its path.</b> Previously
     * there were two questions for that — one for fluids, one for chemicals
     * — and each knew a different subset of the values: one had received the
     * addition for the resolved selection, the other had not. A chemical from
     * a loop thus ran into the item resolution, matched nothing there, and no
     * selection means <i>everything</i> there.
     */
    static ResourceKind of(Value value) {
        return switch (value) {
            case Value.Resource resource -> resource.kind();
            case Value.Selection selection -> selection.kind();
            case Value.Request request -> of(request.kind());
            case null, default -> null;
        };
    }
}
