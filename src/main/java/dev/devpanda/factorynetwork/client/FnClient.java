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
     * Counts the quiet in the editor and saves the draft.
     *
     * <p>On the client's tick and not on the screen: the tick keeps running
     * when someone closes the window in the same second in which they typed
     * the last character.
     */
    @SubscribeEvent
    public static void tickDraft(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientProjectState.tick();
        // The self-test of the image path, once per session. It needs a
        // drawing context and can therefore not be an ordinary check run.
        dev.devpanda.factorynetwork.web.runtime.WebSelfTest.tick();
        // The render proof after that — it needs a screen and can therefore
        // only run once the texture self-test has cleared away its browser
        // again.
        WebProofChain.tick();
        // The benchmark last, and only on request: -Dfn.benchmark=true
        dev.devpanda.factorynetwork.web.runtime.WebBenchmark.tick();
        dev.devpanda.factorynetwork.web.api.Overlays.tick();
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.tick();
        WorldPointer.tick();
        OverlayProof.tick();
        WorldSurfaceDemo.tick();
    }

    /**
     * Right-click on a display in the world: click where the crosshair points.
     *
     * <p>If the gaze hits a face, it gets the click, and the player's swing is
     * suppressed — otherwise it would swing at the air next to the display. If
     * it hits none, everything runs as usual.
     */
    @SubscribeEvent
    public static void clickWorldSurface(
            net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && WorldPointer.click()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Overlays on top of the picture — after everything Minecraft paints there. */
    @SubscribeEvent
    public static void drawOverlays(
            net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        dev.devpanda.factorynetwork.web.api.Overlays.draw(event.getGuiGraphics());
    }

    /**
     * Surfaces in the world — after the translucent blocks, so that water and
     * glass stay in front and the surface does not shine through them.
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
     * The gap between two frames — the only number a player notices.
     *
     * <p>On the frame and not on the tick: the tick runs at a fixed twenty per
     * second and knows nothing of whether the drawing stutters in between.
     */
    @SubscribeEvent
    public static void measureFrame(
            net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        dev.devpanda.factorynetwork.web.runtime.WebBenchmark.frameRendered();
    }

    /**
     * Chromium's message loop, once per frame.
     *
     * <p><b>Before the drawing and not after.</b> What Chromium delivers in
     * this round should still make it into the texture this frame uses —
     * otherwise the page lags one frame behind.
     *
     * <p>Pumping happens behind {@code WebPump}. The spot was once without
     * consequence: as long as MCEF provided the underpinnings, its own mixin
     * did it. Since the runtime belongs to us, there is no mixin any more, and
     * the beat comes from exactly this line.
     */
    @SubscribeEvent
    public static void pumpWebRuntime(
            net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        dev.devpanda.factorynetwork.web.runtime.WebPump.frame();
    }

    /**
     * When leaving a world nothing is left standing.
     *
     * <p>Otherwise the next controller would find the last one's draft — and
     * write it over its own program at the first keystroke.
     */
    @SubscribeEvent
    public static void forgetDraft(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientProjectState.clear();
    }

    /**
     * When leaving a world the browsers close — Chromium stays.
     *
     * <p><b>A full shutdown once stood here, and that was a mistake.</b> CEF
     * can be started exactly once in a process; a second attempt ends with
     * "Settings can only be passed to CEF before createClient is called the
     * first time". Whoever left one world and entered another had no browser
     * for the rest of the session — neither a surface in the world nor the
     * editor.
     *
     * <p>What belongs here are the browsers: they point at blocks of a world
     * that will be gone in a moment. Chromium itself only shuts down when the
     * game is quit, in {@link #shutDownWebRuntime}.
     */
    @SubscribeEvent
    public static void closeBrowsersOfThisWorld(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        dev.devpanda.factorynetwork.web.api.Overlays.closeAll();
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.closeAll();
        dev.devpanda.factorynetwork.web.BrowserManager.closeAll();
    }

    /**
     * When the game is quit, Chromium shuts down.
     *
     * <p>And only here, because it works exactly once. The order sits in
     * {@code WebRuntime.shutdown()}: first ask all browsers to close, then pump
     * for their confirmation, then clean up. Whoever reverses it leaves helper
     * processes behind.
     */
    @SubscribeEvent
    public static void shutDownWebRuntime(
            net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        dev.devpanda.factorynetwork.web.api.WorldSurfaces.closeAll();
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
     * The models of the connectors belong to no block state.
     *
     * <p>Which faces of a cable a part occupies stands in the BlockEntity.
     * Models that no one requests through a blockstate file Minecraft does not
     * load by itself — they are registered here and drawn by the
     * CableBusRenderer.
     */
    @SubscribeEvent
    public static void registerPartModels(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        dev.devpanda.factorynetwork.client.render.ConnectorPartModels.all(event::register);
        // The press's ram also belongs to no block state: where it currently
        // stands hangs on the progress in the BlockEntity.
        event.register(dev.devpanda.factorynetwork.client.render.PressRenderer.RAM);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // The cable is narrower than a block and therefore needs a render type
        // that tolerates transparent edges.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.CABLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.DISPLAY.get(), RenderType.cutout());
        });
    }

    private FnClient() {
    }
}
