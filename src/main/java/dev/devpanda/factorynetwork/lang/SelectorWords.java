package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.util.NameDistance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The words that may stand before the colon of a selection.
 *
 * <p><b>The language's half of the resource kinds.</b> Lexer and parser need
 * to know which words exist and nothing else — not where a kind is stored,
 * not how it reaches a machine. That other half needs the game; this one does
 * not, and keeping them apart is what lets the language be read, checked and
 * tested without it.
 *
 * <p><b>Five words belong to the language itself:</b> {@code item},
 * {@code fluid} and {@code chemical} name a kind, {@code tag} and
 * {@code fluidtag} are notations for two of them. They are known from the
 * start. Every further word is added when a kind registers — the registry of
 * kinds calls {@link #add} for it.
 */
public final class SelectorWords {

    /**
     * Why a word cannot be used in this pack.
     *
     * @param message what is wrong, as a sentence
     * @param hint    what helps
     */
    public record Unavailable(String message, String hint) {
    }

    private static final Set<String> BUILT_IN =
            Set.of("item", "fluid", "chemical", "tag", "fluidtag");

    /** Registration order — it decides nothing, but reads better. */
    private static final Set<String> FOREIGN = new LinkedHashSet<>();

    /**
     * What holds for {@code chemical} until somebody says otherwise.
     *
     * <p><b>The one word of the language that needs a mod.</b> Chemicals are
     * Mekanism's, and without a game there is no mod list to find it in. "Not
     * installed" is then the honest default — the other one would let a
     * checker without the game wave through a program that cannot run. As
     * soon as the kind registers, its own answer replaces this one.
     */
    public static final Unavailable CHEMICAL_NEEDS_ITS_MOD = new Unavailable(
            "Chemikalien brauchen Mekanism.",
            "chemical: spricht die Chemikalien von Mekanism an, und die Mod ist "
                    + "in diesem Pack nicht installiert.");

    private static final Map<String, Supplier<Unavailable>> AVAILABILITY = new LinkedHashMap<>();

    static {
        AVAILABILITY.put("chemical", () -> CHEMICAL_NEEDS_ITS_MOD);
    }

    /**
     * All words, built once.
     *
     * <p>The lexer asks at <b>every</b> word in the source text. Assembling
     * the set anew each time would be one allocation per token — and it only
     * changes when a kind registers.
     */
    private static volatile Set<String> all;

    private SelectorWords() {
    }

    /**
     * Makes a word known, with the answer to whether it can be used here.
     *
     * <p>Called by the registry of kinds and by nobody else: a word without a
     * kind behind it would be accepted by the parser and then mean nothing.
     *
     * @param availability {@code null} from it means "usable"
     */
    public static synchronized void add(String word, Supplier<Unavailable> availability) {
        if (!BUILT_IN.contains(word)) {
            FOREIGN.add(word);
        }
        AVAILABILITY.put(word, availability);
        all = null;
    }

    /** What the lexer may glue together as a selection. */
    public static Set<String> all() {
        Set<String> known = all;
        if (known == null) {
            synchronized (SelectorWords.class) {
                Set<String> together = new LinkedHashSet<>(BUILT_IN);
                together.addAll(FOREIGN);
                known = Set.copyOf(together);
                all = known;
            }
        }
        return known;
    }

    /**
     * The written form behind a word, or {@code null}.
     *
     * <p>{@code null} means: this word is not a resource kind. The caller
     * reports that — it knows where in the program it stands.
     */
    public static Expr.Selector.Kind kindOf(String word) {
        return switch (word) {
            case "item" -> Expr.Selector.Kind.ITEM;
            case "fluid" -> Expr.Selector.Kind.FLUID;
            case "chemical" -> Expr.Selector.Kind.CHEMICAL;
            case "fluidtag" -> Expr.Selector.Kind.FLUIDTAG;
            case "tag" -> Expr.Selector.Kind.TAG;
            default -> all().contains(word) ? Expr.Selector.Kind.CUSTOM : null;
        };
    }

    /**
     * Why this word cannot be used in this pack, or {@code null} if it can.
     *
     * <p>A word nobody has given an answer for counts as usable — with the one
     * exception named at {@link #CHEMICAL_NEEDS_ITS_MOD}.
     */
    public static Unavailable unavailable(String word) {
        Supplier<Unavailable> answer;
        synchronized (SelectorWords.class) {
            answer = AVAILABILITY.get(word);
        }
        return answer == null ? null : answer.get();
    }

    /**
     * The word somebody might have meant, or {@code null}.
     *
     * <p>The same yardstick as for connector names and events: a similar
     * name is the best information, the whole list the second best.
     */
    public static String suggest(String wanted) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : all()) {
            int distance = NameDistance.between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return NameDistance.isCloseEnough(wanted, bestDistance) ? best : null;
    }

    /** The known words, sorted — for a message. */
    public static String known() {
        return String.join(", ", new TreeSet<>(all()));
    }
}
