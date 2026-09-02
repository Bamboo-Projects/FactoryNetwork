package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.MachineLayouts;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the press's ram — the one part that moves.
 *
 * <p><b>Why not in the block model.</b> The ram travels down and back up
 * again while work is being done. A block model stands still, and a block
 * state per intermediate position would be thirty states for one movement.
 * It is therefore a model of its own, drawn here and shifted in the process
 * — the same procedure as with the connectors on the cable.
 *
 * <p><b>Why the movement comes from the game time and not from the
 * progress.</b> The progress is held in the BlockEntity and reaches the
 * client only when a block update is sent — sending one every tick, just so
 * that a ram runs smoothly, would be network load for nothing. The client
 * therefore only asks <i>whether</i> work is being done, and computes the
 * movement itself. One stroke per second, evenly — how far along the press
 * currently is, the GUI tells you anyway, not the ram.
 */
public class PressRenderer implements BlockEntityRenderer<PressBlockEntity> {

    /** The model of the ram, which FnClient registers separately. */
    public static final ModelResourceLocation RAM = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "block/press_ram"));

    /** One stroke per second. */
    private static final float TICKS_PER_STROKE = 20.0F;

    private final Minecraft client = Minecraft.getInstance();

    public PressRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PressBlockEntity press, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        if (press.getLevel() == null) {
            return;
        }

        poses.pushPose();
        // The press faces in four directions; its model is rotated by the
        // block state, the ram has to turn along itself.
        Direction facing = press.getBlockState()
                .getValue(HorizontalDirectionalBlock.FACING);
        poses.translate(0.5F, 0.5F, 0.5F);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -facing.toYRot() + 180.0F));
        poses.translate(-0.5F, -0.5F + drop(press, partialTick), -0.5F);

        client.getBlockRenderer().getModelRenderer().renderModel(
                poses.last(), buffers.getBuffer(RenderType.cutout()),
                press.getBlockState(), client.getModelManager().getModel(RAM),
                1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY,
                RenderType.cutout());
        poses.popPose();
    }

    /**
     * How deep the ram currently sits, in blocks.
     *
     * <p>Zero as long as nothing is running — then it hangs at the top.
     * Otherwise a smooth path down and back, whose depth is exactly the
     * distance down to the anvil.
     */
    private static float drop(PressBlockEntity press, float partialTick) {
        if (press.progress() <= 0 || press.getLevel() == null) {
            return 0.0F;
        }
        float phase = (press.getLevel().getGameTime() % (long) TICKS_PER_STROKE
                + partialTick) / TICKS_PER_STROKE;
        float wave = 0.5F - 0.5F * (float) Math.cos(2.0 * Math.PI * phase);
        return -MachineLayouts.pressStroke() / 16.0F * wave;
    }
}
