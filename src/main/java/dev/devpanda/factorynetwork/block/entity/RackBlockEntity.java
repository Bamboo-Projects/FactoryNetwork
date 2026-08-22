package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import dev.devpanda.factorynetwork.network.ServerBay;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Serverschrank mit zwölf Einschüben.
 *
 * <p>Jeder Einschub nimmt genau ein Rechenwerk, einen Speicher und einen
 * Datenträger auf — und trägt erst dann etwas bei, wenn alle drei stecken.
 * Der Schrank selbst kann nichts; er ist das Gestell, in dem die
 * Entscheidungen sitzen.
 *
 * <p>Die Plätze liegen als flache Liste, drei je Einschub, in der Reihenfolge
 * von {@link ServerPart}. Genau so liegen sie auch im Fenster, und genau so
 * rechnet {@link ServerBay} sie zusammen. Eine Liste von Einschüben, die
 * jeweils drei Plätze halten, wäre dieselbe Sache mit einer
 * Zwischenschicht — und {@link ShelfBlockEntity} führt sowieso eine flache
 * Liste.
 */
public class RackBlockEntity extends ShelfBlockEntity {

    /** So viele Server passen hinein. */
    public static final int BAYS = 12;

    /** Rechenwerk, Speicher, Datenträger. */
    public static final int PARTS_PER_BAY = ServerPart.values().length;

    public static final int SLOTS = BAYS * PARTS_PER_BAY;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.RACK.get(), pos, state, SLOTS);
    }

    /** Zu welchem Einschub ein Platz gehört. */
    public static int bayOf(int slot) {
        return slot / PARTS_PER_BAY;
    }

    /** Welche Art von Bauteil in diesen Platz gehört. */
    public static ServerPart partOf(int slot) {
        return ServerPart.values()[Math.floorMod(slot, PARTS_PER_BAY)];
    }

    /** Der Platz eines bestimmten Bauteils in einem bestimmten Einschub. */
    public static int slotOf(int bay, ServerPart part) {
        return bay * PARTS_PER_BAY + part.ordinal();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof ServerPartItem;
    }

    /**
     * Jeder Platz nimmt nur seine eigene Art.
     *
     * <p>Sonst läge ein Rechenwerk auf dem Datenträgerplatz und der Einschub
     * wäre voll, ohne zu laufen — ein Fehler, den man beim Ansehen nicht
     * findet, weil drei Bauteile drinstecken.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < SLOTS
                && ServerPartItem.partOf(stack) == partOf(slot);
    }

    /**
     * Ein Platz, ein Bauteil.
     *
     * <p>Vorher zählte der Schrank Stapel mit, und ein Stapel von sechzehn
     * Prozessoren auf einem Platz war sechzehnmal so viel Leistung. Damit
     * waren die Plätze keine Grenze mehr, sondern eine Formalität.
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public ShelfMenu.Layout layout() {
        return ShelfMenu.RACK;
    }

    public NonNullList<ItemStack> contents() {
        return parts();
    }

    /** Was in einem Einschub steckt. */
    public ServerBay bay(int bay) {
        if (bay < 0 || bay >= BAYS) {
            return ServerBay.EMPTY;
        }
        return ServerBay.of(
                getItem(slotOf(bay, ServerPart.CPU)),
                getItem(slotOf(bay, ServerPart.RAM)),
                getItem(slotOf(bay, ServerPart.DISK)));
    }

    /** Die Summe der vollständigen Einschübe. */
    public ServerBay capacity() {
        ServerBay total = ServerBay.EMPTY;
        for (int bay = 0; bay < BAYS; bay++) {
            total = total.plus(bay(bay).contribution());
        }
        return total;
    }

    /** Wie viele Einschübe laufen. */
    public int runningBays() {
        int count = 0;
        for (int bay = 0; bay < BAYS; bay++) {
            if (bay(bay).complete()) {
                count++;
            }
        }
        return count;
    }

    /** Wie viele Einschübe etwas enthalten, aber nicht laufen. */
    public int incompleteBays() {
        int count = 0;
        for (int bay = 0; bay < BAYS; bay++) {
            ServerBay contents = bay(bay);
            if (contents.occupied() && !contents.complete()) {
                count++;
            }
        }
        return count;
    }

    /** Wie viele gleichzeitige Abläufe dieser Schrank trägt. */
    public int threads() {
        return capacity().cpu();
    }
}
