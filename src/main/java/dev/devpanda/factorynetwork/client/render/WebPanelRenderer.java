package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.block.entity.WebPanelBlockEntity;
import dev.devpanda.factorynetwork.client.panel.WebPanels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Der Renderer der Tafel — der nichts mehr zeichnet.
 *
 * <p><b>Er ist zum Melder geworden.</b> Seit die Fläche über die API kommt
 * ({@link dev.devpanda.factorynetwork.web.api.FnWeb#openInWorld}), zeichnet
 * die {@link dev.devpanda.factorynetwork.web.api.WorldSurface} das Viereck
 * aus der Weltstufe. Was hier bleibt, ist die eine Auskunft, die nur der
 * Renderer eines Blocks hat: dass diese Tafel im Bild ist und wie sie steht.
 *
 * <p>Genau das ist der Grund, warum diese Klasse überhaupt bleibt: Der
 * Renderer läuft nur für Blöcke in Sicht- und Bildschirmreichweite, und aus
 * seinem Ausbleiben schließt {@code WebPanels}, dass niemand hinsieht.
 */
public class WebPanelRenderer implements BlockEntityRenderer<WebPanelBlockEntity> {

    /** Weiter weg ist eine Seite ohnehin nicht zu lesen. */
    private static final double MAX_DISTANCE = 32.0;

    public WebPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WebPanelBlockEntity panel, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack pose,
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
        Direction facing = panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        WebPanels.seen(panel.getBlockPos(), panel.url(), panel.name(), facing);
    }

    @Override
    public boolean shouldRenderOffScreen(WebPanelBlockEntity panel) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return (int) MAX_DISTANCE;
    }
}
