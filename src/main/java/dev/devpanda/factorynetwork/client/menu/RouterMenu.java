package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.network.Channels;
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
 * Das Fenster des Routers.
 *
 * <p>Am Block anzuklicken ist der schnelle Weg und bleibt — was man dort
 * einstellt, ist räumlich, und eine Seite anzuklicken ist eindeutiger als ein
 * Feld in einer Liste. <b>Aber man kommt oft nicht an alle sechs Seiten:</b>
 * Ein Router in einer Wand hat Flächen, die niemand erreicht. Dafür ist
 * dieses Fenster da.
 *
 * <p>Es hat keine Plätze — ein Router nimmt nichts auf. Was hin und her geht,
 * sind sechs Zahlen und ein Knopfdruck, und beides trägt Minecraft von sich
 * aus: {@link ContainerData} für die Zahlen, {@code clickMenuButton} für den
 * Druck.
 */
public class RouterMenu extends AbstractContainerMenu {

    /** Sechs Seiten, dann vier Bahnlasten, dann die Kapazität. */
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

    /** Welche Bahn diese Seite führt. */
    public int lane(Direction side) {
        return data.get(side.ordinal());
    }

    /** Was diese Bahn trägt. */
    public int load(int lane) {
        return lane >= 1 && lane <= RouterBlockEntity.LANES
                ? data.get(LOAD_OFFSET + lane - 1) : 0;
    }

    /** Was eine Bahn tragen kann. */
    public int capacity() {
        return data.get(CAPACITY_INDEX);
    }

    public String formatLoad(int lane) {
        return String.valueOf(load(lane)) + "/" + String.valueOf(capacity());
    }

    /** Die Knopfnummer für eine Seite und eine Bahn. */
    public static int buttonFor(Direction side, int lane) {
        return side.ordinal() * (RouterBlockEntity.LANES + 1) + lane;
    }

    /**
     * Ein Druck stellt eine Seite auf eine Bahn.
     *
     * <p>Serverseitig, wie jeder Knopf in einem Fenster: Der Client sagt nur,
     * welcher gedrückt wurde.
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
                // Sofort neu aufbauen, statt auf den Turnus zu warten: Fünf
                // Sekunden zwischen Klick und Wirkung sind zu lang, um noch
                // als Ursache erkannt zu werden.
                ControllerRegistry.refreshAround(level, pos);
            }
        });
        return true;
    }

    /** Ein Router nimmt nichts auf, also gibt es auch nichts zu schieben. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FnBlocks.ROUTER.get());
    }

    /** Die Zahlen, die der Router in das Fenster schiebt. */
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
