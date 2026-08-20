package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
    private static final int MAX_LINES = 12;

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
        poses.pushPose();

        // In die Mitte der Vorderseite, dann in die Blickrichtung drehen.
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.translate(0, 0, -0.47);
        poses.scale(-SCALE, -SCALE, SCALE);

        int shown = Math.min(lines.size(), MAX_LINES);
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

    /** Aus der Ferne nicht zeichnen — die Schrift wäre ohnehin unlesbar. */
    @Override
    public boolean shouldRender(DisplayBlockEntity display, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(display.getBlockPos()), MAX_DISTANCE)
                && display.getBlockPos().distToCenterSqr(camera) < MAX_DISTANCE_SQUARED;
    }
}
