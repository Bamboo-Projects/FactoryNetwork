package dev.devpanda.factorynetwork.web.mcef;

/**
 * Wer Chromiums Nachrichtenschleife dreht — in dieser Fassung: MCEF selbst.
 *
 * <p>MCEF hängt per Mixin in {@code GameRenderer.render} und ruft dort
 * {@code N_DoMessageLoopWork}. Es gibt also nichts zu tun, und genau das
 * steht hier.
 *
 * <p><b>Diese Klasse nennt weder MCEF noch {@code org.cef}</b>, und das ist
 * kein Zufall: Sie wird zu jedem Bild gerufen, also auch in einem Pack ohne
 * MCEF. Stünde hier ein Typ, den es dort nicht gibt, bekäme jeder Spieler
 * ohne MCEF sechzig Mal je Sekunde einen {@code NoClassDefFoundError}.
 */
public final class WebPump {

    /** Einmal je Bild, aus dem Renderthread. */
    public static void frame() {
        // Absichtlich nichts.
    }

    private WebPump() {
    }
}
