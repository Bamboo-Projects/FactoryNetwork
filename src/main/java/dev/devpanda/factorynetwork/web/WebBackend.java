package dev.devpanda.factorynetwork.web;

/**
 * Was die Runtime von ihrem Unterbau braucht.
 *
 * <p><b>Eine Schnittstelle für einen Unterbau, den es nur einmal gibt</b> —
 * das sieht nach Vorrat aus und ist keiner. Sie ist die Stelle, an der die
 * Runtime <i>ohne</i> MCEF geladen werden kann: Stünde hier ein
 * MCEF-Typ, brächte schon das Laden von {@link WebRuntime} einen
 * {@code NoClassDefFoundError}, und die Mod startete nicht mehr, bloß weil
 * eine freiwillige Abhängigkeit fehlt.
 *
 * <p>Dass sie später einen zweiten Unterbau tragen kann — einen eigenen
 * JCEF-Aufsatz, einen GPU-Pfad —, ist ein Nebeneffekt und nicht der Zweck.
 */
public interface WebBackend extends AutoCloseable {

    /** Der Name für Protokollzeilen. */
    String name();

    /** Fährt herunter und gibt alles frei. Darf mehrfach gerufen werden. */
    @Override
    void close();
}
