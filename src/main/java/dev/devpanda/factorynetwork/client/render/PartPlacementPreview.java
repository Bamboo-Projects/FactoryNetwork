package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableShapes;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

/**
 * Shows before placement where the connector would go.
 *
 * <p><b>The ordinary block outline does not answer that question.</b> It
 * encompasses the whole cable together with everything already attached to
 * it — which of the six faces the click hits cannot be told from it. With a
 * cable of six block pixels that is no small matter: you aim at a tube and
 * hit the face next to it.
 *
 * <p>It is drawn <b>in addition</b> to the outline and not instead of it:
 * whoever cancels the event deprives the player of the information about
 * which block they are even looking at.
 *
 * <p>Red means: there is no room there. The check for it is the same one
 * placement uses — otherwise the preview would promise something the click
 * then rejects.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class PartPlacementPreview {

    private static final float[] FREE = {1.0F, 1.0F, 1.0F, 0.7F};
    private static final float[] TAKEN = {1.0F, 0.35F, 0.35F, 0.9F};

    @SubscribeEvent
    public static void preview(ExtractBlockOutlineRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || !holdsConnector(player)) {
            return;
        }
        BlockHitResult hit = event.getHitResult();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CableBlock)) {
            return;
        }
        Direction side = hit.getDirection();
        float[] colour = CableBlock.hasRoomForPart(state, level, pos, side) ? FREE : TAKEN;

        var shape = CableShapes.part(CableBlock.sizeOf(state), side);
        int argb = net.minecraft.util.ARGB.colorFromFloat(colour[3], colour[0], colour[1], colour[2]);
        float width = minecraft.getWindow().getAppropriateLineWidth();
        // Since 26.1 the outline is drawn later from a render state; a custom
        // renderer is the way to add to it. Vanilla's outline stays as well.
        event.addCustomRenderer((outline, buffers, poses, translucent, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            net.minecraft.client.renderer.ShapeRenderer.renderShape(poses,
                    buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines()),
                    shape, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    argb, width);
            return false;
        });
    }

    /**
     * Both hands.
     *
     * <p>Placement is done with the hand holding the connector — which one
     * that is is Minecraft's decision, not ours.
     */
    private static boolean holdsConnector(Player player) {
        var connector = FnItems.CONNECTOR.get();
        return player.getMainHandItem().is(connector)
                || player.getOffhandItem().is(connector);
    }

    private PartPlacementPreview() {
    }
}
