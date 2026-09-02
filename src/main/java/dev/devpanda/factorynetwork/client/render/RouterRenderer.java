package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Paints onto each face of a router which lane it carries.
 *
 * <p><b>Why drawn and not baked:</b> six faces with five values each are
 * 15625 block states. Minecraft creates them all at startup, and each of
 * them would get a baked model — for a piece of information that amounts to
 * no more than a ring.
 *
 * <p>The marker sits at the edge of the face, not in its centre: a thick
 * cable covers the middle ten block pixels. A marker there would be hidden
 * exactly when the face is connected — that is, always when you want to read
 * it.
 */
public class RouterRenderer implements BlockEntityRenderer<RouterBlockEntity> {

    /** A strip of five tiles: off, then lane one to four. */
    private static final ResourceLocation LANES = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/router_lanes.png");
    /** Two tiles: disconnected and open. The colour comes at draw time. */
    private static final float TILES = 2;

    /** Beyond that a ring of two block pixels can no longer be read. */
    private static final double MAX_DISTANCE = 24.0;

    /** Just in front of the face, so that nothing flickers. */
    private static final float OFFSET = 0.5F + 0.004F;
    private static final float HALF = 0.5F;

    public RouterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RouterBlockEntity router, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(LANES));
        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        Matrix4f matrix = poses.last().pose();
        for (Direction side : Direction.values()) {
            int lane = router.lane(side);
            // An open face glows, a disconnected one does not. The lighting
            // for it comes from the neighbouring position: inside the block
            // itself it is dark, since that is where the block stands.
            int faceLight = lane == RouterBlockEntity.OFF
                    ? LevelRenderer.getLightColor(router.getLevel(),
                            router.getBlockPos().relative(side))
                    : LightTexture.FULL_BRIGHT;
            // <b>Two tiles, coloured.</b> Ever since the router carries
            // colours instead of four lanes, there would be eighteen states —
            // a strip with eighteen tiles would be a texture no one could
            // redraw any more. The ring is grey and gets its colour at draw
            // time, just like the cable.
            quad(buffer, matrix, side, lane == RouterBlockEntity.OFF ? 0 : 1,
                    colourOf(side, router), faceLight);
        }
        poses.popPose();
    }

    /**
     * A tile onto a face.
     *
     * <p>The corners are computed from two axes in the face rather than from
     * a rotation. The ring is four-fold symmetric, so it makes no difference
     * which of the two axes lies on top — and a computation that rotates
     * nothing cannot get twisted either.
     */
    /**
     * Which colour the marker of this face carries.
     *
     * <p>Grey for "all" and for "off" — neither is a colour, and a hue for it
     * would be one colour too many in the image.
     */
    private static int colourOf(Direction side, RouterBlockEntity router) {
        var filter = router.filter(side);
        if (filter == null || filter.dye() == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | filter.dye().getTextureDiffuseColor();
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Direction side,
                             int lane, int colour, int light) {
        float nx = side.getStepX();
        float ny = side.getStepY();
        float nz = side.getStepZ();
        // Two axes in the face: for horizontal faces X and Z, otherwise the
        // remaining horizontal one and Y.
        boolean vertical = side.getAxis().isVertical();
        float ux = side.getAxis() == Direction.Axis.X ? 0 : 1;
        float uz = side.getAxis() == Direction.Axis.X ? 1 : 0;
        float vy = vertical ? 0 : 1;
        float vz = vertical ? 1 : 0;

        float u0 = lane / TILES;
        float u1 = (lane + 1) / TILES;

        corner(buffer, matrix, nx, ny, nz, -1, -1, ux, uz, vy, vz, u0, 1, colour, light);
        corner(buffer, matrix, nx, ny, nz, 1, -1, ux, uz, vy, vz, u1, 1, colour, light);
        corner(buffer, matrix, nx, ny, nz, 1, 1, ux, uz, vy, vz, u1, 0, colour, light);
        corner(buffer, matrix, nx, ny, nz, -1, 1, ux, uz, vy, vz, u0, 0, colour, light);
    }

    private static void corner(VertexConsumer buffer, Matrix4f matrix,
                               float nx, float ny, float nz, float su, float sv,
                               float ux, float uz, float vy, float vz,
                               float u, float v, int colour, int light) {
        float x = nx * OFFSET + su * ux * HALF;
        float y = ny * OFFSET + sv * vy * HALF;
        float z = nz * OFFSET + su * uz * HALF + sv * vz * HALF;
        buffer.addVertex(matrix, x, y, z)
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    @Override
    public boolean shouldRender(RouterBlockEntity router, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(router.getBlockPos()), MAX_DISTANCE);
    }
}
