package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a router lets through per side.
 *
 * <p><b>The fibre-optic picture.</b> One line carries all colours; the router
 * pulls out individual ones. A side without a filter is the connection to the
 * trunk — there everything passes. A side with a colour taps exactly that
 * one.
 *
 * <p><b>Until 29.08. it was the other way round.</b> The router carried lane
 * numbers and was colour-neutral: whatever met on one lane counted as
 * connected. It was a mixer, not a splitter — two separate subnetworks grew
 * together across it.
 *
 * <p>It can still do the old job: two sides set to the same colour are
 * connected, different ones cross without touching. Crossing is thereby a
 * special case of filtering.
 *
 * <p>The assignment lives in the BlockEntity and not in the block state: six
 * sides with five values each would be 15625 states, and Minecraft creates
 * all of them at startup.
 */
public class RouterBlockEntity extends BlockEntity {

    /** This side is switched off: nothing goes in and nothing comes out. */
    public static final int OFF = 0;

    /**
     * Four lanes.
     *
     * <p>More crossings in a single block can no longer be read — with six
     * sides and six lanes almost every side would be on its own, and for that
     * you need no block.
     */
    /**
     * This side lets everything through — the connection to the trunk.
     *
     * <p>The value is the old lane 1: a router from an older world therefore
     * has a side that lets everything through instead of one on lane 1. That
     * is the more compatible default — it goes on connecting what it
     * connected.
     */
    public static final int ALL = 1;

    /** This many settings exist per side: off, all, and one per colour. */
    public static final int LANES = ALL
            + dev.devpanda.factorynetwork.block.CableColour.values().length;

    private static final String KEY_LANES = "Lanes";

    /**
     * Lane per side, in the order of {@link Direction#values()}.
     *
     * <p>Freshly placed, everything lies on lane one: a router you set down
     * and do not touch behaves like a piece of cable. Whoever wants to
     * separate says so — not the other way round.
     */
    private final byte[] lanes = new byte[Direction.values().length];

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.ROUTER.get(), pos, state);
        java.util.Arrays.fill(lanes, (byte) 1);
    }

    /**
     * What this side lets through.
     *
     * <p>{@code null} means <b>everything</b> — the side toward the trunk. A
     * colour means: only that one. Whether the side is on at all is asked of
     * {@link #isOff}.
     */
    public dev.devpanda.factorynetwork.block.CableColour filter(Direction side) {
        int wert = lanes[side.ordinal()];
        if (wert == OFF || wert == ALL) {
            return null;
        }
        // The colour values start at two, because one already means "all".
        // A router from a world before 29.08. therefore has colours instead
        // of lanes — the separation stays the same, only it is called
        // something else.
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        return farben[Math.min(wert - 2, farben.length - 1)];
    }

    /**
     * The raw setting of this side.
     *
     * <p>For display and storage: off, all, or a colour as a number. Whoever
     * wants to know <i>what</i> passes asks {@link #filter}.
     */
    public int lane(Direction side) {
        return lanes[side.ordinal()];
    }

    /** Is this side switched off? */
    public boolean isOff(Direction side) {
        return lanes[side.ordinal()] == OFF;
    }

    /** Sets the filter: {@code null} for everything. */
    public void setFilter(Direction side,
                          dev.devpanda.factorynetwork.block.CableColour colour) {
        lanes[side.ordinal()] = (byte) (colour == null ? ALL : colour.ordinal() + 2);
        update();
    }

    /**
     * Sets the raw setting.
     *
     * <p>For the screen that clicks through the values, and for storage.
     * Whoever means a colour uses {@link #setFilter}.
     */
    public void setLane(Direction side, int lane) {
        lanes[side.ordinal()] = (byte) Math.max(OFF, Math.min(LANES, lane));
        update();
    }

    /** Switches this side off. */
    public void turnOff(Direction side) {
        lanes[side.ordinal()] = (byte) OFF;
        update();
    }

    private void update() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Advances a side by one lane: 1, 2, 3, 4, off, then 1 again.
     *
     * <p>Forward only. Backward via the sneak key would reach the goal faster,
     * but the sneak key is already taken when clicking a block, and five
     * clicks to get back around are bearable.
     */
    public int cycle(Direction side) {
        int next = lanes[side.ordinal()] + 1;
        if (next > LANES) {
            next = OFF;
        }
        lanes[side.ordinal()] = (byte) next;
        update();
        return next;
    }

    /**
     * Which lane this side carries — zero if there is no router there at all.
     *
     * <p>The graph asks this way because it runs over the world and not over
     * BlockEntities: to it, a switched-off side is the same as no router at
     * all.
     */
    public static int laneAt(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RouterBlockEntity router
                ? router.lanes[side.ordinal()] : OFF;
    }

    /**
     * What this side lets through — for the graph that runs over the world.
     *
     * <p>Returns {@code null} when everything passes <b>or</b> when there is
     * no router there. Whether the side is open at all is asked by the graph
     * with {@link #laneAt} — two questions, two answers.
     */
    public static dev.devpanda.factorynetwork.block.CableColour filterAt(
            BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RouterBlockEntity router
                ? router.filter(side) : null;
    }

    /**
     * The screen for the router.
     *
     * <p>It fetches the lane loads from the controller, which knows the
     * network — the router itself only knows which side lies on which lane.
     */
    public net.minecraft.world.MenuProvider menu() {
        return new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, player) -> new dev.devpanda.factorynetwork.client.menu.RouterMenu(
                        id,
                        dev.devpanda.factorynetwork.client.menu.RouterMenu.dataOf(this,
                                this::laneLoad, this::laneCapacity),
                        net.minecraft.world.inventory.ContainerLevelAccess.create(
                                level, worldPosition)),
                getBlockState().getBlock().getName());
    }

    private int laneLoad(int lane) {
        if (level == null) {
            return 0;
        }
        // Since 29.08. there is no longer any channel load — what a lane
        // carries is its throughput, and that does not depend on how many
        // devices lie behind it.
        return dev.devpanda.factorynetwork.network.Bandwidth.CABLE;
    }

    /** What a lane carries: as much as a solid cable. */
    private int laneCapacity() {
        return dev.devpanda.factorynetwork.network.Bandwidth.CABLE;
    }

    /** How many sides are connected at all. */
    public int connectedSides() {
        int count = 0;
        for (byte lane : lanes) {
            if (lane != OFF) {
                count++;
            }
        }
        return count;
    }

    /** How many different lanes the router currently carries. */
    public int usedLanes() {
        boolean[] seen = new boolean[LANES + 1];
        for (byte lane : lanes) {
            seen[lane] = true;
        }
        int count = 0;
        for (int lane = 1; lane <= LANES; lane++) {
            if (seen[lane]) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] stored = tag.getByteArray(KEY_LANES);
        for (int i = 0; i < lanes.length; i++) {
            // Shorter arrays from older saves should not crash; what is
            // missing stays at the default.
            if (i < stored.length) {
                lanes[i] = (byte) Math.max(OFF, Math.min(LANES, stored[i]));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray(KEY_LANES, lanes.clone());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
