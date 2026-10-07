package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.List;

/**
 * What the language needs to know about a kind of resource.
 *
 * <p><b>The half of a resource kind that needs no game.</b> A program names
 * resources, holds them in variables, counts and compares them and writes
 * them into the log. For all of that it is enough to know what a kind is
 * called, how its resources are named and how a written selection resolves.
 * Where the kind is stored in a network and how it reaches a machine is the
 * other half, and that lives in {@link ResourceKind}, which extends this.
 *
 * <p>The interpreter and the flows work on this type and never on the full
 * one. That is what lets them run without a world — in a test, in a checker,
 * and unchanged on another version of the game.
 *
 * <p><b>The key is an {@code Object}.</b> A common supertype would only exist
 * if all kinds came from the same hand. Which form belongs to this kind is
 * stated by {@link #type()}, and a value checks it on construction.
 *
 * <p><b>A kind is a single thing.</b> It is registered once, and equality is
 * identity — {@code ==} rather than {@code equals}.
 */
public interface Kind {

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
     * restarts and must not equal any other — the registry checks both.
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
     * Something a kind has to tell the player, with what they can do about it.
     *
     * @param message what is wrong, as a sentence
     * @param hint    what helps
     */
    record Problem(String message, String hint) {
    }

    /**
     * Why this kind cannot be used in this pack, or {@code null} if it can.
     *
     * <p><b>A kind is registered even when its mod is missing</b> — otherwise
     * a program naming it would hear "no such resource kind" and the player
     * would hunt for a typo. What the mod's absence does mean is said here,
     * and the core passes it on wherever a selection of this kind turns up.
     */
    default Problem unavailable() {
        return null;
    }

    /**
     * What to say when a selection of this kind matches nothing.
     *
     * <p>In the kind's own words: only it knows whether a missing namespace
     * means its own mod, and what a player most likely mistyped.
     *
     * @param written the selection as the program wrote it, or {@code null}
     */
    default Problem noMatch(String written) {
        return new Problem("Die Auswahl trifft kein " + prefix() + ".",
                "Gibt es das in diesem Pack?");
    }

    /** The unit amounts of this kind are counted in, as shown after a number; empty for pieces. */
    default String unit() {
        return "";
    }
}
