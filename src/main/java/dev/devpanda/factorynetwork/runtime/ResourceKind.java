package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.resources.Identifier;

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
 * <p><b>Two halves.</b> What the language needs — the word, the names, how a
 * selection resolves — is declared in {@link Kind}, and the interpreter sees
 * nothing but that. This interface adds what needs the game: the identifier,
 * the store in the network and the access to machines.
 */
public interface ResourceKind extends Kind {

    /** The identifier of this kind, for example {@code arsnouveau:source}. */
    Identifier id();

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
     * <p>Items and fluids also return nothing here: their path currently
     * lives in {@code WorldHost} and only moves here with slice 3. See
     * {@code maschinenzugriff.md}.
     */
    default dev.devpanda.factorynetwork.network.MachineAccess machine() {
        return dev.devpanda.factorynetwork.network.MachineAccess.NONE;
    }

    /**
     * The full kind behind what a value carries.
     *
     * <p>A value knows only the language's half. Every kind that exists was
     * registered as a full one, though — there is no other way in — so the
     * way back is a matter of saying so.
     */
    static ResourceKind full(Kind kind) {
        return (ResourceKind) kind;
    }

    /**
     * The kind behind a written form, or {@code null}.
     *
     * <p>{@code null} means "no resource kind" and not "items": {@code power}
     * and {@code all} carry no type, and whoever read them as items would
     * move the wrong thing for {@code power}.
     */
    static ResourceKind of(Expr.Selector.Kind written) {
        return full(Kinds.of(written));
    }

    /**
     * The kind behind a selection expression, or {@code null}.
     *
     * <p>The version that also covers third-party kinds: for them the answer
     * stands in the prefix and not in the enum.
     */
    static ResourceKind of(Expr.Selector selector) {
        return full(Kinds.of(selector));
    }

    /** The same question for a selection that is still present as text. */
    static ResourceKind of(Value.Request.Kind written) {
        return full(Kinds.of(written));
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
        return full(Kinds.orItems(written));
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
        return full(Kinds.of(value));
    }
}
