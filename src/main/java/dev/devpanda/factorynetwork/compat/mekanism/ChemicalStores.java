package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellView;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Weg vom Kern zu den Chemikalien — und die Rückfahrkarte.
 *
 * <p>Diese Klasse trägt selbst keinen Mekanism-Typ in einer Signatur; sie ist
 * die Tür, durch die der Kern geht. Was dahinter liegt, wird nur betreten,
 * wenn {@link FnMekanism#installed()} wahr ist — und dann lädt die JVM erst
 * die Klassen, die Mekanism brauchen.
 *
 * <p>Das ist dasselbe Muster wie bei GuideME und Jade, nur eine Schicht
 * tiefer: Dort entscheidet der Mod-Konstruktor, hier jede einzelne Frage.
 */
public final class ChemicalStores {

    private ChemicalStores() {
    }

    /**
     * Der Chemikalienspeicher für ein Netz.
     *
     * <p>Ohne Mekanism der, der nichts kann. Er ist kein Platzhalter, sondern
     * die richtige Antwort: In einem Pack ohne die Mod gibt es keine
     * Chemikalien, und ein Speicher, der so tut, wäre eine Lüge mit
     * Nebenwirkungen.
     */
    public static ResourceStore create() {
        return FnMekanism.installed() ? new MekChemicalStore() : ResourceStore.NONE;
    }

    /**
     * Öffnet eine Chemikalienzelle, oder {@code null} ohne Mekanism.
     *
     * <p>Die Fabrik, die {@code DriveBlockEntity} von außen bekommt.
     */
    public static CellView open(ItemStack cell) {
        return FnMekanism.installed() ? MekCells.open(cell) : null;
    }

    /**
     * Wie viel von diesen Sorten in der Maschine an dieser Stelle liegt.
     *
     * <p>Ohne Mekanism null — es gibt dort keine Chemikalien.
     */
    public static long amountAt(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids) {
        if (!FnMekanism.installed()) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.amountIn(handler, ids);
    }

    /**
     * Wie viel von dieser Sorte gerade in die Maschine passt.
     *
     * <p>Die Probe vor dem Einfüllen: Ein Rezept, dessen Gas nicht ganz
     * hineingeht, soll gar nicht erst anfangen — sonst stünde die Maschine
     * mit halber Rechnung da. Nach außen sind das nur Kennungen und Zahlen;
     * der Behälter bleibt in diesem Paket.
     */
    public static long roomFor(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            String id, long amount) {
        if (!FnMekanism.installed() || amount <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.fill(handler, id, amount, true);
    }

    /**
     * Zieht aus einer Maschine in den Netzspeicher.
     *
     * <p><b>Erst fragen, dann ziehen.</b> Was der Speicher nicht nimmt, darf
     * gar nicht erst aus dem Behälter kommen: Ein Gas, das draußen ist und
     * nirgends hineinpasst, wäre weg. Dieselbe Vorsicht wie bei
     * Flüssigkeiten.
     *
     * @return wie viel angekommen ist
     */
    public static long drainInto(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids,
            ResourceStore store, long limit) {
        if (!FnMekanism.installed() || limit <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : drainIntoHandler(handler, ids, store, limit);
    }

    /**
     * Dasselbe, aber mit einem Behälter statt einer Stelle in der Welt.
     *
     * <p>Getrennt, weil die <b>Rechnung</b> das Prüfbare ist: Ein
     * Mekanism-Tank, der per {@code setBlock} in einen Prüflauf gestellt wird,
     * hat keine Seitenkonfiguration und nimmt deshalb nichts an — nachgemessen.
     * Die Suche nach dem Behälter ist dieselbe wie bei Flüssigkeiten und
     * anderswo geprüft; was hier eigen ist, ist das Hin und Her mit dem
     * Speicher, und das lässt sich mit einem Behälter aus dem API-Jar
     * vorführen.
     */
    public static long drainIntoHandler(mekanism.api.chemical.IChemicalHandler handler,
            java.util.Collection<String> ids,
            ResourceStore store, long limit) {
        long moved = 0;
        // Höchstens acht Sorten je Zug: Ein Behälter mit mehr wird über
        // mehrere Aufrufe geleert, und die Schleife kann nicht ins Endlose
        // laufen, wenn eine Sorte weder passt noch weicht.
        for (int guard = 0; guard < 8 && moved < limit; guard++) {
            var oben = MekTanks.peek(handler, ids);
            if (oben.isEmpty()) {
                break;
            }
            String id = MekTanks.idOf(oben);
            // Erst fragen: So viel, wie der Speicher wirklich nimmt — und
            // keinen Tropfen mehr. Was draußen ist und nirgends hineinpasst,
            // müsste zurück, und das Zurücklegen kann scheitern.
            long room = store.room(id, Math.min(limit - moved, oben.getAmount()));
            if (room <= 0) {
                break;
            }
            var taken = MekTanks.drain(handler, java.util.List.of(id), room);
            if (taken.isEmpty()) {
                break;
            }
            long rest = store.insert(id, taken.getAmount());
            if (rest > 0) {
                // Sollte nach der Frage nicht mehr vorkommen; wenn doch, ist
                // Zurücklegen die einzige Antwort, die nichts verschwinden
                // lässt.
                MekTanks.fill(handler, id, rest, false);
            }
            moved += taken.getAmount() - rest;
        }
        return moved;
    }

    /**
     * Füllt aus dem Netzspeicher in eine Maschine.
     *
     * @return wie viel angekommen ist
     */
    public static long fillFrom(ResourceStore store,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction side, java.util.Collection<String> ids, long limit) {
        if (!FnMekanism.installed() || limit <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : fillIntoHandler(store, handler, ids, limit);
    }

    /** Dasselbe, aber mit einem Behälter statt einer Stelle in der Welt. */
    public static long fillIntoHandler(
            ResourceStore store,
            mekanism.api.chemical.IChemicalHandler handler,
            java.util.Collection<String> ids, long limit) {
        long moved = 0;
        // Ohne Angabe alles, was im Netz liegt. Der Bestand kommt mit den
        // Schlüsseln der Art heraus — hier sind es Kennungen, und der Weg
        // dorthin lässt keine anderen zu.
        java.util.Collection<?> wanted = ids.isEmpty() ? store.contents().keySet() : ids;
        for (Object key : wanted) {
            String id = String.valueOf(key);
            if (moved >= limit) {
                break;
            }
            long have = store.count(id);
            if (have <= 0) {
                continue;
            }
            // Erst proben: Was die Maschine nicht nimmt, bleibt im Speicher.
            long fits = MekTanks.fill(handler, id, Math.min(have, limit - moved), true);
            if (fits <= 0) {
                continue;
            }
            long got = store.extract(id, fits);
            long placed = MekTanks.fill(handler, id, got, false);
            if (placed < got) {
                store.insert(id, got - placed);
            }
            moved += placed;
        }
        return moved;
    }

    /**
     * Was in einer Zelle liegt, als Kennung auf Menge.
     *
     * <p>Für den Tooltip: Der Gegenstand gibt es immer, auch ohne Mekanism —
     * dann ist die Antwort leer, und der Tooltip sagt, woran es liegt.
     */
    public static Map<String, Long> read(ItemStack cell) {
        return FnMekanism.installed() ? MekCells.read(cell) : new LinkedHashMap<>();
    }
}
