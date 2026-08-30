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
