package dev.devpanda.factorynetwork.upgrade;

/**
 * Was in einen Steckplatz passt.
 *
 * <p><b>Zwei Arten, und der Unterschied ist scharf:</b> Ein Modul (siehe
 * {@link Ability}) gibt eine Fähigkeit, die vorher nicht da war — eine
 * Anzeigetafel kann ohne Funk-Modul keinen Funk. Eine {@link Card} hebt einen
 * Wert an einer Fähigkeit, die schon da ist — der Laptop funkt auch ohne
 * Karte, nur nicht weit.
 *
 * <p>Beide belegen denselben Platz. Wer alles will, muss entscheiden, was er
 * weglässt; das ist der Sinn der festen Platzzahl.
 *
 * <p>Ohne Minecraft-Bezug, damit die Rechnung darauf in gewöhnlichen Tests
 * prüfbar bleibt — dasselbe Vorgehen wie im Paket {@code lang}.
 */
public sealed interface Upgrade permits Ability, Card {

    /** Der Name im Registrierungspfad, etwa {@code wireless_module}. */
    String id();
}
