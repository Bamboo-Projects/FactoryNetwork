package dev.devpanda.factorynetwork.network;

/**
 * Wie es um ein Gerät im Netz steht.
 *
 * <p><b>Vier davon sehen im Spiel gleich aus</b>, und drei davon bedeuten
 * „nicht ansprechbar" aus drei verschiedenen Gründen. Wer den falschen
 * vermutet, sucht an der falschen Stelle: einmal am Namen, einmal an der
 * Kanalgrenze, einmal an der Leitung.
 *
 * <p><b>Das ist ein Schatten des Graphen und keine eigene Wahrheit.</b>
 * Gerechnet wird der Zustand beim Neuaufbau des Netzes; der Anschluss hebt
 * ihn nur auf, damit ihn ein Blick auf den Block beantworten kann, ohne den
 * Controller zu fragen.
 */
public enum DeviceState {

    /** Gar nicht am Netz — oder an keinem, das gerade lädt. */
    OFFLINE,
    /** Benannt und erreichbar. */
    ONLINE,
    /** Am Netz, aber ohne Namen — und damit im Code nicht ansprechbar. */
    UNNAMED,
    /** Der Name ist mehrfach vergeben; alle davon sind unbrauchbar. */
    DUPLICATE,
    /**
     * Am Netz, aber die Leitung dorthin ist ausgelastet.
     *
     * <p><b>Wird seit dem 29.08. nicht mehr vergeben.</b> Damals hieß der
     * Zustand „ohne freien Kanal" und bedeutete: stumm. Kanäle gibt es nicht
     * mehr; ein Gerät an einer vollen Leitung arbeitet langsamer, nicht gar
     * nicht — und das ist kein eigener Zustand, sondern eine Zahl.
     *
     * <p>Der Wert bleibt, weil er in jeder bestehenden Welt gespeichert
     * steht. Ein Gerät, das ihn beim Laden mitbringt, bekommt beim ersten
     * Netzaufbau seinen richtigen.
     */
    STARVED;

    /**
     * Die Nummer, unter der dieser Zustand gespeichert wird.
     *
     * <p>Die Reihenfolge oben ist damit festgelegt: Wer sie ändert, ändert
     * gespeicherte Welten. Neue Zustände gehören ans Ende.
     */
    public byte id() {
        return (byte) ordinal();
    }

    /** Zu einer gespeicherten Nummer der Zustand — Unbekanntes gilt als offline. */
    public static DeviceState byId(int id) {
        DeviceState[] all = values();
        return id >= 0 && id < all.length ? all[id] : OFFLINE;
    }
}
