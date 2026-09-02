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
 * Der Weg, einen Browser zu öffnen — vorerst über die Konsole.
 *
 * <p><b>Warum das hier steht und nicht im Web-Paket.</b> Die Runtime darf
 * nichts von der Mod wissen; die Mod darf alles von der Runtime wissen. Ein
 * Befehl ist etwas, das die Mod anbietet, und er gehört auf diese Seite der
 * Grenze. Der Prüflauf über die Paketgrenze würde es sonst auch melden.
 *
 * <p>Später öffnet ein Block oder ein Gegenstand denselben Bildschirm. Bis
 * dahin ist ein Befehl das ehrlichste Mittel: Er behauptet nicht, schon ein
 * fertiges Bedienkonzept zu sein.
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
                                            // BMP, wie der Bericht es
                                            // empfiehlt: Die Kodierung kostet
                                            // ein Zweihundertstel von PNG.
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
     * Erst im nächsten Durchlauf ausführen.
     *
     * <p><b>Sonst passiert nichts.</b> Ein Befehl läuft, während die Konsole
     * noch offen ist; Minecraft schließt sie unmittelbar danach und setzt den
     * Bildschirm dabei auf nichts. Ein Bildschirm, der währenddessen gesetzt
     * wird, ist eine Zehntelsekunde später wieder weg.
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
