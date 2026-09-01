package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class FnClient {

    /**
     * Zählt die Ruhe im Editor und sichert den Entwurf.
     *
     * <p>Am Takt des Clients und nicht am Bildschirm: Der Takt läuft weiter,
     * wenn jemand das Fenster in derselben Sekunde zumacht, in der er das
     * letzte Zeichen getippt hat.
     */
    @SubscribeEvent
    public static void tickDraft(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientProjectState.tick();
        // Der Selbsttest des Bildwegs, einmal je Sitzung. Er braucht einen
        // Zeichenkontext und kann deshalb kein gewöhnlicher Prüflauf sein.
        dev.devpanda.factorynetwork.web.runtime.WebSelfTest.tick();
        // Der Bildnachweis danach — er braucht einen Bildschirm und kann
        // deshalb erst laufen, wenn der Textur-Selbsttest seinen Browser
        // wieder abgeräumt hat.
        WebProofChain.tick();
        // Die Messung zuletzt, und nur auf Ansage: -Dfn.benchmark=true
        dev.devpanda.factorynetwork.web.runtime.WebBenchmark.tick();
        // Und die Flächen in der Welt räumen auf, was niemand mehr ansieht.
        // Hier und nicht im Renderer: Wer nicht gezeichnet wird, meldet sich
        // auch nicht — genau das ist die Information.
        dev.devpanda.factorynetwork.client.panel.WebPanels.tick();
        dev.devpanda.factorynetwork.web.api.Overlays.tick();
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.tick();
        OverlayProof.tick();
        WorldSurfaceDemo.tick();
    }

    /** Overlays über dem Bild — nach allem, was Minecraft dort malt. */
    @SubscribeEvent
    public static void drawOverlays(
            net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        dev.devpanda.factorynetwork.web.api.Overlays.draw(event.getGuiGraphics());
    }

    /**
     * Flächen in der Welt — nach den durchscheinenden Blöcken, damit Wasser
     * und Glas davor bleiben und die Fläche nicht durch sie hindurchleuchtet.
     */
    @SubscribeEvent
    public static void drawWorldSurfaces(
            net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage()
                != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
        if (pose == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 camera = event.getCamera().getPosition();
        var buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.draw(pose, buffers,
                camera.x, camera.y, camera.z);
    }

    /**
     * Der Abstand zweier Bilder — die einzige Zahl, die ein Spieler merkt.
     *
     * <p>Am Bild und nicht am Takt: Der Takt läuft mit festen zwanzig je
     * Sekunde und weiß nichts davon, ob das Zeichnen dazwischen stockt.
     */
    @SubscribeEvent
    public static void measureFrame(
            net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        dev.devpanda.factorynetwork.web.runtime.WebBenchmark.frameRendered();
    }

    /**
     * Chromiums Nachrichtenschleife, einmal je Bild.
     *
     * <p><b>Vor dem Zeichnen und nicht danach.</b> Was Chromium in dieser
     * Runde liefert, soll noch in die Textur, die dieses Bild benutzt — sonst
     * hinkt die Seite um ein Bild hinterher.
     *
     * <p>Gepumpt wird hinter {@code WebPump}. Die Stelle war einmal
     * folgenlos: Solange MCEF den Unterbau stellte, tat es dessen eigener
     * Mixin. Seit die Laufzeitumgebung uns gehört, gibt es keinen Mixin mehr,
     * und der Takt kommt aus genau dieser Zeile.
     */
    @SubscribeEvent
    public static void pumpWebRuntime(
            net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        dev.devpanda.factorynetwork.web.runtime.WebPump.frame();
    }

    /**
     * Beim Verlassen einer Welt bleibt nichts stehen.
     *
     * <p>Sonst fände der nächste Controller den Entwurf des letzten vor —
     * und schriebe ihn beim ersten Anschlag über sein eigenes Programm.
     */
    @SubscribeEvent
    public static void forgetDraft(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientProjectState.clear();
    }

    /**
     * Beim Verlassen einer Welt gehen die Browser zu — Chromium bleibt.
     *
     * <p><b>Hier stand einmal ein vollständiges Herunterfahren, und das war
     * ein Fehler.</b> CEF lässt sich in einem Prozess genau einmal starten;
     * ein zweiter Versuch endet mit „Settings can only be passed to CEF before
     * createClient is called the first time". Wer eine Welt verließ und eine
     * andere betrat, hatte für den Rest der Sitzung keinen Browser mehr —
     * weder eine Fläche in der Welt noch den Editor.
     *
     * <p>Was hierher gehört, sind die Browser: Sie zeigen auf Blöcke einer
     * Welt, die es gleich nicht mehr gibt. Chromium selbst fährt erst beim
     * Beenden des Spiels herunter, in {@link #shutDownWebRuntime}.
     */
    @SubscribeEvent
    public static void closeBrowsersOfThisWorld(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        dev.devpanda.factorynetwork.web.api.Overlays.closeAll();
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.closeAll();
        dev.devpanda.factorynetwork.client.panel.WebPanels.closeAll();
        dev.devpanda.factorynetwork.web.BrowserManager.closeAll();
    }

    /**
     * Beim Beenden des Spiels fährt Chromium herunter.
     *
     * <p>Und nur hier, weil es genau einmal geht. Die Reihenfolge steckt in
     * {@code WebRuntime.shutdown()}: erst alle Browser bitten zuzugehen, dann
     * auf ihre Bestätigung pumpen, dann abräumen. Wer sie umdreht, hinterlässt
     * Hilfsprozesse.
     */
    @SubscribeEvent
    public static void shutDownWebRuntime(
            net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.closeAll();
        dev.devpanda.factorynetwork.client.panel.WebPanels.closeAll();
        dev.devpanda.factorynetwork.web.WebRuntime.shutdown();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FnMenus.TERMINAL.get(), TerminalScreen::new);
        event.register(FnMenus.PRESS.get(),
                dev.devpanda.factorynetwork.client.screen.PressScreen::new);
        event.register(FnMenus.SHELF.get(),
                dev.devpanda.factorynetwork.client.screen.ShelfScreen::new);
        event.register(FnMenus.BURNER.get(),
                dev.devpanda.factorynetwork.client.screen.BurnerScreen::new);
        event.register(FnMenus.ROUTER.get(),
                dev.devpanda.factorynetwork.client.screen.RouterScreen::new);
        event.register(FnMenus.NAME.get(),
                dev.devpanda.factorynetwork.client.screen.NameScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.DISPLAY.get(),
                dev.devpanda.factorynetwork.client.render.DisplayRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.WEB_PANEL.get(),
                dev.devpanda.factorynetwork.client.render.WebPanelRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.ROUTER.get(),
                dev.devpanda.factorynetwork.client.render.RouterRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.DRIVE.get(),
                dev.devpanda.factorynetwork.client.render.DriveRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.RACK.get(),
                dev.devpanda.factorynetwork.client.render.RackRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.CABLE_BUS.get(),
                dev.devpanda.factorynetwork.client.render.CableBusRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.PRESS.get(),
                dev.devpanda.factorynetwork.client.render.PressRenderer::new);
    }

    /**
     * Die Modelle der Anschlüsse gehören zu keinem Blockzustand.
     *
     * <p>Welche Flächen eines Kabels ein Teil tragen, steht in der
     * BlockEntity. Modelle, die niemand über eine Blockstate-Datei anfordert,
     * lädt Minecraft nicht von selbst — sie werden hier angemeldet und vom
     * CableBusRenderer gezeichnet.
     */
    @SubscribeEvent
    public static void registerPartModels(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        dev.devpanda.factorynetwork.client.render.ConnectorPartModels.all(event::register);
        // Der Stempel der Presse gehört ebenfalls keinem Blockzustand: Wo er
        // gerade steht, hängt am Fortschritt in der BlockEntity.
        event.register(dev.devpanda.factorynetwork.client.render.PressRenderer.RAM);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Das Kabel ist schmaler als ein Block und braucht deshalb eine
        // Zeichenart, die durchsichtige Ränder verträgt.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.CABLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.DISPLAY.get(), RenderType.cutout());
        });
    }

    private FnClient() {
    }
}
