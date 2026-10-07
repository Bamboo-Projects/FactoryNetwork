package dev.devpanda.factorynetwork.platform.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * Which modifier keys are held right now.
 *
 * <p>One of the classes that may differ per Minecraft version: later versions
 * no longer offer these questions on the screen class. Client only — nothing
 * that runs on a server may touch this class.
 */
public final class Keys {

    private Keys() {
    }

    public static boolean shift() {
        return Screen.hasShiftDown();
    }

    public static boolean control() {
        return Screen.hasControlDown();
    }

    public static boolean alt() {
        return Screen.hasAltDown();
    }
}
