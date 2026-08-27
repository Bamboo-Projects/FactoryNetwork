package dev.devpanda.factorynetwork.upgrade;

/**
 * Die Fähigkeiten, die ein Modul freischalten kann.
 *
 * <p><b>Sie heißt nach der Fähigkeit und nicht nach dem Modul</b>, weil
 * {@code Module} in Java seit Version 9 vergeben ist: {@code java.lang.Module}
 * steht in jeder Datei ohne Import zur Verfügung. Ein eigener Typ desselben
 * Namens funktioniert zwar — der Import gewinnt —, aber er stellt jedem Leser
 * und jedem Werkzeug ein Bein. Der Gegenstand heißt weiter Funk-Modul.
 */
public enum Ability implements Upgrade {

    /** Ohne Kabel am Netz — für die Anzeigetafel. */
    WIRELESS("wireless_module");

    private final String id;

    Ability(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
