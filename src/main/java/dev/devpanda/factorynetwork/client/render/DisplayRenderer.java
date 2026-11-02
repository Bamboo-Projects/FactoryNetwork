package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.block.DisplayWall;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayLine;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p><b>Items are drawn the way the campfire draws its food</b>: the model is
 * resolved while the frame is extracted and submitted together with the
 * text. They use the inventory view ({@link ItemDisplayContext#GUI}), so that
 * a block looks the way it does in the terminal and not like a block hanging
 * on the wall.
 *
 * <p>Beyond a certain distance nothing is drawn any more: twenty displays on
 * a wall, each with ten lines, are two hundred blocks of text per frame.
 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity, DisplayRenderer.State> {

    /** Beyond that the text cannot be read anyway. */
    private static final double MAX_DISTANCE = 16.0;

    private static final float SCALE = 0.006F;
    private static final int LINE_HEIGHT = 9;

    /** This many lines a panel carries — a wall correspondingly more. */
    private static final int LINES_PER_PANEL = 12;

    /** How many text units a block is tall and wide. */
    private static final float UNITS_PER_BLOCK = 1.0F / SCALE;

    /** The icon in front of a line: one unit smaller than the line, so lines do not touch. */
    private static final float ICON_SIZE = 8.0F;
    /** Icon plus the gap to the text after it. */
    private static final float ICON_ADVANCE = ICON_SIZE + 2.0F;

    /** A grid cell: a 16-unit icon and its margin — the terminal's slot. */
    private static final float CELL = 18.0F;
    private static final float CELL_ICON = 16.0F;

    /**
     * The amount under a grid icon, at this fraction of the font.
     *
     * <p>Smaller than the terminal's, which can afford full size because it
     * draws a shadow. "1,5M" at full size is wider than a cell and runs into
     * the neighbour.
     */
    private static final float AMOUNT_SCALE = 0.6F;

    /** Space kept free left and right of a grid, so it does not touch the frame. */
    private static final float SIDE_MARGIN = 8.0F;

    /**
     * How flat an item is drawn.
     *
     * <p>A block in the inventory view is a cube; at full depth it would
     * reach through the face of the panel behind it. A quarter of its depth
     * still reads as a block from the front.
     */
    private static final float ITEM_DEPTH = 0.25F;

    /**
     * Resolved item models, per item.
     *
     * <p>A full grid is a few hundred icons, and resolving each one every
     * frame is the work the distance limit exists to avoid. An icon on a
     * display is the plain item without data, so its model does not change —
     * except for the few animated ones such as the compass, which stand
     * still here. The renderer is rebuilt on a resource reload, and the
     * cache with it.
     */
    private final Map<Item, ItemStackRenderState> models = new HashMap<>();
    private static final int MODEL_CACHE_LIMIT = 1024;

    private final Font font;
    private final ItemModelResolver itemModels;

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
        this.itemModels = context.itemModelResolver();
    }

    /** What one frame needs from the display; see DriveRenderer.State. */
    public static final class State extends BlockEntityRenderState {
        List<DisplayLine> lines = List.of();
        Direction facing = Direction.NORTH;
        DisplayWall wall;
        int textScale = 1;
        /** The model of each line's icon, {@code null} where a line has none. */
        List<ItemStackRenderState> icons = List.of();
        /** The models of each grid's cells that fit; empty for other lines. */
        List<List<ItemStackRenderState>> cells = List.of();
        Layout layout;
    }

    /**
     * Where everything goes, in text units from the centre of the wall.
     *
     * @param shown    how many lines are drawn at all
     * @param top      where the first one starts
     * @param columns  grid cells per row
     * @param gridRows rows each grid may take — the room left after the text,
     *                 shared between the grids
     */
    record Layout(int shown, float top, int columns, int gridRows) {

        /** How many cells a grid shows. */
        int capacity() {
            return columns * gridRows;
        }

        /** How high a line stands. */
        float height(DisplayLine line) {
            if (!line.isGrid()) {
                return LINE_HEIGHT;
            }
            if (capacity() == 0) {
                return 0;
            }
            int used = Math.min(line.grid().size(), capacity());
            return (float) Math.ceil(used / (double) columns) * CELL;
        }

        /**
         * The layout for these lines on this wall.
         *
         * <p><b>Without a grid exactly as before:</b> as many lines as fit,
         * centred. <b>With a grid</b> the text lines keep their height and the
         * grids share what is left — a grid is the one thing on a display that
         * grows with the wall, because a bigger wall is the reason to want one.
         * Lines after a grid stand after it.
         */
        static Layout of(List<DisplayLine> lines, DisplayWall wall, int textScale) {
            int room = Math.max(1, wall.rows() * LINES_PER_PANEL / textScale);
            float width = wall.columns() * UNITS_PER_BLOCK / textScale - 2 * SIDE_MARGIN;
            int columns = Math.max(1, (int) (width / CELL));
            int grids = 0;
            int text = 0;
            for (DisplayLine line : lines) {
                if (line.isGrid()) {
                    grids++;
                } else {
                    text++;
                }
            }
            if (grids == 0) {
                int shown = Math.min(lines.size(), room);
                return new Layout(shown, -(shown * LINE_HEIGHT) / 2, columns, 0);
            }
            int textShown = Math.min(text, room);
            float left = (room - textShown) * LINE_HEIGHT;
            int gridRows = Math.max(0, (int) (left / grids / CELL));
            Layout draft = new Layout(lines.size(), 0, columns, gridRows);
            // Text lines beyond the room fall off the end, the same as
            // without a grid; what fits is centred.
            float total = 0;
            int shown = 0;
            int textSoFar = 0;
            for (DisplayLine line : lines) {
                if (!line.isGrid() && ++textSoFar > room) {
                    break;
                }
                total += draft.height(line);
                shown++;
            }
            return new Layout(shown, -total / 2.0F, columns, gridRows);
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DisplayBlockEntity display, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTick, camera, breakProgress);
        state.lines = display.shownLines();
        state.facing = display.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        state.wall = display.wall();
        state.textScale = display.textScale();
        if (state.wall == null || state.lines.isEmpty()) {
            state.layout = null;
            return;
        }
        state.layout = Layout.of(state.lines, state.wall, state.textScale);

        // Only what is drawn gets a model: a grid of two hundred items on a
        // single panel resolves the ones that fit.
        Level level = display.getLevel();
        List<ItemStackRenderState> icons = new ArrayList<>(state.layout.shown());
        List<List<ItemStackRenderState>> cells = new ArrayList<>(state.layout.shown());
        for (int i = 0; i < state.layout.shown(); i++) {
            DisplayLine line = state.lines.get(i);
            icons.add(line.icon() == null ? null : model(line.icon(), level));
            if (!line.isGrid()) {
                cells.add(List.of());
                continue;
            }
            int fit = Math.min(line.grid().size(), state.layout.capacity());
            List<ItemStackRenderState> found = new ArrayList<>(fit);
            for (int c = 0; c < fit; c++) {
                found.add(model(line.grid().get(c).item(), level));
            }
            cells.add(found);
        }
        state.icons = icons;
        state.cells = cells;
    }

    private ItemStackRenderState model(Item item, Level level) {
        ItemStackRenderState known = models.get(item);
        if (known != null) {
            return known;
        }
        if (models.size() >= MODEL_CACHE_LIMIT) {
            models.clear();
        }
        ItemStackRenderState resolved = new ItemStackRenderState();
        itemModels.updateForTopItem(resolved, new ItemStack(item), ItemDisplayContext.GUI,
                level, null, 0);
        models.put(item, resolved);
        return resolved;
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        List<DisplayLine> lines = state.lines;
        Layout layout = state.layout;
        if (lines.isEmpty() || state.wall == null || layout == null) {
            return;
        }

        Direction facing = state.facing;
        DisplayWall wall = state.wall;
        int textScale = state.textScale;
        int light = state.lightCoords;
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
        float y = layout.top();
        for (int i = 0; i < layout.shown(); i++) {
            DisplayLine line = lines.get(i);
            if (line.isGrid()) {
                drawGrid(line, state.cells.get(i), layout, y, poses, collector, light);
            } else {
                drawLine(line, state.icons.get(i), y, poses, collector, light);
            }
            y += layout.height(line);
        }
        poses.popPose();
    }

    /** A line of text, with its icon in front if it has one; centred as a whole. */
    private void drawLine(DisplayLine line, ItemStackRenderState icon, float y, PoseStack poses,
                          SubmitNodeCollector collector, int light) {
        float width = font.width(line.text());
        float iconWidth = icon == null ? 0 : ICON_ADVANCE;
        float left = -(width + iconWidth) / 2.0F;
        if (icon != null) {
            drawItem(icon, left + ICON_SIZE / 2.0F, y + (LINE_HEIGHT - 1) / 2.0F, ICON_SIZE,
                    poses, collector, light);
        }
        // Opaque white: since 1.21.6 a text colour without alpha is invisible.
        collector.submitText(poses, left + iconWidth, y,
                net.minecraft.network.chat.Component.literal(line.text()).getVisualOrderText(),
                false, Font.DisplayMode.NORMAL, light, 0xFFFFFFFF, 0, 0);
    }

    /**
     * A grid of icons with their amounts, the way the terminal shows storage.
     *
     * <p>If there are more items than cells, the last cell says how many are
     * missing instead of showing one more — a grid that ends silently reads
     * like the whole store, the same reason {@code list} counts what it cuts.
     */
    private void drawGrid(DisplayLine line, List<ItemStackRenderState> found, Layout layout,
                          float top, PoseStack poses, SubmitNodeCollector collector, int light) {
        if (found.isEmpty()) {
            return;
        }
        int columns = Math.min(layout.columns(), found.size());
        float left = -columns * CELL / 2.0F;
        boolean cut = line.grid().size() > found.size();
        for (int c = 0; c < found.size(); c++) {
            float cellX = left + (c % layout.columns()) * CELL;
            float cellY = top + (c / layout.columns()) * CELL;
            if (cut && c == found.size() - 1) {
                String more = "+" + (line.grid().size() - c);
                collector.submitText(poses, cellX + (CELL - font.width(more)) / 2.0F,
                        cellY + (CELL - LINE_HEIGHT) / 2.0F,
                        net.minecraft.network.chat.Component.literal(more).getVisualOrderText(),
                        false, Font.DisplayMode.NORMAL, light, 0xFFAAAAAA, 0, 0);
                continue;
            }
            drawItem(found.get(c), cellX + CELL / 2.0F, cellY + CELL / 2.0F, CELL_ICON,
                    poses, collector, light);
            String amount = ClientStorageView.shortAmount(line.grid().get(c).amount());
            poses.pushPose();
            // Bottom right, like the terminal; in front of the icon so it lies
            // on top of it and not inside it.
            poses.translate(cellX + CELL - 1, cellY + CELL - 1,
                    -(CELL_ICON * ITEM_DEPTH + 0.5F));
            poses.scale(AMOUNT_SCALE, AMOUNT_SCALE, 1.0F);
            collector.submitText(poses, -font.width(amount), -LINE_HEIGHT + 1,
                    net.minecraft.network.chat.Component.literal(amount).getVisualOrderText(),
                    false, Font.DisplayMode.NORMAL, light, 0xFFFFFFFF, 0, 0);
            poses.popPose();
        }
    }

    /**
     * An item model centred on a point, {@code size} text units tall.
     *
     * <p>In text space x runs right, y runs down and z away from the viewer;
     * a model has y up and its front toward +z. Flipping y and z turns the
     * one into the other — and, unlike flipping y alone, it is a rotation and
     * not a mirror, so the faces keep their winding and are not culled.
     */
    private void drawItem(ItemStackRenderState model, float x, float y, float size,
                          PoseStack poses, SubmitNodeCollector collector, int light) {
        if (model.isEmpty()) {
            return;
        }
        poses.pushPose();
        poses.translate(x, y, -size * ITEM_DEPTH / 2.0F);
        poses.scale(size, -size, -size * ITEM_DEPTH);
        model.submit(poses, collector, light, OverlayTexture.NO_OVERLAY, 0);
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
