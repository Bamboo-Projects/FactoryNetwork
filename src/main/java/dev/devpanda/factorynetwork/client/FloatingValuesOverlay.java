package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.analyser.AnalyserData;
import dev.devpanda.factorynetwork.item.NetworkAnalyserItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The values above the machines: a {@code display} named like a device
 * floats above its machine while the analyser is in hand.
 *
 * <p>Drawn like the connector names, facing the camera and through walls —
 * whoever holds the analyser is looking for something, and a value behind a
 * wall is still the value.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class FloatingValuesOverlay {

    /** Further away than this, nothing floats: the text would be unreadable. */
    private static final double RANGE = 24;

    private static final float LINE = 0.25F;
    private static final int BACKGROUND = 0x88000000;

    private FloatingValuesOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null
                || !(client.player.getMainHandItem().getItem() instanceof NetworkAnalyserItem)
                || !ClientAnalyserState.isFresh()) {
            return;
        }
        AnalyserData data = ClientAnalyserState.data();
        if (data.floating().isEmpty()) {
            return;
        }
        var camera = event.getLevelRenderState().cameraRenderState;
        Vec3 eye = camera.pos;
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        Font font = client.font;
        for (AnalyserData.Floating floating : data.floating()) {
            Vec3 top = Vec3.atBottomCenterOf(floating.pos().above())
                    .add(0, 0.2 + floating.lines().size() * LINE, 0);
            if (top.distanceTo(eye) > RANGE) {
                continue;
            }
            for (int i = 0; i < floating.lines().size(); i++) {
                String text = floating.lines().get(i);
                poses.pushPose();
                poses.translate(top.x - eye.x, top.y - i * LINE - eye.y, top.z - eye.z);
                poses.mulPose(camera.orientation);
                poses.scale(-0.025F, -0.025F, 0.025F);
                Matrix4f matrix = poses.last().pose();
                font.drawInBatch(text, -font.width(text) / 2.0F, 0, 0xFFFFFFFF, false, matrix,
                        buffers, Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
                poses.popPose();
            }
        }
        buffers.endBatch();
    }
}
