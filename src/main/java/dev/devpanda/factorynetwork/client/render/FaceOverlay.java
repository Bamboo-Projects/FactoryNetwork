package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Legt kleine Bilder auf die Vorderseite eines Blocks.
 *
 * <p><b>Warum gezeichnet und nicht gebacken:</b> Ein Laufwerk mit zehn
 * Plätzen und sechs Zuständen je Platz wären über sechzig Millionen
 * Blockzustände. Was drinsteckt, gehört in die BlockEntity, und die Front
 * wird darüber gemalt.
 *
 * <p>Gerechnet wird in Texturkoordinaten der Vorderseite: null links oben,
 * eins rechts unten — dieselben Zahlen, mit denen die Textur entstanden ist.
 * So steht die Geometrie im Renderer an derselben Stelle wie im Malskript,
 * und ein verschobener Schacht fällt beim Vergleich auf.
 */
public final class FaceOverlay {

    /** Knapp vor der Fläche, damit nichts flimmert. */
    private static final float OFFSET = 0.5F + 0.004F;

    private FaceOverlay() {
    }

    /**
     * Ein Rechteck auf die Vorderseite.
     *
     * @param facing wohin der Block zeigt
     * @param tx0 linke Kante in Texturkoordinaten, null bis eins
     * @param ty0 obere Kante
     * @param tx1 rechte Kante
     * @param ty1 untere Kante
     * @param u0 linke Kante im Bildstreifen
     * @param u1 rechte Kante im Bildstreifen
     */
    public static void tile(VertexConsumer buffer, Matrix4f matrix, Direction facing,
                            float tx0, float ty0, float tx1, float ty1,
                            float u0, float u1, int light) {
        float nx = facing.getStepX();
        float nz = facing.getStepZ();
        // Von aussen betrachtet zeigt die Textur nach rechts, wohin die
        // Gegenuhrzeigerdrehung der Blickrichtung zeigt. Bei Norden ist das
        // Westen — wer im Spiel nach Norden sieht, hat Osten rechts.
        Direction right = facing.getCounterClockWise();
        float rx = right.getStepX();
        float rz = right.getStepZ();

        corner(buffer, matrix, nx, nz, rx, rz, tx0, ty0, u0, 0.0F, light);
        corner(buffer, matrix, nx, nz, rx, rz, tx0, ty1, u0, 1.0F, light);
        corner(buffer, matrix, nx, nz, rx, rz, tx1, ty1, u1, 1.0F, light);
        corner(buffer, matrix, nx, nz, rx, rz, tx1, ty0, u1, 0.0F, light);
    }

    private static void corner(VertexConsumer buffer, Matrix4f matrix,
                               float nx, float nz, float rx, float rz,
                               float tx, float ty, float u, float v, int light) {
        float alongRight = tx - 0.5F;
        float x = nx * OFFSET + rx * alongRight;
        float z = nz * OFFSET + rz * alongRight;
        float y = 0.5F - ty;
        buffer.addVertex(matrix, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, 0.0F, nz);
    }
}
