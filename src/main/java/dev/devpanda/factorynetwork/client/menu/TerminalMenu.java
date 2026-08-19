package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Das Menü hinter dem Terminal.
 *
 * <p>Es trägt keine Slots — der Editor ist reiner Text. Gebraucht wird es
 * trotzdem, weil nur ein Menü eine Oberfläche serverseitig offen halten und
 * Nachrichten zuordnen kann.
 */
public class TerminalMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos position;

    /** Aufruf auf dem Client, aus dem Netzwerkpuffer. */
    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos());
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position) {
        super(FnMenus.TERMINAL.get(), id);
        this.position = position;
        this.access = ContainerLevelAccess.create(inventory.player.level(), position);
    }

    public BlockPos position() {
        return position;
    }

    /** Der Controller, auf dessen Programm dieses Terminal arbeitet. */
    public Optional<ControllerBlockEntity> controller(Player player) {
        if (player.level().getBlockEntity(position) instanceof TerminalBlockEntity terminal) {
            return terminal.controller();
        }
        return Optional.empty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FnBlocks.TERMINAL.get());
    }
}
