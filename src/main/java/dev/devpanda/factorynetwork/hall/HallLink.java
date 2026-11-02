package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.network.link.FluidStock;
import dev.devpanda.factorynetwork.network.link.ItemStock;
import dev.devpanda.factorynetwork.network.link.StorageLinkAdapter;
import dev.devpanda.factorynetwork.network.link.StorageLinks;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * A storage link next to a hall block reaches the store of the network
 * inside the hall.
 *
 * <p>That is how the hall is coupled: the factory inside has its own
 * controller, and the network outside fills and empties its store through a
 * link at the door. Only this way round — the inside cannot reach out — so
 * two networks can never ask each other in a circle.
 */
public final class HallLink implements StorageLinkAdapter {

    private final StorageLinkBlockEntity link;

    private HallLink(StorageLinkBlockEntity link) {
        this.link = link;
    }

    /** Called from the entry point. */
    public static void register() {
        StorageLinks.register("factorynetwork_hall", HallLink::nextToHall, HallLink::new);
    }

    private static boolean nextToHall(Level level, BlockPos pos) {
        return door(level, pos) != null;
    }

    private static @Nullable HallBlockEntity door(Level level, BlockPos pos) {
        if (level.dimension() == Halls.DIMENSION) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof HallBlockEntity door
                    && door.hall() != HallBlockEntity.NONE) {
                return door;
            }
        }
        return null;
    }

    /** The controller inside the hall; the first one found, if there are several. */
    private @Nullable NetworkStorage store() {
        Level level = link.getLevel();
        HallBlockEntity door = level == null ? null : door(level, link.getBlockPos());
        if (door == null || level.getServer() == null) {
            return null;
        }
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        Halls.Entry entry = Halls.of(level.getServer()).get(door.hall());
        if (hall == null || entry == null) {
            return null;
        }
        for (BlockPos candidate : ControllerRegistry.in(hall)) {
            if (Halls.inside(door.hall(), entry.size(), candidate) && hall.isLoaded(candidate)
                    && hall.getBlockEntity(candidate) instanceof ControllerBlockEntity controller) {
                return controller.storage();
            }
        }
        return null;
    }

    @Override
    public String systemName() {
        return "Fabrikhalle";
    }

    @Override
    public @Nullable ItemStock items() {
        NetworkStorage store = store();
        return store == null ? null : new Items(store);
    }

    @Override
    public @Nullable FluidStock fluids() {
        return null;
    }

    @Override
    public void remove() {
    }

    private record Items(NetworkStorage store) implements ItemStock {

        @Override
        public void find(List<Item> items, ObjLongConsumer<ItemKey> into) {
            store.contents().forEach((key, amount) -> {
                if (items.isEmpty() || items.contains(key.item())) {
                    into.accept(key, amount);
                }
            });
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            if (simulate) {
                return store.room(key, amount);
            }
            return amount - store.insert(key, amount);
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            if (simulate) {
                return Math.min(amount, store.count(key));
            }
            return store.extract(key, amount);
        }
    }
}
