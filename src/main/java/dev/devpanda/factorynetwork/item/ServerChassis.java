package dev.devpanda.factorynetwork.item;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * Was in einem Servergehäuse steckt.
 *
 * <p><b>Die Bauteile stecken im Gegenstand, nicht im Schrank.</b> Dasselbe
 * Bild wie bei der Speicherzelle: Man zieht einen fertigen Server heraus,
 * trägt ihn weg und steckt ihn anderswo hinein — die Hardware kommt mit.
 * Läge sie im Schrank, wäre das Gehäuse ein Platzhalter und kein Server.
 *
 * <p><b>Bearbeitet wird trotzdem nur im Schrank.</b> Ein Gegenstand, der
 * Gegenstände hält, lässt sich im Rucksack nicht aufmachen — dieselbe Grenze
 * wie bei der Shulkerkiste. Steckt das Gehäuse im Einschub, liegen seine
 * Bauteile in den drei Plätzen daneben und der Gegenstand ist leer; beim
 * Herausziehen wandern sie zurück hinein.
 *
 * <p>Abgelegt unter {@code DataComponents.CONTAINER}: Das ist die
 * Komponente, die Minecraft für „dieser Gegenstand hält Gegenstände"
 * vorsieht. Sie überträgt sich von selbst zum Client, überlebt {@code /give}
 * und sorgt dafür, dass zwei verschieden bestückte Gehäuse nicht zu einem
 * Stapel verschmelzen.
 */
public final class ServerChassis {

    /** Rechenwerk, Speicher, Datenträger — in der Reihenfolge von {@link ServerPart}. */
    public static final int SLOTS = ServerPart.values().length;

    private ServerChassis() {
    }

    /** Die drei Bauteile, immer drei Einträge lang. */
    public static NonNullList<ItemStack> read(ItemStack chassis) {
        NonNullList<ItemStack> parts = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ItemContainerContents contents = chassis.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(parts);
        }
        return parts;
    }

    /** Schreibt die drei Bauteile hinein. Eine leere Liste räumt auf. */
    public static void write(ItemStack chassis, List<ItemStack> parts) {
        boolean anything = parts.stream().anyMatch(part -> !part.isEmpty());
        if (anything) {
            chassis.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
        } else {
            // Nicht mit einer leeren Komponente stehen lassen: Ein leeres
            // Gehäuse soll sich mit einem anderen leeren stapeln lassen und
            // im Tooltip nichts behaupten.
            chassis.remove(DataComponents.CONTAINER);
        }
    }

    /** Steckt überhaupt etwas darin? */
    public static boolean isEmpty(ItemStack chassis) {
        ItemContainerContents contents = chassis.get(DataComponents.CONTAINER);
        return contents == null || contents.nonEmptyStream().findAny().isEmpty();
    }

    /** Ist das ein Gehäuse? */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ServerChassisItem;
    }
}
