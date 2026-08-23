package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Zeigt eine Stelle im Netz in der Welt an.
 *
 * <p><b>Der Griff, der aus einem Texteditor eine Fabrik-Oberfläche macht.</b>
 * Im Code steht {@code crusher_1}; in einer Fabrik mit vierzig Öfen ist damit
 * noch nicht gesagt, welcher das ist. Strg und Klick auf den Namen setzt eine
 * Marke, und die steht auch noch da, wenn das Terminal längst zu ist.
 *
 * <p>Durch Wände sichtbar: Wer sucht, weiß nicht, hinter welcher sie liegt.
 * Und mit einer Frist, damit sie nicht bis zum Ausloggen im Bild steht — zwei
 * Minuten reichen quer durch eine Basis.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class LocateMarker {

    /** So lange bleibt eine Marke stehen, in Ticks. */
    private static final int LIFETIME = 20 * 120;

    /** So nah muss man heran, damit sie von selbst verschwindet. */
    private static final double ARRIVED = 3.0;

    private static final int COLOUR = 0xFF78DC8C;
    private static final int BACKGROUND = 0x88000000;

    private static BlockPos target;
    private static String label = "";
    private static long until;

    private LocateMarker() {
    }

    /** Setzt die Marke. Eine vorherige wird dabei abgelöst. */
    public static void mark(BlockPos pos, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        target = pos;
        label = name;
        until = minecraft.level.getGameTime() + LIFETIME;
    }

    public static void clear() {
        target = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || target == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // Abgelaufen oder angekommen — beides heißt, die Marke hat ihren
        // Zweck erfüllt.
        if (minecraft.level.getGameTime() > until
                || minecraft.player.position().distanceTo(target.getCenter()) < ARRIVED) {
            target = null;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        // Der Kasten um den Block. Ein Hauch größer als der Block selbst,
        // sonst liegt die Linie in seiner Fläche und flackert.
        poses.pushPose();
        poses.translate(-eye.x, -eye.y, -eye.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB box = new AABB(target).inflate(0.02);
        LevelRenderer.renderLineBox(poses, lines, box, 0.47F, 0.86F, 0.55F, 1.0F);
        poses.popPose();
        buffers.endBatch(RenderType.lines());

        // Und der Name darüber, damit man ihn schon von weitem liest.
        Font font = minecraft.font;
        poses.pushPose();
        poses.translate(
                target.getX() + 0.5 - eye.x,
                target.getY() + 1.4 - eye.y,
                target.getZ() + 0.5 - eye.z);
        poses.mulPose(camera.rotation());
        poses.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poses.last().pose();
        font.drawInBatch(label, -font.width(label) / 2.0F, 0, COLOUR, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
        poses.popPose();
        buffers.endBatch();
    }
}
