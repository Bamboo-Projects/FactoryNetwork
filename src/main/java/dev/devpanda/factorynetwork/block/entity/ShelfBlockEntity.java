package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Regal mit Plätzen für Bauteile.
 *
 * <p>Das Laufwerk nimmt Zellen, der Serverschrank Serverbauteile — und sonst
 * unterscheiden sie sich nicht: Beide sind ein Gehäuse, dessen Fähigkeit in
 * dem steckt, was man hineinsteckt. <b>Deshalb steht die Buchführung ein
 * einziges Mal da.</b> Zwei Fassungen wären zwei Orte, an denen ein Bauteil
 * verlorengehen kann, und die eine bekäme irgendwann eine Verbesserung, die
 * der anderen fehlt.
 *
 * <p>Ein {@link Container} ist es, damit ein Fenster darauf zeigen kann. Der
 * Weg durch {@link #setItem} ist der einzige: Dort zählt der Stand hoch, und
 * dort erfährt die Unterklasse, dass sie aufräumen muss.
 */
public abstract class ShelfBlockEntity extends BlockEntity
        implements Container, net.minecraft.world.MenuProvider {

    private final NonNullList<ItemStack> parts;

    /**
     * Zählt hoch, sobald sich die Bestückung ändert.
     *
     * <p>Der Netzindex hält den Bestand aller Zellen zusammen. Ändert er ihn
     * selbst, rechnet er die Änderung ein; wird dagegen eine Zelle
     * herausgezogen, muss er es merken. Genau dafür ist diese Zahl da — sie
     * ist der billigste Weg, „hier hat jemand anders etwas getan" zu sagen.
     *
     * <p><b>Nicht in {@code setChanged}</b>: Das ruft der Speicher nach jeder
     * eigenen Ablage selbst, und dann wäre jeder Index sofort wieder hin.
     */
    private long revision;

    protected ShelfBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                               int slots) {
        super(type, pos, state);
        this.parts = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    /** Was in dieses Regal darf. */
    public abstract boolean accepts(ItemStack stack);

    /** Wie das Fenster dieses Regals zugeschnitten ist. */
    public abstract dev.devpanda.factorynetwork.client.menu.ShelfMenu.Layout layout();

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return dev.devpanda.factorynetwork.client.menu.ShelfMenu.of(
                id, inventory, this, layout());
    }

    /**
     * Ein Platz hat gewechselt.
     *
     * <p>Wird gerufen, <b>bevor</b> der neue Gegenstand eingetragen ist — das
     * Laufwerk schreibt hier den Bestand seiner offenen Zelle zurück, und
     * dafür muss die alte noch dastehen.
     */
    protected void beforeSlotChange(int slot) {
    }

    public long revision() {
        return revision;
    }

    /**
     * Zählt den Stand hoch, ohne über {@link #setItem} zu gehen.
     *
     * <p>Für Unterklassen, die mehrere Plätze in einem Zug umschreiben — der
     * Serverschrank packt beim Herausziehen eines Gehäuses drei Plätze
     * gleichzeitig aus.
     */
    protected void bumpRevision() {
        revision++;
    }

    protected NonNullList<ItemStack> parts() {
        return parts;
    }

    /** Der erste freie Platz, oder -1. */
    public int firstFreeSlot() {
        for (int slot = 0; slot < parts.size(); slot++) {
            if (parts.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Der letzte belegte Platz, oder -1. */
    public int lastUsedSlot() {
        for (int slot = parts.size() - 1; slot >= 0; slot--) {
            if (!parts.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : parts) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // ---- Container --------------------------------------------------------

    @Override
    public int getContainerSize() {
        return parts.size();
    }

    @Override
    public boolean isEmpty() {
        return parts.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < parts.size() ? parts.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // Ein Bauteil geht ganz oder gar nicht: Ein halber Stapel Zellen aus
        // einem Platz wäre ein Platz, der zwei Bestände trägt.
        return removeItemNoUpdate(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = getItem(slot);
        if (!taken.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= parts.size()) {
            return;
        }
        beforeSlotChange(slot);
        parts.set(slot, stack);
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < parts.size(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
    }

    // ---- Sichern ----------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        parts.clear();
        ContainerHelper.loadAllItems(tag, parts, registries);
        revision++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, parts, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, parts, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
