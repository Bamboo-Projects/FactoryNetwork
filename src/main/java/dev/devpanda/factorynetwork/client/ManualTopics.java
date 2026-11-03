package dev.devpanda.factorynetwork.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Which page of the manual explains a word of the language.
 *
 * <p>Ctrl+click on a name in the editor jumps to its definition; on a word of
 * the language itself there is no definition to jump to — so it jumps to the
 * page that explains it, with its 3D scenes. The table is small on purpose:
 * one page per word, the one a beginner should read first.
 *
 * <p>No GuideME here: the manual is an optional mod, wired in by the entry
 * point through {@link #setOpener}. Without it, no page opens.
 */
public final class ManualTopics {

    private static final Map<String, String> PAGES = new HashMap<>();

    static {
        page("programmieren.md", "worker", "from", "to", "rate", "maintain", "when", "priority",
                "overflow", "let", "if", "else", "while", "return", "fn", "per", "move");
        page("auswahlen.md", "filter", "except", "item", "tag", "all", "selection");
        page("fluessigkeiten.md", "fluid", "fluidtag", "chemical");
        page("werte-und-gruppen.md", "global", "const", "group", "members", "strategy",
                "round_robin", "first_available", "least_filled", "random");
        page("ereignisse.md", "on", "emit", "device_changed", "device_output", "device_online",
                "device_offline", "redstone_changed", "storage_changed", "crafting_finished");
        page("ablaeufe.md", "sleep", "await");
        page("listen.md", "for", "in", "where", "sort", "plus", "without", "rest", "first", "sum");
        page("anlagen.md", "multiblock", "devices");
        page("anzeigen.md", "display", "title", "row", "text", "progress", "indicator", "list",
                "items", "button", "scale");
        page("fertigung.md", "craft", "recipe", "at");
        page("speicher.md", "storage", "count", "insert");
        page("kanaele-und-strom.md", "power", "network", "energy");
        page("protokoll.md", "log", "info", "warn", "error", "debug");
    }

    private ManualTopics() {
    }

    /** Opens a page by its file name; set by the entry point when GuideME is there. */
    private static java.util.function.Consumer<String> opener;

    public static void setOpener(java.util.function.Consumer<String> manual) {
        opener = manual;
    }

    private static void page(String page, String... words) {
        for (String word : words) {
            PAGES.putIfAbsent(word, page);
        }
    }

    /** The page for this word, or {@code null} if the manual has none for it. */
    public static String pageFor(String word) {
        return word == null ? null : PAGES.get(word);
    }

    /**
     * Opens the manual at the page for this word.
     *
     * @return whether a page was opened — {@code false} for a word without a
     *         page, or when GuideME is not installed
     */
    public static boolean open(String word) {
        String page = pageFor(word);
        if (page == null || opener == null) {
            return false;
        }
        opener.accept(page);
        return true;
    }
}
