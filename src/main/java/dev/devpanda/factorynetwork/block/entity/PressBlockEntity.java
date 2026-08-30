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

    /**
     * Die Materialplätze.
     *
     * <p>Drei, weil ein Rezept höchstens drei Zutaten fordern darf: Ein
     * Prozessor braucht Redstone, Kupfer und einen Träger, und das ist die
     * dickste Rechnung, die eine Presse führen soll. Wer mehr braucht,
     * braucht keine Presse, sondern eine Fertigungsstraße.
     */
    public static final int SLOT_MATERIAL = 1;
    public static final int MATERIAL_SLOTS = PressRecipe.MOST_MATERIALS;

    public static final int SLOT_RESULT = SLOT_MATERIAL + MATERIAL_SLOTS;

    /**
     * Die Steckplätze für Ausbauten.
     *
     * <p>Fünf, und sie zählen stapelweise: Was sie ausmachen, rechnet
     * {@link dev.devpanda.factorynetwork.upgrade.Tuning}, und die deckelt bei
     * acht Karten je Art.
     */
    public static final int SLOT_UPGRADE = SLOT_RESULT + 1;
    public static final int UPGRADE_SLOTS = 5;

    public static final int SLOTS = SLOT_UPGRADE + UPGRADE_SLOTS;

    /** Fasst so viel, dass ein Vorgang durchläuft, ohne am Tropf zu hängen. */
    public static final int CAPACITY = 40_000;

    /** Höchstens so viel je Tick — sonst wäre die Zeit im Rezept sinnlos. */
    private static final int MAX_INPUT = 2_000;

    /**
     * Was in den Stempelplatz darf.
     *
     * <p>Ein Tag und keine feste Liste: Ein Datenpaket darf Rezepte
     * mitbringen, und ein eigener Stempel dazu soll sich einlegen lassen,
     * ohne dass jemand die Mod anfasst.
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> STAMPS =
            net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID, "stamps"));

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

    /** Was in den Materialplätzen liegt, in ihrer Reihenfolge. */
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
     * Was in den Steckplätzen steckt.
     *
     * <p>Jedes Stück eines Stapels zählt — dieselbe Regel wie bei den
     * Reichweitenkarten, und dieselbe Klasse rechnet sie.
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

    /** Das Rezept, wie diese Presse mit ihren Karten es ausführt. */
    public dev.devpanda.factorynetwork.upgrade.Tuned tuned(PressRecipe recipe) {
        return dev.devpanda.factorynetwork.upgrade.Tuning.of(
                loadout(), recipe.ticks(), recipe.energy());
    }

    /**
     * Reicht das Material, und passt das Ergebnis in den Ausgabeplatz?
     *
     * <p>Beides gegen die Stückzahl gerechnet: Eine Presse mit Stapelkarten
     * verbraucht das Mehrfache und legt das Mehrfache ab. Wer nur das Rezept
     * prüft, fängt einen Durchlauf an, den er nicht zu Ende bringt.
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
            // Zwischen Prüfung und Abschluss hat jemand ausgeräumt.
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
        // Jede Zutat aus dem Platz, der sie erfüllt hat — nicht der Reihe
        // nach: Die Reihenfolge in der Presse ist eine andere als im Rezept.
        for (int i = 0; i < from.length; i++) {
            item(SLOT_MATERIAL + from[i])
                    .shrink(recipe.materials().get(i).count() * batch);
        }
        // Der Stempel bleibt — er ist Werkzeug, nicht Zutat.
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

    /**
     * Das Inventar, das ein Anschluss sieht.
     *
     * <p><b>Ohne das ist die Presse keine Maschine, sondern ein Möbelstück.</b>
     * Sie nahm Strom an, seit es sie gibt — aber kein Anschluss fand je ein
     * Inventar an ihr, und damit konnte kein Worker und kein {@code move} ihr
     * einen Eisenbarren geben. Bei einer Mod, deren Zweck Automatisierung
     * ist, war das die Lücke unter allen anderen.
     *
     * <p><b>Die Plätze haben verschiedene Regeln</b>, und deshalb steht hier
     * ein eigener Handler statt eines Wrappers um den Container: Der Stempel
     * nimmt nur Stempel, das Material nimmt keinen Stempel, und aus dem
     * Ergebnis wird nur genommen. Ein Wrapper ohne Regeln ließe eine
     * Sortiermaschine ihren ganzen Inhalt in die Presse schieben.
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
                    // <b>Nur das Ergebnis geht heraus.</b> Wer den Stempel
                    // abziehen dürfte, hätte eine Presse, die sich selbst
                    // entwaffnet — und das Material gehört der Maschine,
                    // sobald es drin liegt.
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
                        // Ein Stempel und sonst nichts.
                        return stack.is(STAMPS);
                    }
                    if (slot >= SLOT_MATERIAL && slot < SLOT_MATERIAL + MATERIAL_SLOTS) {
                        // Alles, was kein Stempel ist: Welche Rezepte es gibt,
                        // entscheidet ein Datenpaket, und diese Frage gegen den
                        // Rezeptbestand zu stellen hieße, sie bei jedem
                        // Einlegeversuch neu zu stellen.
                        return !stack.is(STAMPS);
                    }
                    // <b>Die Steckplätze sind von außen zu.</b> Sie sind eine
                    // Einstellung und kein Durchlauf: Eine Sortiermaschine,
                    // die Karten hineinschiebt, änderte im Vorbeigehen, wie
                    // schnell die Presse läuft.
                    return false;
                }
            };

    /** Das Inventar für die Anschlüsse ringsum. */
    public net.neoforged.neoforge.items.IItemHandler inventory() {
        return handler;
    }

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
        // Nur wenn es dasteht: Das Update-Paket an den Client trägt die
        // Energie nicht mit — sie steht in der ContainerData des Menüs, und
        // die Anzeige braucht sie im Blockzustand nicht. NeoForge antwortet
        // auf ein fehlendes Tag aber mit einer Ausnahme, und die kostet den
        // Spieler die Verbindung, sobald jemand eine Presse setzt.
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
