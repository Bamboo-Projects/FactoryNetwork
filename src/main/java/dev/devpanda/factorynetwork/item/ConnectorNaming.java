package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.lang.TokenType;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.text.Normalizer;
import java.util.Locale;

/**
 * How a connector arrives at its name.
 *
 * <p>Separate from the item so that the rules can be checked without a server
 * — the same separation as with {@code NameDistance}.
 */
public final class ConnectorNaming {

    /** Numbering does not continue past this point. */
    private static final int MAX_SUFFIX = 999;

    /**
     * Suggests a name for the machine behind the connector.
     *
     * <p>{@code minecraft:blast_furnace} becomes {@code blast_furnace_1}, and
     * the next furnace {@code blast_furnace_2}. The number comes from the
     * network, not from the gun: otherwise two players with two guns would
     * hand out the same name, and whoever crafts the gun anew would start
     * over at one.
     */
    public static String suggestFor(BlockState machine, FactoryGraph graph) {
        String base = baseName(machine);
        return nextFree(base, graph);
    }

    /** The block name without namespace, as the basis for the suggestion. */
    public static String baseName(BlockState machine) {
        if (machine == null || machine.isAir()) {
            return "device";
        }
        String path = BuiltInRegistries.BLOCK.getKey(machine.getBlock()).getPath();
        return path.isBlank() ? "device" : path;
    }

    /** The smallest free number for a base name. */
    public static String nextFree(String base, FactoryGraph graph) {
        for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
            String candidate = base + "_" + suffix;
            if (!graph.isTaken(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    /**
     * What stands out about an entered name.
     *
     * <p>The gun is the place where names come into being — what passes here
     * appears later in the code. That is why it checks the same rules as the
     * compiler, but it <b>does not block</b>: a name that is a keyword stays
     * allowed, it only needs backticks in the code. That is the decision from
     * {@code sprache.md}, section 6 — names are save data, keywords are not.
     */
    public static Warning check(String name, FactoryGraph graph) {
        String trimmed = normalize(name);
        if (trimmed.isBlank()) {
            return new Warning(Kind.EMPTY, null);
        }
        if (!isValidDeviceName(trimmed)) {
            return new Warning(Kind.NOT_AN_IDENTIFIER, null);
        }
        if (TokenType.isKeyword(trimmed)) {
            return new Warning(Kind.KEYWORD, null);
        }
        if (graph != null && graph.isTaken(trimmed)) {
            return new Warning(Kind.TAKEN, nextFree(stripSuffix(trimmed), graph));
        }
        return new Warning(Kind.NONE, null);
    }

    public record Warning(Kind kind, String suggestion) {
        public boolean isFine() {
            return kind == Kind.NONE;
        }
    }

    public enum Kind {
        /** All in order. */
        NONE,
        /** Empty — the connector stays unnamed and thus invisible. */
        EMPTY,
        /** Contains characters that cannot be a name. */
        NOT_AN_IDENTIFIER,
        /** Is a keyword — allowed, but needs backticks in the code. */
        KEYWORD,
        /** Already taken — two equal names make both unusable. */
        TAKEN
    }

    /**
     * NFC normal form, as in the compiler.
     *
     * <p>Without this, names would arise here that cannot be found again in
     * the code: the in-game text input delivers {@code ü}, depending on its
     * origin, as one character or as {@code u} with combining dots appended.
     */
    public static String normalize(String name) {
        return name == null ? "" : Normalizer.normalize(name.trim(), Normalizer.Form.NFC);
    }

    /**
     * Is this usable as the name of a device in the network?
     *
     * <p><b>An identifier, or two with a slash between them.</b> The second
     * form builds an assembly: {@code werk_1/eingang} means "the role eingang
     * in the assembly werk_1", and {@code anlagen.md} explicitly names the
     * label gun as the way an assembly comes into being.
     *
     * <p><b>Only that did not work.</b> {@link #isValidIdentifier} stood
     * here, and it knows no slash — screens like the gun rejected
     * {@code werk_1/eingang}. A multiblock could not be built in the game
     * that way at all, although the manual describes exactly this path.
     *
     * <p>There are no two levels: {@code a/b/c} stays out. In the code it is
     * {@code werk_1.schleusen()}, and that needs exactly one name and one
     * role.
     */
    public static boolean isValidDeviceName(String name) {
        int cut = name.indexOf(
                dev.devpanda.factorynetwork.runtime.MultiblockInstances.SEPARATOR);
        if (cut < 0) {
            return isValidIdentifier(name);
        }
        String instance = name.substring(0, cut);
        String role = name.substring(cut + 1);
        return isValidIdentifier(instance) && isValidIdentifier(role);
    }

    /**
     * Is the name usable as an identifier in Manifold?
     *
     * <p>Without a slash: that belongs in the label and never in the code.
     * Whoever checks a device name uses {@link #isValidDeviceName}.
     */
    public static boolean isValidIdentifier(String name) {
        if (name.isEmpty()) {
            return false;
        }
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Removes an appended number so counting can continue. */
    public static String stripSuffix(String name) {
        int underscore = name.lastIndexOf('_');
        if (underscore <= 0 || underscore == name.length() - 1) {
            return name;
        }
        String tail = name.substring(underscore + 1);
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return name;
            }
        }
        return name.substring(0, underscore);
    }

    private ConnectorNaming() {
    }
}
