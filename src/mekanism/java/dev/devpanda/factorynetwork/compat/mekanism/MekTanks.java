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
 * The grip on a chemical container in the world.
 *
 * <p><b>The capability is built ourselves, not imported.</b> Mekanism keeps it
 * in {@code mekanism.common.capabilities.Capabilities} — that is, in the belly
 * of the mod and not in its API jar. Compiling against {@code common} would be
 * a dependency on internals that carry no guarantee.
 *
 * <p>NeoForge returns the same instance for the same name and the same type;
 * the name appears in Mekanism's bytecode as {@code mekanism:chemical_handler},
 * and that is the contract every third-party mod holds onto. If it changes,
 * this class finds no more containers — and that is an outage, not a crash.
 */
final class MekTanks {

    private static final BlockCapability<IChemicalHandler, Direction> CHEMICAL =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);

    private MekTanks() {
    }

    /**
     * The container on the side the connector is attached to.
     *
     * <p><b>Side-specific, as with items and fluids.</b> A Mekanism machine
     * has a side configuration, and it belongs to the player: whoever sets a
     * side to "nothing" wants nothing there.
     *
     * <p>A fallback to the unsided access stood here briefly and is gone
     * again. Measured: the unsided handler of a chemical tank can be
     * <b>read</b>, but accepts nothing — it is not a back door but a readout.
     * A fallback to it would only have masked that the side is not configured.
     */
    static IChemicalHandler at(Level level, BlockPos pos, Direction side) {
        return level.isLoaded(pos) ? level.getCapability(CHEMICAL, pos, side) : null;
    }

    /** How much of these chemicals is there. */
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
     * What is on top, without taking it out.
     *
     * <p>The look needed to ask <b>beforehand</b>: only once the chemical is
     * known can the store be asked for room — and only then may anything be
     * pulled.
     *
     * @return the stack in the first matching tank, or empty
     */
    static ChemicalStack peek(IChemicalHandler handler, Collection<String> ids) {
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && matches(stack, ids)) {
                return stack;
            }
        }
        return ChemicalStack.EMPTY;
    }

    /**
     * Pulls out and reports what came — as identifier and amount.
     *
     * <p>At most one chemical per pass: whoever has two gases in one container
     * gets them one after another. This is the same restraint as with fluids,
     * and for the same reason — the caller should know what it is holding.
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

    /** Fills in and reports how much went in. */
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

    /** The identifier of what is in a stack. */
    static String idOf(ChemicalStack stack) {
        return MekCells.idOf(stack.getChemical());
    }

    private static boolean matches(ChemicalStack stack, Collection<String> ids) {
        // An empty selection means "everything" — the same rule as with a
        // worker's filter.
        return ids.isEmpty() || ids.contains(MekCells.idOf(stack.getChemical()));
    }
}
