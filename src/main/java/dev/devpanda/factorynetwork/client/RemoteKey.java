package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.network.packet.OpenRemotePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens the wireless terminal, wherever the device is carried.
 *
 * <p>Needed for a device worn in a Curios slot, and handy for one in the
 * inventory too. B by default: unused by vanilla, and G is taken by the guide.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class RemoteKey {

    /** Shared with the other keys of this mod, so they stand under one heading. */
    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "keys"));

    private static final KeyMapping OPEN = new KeyMapping("key.factorynetwork.open_remote",
            GLFW.GLFW_KEY_B, CATEGORY);

    private RemoteKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN);
        event.register(SlotOverlay.TOGGLE_KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                ClientPacketDistributor.sendToServer(new OpenRemotePacket());
            }
        }
    }
}
