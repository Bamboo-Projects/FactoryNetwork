package dev.devpanda.factorynetwork.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.web.WebRuntimeStatus;
import dev.devpanda.factorynetwork.web.screen.BrowserScreen;
import dev.devpanda.factorynetwork.web.screen.ProbePage;
import dev.devpanda.factorynetwork.web.screen.RenderProof;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The way to open a browser — for now through the console.
 *
 * <p><b>Why this stands here and not in the web package.</b> The runtime must
 * know nothing of the mod; the mod may know everything of the runtime. A
 * command is something the mod offers, and it belongs on this side of the
 * boundary. The check across the package boundary would otherwise report it
 * too.
 *
 * <p>Later a block or an item will open the same screen. Until then a command
 * is the most honest means: it does not pretend to be a finished control
 * scheme already.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class WebCommands {

    private WebCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fnweb")
                .then(Commands.literal("probe")
                        .executes(context -> {
                            openProbe();
                            return 1;
                        }))
                .then(Commands.literal("nachweis")
                        .executes(context -> {
                            later(() -> RenderProof.openIfPossible(Minecraft.getInstance()));
                            return 1;
                        }))
                .then(Commands.literal("seite")
                        .then(Commands.argument("adresse", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String url = StringArgumentType.getString(context, "adresse");
                                    later(() -> open(url, false));
                                    return 1;
                                })))
                .then(Commands.literal("hintergrund")
                        .executes(context -> {
                            later(() -> dev.devpanda.factorynetwork.web.screen
                                    .BackdropProof.openIfPossible(Minecraft.getInstance()));
                            return 1;
                        }))
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
                .then(Commands.literal("glas")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    dev.devpanda.factorynetwork.web.screen.BackdropScreen.open(
                                            Minecraft.getInstance(),
                                            dev.devpanda.factorynetwork.web.screen
                                                    .BackdropScreen.Mode.LOW_5,
                                            0.5,
                                            // BMP, as the report
                                            // recommends: the encoding costs
                                            // a two-hundredth of PNG.
                                            dev.devpanda.factorynetwork.web.capture
                                                    .WorldCapture.Format.BMP);
                                } catch (Exception broken) {
                                    say("Hintergrund ließ sich nicht öffnen: " + broken.getMessage());
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("glasmessung")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    dev.devpanda.factorynetwork.web.screen
                                            .BackdropBenchmark.open(Minecraft.getInstance());
                                } catch (Exception broken) {
                                    say("Messung ließ sich nicht öffnen: " + broken.getMessage());
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("messung")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    dev.devpanda.factorynetwork.web.screen
                                            .InteractionBenchmark.open(Minecraft.getInstance());
                                } catch (Exception broken) {
                                    say("Messung ließ sich nicht öffnen: " + broken.getMessage());
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
                        }))
                .then(Commands.literal("devtools")
                        .executes(context -> {
                            WebDevTools.show();
                            return 1;
                        }))
                .then(Commands.literal("zustand")
                        .executes(context -> {
                            WebRuntimeStatus status = BrowserScreen.availability();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Web-Runtime: " + status), false);
                            return 1;
                        })));
    }

    private static void openProbe() {
        later(() -> {
            try {
                open(ProbePage.url(), false);
            } catch (Exception broken) {
                say("Die Prüfseite ließ sich nicht ablegen: " + broken.getMessage());
            }
        });
    }

    private static void open(String url, boolean transparent) {
        WebRuntimeStatus status = BrowserScreen.availability();
        if (!status.usable()) {
            say("Kein Browser zu haben: " + status);
            return;
        }
        Minecraft.getInstance().setScreen(
                new BrowserScreen(Component.literal("Web"), url, transparent));
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
            client.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
