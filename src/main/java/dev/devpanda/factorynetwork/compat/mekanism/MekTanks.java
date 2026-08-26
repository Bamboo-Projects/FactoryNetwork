package dev.devpanda.factorynetwork.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.Collection;

/**
 * Der Griff an einen Chemikalienbehälter in der Welt.
 *
 * <p><b>Die Capability wird selbst gebaut, nicht importiert.</b> Mekanism
 * hält sie in {@code mekanism.common.capabilities.Capabilities} — also im
 * Bauch der Mod und nicht in ihrem API-Jar. Gegen {@code common} zu
 * übersetzen wäre eine Abhängigkeit auf Innenleben, das keine Zusage trägt.
 *
 * <p>NeoForge gibt für denselben Namen und denselben Typ dieselbe Instanz
 * zurück; der Name steht in Mekanisms Bytecode als
 * {@code mekanism:chemical_handler}, und das ist der Vertrag, an dem sich
 * jede Fremdmod festhält. Ändert er sich, findet diese Klasse keine Behälter
 * mehr — und das ist ein Ausfall, kein Absturz.
 */
final class MekTanks {

    private static final BlockCapability<IChemicalHandler, Direction> CHEMICAL =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);

    private MekTanks() {
    }

    /**
     * Der Behälter an der Seite, an der der Connector hängt.
     *
     * <p><b>Seitenbezogen, wie bei Gegenständen und Flüssigkeiten.</b> Eine
     * Mekanism-Maschine hat eine Seitenkonfiguration, und sie gehört dem
     * Spieler: Wer eine Seite auf „nichts" stellt, will dort nichts.
     *
     * <p>Ein Rückfall auf den ungeteilten Zugriff stand hier kurz und ist
     * wieder weg. Nachgemessen: Der ungeteilte Handler eines Chemikalientanks
     * lässt sich <b>lesen</b>, nimmt aber nichts an — er ist kein
     * Hintereingang, sondern eine Auskunft. Ein Rückfall darauf hätte nur
     * verschleiert, dass die Seite nicht eingerichtet ist.
     */
    static IChemicalHandler at(Level level, BlockPos pos, Direction side) {
        return level.isLoaded(pos) ? level.getCapability(CHEMICAL, pos, side) : null;
    }

    /** Wie viel von diesen Sorten dort liegt. */
    static long amountIn(IChemicalHandler handler, Collection<String> ids) {
        long total = 0;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && matches(stack, ids)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Zieht heraus und meldet, was kam — als Kennung und Menge.
     *
     * <p>Höchstens eine Sorte je Zug: Wer zwei Gase in einem Behälter hat,
     * bekommt sie nacheinander. Das ist dieselbe Zurückhaltung wie bei
     * Flüssigkeiten, und aus demselben Grund — der Aufrufer soll wissen, was
     * er in der Hand hält.
     */
    static ChemicalStack drain(IChemicalHandler handler, Collection<String> ids, long limit) {
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (stack.isEmpty() || !matches(stack, ids)) {
                continue;
            }
            ChemicalStack taken = handler.extractChemical(tank,
                    Math.min(limit, stack.getAmount()), Action.EXECUTE);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ChemicalStack.EMPTY;
    }

    /** Füllt ein und meldet, wie viel hineinging. */
    static long fill(IChemicalHandler handler, String id, long amount, boolean simulate) {
        Chemical chemical = MekCells.chemical(id);
        if (chemical == null || amount <= 0) {
            return 0;
        }
        ChemicalStack stack = new ChemicalStack(chemical, amount);
        ChemicalStack rest = handler.insertChemical(stack,
                simulate ? Action.SIMULATE : Action.EXECUTE);
        return amount - rest.getAmount();
    }

    /** Die Kennung dessen, was in einem Stapel steckt. */
    static String idOf(ChemicalStack stack) {
        return MekCells.idOf(stack.getChemical());
    }

    private static boolean matches(ChemicalStack stack, Collection<String> ids) {
        // Eine leere Auswahl heißt „alles" — dieselbe Regel wie beim Filter
        // eines Workers.
        return ids.isEmpty() || ids.contains(MekCells.idOf(stack.getChemical()));
    }
}
