package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Zeichnet die Anschlüsse, die an den Flächen eines Kabels sitzen.
 *
 * <p><b>Warum gezeichnet und nicht gebacken:</b> Welche Flächen ein Teil
 * tragen, steht in der BlockEntity und nicht im Blockzustand. Es in den
 * Zustand zu nehmen hieße sechs weitere Wahrheitswerte — mal den vorhandenen
 * sechs Verbindungen, mal siebzehn Farben: fast siebzigtausend Zustände je
 * Kabelart, die Minecraft alle beim Start anlegt.
 *
 * <p><b>Der Preis:</b> Mit einem angemeldeten Renderer landet <b>jede</b>
 * Kabel-BlockEntity in der Zeichenliste, auch die ohne Teile — und das sind
 * fast alle. Deshalb steht der Rücksprung in der ersten Zeile. Was das bei
 * zehntausend Kabeln kostet, ist ungemessen und steht als offener Punkt; ein
 * Wechsel auf ein gebackenes Modell bliebe jederzeit möglich, weil hier
 * nichts gespeichert wird.
 */
public class CableBusRenderer implements BlockEntityRenderer<CableBusBlockEntity> {

    private final BlockRenderDispatcher blocks;

    public CableBusRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CableBusBlockEntity bus, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!bus.hasParts()) {
            return;
        }
        BlockState cable = bus.getBlockState();
        int size = CableBlock.sizeOf(cable);
        var models = Minecraft.getInstance().getModelManager();
        // Dieselbe Zeichenart wie das Kabel: Die Ränder der Textur sind
        // durchsichtig.
        var buffer = buffers.getBuffer(RenderType.cutout());
        for (var entry : bus.parts().entrySet()) {
            // Die Farbe trifft nur den Lämpchenring: renderModel färbt allein
            // Flächen mit tintindex, und den hat im Teilmodell nur er.
            var state = entry.getValue().state();
            blocks.getModelRenderer().renderModel(poses.last(), buffer, cable,
                    models.getModel(ConnectorPartModels.of(size, entry.getKey())),
                    DeviceStateColours.red(state), DeviceStateColours.green(state),
                    DeviceStateColours.blue(state),
                    light, overlay, ModelData.EMPTY, RenderType.cutout());
        }
    }
}
