package dev.devpanda.factorynetwork.client;

/**
 * A search field outside the terminal that the synced search modes follow,
 * such as JEI's.
 *
 * <p>The terminal only knows this slot; whoever provides a search fills it
 * from their own adapter under {@code compat}. Empty, the synced modes behave
 * like the plain ones.
 */
public final class ExternalSearch {

    /** What a search field outside the terminal has to offer. */
    public interface Source {
        String text();

        void setText(String text);
    }

    private static Source source;

    private ExternalSearch() {
    }

    /** Fills the slot, or empties it with {@code null}. */
    public static void install(Source value) {
        source = value;
    }

    public static boolean available() {
        return source != null;
    }

    /** The outside text, or {@code null} while there is none. */
    public static String text() {
        return source == null ? null : source.text();
    }

    public static void setText(String text) {
        if (source != null && !text.equals(source.text())) {
            source.setText(text);
        }
    }
}
