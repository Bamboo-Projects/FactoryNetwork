package dev.devpanda.factorynetwork.util;

/**
 * Distance between two names, for suggestions like "did you mean
 * {@code crusher_1}?".
 *
 * <p>Deliberately sits here without any tie to Minecraft: this way it can be
 * tested in ordinary tests without starting a server. The first bug in it only
 * showed up in a GameTest that takes a minute — the same check takes
 * milliseconds here.
 */
public final class NameDistance {

    /**
     * How many single edits lead from {@code a} to {@code b} —
     * insert, delete, replace and <b>transpose</b>.
     *
     * <p>Transposing two neighbours costs one step and not two. That is the
     * difference between Levenshtein and Damerau, and in practice it decides
     * almost everything: a transposition is the most common typo there is.
     * Without it {@code halel} got no suggestion of {@code halle}, because two
     * steps lay above the threshold — and that is exactly when it would have
     * been needed.
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
                        // The diagonal, not the cell above. Right here was the
                        // bug that overestimated every distance.
                        previous[j - 1] + cost);
                // Two neighbours transposed: one step, not two.
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
     * At what distance a suggestion confuses more than it helps.
     *
     * <p>A third of the length, but at least one: for short names a single
     * letter should be enough, for long ones more may be off.
     */
    public static boolean isCloseEnough(String wanted, int distance) {
        return distance <= Math.max(1, wanted.length() / 3);
    }

    private NameDistance() {
    }
}
