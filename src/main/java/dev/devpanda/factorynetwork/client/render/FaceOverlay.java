package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Places small images onto the front face of a block.
 *
 * <p><b>Why drawn and not baked:</b> a drive with ten slots and six states
 * per slot would be over sixty million block states. What is inside belongs
 * in the BlockEntity, and the front is painted over it.
 *
 * <p>The computation is in texture coordinates of the front face: zero at the
 * top left, one at the bottom right — the same numbers the texture was made
 * with. That way the geometry in the renderer sits in the same place as in
 * the drawing script, and a shifted bay stands out on comparison.
 */
public final class FaceOverlay {

    /** Just in front of the face, so that nothing flickers. */
    private static final float OFFSET = 0.5F + 0.004F;

    private FaceOverlay() {
    }

    /**
     * A rectangle onto the front face.
     *
     * @param facing which way the block points
     * @param tx0 left edge in texture coordinates, zero to one
     * @param ty0 top edge
     * @param tx1 right edge
     * @param ty1 bottom edge
     * @param u0 left edge in the image strip
     * @param u1 right edge in the image strip
     */
    public static void tile(VertexConsumer buffer, Matrix4f matrix, Direction facing,
                            float tx0, float ty0, float tx1, float ty1,
                            float u0, float u1, int light) {
        tile(buffer, matrix, facing, tx0, ty0, tx1, ty1, u0, u1, light, 0.0F);
    }

    /**
     * The same, but set a little deeper.
     *
     * <p>The server rack has a recessed front: the bays lie behind the frame,
     * not on it. Without this offset they would sit inside the sheet metal.
     *
     * @param depth how far behind the outer face, in blocks
     */
    public static void tile(VertexConsumer buffer, Matrix4f matrix, Direction facing,
                            float tx0, float ty0, float tx1, float ty1,
                            float u0, float u1, int light, float depth) {
        float nx = facing.getStepX();
        float nz = facing.getStepZ();
        // Seen from outside, the texture points to the right, where the
        // counter-clockwise turn of the facing direction points. For north
        // that is west — someone looking north in the game has east on the
        // right.
        Direction right = facing.getCounterClockWise();
        float rx = right.getStepX();
        float rz = right.getStepZ();

        corner(buffer, matrix, nx, nz, rx, rz, tx0, ty0, u0, 0.0F, light, depth);
        corner(buffer, matrix, nx, nz, rx, rz, tx0, ty1, u0, 1.0F, light, depth);
        corner(buffer, matrix, nx, nz, rx, rz, tx1, ty1, u1, 1.0F, light, depth);
        corner(buffer, matrix, nx, nz, rx, rz, tx1, ty0, u1, 0.0F, light, depth);
    }

    private static void corner(VertexConsumer buffer, Matrix4f matrix,
                               float nx, float nz, float rx, float rz,
                               float tx, float ty, float u, float v, int light,
                               float depth) {
        float alongRight = tx - 0.5F;
        float front = OFFSET - depth;
        float x = nx * front + rx * alongRight;
        float z = nz * front + rz * alongRight;
        float y = 0.5F - ty;
        buffer.addVertex(matrix, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, 0.0F, nz);
    }
}
