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

    /**
     * In welcher Welt der Mast steht, oder {@code null} am Block.
     *
     * <p><b>Ohne sie liefe das Fenster leer.</b> Der Controller wird über ein
     * Level aufgelöst, und wer im Nether steht und auf ein Netz in der
     * Oberwelt schaut, bekäme dort nichts — das Fenster ginge auf und jede
     * Handlung verpuffte.
     */
    private final ResourceKey<Level> home;

    /**
     * Schreibt, was ein Terminal-Block zu melden hat: nichts als seinen Ort.
     *
     * <p><b>Steht hier, direkt neben dem Konstruktor, der es liest.</b> Als
     * die beiden Seiten in verschiedenen Dateien standen, ging genau das
     * schief, was hier nicht mehr schiefgehen soll: Der Schreiber setzte ein
     * Feld weniger als der Leser erwartete, und der Client warf beim Öffnen
     * eine ArrayIndexOutOfBoundsException.
     */
    public static void writeBlock(RegistryFriendlyByteBuf buffer, BlockPos position) {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(false);
    }

    /** Und was ein Ferngerät zu melden hat: Welt, Art und Platz im Inventar. */
    public static void writeRemote(RegistryFriendlyByteBuf buffer, GlobalPos mast,
                                   RemoteDevice device, int slot) {
        buffer.writeBlockPos(mast.pos());
        buffer.writeBoolean(true);
        // Die Welt muss mit: Das Menü löst seinen Controller sonst über die
        // Welt des Spielers auf, und der steht woanders.
        buffer.writeResourceKey(mast.dimension());
        buffer.writeEnum(device);
        buffer.writeVarInt(slot);
    }

    /**
     * Liest, was der Öffner geschrieben hat.
     *
     * <p><b>Ein Flag für alles Ferne, nicht drei.</b> Aus der Ferne gibt es
     * immer eine Welt, ein Gerät und einen Platz im Inventar; am Block keins
     * davon. Zwei Flags waren ein Feld, das man beim Schreiben vergessen
     * konnte — und genau das ist passiert: Der Enum wurde als Boolean
     * gelesen, und danach war jedes weitere Feld verschoben.
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

        // Drei Reihen Inventar
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(slotFor(inventory, column + row * 9 + 9,
                        INV_X + column * SLOT_SIZE, INV_Y + row * SLOT_SIZE));
            }
        }
        // Schnellzugriff
        for (int column = 0; column < 9; column++) {
            addSlot(slotFor(inventory, column, INV_X + column * SLOT_SIZE, HOTBAR_Y));
        }

        // Ab jetzt bekommt der Spieler den Netzzustand — die Statuszeile
        // steht auf jedem Reiter, also darf sie nicht am Speicher hängen.
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            controller(serverPlayer).ifPresent(
                    controller -> controller.watchTerminal(serverPlayer));
        }
    }

    /**
     * Ein gewöhnlicher Platz — außer für das Gerät, mit dem dieses Fenster
     * offen ist.
     *
     * <p><b>Sonst zieht man die Leiter hinter sich hoch.</b> Wer sein
     * Wireless Terminal aus dem Terminal heraus ins Lager legt, hat es im
     * Netz und kommt ohne ein zweites Gerät nicht mehr daran. Das Fenster
     * ginge im selben Moment zu, und das Netz behielte den Schlüssel zu sich
     * selbst.
     *
     * <p>Am Block ist nichts gesperrt: Dort gibt es kein Gerät, das man sich
     * wegnehmen könnte.
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

    /** Liegt an diesem Platz im Fenster das Gerät, mit dem es offen ist? */
    private boolean holdsOpenDevice(int index) {
        return device != null && index >= 0 && index < slots.size()
                && slots.get(index) instanceof Slot slot
                && !slot.mayPickup(owner);
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
        if (device == null) {
            return Optional.empty();
        }
        // In der Welt des Masts nachsehen, nicht in der des Spielers: Mit
        // einer Grenzenlos-Karte sitzt er in einer anderen.
        net.minecraft.world.level.Level level = levelOfMast(player);
        return level == null || !level.isLoaded(position)
                ? Optional.empty()
                : dev.devpanda.factorynetwork.network.ControllerRegistry
                        .owning(level, position);
    }

    /** Die Welt, in der der Mast steht — auch wenn es nicht die des Spielers ist. */
    private net.minecraft.world.level.Level levelOfMast(Player player) {
        if (home == null || player.level().dimension().equals(home)) {
            return player.level();
        }
        return player.getServer() == null ? null : player.getServer().getLevel(home);
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
        if (!slot.hasItem() || holdsOpenDevice(index)) {
            // Das Gerät, mit dem dieses Fenster offen ist, bleibt liegen —
            // siehe slotFor.
            return ItemStack.EMPTY;
        }
        // Auch dieser Weg legt ins Netz ab, geht aber über Vanillas Klick
        // und nicht über StorageActionPacket. Ohne diese Zeile wäre
        // "ein Stapel bewegt kostet Strom" nur in einer Richtung wahr.
        if (!charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
            return ItemStack.EMPTY;
        }
        Optional<ControllerBlockEntity> controller = controller(player);
        if (controller.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (!dev.devpanda.factorynetwork.storage.StorageKeys.storable(stack)) {
            // Das Lager führt nur Kennung und Menge. Ein Stapel mit eigenen
            // Daten ginge nackt hinein und käme nackt zurück — lieber im
            // Rucksack behalten als still verlieren.
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("message.factorynetwork.storage.keeps_data"), true);
            return ItemStack.EMPTY;
        }
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
     * Nimmt Strom aus dem Akku des Geräts.
     *
     * <p>Am Block kostet nichts — dort hängt das Terminal am Netz, und was
     * das Netz zieht, rechnet der Controller ab.
     *
     * <p><b>Erst fragen, dann nehmen.</b> Ein Abzug, der die Ablehnung nicht
     * überlebt, wäre schlimmer als gar keiner: Wer hundert FE hat und eine
     * Handlung für hundertzwanzig versucht, verlöre die hundert und bekäme
     * nichts dafür — und beim nächsten Versuch wieder.
     *
     * @return ob genug da war
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
     * Zieht ab, was ein offenes Fenster je Tick kostet.
     *
     * <p><b>Hier und nicht in einem eigenen Ticker:</b> Vanilla ruft das je
     * Tick für jedes offene Fenster, und ein zweiter Weg dafür wäre ein
     * zweiter Weg, ihn zu vergessen.
     *
     * <p>Geschlossen wird hier nicht. Das erledigt {@link #stillValid} beim
     * nächsten Durchgang — ein Fenster mitten im Verteilen der Änderungen zu
     * schließen, hieße die Liste zu ändern, über die gerade gelaufen wird.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (device != null) {
            charge(owner, dev.devpanda.factorynetwork.network.Power.REMOTE_TICK);
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
            // Leerer Akku heißt zu. Der Ladestand wird in broadcastChanges
            // abgezogen; hier fällt auf, dass nichts mehr da ist.
            return RemoteAccess.allowed(player, deviceSlot,
                            net.minecraft.core.GlobalPos.of(home, position))
                    && dev.devpanda.factorynetwork.item.RemoteDeviceItem.energyOf(
                            player.getInventory().getItem(deviceSlot)) > 0;
        }
        return stillValid(access, player, FnBlocks.TERMINAL.get());
    }
}
