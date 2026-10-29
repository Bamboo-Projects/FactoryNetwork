package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.MachineLayouts;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

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
public class PressRenderer implements BlockEntityRenderer<PressBlockEntity, PressRenderer.State> {

    /** The model of the ram, which FnClient registers separately. */
    /** Where the ram's model lies; registered in {@code FnClient}. */
    public static final Identifier RAM_MODEL =
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "block/press_ram");

    /** The ram as a standalone model, see {@code ConnectorPartModels}. */
    public static final StandaloneModelKey<BlockStateModelPart> RAM =
            new StandaloneModelKey<>(RAM_MODEL::toString);

    /** Where the ram stands this frame; see DriveRenderer.State. */
    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float drop;
        boolean placed;
    }

    /** One stroke per second. */
    private static final float TICKS_PER_STROKE = 20.0F;

    private final Minecraft client = Minecraft.getInstance();

    public PressRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PressBlockEntity press, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(press, state, partialTick, camera, breakProgress);
        state.placed = press.getLevel() != null;
        // The press faces in four directions; its model is rotated by the
        // block state, the ram has to turn along itself.
        state.facing = press.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        state.drop = drop(press, partialTick);
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (!state.placed) {
            return;
        }
        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -state.facing.toYRot() + 180.0F));
        poses.translate(-0.5F, -0.5F + state.drop, -0.5F);
        collector.submitBlockModel(poses, RenderTypes.cutoutMovingBlock(),
                java.util.List.of(client.getModelManager().getStandaloneModel(RAM)), new int[0],
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
