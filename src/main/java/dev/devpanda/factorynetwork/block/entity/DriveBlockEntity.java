package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Laufwerk mit zehn Plätzen.
 *
 * <p>Zehn wie bei Applied Energistics: genug, dass ein Laufwerk lohnt, wenig
 * genug, dass eine große Basis mehrere braucht.
 *
 * <p>Die Zellen liegen hier, ihr Inhalt aber steckt in ihnen selbst. Das
 * Laufwerk ist ein Regal, kein Speicher.
 */
public class DriveBlockEntity extends BlockEntity {

    public static final int SLOTS = 10;

    private final NonNullList<ItemStack> cells =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /** Die offenen Gegenstandszellen, nach Platznummer. */
    private final java.util.Map<Integer, CellInventory<net.minecraft.world.item.Item>>
            openItems = new java.util.HashMap<>();

    /**
     * Die offenen Flüssigkeitszellen, nach Platznummer.
     *
     * <p>Zwei Abbildungen statt einer mit Platzhaltertyp: Aus einer
     * gemeinsamen käme bei jedem Zugriff ein ungeprüfter Typwechsel, und der
     * Übersetzer könnte nicht mehr helfen.
     */
    private final java.util.Map<Integer, CellInventory<net.minecraft.world.level.material.Fluid>>
            openFluids = new java.util.HashMap<>();

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

    public long revision() {
        return revision;
    }

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.DRIVE.get(), pos, state);
    }

    public ItemStack cell(int slot) {
        return slot >= 0 && slot < SLOTS ? cells.get(slot) : ItemStack.EMPTY;
    }

    public void setCell(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        // Erst zurückschreiben, dann tauschen: Eine Zelle, die herausgeht,
        // nimmt ihren Bestand mit — aber nur, wenn er im Gegenstand steht.
        CellInventory<?> alt = openItems.remove(slot);
        if (alt == null) {
            alt = openFluids.remove(slot);
        }
        if (alt != null) {
            alt.flush();
        }
        cells.set(slot, stack);
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public NonNullList<ItemStack> cells() {
        return cells;
    }

    /**
     * Die eingesetzten Zellen als lebende Speicher.
     *
     * <p><b>Sie werden gehalten, nicht bei jedem Zugriff neu gelesen.</b> Das
     * ist der Unterschied zwischen einer Anlage, die läuft, und einer, die
     * ruckelt: Der Inhalt steckt als NBT im Gegenstand, und ihn je Zugriff zu
     * entpacken heißt, bei zehn Zellen mit je vierundsechzig Arten mehrere
     * tausend Einträge je Tick durch Registry-Suchen zu schicken. Applied
     * Energistics hält seine Zellen aus demselben Grund im Speicher und
     * schreibt erst, wenn die BlockEntity gesichert wird.
     *
     * <p>Gebunden wird an den Gegenstand selbst, nicht an die Platznummer:
     * Wer eine Zelle herauszieht und eine andere hineinsteckt, bekommt eine
     * neue Sicht, auch wenn der Platz derselbe ist.
     */
    public List<CellInventory<net.minecraft.world.item.Item>> inventories() {
        return live(openItems, StorageCellItem.class, CellInventory::ofItems);
    }

    /** Dieselbe Sicht auf die Flüssigkeitszellen. */
    public List<CellInventory<net.minecraft.world.level.material.Fluid>> fluidInventories() {
        return live(openFluids, dev.devpanda.factorynetwork.storage.FluidCellItem.class,
                CellInventory::ofFluids);
    }

    private <T> List<CellInventory<T>> live(
            java.util.Map<Integer, CellInventory<T>> cache, Class<?> kind,
            java.util.function.Function<ItemStack, CellInventory<T>> open) {
        List<CellInventory<T>> found = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = cells.get(slot);
            CellInventory<T> alive = cache.get(slot);
            if (!kind.isInstance(stack.getItem())) {
                if (alive != null) {
                    cache.remove(slot);
                }
                continue;
            }
            if (alive == null || alive.stack() != stack) {
                // Andere Zelle im Platz — die alte hat ihren Inhalt schon
                // zurückgeschrieben, als sie herausgenommen wurde.
                alive = open.apply(stack);
                if (!alive.isValid()) {
                    continue;
                }
                cache.put(slot, alive);
            }
            found.add(alive);
        }
        return found;
    }

    /**
     * Schreibt alle offenen Zellen zurück in ihre Gegenstände.
     *
     * <p>Muss vor jedem Sichern geschehen und immer dann, wenn eine Zelle das
     * Laufwerk verlässt. Sonst steht im Gegenstand ein Bestand von vorhin.
     */
    public void flushCells() {
        openItems.values().forEach(CellInventory::flush);
        openFluids.values().forEach(CellInventory::flush);
    }

    /** Der erste freie Platz, oder -1. */
    public int firstFreeSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (cells.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Der letzte belegte Platz, oder -1. */
    public int lastUsedSlot() {
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!cells.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : cells) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public boolean isEmpty() {
        return cells.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cells.clear();
        openItems.clear();
        openFluids.clear();
        ContainerHelper.loadAllItems(tag, cells, registries);
        revision++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Der springende Punkt: Was im Speicher liegt, muss vor dem Sichern in
        // die Gegenstände. Ohne diese Zeile wäre der Bestand nach einem
        // Neustart der von vorhin.
        flushCells();
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, cells, registries);
    }

    @Override
    public void setRemoved() {
        flushCells();
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, cells, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
