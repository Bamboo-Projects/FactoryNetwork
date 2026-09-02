package dev.devpanda.factorynetwork.item;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * What sits inside a server chassis.
 *
 * <p><b>The parts sit in the item, not in the rack.</b> The same picture as
 * with the storage cell: you pull a finished server out, carry it away and
 * plug it in somewhere else — the hardware comes along. Were it in the rack,
 * the chassis would be a placeholder and not a server.
 *
 * <p><b>Editing still happens only in the rack.</b> An item that holds
 * items cannot be opened in the backpack — the same limit as with the
 * shulker box. When the chassis sits in the bay, its parts lie in the three
 * slots beside it and the item is empty; on pulling it out they move back
 * inside.
 *
 * <p>Stored under {@code DataComponents.CONTAINER}: that is the component
 * Minecraft provides for "this item holds items". It transfers to the client
 * on its own, survives {@code /give} and ensures that two differently
 * equipped chassis do not merge into one stack.
 */
public final class ServerChassis {

    /** Processor, memory, disk — in the order of {@link ServerPart}. */
    public static final int SLOTS = ServerPart.values().length;

    private ServerChassis() {
    }

    /** The three parts, always three entries long. */
    public static NonNullList<ItemStack> read(ItemStack chassis) {
        NonNullList<ItemStack> parts = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ItemContainerContents contents = chassis.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(parts);
        }
        return parts;
    }

    /** Writes the three parts in. An empty list clears it out. */
    public static void write(ItemStack chassis, List<ItemStack> parts) {
        boolean anything = parts.stream().anyMatch(part -> !part.isEmpty());
        if (anything) {
            chassis.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
        } else {
            // Do not leave it with an empty component: an empty chassis
            // should be stackable with another empty one and claim nothing
            // in the tooltip.
            chassis.remove(DataComponents.CONTAINER);
        }
    }

    /** Is there anything in it at all? */
    public static boolean isEmpty(ItemStack chassis) {
        ItemContainerContents contents = chassis.get(DataComponents.CONTAINER);
        return contents == null || contents.nonEmptyStream().findAny().isEmpty();
    }

    /** Is this a chassis? */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ServerChassisItem;
    }
}
