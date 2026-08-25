package dev.devpanda.factorynetwork.lang;

import java.util.Map;

/**
 * Die Ereignisse, die das Netz von sich aus auslöst.
 *
 * <p><b>Hier und sonst nirgends.</b> Die Namen standen als Zeichenketten an
 * der Stelle, die sie auslöst, und als zweite Liste im Editor — beide Listen
 * liefen auseinander, sobald ein Ereignis dazukam. Der Editor bot ein
 * {@code inventory_changed} an, das nie jemand ausgelöst hat, und
 * verschwieg {@code device_changed}.
 *
 * <p>Das ist die teuerste Art von Fehler, die diese Sprache kennt: Ein
 * {@code on}-Block braucht keine Deklaration, also nimmt der Übersetzer
 * jeden Namen an. Wer sich vertippt, bekommt keine Meldung — sein Block
 * hängt im Programm und läuft nie. Deshalb prüft {@link EventCheck} gegen
 * diese Karte, und wer ein Ereignis auslöst, nimmt die Konstante von hier.
 */
public final class BuiltinEvents {

    /** Ein Gerät ist im Netz aufgetaucht. */
    public static final String DEVICE_ONLINE = "device_online";

    /** Ein Gerät ist verschwunden — übergeben wird sein Name, kein Gerät. */
    public static final String DEVICE_OFFLINE = "device_offline";

    /** An einem Gerät hat sich der Inhalt geändert. */
    public static final String DEVICE_CHANGED = "device_changed";

    /**
     * In einem Gerät ist etwas dazugekommen.
     *
     * <p><b>Nicht „fertig".</b> Ob eine Maschine ihre Arbeit beendet hat,
     * weiß von außen niemand; gemessen wird der Unterschied zum letzten
     * Blick. Was das Netz selbst einlegt, zählt nie mit.
     */
    public static final String DEVICE_OUTPUT = "device_output";

    /** Das Redstonesignal an einem Connector ist ein anderes geworden. */
    public static final String REDSTONE_CHANGED = "redstone_changed";

    /**
     * Wie viele Werte ein Block bekommt.
     *
     * <p>Nur {@code redstone_changed} übergibt zwei — das Gerät und die
     * Stärke. Wer die drei anderen mit zwei Namen schreibt, bekommt einen
     * zweiten Namen, der für immer leer bleibt.
     */
    public static final Map<String, Integer> ARITY = Map.of(
            DEVICE_ONLINE, 1,
            DEVICE_OFFLINE, 1,
            DEVICE_CHANGED, 1,
            DEVICE_OUTPUT, 1,
            REDSTONE_CHANGED, 2);

    private BuiltinEvents() {
    }
}
