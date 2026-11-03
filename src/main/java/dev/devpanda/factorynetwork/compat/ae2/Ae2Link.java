package dev.devpanda.factorynetwork.compat.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.network.link.FluidStock;
import dev.devpanda.factorynetwork.network.link.ItemStock;
import dev.devpanda.factorynetwork.network.link.StorageLinkAdapter;
import dev.devpanda.factorynetwork.network.link.StorageLinks;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * The storage link on an ME network.
 *
 * <p><b>A machine in the ME network, not a hopper.</b> The link joins the
 * network with a node of its own, takes a channel and some power, and every
 * access goes through AE2 with the link as its source: security rules and
 * channel limits apply to it as to any AE2 machine. That is way 2 of
 * {@code docs/ae2.md}.
 *
 * <p>The stock is read from the storage service's cached inventory, by sort
 * ({@link KeyCounter#findFuzzy}): a transfer costs what it moves, not what
 * the network holds.
 */
public final class Ae2Link implements StorageLinkAdapter, IInWorldGridNodeHost, IActionHost {

    private final StorageLinkBlockEntity link;
    private final IManagedGridNode node;
    private final IActionSource source;
    private boolean created;

    private Ae2Link(StorageLinkBlockEntity link) {
        this.link = link;
        this.node = GridHelper.createManagedNode(this, Listener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(FnItems.STORAGE_LINK.get())
                .setTagName("factorynetwork_storage_link");
        this.source = IActionSource.ofMachine(this);
    }

    /** Called from the entry point, only when AE2 is loaded. */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        StorageLinks.register("ae2", Ae2Link::nextToAe2, Ae2Link::new);
        modBus.addListener(Ae2Link::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // AE2 finds in-world nodes through this capability. Only a link that
        // is attached to AE2 answers; one on another system stays invisible.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                FnBlockEntities.STORAGE_LINK.get(),
                (entity, context) -> entity.adapter() instanceof Ae2Link ae2 ? ae2 : null);
    }

    private static boolean nextToAe2(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && !(level.getBlockEntity(next) instanceof StorageLinkBlockEntity)
                    && GridHelper.getNodeHost(level, next) != null) {
                return true;
            }
        }
        return false;
    }

    // ---- StorageLinkAdapter ---------------------------------------------------

    @Override
    public String systemName() {
        return "Applied Energistics 2";
    }

    @Override
    public void tick() {
        // The node joins on the first tick after attaching: before that the
        // block entity may not be fully in the level yet.
        if (!created && link.getLevel() != null) {
            created = true;
            node.create(link.getLevel(), link.getBlockPos());
        }
    }

    @Override
    public void remove() {
        node.destroy();
    }

    private @Nullable MEStorage inventory() {
        if (!node.isActive()) {
            return null;
        }
        var grid = node.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    private @Nullable KeyCounter cached() {
        if (!node.isActive()) {
            return null;
        }
        var grid = node.getGrid();
        return grid == null ? null : grid.getStorageService().getCachedInventory();
    }

    @Override
    public @Nullable ItemStock items() {
        return node.isActive() ? items : null;
    }

    @Override
    public @Nullable FluidStock fluids() {
        return node.isActive() ? fluids : null;
    }

    private final ItemStock items = new ItemStock() {
        @Override
        public void find(List<Item> wanted, ObjLongConsumer<ItemKey> into) {
            findRaw(wanted, (raw, amount) -> into.accept(keyOf(raw), amount));
        }

        /** Hands over AE2's own keys: the conversion waits for {@link #keyOf}. */
        @Override
        public void findRaw(List<Item> wanted, ObjLongConsumer<Object> into) {
            KeyCounter stock = cached();
            if (stock == null) {
                return;
            }
            if (wanted.isEmpty()) {
                for (var entry : stock) {
                    if (entry.getKey() instanceof AEItemKey key && entry.getLongValue() > 0) {
                        into.accept(key, entry.getLongValue());
                    }
                }
                return;
            }
            for (Item item : wanted) {
                // Every variant of the item: a named or enchanted one is a
                // sort of its own in both systems.
                for (var entry : stock.findFuzzy(AEItemKey.of(item), FuzzyMode.IGNORE_ALL)) {
                    if (entry.getKey() instanceof AEItemKey key && key.getItem() == item
                            && entry.getLongValue() > 0) {
                        into.accept(key, entry.getLongValue());
                    }
                }
            }
        }

        @Override
        public ItemKey keyOf(Object raw) {
            return ItemKey.of(((AEItemKey) raw).toStack());
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            MEStorage inventory = inventory();
            return inventory == null ? 0 : inventory.insert(AEItemKey.of(key.toStack(1)), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            MEStorage inventory = inventory();
            return inventory == null ? 0 : inventory.extract(AEItemKey.of(key.toStack(1)), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }
    };

    private final FluidStock fluids = new FluidStock() {
        @Override
        public void find(List<Fluid> wanted, ObjLongConsumer<Fluid> into) {
            KeyCounter stock = cached();
            if (stock == null) {
                return;
            }
            if (wanted.isEmpty()) {
                for (var entry : stock) {
                    if (entry.getKey() instanceof AEFluidKey key && entry.getLongValue() > 0) {
                        into.accept(key.getFluid(), entry.getLongValue());
                    }
                }
                return;
            }
            for (Fluid fluid : wanted) {
                long amount = stock.get(AEFluidKey.of(fluid));
                if (amount > 0) {
                    into.accept(fluid, amount);
                }
            }
        }

        @Override
        public long insert(Fluid fluid, long amount, boolean simulate) {
            MEStorage inventory = inventory();
            return inventory == null ? 0 : inventory.insert(AEFluidKey.of(fluid), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }

        @Override
        public long extract(Fluid fluid, long amount, boolean simulate) {
            MEStorage inventory = inventory();
            return inventory == null ? 0 : inventory.extract(AEFluidKey.of(fluid), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }
    };

    // ---- AE2 ------------------------------------------------------------------

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return node.getNode();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return node.getNode();
    }

    /** AE2 asks the owner to save when the node changes; the link keeps nothing of it. */
    private enum Listener implements IGridNodeListener<Ae2Link> {
        INSTANCE;

        @Override
        public void onSaveChanges(Ae2Link owner, IGridNode node) {
            owner.link.setChanged();
        }
    }
}
