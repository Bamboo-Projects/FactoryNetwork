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
 * Zeichnet die Zeilen eines Displays auf seine Vorderseite.
 *
 * <p>Text in der Welt, nicht in einer Oberfläche — man soll im Vorbeigehen
 * sehen, wie es der Fabrik geht, ohne etwas anzuklicken. Das ist der ganze
 * Zweck des Blocks; ein Display, für das man erst ein Fenster öffnet, wäre
 * ein umständliches Terminal.
 *
 * <p><b>Eine Wand aus Tafeln ist ein Bildschirm.</b> Geschrieben wird von der
 * Tafel unten links, und zwar über die ganze Fläche. Die Schrift bleibt dabei
 * gleich groß: Der Platz einer großen Wand geht in mehr Zeilen und längere,
 * nicht in größere Buchstaben — eine Wand, deren Text mit ihr wächst, ist aus
 * drei Metern genauso lesbar wie eine einzelne Tafel und verschenkt den
 * ganzen Vorteil.
 *
 * <p>Ab einer gewissen Entfernung wird nichts mehr gezeichnet: Zwanzig
 * Displays an einer Wand, jedes mit zehn Zeilen, sind zweihundert Textblöcke
 * je Bild.
 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity> {

    /** Weiter als das ist die Schrift ohnehin nicht zu lesen. */
    private static final double MAX_DISTANCE = 16.0;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

    private static final float SCALE = 0.006F;
    private static final int LINE_HEIGHT = 9;

    /** So viele Zeilen trägt eine Tafel — eine Wand entsprechend mehr. */
    private static final int LINES_PER_PANEL = 12;

    /** Wie viele Schrifteinheiten ein Block hoch und breit ist. */
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
        poses.pushPose();

        // In die Mitte der Vorderseite, dann in die Blickrichtung drehen.
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.translate(0, 0, -0.47);
        poses.scale(-SCALE, -SCALE, SCALE);

        // Von der eigenen Tafel in die Mitte der Wand. Der Maßstab steht
        // schon, also wird hier in Schrifteinheiten gerechnet — und die
        // x-Achse ist gespiegelt, deshalb das Minus.
        float toCentreX = -((wall.columns() - 1) / 2.0F - wall.anchorColumn());
        float toCentreY = -((wall.rows() - 1) / 2.0F - wall.anchorRow());
        poses.translate(toCentreX * UNITS_PER_BLOCK, toCentreY * UNITS_PER_BLOCK, 0.0F);

        int room = wall.rows() * LINES_PER_PANEL;
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
     * Der Text einer Wand reicht über die eigene Tafel hinaus.
     *
     * <p>Ohne diese Angabe verschwindet er, sobald der Block unten links aus
     * dem Blickfeld rutscht — und das ist bei einer Wand, vor der man steht,
     * fast immer der Fall.
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

    /** Aus der Ferne nicht zeichnen — die Schrift wäre ohnehin unlesbar. */
    @Override
    public boolean shouldRender(DisplayBlockEntity display, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(display.getBlockPos()), MAX_DISTANCE)
                && display.getBlockPos().distToCenterSqr(camera) < MAX_DISTANCE_SQUARED;
    }
}
