package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Withdrawing and storing from the storage view.
 *
 * <p><b>The server recomputes.</b> What the client shows is a snapshot from
 * before — in the meantime a worker may have emptied the stock. That is why
 * the requested amount is never believed, but checked against the real stock.
 * Without this rule you get exactly the bugs that comparable mods have long
 * struggled with.
 */
public record StorageActionPacket(Kind kind, dev.devpanda.factorynetwork.storage.ItemKey key, int amount)
        implements CustomPacketPayload {

    public enum Kind {
        /** From the network onto the cursor. */
        EXTRACT,
        /** From the cursor into the network. */
        INSERT
    }

    public static final Type<StorageActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(index -> Kind.values()[index], Kind::ordinal),
                    StorageActionPacket::kind,
                    dev.devpanda.factorynetwork.storage.ItemKey.STREAM_CODEC, StorageActionPacket::key,
                    ByteBufCodecs.VAR_INT, StorageActionPacket::amount,
                    StorageActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            // From afar, every reach into storage costs. At the block,
            // charge() always returns true — there the network pays.
            if (!menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> apply(packet, player, controller, menu));
        });
    }

    private static void apply(StorageActionPacket packet, ServerPlayer player,
                              ControllerBlockEntity controller, TerminalMenu menu) {
        switch (packet.kind()) {
            case EXTRACT -> {
                // The item's stack limit, not that of its key: a component
                // can change it.
                int wanted = Math.min(packet.amount(), packet.key().maxStackSize());
                // Recompute: the stock may have changed.
                long taken = controller.storage().extract(packet.key(), wanted);
                if (taken <= 0) {
                    return;
                }
                ItemStack stack = packet.key().toStack((int) taken);
                if (menu.getCarried().isEmpty()) {
                    menu.setCarried(stack);
                } else if (ItemStack.isSameItemSameComponents(menu.getCarried(), stack)) {
                    ItemStack carried = menu.getCarried();
                    int room = carried.getMaxStackSize() - carried.getCount();
                    int added = Math.min(room, stack.getCount());
                    carried.grow(added);
                    // What does not fit on the cursor goes back into the network.
                    if (added < stack.getCount()) {
                        controller.storage().insert(packet.key(), stack.getCount() - added);
                    }
                } else {
                    // Cursor is occupied: withdraw nothing, put everything back.
                    controller.storage().insert(packet.key(), taken);
                    return;
                }
                controller.setChanged();
            }
            case INSERT -> {
                ItemStack carried = menu.getCarried();
                // Compare fully: someone holding an enchanted book on the
                // cursor does not deposit the empty one.
                if (carried.isEmpty() || !packet.key().equals(dev.devpanda.factorynetwork.storage.ItemKey.of(carried))) {
                    return;
                }
                int amount = Math.min(packet.amount(), carried.getCount());
                controller.storage().insert(packet.key(), amount);
                carried.shrink(amount);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                controller.setChanged();
            }
        }
        controller.pushStorageTo(player, true);
    }
}
