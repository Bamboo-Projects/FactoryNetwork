package dev.devpanda.factorynetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Was jeder Spieler für sich einstellt.
 *
 * <p><b>Getrennt von {@link FnConfig}, und das ist keine Ordnungsfrage.</b>
 * Dort stehen Grenzen, die für alle auf einem Server gleich gelten müssen —
 * sonst rechnen zwei Spieler dasselbe Programm verschieden. Hier steht, was
 * nur den eigenen Rechner betrifft: Was der eine an Bildrate übrig hat, hat
 * der andere nicht.
 */
public final class FnClientConfig {

    /**
     * Wie viele Web-Flächen gleichzeitig leben.
     *
     * <p>Gemessen an drei Flächen zu 512×512: sieben Hilfsprozesse, rund 370
     * Megabyte, etwa eine Millisekunde Upload je Bild. Jede weitere kostet
     * einen Renderer und rund achtzig Megabyte — Grafikprozess und
     * Hilfsdienste entstehen nur einmal.
     */
    public static final int DEFAULT_WEB_PANELS = 3;

    private static final ModConfigSpec.IntValue WEB_PANELS;

    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Einstellungen für diesen Rechner.",
                        "Sie wirken nur bei dir und nicht auf dem Server.")
                .push("web");
        WEB_PANELS = builder
                .comment("Wie viele Web-Flächen gleichzeitig laufen dürfen.",
                        "",
                        "Jede Fläche ist eine eigene Seite in Chromium und kostet einen",
                        "Renderer-Prozess und rund achtzig Megabyte. Der Grafikprozess",
                        "und die Hilfsdienste entstehen dagegen nur einmal, egal wie",
                        "viele Flächen hängen.",
                        "",
                        "Gemessen bei 512x512 je Fläche:",
                        "   3 Flächen   7 Prozesse   ~370 MB   ~1 ms Upload je Bild",
                        "   6 Flächen  10 Prozesse   ~650 MB   ~2 ms",
                        "  10 Flächen  14 Prozesse     ~1 GB   ~4 ms",
                        "",
                        "Ab etwa zehn geht ein Viertel des Bildbudgets für Uploads drauf.",
                        "Flächen über der Grenze bleiben dunkel, bis eine andere zumacht;",
                        "was niemand ansieht, macht nach fünf Sekunden von selbst zu.")
                .defineInRange("panels", DEFAULT_WEB_PANELS, 0, 16);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private FnClientConfig() {
    }

    /**
     * Wie viele Flächen gleichzeitig leben dürfen.
     *
     * <p>Der Rückfall auf den Vorgabewert gilt, solange die Datei nicht
     * geladen ist — im Prüfstand etwa, oder ganz früh beim Start.
     */
    public static int webPanels() {
        return CLIENT_SPEC.isLoaded() ? WEB_PANELS.get() : DEFAULT_WEB_PANELS;
    }
}
