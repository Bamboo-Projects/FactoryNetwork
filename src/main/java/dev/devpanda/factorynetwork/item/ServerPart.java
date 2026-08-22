package dev.devpanda.factorynetwork.item;

/**
 * Die drei Bauteile eines Servers.
 *
 * <p>Ein Einschub im Schrank nimmt genau eines von jeder Art. <b>Erst wenn
 * alle drei stecken, ist es ein Server</b> — ein Rechner ohne Speicher ist
 * keiner, und ein halb bestückter Einschub soll nichts beitragen. Das ist
 * die Entscheidung, um die es bei diesem Block überhaupt geht: nicht „wie
 * viele Bauteile", sondern „wie viele vollständige Server".
 */
public enum ServerPart {

    /** Rechenwerk: wie viele Abläufe gleichzeitig laufen. */
    CPU("cpu"),

    /** Arbeitsspeicher: wie viele Abläufe überhaupt bestehen dürfen. */
    RAM("ram"),

    /** Datenträger: wie groß das Programm sein darf. */
    DISK("disk");

    private final String prefix;

    ServerPart(String prefix) {
        this.prefix = prefix;
    }

    /** Der Anfang des Registrierungsnamens, etwa {@code cpu_32}. */
    public String prefix() {
        return prefix;
    }

    /**
     * Die Stufen dieser Art, von klein nach groß.
     *
     * <p>Viermal so viel je Stufe, und die Zahl im Namen ist der Wert — wie
     * bei den Speicherzellen. Wer eine große Stufe baut, tauscht nicht nur
     * Leistung ein, sondern vor allem Platz: Ein Schrank hat zwölf
     * Einschübe, und die sind das Knappe.
     */
    public int[] tiers() {
        return switch (this) {
            case CPU -> new int[] {2, 8, 32, 128};
            case RAM -> new int[] {8, 32, 128, 512};
            case DISK -> new int[] {64, 256, 1024, 4096};
        };
    }
}
