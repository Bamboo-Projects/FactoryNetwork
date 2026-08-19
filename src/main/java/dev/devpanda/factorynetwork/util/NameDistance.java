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
     * Levenshtein-Abstand: wie viele einzelne Änderungen — einfügen, löschen,
     * ersetzen — von {@code a} nach {@code b} führen.
     */
    public static int between(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        // Die Diagonale, nicht die Zelle darüber. Genau hier
                        // steckte der Fehler, der jeden Abstand überschätzte.
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
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
