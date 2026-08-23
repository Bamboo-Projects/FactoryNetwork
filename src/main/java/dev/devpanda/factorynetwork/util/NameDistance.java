package dev.devpanda.factorynetwork.util;

/**
 * Abstand zwischen zwei Namen, für Vorschläge wie „meintest du
 * {@code crusher_1}?".
 *
 * <p>Steht bewusst ohne jeden Minecraft-Bezug hier: So lässt sie sich in
 * gewöhnlichen Tests prüfen, ohne einen Server zu starten. Der erste Fehler
 * darin fiel erst in einem GameTest auf, der eine Minute braucht — dieselbe
 * Prüfung dauert hier Millisekunden.
 */
public final class NameDistance {

    /**
     * Wie viele einzelne Änderungen von {@code a} nach {@code b} führen —
     * einfügen, löschen, ersetzen und <b>vertauschen</b>.
     *
     * <p>Das Vertauschen zweier Nachbarn kostet einen Schritt und nicht zwei.
     * Das ist der Unterschied zwischen Levenshtein und Damerau, und er
     * entscheidet in der Praxis fast alles: Ein Dreher ist der häufigste
     * Vertipper überhaupt. Ohne ihn bekam {@code halel} keinen Vorschlag auf
     * {@code halle}, weil zwei Schritte über der Schwelle lagen — und genau
     * dann hätte man ihn gebraucht.
     */
    public static int between(String a, String b) {
        int width = b.length() + 1;
        int[] twoBack = new int[width];
        int[] previous = new int[width];
        int[] current = new int[width];
        for (int j = 0; j < width; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        // Die Diagonale, nicht die Zelle darüber. Genau hier
                        // steckte der Fehler, der jeden Abstand überschätzte.
                        previous[j - 1] + cost);
                // Zwei Nachbarn vertauscht: ein Schritt, nicht zwei.
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, twoBack[j - 2] + 1);
                }
                current[j] = value;
            }
            int[] scratch = twoBack;
            twoBack = previous;
            previous = current;
            current = scratch;
        }
        return previous[b.length()];
    }

    /**
     * Ab welchem Abstand ein Vorschlag mehr verwirrt als hilft.
     *
     * <p>Ein Drittel der Länge, mindestens aber eins: Bei kurzen Namen soll
     * ein Buchstabe reichen, bei langen darf mehr danebengehen.
     */
    public static boolean isCloseEnough(String wanted, int distance) {
        return distance <= Math.max(1, wanted.length() / 3);
    }

    private NameDistance() {
    }
}
