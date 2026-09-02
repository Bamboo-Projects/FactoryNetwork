package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.registry.FnComponents;
import dev.devpanda.factorynetwork.terminal.RemoteAccess;
import dev.devpanda.factorynetwork.upgrade.Loadout;
import dev.devpanda.factorynetwork.upgrade.RemoteDevice;
import dev.devpanda.factorynetwork.upgrade.UpgradeSlots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * A device for remote access: the wireless terminal or the laptop.
 *
 * <p>One item for both. What sets them apart is in {@link RemoteDevice} —
 * the number of slots and the question whether the code tab is included.
 *
 * <p><b>Such a device carries three things with it:</b> the mast it is bound
 * to; what sits in its slots; and how full its battery is. All three as
 * registered components, see
 * {@link FnComponents}.
 */
public class RemoteDeviceItem extends Item {

    private final RemoteDevice device;
    private final int capacity;

    public RemoteDeviceItem(Properties properties, RemoteDevice device, int capacity) {
        super(properties.stacksTo(1));
        this.device = device;
        this.capacity = capacity;
    }

    public RemoteDevice device() {
        return device;
    }

    /** How much power fits in. */
    public int capacity() {
        return capacity;
    }

    /** Which device this is — or {@code null} if it is none. */
    public static RemoteDevice deviceOf(ItemStack stack) {
        return stack.getItem() instanceof RemoteDeviceItem item ? item.device() : null;
    }

    /** Which mast the device hangs on, world and all — or {@code null}. */
    public static GlobalPos mastOf(ItemStack stack) {
        return stack.get(FnComponents.MAST.get());
    }

    /** The name of the coupled network, or {@code null}. */
    public static String networkOf(ItemStack stack) {
        return stack.get(FnComponents.NETWORK_NAME.get());
    }

    /**
     * Binds the device to a mast.
     *
     * <p>The network name comes along so the tooltip can show it without
     * querying the world — in the inventory the client does not have it.
     */
    public static void bind(ItemStack stack, GlobalPos mast, String network) {
        stack.set(FnComponents.MAST.get(), mast);
        stack.set(FnComponents.NETWORK_NAME.get(), network);
    }

    /**
     * Binds a device to this mast — or unbinds it again.
     *
     * <p>Lives here and not in the block so that a test run can call it. A
     * test that instead calls {@link #bind} checks the setting of a
     * component and not the rule that matters: <b>binding the same mast
     * twice unbinds.</b>
     *
     * <p>Without the way back there would be none — other than throwing the
     * device away.
     *
     * @return {@code true} if a binding exists afterwards;
     *         {@code false} after unbinding
     */
    public static boolean couple(ItemStack stack, GlobalPos mast) {
        if (mast.equals(mastOf(stack))) {
            unbind(stack);
            return false;
        }
        bind(stack, mast, describe(mast));
        return true;
    }

    /**
     * What a mast is called in the tooltip.
     *
     * <p>The name is the position: there are no network names, and an
     * invented number would not be findable in the game.
     *
     * <p>The world is only stated when it is not the Overworld. Naming it
     * everywhere would make the line longer without saying anything — most
     * networks stand there anyway.
     */
    public static String describe(GlobalPos mast) {
        BlockPos pos = mast.pos();
        String place = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        if (mast.dimension().equals(Level.OVERWORLD)) {
            return place;
        }
        return place + " (" + mast.dimension().location().getPath() + ")";
    }

    /** Takes back the binding. */
    public static void unbind(ItemStack stack) {
        stack.remove(FnComponents.MAST.get());
        stack.remove(FnComponents.NETWORK_NAME.get());
    }

    /**
     * What sits in the slots.
     *
     * <p>Read fresh from the stack, not remembered: a device can lie in a
     * chest and be rearranged there while a window is open.
     */
    public static UpgradeSlots slotsOf(ItemStack stack) {
        RemoteDevice device = deviceOf(stack);
        if (device == null) {
            return new UpgradeSlots(0);
        }
        UpgradeSlots slots = new UpgradeSlots(device.slots());
        ItemContainerContents held = stack.get(DataComponents.CONTAINER);
        if (held != null) {
            held.copyInto(slots.contents());
        }
        return slots;
    }

    /** Writes the slots back to the stack. */
    public static void saveSlots(ItemStack stack, UpgradeSlots slots) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots.contents()));
    }

    /** What this device can do — the short form for the range calculation. */
    public static Loadout loadoutOf(ItemStack stack) {
        return slotsOf(stack).loadout();
    }

    /** The charge level, or 0 without a battery. */
    public static int energyOf(ItemStack stack) {
        IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return battery == null ? 0 : battery.getEnergyStored();
    }

    /**
     * A right-click into the air opens the terminal from afar.
     *
     * <p>Four reasons not to do it, and a separate message for each: no
     * network bound, the mast is gone, it belongs to no network, or it is
     * too far away. A single message "doesn't work" would leave the player
     * guessing which of the four cases applies.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        GlobalPos mast = mastOf(held);
        if (mast == null) {
            return refuse(player, held, "message.factorynetwork.remote.no_network");
        }
        Level home = level.dimension().equals(mast.dimension()) || player.getServer() == null
                ? level : player.getServer().getLevel(mast.dimension());
        if (home == null || !home.isLoaded(mast.pos())) {
            // Not the same as dismantled, and therefore a separate message:
            // whoever reads "The transmitter mast is gone" builds a new one —
            // and the old one still stands, only no one is looking there
            // right now.
            return refuse(player, held, "message.factorynetwork.remote.not_loaded");
        }
        if (!(home.getBlockEntity(mast.pos())
                instanceof dev.devpanda.factorynetwork.block.entity.MastBlockEntity standing)) {
            return refuse(player, held, "message.factorynetwork.remote.mast_gone");
        }
        var controller = dev.devpanda.factorynetwork.network.ControllerRegistry
                .owning(home, mast.pos());
        if (controller.isEmpty()) {
            return refuse(player, held, "message.factorynetwork.remote.no_controller");
        }
        int slot = RemoteAccess.slotOf(player, held);
        if (slot < 0 || !RemoteAccess.allowed(player, slot, mast)) {
            // Two reasons, one message would be too few: whoever stands in
            // another dimension does not walk closer — they need a card.
            return refuse(player, held,
                    level.dimension().equals(mast.dimension())
                            ? "message.factorynetwork.remote.out_of_range"
                            : "message.factorynetwork.remote.other_world");
        }
        // Ask first, then take: a half-charged device should keep its
        // remainder if it cannot open the window anyway.
        int opening = dev.devpanda.factorynetwork.network.Power.REMOTE_OPEN;
        var battery = held.getCapability(Capabilities.EnergyStorage.ITEM);
        if (battery == null || battery.extractEnergy(opening, true) < opening) {
            return refuse(player, held, "message.factorynetwork.remote.empty");
        }
        battery.extractEnergy(opening, false);
        if (player instanceof ServerPlayer serverPlayer) {
            // Send the state first, then open — as at the block: the editor
            // should already have its data when it draws.
            //
            // watchTerminal alone is not enough. It sends project, flows and
            // displays, but not the network overview — that would otherwise
            // arrive only on the next regular send, and the tab would stand
            // empty until then.
            controller.get().sendNetworkStateTo(serverPlayer);
            controller.get().watchTerminal(serverPlayer);
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                            .TerminalMenu(id, inventory, mast.pos(), mast.dimension(),
                                    device, slot),
                    Component.translatable(getDescriptionId())), buffer ->
                    dev.devpanda.factorynetwork.client.menu.TerminalMenu
                            .writeRemote(buffer, mast, device, slot));
        }
        return InteractionResultHolder.consume(held);
    }

    /** A refusal with a reason. */
    private static InteractionResultHolder<ItemStack> refuse(Player player, ItemStack held,
                                                             String message) {
        player.displayClientMessage(Component.translatable(message), true);
        return InteractionResultHolder.fail(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        // Two devices look the same in the inventory. What sets them apart
        // is the network they hang on — that deserves to be visible.
        String network = networkOf(stack);
        if (network == null || network.isBlank()) {
            lines.add(Component.translatable("item.factorynetwork.remote.unbound")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.factorynetwork.remote.bound", network)
                    .withStyle(ChatFormatting.AQUA));
        }
        IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (battery != null) {
            lines.add(Component.translatable("item.factorynetwork.remote.charge",
                            battery.getEnergyStored(), battery.getMaxEnergyStored())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return battery != null && battery.getEnergyStored() < battery.getMaxEnergyStored();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (battery == null || battery.getMaxEnergyStored() == 0) {
            return 0;
        }
        return Math.round(13.0F * battery.getEnergyStored() / battery.getMaxEnergyStored());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // The same blue as the machines' energy display.
        return 0x3BA7DB;
    }
}
