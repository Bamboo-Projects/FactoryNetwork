package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.item.EntanglementItem;
import dev.devpanda.factorynetwork.network.BridgeRegistry;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Eine Quantum-Brücke: ein Ende einer Leitung ohne Kabel dazwischen.
 *
 * <p>In ihr steckt eine Hälfte einer Verschränkung. Die andere Hälfte steckt
 * in einer zweiten Brücke, irgendwo in derselben Welt — und die beiden
 * verbinden ihre Netze, als läge ein dichtes Kabel dazwischen.
 *
 * <p><b>Was sie nicht tut: suchen.</b> Sie meldet sich beim Laden in
 * {@link BridgeRegistry} an, unter der Nummer ihrer Hälfte. Eine Brücke, die
 * ihren Partner über die Welt suchen müsste, durchsuchte bei jeder Frage
 * Millionen Blöcke.
 */
public class BridgeBlockEntity extends BlockEntity implements Container {

    /** Ein Platz: eine Brücke trägt genau eine Hälfte. */
    public static final int SLOTS = 1;

    private final NonNullList<ItemStack> contents =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Unter welcher Nummer diese Brücke angemeldet ist.
     *
     * <p>Gemerkt und nicht aus dem Platz gelesen: Beim Abmelden ist die
     * Hälfte womöglich schon heraus, und dann fände die Abmeldung ihren
     * eigenen Eintrag nicht mehr — die Brücke bliebe als Geist im
     * Verzeichnis stehen.
     */
    private @Nullable UUID registered;

    public BridgeBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.BRIDGE.get(), pos, state);
    }

    /** Die Nummer der Hälfte, die hier steckt — oder {@code null}. */
    public @Nullable UUID pair() {
        return EntanglementItem.idOf(contents.get(0));
    }

    /** Die Gegenstelle, oder {@code null}: siehe {@link BridgeRegistry}. */
    public @Nullable BlockPos partner() {
        return level == null ? null : BridgeRegistry.partnerOf(level, worldPosition);
    }

    /**
     * Trägt die Anmeldung nach, wenn sich die Hälfte geändert hat.
     *
     * <p>Erst ab-, dann anmelden, und beides nur bei einer echten Änderung:
     * Ein Verzeichnis, das bei jedem {@code setChanged} neu geschrieben wird,
     * ist ein Verzeichnis, das man in jedem Tick anfasst.
     */
    private void syncRegistration() {
        if (level == null || level.isClientSide) {
            return;
        }
        UUID now = pair();
        if (java.util.Objects.equals(registered, now)) {
            return;
        }
        if (registered != null) {
            BridgeRegistry.remove(level, registered, worldPosition);
        }
        registered = now;
        if (now != null) {
            BridgeRegistry.add(level, now, worldPosition);
        }
        // Das Netz sieht anders aus, sobald eine Brücke auf- oder zugeht.
        ControllerRegistry.refreshAround(level, worldPosition);
        showLink();
    }

    /**
     * Trägt den Zustand ins Blockbild.
     *
     * <p>Auch an der Gegenstelle: Sie erfährt sonst nie, dass sie einen
     * Partner bekommen hat — sie wird ja nicht angefasst.
     */
    private void showLink() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos partner = partner();
        setLinked(worldPosition, partner != null);
        if (partner != null && level.isLoaded(partner)) {
            setLinked(partner, true);
        }
    }

    private void setLinked(BlockPos pos, boolean linked) {
        if (level == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock
                && state.getValue(dev.devpanda.factorynetwork.block.BridgeBlock.LINKED)
                        != linked) {
            level.setBlock(pos, state.setValue(
                    dev.devpanda.factorynetwork.block.BridgeBlock.LINKED, linked),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncRegistration();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && registered != null) {
            // Die Gegenstelle merken, bevor die Abmeldung sie unauffindbar
            // macht: Sie wird beim Abbau nicht angefasst und erführe sonst
            // nie, dass sie allein ist — ein Licht, hinter dem nichts mehr
            // steht.
            BlockPos partner = partner();
            BridgeRegistry.remove(level, registered, worldPosition);
            registered = null;
            if (partner != null && level.isLoaded(partner)) {
                setLinked(partner, false);
            }
            ControllerRegistry.refreshAround(level, worldPosition);
        }
        super.setRemoved();
    }

    // ---- Container ------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return contents.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return contents.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(contents, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = ContainerHelper.takeItem(contents, slot);
        setChanged();
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        contents.set(slot, stack);
        setChanged();
    }

    /** Nur eine Verschränkung passt hinein — ein Platz für alles wäre eine Kiste. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty() || EntanglementItem.idOf(stack) != null;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        contents.clear();
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        syncRegistration();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, contents, registries);
    }
}
