package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.network.packet.RequestEditPacket;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * „Bearbeitung anfragen" — für beide Fenster.
 *
 * <p>Die Sperre hält, und wer davorsteht, konnte bisher nur warten. Ein
 * Klopfen kostet nichts und löst den häufigsten Fall: Der andere hat die
 * Datei offen, tippt aber gar nicht mehr.
 *
 * <p><b>F4 und kein Knopf.</b> Ein Knopf bräuchte Platz in einer Fußzeile,
 * die im Reiter 288 Pixel breit ist — und er stünde dort auch dann, wenn
 * niemand die Datei hält. Die Taste steht in der Griffliste und im Hinweis
 * daneben; gefunden wird sie dort, wo die Frage aufkommt.
 */
public final class RequestEdit {

    /** F4. */
    public static final int KEY = 293;

    private RequestEdit() {
    }

    /**
     * Fragt nach der Datei, wenn sie jemand anders hält.
     *
     * @return ob die Taste verbraucht wurde
     */
    public static boolean ask(String file) {
        if (file == null || ClientProjectState.heldBy(file) == null) {
            // Frei — dann ist nichts anzufragen, und die Taste soll nicht so
            // tun, als hätte sie etwas getan.
            return false;
        }
        PacketDistributor.sendToServer(new RequestEditPacket(file));
        return true;
    }
}
