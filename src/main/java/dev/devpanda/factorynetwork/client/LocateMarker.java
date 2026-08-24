package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Zeigt eine Stelle im Netz in der Welt an.
 *
 * <p><b>Der Griff, der aus einem Texteditor eine Fabrik-Oberfläche macht.</b>
 * Im Code steht {@code crusher_1}; in einer Fabrik mit vierzig Öfen ist damit
 * noch nicht gesagt, welcher das ist. Strg und Klick auf den Namen setzt eine
 * Marke, und die steht auch noch da, wenn das Terminal längst zu ist.
 *
 * <p>Durch Wände sichtbar: Wer sucht, weiß nicht, hinter welcher sie liegt.
 * Und mit einer Frist, damit sie nicht bis zum Ausloggen im Bild steht — zwei
 * Minuten reichen quer durch eine Basis.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class LocateMarker {

    /** So lange bleibt eine Marke stehen, in Ticks. */
    private static final int LIFETIME = 20 * 120;

    /** So nah muss man heran, damit sie von selbst verschwindet. */
    private static final double ARRIVED = 3.0;

    /**
     * So lange steht sie auf jeden Fall, in Ticks.
     *
     * <p><b>Sonst antwortet sie gerade dann nicht, wenn man fragt.</b> Wer
     * vor vier Connectoren steht und wissen will, welcher {@code kiste_2}
     * ist, steht näher als {@link #ARRIVED} — die Marke galt damit als
     * erreicht und verschwand im selben Bild, in dem sie gesetzt wurde. Das
     * sah aus wie ein kaputter Griff und war eine Annahme über den Abstand,
     * aus dem jemand sucht.
     */
    private static final int MINIMUM = 20 * 30;

    private static final int COLOUR = 0xFF78DC8C;
    private static final int BACKGROUND = 0x88000000;

    private static BlockPos target;
    private static String label = "";
    private static long until;

    /** Wann sie gesetzt wurde — für {@link #MINIMUM}. */
    private static long since;

    private LocateMarker() {
    }

    /** Setzt die Marke. Eine vorherige wird dabei abgelöst. */
    public static void mark(BlockPos pos, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        target = pos;
        label = name;
        since = minecraft.level.getGameTime();
        until = since + LIFETIME;
    }

    public static void clear() {
        target = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || target == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // Abgelaufen oder angekommen — beides heißt, die Marke hat ihren
        // Zweck erfüllt. „Angekommen" gilt aber erst nach MINIMUM: Wer schon
        // davorsteht, während er fragt, soll trotzdem eine Antwort bekommen.
        long now = minecraft.level.getGameTime();
        boolean arrived = now - since >= MINIMUM
                && minecraft.player.position().distanceTo(target.getCenter()) < ARRIVED;
        if (now > until || arrived) {
            target = null;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        // Der Kasten um den Block. Ein Hauch größer als der Block selbst,
        // sonst liegt die Linie in seiner Fläche und flackert.
        //
        // <b>Von Hand und nicht über RenderType.lines():</b> Der bringt
        // seinen Tiefentest mit, und dann liegt die Marke hinter der ersten
        // Wand — genau da, wo man sie sucht. Derselbe Weg wie beim
        // Analysator, der das Netz aus demselben Grund durch Wände zeigt.
        poses.pushPose();
        poses.translate(-eye.x, -eye.y, -eye.z);
        Matrix4f box = poses.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(3.0F);

        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR);
        outline(buffer, box, target);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poses.popPose();

        // Und der Name darüber, damit man ihn schon von weitem liest.
        Font font = minecraft.font;
        poses.pushPose();
        poses.translate(
                target.getX() + 0.5 - eye.x,
                target.getY() + 1.4 - eye.y,
                target.getZ() + 0.5 - eye.z);
        poses.mulPose(camera.rotation());
        poses.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poses.last().pose();
        font.drawInBatch(label, -font.width(label) / 2.0F, 0, COLOUR, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
        poses.popPose();
        buffers.endBatch();
    }

    /**
     * Die zwölf Kanten eines Blocks.
     *
     * <p>Ein Hauch größer als der Block, sonst liegt die Linie in seiner
     * Fläche und flackert.
     */
    private static void outline(com.mojang.blaze3d.vertex.BufferBuilder buffer,
                                Matrix4f matrix, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.02);
        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;

        // Boden
        line(buffer, matrix, x0, y0, z0, x1, y0, z0);
        line(buffer, matrix, x1, y0, z0, x1, y0, z1);
        line(buffer, matrix, x1, y0, z1, x0, y0, z1);
        line(buffer, matrix, x0, y0, z1, x0, y0, z0);
        // Decke
        line(buffer, matrix, x0, y1, z0, x1, y1, z0);
        line(buffer, matrix, x1, y1, z0, x1, y1, z1);
        line(buffer, matrix, x1, y1, z1, x0, y1, z1);
        line(buffer, matrix, x0, y1, z1, x0, y1, z0);
        // Pfosten
        line(buffer, matrix, x0, y0, z0, x0, y1, z0);
        line(buffer, matrix, x1, y0, z0, x1, y1, z0);
        line(buffer, matrix, x1, y0, z1, x1, y1, z1);
        line(buffer, matrix, x0, y0, z1, x0, y1, z1);
    }

    private static void line(com.mojang.blaze3d.vertex.BufferBuilder buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(0.47F, 0.86F, 0.55F, 1.0F);
        buffer.addVertex(matrix, x1, y1, z1).setColor(0.47F, 0.86F, 0.55F, 1.0F);
    }
}
