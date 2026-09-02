package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Puts into the label gun the name that the next click assigns.
 *
 * <p>It is entered in a dedicated window — previously that only went through
 * the anvil, which no one expects, or not at all.
 */
public record SetLabelPacket(String label) implements CustomPacketPayload {

    private static final int MAX_LENGTH = 64;

    public static final Type<SetLabelPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "set_label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLabelPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_LENGTH), SetLabelPacket::label,
                    SetLabelPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetLabelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.is(FnItems.LABEL_GUN.get())) {
                    LabelGunItem.setActiveLabel(stack, packet.label());
                    return;
                }
            }
        });
    }
}
