package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.block.DisplayWall;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the lines of a display onto its front face.
 *
 * <p>Text in the world, not in a GUI — you should see how the factory is
 * doing in passing, without clicking anything. That is the whole purpose of
 * the block; a display for which you first have to open a window would be a
 * clumsy terminal.
 *
 * <p><b>A wall of panels is one screen.</b> Writing starts from the panel at
 * the bottom left, and spans the whole area.
 *
 * <p><b>The text does not grow along on its own.</b> The room of a large wall
 * goes into more lines and longer ones — a wall whose text grows with it is
 * just as readable from three metres as a single panel and gives away the
 * entire advantage. Whoever wants a heading readable from twenty metres
 * writes {@code scale 4} into the display block: then the one who built the
 * wall decides, and not the wall.
 *
 * <p>Beyond a certain distance nothing is drawn any more: twenty displays on
 * a wall, each with ten lines, are two hundred blocks of text per frame.
 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity> {

    /** Beyond that the text cannot be read anyway. */
    private static final double MAX_DISTANCE = 16.0;

    private static final float SCALE = 0.006F;
    private static final int LINE_HEIGHT = 9;

    /** This many lines a panel carries — a wall correspondingly more. */
    private static final int LINES_PER_PANEL = 12;

    /** How many text units a block is tall and wide. */
    private static final float UNITS_PER_BLOCK = 1.0F / SCALE;

    private final Font font;

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(DisplayBlockEntity display, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        List<String> lines = display.lines();
        if (lines.isEmpty()) {
            return;
        }

        Direction facing = display.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        DisplayWall wall = display.wall();
        int textScale = display.textScale();
        poses.pushPose();

        // To the centre of the front face, then rotate to the facing direction.
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.translate(0, 0, -0.47);
        poses.scale(-SCALE * textScale, -SCALE * textScale, SCALE);

        // From the panel's own position to the centre of the wall. The scale
        // is already set, so this is computed in text units here — and the
        // x-axis is mirrored, hence the minus.
        float toCentreX = -((wall.columns() - 1) / 2.0F - wall.anchorColumn());
        float toCentreY = -((wall.rows() - 1) / 2.0F - wall.anchorRow());
        // The scale is now baked into the matrix, so a block is fewer text
        // units wide. Without the division the shift would scale with the
        // text size and the wall would slide out of the frame.
        float unitsPerBlock = UNITS_PER_BLOCK / textScale;
        poses.translate(toCentreX * unitsPerBlock, toCentreY * unitsPerBlock, 0.0F);

        // Large text, fewer lines — the trade-off is out in the open, and the
        // player chose it themselves.
        int room = Math.max(1, wall.rows() * LINES_PER_PANEL / textScale);
        int shown = Math.min(lines.size(), room);
        int totalHeight = shown * LINE_HEIGHT;
        int top = -totalHeight / 2;

        Matrix4f matrix = poses.last().pose();
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            int width = font.width(line);
            font.drawInBatch(line, -width / 2.0F, top + i * LINE_HEIGHT, 0xFFFFFF, false,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        }
        poses.popPose();
    }

    /**
     * The text of a wall reaches beyond its own panel.
     *
     * <p>Without this declaration it disappears as soon as the bottom-left
     * block slides out of view — and with a wall you are standing in front
     * of, that is almost always the case.
     */
    @Override
    public AABB getRenderBoundingBox(DisplayBlockEntity display) {
        DisplayWall wall = display.wall();
        if (wall.isSingle()) {
            return new AABB(display.getBlockPos());
        }
        AABB box = new AABB(display.getBlockPos());
        for (net.minecraft.core.BlockPos member : wall.members()) {
            box = box.minmax(new AABB(member));
        }
        return box;
    }

    /**
     * Do not draw from afar — the text would be illegible anyway.
     *
     * <p><b>The limit grows with the scale.</b> Sixteen blocks was the
     * distance at which text at normal size still gives something; text four
     * times as large gives something at four times the distance. Without
     * that, {@code scale} would be a control that enlarges the text and yet
     * makes it disappear exactly where you wanted to read it.
     */
    @Override
    public boolean shouldRender(DisplayBlockEntity display, Vec3 camera) {
        double reach = MAX_DISTANCE * display.textScale();
        return camera.closerThan(Vec3.atCenterOf(display.getBlockPos()), reach)
                && display.getBlockPos().distToCenterSqr(camera) < reach * reach;
    }
}
