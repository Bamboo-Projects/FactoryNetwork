package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.press.FnRecipes;
import dev.devpanda.factorynetwork.press.PressInput;
import dev.devpanda.factorynetwork.press.PressRecipe;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.devpanda.factorynetwork.network.InternalBuffer;

import java.util.Optional;

/**
 * Die Presse.
 *
 * <p>Drei Plätze: Stempel, Material, Ausgabe. Sie zieht Strom aus dem
 * gewöhnlichen Forge-Netz — jede Mod im Pack kann sie speisen, und wir bauen
 * kein eigenes Energiesystem daneben.
 *
 * <p><b>Ohne Strom passiert nichts, und das sagt sie auch.</b> Eine Maschine,
 * die stumm stehenbleibt, schickt den Spieler auf die Suche nach dem falschen
 * Fehler; deshalb steht der Ladestand in der Oberfläche und im Jade-Tooltip.
 */
public class PressBlockEntity extends BlockEntity
        implements net.minecraft.world.MenuProvider {

    public static final int SLOT_STAMP = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_RESULT = 2;
    public static final int SLOTS = 3;

    /** Fasst so viel, dass ein Vorgang durchläuft, ohne am Tropf zu hängen. */
    public static final int CAPACITY = 40_000;

    /** Höchstens so viel je Tick — sonst wäre die Zeit im Rezept sinnlos. */
    private static final int MAX_INPUT = 2_000;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Nimmt an, gibt nichts ab.
     *
     * <p>Eine Maschine, die ihren Strom weiterreicht, wird zum Kabel — und
     * dann baut jemand eine Kette daraus und wundert sich über die Verluste.
     */
    private final InternalBuffer energy = new InternalBuffer(CAPACITY, MAX_INPUT) {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int taken = super.receiveEnergy(toReceive, simulate);
            if (taken > 0 && !simulate) {
                setChanged();
            }
            return taken;
        }
    };

    private int progress;
    private int required;

    public PressBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.PRESS.get(), pos, state);
    }

    public InternalBuffer energy() {
        return energy;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public ItemStack item(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS) {
            items.set(slot, stack);
            setChanged();
        }
    }

    public int progress() {
        return progress;
    }

    public int required() {
        return required;
    }

    /**
     * Ein Tick Arbeit.
     *
     * <p>Erst prüfen, ob ein Rezept passt und das Ergebnis Platz hat, dann
     * Strom abziehen. Andersherum verbrauchte eine volle Presse Energie für
     * nichts.
     */
    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        Optional<PressRecipe> recipe = recipeFor();
        if (recipe.isEmpty() || !fits(recipe.get())) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        PressRecipe found = recipe.get();
        required = Math.max(1, found.ticks());
        int perTick = Math.max(1, found.energy() / required);
        if (!energy.has(perTick)) {
            return;
        }
        energy.consume(perTick);
        progress++;
        if (progress >= required) {
            finish(found);
        }
        setChanged();
    }

    private Optional<PressRecipe> recipeFor() {
        if (level == null || item(SLOT_STAMP).isEmpty() || item(SLOT_MATERIAL).isEmpty()) {
            return Optional.empty();
        }
        PressInput input = new PressInput(item(SLOT_STAMP), item(SLOT_MATERIAL));
        return level.getRecipeManager()
                .getRecipeFor(FnRecipes.PRESS.get(), input, level)
                .map(holder -> holder.value());
    }

    /** Passt das Ergebnis in den Ausgabeplatz? */
    private boolean fits(PressRecipe recipe) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack current = item(SLOT_RESULT);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    private void finish(PressRecipe recipe) {
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        ItemStack current = item(SLOT_RESULT);
        if (current.isEmpty()) {
            items.set(SLOT_RESULT, result);
        } else {
            current.grow(result.getCount());
        }
        // Der Stempel bleibt — er ist Werkzeug, nicht Zutat.
        item(SLOT_MATERIAL).shrink(1);
        progress = 0;
    }

    // ---- Fenster ----------------------------------------------------------

    /**
     * Die Zahlen, die das Fenster laufend braucht.
     *
     * <p>Der schmale Weg, den Minecraft dafür vorsieht: ein paar Werte, die
     * sich jeden Tick ändern und beim Zuschauen aktuell sein müssen. Ein
     * eigenes Paket dafür wäre Aufwand für vier Zahlen.
     */
    private final net.minecraft.world.inventory.ContainerData data =
            new net.minecraft.world.inventory.ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case dev.devpanda.factorynetwork.client.menu.PressMenu.DATA_PROGRESS
                                -> progress;
                        case dev.devpanda.factorynetwork.client.menu.PressMenu.DATA_REQUIRED
                                -> required;
                        case dev.devpanda.factorynetwork.client.menu.PressMenu.DATA_ENERGY
                                -> energy.getEnergyStored();
                        case dev.devpanda.factorynetwork.client.menu.PressMenu.DATA_CAPACITY
                                -> CAPACITY;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // Nichts: Der Server rechnet, der Client schaut zu.
                }

                @Override
                public int getCount() {
                    return 4;
                }
            };

    /** Die Plätze als Container, damit das Fenster damit umgehen kann. */
    private final net.minecraft.world.Container container =
            new net.minecraft.world.SimpleContainer(SLOTS) {
                @Override
                public net.minecraft.world.item.ItemStack getItem(int slot) {
                    return PressBlockEntity.this.item(slot);
                }

                @Override
                public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
                    PressBlockEntity.this.setItem(slot, stack);
                }

                @Override
                public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
                    net.minecraft.world.item.ItemStack stack = PressBlockEntity.this.item(slot);
                    if (stack.isEmpty()) {
                        return net.minecraft.world.item.ItemStack.EMPTY;
                    }
                    net.minecraft.world.item.ItemStack taken = stack.split(amount);
                    setChanged();
                    return taken;
                }

                @Override
                public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
                    net.minecraft.world.item.ItemStack stack = PressBlockEntity.this.item(slot);
                    PressBlockEntity.this.setItem(slot,
                            net.minecraft.world.item.ItemStack.EMPTY);
                    return stack;
                }

                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return level != null
                            && level.getBlockEntity(worldPosition) == PressBlockEntity.this
                            && player.distanceToSqr(worldPosition.getX() + 0.5,
                                    worldPosition.getY() + 0.5,
                                    worldPosition.getZ() + 0.5) <= 64.0;
                }

                @Override
                public boolean isEmpty() {
                    return items.stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty);
                }
            };

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.factorynetwork.press");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new dev.devpanda.factorynetwork.client.menu.PressMenu(
                id, inventory, worldPosition, container, data);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.deserializeNBT(registries, tag.get("Energy"));
        progress = tag.getInt("Progress");
        required = tag.getInt("Required");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Required", required);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("Required", required);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
