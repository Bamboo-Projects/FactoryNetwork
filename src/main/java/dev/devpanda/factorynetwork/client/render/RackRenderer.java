package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.network.ServerBay;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Zeigt an der Front, welche Einschübe laufen.
 *
 * <p>Drei Zustände, und der mittlere ist der wichtige: <b>angefangen und
 * nicht fertig</b>. Ein Einschub, in dem zwei von drei Bauteilen stecken,
 * sieht von weitem aus wie ein voller und rechnet doch nicht — ohne eigene
 * Farbe dafür sucht man den Fehler im Programm.
 *
 * <p>Gezeichnet über zwei Blöcke: Der Schrank ist zwei hoch, die BlockEntity
 * sitzt unten, und die oberen sechs Einschübe liegen im Nachbarblock.
 */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity> {

    private static final ResourceLocation BLADES = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/rack_blades.png");

    /** Leer, angefangen, laufend. */
    private static final float TILES = 3.0F;
    private static final double MAX_DISTANCE = 32.0;

    /** Ein Block ist sechzehn Pixel; die Front des Schranks ist zweiunddreißig. */
    private static final float PIXEL = 16.0F;

    /** So weit liegt die Front hinter dem Rahmen — dieselben zwei Pixel wie im Modell. */
    private static final float DEPTH = 2.0F / PIXEL;

    private static final float BAY_X0 = 3.0F;
    private static final float BAY_X1 = 13.0F;

    /** Oberkante des ersten Einschubs, gemessen von oben über beide Blöcke. */
    private static final float BAY_TOP = 2.0F;
    private static final float BAY_HEIGHT = 2.0F;

    /** Die achtundzwanzig Pixel zwischen den Rahmenleisten, geteilt durch zwölf. */
    private static final float BAY_STEP = 28.0F / RackBlockEntity.BAYS;

    public RackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RackBlockEntity rack, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        Direction facing = rack.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(BLADES));
        int dark = LevelRenderer.getLightColor(rack.getLevel(),
                rack.getBlockPos().relative(facing));

        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        Matrix4f matrix = poses.last().pose();
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            int kind = kindOf(rack.bay(bay));
            float top = BAY_TOP + bay * BAY_STEP;
            FaceOverlay.tile(buffer, matrix, facing,
                    BAY_X0 / PIXEL, top / PIXEL - 1.0F,
                    BAY_X1 / PIXEL, (top + BAY_HEIGHT) / PIXEL - 1.0F,
                    kind / TILES, (kind + 1) / TILES,
                    kind == 0 ? dark : LightTexture.FULL_BRIGHT, DEPTH);
        }
        poses.popPose();
    }

    private static int kindOf(ServerBay bay) {
        if (!bay.occupied()) {
            return 0;
        }
        return bay.complete() ? 2 : 1;
    }

    /**
     * Der Schrank reicht einen Block höher als seine BlockEntity.
     *
     * <p>Ohne diese Angabe verschwinden die oberen sechs Einschübe, sobald
     * der untere Block aus dem Blickfeld rutscht — man sieht den Schrank und
     * seine Lämpchen sind weg.
     */
    @Override
    public AABB getRenderBoundingBox(RackBlockEntity rack) {
        return new AABB(rack.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    public boolean shouldRender(RackBlockEntity rack, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(rack.getBlockPos()), MAX_DISTANCE);
    }
}
