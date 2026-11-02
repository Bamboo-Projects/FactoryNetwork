package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.platform.Nbt;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DeviceScan;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.network.Power;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import dev.devpanda.factorynetwork.runtime.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;

import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.*;

/**
 * The game tests that need Mekanism, moved out of
 * {@link FactoryNetworkGameTests} so that the others compile where
 * Mekanism is not published, as on 26.1. Built only with Mekanism; see
 * build.gradle.
 */
public final class MekanismGameTests {

    private MekanismGameTests() {
    }

    /**
     * With Mekanism a chemical selection goes through.
     *
     * <p>The test was once called "without Mekanism the message says that
     * Mekanism is missing", and it has changed its truth twice: first when
     * the dependency came into the test run, then when the integration was
     * in place. Now it checks what applies from here on — with Mekanism,
     * {@code chemical:} is a selection like any other.
     *
     * <p>The case without Mekanism is a unit test in
     * {@code FilterCheckTest}, where no mod list is loaded.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void withMekanismChemicalsGoThrough(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(
                dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed(),
                "Der Prüflauf soll Mekanism kennen");
        helper.assertTrue(entity.deploy("""
                fn holen() {
                    move chemical:mekanism/hydrogen from depot to storage
                }"""), "Das Programm wurde nicht übernommen");

        // A chest hangs on depot and no tank — nothing comes, but nothing
        // throws either. A device without chemicals is not an error message
        // but an empty device.
        // A chest hangs on depot, so nothing comes — but nothing throws
        // either, and that is the difference from before.
        entity.callFunction("holen", List.of());

        // And a template now accepts chemical: instead of rejecting it.
        helper.assertTrue(entity.deploy("""
                filter gase {
                    chemical:mekanism/hydrogen
                }"""), "Mit Mekanism gehört chemical: in eine Vorlage");
        helper.succeed();
    }

    /**
     * A chemical is a value and not just a selection.
     *
     * <p>What counts here is the <b>resolution against the registry</b>: it
     * belongs to Mekanism, and without the mod it does not exist — which is
     * why this case is in the test run and not in the unit test. What comes
     * after, reading the kind off an entry, is there.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void achemicalIsAvalueOfItsOwn(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(
                dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed(),
                "Der Prüflauf soll Mekanism kennen");
        helper.assertTrue(entity.deploy("""
                fn zeigen() {
                    for gas in chemical:mekanism/hydrogen {
                        log(gas)
                    }
                }"""), "Das Programm wurde nicht übernommen");

        entity.callFunction("zeigen", List.of());

        helper.assertTrue(entity.log().stream().anyMatch(zeile ->
                        zeile.text().toLowerCase().contains("hydrogen")
                                || zeile.text().toLowerCase().contains("wasserstoff")),
                "Eine Chemikalie muss als Wert im Protokoll stehen: " + entity.log());
        helper.succeed();
    }

    /**
     * Chemicals go from the network into a tank and back again.
     *
     * <p><b>The tank is not one in the world but one from Mekanism's
     * API.</b> That is no evasion but the consequence of a measurement: a
     * chemical tank that a test run places via {@code setBlock} hands out a
     * capability on <b>no</b> side and accepts nothing undivided either — it
     * lacks the side configuration a player brings along when placing.
     * Measured with all six sides.
     *
     * <p>What is specific here and therefore checked is the back and forth
     * with the network storage: probe first, then extract, and put back what
     * the tank does not take after all. How the tank is found is the same
     * path as with fluids.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void chemicalsGoIntoAtankAndBack(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        driveWithChemicalCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.ChemicalCellTier.K256);
        entity.rebuildNetwork();
        entity.chemicals().insert("mekanism:hydrogen", 3000);

        var tank = mekanism.api.chemical.BasicChemicalTank.create(
                10_000, mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
                mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
                mekanism.api.functions.ConstantPredicates.alwaysTrue(), null);
        var handler = new mekanism.api.chemical.IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return 1;
            }

            @Override
            public mekanism.api.chemical.ChemicalStack getChemicalInTank(int slot) {
                return tank.getStack();
            }

            @Override
            public void setChemicalInTank(int slot,
                    mekanism.api.chemical.ChemicalStack stack) {
                tank.setStack(stack);
            }

            @Override
            public long getChemicalTankCapacity(int slot) {
                return tank.getCapacity();
            }

            @Override
            public boolean isValid(int slot, mekanism.api.chemical.ChemicalStack stack) {
                return true;
            }

            @Override
            public mekanism.api.chemical.ChemicalStack insertChemical(int slot,
                    mekanism.api.chemical.ChemicalStack stack, mekanism.api.Action action) {
                return tank.insert(stack, action, mekanism.api.AutomationType.EXTERNAL);
            }

            @Override
            public mekanism.api.chemical.ChemicalStack extractChemical(int slot, long amount,
                    mekanism.api.Action action) {
                return tank.extract(amount, action, mekanism.api.AutomationType.EXTERNAL);
            }
        };

        long gegeben = dev.devpanda.factorynetwork.compat.mekanism.MekBridge
                .fillIntoHandler(entity.chemicals(), handler,
                        List.of("mekanism:hydrogen"), 1000);
        helper.assertValueEqual(gegeben, 1000L, "Tausend gehen in den Behälter");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 2000L,
                "und fehlen im Netz");

        long geholt = dev.devpanda.factorynetwork.compat.mekanism.MekBridge
                .drainIntoHandler(handler, List.of("mekanism:hydrogen"),
                        entity.chemicals(), 1000);
        helper.assertValueEqual(geholt, 1000L, "und wieder heraus");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 3000L,
                "ohne dass unterwegs etwas verschwindet");
        helper.succeed();
    }

    /**
     * What the storage cannot hold stays in the tank.
     *
     * <p>The promise that already applies to fluids: a gas that is outside
     * and fits nowhere would be gone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void whatThestoreCannotHoldStaysInThetank(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // The smallest cell: 64,000 mB.
        driveWithChemicalCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.ChemicalCellTier.K64);
        entity.rebuildNetwork();
        entity.chemicals().insert("mekanism:hydrogen", 60_000);

        var tank = mekanism.api.chemical.BasicChemicalTank.create(
                100_000, mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
                mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
                mekanism.api.functions.ConstantPredicates.alwaysTrue(), null);
        tank.setStack(new mekanism.api.chemical.ChemicalStack(
                mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(
                        net.minecraft.resources.Identifier.parse("mekanism:hydrogen")),
                20_000));
        var handler = new mekanism.api.chemical.IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return 1;
            }

            @Override
            public mekanism.api.chemical.ChemicalStack getChemicalInTank(int slot) {
                return tank.getStack();
            }

            @Override
            public void setChemicalInTank(int slot,
                    mekanism.api.chemical.ChemicalStack stack) {
                tank.setStack(stack);
            }

            @Override
            public long getChemicalTankCapacity(int slot) {
                return tank.getCapacity();
            }

            @Override
            public boolean isValid(int slot, mekanism.api.chemical.ChemicalStack stack) {
                return true;
            }

            @Override
            public mekanism.api.chemical.ChemicalStack insertChemical(int slot,
                    mekanism.api.chemical.ChemicalStack stack, mekanism.api.Action action) {
                return tank.insert(stack, action, mekanism.api.AutomationType.EXTERNAL);
            }

            @Override
            public mekanism.api.chemical.ChemicalStack extractChemical(int slot, long amount,
                    mekanism.api.Action action) {
                return tank.extract(amount, action, mekanism.api.AutomationType.EXTERNAL);
            }
        };

        long geholt = dev.devpanda.factorynetwork.compat.mekanism.MekBridge
                .drainIntoHandler(handler, List.of("mekanism:hydrogen"),
                        entity.chemicals(), 20_000);

        helper.assertValueEqual(geholt, 4000L, "nur, was noch hineinpasst");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 64_000L,
                "die Zelle ist voll");
        helper.assertValueEqual(tank.getStored(), 16_000L,
                "und der Rest liegt weiter im Behälter");
        helper.succeed();
    }

    /**
     * The contents ride along in the cell.
     *
     * <p>The reason a cell is worth something — and the same as with the
     * other two kinds.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void thestoredChemicalsRideAlongInThecell(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithChemicalCell(helper, drivePos,
                dev.devpanda.factorynetwork.storage.ChemicalCellTier.K256);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.chemicals().insert("mekanism:hydrogen", 2500);

        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                blockEntity(helper, drivePos);
        drive.flushCells();
        var inhalt = dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores
                .read(drive.cell(0), helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault("mekanism:hydrogen", 0L), 2500L,
                "Die Zelle trägt ihren Inhalt selbst");

        drive.setCell(0, ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 0L,
                "Ohne die Zelle ist der Bestand weg");
        helper.succeed();
    }

    /**
     * A chemical selection resolves.
     *
     * <p>Mekanism is in the test run, so there is hydrogen. Without Mekanism
     * the list is empty and the message says so — that case is a unit test.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void achemicalSelectionResolves(GameTestHelper helper) {
        var found = dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(
                dev.devpanda.factorynetwork.lang.Selectors.parse(
                        "chemical:mekanism/hydrogen"));

        helper.assertValueEqual(found.size(), 1, "genau eine Chemikalie");
        helper.assertValueEqual(found.get(0), "mekanism:hydrogen", "und zwar Wasserstoff");
        helper.succeed();
    }

    /** A pattern hits several, and a name that does not exist hits none. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void achemicalPatternHitsSeveral(GameTestHelper helper) {
        var alle = dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(
                dev.devpanda.factorynetwork.lang.Selectors.parse("chemical:mekanism/*"));
        helper.assertTrue(alle.size() > 5,
                "Mekanism bringt mehr als fünf Chemikalien mit, gefunden: " + alle.size());

        var keine = dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(
                dev.devpanda.factorynetwork.lang.Selectors.parse("chemical:mekanism/gibtsnicht"));
        helper.assertValueEqual(keine.size(), 0, "einen erfundenen Namen gibt es nicht");
        helper.succeed();
    }

    /**
     * A chemical selection that hits nothing reports itself.
     *
     * <p><b>The third drift of the same kind.</b> Cut 1 found it in the value
     * model, the search in cut 2 found none in the stores — it sat in the
     * resolvers: with items and fluids "hits nothing" is an error with a
     * message, with chemicals an empty list came back.
     *
     * <p>And empty means <b>everything</b> further down: {@code MekTanks.matches}
     * lets every kind through when none is given, and
     * {@code fillIntoHandler} then takes the whole network stock. A typo in
     * {@code chemical:…} thus filled some gas or other into the machine —
     * the same class of bug as a {@code move} with an empty filter that
     * clears out the chest.
     *
     * <p>The <b>message</b> is checked and not the gas: a Mekanism tank
     * placed via {@code setBlock} hands out a capability on no side, and the
     * error has to fall before any tank is asked anyway.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achemicalSelectionThatHitsNothingSaysSo(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        driveWithChemicalCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.ChemicalCellTier.K256);
        entity.rebuildNetwork();
        entity.chemicals().insert("mekanism:hydrogen", 3000);

        helper.assertTrue(
                dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed(),
                "Der Prüflauf soll Mekanism kennen");
        helper.assertTrue(entity.deploy("""
                fn versuchen() {
                    move 100 chemical:mekanism/gibtesnicht from storage to depot
                }"""), "Das Programm wurde nicht übernommen");

        boolean gemeldet = false;
        try {
            entity.callFunction("versuchen", java.util.List.of());
        } catch (ScriptError error) {
            gemeldet = true;
            helper.assertTrue(error.getMessage().contains("trifft"),
                    "Die Meldung soll sagen, dass die Auswahl nichts trifft: "
                            + error.getMessage());
        }
        helper.assertTrue(gemeldet,
                "Eine Chemikalienauswahl, die nichts trifft, darf nicht still 0 liefern — "
                        + "leer heißt weiter unten alles");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 3000L,
                "und der Wasserstoff muss unangetastet im Netz liegen");
        helper.succeed();
    }

    /**
     * And an exclusion over it yields a chemical, not a number.
     *
     * <p>{@code except} is the path on which a chemical selection is
     * resolved before moving. If exactly one kind remains, it is there with
     * {@code .chemical} — the same rule as with {@code it.item} and
     * {@code it.fluid}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void achemicalExceptKeepsTheKind(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn zeigen() {
                    let rest = chemical:mekanism/hydrogen except chemical:mekanism/oxygen
                    return rest.chemical
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("zeigen", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf muss durchlaufen: " + flow.detail());
        helper.assertTrue(flow.result()
                        instanceof dev.devpanda.factorynetwork.runtime.Value.Resource resource
                        && resource.kind()
                                == dev.devpanda.factorynetwork.runtime.ResourceKinds.CHEMICAL,
                "und eine Chemikalie liefern: " + flow.result().describe());
        helper.succeed();
    }

    /**
     * A chemical worker needs a filter.
     *
     * <p>The same rule as with fluids and for the same reason: a tank
     * usually holds exactly one kind, and pulling the wrong one is more
     * expensive than with items.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achemicalWorkerNeedsAfilter(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Without filter the worker is not a chemical worker at all — it
        // falls into the item branch. So with filter, but without a target in
        // the network: that too must give a message and no silent standstill.
        helper.assertTrue(entity.deploy("""
                worker gase {
                    from gibtsnicht
                    to storage
                    filter chemical:mekanism/hydrogen
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var state = entity.runtime().states().get("gase");
                    helper.assertValueEqual(state.status.name(), "HALTED",
                            "der Worker muss anhalten");
                    helper.assertTrue(state.detail.contains("gibtsnicht"),
                            "und sagen, welches Gerät fehlt: " + state.detail);
                })
                .thenSucceed();
    }

    /**
     * And it only goes between device and storage.
     *
     * <p>From device to device it runs via the storage; for that you write
     * two workers. A third path for the same operation would be a third
     * place where an amount can get lost on the way.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achemicalWorkerGoesBetweenDeviceAndStorage(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker gase {
                    from quarry_output
                    to depot
                    filter chemical:mekanism/hydrogen
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var state = entity.runtime().states().get("gase");
                    helper.assertValueEqual(state.status.name(), "HALTED",
                            "der Worker muss anhalten");
                    helper.assertTrue(state.detail.contains("Speicher"),
                            "und auf den Speicher zeigen: " + state.detail);
                })
                .thenSucceed();
    }

    /**
     * A chemical cell holds chemicals.
     *
     * <p>The same two limits as everywhere — so many kinds, so much amount —
     * and the same arithmetic behind it: it has been open over the type
     * since the fluids, and chemicals changed nothing about that.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achemicalCellHoldsChemicals(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithChemicalCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.ChemicalCellTier.K64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.chemicals().insert("mekanism:hydrogen", 1000), 0L,
                "das passt hinein");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 1000L,
                "und steht danach im Netz");

        // The smallest cell holds 64,000 mB; what goes beyond stays outside.
        helper.assertValueEqual(entity.chemicals().insert("mekanism:oxygen", 64_000), 1000L,
                "was über die Menge geht, bleibt draußen");

        helper.assertValueEqual(entity.chemicals().extract("mekanism:hydrogen", 400), 400L,
                "und es kommt wieder heraus");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 600L,
                "der Rest bleibt liegen");
        helper.succeed();
    }

    /** And the editor shows what it resolves to. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theeditorShowsWhatAchemicalResolvesTo(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse(
                        "chemical:mekanism/hydrogen"));

        helper.assertValueEqual(summary.get(0), "trifft 1 Art", "die Zahl");
        helper.assertTrue(summary.size() > 1, "und der Name: " + summary);
        helper.succeed();
    }
}
