package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
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
 * Shows the names of the connectors in the world, as long as the label gun is
 * in hand.
 *
 * <p>SuperFactoryManager solves the same problem with three view modes and an
 * overlay that explains which one you are currently in. That is the price of
 * the names being held in the gun there and not in the world. Here the
 * connectors carry their names themselves, so they can simply be shown — no
 * mode, no remembering.
 *
 * <p>Three states are distinguishable, and all three are ones you want to
 * see: named, unnamed, and assigned twice.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class ConnectorNameOverlay {

    /** Nothing is labelled beyond this — otherwise the screen fills up. */
    private static final int RANGE = 16;

    // The same palette as the little lamps on the connectors: red means the
    // same in both places, and it stands there only once.
    private static final int COLOR_NAMED = dev.devpanda.factorynetwork.client.render
            .DeviceStateColours.opaque(dev.devpanda.factorynetwork.network
                    .DeviceState.ONLINE);
    private static final int COLOR_UNNAMED = dev.devpanda.factorynetwork.client.render
            .DeviceStateColours.opaque(dev.devpanda.factorynetwork.network
                    .DeviceState.UNNAMED);
    private static final int COLOR_CONFLICT = dev.devpanda.factorynetwork.client.render
            .DeviceStateColours.opaque(dev.devpanda.factorynetwork.network
                    .DeviceState.DUPLICATE);
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
        Map<BlockPos, List<dev.devpanda.factorynetwork.block.entity.ConnectorPart>> connectors =
                nearbyConnectors(level, center);
        if (connectors.isEmpty()) {
            return;
        }

        // Names that occur more than once are unusable — both connectors turn
        // red, not just one of them.
        Map<String, Integer> counts = new HashMap<>();
        for (List<dev.devpanda.factorynetwork.block.entity.ConnectorPart> parts : connectors.values()) {
            for (dev.devpanda.factorynetwork.block.entity.ConnectorPart connector : parts) {
                String label = connector.label();
                if (!label.isBlank()) {
                    counts.merge(label, 1, Integer::sum);
                }
            }
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        for (Map.Entry<BlockPos, List<dev.devpanda.factorynetwork.block.entity.ConnectorPart>> entry
                : connectors.entrySet()) {
            BlockPos pos = entry.getKey();
            List<dev.devpanda.factorynetwork.block.entity.ConnectorPart> parts = entry.getValue();
            for (int i = 0; i < parts.size(); i++) {
                dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = parts.get(i);
                String label = connector.label();
                String shown = label.isBlank() ? "?" : label;
                // Only when several hang on one block does it need to say
                // which face is meant. For a single one the note would be mere
                // clutter.
                if (parts.size() > 1) {
                    shown = mark(connector.facing()) + " " + shown;
                }
                int color = label.isBlank() ? COLOR_UNNAMED
                        : counts.getOrDefault(label, 0) > 1 ? COLOR_CONFLICT : COLOR_NAMED;

                poses.pushPose();
                // Stacked, not overlapping: six names on one cable block would
                // otherwise all stand in the same spot.
                poses.translate(
                        pos.getX() + 0.5 - cameraPosition.x,
                        pos.getY() + 1.1 + i * LINE_HEIGHT - cameraPosition.y,
                        pos.getZ() + 0.5 - cameraPosition.z);
                poses.mulPose(camera.rotation());
                poses.scale(-0.025F, -0.025F, 0.025F);

                Matrix4f matrix = poses.last().pose();
                float half = -font.width(shown) / 2.0F;
                font.drawInBatch(shown, half, 0, color, false, matrix, buffers,
                        Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
                poses.popPose();
            }
        }
        buffers.endBatch();
    }

    /**
     * All connectors in range.
     *
     * <p>Read from the chunk's loaded BlockEntities, not by scanning the
     * blocks: a cube 33 blocks on a side would be thirty-five thousand
     * positions — in every frame.
     */
    private static Map<BlockPos, List<dev.devpanda.factorynetwork.block.entity.ConnectorPart>> nearbyConnectors(
            Level level, BlockPos center) {
        Map<BlockPos, List<dev.devpanda.factorynetwork.block.entity.ConnectorPart>> found =
                new java.util.LinkedHashMap<>();
        int chunkRange = (RANGE >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int x = centerChunkX - chunkRange; x <= centerChunkX + chunkRange; x++) {
            for (int z = centerChunkZ - chunkRange; z <= centerChunkZ + chunkRange; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                for (BlockEntityHolder holder : entitiesIn(level, x, z)) {
                    var entity = holder.entity();
                    if (entity.getBlockPos().distSqr(center) > RANGE * RANGE) {
                        continue;
                    }
                    if (entity instanceof dev.devpanda.factorynetwork.block.entity
                            .CableBusBlockEntity bus && bus.hasParts()) {
                        // Ordered by face, because the map is an EnumMap: the
                        // same six names always stand stacked in the same
                        // order.
                        found.put(bus.getBlockPos(), List.copyOf(bus.parts().values()));
                    }
                }
            }
        }
        return found;
    }

    /** How far apart two stacked names stand, in blocks. */
    private static final double LINE_HEIGHT = 0.28;

    /**
     * The face in two characters.
     *
     * <p>German abbreviations and no arrows: whether the font has an arrow
     * you only see in-game — {@code Ob} and {@code Un} it has for certain.
     */
    private static String mark(net.minecraft.core.Direction side) {
        return switch (side) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "O";
            case WEST -> "W";
            case UP -> "Ob";
            case DOWN -> "Un";
        };
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
