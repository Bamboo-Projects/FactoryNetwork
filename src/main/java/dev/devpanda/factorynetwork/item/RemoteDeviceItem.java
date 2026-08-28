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
 * Ein Gerät für den Fernzugriff: das Wireless Terminal oder der Laptop.
 *
 * <p>Ein Gegenstand für beide. Was sie unterscheidet, steht in
 * {@link RemoteDevice} — die Zahl der Steckplätze und die Frage, ob der
 * Code-Reiter dabei ist.
 *
 * <p><b>Drei Dinge trägt so ein Gerät mit sich:</b> den Mast, an dem es
 * angemeldet ist; was in seinen Steckplätzen liegt; und wie voll sein Akku
 * ist. Alle drei als angemeldete Komponenten, siehe
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

    /** Wie viel Strom hineinpasst. */
    public int capacity() {
        return capacity;
    }

    /** Welches Gerät das ist — oder {@code null}, wenn es keines ist. */
    public static RemoteDevice deviceOf(ItemStack stack) {
        return stack.getItem() instanceof RemoteDeviceItem item ? item.device() : null;
    }

    /** An welchem Mast das Gerät hängt, samt Welt — oder {@code null}. */
    public static GlobalPos mastOf(ItemStack stack) {
        return stack.get(FnComponents.MAST.get());
    }

    /** Der Name des gekoppelten Netzes, oder {@code null}. */
    public static String networkOf(ItemStack stack) {
        return stack.get(FnComponents.NETWORK_NAME.get());
    }

    /**
     * Meldet das Gerät an einem Mast an.
     *
     * <p>Der Netzname kommt mit, damit der Tooltip ihn zeigen kann, ohne die
     * Welt zu befragen — im Inventar hat der Client sie nicht.
     */
    public static void bind(ItemStack stack, GlobalPos mast, String network) {
        stack.set(FnComponents.MAST.get(), mast);
        stack.set(FnComponents.NETWORK_NAME.get(), network);
    }

    /**
     * Meldet ein Gerät an diesem Mast an — oder wieder ab.
     *
     * <p>Steht hier und nicht im Block, damit ein Prüflauf sie aufrufen kann.
     * Ein Test, der stattdessen {@link #bind} ruft, prüft das Setzen einer
     * Komponente und nicht die Regel, um die es geht: <b>derselbe Mast
     * zweimal meldet ab.</b>
     *
     * <p>Ohne den Rückweg gäbe es keinen — außer das Gerät wegzuwerfen.
     *
     * @return {@code true}, wenn danach eine Anmeldung besteht;
     *         {@code false} nach dem Abmelden
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
     * Wie ein Mast im Tooltip heißt.
     *
     * <p>Der Name ist die Position: Es gibt keine Netznamen, und eine
     * erfundene Nummer wäre im Spiel nicht wiederzufinden.
     *
     * <p>Die Welt steht nur dabei, wenn es nicht die Oberwelt ist. Sie
     * überall zu nennen machte die Zeile länger, ohne etwas zu sagen — die
     * meisten Netze stehen ohnehin dort.
     */
    public static String describe(GlobalPos mast) {
        BlockPos pos = mast.pos();
        String place = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        if (mast.dimension().equals(Level.OVERWORLD)) {
            return place;
        }
        return place + " (" + mast.dimension().location().getPath() + ")";
    }

    /** Nimmt die Anmeldung zurück. */
    public static void unbind(ItemStack stack) {
        stack.remove(FnComponents.MAST.get());
        stack.remove(FnComponents.NETWORK_NAME.get());
    }

    /**
     * Was in den Steckplätzen liegt.
     *
     * <p>Frisch aus dem Stapel gelesen, nicht gemerkt: Ein Gerät kann in
     * einer Kiste liegen und dort umgebaut werden, während ein Fenster offen
     * ist.
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

    /** Schreibt die Steckplätze zurück an den Stapel. */
    public static void saveSlots(ItemStack stack, UpgradeSlots slots) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots.contents()));
    }

    /** Was dieses Gerät kann — die Kurzform für die Reichweitenrechnung. */
    public static Loadout loadoutOf(ItemStack stack) {
        return slotsOf(stack).loadout();
    }

    /** Der Ladestand, oder 0 ohne Akku. */
    public static int energyOf(ItemStack stack) {
        IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return battery == null ? 0 : battery.getEnergyStored();
    }

    /**
     * Rechtsklick in die Luft öffnet das Terminal aus der Ferne.
     *
     * <p>Vier Gründe, es nicht zu tun, und für jeden eine eigene Meldung:
     * kein Netz angemeldet, der Mast steht nicht mehr, er gehört zu keinem
     * Netz, oder er ist zu weit weg. Eine gemeinsame Meldung „geht nicht"
     * ließe den Spieler raten, welcher der vier Fälle vorliegt.
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
            // Nicht dasselbe wie abgebaut, und deshalb eine eigene Meldung:
            // Wer "Der Sendemast steht nicht mehr" liest, baut einen neuen —
            // und der alte steht noch, nur schaut dort gerade niemand hin.
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
            // Zwei Gründe, eine Meldung wäre zu wenig: Wer in einer anderen
            // Welt steht, läuft nicht näher heran — er braucht eine Karte.
            return refuse(player, held,
                    level.dimension().equals(mast.dimension())
                            ? "message.factorynetwork.remote.out_of_range"
                            : "message.factorynetwork.remote.other_world");
        }
        // Erst fragen, dann nehmen: Ein halb geladenes Gerät soll seinen
        // Rest behalten, wenn es das Fenster ohnehin nicht öffnen kann.
        int opening = dev.devpanda.factorynetwork.network.Power.REMOTE_OPEN;
        var battery = held.getCapability(Capabilities.EnergyStorage.ITEM);
        if (battery == null || battery.extractEnergy(opening, true) < opening) {
            return refuse(player, held, "message.factorynetwork.remote.empty");
        }
        battery.extractEnergy(opening, false);
        if (player instanceof ServerPlayer serverPlayer) {
            // Erst den Zustand schicken, dann öffnen — wie am Block: Der
            // Editor soll seine Daten schon haben, wenn er zeichnet.
            //
            // watchTerminal allein reicht nicht. Es schickt Projekt, Abläufe
            // und Anzeigen, aber nicht die Netzübersicht — die käme sonst
            // erst beim nächsten regelmäßigen Schicken, und der Reiter stünde
            // bis dahin leer da.
            controller.get().sendNetworkStateTo(serverPlayer);
            controller.get().watchTerminal(serverPlayer);
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                            .TerminalMenu(id, inventory, mast.pos(), mast.dimension(),
                                    device, slot),
                    Component.translatable(getDescriptionId())), buffer -> {
                buffer.writeBlockPos(mast.pos());
                buffer.writeBoolean(true);
                // Die Welt muss mit: Das Menü löst seinen Controller sonst
                // über die Welt des Spielers auf, und der steht woanders.
                buffer.writeResourceKey(mast.dimension());
                buffer.writeEnum(device);
                buffer.writeVarInt(slot);
            });
        }
        return InteractionResultHolder.consume(held);
    }

    /** Eine Absage mit Grund. */
    private static InteractionResultHolder<ItemStack> refuse(Player player, ItemStack held,
                                                             String message) {
        player.displayClientMessage(Component.translatable(message), true);
        return InteractionResultHolder.fail(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        // Zwei Geräte sehen im Inventar gleich aus. Was sie unterscheidet,
        // ist das Netz, an dem sie hängen — das gehört sichtbar.
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
        // Dasselbe Blau wie die Energieanzeige der Maschinen.
        return 0x3BA7DB;
    }
}
