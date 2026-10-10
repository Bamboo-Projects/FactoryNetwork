package dev.devpanda.factorynetwork.client;

import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The one door to everything that needs the web runtime.
 *
 * <p><b>Only to be touched after {@link WebSupport#installed()} said yes.</b>
 * This class and the ones it calls name BambooCEF's classes; loading any of
 * them in a game without BambooCEF fails. Keeping every such call here makes
 * that rule checkable: outside the web classes themselves, nothing else may
 * mention them, and {@code ModBoundaryTest} holds the list.
 */
public final class WebBridge {

    /** What became of the attempt to open the web editor. */
    public enum Opening {
        /** It is open. */
        OPENED,
        /** The runtime is still downloading or starting; trying again soon is worth it. */
        PREPARING,
        /** It will not open here: unsupported system, or the runtime failed. */
        UNAVAILABLE
    }

    private WebBridge() {
    }

    /** Lets the runtime get ready in the background, once at client setup. */
    public static void prepare() {
        FnWeb.prepare();
    }

    /** Everything on the web side that wants a client tick. */
    public static void tick() {
        EditorApp.tick();
        WorldPointer.tick();
        OverlayProof.tick();
        WorldSurfaceDemo.tick();
        IdeLauncher.tick();
    }

    /** Whether a use-item click was meant for a web surface in the world. */
    public static boolean click() {
        return WorldPointer.click();
    }

    /** Forgets the open editor session when the player leaves the world. */
    public static void clear() {
        EditorApp.clear();
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        WebCommands.register(event);
    }

    /**
     * The screen that waits for the runtime and opens the editor once it is up.
     *
     * <p>Built here so that the terminal does not have to name it: the
     * waiting screen asks the runtime for its state, and with that it belongs
     * to the classes a game without BambooCEF must not load.
     */
    public static Screen waitingScreen(Screen parent, TerminalMenu menu) {
        return new dev.devpanda.factorynetwork.client.screen.WebEditorLoadingScreen(parent, menu);
    }

    /** Tries to open the web editor on the controller's project. */
    public static Opening openEditor(Minecraft client, Screen parent, TerminalMenu menu) {
        if (EditorApp.open(client, parent, menu)) {
            return Opening.OPENED;
        }
        return switch (FnWeb.status().state()) {
            case NOT_STARTED, NOT_DOWNLOADED, READY -> Opening.PREPARING;
            default -> Opening.UNAVAILABLE;
        };
    }
}
