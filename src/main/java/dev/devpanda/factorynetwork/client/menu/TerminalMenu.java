package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.core.BlockPos;
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
 * Das Menü hinter dem Terminal.
 *
 * <p>Die sechsunddreißig Slots des Spielerinventars sitzen an den Stellen,
 * an denen sie in jedem Minecraft-Inventar sitzen. Das ist kein Zufall: Wer
 * ein Fenster öffnet, greift blind dorthin, und ein echter {@link Slot}
 * bringt Umschalt-Klick, Ziehen über mehrere Felder, Zahlentasten und
 * Doppelklick-Sammeln von selbst mit.
 *
 * <p>Der Netzbestand ist dagegen <b>kein</b> Slot. Zwanzigtausend Arten
 * lassen sich nicht anlegen; er wird gezeichnet und über eigene Nachrichten
 * bedient.
 */
public class TerminalMenu extends AbstractContainerMenu {

    /** Dieselben Werte wie in jedem Vanilla-Fenster. */
    private static final int INV_X = 8;
    private static final int INV_Y = 140;
    private static final int HOTBAR_Y = 198;
    private static final int SLOT_SIZE = 18;

    private final ContainerLevelAccess access;
    private final BlockPos position;
    private final Player owner;

    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos());
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position) {
        super(FnMenus.TERMINAL.get(), id);
        this.position = position;
        this.owner = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), position);

        // Drei Reihen Inventar
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        INV_X + column * SLOT_SIZE, INV_Y + row * SLOT_SIZE));
            }
        }
        // Schnellzugriff
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INV_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    public BlockPos position() {
        return position;
    }

    /** Der Controller, auf dessen Daten dieses Terminal arbeitet. */
    public Optional<ControllerBlockEntity> controller(Player player) {
        if (player.level().getBlockEntity(position) instanceof TerminalBlockEntity terminal) {
            return terminal.controller();
        }
        return Optional.empty();
    }

    /**
     * Umschalt-Klick legt ins Netz ab — aber nur, wenn der Speicher-Reiter
     * offen ist. Sonst verschwänden Gegenstände, während jemand Code liest.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer serverPlayer) || !storageTabOpen) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        Optional<ControllerBlockEntity> controller = controller(player);
        if (controller.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        controller.get().storage().insert(stack.getItem(), stack.getCount());
        slot.set(ItemStack.EMPTY);
        controller.get().pushStorageTo(serverPlayer, true);
        return ItemStack.EMPTY;
    }

    /**
     * Ob der Speicher-Reiter offen ist. Der Client meldet das, damit der
     * Bestand nicht läuft, während jemand im Editor tippt.
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
        // Abmelden, sonst schickt der Controller ewig weiter.
        if (player instanceof ServerPlayer serverPlayer) {
            controller(player).ifPresent(controller -> controller.unwatchStorage(serverPlayer));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FnBlocks.TERMINAL.get());
    }
}
