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
 * Malt auf jede Seite eines Routers, welche Bahn sie führt.
 *
 * <p><b>Warum gezeichnet und nicht gebacken:</b> Sechs Seiten mit je fünf
 * Werten sind 15625 Blockzustände. Die legt Minecraft beim Start alle an, und
 * jeder davon bekäme ein gebackenes Modell — für eine Auskunft, die sich in
 * einem Ring erschöpft.
 *
 * <p>Die Kennung liegt am Rand der Fläche, nicht in ihrer Mitte: Ein dickes
 * Kabel deckt die mittleren zehn Blockpixel ab. Eine Kennung dort wäre genau
 * dann verdeckt, wenn die Seite angeschlossen ist — also immer dann, wenn man
 * sie lesen will.
 */
public class RouterRenderer implements BlockEntityRenderer<RouterBlockEntity> {

    /** Ein Streifen aus fünf Kacheln: aus, dann Bahn eins bis vier. */
    private static final ResourceLocation LANES = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/router_lanes.png");
    private static final float TILES = RouterBlockEntity.LANES + 1;

    /** Weiter als das ist ein Ring von zwei Blockpixeln nicht mehr zu lesen. */
    private static final double MAX_DISTANCE = 24.0;

    /** Knapp vor der Fläche, damit nichts flimmert. */
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
            // Eine geführte Bahn leuchtet, eine abgeklemmte nicht. Die
            // Beleuchtung dafür kommt von der Nachbarstelle: Im Block selbst
            // ist es dunkel, dort steht ja der Block.
            int faceLight = lane == RouterBlockEntity.OFF
                    ? LevelRenderer.getLightColor(router.getLevel(),
                            router.getBlockPos().relative(side))
                    : LightTexture.FULL_BRIGHT;
            quad(buffer, matrix, side, lane, faceLight);
        }
        poses.popPose();
    }

    /**
     * Eine Kachel auf eine Seite.
     *
     * <p>Die Ecken werden aus zwei Achsen in der Fläche gerechnet statt aus
     * einer Drehung. Der Ring ist vierfach symmetrisch, also spielt es keine
     * Rolle, welche der beiden Achsen oben liegt — und eine Rechnung, die
     * nichts dreht, kann sich auch nicht verdrehen.
     */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, Direction side,
                             int lane, int light) {
        float nx = side.getStepX();
        float ny = side.getStepY();
        float nz = side.getStepZ();
        // Zwei Achsen in der Fläche: bei waagerechten Seiten X und Z, sonst
        // die verbleibende waagerechte und Y.
        boolean vertical = side.getAxis().isVertical();
        float ux = side.getAxis() == Direction.Axis.X ? 0 : 1;
        float uz = side.getAxis() == Direction.Axis.X ? 1 : 0;
        float vy = vertical ? 0 : 1;
        float vz = vertical ? 1 : 0;

        float u0 = lane / TILES;
        float u1 = (lane + 1) / TILES;

        corner(buffer, matrix, nx, ny, nz, -1, -1, ux, uz, vy, vz, u0, 1, light);
        corner(buffer, matrix, nx, ny, nz, 1, -1, ux, uz, vy, vz, u1, 1, light);
        corner(buffer, matrix, nx, ny, nz, 1, 1, ux, uz, vy, vz, u1, 0, light);
        corner(buffer, matrix, nx, ny, nz, -1, 1, ux, uz, vy, vz, u0, 0, light);
    }

    private static void corner(VertexConsumer buffer, Matrix4f matrix,
                               float nx, float ny, float nz, float su, float sv,
                               float ux, float uz, float vy, float vz,
                               float u, float v, int light) {
        float x = nx * OFFSET + su * ux * HALF;
        float y = ny * OFFSET + sv * vy * HALF;
        float z = nz * OFFSET + su * uz * HALF + sv * vz * HALF;
        buffer.addVertex(matrix, x, y, z)
                .setColor(0xFFFFFFFF)
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
