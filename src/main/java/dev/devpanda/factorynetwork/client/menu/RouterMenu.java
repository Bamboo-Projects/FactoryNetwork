package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The router's window.
 *
 * <p>Clicking on the block is the quick way and stays — what you set there is
 * spatial, and clicking a face is less ambiguous than a field in a list.
 * <b>But often you cannot reach all six faces:</b> a router in a wall has
 * faces no one can get at. That is what this window is for.
 *
 * <p>It has no slots — a router holds nothing. What travels back and forth
 * is six numbers and a button press, and Minecraft carries both on its own:
 * {@link ContainerData} for the numbers, {@code clickMenuButton} for the
 * press.
 */
public class RouterMenu extends AbstractContainerMenu {

    /** Six faces, then four lane loads, then the capacity. */
    public static final int DATA_SIZE = Direction.values().length + RouterBlockEntity.LANES + 1;
    private static final int LOAD_OFFSET = Direction.values().length;
    private static final int CAPACITY_INDEX = DATA_SIZE - 1;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public RouterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, new SimpleContainerData(DATA_SIZE), ContainerLevelAccess.NULL);
    }

    public RouterMenu(int id, ContainerData data, ContainerLevelAccess access) {
        super(FnMenus.ROUTER.get(), id);
        this.data = data;
        this.access = access;
        addDataSlots(data);
    }

    /** Which lane this face carries. */
    public int lane(Direction side) {
        return data.get(side.ordinal());
    }

    /** What this lane carries. */
    public int load(int lane) {
        return lane >= 1 && lane <= RouterBlockEntity.LANES
                ? data.get(LOAD_OFFSET + lane - 1) : 0;
    }

    /** What a lane can carry. */
    public int capacity() {
        return data.get(CAPACITY_INDEX);
    }

    public String formatLoad(int lane) {
        // In bytes per second, as everywhere: "0.4 of 1 KB/s" instead of "12/16".
        return dev.devpanda.factorynetwork.network.Bandwidth.usage(
                load(lane), capacity());
    }

    /** The button number for a face and a lane. */
    public static int buttonFor(Direction side, int lane) {
        return side.ordinal() * (RouterBlockEntity.LANES + 1) + lane;
    }

    /**
     * A press sets a face to a lane.
     *
     * <p>Server-side, like every button in a window: the client only says
     * which one was pressed.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int schritte = RouterBlockEntity.LANES + 1;
        int seite = id / schritte;
        int bahn = id % schritte;
        if (seite < 0 || seite >= Direction.values().length || bahn > RouterBlockEntity.LANES) {
            return false;
        }
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
                router.setLane(Direction.values()[seite], bahn);
                // Rebuild immediately instead of waiting for the cycle: five
                // seconds between click and effect are too long to still be
                // recognised as the cause.
                ControllerRegistry.refreshAround(level, pos);
            }
        });
        return true;
    }

    /** A router holds nothing, so there is nothing to move either. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FnBlocks.ROUTER.get());
    }

    /** The numbers the router pushes into the window. */
    public static ContainerData dataOf(RouterBlockEntity router,
                                       java.util.function.ToIntFunction<Integer> laneLoad,
                                       java.util.function.IntSupplier capacity) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index < LOAD_OFFSET) {
                    return router.lane(Direction.values()[index]);
                }
                if (index == CAPACITY_INDEX) {
                    return capacity.getAsInt();
                }
                return laneLoad.applyAsInt(index - LOAD_OFFSET + 1);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public static BlockPos noPosition() {
        return BlockPos.ZERO;
    }
}
