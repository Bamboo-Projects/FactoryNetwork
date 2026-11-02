package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.network.link.FluidStock;
import dev.devpanda.factorynetwork.network.link.FluidStockView;
import dev.devpanda.factorynetwork.network.link.ItemStock;
import dev.devpanda.factorynetwork.network.link.ItemStockView;
import dev.devpanda.factorynetwork.network.link.StorageLinkAdapter;
import dev.devpanda.factorynetwork.network.link.StorageLinks;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The storage link: a foreign storage system as one device of the network.
 *
 * <p>A connector pointed at it sees the whole stock of the system behind it,
 * asked by sort (see {@link dev.devpanda.factorynetwork.network.link.KeyedItems}).
 * Which system that is, the link finds out itself from its neighbours; the
 * adapters for AE2 and the others live under {@code compat}.
 */
public class StorageLinkBlockEntity extends BlockEntity {

    /** How often a link without a system looks around again, in ticks. */
    private static final int SEARCH_INTERVAL = 20;

    private @Nullable StorageLinkAdapter adapter;
    private boolean searchNow = true;
    private int sinceSearch;

    public StorageLinkBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.STORAGE_LINK.get(), pos, state);
    }

    /** The system behind the link, or {@code null} while there is none. */
    public @Nullable StorageLinkAdapter adapter() {
        return adapter;
    }

    /** All items of the system as slots, or {@code null}. */
    @SuppressWarnings("removal")
    public net.neoforged.neoforge.items.@Nullable IItemHandler itemView() {
        ItemStock stock = adapter == null ? null : adapter.items();
        return stock == null ? null : new ItemStockView(stock, List.of());
    }

    /** All fluids of the system as tanks, or {@code null}. */
    @SuppressWarnings("removal")
    public net.neoforged.neoforge.fluids.capability.@Nullable IFluidHandler fluidView() {
        FluidStock stock = adapter == null ? null : adapter.fluids();
        return stock == null ? null : new FluidStockView(stock, List.of());
    }

    /** A neighbour changed: maybe a system arrived or left. */
    public void neighbourChanged() {
        searchNow = true;
    }

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (adapter == null) {
            if (searchNow || ++sinceSearch >= SEARCH_INTERVAL) {
                searchNow = false;
                sinceSearch = 0;
                var system = StorageLinks.at(level, worldPosition);
                if (system != null) {
                    adapter = system.attach().apply(this);
                }
            }
            return;
        }
        adapter.tick();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (adapter != null) {
            adapter.remove();
            adapter = null;
        }
    }
}
