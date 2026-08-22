package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.item.ProcessorItem;
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
 * Zeigt an der Front, welche Einschübe bestückt sind.
 *
 * <p>Dasselbe wie beim Laufwerk und aus demselben Grund: Ein Schrank, dem man
 * nicht ansieht, ob noch Platz ist, zwingt zum Anklicken.
 */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity> {

    private static final ResourceLocation BLADES = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/misc/rack_blades.png");

    /** Leer, Prozessor, Co-Prozessor. */
    private static final float TILES = 3.0F;
    private static final double MAX_DISTANCE = 24.0;

    /** Die Einschübe liegen so, wie sie {@code textures.py} malt. */
    private static final float TEXTURE = 64.0F;
    private static final int BLADE_X = 8;
    private static final int BLADE_Y = 11;
    private static final int BLADE_W = 4;
    private static final int BLADE_H = 36;
    private static final int STEP = 6;

    public RackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RackBlockEntity rack, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        Direction facing = rack.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(BLADES));
        int dark = LevelRenderer.getLightColor(rack.getLevel(),
                rack.getBlockPos().relative(facing));

        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        Matrix4f matrix = poses.last().pose();
        for (int slot = 0; slot < RackBlockEntity.SLOTS; slot++) {
            int kind = kindOf(rack.processor(slot));
            int x = BLADE_X + slot * STEP;
            FaceOverlay.tile(buffer, matrix, facing,
                    x / TEXTURE, BLADE_Y / TEXTURE,
                    (x + BLADE_W) / TEXTURE, (BLADE_Y + BLADE_H) / TEXTURE,
                    kind / TILES, (kind + 1) / TILES,
                    kind == 0 ? dark : LightTexture.FULL_BRIGHT);
        }
        poses.popPose();
    }

    /**
     * Leer, klein oder groß.
     *
     * <p>Unterschieden wird an der Leistung und nicht am Gegenstand: Kommt
     * einmal ein dritter Prozessor dazu, reiht er sich von selbst ein.
     */
    private static int kindOf(ItemStack stack) {
        int threads = ProcessorItem.threadsOf(stack);
        if (threads <= 0) {
            return 0;
        }
        return threads >= 8 ? 2 : 1;
    }

    @Override
    public boolean shouldRender(RackBlockEntity rack, Vec3 camera) {
        return camera.closerThan(Vec3.atCenterOf(rack.getBlockPos()), MAX_DISTANCE);
    }
}
