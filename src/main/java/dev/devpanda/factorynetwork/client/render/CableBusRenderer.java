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
 * Draws the connectors that sit on the faces of a cable.
 *
 * <p><b>Why drawn and not baked:</b> which faces carry a part is held in the
 * BlockEntity, not in the block state. Taking it into the state would mean
 * six more booleans — times the existing six connections, times seventeen
 * colours: almost seventy thousand states per cable type, all of which
 * Minecraft creates at startup.
 *
 * <p><b>The price:</b> with a registered renderer, <b>every</b> cable
 * BlockEntity ends up in the draw list, even those without parts — and that
 * is almost all of them. That is why the early return is in the first line.
 * What that costs with ten thousand cables is unmeasured and stands as an
 * open item; a switch to a baked model would remain possible at any time,
 * because nothing is stored here.
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
        // The same render type as the cable: the edges of the texture are
        // transparent.
        var buffer = buffers.getBuffer(RenderType.cutout());
        for (var entry : bus.parts().entrySet()) {
            // The colour affects only the indicator-light ring: renderModel
            // tints only faces with a tintindex, and in the part model only
            // the ring has one.
            var state = entry.getValue().state();
            blocks.getModelRenderer().renderModel(poses.last(), buffer, cable,
                    models.getModel(ConnectorPartModels.of(size, entry.getKey())),
                    DeviceStateColours.red(state), DeviceStateColours.green(state),
                    DeviceStateColours.blue(state),
                    light, overlay, ModelData.EMPTY, RenderType.cutout());
        }
    }
}
