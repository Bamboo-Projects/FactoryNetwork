package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.network.InternalBuffer;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Die Brennkammer: Strom aus Ofenbrennstoff.
 *
 * <p><b>Absichtlich mittelmäßig.</b> Sie hat keine Ausbaustufen und keinen
 * Aufrüstpfad, und in einem Pack stellt man nach der ersten Stunde etwas
 * Besseres daneben. Sie ist nicht dafür da, mit Generatoren anderer Mods zu
 * konkurrieren, sondern dafür, dass man ohne sie nicht blockiert ist —
 * dasselbe Bild wie die Vibrationskammer bei Applied Energistics.
 *
 * <p>Der Grund ist konkreter als der Strom des Netzes: Die Presse braucht FE,
 * und ohne eine Quelle in der Mod selbst wäre die ganze Fertigungskette —
 * Erz, Platte, Kerne, Zellen, Prozessoren — ohne Fremdmod nicht zu
 * durchlaufen.
 */
public class BurnerBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** Wie viel sie erzeugt, solange sie brennt. */
    public static final int PER_TICK = 40;

    /** Was sie puffert, bis der Nachbar es abnimmt. */
    public static final int CAPACITY = 20_000;

    public static final int SLOT_FUEL = 0;
    public static final int SLOTS = 1;

    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    private static final int DATA_SIZE = 4;

    private static final String KEY_BURN = "Burn";
    private static final String KEY_BURN_TOTAL = "BurnTotal";
    private static final String KEY_ENERGY = "Energy";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Gibt nach außen nichts ab, sondern schiebt selbst.
     *
     * <p>Der Controller nimmt an und zieht nicht. Eine Quelle, die nur
     * bereitstellt, käme bei ihm nie an.
     */
    private final InternalBuffer energy = new InternalBuffer(CAPACITY, CAPACITY);

    private int burnTicks;
    private int burnTotal;

    public BurnerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.BURNER.get(), pos, state);
    }

    public InternalBuffer energy() {
        return energy;
    }

    public boolean isBurning() {
        return burnTicks > 0;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    /**
     * Ein Tick: brennen, erzeugen, weitergeben.
     *
     * <p>Nachgelegt wird erst, wenn der Puffer nicht voll ist. Sonst
     * verbrennt eine Kohle, während niemand abnimmt — und das merkt man erst,
     * wenn der Kohlenstapel weg ist.
     */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
                                  BlockState state, BurnerBlockEntity burner) {
        boolean brannte = burner.burnTicks > 0;
        if (burner.burnTicks > 0) {
            burner.burnTicks--;
            burner.energy.charge(PER_TICK);
        } else if (burner.energy.getEnergyStored() < CAPACITY) {
            burner.light();
        }
        burner.pushToNeighbours(level, pos);
        if (brannte != burner.burnTicks > 0) {
            // Die Front zeigt das Feuer, und der Block leuchtet dabei. Beides
            // hängt am Zustand, nicht an der BlockEntity: Es ist genau ein
            // Wahrheitswert.
            level.setBlock(pos, state.setValue(
                    dev.devpanda.factorynetwork.block.BurnerBlock.LIT,
                    burner.burnTicks > 0), 3);
            burner.setChanged();
        }
    }

    /** Zündet den nächsten Brennstoff, wenn einer da ist. */
    private void light() {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) {
            return;
        }
        int burn = fuel.getBurnTime(null);
        if (burn <= 0) {
            return;
        }
        burnTicks = burn;
        burnTotal = burn;
        // Ein Eimer Lava lässt seinen Eimer da, wie im Ofen auch.
        ItemStack rest = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !rest.isEmpty()) {
            items.set(SLOT_FUEL, rest);
        }
        setChanged();
    }

    /**
     * Schiebt in jeden Nachbarn, der annimmt.
     *
     * <p>Reihum in fester Richtungsfolge: Wer zwei Verbraucher anschließt,
     * soll erklären können, welcher zuerst bekommt.
     */
    private void pushToNeighbours(net.minecraft.world.level.Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(side), side.getOpposite());
            if (sink == null || !sink.canReceive()) {
                continue;
            }
            int taken = sink.receiveEnergy(energy.getEnergyStored(), false);
            if (taken > 0) {
                energy.consume(taken);
                setChanged();
            }
        }
    }

    /** Die Zahlen für das Fenster. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN -> burnTicks;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    // ---- Container --------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_FUEL).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS) {
            items.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getBurnTime(null) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    // ---- Fenster ----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new dev.devpanda.factorynetwork.client.menu.BurnerMenu(
                id, inventory, this, data);
    }

    // ---- Sichern ----------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTicks = tag.getInt(KEY_BURN);
        burnTotal = Math.max(1, tag.getInt(KEY_BURN_TOTAL));
        if (tag.contains(KEY_ENERGY)) {
            energy.deserializeNBT(registries, tag.get(KEY_ENERGY));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(KEY_BURN, burnTicks);
        tag.putInt(KEY_BURN_TOTAL, burnTotal);
        tag.put(KEY_ENERGY, energy.serializeNBT(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(KEY_BURN, burnTicks);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
