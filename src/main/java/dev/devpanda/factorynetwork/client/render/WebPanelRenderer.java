package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.devpanda.factorynetwork.block.entity.WebPanelBlockEntity;
import dev.devpanda.factorynetwork.client.panel.WebPanels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Zeichnet die Seite einer Web-Fläche auf ihre Vorderseite.
 *
 * <p><b>Der Umweg über eine {@code ResourceLocation} ist keiner.</b> Im
 * Bildschirm lässt sich Chromiums Textur unmittelbar binden; in der Welt läuft
 * alles über gepufferte Puffer und {@code RenderType}, und ein Zeichnen
 * dazwischen kollidierte mit dem, was noch nicht abgeschickt ist. Deshalb
 * meldet {@code WebPanels} jede Sitzung beim Texturverwalter an, und hier
 * steht nur ein Viereck.
 *
 * <p><b>Die Fläche leuchtet.</b> Ein Bildschirm im Dunkeln, der schwarz wird,
 * ist ein kaputter Bildschirm — also volles Licht, wie bei jeder Anzeige.
 *
 * <p><b>Die Entfernung entscheidet den Takt.</b> Nicht hier, sondern in
 * {@code WebPanels}; gemeldet wird sie von hier, weil der Renderer sie ohnehin
 * kennt und weil er nur läuft, wenn die Fläche im Bild ist.
 */
public class WebPanelRenderer implements BlockEntityRenderer<WebPanelBlockEntity> {

    /** Weiter weg ist eine Seite ohnehin nicht zu lesen. */
    private static final double MAX_DISTANCE = 32.0;

    /** Volles Licht, Blockanteil und Himmelsanteil. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Wo die Vorderseite der Tafel liegt, von ihrer Mitte aus.
     *
     * <p>Die Tafel ist zwei Pixel dick, also endet sie sechs Sechzehntel vor
     * der Blockmitte und nicht acht. Ein halber Block wäre zwei Pixel zu weit
     * — die Seite schwebte vor der Tafel, wäre perspektivisch größer als ihr
     * Block und ragte über die Nachbarn.
     */
    private static final float FRONT = 0.5F - 2.0F / 16.0F;

    /**
     * Wie weit die Fläche vor der Blockfläche liegt.
     *
     * <p>Ohne diesen Abstand streiten sich zwei Flächen auf demselben Tiefen-
     * wert, und die Seite flimmert je nach Blickwinkel.
     */
    private static final float EPSILON = 0.001F;

    public WebPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WebPanelBlockEntity panel, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (panel.getLevel() == null) {
            return;
        }
        Vec3 camera = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(panel.getBlockPos().distToCenterSqr(camera));
        if (distance > MAX_DISTANCE) {
            return;
        }

        ResourceLocation texture =
                WebPanels.textureFor(panel.getBlockPos(), panel.url(), distance);
        if (texture == null) {
            return;
        }

        Direction facing = panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        pose.pushPose();
        // In die Mitte des Blocks, dann in die Blickrichtung der Tafel drehen.
        //
        // <b>Nach der Drehung zeigt lokales +Z zum Betrachter</b>, nicht von
        // ihm weg — die Drehung dreht ja gerade die Vorderseite zu ihm hin.
        // Ein Versatz nach -Z schöbe die Seite in den Block hinein, und zu
        // sehen wäre die Modelltextur.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, FRONT + EPSILON);

        Matrix4f matrix = pose.last().pose();
        VertexConsumer quad = buffers.getBuffer(RenderType.text(texture));
        // Gegen den Uhrzeigersinn ab unten links, und v läuft mit der Kante
        // der Seite: oben null, unten eins. Genau wie im Bildschirm — wer das
        // dreht, bekommt die Seite auf dem Kopf.
        vertex(quad, matrix, -0.5F, -0.5F, 0f, 1f);
        vertex(quad, matrix, 0.5F, -0.5F, 1f, 1f);
        vertex(quad, matrix, 0.5F, 0.5F, 1f, 0f);
        vertex(quad, matrix, -0.5F, 0.5F, 0f, 0f);
        pose.popPose();
    }

    private static void vertex(VertexConsumer quad, Matrix4f matrix,
                               float x, float y, float u, float v) {
        quad.addVertex(matrix, x, y, 0f)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setLight(FULL_BRIGHT);
    }

    /**
     * Auch dann zeichnen, wenn der Block selbst außer Sicht ist.
     *
     * <p>Eine Tafel ist zwei Pixel dick; ihr Begrenzungskörper verschwindet
     * schon, wenn die Seite noch gut zu sehen wäre.
     */
    @Override
    public boolean shouldRenderOffScreen(WebPanelBlockEntity panel) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return (int) MAX_DISTANCE;
    }
}
