package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.analyser.AnalyserData;
import dev.devpanda.factorynetwork.client.ClientAnalyserState;
import dev.devpanda.factorynetwork.item.NetworkAnalyserItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the network into the world as a wireframe.
 *
 * <p><b>Without a depth test.</b> That is the one property everything hangs
 * on: in a built-up base the cables lie behind walls, and a troubleshooting
 * tool that only shows what is visible anyway is no help. ExtendedAE's network
 * analyser makes the same decision.
 *
 * <p>Nodes are small cubes, links are lines. The colour says what is going
 * on — green means room, yellow tight, red full or broken.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class AnalyserRenderer {

    private static final float NODE = 0.12f;

    private AnalyserRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || !(client.player.getMainHandItem().getItem() instanceof NetworkAnalyserItem)) {
            return;
        }
        if (!ClientAnalyserState.isFresh()) {
            return;
        }
        AnalyserData data = ClientAnalyserState.data();
        if (data.nodes().isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        // Through walls: a pipeline without a depth test, see FnRenderTypes.
        var buffers = client.renderBuffers().bufferSource();
        var pose = stack.last();
        drawLinks(data, pose, buffers.getBuffer(
                dev.devpanda.factorynetwork.client.render.FnRenderTypes.LINES_SEE_THROUGH));
        drawNodes(data, pose, buffers.getBuffer(
                dev.devpanda.factorynetwork.client.render.FnRenderTypes.BOXES_SEE_THROUGH));
        buffers.endBatch(dev.devpanda.factorynetwork.client.render.FnRenderTypes.LINES_SEE_THROUGH);
        buffers.endBatch(dev.devpanda.factorynetwork.client.render.FnRenderTypes.BOXES_SEE_THROUGH);
        stack.popPose();
    }

    private static void drawLinks(AnalyserData data,
                                  com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
                                  com.mojang.blaze3d.vertex.VertexConsumer buffer) {
        for (AnalyserData.Link link : data.links()) {
            float[] colour = colourOf(link.state());
            line(buffer, matrix, centre(link.from()), centre(link.to()), colour);
        }
    }

    private static void drawNodes(AnalyserData data,
                                  com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
                                  com.mojang.blaze3d.vertex.VertexConsumer buffer) {
        for (AnalyserData.Node node : data.nodes()) {
            float[] colour = colourOf(node.state());
            cube(buffer, matrix, centre(node.pos()), NODE, colour);
        }
    }

    private static Vec3 centre(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static float[] colourOf(AnalyserData.LinkState state) {
        return switch (state) {
            case FREE -> new float[] {0.35f, 0.85f, 0.35f, 0.75f};
            case TIGHT -> new float[] {0.95f, 0.75f, 0.20f, 0.85f};
            case FULL -> new float[] {0.95f, 0.25f, 0.20f, 0.95f};
        };
    }

    private static float[] colourOf(AnalyserData.NodeState state) {
        return switch (state) {
            case CONTROLLER -> new float[] {0.40f, 0.70f, 1.00f, 0.95f};
            case DEVICE -> new float[] {0.85f, 0.85f, 0.85f, 0.80f};
            case DISPLAY -> new float[] {0.60f, 0.60f, 0.75f, 0.70f};
            case UNNAMED -> new float[] {0.95f, 0.75f, 0.20f, 0.90f};
            case DUPLICATE -> new float[] {0.95f, 0.40f, 0.85f, 0.95f};
            // Congested, not dead: the same red, but it now means slower
            // instead of silent.
            case CONGESTED -> new float[] {0.95f, 0.25f, 0.20f, 0.95f};
            case DRIVE -> new float[] {0.45f, 0.85f, 0.60f, 0.75f};
            case RACK -> new float[] {0.85f, 0.65f, 0.35f, 0.75f};
            case ROUTER -> new float[] {0.70f, 0.75f, 0.85f, 0.60f};
            // Paler than the controller: the same family, a different role.
            case EXTENSION -> new float[] {0.40f, 0.70f, 1.00f, 0.55f};
        };
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer buffer,
                             com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
                             Vec3 from, Vec3 to, float[] c) {
        float dx = (float) (to.x - from.x);
        float dy = (float) (to.y - from.y);
        float dz = (float) (to.z - from.z);
        buffer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(c[0], c[1], c[2], c[3])
                .setNormal(matrix, dx, dy, dz).setLineWidth(3.0F);
        buffer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(c[0], c[1], c[2], c[3])
                .setNormal(matrix, dx, dy, dz).setLineWidth(3.0F);
    }

    /** A small cube around a point — six faces, no texture. */
    private static void cube(com.mojang.blaze3d.vertex.VertexConsumer buffer,
                             com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
                             Vec3 at, float size, float[] c) {
        float x0 = (float) at.x - size;
        float y0 = (float) at.y - size;
        float z0 = (float) at.z - size;
        float x1 = (float) at.x + size;
        float y1 = (float) at.y + size;
        float z1 = (float) at.z + size;

        quad(buffer, matrix, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(buffer, matrix, c, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(buffer, matrix, c, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(buffer, matrix, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(buffer, matrix, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, matrix, c, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer,
                             com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
                             float[] c, float... p) {
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(matrix, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
                    .setColor(c[0], c[1], c[2], c[3]);
        }
    }
}
