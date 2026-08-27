package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.upgrade.Loadout;
import dev.devpanda.factorynetwork.upgrade.UpgradeSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Was im Sendemast steckt.
 *
 * <p>Vier Plätze, und mehr Zustand hat er nicht: Wie weit er trägt, rechnet
 * {@code Range} aus dem, was darin liegt.
 *
 * <p><b>Er ist ein {@link Container}, aber kein {@link ShelfBlockEntity}.</b>
 * Ein Regal hält Zellen und Serverbauteile und weiß, was ein Einschub ist;
 * hier liegen vier gleichwertige Plätze, und was hineindarf, entscheidet
 * {@link UpgradeSlots}. Das Fenster teilen sich beide trotzdem — es ist
 * dieselbe Handlung.
 */
public class MastBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** So viele Plätze hat ein Mast — siehe fernzugriff.md §3. */
    public static final int SLOTS = 4;

    private final UpgradeSlots slots = new UpgradeSlots(SLOTS);

    public MastBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.MAST.get(), pos, state);
    }

    public UpgradeSlots slots() {
        return slots;
    }

    /** Was dieser Mast kann und wie weit er trägt. */
    public Loadout loadout() {
        return slots.loadout();
    }

    // ---- Container ------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!slots.get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slots.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(slots.contents(), slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(slots.contents(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        slots.set(slot, stack);
        setChanged();
    }

    /**
     * Was hineindarf, entscheidet das Ausbausystem.
     *
     * <p>Das Fenster fragt hier, bevor es einen Stapel annimmt — sonst zieht
     * der Spieler ihn hinein und er springt zurück.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slots.accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ---- Fenster --------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.factorynetwork.mast");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory,
                                                      Player player) {
        return ShelfMenu.of(id, inventory, this, ShelfMenu.MAST);
    }

    // ---- Speichern ------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        slots.save(tag, registries);
    }

    /**
     * Der Inhalt geht auch an den Client.
     *
     * <p>Ohne das zeigt das Fenster beim Öffnen vier leere Plätze und füllt
     * sie erst einen Tick später — das sieht aus, als hätte man etwas
     * verloren.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        slots.save(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
