package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.item.ServerChassis;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import dev.devpanda.factorynetwork.network.ServerBay;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Serverschrank mit zwölf Einschüben.
 *
 * <p>In jeden Einschub gehört ein <b>Servergehäuse</b>, und erst dann öffnen
 * sich daneben die drei Plätze für Rechenwerk, Speicher und Datenträger. Ein
 * Einschub trägt erst etwas bei, wenn alle vier stecken.
 *
 * <p>Die Plätze liegen als flache Liste, vier je Einschub: erst das Gehäuse,
 * dann die drei Bauteile in der Reihenfolge von {@link ServerPart}. Genau so
 * liegen sie auch im Fenster. Eine Liste von Einschüben, die jeweils vier
 * Plätze halten, wäre dieselbe Sache mit einer Zwischenschicht — und
 * {@link ShelfBlockEntity} führt sowieso eine flache Liste.
 *
 * <p><b>Das Gehäuse nimmt seine Bauteile mit.</b> Wird es herausgezogen,
 * wandern sie in den Gegenstand; wird ein bestücktes hineingesteckt, kommen
 * sie heraus in die Plätze. Damit ist ein fertiger Server tragbar, ohne dass
 * man je einen Gegenstand im Rucksack aufmachen müsste.
 */
public class RackBlockEntity extends ShelfBlockEntity {

    /** So viele Server passen hinein. */
    public static final int BAYS = 12;

    /** Rechenwerk, Speicher, Datenträger. */
    public static final int PARTS_PER_BAY = ServerPart.values().length;

    /** Und davor das Gehäuse. */
    public static final int SLOTS_PER_BAY = PARTS_PER_BAY + 1;

    public static final int SLOTS = BAYS * SLOTS_PER_BAY;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.RACK.get(), pos, state, SLOTS);
    }

    /** Zu welchem Einschub ein Platz gehört. */
    public static int bayOf(int slot) {
        return slot / SLOTS_PER_BAY;
    }

    /** Der Gehäuseplatz eines Einschubs — der erste. */
    public static int chassisSlot(int bay) {
        return bay * SLOTS_PER_BAY;
    }

    public static boolean isChassisSlot(int slot) {
        return Math.floorMod(slot, SLOTS_PER_BAY) == 0;
    }

    /**
     * Welche Art von Bauteil in diesen Platz gehört, oder {@code null} für
     * den Gehäuseplatz.
     */
    public static ServerPart partOf(int slot) {
        int within = Math.floorMod(slot, SLOTS_PER_BAY);
        return within == 0 ? null : ServerPart.values()[within - 1];
    }

    /** Der Platz eines bestimmten Bauteils in einem bestimmten Einschub. */
    public static int slotOf(int bay, ServerPart part) {
        return chassisSlot(bay) + 1 + part.ordinal();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return ServerChassis.is(stack) || stack.getItem() instanceof ServerPartItem;
    }

    /**
     * Jeder Platz nimmt nur, was hineingehört — und Bauteile nur, wenn ein
     * Gehäuse dasteht.
     *
     * <p>Die zweite Regel macht das Gehäuse zu dem, was es sein soll. Ohne
     * sie wären die drei Plätze schon der Server, und das Gehäuse wäre ein
     * Gegenstand, den man kauft und der nichts ändert.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return false;
        }
        if (isChassisSlot(slot)) {
            // Auch ein bestücktes Gehäuse: Es packt beim Einsetzen aus.
            return ServerChassis.is(stack);
        }
        return ServerPartItem.partOf(stack) == partOf(slot)
                && !getItem(chassisSlot(bayOf(slot))).isEmpty();
    }

    /**
     * Ein Platz, ein Gegenstand.
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

    // ---- Das Gehäuse nimmt mit, was drinsteckt ----------------------------

    /**
     * Bevor ein Gehäuse den Platz verlässt, packt es ein.
     *
     * <p>Diese Stelle ist der einzige Weg nach draußen: {@code removeItem}
     * geht über {@code setItem}, und das ruft hier herein, <b>solange der
     * alte Gegenstand noch dasteht</b>. Der Aufrufer bekommt danach genau
     * dieses Stück Blech in die Hand — mit den Bauteilen darin.
     */
    @Override
    protected void beforeSlotChange(int slot) {
        if (!isChassisSlot(slot)) {
            return;
        }
        ItemStack chassis = getItem(slot);
        if (!chassis.isEmpty()) {
            packInto(chassis, bayOf(slot));
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (isChassisSlot(slot) && !stack.isEmpty()) {
            unpackFrom(stack, bayOf(slot));
        }
    }

    /**
     * Schreibt die drei Bauteile eines Einschubs in das Gehäuse und leert
     * die Plätze.
     *
     * <p><b>Geschrieben wird direkt in die Liste</b> und nicht über
     * {@code setItem}: Das riefe wieder hier herein, und ein Aufräumen, das
     * sich selbst aufruft, ist ein Aufräumen, das man nicht mehr überblickt.
     */
    private void packInto(ItemStack chassis, int bay) {
        List<ItemStack> taken = new ArrayList<>(PARTS_PER_BAY);
        boolean anything = false;
        for (ServerPart part : ServerPart.values()) {
            ItemStack stack = getItem(slotOf(bay, part));
            taken.add(stack);
            anything |= !stack.isEmpty();
        }
        if (!anything) {
            return;
        }
        ServerChassis.write(chassis, taken);
        for (ServerPart part : ServerPart.values()) {
            parts().set(slotOf(bay, part), ItemStack.EMPTY);
        }
        bumpRevision();
        setChanged();
    }

    /** Und der umgekehrte Weg, sobald ein Gehäuse eingesetzt wurde. */
    private void unpackFrom(ItemStack chassis, int bay) {
        if (ServerChassis.isEmpty(chassis)) {
            return;
        }
        NonNullList<ItemStack> stored = ServerChassis.read(chassis);
        for (ServerPart part : ServerPart.values()) {
            ItemStack stack = stored.get(part.ordinal());
            // Nur, was hineingehört. Ein Gehäuse aus dem Kreativmodus kann
            // alles enthalten, und ein Rechenwerk auf dem Datenträgerplatz
            // wäre ein Einschub, der voll aussieht und nicht läuft.
            parts().set(slotOf(bay, part),
                    ServerPartItem.partOf(stack) == part ? stack : ItemStack.EMPTY);
        }
        ServerChassis.write(chassis, List.of());
        bumpRevision();
        setChanged();
    }

    /**
     * Packt alle Einschübe ein — vor dem Abbauen.
     *
     * <p>Danach liegen in den Bauteilplätzen keine losen Teile mehr, und was
     * herausfällt, sind zwölf fertige Server statt achtundvierzig Einzelteile.
     */
    public void packAll() {
        for (int bay = 0; bay < BAYS; bay++) {
            ItemStack chassis = getItem(chassisSlot(bay));
            if (!chassis.isEmpty()) {
                packInto(chassis, bay);
            }
        }
    }

    // ---- Was der Schrank trägt --------------------------------------------

    /** Steckt in diesem Einschub ein Gehäuse? */
    public boolean hasChassis(int bay) {
        return bay >= 0 && bay < BAYS && !getItem(chassisSlot(bay)).isEmpty();
    }

    /**
     * Was in einem Einschub steckt.
     *
     * <p>Ohne Gehäuse nichts — auch wenn dort wider Erwarten Bauteile lägen.
     * Die Regel steht damit an einer Stelle und nicht an dreien.
     */
    public ServerBay bay(int bay) {
        if (!hasChassis(bay)) {
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

    /**
     * Wie viele Einschübe angefangen und nicht fertig sind.
     *
     * <p>Ein Gehäuse ohne Hardware zählt dazu: Es sieht von außen aus wie
     * ein Server und ist keiner.
     */
    public int incompleteBays() {
        int count = 0;
        for (int bay = 0; bay < BAYS; bay++) {
            if (hasChassis(bay) && !bay(bay).complete()) {
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
