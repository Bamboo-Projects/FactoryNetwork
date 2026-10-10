package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.platform.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The way to open one of this mod's web surfaces — for now through the console.
 *
 * <p><b>Only what this mod itself offers.</b> Everything here goes through the
 * public API of the web runtime: an overlay, a surface in the world, the
 * editor. What inspects the runtime instead — the render proof, the backdrop
 * measurements, Chromium's tools — has moved to {@code /bamboocef} and lives
 * beside the runtime.
 *
 * <p>Later a block or an item will open the same screen. Until then a command
 * is the most honest means: it does not pretend to be a finished control
 * scheme already.
 */
public final class WebCommands {

    private WebCommands() {
    }

    /** Called through {@link WebBridge}, and only where BambooCEF is installed. */
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fnweb")
                .then(Commands.literal("ide")
                        .executes(context -> {
                            later(() -> {
                                if (!EditorApp.open(Minecraft.getInstance())) {
                                    say("Die Oberfläche ließ sich nicht öffnen — "
                                            + "ist tools/monaco.py gelaufen?");
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("overlay")
                        .executes(context -> {
                            later(() -> {
                                var overlay = OverlayDemo.toggle();
                                say(overlay == null
                                        ? "Overlay geschlossen"
                                        : "Overlay offen: Pfeile, Enter und Escape gehen an die Seite, "
                                                + "F10 gibt sie zurück");
                            });
                            return 1;
                        }))
                .then(Commands.literal("welt")
                        .executes(context -> {
                            later(() -> {
                                var surface = WorldSurfaceDemo.toggle();
                                say(surface == null
                                        ? "Weltfläche geschlossen"
                                        : "Weltfläche steht drei Blöcke vor dir");
                            });
                            return 1;
                        })));
    }

    /**
     * Only run on the next pass.
     *
     * <p><b>Otherwise nothing happens.</b> A command runs while the console is
     * still open; Minecraft closes it immediately afterwards and sets the
     * screen to nothing in doing so. A screen that is set in the meantime is
     * gone again a tenth of a second later.
     */
    private static void later(Runnable what) {
        Minecraft.getInstance().execute(what);
    }

    private static void say(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            Chat.tell(client.player, Component.literal(message), false);
        }
    }
}
