package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;

/**
 * Auswahlausdrücke in ihrer geschriebenen Form.
 *
 * <p>Zwei Fragen, die an mehreren Stellen gebraucht werden: <b>Welcher
 * Selektor steht an dieser Stelle einer Zeile</b> — für den Editor, der zeigen
 * soll, worauf sich ein Muster auflöst — und <b>was bedeutet dieser Text</b>,
 * wenn er nicht mehr als Programmtext, sondern als Wert daherkommt.
 *
 * <p>Die zweite hatte die Laufzeit schon für sich; hier steht sie einmal, weil
 * der Editor sie ebenfalls braucht. Zwei Fassungen liefen auseinander, und die
 * eine bekäme irgendwann eine Verbesserung, die der anderen fehlt.
 */
public final class Selectors {

    private Selectors() {
    }

    /**
     * Der Selektor an dieser Stelle der Zeile, oder ein leerer Text.
     *
     * <p>Ein Selektor ist mehr als ein Wort: {@code item:*_ore} trägt einen
     * Doppelpunkt, einen Stern und einen Unterstrich, und {@code tag:c/ores}
     * dazu einen Schrägstrich. {@code wordAt} im Editor hört an jedem davon
     * auf — deshalb diese eigene Fassung.
     *
     * <p>Ohne Doppelpunkt gibt es nichts zurück: Ein Name ist kein Selektor,
     * und die beiden ohne Sorte ({@code power}, {@code all}) lösen sich auf
     * nichts auf, was man zeigen könnte.
     */
    public static String at(String line, int column) {
        if (line == null || column < 0 || column > line.length()) {
            return "";
        }
        // Kein Zurückgehen wie bei einem Klick: Der Zeiger schwebt, und wer
        // rechts neben einer kurzen Zeile steht, meint nichts. Ein Tooltip,
        // der dort noch etwas zeigt, folgt der Maus statt dem Blick.
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
     * Baut aus der geschriebenen Form wieder einen Auswahlausdruck, oder
     * {@code null}.
     *
     * <p><b>Ohne Meldung.</b> Wer eine braucht, formuliert sie selbst — die
     * Laufzeit tut es, weil dort ein Programm läuft; der Editor nicht, weil
     * dort der Zeiger nur zufällig über einem Wort steht.
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
        // Bei einem Tag trennt der erste Schrägstrich immer den Namensraum ab;
        // bei einem Gegenstand nur dann, wenn davor kein Muster steht —
        // „item:*_ore" sucht über alle Namensräume.
        if (slash >= 0 && (kind == Expr.Selector.Kind.TAG
                || kind == Expr.Selector.Kind.FLUIDTAG || !rest.startsWith("*"))) {
            namespace = rest.substring(0, slash);
            path = rest.substring(slash + 1);
        }
        return new Expr.Selector(kind, prefix, namespace, path, new Span(0, 0, 1, 1));
    }
}
