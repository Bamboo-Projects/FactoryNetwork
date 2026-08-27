package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;

/**
 * Die beiden Geräte für den Fernzugriff.
 *
 * <p><b>Die Trennung ist der Sinn der Sache:</b> Unterwegs kommt man ans
 * Lager, aber nicht an den Code — dafür braucht man den Laptop. Er kann
 * alles, was das Terminal kann, und kostet mehr.
 *
 * <p>Der zweite Unterschied sind die Steckplätze: vier gegen zwei. Der Laptop
 * reicht damit auch weiter, denn Reichweite kommt aus Karten und Karten
 * brauchen Plätze. Siehe {@code docs/fernzugriff.md} §3.
 *
 * <p><b>Warum das Protokoll auch aus der Ferne geht.</b> Der Entwurf zählte
 * vier Bereiche auf und übersprang es. Die Regel, die ein Spieler sich merken
 * kann, ist aber nicht „vier von sechs", sondern <b>alles außer Code</b> — und
 * das Protokoll ist Diagnose wie die Netzübersicht, die aus der Ferne
 * unstrittig dabei ist.
 */
public enum RemoteDevice {

    /** Der frühe Zugang: alles außer Code. */
    TERMINAL("wireless_terminal", 2, false),

    /** Und das Ziel: alles. */
    LAPTOP("laptop", 4, true);

    private final String id;
    private final int slots;
    private final boolean code;

    RemoteDevice(String id, int slots, boolean code) {
        this.id = id;
        this.slots = slots;
        this.code = code;
    }

    /** Der Name im Register, ohne Namensraum. */
    public String id() {
        return id;
    }

    /** Wie viele Ausbauten hineinpassen. */
    public int slots() {
        return slots;
    }

    /**
     * Darf dieses Gerät diesen Reiter zeigen?
     *
     * <p>Die Frage beantwortet der <b>Server</b>. Der Bildschirm fragt
     * dasselbe, damit er nichts zeichnet, was der Server ablehnen würde — ein
     * Reiter, der sich öffnen lässt und dann nichts tut, ist schlimmer als
     * einer, der gar nicht da ist.
     */
    public boolean allows(TerminalTab tab) {
        return tab != TerminalTab.CODE || code;
    }
}
