package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.network.packet.OpenRemotePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens the wireless terminal, wherever the device is carried.
 *
 * <p>Needed for a device worn in a Curios slot, and handy for one in the
 * inventory too. B by default: unused by vanilla, and G is taken by the guide.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class RemoteKey {

    private static final String CATEGORY = "key.category.factorynetwork.keys";

    private static final KeyMapping OPEN = new KeyMapping("key.factorynetwork.open_remote",
            GLFW.GLFW_KEY_B, CATEGORY);

    private RemoteKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                PacketDistributor.sendToServer(new OpenRemotePacket());
            }
        }
    }
}
