package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

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
public class CableBusRenderer implements BlockEntityRenderer<CableBusBlockEntity, CableBusRenderer.State> {

    /** Per occupied face the part's model and its colour; see DriveRenderer.State. */
    public static final class State extends BlockEntityRenderState {
        final java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> models =
                new java.util.ArrayList<>();
        final java.util.List<Integer> colours = new java.util.ArrayList<>();
    }

    public CableBusRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CableBusBlockEntity bus, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(bus, state, partialTick, camera, breakProgress);
        state.models.clear();
        state.colours.clear();
        if (!bus.hasParts()) {
            return;
        }
        int size = CableBlock.sizeOf(bus.getBlockState());
        var models = Minecraft.getInstance().getModelManager();
        for (var entry : bus.parts().entrySet()) {
            state.models.add(models.getStandaloneModel(ConnectorPartModels.of(size, entry.getKey())));
            // The colour affects only the indicator-light ring: only faces with
            // a tintindex are tinted, and in the part model only the ring has one.
            state.colours.add(DeviceStateColours.opaque(entry.getValue().state()));
        }
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        for (int i = 0; i < state.models.size(); i++) {
            // The same kind of drawing as the cable: the edges of the texture
            // are transparent.
            collector.submitBlockModel(poses, RenderTypes.cutoutMovingBlock(),
                    java.util.List.of(state.models.get(i)), new int[] {state.colours.get(i)},
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
