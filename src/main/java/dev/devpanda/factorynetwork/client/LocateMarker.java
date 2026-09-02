package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shows a spot in the network in the world.
 *
 * <p><b>The move that turns a text editor into a factory interface.</b> In the
 * code it says {@code crusher_1}; in a factory with forty furnaces that does
 * not yet say which one that is. Ctrl and a click on the name sets a marker,
 * and it still stands there long after the terminal is closed.
 *
 * <p>Visible through walls: whoever is searching does not know which one it
 * lies behind. And with a time limit, so it does not stay on screen until
 * logout — two minutes reach right across a base.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class LocateMarker {

    /** This long a marker stays, in ticks. */
    private static final int LIFETIME = 20 * 120;

    /** This close you have to get for it to vanish by itself. */
    private static final double ARRIVED = 3.0;

    /**
     * This long it stands in any case, in ticks.
     *
     * <p><b>Otherwise it fails to answer precisely when you ask.</b> Whoever
     * stands in front of four connectors and wants to know which one is
     * {@code kiste_2} is standing closer than {@link #ARRIVED} — the marker
     * thereby counted as reached and vanished in the same frame in which it
     * was set. That looked like a broken move and was an assumption about the
     * distance from which someone searches.
     */
    private static final int MINIMUM = 20 * 30;

    private static final int COLOUR = 0xFF78DC8C;
    private static final int BACKGROUND = 0x88000000;

    private static BlockPos target;
    private static String label = "";
    private static long until;

    /** When it was set — for {@link #MINIMUM}. */
    private static long since;

    private LocateMarker() {
    }

    /** Sets the marker. A previous one is replaced in doing so. */
    public static void mark(BlockPos pos, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        target = pos;
        label = name;
        since = minecraft.level.getGameTime();
        until = since + LIFETIME;
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
        // Expired or arrived — both mean the marker has served its purpose.
        // "Arrived" only counts after MINIMUM, though: whoever is already
        // standing in front of it while asking should still get an answer.
        long now = minecraft.level.getGameTime();
        boolean arrived = now - since >= MINIMUM
                && minecraft.player.position().distanceTo(target.getCenter()) < ARRIVED;
        if (now > until || arrived) {
            target = null;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        // The box around the block. A hair larger than the block itself,
        // otherwise the line lies in its surface and flickers.
        //
        // <b>By hand and not via RenderType.lines():</b> that brings its own
        // depth test along, and then the marker lies behind the first wall —
        // exactly where you are looking for it. The same approach as with the
        // analyser, which shows the network through walls for the same reason.
        poses.pushPose();
        poses.translate(-eye.x, -eye.y, -eye.z);
        Matrix4f box = poses.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(3.0F);

        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR);
        outline(buffer, box, target);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poses.popPose();

        // And the name above it, so you can read it from afar.
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

    /**
     * The twelve edges of a block.
     *
     * <p>A hair larger than the block, otherwise the line lies in its surface
     * and flickers.
     */
    private static void outline(com.mojang.blaze3d.vertex.BufferBuilder buffer,
                                Matrix4f matrix, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.02);
        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;

        // Bottom
        line(buffer, matrix, x0, y0, z0, x1, y0, z0);
        line(buffer, matrix, x1, y0, z0, x1, y0, z1);
        line(buffer, matrix, x1, y0, z1, x0, y0, z1);
        line(buffer, matrix, x0, y0, z1, x0, y0, z0);
        // Top
        line(buffer, matrix, x0, y1, z0, x1, y1, z0);
        line(buffer, matrix, x1, y1, z0, x1, y1, z1);
        line(buffer, matrix, x1, y1, z1, x0, y1, z1);
        line(buffer, matrix, x0, y1, z1, x0, y1, z0);
        // Uprights
        line(buffer, matrix, x0, y0, z0, x0, y1, z0);
        line(buffer, matrix, x1, y0, z0, x1, y1, z0);
        line(buffer, matrix, x1, y0, z1, x1, y1, z1);
        line(buffer, matrix, x0, y0, z1, x0, y1, z1);
    }

    private static void line(com.mojang.blaze3d.vertex.BufferBuilder buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(0.47F, 0.86F, 0.55F, 1.0F);
        buffer.addVertex(matrix, x1, y1, z1).setColor(0.47F, 0.86F, 0.55F, 1.0F);
    }
}
