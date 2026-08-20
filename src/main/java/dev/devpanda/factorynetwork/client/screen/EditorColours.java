package dev.devpanda.factorynetwork.client.screen;

/**
 * Die Farben des Code-Bildschirms.
 *
 * <p>Dieselben Werte, mit denen die Blocktexturen arbeiten: Blau für
 * Schlüsselwörter, Grün für Auswahlausdrücke. Wer den Controller ansieht und
 * dann den Code, soll denselben Farbklang wiedererkennen.
 */
public final class EditorColours {

    public static final int TEXT = 0xD8DEE4;
    public static final int MUTED = 0x8A939C;
    public static final int KEYWORD = 0x8AB4F8;
    public static final int SELECTOR = 0xA3D9A5;
    public static final int COMMENT = 0x6B7480;
    public static final int ERROR = 0xE88388;
    public static final int CURSOR = 0xD8DEE4;
    /** Hinterlegung einer Zeile mit Fehler — bei zehn Pixeln Höhe trägt keine Welle. */
    public static final int ERROR_LINE = 0x30E88388;

    private EditorColours() {
    }
}
