package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.network.DeviceState;

/**
 * Which colour a network state carries.
 *
 * <p><b>In one place</b>, because it is needed in three: at the indicator
 * light of the connector on the cable, at the indicator light of the
 * connector block, and at the name label above both. Three palettes would be
 * three chances for red to be called orange in one place.
 *
 * <p>The values multiply a white texture — they are not colours but factors.
 * That is why green here is light and not deep green: an indicator light
 * should glow and not look like a sticker.
 */
public final class DeviceStateColours {

    /** Named and reachable. */
    private static final int ONLINE = 0xA3D9A5;
    /** On the network, but without a name. */
    private static final int UNNAMED = 0x8A939C;
    /** Assigned twice — both unusable. */
    private static final int DUPLICATE = 0xE8C888;
    /** On the network, but without a free channel. */
    private static final int STARVED = 0xE88388;
    /** Not on the network at all. */
    private static final int OFFLINE = 0x4A4F55;

    private DeviceStateColours() {
    }

    /** The colour value as 0xRRGGBB. */
    public static int of(DeviceState state) {
        return switch (state) {
            case ONLINE -> ONLINE;
            case UNNAMED -> UNNAMED;
            case DUPLICATE -> DUPLICATE;
            case STARVED -> STARVED;
            case OFFLINE -> OFFLINE;
        };
    }

    /** The same value as opaque 0xAARRGGBB, for text. */
    public static int opaque(DeviceState state) {
        return 0xFF000000 | of(state);
    }

    /** The red component as a factor, for {@code renderModel}. */
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
