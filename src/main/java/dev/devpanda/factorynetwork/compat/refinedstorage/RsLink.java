package dev.devpanda.factorynetwork.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageListener;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.network.link.FluidStock;
import dev.devpanda.factorynetwork.network.link.ItemStock;
import dev.devpanda.factorynetwork.network.link.StorageLinkAdapter;
import dev.devpanda.factorynetwork.network.link.StorageLinks;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * The storage link on a Refined Storage network.
 *
 * <p>The link joins the network as a node of its own and draws energy like
 * any RS device. Accesses carry the link as their actor.
 *
 * <p><b>Asked by sort, through an index of our own.</b> RS can answer the
 * amount of one exact resource, but not "every variant of this item"
 * without walking its whole list. The link therefore keeps an index from
 * item to resources, built once when it joins a network and kept current by
 * RS's own change listener, so that a transfer does not walk the network.
 */
public final class RsLink implements StorageLinkAdapter {

    /** Energy per tick, in RS units; the external storage of RS uses about as much. */
    private static final long ENERGY = 2;

    private static final Actor ACTOR = () -> "FactoryNetwork";

    private final StorageLinkBlockEntity link;
    private final SimpleNetworkNode node = new SimpleNetworkNode(ENERGY);
    private final NetworkNodeContainerProvider containers;
    private boolean initialized;

    /** The network whose storage the index mirrors, and the index. */
    private @Nullable StorageNetworkComponent indexed;
    private final Map<Item, Map<ItemKey, Long>> itemIndex = new HashMap<>();
    private final Map<Fluid, Long> fluidIndex = new HashMap<>();

    private final RootStorageListener listener = this::changed;

    private RsLink(StorageLinkBlockEntity link) {
        this.link = link;
        this.containers = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        this.containers.addContainer(RefinedStorageApi.INSTANCE
                .createNetworkNodeContainer(link, node).build());
    }

    /** Called from the entry point, only when Refined Storage is loaded. */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        StorageLinks.register("refinedstorage", RsLink::nextToRs, RsLink::new);
        modBus.addListener(RsLink::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                FnBlockEntities.STORAGE_LINK.get(),
                (entity, side) -> entity.adapter() instanceof RsLink rs ? rs.containers : null);
    }

    private static boolean nextToRs(Level level, BlockPos pos) {
        var capability = RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && !(level.getBlockEntity(next) instanceof StorageLinkBlockEntity)
                    && level.getCapability(capability, next, direction.getOpposite()) != null) {
                return true;
            }
        }
        return false;
    }

    // ---- StorageLinkAdapter ---------------------------------------------------

    @Override
    public String systemName() {
        return "Refined Storage";
    }

    @Override
    public void tick() {
        if (!initialized && link.getLevel() != null) {
            initialized = true;
            containers.initialize(link.getLevel(), null);
        }
        // RS leaves activeness and energy to the block entity: active while
        // the network has the energy for this tick, and the energy is drawn
        // in doWork, as RS's own devices do it.
        Network network = node.getNetwork();
        boolean active = network != null && (!RefinedStorageApi.INSTANCE.isEnergyRequired()
                || network.getComponent(com.refinedmods.refinedstorage.api.network.energy
                        .EnergyNetworkComponent.class).getStored() >= ENERGY);
        if (active != node.isActive()) {
            node.setActive(active);
        }
        node.doWork();
        // The node can move to another network when cables change; the index
        // follows it.
        StorageNetworkComponent current = storage();
        if (current != indexed) {
            reindex(current);
        }
    }

    @Override
    public void remove() {
        reindex(null);
        containers.remove(link.getLevel());
    }

    private @Nullable StorageNetworkComponent storage() {
        Network network = node.getNetwork();
        return network == null || !node.isActive() ? null
                : network.getComponent(StorageNetworkComponent.class);
    }

    private void reindex(@Nullable StorageNetworkComponent storage) {
        if (indexed != null) {
            indexed.removeListener(listener);
        }
        itemIndex.clear();
        fluidIndex.clear();
        indexed = storage;
        if (storage == null) {
            return;
        }
        for (var entry : storage.getAll()) {
            record(entry.resource(), entry.amount());
        }
        storage.addListener(listener);
    }

    private void changed(MutableResourceList.OperationResult change) {
        record(change.resource(), change.available() ? change.amount() : 0);
    }

    private void record(Object resource, long amount) {
        if (resource instanceof ItemResource item) {
            ItemKey key = ItemKey.of(item.item(), item.components());
            if (amount > 0) {
                itemIndex.computeIfAbsent(item.item(), ignored -> new HashMap<>()).put(key, amount);
            } else {
                Map<ItemKey, Long> sorts = itemIndex.get(item.item());
                if (sorts != null) {
                    sorts.remove(key);
                    if (sorts.isEmpty()) {
                        itemIndex.remove(item.item());
                    }
                }
            }
        } else if (resource instanceof FluidResource fluid && fluid.components().isEmpty()) {
            if (amount > 0) {
                fluidIndex.put(fluid.fluid(), amount);
            } else {
                fluidIndex.remove(fluid.fluid());
            }
        }
    }

    @Override
    public @Nullable ItemStock items() {
        return indexed == null ? null : items;
    }

    @Override
    public @Nullable FluidStock fluids() {
        return indexed == null ? null : fluids;
    }

    private final ItemStock items = new ItemStock() {
        @Override
        public void find(List<Item> wanted, ObjLongConsumer<ItemKey> into) {
            if (wanted.isEmpty()) {
                itemIndex.values().forEach(sorts -> sorts.forEach(into::accept));
                return;
            }
            for (Item item : wanted) {
                Map<ItemKey, Long> sorts = itemIndex.get(item);
                if (sorts != null) {
                    sorts.forEach(into::accept);
                }
            }
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            StorageNetworkComponent storage = indexed;
            return storage == null ? 0 : storage.insert(resourceOf(key), amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE, ACTOR);
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            StorageNetworkComponent storage = indexed;
            return storage == null ? 0 : storage.extract(resourceOf(key), amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE, ACTOR);
        }
    };

    private static ItemResource resourceOf(ItemKey key) {
        return ItemResource.ofItemStack(key.toStack(1));
    }

    private final FluidStock fluids = new FluidStock() {
        @Override
        public void find(List<Fluid> wanted, ObjLongConsumer<Fluid> into) {
            if (wanted.isEmpty()) {
                fluidIndex.forEach(into::accept);
                return;
            }
            for (Fluid fluid : wanted) {
                Long amount = fluidIndex.get(fluid);
                if (amount != null) {
                    into.accept(fluid, amount);
                }
            }
        }

        @Override
        public long insert(Fluid fluid, long amount, boolean simulate) {
            StorageNetworkComponent storage = indexed;
            return storage == null ? 0 : storage.insert(new FluidResource(fluid), amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE, ACTOR);
        }

        @Override
        public long extract(Fluid fluid, long amount, boolean simulate) {
            StorageNetworkComponent storage = indexed;
            return storage == null ? 0 : storage.extract(new FluidResource(fluid), amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE, ACTOR);
        }
    };
}
