package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableShapes;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
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
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Zeigt vor dem Setzen, wohin der Anschluss käme.
 *
 * <p><b>Der gewöhnliche Blockumriss beantwortet die Frage nicht.</b> Er
 * umfasst das ganze Kabel samt allem, was schon daran hängt — welche der
 * sechs Flächen der Klick trifft, sieht man ihm nicht an. Bei einem Kabel von
 * sechs Blockpixeln ist das keine Kleinigkeit: Man zielt auf eine Röhre und
 * trifft die Fläche daneben.
 *
 * <p>Gezeichnet wird <b>zusätzlich</b> zum Umriss und nicht statt seiner: Wer
 * das Ereignis abbricht, nimmt dem Spieler die Auskunft, welchen Block er
 * überhaupt ansieht.
 *
 * <p>Rot heißt: Dort ist kein Platz. Die Prüfung dafür ist dieselbe, die das
 * Setzen benutzt — sonst verspräche die Vorschau etwas, das der Klick danach
 * ablehnt.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class PartPlacementPreview {

    private static final float[] FREE = {1.0F, 1.0F, 1.0F, 0.7F};
    private static final float[] TAKEN = {1.0F, 0.35F, 0.35F, 0.9F};

    @SubscribeEvent
    public static void preview(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || !holdsConnector(player)) {
            return;
        }
        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CableBlock)) {
            return;
        }
        Direction side = hit.getDirection();
        float[] colour = CableBlock.hasRoomForPart(state, level, pos, side) ? FREE : TAKEN;

        Vec3 camera = event.getCamera().getPosition();
        LevelRenderer.renderVoxelShape(event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()),
                CableShapes.part(CableBlock.sizeOf(state), side),
                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                colour[0], colour[1], colour[2], colour[3], false);
    }

    /**
     * Beide Hände.
     *
     * <p>Gesetzt wird mit der Hand, die den Anschluss hält — welche das ist,
     * entscheidet Minecraft und nicht wir.
     */
    private static boolean holdsConnector(Player player) {
        var connector = FnItems.CONNECTOR.get();
        return player.getMainHandItem().is(connector)
                || player.getOffhandItem().is(connector);
    }

    private PartPlacementPreview() {
    }
}
