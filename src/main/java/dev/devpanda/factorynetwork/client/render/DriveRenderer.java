package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellTier;
import dev.devpanda.factorynetwork.storage.ChemicalCellItem;
import dev.devpanda.factorynetwork.storage.EnergyCellItem;
import dev.devpanda.factorynetwork.storage.FluidCellItem;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shows on the front which cells are inserted.
 *
 * <p>A drive you cannot tell at a glance is fully stocked forces a click.
 * With a wall of ten drives, that is the difference between looking and
 * searching.
 *
 * <p><b>What is shown is the cell, not its fill level.</b> The contents live
 * in the drive and only reach the item when saved — what the client knows
 * would be the state from before. Better no display at all than one that
 * lags behind; how full it is, Jade and the window tell you.
 */
public class DriveRenderer implements BlockEntityRenderer<DriveBlockEntity, DriveRenderer.State> {

    private static final Identifier BAYS = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/drive_bays.png");

    /** Empty, four item sizes, fluid, energy, then chemical. See textures.py. */
    private static final float TILES = 8.0F;

    /** Beyond that a bay of five block pixels cannot be read. */
    private static final double MAX_DISTANCE = 24.0;

    /** The bays are laid out the way {@code textures.py} draws them. */
    private static final float TEXTURE = 64.0F;
    /**
     * How far the bay panel sits behind the housing — the same number as in
     * the model.
     *
     * <p>As long as the front was a flat cube, it did not exist. Since the
     * rebuild the panel sits one block pixel deeper, and without this offset
     * the cells would float in front of it.
     */
    private static final float DEPTH =
            dev.devpanda.factorynetwork.block.DriveLayout.RECESS / 16.0F;

    private static final int BAY_X = 10;
    private static final int BAY_Y = 12;
    private static final int BAY_W = 20;
    private static final int BAY_H = 6;
    private static final int COLUMN_STEP = 24;
    private static final int ROW_STEP = 8;
    private static final int ROWS = 5;
    private static final int COLUMNS = 2;

    public DriveRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * What one frame needs from the drive.
     *
     * <p>Since 26.1 drawing happens in two steps: first everything is read
     * from the block entity, then drawn from this copy alone.
     */
    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        int dark;
        final int[] kinds = new int[ROWS * COLUMNS];
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DriveBlockEntity drive, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(drive, state, partialTick, camera, breakProgress);
        state.facing = drive.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        state.dark = drive.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT
                : LevelRenderer.getLightCoords(drive.getLevel(),
                        drive.getBlockPos().relative(state.facing));
        for (int slot = 0; slot < state.kinds.length; slot++) {
            state.kinds[slot] = kindOf(drive.cell(slot));
        }
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        Direction facing = state.facing;
        int dark = state.dark;

        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(BAYS), (pose, buffer) -> {
        Matrix4f matrix = pose.pose();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = row * COLUMNS + column;
                int kind = state.kinds[slot];
                int x = BAY_X + column * COLUMN_STEP;
                int y = BAY_Y + row * ROW_STEP;
                FaceOverlay.tile(buffer, matrix, facing,
                        x / TEXTURE, y / TEXTURE,
                        (x + BAY_W) / TEXTURE, (y + BAY_H) / TEXTURE,
                        kind / TILES, (kind + 1) / TILES,
                        kind == 0 ? dark : LightCoordsUtil.FULL_BRIGHT, DEPTH);
            }
        }
        });
        poses.popPose();
    }

    /** Which tile of the strip belongs to this cell. */
    private static int kindOf(ItemStack stack) {
        if (stack.getItem() instanceof ChemicalCellItem) {
            return 7;
        }
        if (stack.getItem() instanceof FluidCellItem) {
            return 5;
        }
        if (stack.getItem() instanceof EnergyCellItem) {
            return 6;
        }
        CellTier tier = StorageCellItem.tierOf(stack);
        return tier == null ? 0 : tier.ordinal() + 1;
    }

    @Override
    public boolean shouldRender(DriveBlockEntity drive, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(drive.getBlockPos()), MAX_DISTANCE);
    }
}
