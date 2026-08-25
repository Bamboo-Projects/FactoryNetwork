package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import dev.devpanda.factorynetwork.storage.EnergyCellItem;
import dev.devpanda.factorynetwork.storage.EnergyCellView;
import dev.devpanda.factorynetwork.storage.FluidCellItem;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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
public class DriveBlockEntity extends ShelfBlockEntity {

    public static final int SLOTS = 10;

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
     * Die offenen Energiezellen, nach Platznummer.
     *
     * <p>Die dritte Abbildung aus demselben Grund wie die zweite — und mit
     * demselben Gewinn: Eine Energiezelle trägt eine Zahl, keine Karte, und
     * damit passt sie in {@link CellInventory} nicht hinein.
     */
    private final java.util.Map<Integer, EnergyCellView> openEnergy = new java.util.HashMap<>();

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.DRIVE.get(), pos, state, SLOTS);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof StorageCellItem
                || stack.getItem() instanceof FluidCellItem
                || stack.getItem() instanceof EnergyCellItem;
    }

    @Override
    public dev.devpanda.factorynetwork.client.menu.ShelfMenu.Layout layout() {
        return dev.devpanda.factorynetwork.client.menu.ShelfMenu.DRIVE;
    }

    public ItemStack cell(int slot) {
        return getItem(slot);
    }

    public void setCell(int slot, ItemStack stack) {
        setItem(slot, stack);
    }

    public NonNullList<ItemStack> cells() {
        return parts();
    }

    /**
     * Eine Zelle verlässt ihren Platz.
     *
     * <p>Erst zurückschreiben, dann tauschen: Eine Zelle, die herausgeht,
     * nimmt ihren Bestand mit — aber nur, wenn er im Gegenstand steht.
     */
    @Override
    protected void beforeSlotChange(int slot) {
        CellView leaving = openItems.remove(slot);
        if (leaving == null) {
            leaving = openFluids.remove(slot);
        }
        if (leaving == null) {
            leaving = openEnergy.remove(slot);
        }
        if (leaving != null) {
            leaving.flush();
        }
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
        return live(openFluids, FluidCellItem.class, CellInventory::ofFluids);
    }

    /**
     * Und auf die Energiezellen.
     *
     * <p>Sie zählen zum Stromvorrat des Netzes, nicht zum Speicher. Wer sie
     * abfragt, ist deshalb nicht der Netzindex, sondern
     * {@code NetworkPower}.
     */
    public List<EnergyCellView> energyCells() {
        return live(openEnergy, EnergyCellItem.class, EnergyCellView::of);
    }

    private <V extends CellView> List<V> live(
            java.util.Map<Integer, V> cache, Class<?> kind,
            java.util.function.Function<ItemStack, V> open) {
        List<V> found = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(slot);
            V alive = cache.get(slot);
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
        openEnergy.values().forEach(EnergyCellView::flush);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        openItems.clear();
        openFluids.clear();
        openEnergy.clear();
        super.loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Der springende Punkt: Was im Speicher liegt, muss vor dem Sichern in
        // die Gegenstände. Ohne diese Zeile wäre der Bestand nach einem
        // Neustart der von vorhin.
        flushCells();
        super.saveAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        flushCells();
        super.setRemoved();
    }
}
