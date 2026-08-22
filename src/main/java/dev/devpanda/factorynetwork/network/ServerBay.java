package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Einschub im Serverschrank: Rechenwerk, Speicher, Datenträger.
 *
 * <p><b>Erst alle drei ergeben einen Server.</b> Ein Einschub mit zwei
 * Bauteilen trägt nichts bei — nicht anteilig, gar nichts. Das ist die
 * Regel, an der der ganze Block hängt: Wer zwölf Rechenwerke einbaut, hat
 * zwölf Bauteile und keinen einzigen Server, und wer aufrüsten will, muss
 * sich entscheiden, welcher Einschub das große Teil bekommt.
 *
 * <p>Die Alternative — jedes Bauteil zählt für sich — wäre dieselbe Zahl mit
 * weniger Entscheidung: Man baut von allem das Größte ein, und die zwölf
 * Plätze sind nur noch eine Obergrenze.
 */
public record ServerBay(int cpu, int ram, int disk) {

    public static final ServerBay EMPTY = new ServerBay(0, 0, 0);

    /**
     * Liest einen Einschub aus seinen drei Plätzen.
     *
     * <p>Die Reihenfolge ist die von {@link ServerPart}, und sie ist auch die
     * Reihenfolge der Plätze im Fenster — ein Platz nimmt nur seine Art an.
     */
    public static ServerBay of(ItemStack cpu, ItemStack ram, ItemStack disk) {
        return new ServerBay(
                ServerPartItem.valueOf(cpu, ServerPart.CPU),
                ServerPartItem.valueOf(ram, ServerPart.RAM),
                ServerPartItem.valueOf(disk, ServerPart.DISK));
    }

    /** Läuft dieser Einschub? Nur mit allen drei Bauteilen. */
    public boolean complete() {
        return cpu > 0 && ram > 0 && disk > 0;
    }

    /** Ist überhaupt etwas eingebaut? */
    public boolean occupied() {
        return cpu > 0 || ram > 0 || disk > 0;
    }

    /** Was dieser Einschub beiträgt — nichts, solange er unvollständig ist. */
    public ServerBay contribution() {
        return complete() ? this : EMPTY;
    }

    public ServerBay plus(ServerBay other) {
        return new ServerBay(cpu + other.cpu, ram + other.ram, disk + other.disk);
    }

    /** Welches Bauteil fehlt noch — für die Meldung im Fenster. */
    public ServerPart missing() {
        if (cpu <= 0) {
            return ServerPart.CPU;
        }
        if (ram <= 0) {
            return ServerPart.RAM;
        }
        if (disk <= 0) {
            return ServerPart.DISK;
        }
        return null;
    }
}
