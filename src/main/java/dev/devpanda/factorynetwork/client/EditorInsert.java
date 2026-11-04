package dev.devpanda.factorynetwork.client;

import java.util.function.Consumer;

/**
 * Where text from outside the editor lands — a recipe from JEI, for one.
 *
 * <p>The editor that is open registers itself; without one, the text goes
 * to the clipboard and the player is told.
 */
public final class EditorInsert {

    private static Consumer<String> target;

    private EditorInsert() {
    }

    public static void setTarget(Consumer<String> editor) {
        target = editor;
    }

    /** Takes the target away, if it is still this one. */
    public static void clearTarget(Consumer<String> editor) {
        if (target == editor) {
            target = null;
        }
    }

    /** Inserts at the cursor, or copies; returns whether an editor took it. */
    public static boolean insert(String text) {
        if (target != null) {
            target.accept(text);
            return true;
        }
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
        return false;
    }
}
