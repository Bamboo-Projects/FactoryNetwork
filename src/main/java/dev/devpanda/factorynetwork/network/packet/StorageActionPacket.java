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
 * Entnehmen und Einlagern aus der Speicheransicht.
 *
 * <p><b>Der Server rechnet nach.</b> Was der Client anzeigt, ist ein
 * Schnappschuss von vorhin — in der Zwischenzeit kann ein Worker den Bestand
 * geleert haben. Deshalb wird die angeforderte Menge nie geglaubt, sondern
 * gegen den echten Bestand geprüft. Ohne diese Regel entstehen genau die
 * Fehler, an denen sich vergleichbare Mods lange abgearbeitet haben.
 */
public record StorageActionPacket(Kind kind, Item item, int amount) implements CustomPacketPayload {

    public enum Kind {
        /** Aus dem Netz auf den Mauszeiger. */
        EXTRACT,
        /** Vom Mauszeiger ins Netz. */
        INSERT
    }

    public static final Type<StorageActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(index -> Kind.values()[index], Kind::ordinal),
                    StorageActionPacket::kind,
                    ByteBufCodecs.registry(Registries.ITEM), StorageActionPacket::item,
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
            // Aus der Ferne kostet jeder Griff ins Lager. Am Block gibt
            // charge() immer true zurück — dort zahlt das Netz.
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
                int wanted = Math.min(packet.amount(), packet.item().getDefaultMaxStackSize());
                // Nachrechnen: Der Bestand kann sich geändert haben.
                long taken = controller.storage().extract(packet.item(), wanted);
                if (taken <= 0) {
                    return;
                }
                ItemStack stack = new ItemStack(packet.item(), (int) taken);
                if (menu.getCarried().isEmpty()) {
                    menu.setCarried(stack);
                } else if (ItemStack.isSameItemSameComponents(menu.getCarried(), stack)) {
                    ItemStack carried = menu.getCarried();
                    int room = carried.getMaxStackSize() - carried.getCount();
                    int added = Math.min(room, stack.getCount());
                    carried.grow(added);
                    // Was nicht auf den Zeiger passt, geht zurück ins Netz.
                    if (added < stack.getCount()) {
                        controller.storage().insert(packet.item(), stack.getCount() - added);
                    }
                } else {
                    // Zeiger ist belegt: nichts entnehmen, alles zurücklegen.
                    controller.storage().insert(packet.item(), taken);
                    return;
                }
                controller.setChanged();
            }
            case INSERT -> {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty() || carried.getItem() != packet.item()) {
                    return;
                }
                int amount = Math.min(packet.amount(), carried.getCount());
                controller.storage().insert(packet.item(), amount);
                carried.shrink(amount);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                controller.setChanged();
            }
        }
        controller.pushStorageTo(player, true);
    }
}
