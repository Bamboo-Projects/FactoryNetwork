package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
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
 * Shows on the front which bays are running.
 *
 * <p>Three states, and the middle one is the important one: <b>started and
 * not finished</b>. A bay in which a chassis without storage media sits looks
 * from afar like a full one and yet does not compute — without a colour of
 * its own for it you go looking for the bug in the program.
 *
 * <p>Drawn across two blocks: the rack is two tall, the BlockEntity sits at
 * the bottom, and the upper six bays lie in the neighbouring block.
 */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity> {

    private static final ResourceLocation BLADES = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/rack_blades.png");

    /** Empty, started, running. */
    private static final float TILES = 3.0F;
    private static final double MAX_DISTANCE = 32.0;

    /** A block is sixteen pixels; the front of the rack is thirty-two. */
    private static final float PIXEL = 16.0F;

    /** How far the front sits behind the frame — the same two pixels as in the model. */
    private static final float DEPTH = 2.0F / PIXEL;

    private static final float BAY_X0 = 3.0F;
    private static final float BAY_X1 = 13.0F;

    /** Top edge of the first bay, measured from the top across both blocks. */
    private static final float BAY_TOP = 2.0F;
    private static final float BAY_HEIGHT = 2.0F;

    /** The twenty-eight pixels between the frame rails, divided by twelve. */
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
            int kind = kindOf(rack, bay);
            float top = BAY_TOP + bay * BAY_STEP;
            FaceOverlay.tile(buffer, matrix, facing,
                    BAY_X0 / PIXEL, top / PIXEL - 1.0F,
                    BAY_X1 / PIXEL, (top + BAY_HEIGHT) / PIXEL - 1.0F,
                    kind / TILES, (kind + 1) / TILES,
                    kind == 0 ? dark : LightTexture.FULL_BRIGHT, DEPTH);
        }
        poses.popPose();
    }

    /**
     * Empty, started, running.
     *
     * <p>Started is already a chassis without hardware: it looks like a
     * server and is none. The window gives the same information, and two
     * places that count differently would be worse than one that stays
     * silent.
     */
    private static int kindOf(RackBlockEntity rack, int bay) {
        if (!rack.hasChassis(bay)) {
            return 0;
        }
        return rack.bay(bay).complete() ? 2 : 1;
    }

    /**
     * The rack reaches one block higher than its BlockEntity.
     *
     * <p>Without this declaration the upper six bays disappear as soon as the
     * lower block slides out of view — you see the rack and its indicator
     * lights are gone.
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
