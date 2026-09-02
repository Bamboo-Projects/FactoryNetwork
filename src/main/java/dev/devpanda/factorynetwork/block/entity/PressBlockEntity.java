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
 * The press.
 *
 * <p>Three slots: stamp, material, output. It draws power from the ordinary
 * Forge network — any mod in the pack can feed it, and we build no energy
 * system of our own beside it.
 *
 * <p><b>Without power nothing happens, and it says so too.</b> A machine that
 * stands there silently sends the player hunting for the wrong bug; that is
 * why the charge level appears in the interface and in the Jade tooltip.
 */
public class PressBlockEntity extends BlockEntity
        implements net.minecraft.world.MenuProvider {

    public static final int SLOT_STAMP = 0;

    /**
     * The material slots.
     *
     * <p>Three, because a recipe may demand at most three ingredients: a
     * processor needs redstone, copper and a carrier, and that is the heaviest
     * reckoning a press should carry. Whoever needs more needs not a press but
     * an assembly line.
     */
    public static final int SLOT_MATERIAL = 1;
    public static final int MATERIAL_SLOTS = PressRecipe.MOST_MATERIALS;

    public static final int SLOT_RESULT = SLOT_MATERIAL + MATERIAL_SLOTS;

    /**
     * The slots for upgrades.
     *
     * <p>Five, and they count by the stack: what they amount to is computed by
     * {@link dev.devpanda.factorynetwork.upgrade.Tuning}, which caps at eight
     * cards per type.
     */
    public static final int SLOT_UPGRADE = SLOT_RESULT + 1;
    public static final int UPGRADE_SLOTS = 5;

    public static final int SLOTS = SLOT_UPGRADE + UPGRADE_SLOTS;

    /** Holds enough that one pass runs through without hanging on a drip feed. */
    public static final int CAPACITY = 40_000;

    /** At most this much per tick — otherwise the time in the recipe would be pointless. */
    private static final int MAX_INPUT = 2_000;

    /**
     * What may go into the stamp slot.
     *
     * <p>A tag and not a fixed list: a datapack may bring recipes along, and
     * a stamp of its own should be insertable without anyone touching the mod.
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> STAMPS =
            net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID, "stamps"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Accepts, hands nothing out.
     *
     * <p>A machine that passes its power on becomes a cable — and then someone
     * builds a chain of them and wonders about the losses.
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
     * One tick of work.
     *
     * <p>First check whether a recipe fits and the result has room, then draw
     * power. The other way round, a full press would consume energy for
     * nothing.
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
        var tuned = tuned(found);
        required = Math.max(1, tuned.ticks());
        int perTick = Math.max(1, tuned.energy() / required);
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

    /** What lies in the material slots, in their order. */
    private PressInput input() {
        java.util.List<ItemStack> materials = new java.util.ArrayList<>(MATERIAL_SLOTS);
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            materials.add(item(SLOT_MATERIAL + i));
        }
        return new PressInput(item(SLOT_STAMP), materials);
    }

    private Optional<PressRecipe> recipeFor() {
        if (level == null || item(SLOT_STAMP).isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .getRecipeFor(FnRecipes.PRESS.get(), input(), level)
                .map(holder -> holder.value());
    }

    /**
     * What sits in the upgrade slots.
     *
     * <p>Every piece of a stack counts — the same rule as for the range cards,
     * and the same class computes it.
     */
    private dev.devpanda.factorynetwork.upgrade.Loadout loadout() {
        java.util.Map<dev.devpanda.factorynetwork.upgrade.Upgrade, Integer> counts =
                new java.util.LinkedHashMap<>();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = item(SLOT_UPGRADE + i);
            var upgrade = dev.devpanda.factorynetwork.item.UpgradeItem.upgradeOf(stack);
            if (upgrade != null) {
                counts.merge(upgrade, stack.getCount(), Integer::sum);
            }
        }
        return dev.devpanda.factorynetwork.upgrade.Loadout.ofCounts(counts);
    }

    /** The recipe, as this press carries it out with its cards. */
    public dev.devpanda.factorynetwork.upgrade.Tuned tuned(PressRecipe recipe) {
        return dev.devpanda.factorynetwork.upgrade.Tuning.of(
                loadout(), recipe.ticks(), recipe.energy());
    }

    /**
     * Is there enough material, and does the result fit into the output slot?
     *
     * <p>Both reckoned against the batch size: a press with stack cards
     * consumes the multiple and deposits the multiple. Whoever checks only the
     * recipe starts a run they cannot finish.
     */
    private boolean fits(PressRecipe recipe) {
        int batch = tuned(recipe).batch();
        int[] from = recipe.slotsFor(input());
        if (from == null) {
            return false;
        }
        for (int i = 0; i < from.length; i++) {
            int needed = recipe.materials().get(i).count() * batch;
            if (item(SLOT_MATERIAL + from[i]).getCount() < needed) {
                return false;
            }
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack current = item(SLOT_RESULT);
        int made = result.getCount() * batch;
        if (current.isEmpty()) {
            return made <= result.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + made <= current.getMaxStackSize();
    }

    private void finish(PressRecipe recipe) {
        int batch = tuned(recipe).batch();
        int[] from = recipe.slotsFor(input());
        if (from == null) {
            // Between the check and the finish someone cleared it out.
            progress = 0;
            return;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        result.setCount(result.getCount() * batch);
        ItemStack current = item(SLOT_RESULT);
        if (current.isEmpty()) {
            items.set(SLOT_RESULT, result);
        } else {
            current.grow(result.getCount());
        }
        // Each ingredient from the slot that fulfilled it — not in order: the
        // order in the press is a different one than in the recipe.
        for (int i = 0; i < from.length; i++) {
            item(SLOT_MATERIAL + from[i])
                    .shrink(recipe.materials().get(i).count() * batch);
        }
        // The stamp stays — it is a tool, not an ingredient.
        progress = 0;
    }

    // ---- Screen -------------------------------------------------------------

    /**
     * The numbers the screen needs continuously.
     *
     * <p>The narrow path Minecraft provides for this: a few values that change
     * every tick and must be current while you watch. A packet of its own for
     * this would be effort for four numbers.
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
                    // Nothing: the server computes, the client watches.
                }

                @Override
                public int getCount() {
                    return 4;
                }
            };

    /**
     * The inventory a connector sees.
     *
     * <p><b>Without it the press is not a machine but a piece of furniture.</b>
     * It has accepted power since it existed — but no connector ever found an
     * inventory on it, and so no worker and no {@code move} could give it an
     * iron ingot. In a mod whose purpose is automation, that was the gap
     * beneath all others.
     *
     * <p><b>The slots have different rules</b>, and that is why there is a
     * dedicated handler here instead of a wrapper around the container: the
     * stamp takes only stamps, the material takes no stamp, and from the
     * result only takes are allowed. A wrapper without rules would let a
     * sorting machine shove its entire contents into the press.
     */
    private final net.neoforged.neoforge.items.IItemHandler handler =
            new net.neoforged.neoforge.items.IItemHandler() {

                @Override
                public int getSlots() {
                    return SLOTS;
                }

                @Override
                public ItemStack getStackInSlot(int slot) {
                    return item(slot);
                }

                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (stack.isEmpty() || !isItemValid(slot, stack)) {
                        return stack;
                    }
                    ItemStack present = item(slot);
                    int room = Math.min(stack.getMaxStackSize(), getSlotLimit(slot))
                            - present.getCount();
                    if (room <= 0) {
                        return stack;
                    }
                    if (!present.isEmpty()
                            && !ItemStack.isSameItemSameComponents(present, stack)) {
                        return stack;
                    }
                    int fits = Math.min(room, stack.getCount());
                    if (!simulate) {
                        if (present.isEmpty()) {
                            setItem(slot, stack.copyWithCount(fits));
                        } else {
                            present.grow(fits);
                            setChanged();
                        }
                    }
                    return fits >= stack.getCount()
                            ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - fits);
                }

                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    // <b>Only the result goes out.</b> Whoever could pull the
                    // stamp out would have a press that disarms itself — and
                    // the material belongs to the machine once it lies inside.
                    if (slot != SLOT_RESULT || amount <= 0) {
                        return ItemStack.EMPTY;
                    }
                    ItemStack present = item(slot);
                    if (present.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    int taken = Math.min(amount, present.getCount());
                    if (simulate) {
                        return present.copyWithCount(taken);
                    }
                    ItemStack out = present.split(taken);
                    setChanged();
                    return out;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 64;
                }

                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    if (slot == SLOT_STAMP) {
                        // A stamp and nothing else.
                        return stack.is(STAMPS);
                    }
                    if (slot >= SLOT_MATERIAL && slot < SLOT_MATERIAL + MATERIAL_SLOTS) {
                        // Anything that is not a stamp: which recipes exist is
                        // decided by a datapack, and asking this question
                        // against the set of recipes would mean asking it anew
                        // on every attempt to insert.
                        return !stack.is(STAMPS);
                    }
                    // <b>The upgrade slots are closed from outside.</b> They
                    // are a setting, not a throughput: a sorting machine that
                    // shoved cards in would change, in passing, how fast the
                    // press runs.
                    return false;
                }
            };

    /** The inventory for the connectors all around. */
    public net.neoforged.neoforge.items.IItemHandler inventory() {
        return handler;
    }

    /** The slots as a container, so the screen can work with them. */
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
        // Only if it is present: the update packet to the client does not
        // carry the energy — it lives in the menu's ContainerData, and the
        // display does not need it in the block state. NeoForge, however,
        // answers a missing tag with an exception, and that costs the player
        // the connection the moment someone places a press.
        if (tag.contains("Energy")) {
            energy.deserializeNBT(registries, tag.get("Energy"));
        }
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
