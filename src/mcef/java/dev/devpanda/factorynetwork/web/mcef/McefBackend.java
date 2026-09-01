package dev.devpanda.factorynetwork.web.mcef;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFPlatform;
import dev.devpanda.factorynetwork.web.WebBackend;
import dev.devpanda.factorynetwork.web.WebRuntimeState;
import dev.devpanda.factorynetwork.web.WebRuntimeUnavailable;

/**
 * Der Unterbau auf MCEF.
 *
 * <p><b>Diese Klasse ist die einzige, die MCEF anfasst</b>, und sie wird erst
 * geladen, wenn jemand sie wirklich aufruft. Das ist der ganze Trick, mit dem
 * eine freiwillige Abhängigkeit freiwillig bleibt: Läge ein MCEF-Typ im Feld
 * einer Klasse, die beim Start geladen wird, bekäme jeder Spieler ohne MCEF
 * einen {@code NoClassDefFoundError} — und die Mod startete nicht mehr.
 *
 * <p><b>MCEF fährt sich selbst hoch.</b> Es hängt per Mixin in Minecrafts
 * Renderer und ruft dort Chromiums Nachrichtenschleife; die Initialisierung
 * läuft nebenher. Deshalb wird hier nichts gestartet, sondern gefragt — und
 * gefragt wird erst, wenn zum ersten Mal jemand einen Browser will. Zu diesem
 * Zeitpunkt ist MCEF entweder fertig oder gescheitert, und beides ist eine
 * Antwort.
 */
public final class McefBackend implements WebBackend {

    /** So heißt MCEF in der Modliste. */
    public static final String MOD_ID = "mcef";

    private McefBackend() {
    }

    /**
     * Fragt MCEF, ob es so weit ist.
     *
     * @throws WebRuntimeUnavailable mit dem Grund, wenn nicht
     */
    public static WebBackend create() {
        if (!modPresent()) {
            throw new WebRuntimeUnavailable(WebRuntimeState.MOD_MISSING,
                    "MCEF liegt nicht in diesem Pack");
        }
        platformOrFail();
        if (!MCEF.isInitialized()) {
            // <b>Hier endet, was wir sicher wissen.</b> MCEF meldet mit einem
            // einzigen boolean, ob es hochkam, und nennt keinen Grund. Der
            // häufigste ist ein Download, der nicht durchging — Chromium wird
            // beim ersten Start von einem eigenen Spiegel geholt. Es könnte
            // aber ebenso eine fehlende Systembibliothek sein.
            //
            // Diesen Zustand als NOT_DOWNLOADED auszugeben wäre geraten. Er
            // heißt deshalb FAILED und trägt den Verdacht im Text; wer es
            // genauer wissen will, liest MCEFs eigenes Protokoll.
            throw new WebRuntimeUnavailable(WebRuntimeState.FAILED,
                    "MCEF ist da, aber nicht hochgekommen — meist ein "
                            + "unvollständiger Download von Chromium");
        }
        return new McefBackend();
    }

    private static boolean modPresent() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable outsideTheGame) {
            // Kein FML — dann läuft das hier in einem Prüflauf und nicht im
            // Spiel, und ohne Spiel gibt es auch keinen Browser.
            return false;
        }
    }

    private static void platformOrFail() {
        try {
            MCEFPlatform.getPlatform();
        } catch (Throwable unknown) {
            // Für diese Plattform gibt es keine Binärdateien. Kein
            // Nachinstallieren hilft, und das muss anders klingen als ein
            // fehlgeschlagener Download.
            throw new WebRuntimeUnavailable(WebRuntimeState.UNSUPPORTED,
                    "Für diese Plattform gibt es kein Chromium von MCEF");
        }
    }

    @Override
    public String name() {
        return "MCEF auf " + MCEFPlatform.getPlatform().getNormalizedName();
    }

    @Override
    public void close() {
        // <b>Absichtlich nichts.</b> MCEF gehört uns nicht: Es hängt seinen
        // eigenen Abschalthaken ein und wird von anderen Mods im selben Pack
        // mitbenutzt. Wer hier MCEF.shutdown() ruft, nimmt ihnen den Browser
        // weg. Was uns gehört — Browser, Texturen —, schließt der Manager.
    }
}
