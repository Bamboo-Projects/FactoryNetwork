package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.network.DeviceState;

/**
 * Welche Farbe ein Netzzustand trägt.
 *
 * <p><b>An einer Stelle</b>, weil sie an dreien gebraucht wird: am Lämpchen
 * des Anschlusses am Kabel, am Lämpchen des Connectorblocks und am Namenszug
 * über beiden. Drei Paletten wären drei Gelegenheiten, dass Rot an einer
 * Stelle Orange heißt.
 *
 * <p>Die Werte multiplizieren eine weiße Textur — es sind keine Farben,
 * sondern Faktoren. Deshalb ist Grün hier hell und nicht sattgrün: Ein
 * Lämpchen soll leuchten und nicht wie ein Aufkleber wirken.
 */
public final class DeviceStateColours {

    /** Benannt und erreichbar. */
    private static final int ONLINE = 0xA3D9A5;
    /** Am Netz, aber ohne Namen. */
    private static final int UNNAMED = 0x8A939C;
    /** Doppelt vergeben — beide unbrauchbar. */
    private static final int DUPLICATE = 0xE8C888;
    /** Am Netz, aber ohne freien Kanal. */
    private static final int STARVED = 0xE88388;
    /** Gar nicht am Netz. */
    private static final int OFFLINE = 0x4A4F55;

    private DeviceStateColours() {
    }

    /** Der Farbwert als 0xRRGGBB. */
    public static int of(DeviceState state) {
        return switch (state) {
            case ONLINE -> ONLINE;
            case UNNAMED -> UNNAMED;
            case DUPLICATE -> DUPLICATE;
            case STARVED -> STARVED;
            case OFFLINE -> OFFLINE;
        };
    }

    /** Derselbe Wert als undurchsichtiges 0xAARRGGBB, für Schrift. */
    public static int opaque(DeviceState state) {
        return 0xFF000000 | of(state);
    }

    /** Der Rotanteil als Faktor, für {@code renderModel}. */
    public static float red(DeviceState state) {
        return ((of(state) >> 16) & 0xFF) / 255.0F;
    }

    public static float green(DeviceState state) {
        return ((of(state) >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(DeviceState state) {
        return (of(state) & 0xFF) / 255.0F;
    }
}
