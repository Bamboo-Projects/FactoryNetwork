package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import dev.devpanda.factorynetwork.terminal.RemoteAccess;
import dev.devpanda.factorynetwork.terminal.TerminalTab;
import dev.devpanda.factorynetwork.upgrade.RemoteDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The menu behind the terminal.
 *
 * <p>The thirty-six slots of the player inventory sit in the places where
 * they sit in every Minecraft inventory. That is no accident: whoever opens
 * a window reaches there blindly, and a real {@link Slot} brings shift-click,
 * dragging across several fields, number keys, and double-click collecting on
 * its own.
 *
 * <p>The network stock, by contrast, is <b>not</b> a slot. Twenty thousand
 * kinds cannot be created; it is drawn and operated over its own messages.
 */
public class TerminalMenu extends AbstractContainerMenu {

    /** The same values as in every vanilla window — see TerminalLayout. */
    private static final int INV_X = TerminalLayout.INV_X;
    private static final int INV_Y = TerminalLayout.INV_Y;
    private static final int HOTBAR_Y = TerminalLayout.HOTBAR_Y;
    private static final int SLOT_SIZE = TerminalLayout.SLOT;

    private final ContainerLevelAccess access;
    private final BlockPos position;
    private final Player owner;

    /**
     * What this window was opened with, or {@code null} at the block.
     *
     * <p>It travels along the wire so that the client draws the same tabs the
     * server allows. A tab that can be opened and then does nothing is worse
     * than one that is not there at all.
     */
    private final RemoteDevice device;

    /**
     * The inventory slot the device was in when it was opened.
     *
     * <p>Remembered and not searched for each time: a device that moves into
     * a chest while in use should close the window — and only someone who
     * knows where it was will notice that.
     */
    private final int deviceSlot;

    /**
     * Which world the mast stands in, or {@code null} at the block.
     *
     * <p><b>Without it the window would run empty.</b> The controller is
     * resolved via a level, and someone standing in the Nether looking at a
     * network in the Overworld would get nothing there — the window would
     * open and every action would fizzle out.
     */
    private final ResourceKey<Level> home;

    /**
     * Writes what a terminal block has to report: nothing but its location.
     *
     * <p><b>Sits here, right next to the constructor that reads it.</b> When
     * the two sides lived in different files, exactly what should no longer go
     * wrong here went wrong: the writer set one field fewer than the reader
     * expected, and the client threw an ArrayIndexOutOfBoundsException on
     * opening.
     */
    public static void writeBlock(RegistryFriendlyByteBuf buffer, BlockPos position) {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(false);
    }

    /** And what a remote device has to report: world, kind, and inventory slot. */
    public static void writeRemote(RegistryFriendlyByteBuf buffer, GlobalPos mast,
                                   RemoteDevice device, int slot) {
        buffer.writeBlockPos(mast.pos());
        buffer.writeBoolean(true);
        // The world must come along: otherwise the menu resolves its
        // controller via the player's world, and the player is elsewhere.
        buffer.writeResourceKey(mast.dimension());
        buffer.writeEnum(device);
        buffer.writeVarInt(slot);
    }

    /**
     * Reads what the opener wrote.
     *
     * <p><b>One flag for everything remote, not three.</b> From afar there is
     * always a world, a device, and an inventory slot; at the block none of
     * them. Two flags were one field you could forget when writing — and that
     * is exactly what happened: the enum was read as a boolean, and after that
     * every further field was shifted.
     */
    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer, buffer.readBoolean());
    }

    private TerminalMenu(int id, Inventory inventory, BlockPos position,
                         RegistryFriendlyByteBuf buffer, boolean remote) {
        this(id, inventory, position,
                remote ? buffer.readResourceKey(Registries.DIMENSION) : null,
                remote ? buffer.readEnum(RemoteDevice.class) : null,
                remote ? buffer.readVarInt() : -1);
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position) {
        this(id, inventory, position, null, null, -1);
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position,
                        ResourceKey<Level> home, RemoteDevice device, int deviceSlot) {
        super(FnMenus.TERMINAL.get(), id);
        this.position = position;
        this.home = home;
        this.device = device;
        this.deviceSlot = deviceSlot;
        this.owner = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), position);

        // Three rows of inventory
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(slotFor(inventory, column + row * 9 + 9,
                        INV_X + column * SLOT_SIZE, INV_Y + row * SLOT_SIZE));
            }
        }
        // Hotbar
        for (int column = 0; column < 9; column++) {
            addSlot(slotFor(inventory, column, INV_X + column * SLOT_SIZE, HOTBAR_Y));
        }

        // From now on the player receives the network state — the status row
        // is on every tab, so it must not depend on storage.
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            controller(serverPlayer).ifPresent(
                    controller -> controller.watchTerminal(serverPlayer));
        }
    }

    /**
     * An ordinary slot — except for the device this window is open with.
     *
     * <p><b>Otherwise you pull the ladder up behind you.</b> Whoever puts
     * their Wireless Terminal from the terminal into storage has it in the
     * network and can no longer reach it without a second device. The window
     * would close at that same moment, and the network would keep the key to
     * itself.
     *
     * <p>At the block nothing is locked: there is no device there that you
     * could take away from yourself.
     */
    private Slot slotFor(Inventory inventory, int index, int x, int y) {
        if (device == null || index != deviceSlot) {
            return new Slot(inventory, index, x, y);
        }
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        };
    }

    /** Is the device this window is open with at this slot in the window? */
    private boolean holdsOpenDevice(int index) {
        return device != null && index >= 0 && index < slots.size()
                && slots.get(index) instanceof Slot slot
                && !slot.mayPickup(owner);
    }

    public BlockPos position() {
        return position;
    }

    /**
     * The controller on whose data this window works.
     *
     * <p>At the block, a terminal stands at the position and knows which
     * network it belongs to. From afar, a transmitter mast stands there — and
     * you ask it, like any other block, which network it lies in.
     */
    public Optional<ControllerBlockEntity> controller(Player player) {
        if (player.level().getBlockEntity(position) instanceof TerminalBlockEntity terminal) {
            return terminal.controller();
        }
        if (device == null) {
            return Optional.empty();
        }
        // Look in the mast's world, not the player's: with a boundless card
        // it sits in a different one.
        net.minecraft.world.level.Level level = levelOfMast(player);
        return level == null || !level.isLoaded(position)
                ? Optional.empty()
                : dev.devpanda.factorynetwork.network.ControllerRegistry
                        .owning(level, position);
    }

    /** The world the mast stands in — even when it is not the player's. */
    private net.minecraft.world.level.Level levelOfMast(Player player) {
        if (home == null || player.level().dimension().equals(home)) {
            return player.level();
        }
        return player.getServer() == null ? null : player.getServer().getLevel(home);
    }

    /** What this window was opened with, or {@code null} at the block. */
    public RemoteDevice device() {
        return device;
    }

    /**
     * May this tab be shown?
     *
     * <p>At the block, all of them. From afar, everything except code, unless
     * it is a laptop.
     */
    public boolean allows(TerminalTab tab) {
        return device == null || device.allows(tab);
    }

    /**
     * Shift-click deposits into the network — but only when the storage tab
     * is open. Otherwise items would vanish while someone is reading code.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer serverPlayer) || !storageTabOpen) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || holdsOpenDevice(index)) {
            // The device this window is open with stays put — see slotFor.
            return ItemStack.EMPTY;
        }
        // This path also deposits into the network, but goes through
        // vanilla's click and not through StorageActionPacket. Without this
        // line, "moving a stack costs power" would be true in only one
        // direction.
        if (!charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
            return ItemStack.EMPTY;
        }
        Optional<ControllerBlockEntity> controller = controller(player);
        if (controller.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        // The whole stack, together with everything it carries — and whatever
        // does not fit stays put. A network can be full ever since there are
        // cells; without this line the remainder would vanish.
        long left = controller.get().storage().insert(stack);
        slot.set(left <= 0 ? ItemStack.EMPTY : stack.copyWithCount((int) left));
        controller.get().pushStorageTo(serverPlayer, true);
        return ItemStack.EMPTY;
    }

    /**
     * Whether the storage tab is open. The client reports this so that the
     * stock does not stream while someone is typing in the editor.
     */
    private boolean storageTabOpen;

    public void setStorageTabOpen(ServerPlayer player, boolean open) {
        if (storageTabOpen == open) {
            return;
        }
        storageTabOpen = open;
        controller(player).ifPresent(controller -> {
            if (open) {
                controller.watchStorage(player);
            } else {
                controller.unwatchStorage(player);
            }
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Unsubscribe, otherwise the controller keeps sending forever.
        if (player instanceof ServerPlayer serverPlayer) {
            controller(player).ifPresent(controller -> controller.unwatchTerminal(serverPlayer));
        }
    }

    /**
     * Takes power from the device's battery.
     *
     * <p>At the block nothing costs anything — there the terminal hangs on the
     * network, and what the network draws is settled by the controller.
     *
     * <p><b>Ask first, then take.</b> A deduction that does not survive the
     * rejection would be worse than none: someone who has a hundred FE and
     * attempts an action costing a hundred and twenty would lose the hundred
     * and get nothing for it — and again on the next attempt.
     *
     * @return whether there was enough
     */
    public boolean charge(Player player, int amount) {
        if (device == null) {
            return true;
        }
        var battery = player.getInventory().getItem(deviceSlot)
                .getCapability(net.neoforged.neoforge.capabilities.Capabilities
                        .EnergyStorage.ITEM);
        if (battery == null || battery.extractEnergy(amount, true) < amount) {
            return false;
        }
        battery.extractEnergy(amount, false);
        return true;
    }

    /**
     * Deducts what an open window costs per tick.
     *
     * <p><b>Here and not in a ticker of its own:</b> vanilla calls this per
     * tick for every open window, and a second path for it would be a second
     * path to forget it.
     *
     * <p>Nothing is closed here. That is done by {@link #stillValid} on the
     * next pass — closing a window in the middle of distributing the changes
     * would mean altering the list that is currently being iterated.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (device != null) {
            charge(owner, dev.devpanda.factorynetwork.network.Power.REMOTE_TICK);
        }
    }

    /**
     * At the block: is it still there and is the player near enough?
     *
     * <p>From afar the questions are different, and they live in
     * {@link RemoteAccess} — there a test can ask them without waiting for a
     * window to close.
     */
    @Override
    public boolean stillValid(Player player) {
        if (device != null) {
            // An empty battery means closed. The charge is deducted in
            // broadcastChanges; here it is noticed that nothing is left.
            return RemoteAccess.allowed(player, deviceSlot,
                            net.minecraft.core.GlobalPos.of(home, position))
                    && dev.devpanda.factorynetwork.item.RemoteDeviceItem.energyOf(
                            player.getInventory().getItem(deviceSlot)) > 0;
        }
        return stillValid(access, player, FnBlocks.TERMINAL.get());
    }
}
