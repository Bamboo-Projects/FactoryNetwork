package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;

/**
 * Selector expressions in their written form.
 *
 * <p>Two questions that are needed in several places: <b>which selector stands
 * at this position in a line</b> — for the editor, which should show what a
 * pattern resolves to — and <b>what does this text mean</b>, when it comes not
 * as program text but as a value.
 *
 * <p>The runtime already had the second one to itself; here it stands once,
 * because the editor needs it too. Two versions drifted apart, and one of them
 * would eventually get an improvement the other lacks.
 */
public final class Selectors {

    private Selectors() {
    }

    /**
     * The selector at this position in the line, or an empty text.
     *
     * <p>A selector is more than a word: {@code item:*_ore} carries a colon, a
     * star, and an underscore, and {@code tag:c/ores} a slash on top. The
     * editor's {@code wordAt} stops at each of those — hence this dedicated
     * version.
     *
     * <p>Without a colon it returns nothing: a name is not a selector, and the
     * two without a sort ({@code power}, {@code all}) resolve to nothing that
     * could be shown.
     */
    public static String at(String line, int column) {
        if (line == null || column < 0 || column > line.length()) {
            return "";
        }
        // No stepping back as with a click: the cursor hovers, and whoever is
        // to the right of a short line means nothing. A tooltip that still
        // shows something there follows the mouse instead of the gaze.
        int cursor = column;
        if (cursor >= line.length() || !isSelectorChar(line.charAt(cursor))) {
            return "";
        }
        int from = cursor;
        while (from > 0 && isSelectorChar(line.charAt(from - 1))) {
            from--;
        }
        int to = cursor;
        while (to + 1 < line.length() && isSelectorChar(line.charAt(to + 1))) {
            to++;
        }
        String found = line.substring(from, to + 1);
        return found.indexOf(':') < 0 ? "" : found;
    }

    private static boolean isSelectorChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/'
                || c == '*' || c == '.' || c == '-';
    }

    /**
     * Rebuilds a selector expression from the written form, or {@code null}.
     *
     * <p><b>Without a message.</b> Whoever needs one formulates it themselves —
     * the runtime does, because a program runs there; the editor does not,
     * because there the cursor only happens to stand over a word by chance.
     */
    public static Expr.Selector parse(String written) {
        if (written == null) {
            return null;
        }
        int colon = written.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String prefix = written.substring(0, colon);
        Expr.Selector.Kind kind = dev.devpanda.factorynetwork.runtime.ResourceKinds
                .kindOf(prefix);
        if (kind == null) {
            return null;
        }
        String rest = written.substring(colon + 1);
        int slash = rest.indexOf('/');
        String namespace = null;
        String path = rest;
        // For a tag the first slash always splits off the namespace; for an
        // item only when no pattern stands before it — "item:*_ore" searches
        // across all namespaces.
        if (slash >= 0 && (kind == Expr.Selector.Kind.TAG
                || kind == Expr.Selector.Kind.FLUIDTAG || !rest.startsWith("*"))) {
            namespace = rest.substring(0, slash);
            path = rest.substring(slash + 1);
        }
        return new Expr.Selector(kind, prefix, namespace, path, new Span(0, 0, 1, 1));
    }
}
