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
 * Die Bahnzuordnung eines Routers: je Seite eine Zahl.
 *
 * <p>Gleiche Bahn heißt verbunden, verschiedene Bahnen kreuzen sich
 * berührungslos, {@link #OFF} heißt abgeklemmt. Damit ersetzt ein einziger
 * Block, was beim dünnen Kabel das Bündeln wäre — beim dicken ist im Block
 * kein Platz für vier Stränge nebeneinander.
 *
 * <p>Die Zuordnung steht in der BlockEntity und nicht im Blockzustand: Sechs
 * Seiten mit je fünf Werten wären 15625 Zustände, und die legt Minecraft
 * alle beim Start an.
 */
public class RouterBlockEntity extends BlockEntity {

    /** Diese Seite ist abgeklemmt: Es geht nichts hinein und nichts heraus. */
    public static final int OFF = 0;

    /**
     * Vier Bahnen.
     *
     * <p>Mehr Kreuzungen in einem einzigen Block sind nicht mehr zu lesen —
     * bei sechs Seiten und sechs Bahnen wäre fast jede Seite für sich, und
     * dafür braucht es keinen Block.
     */
    public static final int LANES = 4;

    private static final String KEY_LANES = "Lanes";

    /**
     * Bahn je Seite, in der Reihenfolge von {@link Direction#values()}.
     *
     * <p>Frisch gesetzt liegt alles auf Bahn eins: Ein Router, den man
     * hinstellt und nicht anfasst, verhält sich wie ein Stück Kabel. Wer
     * trennen will, sagt es — nicht umgekehrt.
     */
    private final byte[] lanes = new byte[Direction.values().length];

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.ROUTER.get(), pos, state);
        java.util.Arrays.fill(lanes, (byte) 1);
    }

    public int lane(Direction side) {
        return lanes[side.ordinal()];
    }

    public void setLane(Direction side, int lane) {
        lanes[side.ordinal()] = (byte) Math.max(OFF, Math.min(LANES, lane));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Schaltet eine Seite eine Bahn weiter: 1, 2, 3, 4, aus, wieder 1.
     *
     * <p>Nur vorwärts. Rückwärts über die Schleichtaste wäre schneller am
     * Ziel, aber die Schleichtaste ist beim Anklicken eines Blocks schon
     * belegt, und fünf Klicks bis zurück sind auszuhalten.
     */
    public int cycle(Direction side) {
        int next = lane(side) + 1;
        if (next > LANES) {
            next = OFF;
        }
        setLane(side, next);
        return next;
    }

    /**
     * Welche Bahn diese Seite führt — null, wenn dort gar kein Router steht.
     *
     * <p>Der Graph fragt so, weil er über die Welt läuft und nicht über
     * BlockEntities: Für ihn ist eine abgeklemmte Seite dasselbe wie gar
     * kein Router.
     */
    public static int laneAt(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RouterBlockEntity router
                ? router.lane(side) : OFF;
    }

    /**
     * Das Fenster zum Router.
     *
     * <p>Die Bahnlasten holt es sich beim Controller, der das Netz kennt —
     * der Router selbst weiß nur, welche Seite auf welcher Bahn liegt.
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
        return dev.devpanda.factorynetwork.network.ControllerRegistry
                .owning(level, worldPosition)
                .map(controller -> controller.graph().laneLoad(worldPosition, lane))
                .orElse(0);
    }

    private int laneCapacity() {
        return dev.devpanda.factorynetwork.network.Channels.quarters(
                dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE);
    }

    /** Wie viele Seiten überhaupt angeschlossen sind. */
    public int connectedSides() {
        int count = 0;
        for (byte lane : lanes) {
            if (lane != OFF) {
                count++;
            }
        }
        return count;
    }

    /** Wie viele verschiedene Bahnen der Router gerade führt. */
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
            // Kürzere Felder aus älteren Ständen sollen nicht abstürzen; was
            // fehlt, bleibt auf der Vorgabe.
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
