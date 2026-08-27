package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import dev.devpanda.factorynetwork.terminal.RemoteAccess;
import dev.devpanda.factorynetwork.terminal.TerminalTab;
import dev.devpanda.factorynetwork.upgrade.RemoteDevice;
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

    /** Dieselben Werte wie in jedem Vanilla-Fenster — siehe TerminalLayout. */
    private static final int INV_X = TerminalLayout.INV_X;
    private static final int INV_Y = TerminalLayout.INV_Y;
    private static final int HOTBAR_Y = TerminalLayout.HOTBAR_Y;
    private static final int SLOT_SIZE = TerminalLayout.SLOT;

    private final ContainerLevelAccess access;
    private final BlockPos position;
    private final Player owner;

    /**
     * Womit dieses Fenster geöffnet wurde, oder {@code null} am Block.
     *
     * <p>Es geht über die Leitung mit, damit der Client dieselben Reiter
     * zeichnet, die der Server erlaubt. Ein Reiter, der sich öffnen lässt und
     * dann nichts tut, ist schlimmer als einer, der gar nicht da ist.
     */
    private final RemoteDevice device;

    /**
     * Der Platz im Inventar, an dem das Gerät beim Öffnen lag.
     *
     * <p>Gemerkt und nicht jedes Mal gesucht: Ein Gerät, das während des
     * Betriebs in eine Kiste wandert, soll das Fenster schließen — und das
     * merkt nur, wer weiß, wo es lag.
     */
    private final int deviceSlot;

    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(),
                buffer.readBoolean() ? buffer.readEnum(RemoteDevice.class) : null,
                buffer.readVarInt());
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position) {
        this(id, inventory, position, null, -1);
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos position,
                        RemoteDevice device, int deviceSlot) {
        super(FnMenus.TERMINAL.get(), id);
        this.position = position;
        this.device = device;
        this.deviceSlot = deviceSlot;
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

        // Ab jetzt bekommt der Spieler den Netzzustand — die Statuszeile
        // steht auf jedem Reiter, also darf sie nicht am Speicher hängen.
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            controller(serverPlayer).ifPresent(
                    controller -> controller.watchTerminal(serverPlayer));
        }
    }

    public BlockPos position() {
        return position;
    }

    /**
     * Der Controller, auf dessen Daten dieses Fenster arbeitet.
     *
     * <p>Am Block steht ein Terminal an der Position und weiß, zu welchem
     * Netz es gehört. Aus der Ferne steht dort ein Sendemast — und den fragt
     * man wie jeden anderen Block danach, in welchem Netz er liegt.
     */
    public Optional<ControllerBlockEntity> controller(Player player) {
        if (player.level().getBlockEntity(position) instanceof TerminalBlockEntity terminal) {
            return terminal.controller();
        }
        if (device != null) {
            return dev.devpanda.factorynetwork.network.ControllerRegistry
                    .owning(player.level(), position);
        }
        return Optional.empty();
    }

    /** Womit dieses Fenster geöffnet wurde, oder {@code null} am Block. */
    public RemoteDevice device() {
        return device;
    }

    /**
     * Darf dieser Reiter gezeigt werden?
     *
     * <p>Am Block alle. Aus der Ferne alles außer Code, es sei denn, es ist
     * ein Laptop.
     */
    public boolean allows(TerminalTab tab) {
        return device == null || device.allows(tab);
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
            controller(player).ifPresent(controller -> controller.unwatchTerminal(serverPlayer));
        }
    }

    /**
     * Am Block: Steht er noch da und ist der Spieler nah genug?
     *
     * <p>Aus der Ferne sind es andere Fragen, und sie stehen in
     * {@link RemoteAccess} — dort kann ein Prüflauf sie stellen, ohne auf
     * das Zugehen eines Fensters zu warten.
     */
    @Override
    public boolean stillValid(Player player) {
        if (device != null) {
            return RemoteAccess.allowed(player, deviceSlot, position);
        }
        return stillValid(access, player, FnBlocks.TERMINAL.get());
    }
}
