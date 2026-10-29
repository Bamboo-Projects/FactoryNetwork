package dev.devpanda.factorynetwork.platform.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Which modifier keys are held right now.
 *
 * <p>One of the classes that may differ per Minecraft version: since 26.1 the
 * game asks the client and no longer the screen class. Client only — nothing
 * that runs on a server may touch this class.
 */
public final class Keys {

    private Keys() {
    }

    public static boolean shift() {
        return Minecraft.getInstance().hasShiftDown();
    }

    public static boolean control() {
        return Minecraft.getInstance().hasControlDown();
    }

    public static boolean alt() {
        return Minecraft.getInstance().hasAltDown();
    }

    /**
     * The three as GLFW modifier flags.
     *
     * <p>Since 26.1 a typed character arrives without flags; whoever passes
     * one on with the old shape asks here.
     */
    public static int held() {
        int flags = 0;
        if (shift()) {
            flags |= GLFW.GLFW_MOD_SHIFT;
        }
        if (control()) {
            flags |= GLFW.GLFW_MOD_CONTROL;
        }
        if (alt()) {
            flags |= GLFW.GLFW_MOD_ALT;
        }
        return flags;
    }
}
