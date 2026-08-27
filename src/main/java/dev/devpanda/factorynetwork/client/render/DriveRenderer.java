package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellTier;
import dev.devpanda.factorynetwork.storage.EnergyCellItem;
import dev.devpanda.factorynetwork.storage.FluidCellItem;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Zeigt an der Front, welche Zellen stecken.
 *
 * <p>Ein Laufwerk, dem man nicht ansieht, ob es voll bestückt ist, zwingt zum
 * Anklicken. Bei einer Wand aus zehn Laufwerken ist das der Unterschied
 * zwischen Hinsehen und Suchen.
 *
 * <p><b>Gezeigt wird die Zelle, nicht ihr Füllstand.</b> Der Inhalt lebt im
 * Laufwerk und steht erst beim Sichern im Gegenstand — was der Client kennt,
 * wäre der Stand von vorhin. Lieber gar keine Anzeige als eine, die
 * hinterherhinkt; wie voll es ist, sagen Jade und das Fenster.
 */
public class DriveRenderer implements BlockEntityRenderer<DriveBlockEntity> {

    private static final ResourceLocation BAYS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/drive_bays.png");

    /** Leer, dann die vier Größen, dann die Flüssigkeitszelle. */
    private static final float TILES = 7.0F;

    /** Weiter als das ist ein Schacht von fünf Blockpixeln nicht zu lesen. */
    private static final double MAX_DISTANCE = 24.0;

    /** Die Schächte liegen so, wie sie {@code textures.py} malt. */
    private static final float TEXTURE = 64.0F;
    /**
     * So weit liegt das Schachtfeld hinter der Fassung — dieselbe Zahl wie im
     * Modell.
     *
     * <p>Solange die Front ein flacher Würfel war, gab es sie nicht. Seit dem
     * Umbau liegt das Feld einen Blockpixel tiefer, und ohne diesen Abstand
     * schwebten die Zellen davor.
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

    @Override
    public void render(DriveBlockEntity drive, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        Direction facing = drive.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(BAYS));
        int dark = LevelRenderer.getLightColor(drive.getLevel(),
                drive.getBlockPos().relative(facing));

        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        Matrix4f matrix = poses.last().pose();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = row * COLUMNS + column;
                int kind = kindOf(drive.cell(slot));
                int x = BAY_X + column * COLUMN_STEP;
                int y = BAY_Y + row * ROW_STEP;
                FaceOverlay.tile(buffer, matrix, facing,
                        x / TEXTURE, y / TEXTURE,
                        (x + BAY_W) / TEXTURE, (y + BAY_H) / TEXTURE,
                        kind / TILES, (kind + 1) / TILES,
                        kind == 0 ? dark : LightTexture.FULL_BRIGHT, DEPTH);
            }
        }
        poses.popPose();
    }

    /** Welche Kachel des Streifens zu dieser Zelle gehört. */
    private static int kindOf(ItemStack stack) {
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
