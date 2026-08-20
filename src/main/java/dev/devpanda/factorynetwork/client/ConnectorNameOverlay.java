package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zeigt die Namen der Connectoren in der Welt, solange die Label-Gun in der
 * Hand ist.
 *
 * <p>SuperFactoryManager löst dasselbe Problem mit drei Ansichtsmodi und einem
 * Overlay, das erklärt, in welchem man gerade ist. Das ist der Preis dafür,
 * dass die Namen dort in der Gun stecken und nicht in der Welt. Hier tragen
 * die Connectoren ihre Namen selbst, also lassen sie sich einfach anzeigen —
 * ohne Modus, ohne Erinnerung.
 *
 * <p>Drei Zustände sind unterscheidbar, und alle drei sind welche, die man
 * sehen will: benannt, unbenannt, und doppelt vergeben.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class ConnectorNameOverlay {

    /** Weiter als das wird nicht beschriftet — sonst steht der Bildschirm voll. */
    private static final int RANGE = 16;

    private static final int COLOR_NAMED = 0xFFA3D9A5;
    private static final int COLOR_UNNAMED = 0xFF8A939C;
    private static final int COLOR_CONFLICT = 0xFFE88388;
    private static final int BACKGROUND = 0x66000000;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (!holdsLabelGun(player)) {
            return;
        }
        render(event, minecraft, level, player);
    }

    private static boolean holdsLabelGun(Player player) {
        return player.getMainHandItem().is(FnItems.LABEL_GUN.get())
                || player.getOffhandItem().is(FnItems.LABEL_GUN.get());
    }

    private static void render(RenderLevelStageEvent event, Minecraft minecraft,
                               Level level, Player player) {
        BlockPos center = player.blockPosition();
        List<ConnectorBlockEntity> connectors = nearbyConnectors(level, center);
        if (connectors.isEmpty()) {
            return;
        }

        // Namen, die mehr als einmal vorkommen, sind unbrauchbar — beide
        // Connectoren werden rot, nicht einer davon.
        Map<String, Integer> counts = new HashMap<>();
        for (ConnectorBlockEntity connector : connectors) {
            String label = connector.label();
            if (!label.isBlank()) {
                counts.merge(label, 1, Integer::sum);
            }
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        for (ConnectorBlockEntity connector : connectors) {
            String label = connector.label();
            String shown = label.isBlank() ? "?" : label;
            int color = label.isBlank() ? COLOR_UNNAMED
                    : counts.getOrDefault(label, 0) > 1 ? COLOR_CONFLICT : COLOR_NAMED;

            BlockPos pos = connector.getBlockPos();
            poses.pushPose();
            poses.translate(
                    pos.getX() + 0.5 - cameraPosition.x,
                    pos.getY() + 1.1 - cameraPosition.y,
                    pos.getZ() + 0.5 - cameraPosition.z);
            poses.mulPose(camera.rotation());
            poses.scale(-0.025F, -0.025F, 0.025F);

            Matrix4f matrix = poses.last().pose();
            float half = -font.width(shown) / 2.0F;
            font.drawInBatch(shown, half, 0, color, false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
            poses.popPose();
        }
        buffers.endBatch();
    }

    /**
     * Alle Connectoren in Reichweite.
     *
     * <p>Gelesen wird aus den geladenen BlockEntities des Chunks, nicht durch
     * Abtasten der Blöcke: Ein Würfel von 33 Blocklänge wären
     * fünfunddreißigtausend Positionen — in jedem Bild.
     */
    private static List<ConnectorBlockEntity> nearbyConnectors(Level level, BlockPos center) {
        List<ConnectorBlockEntity> found = new ArrayList<>();
        int chunkRange = (RANGE >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int x = centerChunkX - chunkRange; x <= centerChunkX + chunkRange; x++) {
            for (int z = centerChunkZ - chunkRange; z <= centerChunkZ + chunkRange; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                for (BlockEntityHolder holder : entitiesIn(level, x, z)) {
                    if (holder.entity() instanceof ConnectorBlockEntity connector
                            && connector.getBlockPos().distSqr(center) <= RANGE * RANGE) {
                        found.add(connector);
                    }
                }
            }
        }
        return found;
    }

    private record BlockEntityHolder(net.minecraft.world.level.block.entity.BlockEntity entity) {}

    private static List<BlockEntityHolder> entitiesIn(Level level, int chunkX, int chunkZ) {
        List<BlockEntityHolder> holders = new ArrayList<>();
        level.getChunk(chunkX, chunkZ).getBlockEntities().values()
                .forEach(entity -> holders.add(new BlockEntityHolder(entity)));
        return holders;
    }

    private ConnectorNameOverlay() {
    }
}
