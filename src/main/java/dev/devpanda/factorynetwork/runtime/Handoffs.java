package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Die zwei Griffe, mit denen überall Ware in ein Gerät geht und wieder heraus.
 *
 * <p>Sie stehen hier und nicht je einmal beim Worker und beim Interpreter,
 * weil beide dieselbe Reihenfolge einhalten müssen: <b>erst einlegen, dann
 * entnehmen.</b> Andersherum wäre die Ware schon aus der Quelle, wenn das Ziel
 * sie nicht nimmt — und eine Maschine, die ihren Brennstoffplatz hergibt,
 * füllt ihn nicht wieder auf.
 *
 * <p>Die Reihenfolge hat einen Preis, und der ist {@link #pullBack}: Liefert
 * die Quelle beim echten Griff weniger als versprochen, liegt der Unterschied
 * schon im Ziel. Er ist aus dem Nichts entstanden und muss wieder heraus.
 */
public final class Handoffs {

    private Handoffs() {
    }

    /**
     * Was ein Griff bewegt hat und was dabei aufgefallen ist.
     *
     * @param moved     wie viel wirklich von der Quelle ins Ziel ging
     * @param targetFull ob das Ziel nichts mehr annahm
     * @param stranded  wie viel im Ziel liegt, ohne je aus der Quelle gekommen
     *                  zu sein — siehe {@link #pullBack}
     */
    public record Handoff(long moved, boolean targetFull, long stranded) {
    }

    /**
     * Von einem Gerät ins andere, so viel wie das Ziel nimmt.
     *
     * <p>Die Reihenfolge ist die des ganzen Hauses: erst einlegen, dann
     * entnehmen. Was die Quelle beim echten Griff weniger hergibt als beim
     * Probelauf, holt {@link #pullBack} wieder aus dem Ziel.
     */
    public static Handoff items(IItemHandler in, IItemHandler out,
                                java.util.List<net.minecraft.world.item.Item> filter,
                                long limit) {
        long moved = 0;
        long stranded = 0;
        for (int slot = 0; slot < in.getSlots() && moved < limit; slot++) {
            ItemStack stack = in.getStackInSlot(slot);
            if (stack.isEmpty() || (!filter.isEmpty() && !filter.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(limit - moved, stack.getCount());
            ItemStack simulated = in.extractItem(slot, wanted, true);
            ItemStack rest = insertInto(out, simulated);
            int accepted = simulated.getCount() - rest.getCount();
            if (accepted <= 0) {
                return new Handoff(moved, true, stranded);
            }
            // Was der Griff wirklich hergibt, und nicht, was der Probelauf
            // versprochen hat. Der Unterschied liegt schon im Ziel und ist aus
            // dem Nichts entstanden.
            int taken = in.extractItem(slot, accepted, false).getCount();
            if (taken < accepted) {
                stranded += pullBack(out, ItemKey.of(simulated), accepted - taken);
            }
            moved += taken;
        }
        return new Handoff(moved, false, stranded);
    }

    /**
     * Ein Griff aus einem Tank in einen anderen.
     *
     * <p>Nur ein Griff und keine Schleife: Was ein leerer oder unwilliger Tank
     * bedeutet, entscheiden die beiden Aufrufer verschieden — der Worker hört
     * auf, der Interpreter geht zur nächsten Sorte weiter. Diese Entscheidung
     * gehört ihnen und nicht hierher.
     *
     * <p>Der Rückweg ist hier enger als bei Gegenständen: Eine gezogene
     * Flüssigkeit lässt sich nicht in die Quelle zurücklegen, weil ein Tank
     * sie nicht wieder annehmen muss. Was zu viel im Ziel liegt, wird deshalb
     * dort abgezogen und ist danach weg — es hat nie existiert.
     */
    public static Handoff fluid(net.neoforged.neoforge.fluids.capability.IFluidHandler in,
                                net.neoforged.neoforge.fluids.capability.IFluidHandler out,
                                net.neoforged.neoforge.fluids.FluidStack wanted) {
        var action = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        var probe = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        net.neoforged.neoforge.fluids.FluidStack simulated = in.drain(wanted, probe);
        if (simulated.isEmpty()) {
            return new Handoff(0, false, 0);
        }
        int accepted = out.fill(simulated, action);
        if (accepted <= 0) {
            return new Handoff(0, true, 0);
        }
        // Was der Griff wirklich hergibt, und nicht, was der Probelauf
        // versprochen hat.
        int taken = in.drain(simulated.copyWithAmount(accepted), action).getAmount();
        if (taken >= accepted) {
            return new Handoff(taken, false, 0);
        }
        int surplus = accepted - taken;
        int pulled = out.drain(simulated.copyWithAmount(surplus), action).getAmount();
        return new Handoff(taken, false, surplus - pulled);
    }

    /** Legt über alle Fächer ein und liefert, was nicht hineinpasste. */
    public static ItemStack insertInto(IItemHandler handler, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        return rest;
    }

    /**
     * Holt zurück, was gerade eingelegt und nicht gedeckt war.
     *
     * <p>Der Rückweg für den einen Fall, in dem sonst Ware entsteht: Das Ziel
     * hat schon bekommen, die Quelle hat dann weniger hergegeben. Was hier
     * herauskommt, geht <b>nirgendwo</b> hin — es hat nie existiert, und es
     * irgendwo abzulegen wäre die Verdopplung mit einem Umweg.
     *
     * <p>Gelingen muss es nicht. Ein Eingangsfach gibt nichts heraus, und dann
     * bleibt der Rest liegen; der Aufrufer erfährt es an der Rückgabe und
     * zählt ihn nicht als bewegt. Das ist die kleinere Hälfte des Übels: eine
     * Menge, die einmal zu viel im Gerät liegt, statt einer, die jeden Tick
     * nachwächst.
     *
     * @return wie viel nicht mehr herauszuholen war
     */
    public static long pullBack(IItemHandler handler, ItemKey item, long amount) {
        long left = amount;
        for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
            // Ganzer Gegenstand, nicht nur die Kennung — sonst käme die
            // verzauberte Spitzhacke zurück statt der nackten, die eben
            // hineinging.
            if (!item.equals(ItemKey.of(handler.getStackInSlot(slot)))) {
                continue;
            }
            left -= handler.extractItem(slot, (int) Math.min(left, Integer.MAX_VALUE),
                    false).getCount();
        }
        return left;
    }
}
