package dev.devpanda.factorynetwork.test;

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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks in a real world what unit tests cannot: that blocks find each
 * other, that the graph sees the connectors, and that a worker really moves
 * items.
 *
 * <p>Setup in all tests: controller, one cable, two connectors, each with a
 * chest in front of it. The same arrangement that the concept uses as its
 * example.
 */
@GameTestHolder(FactoryNetwork.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactoryNetworkGameTests {

    private static final String EMPTY = "empty";

    /** Builds the setup and returns the position of the controller. */
    private static BlockPos buildSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Cable to the east, two connectors on it.
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        BlockPos sourceConnector = cable.north();
        BlockPos targetConnector = cable.south();
        connector(helper, sourceConnector, Direction.NORTH);
        connector(helper, targetConnector, Direction.SOUTH);

        helper.setBlock(sourceConnector.north(), Blocks.CHEST);
        helper.setBlock(targetConnector.south(), Blocks.CHEST);

        name(helper, sourceConnector, "quarry_output");
        name(helper, targetConnector, "depot");

        // A drive with a large cell: since cells exist, a network without a
        // drive stores nothing. Whoever tests the storage itself uses
        // bareSetup and places their own drive.
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        return controller;
    }

    /** The same setup without a drive — for the checks on the storage itself. */
    private static BlockPos bareSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        BlockPos sourceConnector = cable.north();
        BlockPos targetConnector = cable.south();
        connector(helper, sourceConnector, Direction.NORTH);
        connector(helper, targetConnector, Direction.SOUTH);
        helper.setBlock(sourceConnector.north(), Blocks.CHEST);
        helper.setBlock(targetConnector.south(), Blocks.CHEST);
        name(helper, sourceConnector, "quarry_output");
        name(helper, targetConnector, "depot");
        return controller;
    }

    /**
     * A cable with a connector on this face.
     *
     * <p><b>The replacement for the old connector block</b>, at the same spot
     * and with the same facing — which is why in the test runs every chest
     * stays where it was.
     *
     * <p>One difference remains, and it is not a small one: this spot is now
     * a cable and <b>conducts onwards</b>. Wherever a test run relied on a
     * connector being a dead end, that is now stated explicitly.
     */
    private static void connector(GameTestHelper helper, BlockPos pos, Direction facing) {
        connector(helper, pos, facing, CableColour.NONE);
    }

    /** The same on a coloured strand — the colour separates, as everywhere. */
    private static void connector(GameTestHelper helper, BlockPos pos, Direction facing,
                                  CableColour colour) {
        partOn(helper, pos, facing, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, colour));
    }

    /**
     * A connector on a <b>dense</b> cable.
     *
     * <p>Needed where the strand carries sixty-four channels: a thin cable at
     * this spot would be the weakest link and cap the whole run at sixteen.
     */
    private static void denseConnector(GameTestHelper helper, BlockPos pos, Direction facing) {
        partOn(helper, pos, facing, FnBlocks.DENSE_CABLE.get().defaultBlockState());
    }

    private static void partOn(GameTestHelper helper, BlockPos pos, Direction facing,
                               net.minecraft.world.level.block.state.BlockState cable) {
        helper.setBlock(pos, cable);
        if (helper.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(facing);
        } else {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", pos);
        }
    }

    /** The connector at this spot — exactly one sits there. */
    private static dev.devpanda.factorynetwork.block.entity.ConnectorPart partAt(
            GameTestHelper helper, BlockPos pos) {
        return dev.devpanda.factorynetwork.block.entity.Connectors.at(
                helper.getLevel(), helper.absolutePos(pos));
    }

    private static void name(GameTestHelper helper, BlockPos pos, String label) {
        var connector = partAt(helper, pos);
        if (connector != null) {
            connector.setLabel(label);
        } else {
            helper.fail("An dieser Stelle sitzt kein Anschluss", pos);
        }
    }

    /** How much lies in the chest at this spot, across all slots. */
    private static int countIn(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getBlockEntity(pos) instanceof ChestBlockEntity container)) {
            return 0;
        }
        int found = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            found += container.getItem(slot).getCount();
        }
        return found;
    }

    private static ControllerBlockEntity controllerAt(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            powerUp(controller);
            return controller;
        }
        helper.fail("Am Controller hängt keine BlockEntity", pos);
        throw new IllegalStateException();
    }

    /**
     * Fills the power reserve and boots the network.
     *
     * <p>There is no generator in the test world. Without this handle every
     * test would be one about the power outage — and none about what it
     * actually wants to test. The real path is taken: fill first, then wait
     * out the boot-up time.
     */
    private static void powerUp(ControllerBlockEntity controller) {
        var power = controller.power();
        if (power.isRunning()) {
            return;
        }
        power.fill(dev.devpanda.factorynetwork.network.Power.CAPACITY);
        power.setDraw(controller.powerDraw());
        for (int i = 0; i <= dev.devpanda.factorynetwork.network.Power.BOOT_TICKS; i++) {
            power.tick();
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void networkFindsNamedConnectors(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("quarry_output"),
                "Der Graph kennt quarry_output nicht");
        helper.assertTrue(entity.graph().connectorNames().contains("depot"),
                "Der Graph kennt depot nicht");
        helper.assertTrue(entity.graph().cableCount() >= 1,
                "Der Graph hat kein Kabel gefunden");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void unnamedConnectorStaysInvisible(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        // A third connector without a name must not appear in the network.
        BlockPos extra = controller.east().above();
        connector(helper, extra, Direction.NORTH);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().connectorNames().size(), 2,
                "Anzahl benannter Connectoren");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void workerMovesItemsFromChestToStorage(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos chest = controller.east().north().north();
        if (helper.getBlockEntity(chest) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 32));
        } else {
            helper.fail("Keine Kiste an der Quelle", chest);
        }

        boolean accepted = entity.deploy("""
                worker quarry_import {
                    from quarry_output
                    to storage
                    rate 64 per 1t
                }""");
        helper.assertTrue(accepted, "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        // The worker needs a few ticks, then the ore must be in storage.
        helper.runAfterDelay(20, () -> {
            long stored = entity.storage().count(Items.IRON_ORE);
            helper.assertTrue(stored > 0,
                    "Der Worker hat nichts in den Speicher gelegt (Stand: " + stored + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void workerMovesBetweenTwoConnectors(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        BlockPos target = controller.east().south().south();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }

        boolean accepted = entity.deploy("""
                worker haul {
                    from quarry_output
                    to depot
                    rate 64 per 1t
                }""");
        helper.assertTrue(accepted, "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(20, () -> {
            if (helper.getBlockEntity(target) instanceof ChestBlockEntity container) {
                helper.assertTrue(!container.getItem(0).isEmpty(),
                        "In der Zielkiste liegt nichts");
                helper.succeed();
            } else {
                helper.fail("Keine Kiste am Ziel", target);
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void brokenProgramKeepsTheOldOneRunning(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        helper.assertTrue(entity.deploy("""
                worker good {
                    from quarry_output
                    to storage
                }"""), "Das erste Programm wurde nicht übernommen");

        helper.assertFalse(entity.deploy("worker broken { nonsense }"),
                "Fehlerhafter Code wurde übernommen");
        helper.assertValueEqual(entity.program().workers().size(), 1,
                "Anzahl laufender Worker nach fehlerhafter Übernahme");
        helper.assertValueEqual(entity.program().workers().get(0).name(), "good",
                "Name des weiterlaufenden Workers");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void unknownConnectorIsReportedWithASuggestion(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        String suggestion = entity.graph().closestName("quary_output").orElse("");
        helper.assertValueEqual(suggestion, "quarry_output", "Vorschlag bei Tippfehler");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void functionMovesItemsBetweenConnectors(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        BlockPos target = controller.east().south().south();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 64));
        }

        helper.assertTrue(entity.deploy("""
                fn schiebe() {
                    move 16 item:iron_ore from quarry_output to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("schiebe", List.of());

        if (helper.getBlockEntity(target) instanceof ChestBlockEntity container) {
            int moved = container.getItem(0).getCount();
            helper.assertValueEqual(moved, 16,
                    "Die vorangestellte Menge muss beachtet werden");
        } else {
            helper.fail("Keine Kiste am Ziel", target);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void functionReadsRedstone(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // A redstone block next to the connector gives a signal.
        helper.setBlock(controller.east().north().above(), Blocks.REDSTONE_BLOCK);

        helper.assertTrue(entity.deploy("""
                fn stärke() {
                    return quarry_output.redstone()
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(5, () -> {
            Value result = entity.callFunction("stärke", List.of());
            long strength = ((Value.Int) result).value();
            helper.assertTrue(strength > 0,
                    "Redstone wurde nicht gelesen (Stand: " + strength + ")");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void unknownConnectorInCodeSuggests(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.assertTrue(entity.deploy("""
                fn test() {
                    return quary_output.online
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        try {
            entity.callFunction("test", List.of());
            helper.fail("Der unbekannte Connector hätte auffallen müssen");
        } catch (ScriptError error) {
            helper.assertTrue(error.hint() != null && error.hint().contains("quarry_output"),
                    "Der Vorschlag fehlt: " + error);
            helper.succeed();
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void maintainStopsAtTheWantedAmount(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        BlockPos target = controller.east().south().south();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COAL, 64));
        }

        helper.assertTrue(entity.deploy("""
                worker fuel {
                    from quarry_output
                    to depot
                    filter item:coal
                    maintain 8
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(30, () -> {
            if (helper.getBlockEntity(target) instanceof ChestBlockEntity container) {
                int present = container.getItem(0).getCount();
                helper.assertValueEqual(present, 8,
                        "maintain muss bei der zugesagten Menge aufhören");
                helper.succeed();
            } else {
                helper.fail("Keine Kiste am Ziel", target);
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void conditionKeepsWorkerAsleep(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 64));
        }

        // The storage is empty, the condition demands more than 100.
        helper.assertTrue(entity.deploy("""
                worker only_when_full {
                    from quarry_output
                    to storage
                    when storage.count(item:iron_ore) > 100
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                    "Der Worker darf bei falscher Bedingung nichts bewegen");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void tagFilterCollectsEveryMatchingItem(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // Two different logs — the same tag.
        BlockPos source = controller.east().north().north();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.OAK_LOG, 8));
            container.setItem(1, new ItemStack(Items.BIRCH_LOG, 8));
            container.setItem(2, new ItemStack(Items.IRON_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                worker logs {
                    from quarry_output
                    to storage
                    filter tag:minecraft/logs
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            long oak = entity.storage().count(Items.OAK_LOG);
            long birch = entity.storage().count(Items.BIRCH_LOG);
            long ore = entity.storage().count(Items.IRON_ORE);
            helper.assertTrue(oak == 8 && birch == 8,
                    "Der Tag muss beide Holzarten holen (Eiche " + oak + ", Birke " + birch + ")");
            helper.assertValueEqual(ore, 0L, "Das Erz gehört nicht zum Tag");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void patternMatchesInTheMiddleOfTheName(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.DEEPSLATE_IRON_ORE, 8));
            container.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
        }

        // Exactly the case from AllTheOres: the stone type is the prefix, the
        // shape is the suffix. A pattern anchored at one end only would not
        // find both.
        helper.assertTrue(entity.deploy("""
                worker ores {
                    from quarry_output
                    to storage
                    filter item:*iron_ore
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            long plain = entity.storage().count(Items.IRON_ORE);
            long deep = entity.storage().count(Items.DEEPSLATE_IRON_ORE);
            long ingot = entity.storage().count(Items.IRON_INGOT);
            helper.assertTrue(plain == 8 && deep == 8,
                    "Das Muster muss beide Erzvarianten treffen (" + plain + ", " + deep + ")");
            helper.assertValueEqual(ingot, 0L, "Der Barren ist kein Erz");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void maintainCountsPerItemType(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // Two kinds in the same tag. With "in total" 8 arrive altogether, with
        // "per kind" eight of each — that is what tells the two readings apart.
        BlockPos source = controller.east().north().north();
        BlockPos target = controller.east().south().south();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COAL, 64));
            container.setItem(1, new ItemStack(Items.CHARCOAL, 64));
        }

        helper.assertTrue(entity.deploy("""
                worker fuel {
                    from quarry_output
                    to depot
                    filter tag:minecraft/coals
                    maintain 8
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(40, () -> {
            if (!(helper.getBlockEntity(target) instanceof ChestBlockEntity container)) {
                helper.fail("Keine Kiste am Ziel", target);
                return;
            }
            long coal = 0;
            long charcoal = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(Items.COAL)) {
                    coal += stack.getCount();
                } else if (stack.is(Items.CHARCOAL)) {
                    charcoal += stack.getCount();
                }
            }
            helper.assertValueEqual(coal, 8L, "Kohle je Art");
            helper.assertValueEqual(charcoal, 8L, "Holzkohle je Art");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void moveRespectsATagFilter(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.OAK_LOG, 16));
            container.setItem(1, new ItemStack(Items.IRON_ORE, 16));
        }

        // Previously a selection that is not a single item silently fell back
        // to "everything" — the ore would have travelled along.
        helper.assertTrue(entity.deploy("""
                fn hole() {
                    move 4 tag:minecraft/logs from quarry_output to storage
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("hole", List.of());

        helper.assertValueEqual(entity.storage().count(Items.OAK_LOG), 4L,
                "Holz, und zwar genau vier");
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                "Das Erz gehört nicht zum Tag und darf nicht mitkommen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void duplicateNamesMakeBothUnusable(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        // Set both connectors to the same name.
        BlockPos second = controller.east().south();
        name(helper, second, "quarry_output");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().isAmbiguous("quarry_output"),
                "Der doppelte Name muss als solcher erkannt werden");
        helper.assertTrue(entity.graph().connector("quarry_output").isEmpty(),
                "Ein doppelter Name darf auf keinen der beiden zeigen");
        helper.assertValueEqual(entity.graph().ambiguousNames().size(), 1,
                "Anzahl mehrdeutiger Namen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void gunNumbersFromTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // quarry_output and depot are taken; a furnace is to be called
        // furnace_1, and the second time furnace_2.
        entity.rebuildNetwork();
        String first = ConnectorNaming.nextFree("furnace", entity.graph());
        helper.assertValueEqual(first, "furnace_1", "erster freier Name");

        // Now take furnace_1 — the suggestion must keep counting.
        name(helper, controller.east().south(), "furnace_1");
        entity.rebuildNetwork();
        String second = ConnectorNaming.nextFree("furnace", entity.graph());
        helper.assertValueEqual(second, "furnace_2", "zweiter freier Name");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void decomposedUmlautResolvesToTheComposedName(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        BlockPos connector = controller.east().south();

        // Set the decomposed form, as some text input methods deliver it.
        String decomposed = "ofen_su\u0308d";
        partAt(helper, connector).setLabel(ConnectorNaming.normalize(decomposed));

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
        controllerEntity.rebuildNetwork();

        // The lookup uses the composed form.
        helper.assertTrue(controllerEntity.graph().connector("ofen_süd").isPresent(),
                "Zerlegtes und zusammengesetztes ü müssen derselbe Name sein");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void storageExtractionIsCappedByWhatIsThere(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 3);

        // The display may be stale: whoever requests 64 gets the 3 that are
        // really there — and no more.
        long taken = entity.storage().extract(Items.IRON_INGOT, 64);
        helper.assertValueEqual(taken, 3L, "entnommene Menge");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 0L,
                "Rest im Speicher");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extractingFromAnEmptyStorageYieldsNothing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.assertValueEqual(entity.storage().extract(Items.DIAMOND, 1), 0L,
                "Entnahme aus leerem Speicher");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void storageSurvivesAMissingMod(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 5);

        // An entry from a mod that no longer exists must not end up as air in
        // the stock when read. The stock now lives in the cell, so that is
        // where it is checked.
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(controller.above());
        drive.flushCells();
        ItemStack cell = drive.cell(0);
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell, tag -> {
                    net.minecraft.nbt.ListTag entries =
                            tag.getList("Cell", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    net.minecraft.nbt.CompoundTag ghost = new net.minecraft.nbt.CompoundTag();
                    ghost.putString("Item", "verschwundene_mod:zauberstab");
                    ghost.putLong("Count", 99);
                    entries.add(ghost);
                    tag.put("Cell", entries);
                });

        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(cell,
                helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.size(), 1,
                "nur der bekannte Gegenstand darf überleben");
        helper.assertValueEqual(inhalt.getOrDefault(dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.IRON_INGOT), 0L), 5L, "Bestand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cablesOfDifferentColoursDoNotConnect(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Green strand to the first connector
        BlockPos green = controller.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        // The connector sits on the green strand and carries its colour: an
        // uncoloured cable at this spot would join both strands, and that is
        // exactly what the test run is meant to rule out.
        BlockPos reachable = green.east();
        connector(helper, reachable, Direction.NORTH, CableColour.GREEN);
        name(helper, reachable, "erreichbar");

        // Red strand attached to the green one — must not conduct through
        BlockPos red = green.above();
        helper.setBlock(red, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.RED));
        BlockPos hidden = red.east();
        connector(helper, hidden, Direction.NORTH, CableColour.RED);
        name(helper, hidden, "getrennt");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connector("erreichbar").isPresent(),
                "Der grüne Strang muss durchleiten");
        helper.assertTrue(entity.graph().connector("getrennt").isEmpty(),
                "Der rote Strang darf nicht am grünen hängen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void plainCableConnectsToEveryColour(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Default colour between the controller and a blue strand
        BlockPos plain = controller.east();
        helper.setBlock(plain, FnBlocks.CABLE.get());
        BlockPos blue = plain.east();
        helper.setBlock(blue, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.BLUE));
        BlockPos target = blue.east();
        connector(helper, target, Direction.NORTH);
        name(helper, target, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isPresent(),
                "Die Standardfarbe muss sich mit jeder Farbe verbinden");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aBundleDoesNotBridgeColours(GameTestHelper helper) {
        // The actual test: a bundle must not join two strands of the same
        // colour across a foreign colour.
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Green away from the controller
        BlockPos green = controller.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));

        // A bundle with only red behind it — green ends here
        BlockPos redOnly = green.east();
        helper.setBlock(redOnly, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.RED));

        BlockPos behind = redOnly.east();
        connector(helper, behind, Direction.NORTH);
        name(helper, behind, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isEmpty(),
                "Grün darf nicht über einen roten Block hinweg weiterlaufen");
        helper.succeed();
    }

    /** Lays a row of cables and hangs a connector on each end. */
    private static void line(GameTestHelper helper, BlockPos from, int length) {
        for (int i = 0; i < length; i++) {
            helper.setBlock(from.east(i), FnBlocks.CABLE.get());
        }
    }



    /** Builds controller, cable row and three named chests. */
    private static ControllerBlockEntity threeChests(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 4; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        for (int i = 0; i < 3; i++) {
            BlockPos connector = controller.east(i + 2).above();
            connector(helper, connector, Direction.UP);
            helper.setBlock(connector.above(), Blocks.CHEST);
            name(helper, connector, "kiste_" + (i + 1));
        }
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        return controllerAt(helper, controller);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aGroupTakesEveryMatchingConnector(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = threeChests(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                group kisten {
                    members kiste_*
                }

                worker verteile {
                    from storage
                    to kisten
                    filter item:cobblestone
                    rate 3 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.storage().insert(Items.COBBLESTONE, 30);

        helper.runAfterDelay(30, () -> {
            var group = entity.runtime().groups().get("kisten");
            helper.assertTrue(group != null, "Die Gruppe fehlt");
            helper.assertValueEqual(group.members().size(), 3,
                    "Das Muster muss alle drei Kisten treffen");
            helper.assertTrue(entity.storage().count(Items.COBBLESTONE) < 30,
                    "Es muss etwas verteilt worden sein");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void roundRobinSpreadsAcrossMembers(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = threeChests(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                group kisten {
                    members kiste_*
                    strategy round_robin
                }

                worker verteile {
                    from storage
                    to kisten
                    filter item:cobblestone
                    rate 1 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.storage().insert(Items.COBBLESTONE, 12);

        helper.runAfterDelay(60, () -> {
            // Round robin means: not everything lands in the same chest.
            int filled = 0;
            for (int i = 0; i < 3; i++) {
                BlockPos chest = controller.east(i + 2).above(2);
                if (helper.getBlockEntity(chest) instanceof ChestBlockEntity container
                        && !container.getItem(0).isEmpty()) {
                    filled++;
                }
            }
            helper.assertTrue(filled >= 2,
                    "Reihum muss auf mehrere Kisten verteilen, gefüllt: " + filled);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aDisplayShowsWhatTheProgramSays(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
            entity.setDisplayName("lager");
        } else {
            helper.fail("Am Display hängt keine BlockEntity", display);
            return;
        }

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
        // Build the network first: without that the storage does not know its
        // drive, and then it stores nothing.
        controllerEntity.rebuildNetwork();
        controllerEntity.storage().insert(Items.IRON_INGOT, 1234);
        helper.assertTrue(controllerEntity.deploy("""
                display lager {
                    title "Lager"
                    row "Eisen" storage.count(item:iron_ingot)
                    indicator "Kohle" storage.count(item:coal)
                }"""), "Das Programm wurde nicht übernommen");
        controllerEntity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (!(helper.getBlockEntity(display) instanceof DisplayBlockEntity shown)) {
                helper.fail("Display verschwunden", display);
                return;
            }
            var lines = shown.lines();
            helper.assertValueEqual(lines.size(), 3, "Zeilen auf dem Display");
            helper.assertTrue(lines.get(0).contains("Lager"), "Überschrift fehlt");
            // 1234 is shortened to 1,2k — large numbers are unreadable otherwise
            helper.assertTrue(lines.get(1).contains("1,2k"),
                    "Der Bestand fehlt: " + lines.get(1));
            helper.succeed();
        });
    }

    /**
     * {@code scale} makes the text larger, and the line stays one line.
     *
     * <p>The scale belongs to the panel and not into its text: it travels
     * over with the lines so that the client need not know the language —
     * but it writes nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void scaleMakesTheTextBigger(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
            entity.setDisplayName("halle");
        } else {
            helper.fail("Am Display hängt keine BlockEntity", display);
            return;
        }

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
        controllerEntity.rebuildNetwork();
        helper.assertTrue(controllerEntity.deploy("""
                display halle {
                    scale 4
                    title "ERZLAGER"
                }"""), "Das Programm wurde nicht übernommen");
        controllerEntity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (!(helper.getBlockEntity(display) instanceof DisplayBlockEntity shown)) {
                helper.fail("Display verschwunden", display);
                return;
            }
            helper.assertValueEqual(shown.textScale(), 4, "der Maßstab");
            helper.assertValueEqual(shown.lines().size(), 1,
                    "scale ist keine Zeile: " + shown.lines());
            helper.assertTrue(shown.lines().get(0).contains("ERZLAGER"),
                    "die Überschrift fehlt: " + shown.lines());
            helper.succeed();
        });
    }

    /** A nonsensical scale is pulled into the feasible range. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void anabsurdScaleIsPulledIntoRange(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
            entity.setDisplayName("halle");
        }

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
        controllerEntity.rebuildNetwork();
        // Zero would be an invisible panel, a thousand one letter across half
        // the wall. Neither is an error in the program, just a number nobody
        // meant that way.
        helper.assertTrue(controllerEntity.deploy("""
                display halle {
                    scale 0
                    text "unten"
                }"""), "Das Programm wurde nicht übernommen");
        controllerEntity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (helper.getBlockEntity(display) instanceof DisplayBlockEntity shown) {
                helper.assertValueEqual(shown.textScale(), 1, "null wird eins");
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aDisplayWithoutItsDeclarationSaysSo(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
            entity.setDisplayName("gibt_es_nicht");
        }
        controllerAt(helper, controller).rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
                // An empty surface would leave open whether the network is up
                // or the name is wrong. The display says so itself.
                helper.assertTrue(entity.lines().stream()
                                .anyMatch(line -> line.contains("gibt_es_nicht")),
                        "Das Display muss den fehlenden Namen nennen");
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void programCanSwitchRedstone(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn alarm(stärke: Int) {
                    depot.redstone(stärke)
                }"""), "Das Programm wurde nicht übernommen");

        BlockPos connector = controller.east().south();
        entity.callFunction("alarm", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(15)));

        var emitter = partAt(helper, connector);
        if (emitter == null) {
            helper.fail("Kein Anschluss", connector);
            return;
        }
        helper.assertValueEqual(emitter.emittedRedstone(), 15, "gesetzte Stärke");
        // And the block really does pass it on to the outside.
        helper.assertTrue(helper.getLevel().getBestNeighborSignal(
                helper.absolutePos(connector.above())) > 0,
                "Das Signal muss beim Nachbarn ankommen");

        entity.callFunction("alarm", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(0)));
        helper.assertValueEqual(partAt(helper, connector).emittedRedstone(), 0,
                "wieder aus");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void redstoneBeyondFifteenIsRejected(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn zuviel() {
                    depot.redstone(99)
                }"""), "Das Programm wurde nicht übernommen");

        try {
            entity.callFunction("zuviel", java.util.List.of());
            helper.fail("Neunundneunzig hätte auffallen müssen");
        } catch (dev.devpanda.factorynetwork.runtime.ScriptError error) {
            helper.assertTrue(error.getMessage().contains("0 bis 15"),
                    "Die Meldung muss den Bereich nennen: " + error.getMessage());
            helper.succeed();
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFlowWaitsForAnEventAndResumes(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event ChargeDone(amount: Int)

                fn warte() {
                    let vorher = 7
                    let ergebnis = await ChargeDone
                    return vorher
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("warte", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Der Ablauf muss warten");
        // The number from before the wait must survive the halt.
        helper.assertTrue(flow.find("vorher") != null, "Die Variable ist verloren");

        entity.fireEvent("ChargeDone", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(42)));

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Nach dem Ereignis muss er fertig sein");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(), 7L,
                "Der Rückgabewert stammt von vor dem Warten");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anAwaitBindsItsResult(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event ChargeDone(amount: Int)

                fn hole() {
                    let ergebnis = await ChargeDone
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("hole", java.util.List.of());
        entity.fireEvent("ChargeDone", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(99)));

        helper.assertValueEqual(flow.status().name(), "DONE", "Zustand");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(), 99L,
                "Das Ergebnis des Wartens muss ankommen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void sleepPausesAndContinues(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn kurz() {
                    let a = 1
                    sleep 10t
                    return 5
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("kurz", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "SLEEPING", "Er muss schlafen");

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(flow.status().name(), "DONE",
                    "Nach der Wartezeit muss er fertig sein");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whereDecidesWhoWakesUp(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Fertig(id: Int)

                fn wartetAuf(ziel: Int) {
                    let ergebnis = await Fertig where id == ziel
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var ersterAblauf = entity.startFlow("wartetAuf",
                java.util.List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(1)));
        var zweiterAblauf = entity.startFlow("wartetAuf",
                java.util.List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(2)));

        entity.fireEvent("Fertig", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(2)));

        helper.assertValueEqual(ersterAblauf.status().name(), "AWAITING",
                "Der Ablauf mit ziel=1 darf nicht aufwachen");
        helper.assertValueEqual(zweiterAblauf.status().name(), "DONE",
                "Der Ablauf mit ziel=2 muss aufwachen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aTimeoutRunsItsElseBranch(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Nie()

                fn gibtAuf() {
                    let ergebnis = await Nie timeout 5t else {
                        return 3
                    }
                    return 9
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("gibtAuf", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Zuerst wartet er");

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(flow.status().name(), "DONE",
                    "Der else-Zweig verlässt den Ablauf ordentlich");
            helper.assertValueEqual(
                    ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(), 3L,
                    "Gelaufen ist der else-Zweig, nicht die Zeile dahinter");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void awaitInsideNestedBlocksResumesEachRound(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Here await sits in an if inside a while — exactly the nesting on
        // which finding the frames again proves itself.
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn zaehlt() {
                    let summe = 0
                    let runde = 0
                    while runde < 3 {
                        if runde >= 0 {
                            let wert = await Takt
                            summe = summe + wert
                        }
                        runde = runde + 1
                    }
                    return summe
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        for (int runde = 1; runde <= 3; runde++) {
            helper.assertValueEqual(flow.status().name(), "AWAITING",
                    "Vor Runde " + runde + " muss er warten");
            entity.fireEvent("Takt", java.util.List.of(
                    new dev.devpanda.factorynetwork.runtime.Value.Int(runde)));
        }

        helper.assertValueEqual(flow.status().name(), "DONE", "Nach drei Runden ist Schluss");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(), 6L,
                "1 + 2 + 3 muss zusammenkommen");
        helper.succeed();
    }

    /**
     * {@code network.power} reads the reserve of the running network.
     *
     * <p>The arithmetic behind it is in the unit test; here it is about the
     * <b>wiring</b>. The controller holds the reserve, the expression asks the
     * host, and a forgotten {@code setPower} would otherwise show up nowhere:
     * without a world the expression reports honestly, with a world one would
     * see the same message and take it for correct.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void networkPowerReadsTheRunningNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.power().fill(10_000);

        helper.assertTrue(entity.deploy("""
                fn vorrat() {
                    return network.power
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("vorrat", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE", "Der Ablauf muss durchlaufen");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(),
                (long) entity.power().stored(),
                "und dieselbe Zahl liefern, die der Controller hält");
        helper.succeed();
    }

    /**
     * And a worker may ask about it: {@code when network.power > …}.
     *
     * <p>The manual promises exactly this line — a worker that stops before
     * the network goes out. It hangs on the same wiring as above, but on a
     * different path to it: a worker's condition is evaluated by the runtime,
     * not by a flow.
     *
     * <p><b>The status is checked, not just the standstill.</b> A worker that
     * cannot evaluate the condition at all stands still too — but then on
     * {@code HALTED}. The difference is the whole point.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aworkerCanAskForTheNetworkReserve(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        if (helper.getBlockEntity(controller.east().north().north())
                instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        } else {
            helper.fail("Keine Kiste als Quelle");
        }

        // A threshold no network reaches: the worker must be able to read it
        // and still must not work.
        helper.assertTrue(entity.deploy("""
                worker holen {
                    from quarry_output
                    to storage
                    filter item:cobblestone
                    when network.power > 999999999
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var state = entity.runtime().states().get("holen");
                    helper.assertValueEqual(state.status.name(), "WAITING_CONDITION",
                            "die Bedingung muss ablesbar und unerfüllt sein, nicht kaputt: "
                                    + state.detail);
                    helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 0L,
                            "und nichts bewegt haben");
                })
                .thenSucceed();
    }

    /** And {@code network.capacity}, how much fits in. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void networkCapacityReadsWhatFits(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn platz() {
                    return network.capacity
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("platz", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE", "Der Ablauf muss durchlaufen");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value(),
                (long) entity.power().capacity(),
                "und die Bezugsgröße liefern, nicht den Stand");
        helper.succeed();
    }

    /**
     * {@code hebel.click()} touches the machine the connector points at.
     *
     * <p>Some machines do nothing until someone touches them — and for those
     * there was no handle so far. <b>No second block:</b> input and output are
     * already separated here by the code and not by the block shape, and the
     * same goes for a third capability.
     *
     * <p>Tested on a lever, and that is deliberate: it is the only vanilla
     * response to a click that you can <b>see</b> without opening a window —
     * its state flips, and that is stored in the block.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void clickTouchesTheMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos hebel = controller.east().north().north();
        helper.setBlock(hebel, Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn druecken() {
                    return quarry_output.click()
                }"""), "Das Programm wurde nicht übernommen");

        boolean vorher = helper.getBlockState(hebel)
                .getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
        var flow = entity.startFlow("druecken", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf muss durchlaufen: " + flow.detail());
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Bool) flow.result()).value(), true,
                "und melden, dass der Klick angekommen ist");
        helper.assertValueEqual(helper.getBlockState(hebel)
                        .getValue(net.minecraft.world.level.block.LeverBlock.POWERED),
                !vorher, "der Hebel muss umgelegt sein");
        helper.succeed();
    }

    /**
     * And on a block that a click does not interest, it reports that.
     *
     * <p>Not an error: a stone that does not react to a right-click is not a
     * broken program. But {@code false} instead of {@code true}, so that a
     * flow waiting for an effect notices.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void clickOnastoneChangesNothing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().north().north(), Blocks.STONE);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn druecken() {
                    return quarry_output.click()
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("druecken", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf muss durchlaufen: " + flow.detail());
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Bool) flow.result()).value(), false,
                "und melden, dass nichts geschehen ist");
        helper.succeed();
    }

    /**
     * A foreign line may <b>draw</b> from the controller, not just feed in.
     *
     * <p>Until now the connection only accepted. Whoever wanted power from the
     * network in another system needed a worker into an energy cube and the
     * foreign connection on that — and exactly this detour costs transfer
     * rate, because the cube has its own.
     *
     * <p>Now it goes directly: a Flux Plug, a cable, a consumer draws from the
     * controller. <b>Without a rate limit</b> — that applied to the intake,
     * and for the output it would be exactly what gets in the way here.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aforeignLineMayDrawFromTheController(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.power().fill(entity.power().capacity());

        var port = entity.power().port();
        int vorher = port.getEnergyStored();
        helper.assertTrue(port.canExtract(), "Der Anschluss muss herausgeben");

        int gezogen = port.extractEnergy(5_000, false);

        helper.assertValueEqual(gezogen, 5_000, "und zwar so viel, wie verlangt wurde");
        helper.assertValueEqual(port.getEnergyStored(), vorher - 5_000,
                "und der Vorrat muss um genau das kleiner sein");
        helper.succeed();
    }

    /**
     * And it stops before the network switches itself off.
     *
     * <p>The one limit that remains — and it is not a rate but a floor.
     * Without it a foreign line would pull the network below the start-up
     * threshold, it would go out, boot for three seconds, go out again: a
     * flicker that looks like a bug and is none.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void drawingStopsAtTheFloor(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.power().fill(entity.power().capacity());

        var port = entity.power().port();
        port.extractEnergy(Integer.MAX_VALUE, false);

        helper.assertTrue(entity.power().stored() > 0,
                "das Netz darf sich nicht selbst leersaugen lassen");
        helper.assertValueEqual(port.extractEnergy(1_000, false), 0,
                "und am Boden gibt es nichts mehr");
        helper.succeed();
    }

    /**
     * {@code store kiste_1 { }} — what lies in the chest belongs to the network.
     *
     * <p>The storage bus as AE2 has it, only without a block of its own. The
     * difference from an ordinary device is the whole point: without this
     * line the chest is something you fetch from with {@code move}; with it
     * its contents count toward the stock that every job and every display
     * sees.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void astoreCountsTowardsTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        if (helper.getBlockEntity(controller.east().north().north())
                instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_ORE, 12));
        } else {
            helper.fail("Keine Kiste als Speicher");
        }

        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                "vorher gehört die Kiste sich selbst");

        helper.assertTrue(entity.deploy("""
                store quarry_output {
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 12L,
                "danach zählt ihr Inhalt zum Netz");
        helper.assertTrue(entity.storage().byItem().containsKey(Items.IRON_ORE),
                "und steht im Bestand, den das Terminal zeigt");
        helper.succeed();
    }

    /**
     * And what somebody puts in, the network sees at the next look.
     *
     * <p>The difference between a copy and a view: a chest changes without the
     * network — a player empties it, a hopper fills it. A stock that only
     * notices after a rebuild would be wrong, and a job that counts on it
     * would leave half-finished work behind.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void astoreSeesWhatSomebodyPutsIn(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                store quarry_output {
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenExecute(() -> {
                    if (helper.getBlockEntity(controller.east().north().north())
                            instanceof ChestBlockEntity chest) {
                        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                    } else {
                        helper.fail("Keine Kiste als Speicher");
                    }
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.COBBLESTONE), 5L,
                        "was hineinkommt, gehört ab dann dem Netz"))
                .thenSucceed();
    }

    /**
     * What the network puts away may land in the chest.
     *
     * <p>The other half of the storage bus, and without it the first would be
     * dangerous: a stock you can see and cannot touch throws off every job
     * that counts on it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void astoreTakesWhatTheNetworkPutsAway(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // No drive, no cell — the chest is the entire storage.
        helper.assertTrue(entity.deploy("""
                store quarry_output {
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        long rest = entity.storage().insert(Items.IRON_ORE, 7);

        helper.assertValueEqual(rest, 0L, "alles muss untergekommen sein");
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 7L,
                "und im Bestand stehen");
        if (helper.getBlockEntity(controller.east().north().north())
                instanceof ChestBlockEntity chest) {
            helper.assertValueEqual(chest.getItem(0).getCount(), 7,
                    "und wirklich in der Kiste liegen");
        } else {
            helper.fail("Keine Kiste als Speicher");
        }
        helper.succeed();
    }

    /** And the network fetches it back out of there too. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void astoreGivesBackWhatTheNetworkAsksFor(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        if (helper.getBlockEntity(controller.east().north().north())
                instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_ORE, 9));
        } else {
            helper.fail("Keine Kiste als Speicher");
        }

        helper.assertTrue(entity.deploy("""
                store quarry_output {
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        long geholt = entity.storage().extract(Items.IRON_ORE, 4);

        helper.assertValueEqual(geholt, 4L, "vier davon muss es hergeben");
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 5L,
                "und der Bestand muss nachziehen");
        helper.succeed();
    }

    /**
     * A {@code filter} says what may go into the chest.
     *
     * <p>As in AE2: the bus only accepts what is listed. What already lies
     * inside still counts toward the stock — concealing it because it does
     * not match the filter would be a lie about something everyone can see.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void afilterSaysWhatMayGoIn(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                store quarry_output {
                    filter item:iron_ore
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.storage().insert(Items.IRON_ORE, 3), 0L,
                "Erz gehört hinein");
        helper.assertValueEqual(entity.storage().insert(Items.COBBLESTONE, 3), 3L,
                "Kopfsteinpflaster nicht — es kommt zurück");
        helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 0L,
                "und steht in keinem Bestand");
        helper.succeed();
    }

    /** What already lies inside also counts against the filter. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void whatIsAlreadyInsideStillCounts(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        if (helper.getBlockEntity(controller.east().north().north())
                instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        } else {
            helper.fail("Keine Kiste als Speicher");
        }

        helper.assertTrue(entity.deploy("""
                store quarry_output {
                    filter item:iron_ore
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 8L,
                "was dort liegt, ist da — Filter hin oder her");
        helper.succeed();
    }

    /**
     * A connector from below on a furnace, with coal in the fuel slot.
     *
     * <p>From below a furnace shows its output slot and its fuel slot. Showing
     * is not handing over, though: it keeps the fuel to itself from below —
     * {@code canTakeItemThroughFace} says exactly that, and the comments on
     * the storage bus explicitly call it not an error but a machine keeping
     * its rules.
     *
     * <p>That gives a stock the network sees and cannot touch. Exactly what
     * every test needs that wants to know whether a spot reads the result of
     * {@code extract} or merely hopes.
     */
    private static void furnaceStore(GameTestHelper helper, BlockPos controller) {
        furnaceStoreAt(helper, controller.east().above());
    }

    /** The same furnace, but at a chosen spot on the cable. */
    private static void furnaceStoreAt(GameTestHelper helper, BlockPos below) {
        connector(helper, below, Direction.UP);
        helper.setBlock(below.above(), Blocks.FURNACE);
        name(helper, below, "ofen");
        if (helper.getBlockEntity(below.above())
                instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace) {
            furnace.setItem(1, new ItemStack(Items.COAL, 64));
        } else {
            helper.fail("kein Ofen am Anschluss", below.above());
        }
    }

    /** All coal in the test world: what is in the furnace and what is in the target. */
    private static long coalInWorld(GameTestHelper helper, BlockPos controller) {
        long found = 0;
        if (helper.getBlockEntity(controller.east().above().above())
                instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace) {
            for (int slot = 0; slot < furnace.getContainerSize(); slot++) {
                if (furnace.getItem(slot).is(Items.COAL)) {
                    found += furnace.getItem(slot).getCount();
                }
            }
        }
        if (helper.getBlockEntity(controller.east().south().south())
                instanceof ChestBlockEntity depot) {
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                if (depot.getItem(slot).is(Items.COAL)) {
                    found += depot.getItem(slot).getCount();
                }
            }
        }
        return found;
    }

    /**
     * <b>What the storage does not hand over must not arrive in the target.</b>
     *
     * <p>The counterpart to item loss, and the more expensive of the two:
     * {@code move} first puts into the chest, then fetches the same amount
     * from the storage. A storage bus on a furnace shows its coal and does not
     * hand it over — afterwards it lies there twice, in the furnace and in
     * the chest.
     *
     * <p>And not just once: the stock does not follow, because nothing came
     * out. On the next call the same coal is ready again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void moveTakesOnlyWhatTheStorageGives(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        furnaceStore(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                store ofen {
                }

                fn holt() {
                    move 64 item:coal from storage to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(5)
                // This line is no decoration: if the stock does not see the
                // coal at all, the rest checks nothing.
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.COAL), 64L,
                        "der Bestand muss die Kohle des Ofens zeigen"))
                .thenExecute(() -> entity.startFlow("holt", List.of()))
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(
                        coalInWorld(helper, controller), 64L,
                        "aus vierundsechzig Kohle sind mehr geworden"))
                .thenSucceed();
    }

    /** And the worker walks the same path on its own track. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void workerTakesOnlyWhatTheStorageGives(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        furnaceStore(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                store ofen {
                }

                worker holt {
                    from storage
                    to depot
                    filter item:coal
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(coalInWorld(helper, controller), 64L,
                    "aus vierundsechzig Kohle sind mehr geworden");
            // And the worker says what is going on. "Nothing to do" would be
            // the most wrong answer of all: it grasps at nothing every tick.
            var state = entity.runtime().states().get("holt");
            helper.assertTrue(state != null, "Der Worker hat keinen Zustand");
            helper.assertValueEqual(state.status.name(), "HALTED",
                    "der Worker muss das melden, nicht schweigen");
            helper.succeed();
        });
    }

    /**
     * The way back itself, at both ends.
     *
     * <p>{@code pullBack} is the one place that pays the price for the order
     * "insert first, then extract". It has to do two things: really fetch
     * everything back out of a chest, and for a slot that hands nothing over,
     * <b>say that it could not</b>. The second part is the more important —
     * it is the number the caller must not count as moved.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pullBackSaysWhatStaysInside(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        controllerAt(helper, controller);
        furnaceStore(helper, controller);

        var key = dev.devpanda.factorynetwork.storage.ItemKey.of(new ItemStack(Items.COAL));

        // Everything comes back out of the chest.
        BlockPos depot = controller.east().south();
        IItemHandler chest = partAt(helper, depot).machineInventory();
        dev.devpanda.factorynetwork.runtime.Handoffs.insertInto(chest,
                new ItemStack(Items.COAL, 32));
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.Handoffs.pullBack(chest, key, 32), 0L,
                "aus einer Kiste muss alles zurückkommen");
        helper.assertValueEqual(countIn(helper, depot.south()), 0,
                "und die Kiste danach leer sein");

        // Nothing comes back out of the fuel slot from below, and exactly this
        // number is the answer.
        IItemHandler furnace = partAt(helper, controller.east().above()).machineInventory();
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.Handoffs.pullBack(furnace, key, 64), 64L,
                "der Ofen rückt von unten nichts heraus — das muss dastehen");
        helper.succeed();
    }

    /**
     * An inventory that promises more than it hands over.
     *
     * <p>The simulation says sixty-four, the real grab delivers thirty-two.
     * That such a thing exists has been in the source for a while — the way
     * back at the network storage is explicitly there for it. Between two
     * devices it was missing, and there the error is the more expensive
     * direction: what the target already has and the source keeps exists
     * twice afterwards.
     */
    private record Boasting(IItemHandler inner) implements IItemHandler {

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inner.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return simulate
                    ? inner.extractItem(slot, amount, true)
                    : inner.extractItem(slot, amount / 2, false);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }

    /** An inventory that accepts and hands nothing back out — like an input slot. */
    private record Keeping(IItemHandler inner) implements IItemHandler {

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inner.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }

    private static long countIn(IItemHandler handler) {
        long found = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            found += handler.getStackInSlot(slot).getCount();
        }
        return found;
    }

    /**
     * <b>Nothing may come into being between two devices.</b>
     *
     * <p>The grab inserts first and extracts afterwards — the order built
     * against loss. If the source hands over less in the real grab than in
     * the simulation, the difference is already in the target and came out of
     * nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void nothingIsBornBetweenTwoDevices(GameTestHelper helper) {
        var inner = new net.neoforged.neoforge.items.ItemStackHandler(1);
        inner.setStackInSlot(0, new ItemStack(Items.COAL, 64));
        IItemHandler source = new Boasting(inner);
        IItemHandler target = new net.neoforged.neoforge.items.ItemStackHandler(1);

        var done = dev.devpanda.factorynetwork.runtime.Handoffs.items(
                source, target, List.of(), 64);

        helper.assertValueEqual(countIn(source) + countIn(target), 64L,
                "aus vierundsechzig Kohle sind mehr geworden");
        helper.assertValueEqual(done.moved(), 32L,
                "bewegt ist nur, was die Quelle wirklich hergegeben hat");
        helper.succeed();
    }

    /**
     * And if the way back is closed too, at least it is stated.
     *
     * <p>An input slot hands nothing out. Then the difference stays in the
     * target — the gap cannot be closed here, only reported. Reported it must
     * be: counted as moved it would be a number every job builds on and that
     * does not exist.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void whatCannotComeBackIsSaidOutLoud(GameTestHelper helper) {
        var inner = new net.neoforged.neoforge.items.ItemStackHandler(1);
        inner.setStackInSlot(0, new ItemStack(Items.COAL, 64));
        IItemHandler source = new Boasting(inner);
        IItemHandler target = new Keeping(
                new net.neoforged.neoforge.items.ItemStackHandler(1));

        var done = dev.devpanda.factorynetwork.runtime.Handoffs.items(
                source, target, List.of(), 64);

        helper.assertValueEqual(done.moved(), 32L,
                "bewegt ist nur, was die Quelle wirklich hergegeben hat");
        helper.assertValueEqual(done.stranded(), 32L,
                "und was nicht zurückkam, muss dastehen");
        helper.succeed();
    }

    /**
     * A tank that promises more than it hands over.
     *
     * <p>The same as {@link Boasting}, only fluid — and here the way back is
     * narrower: nothing goes back into the source, because a tank need not
     * accept again.
     */
    private record BoastingTank(
            net.neoforged.neoforge.fluids.capability.IFluidHandler inner)
            implements net.neoforged.neoforge.fluids.capability.IFluidHandler {

        @Override
        public int getTanks() {
            return inner.getTanks();
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return inner.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return inner.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return inner.isFluidValid(tank, stack);
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return inner.fill(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(
                net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return action.simulate()
                    ? inner.drain(resource, action)
                    : inner.drain(resource.copyWithAmount(resource.getAmount() / 2), action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return inner.drain(action.simulate() ? maxDrain : maxDrain / 2, action);
        }
    }

    /** A tank that accepts and hands nothing back out. */
    private record KeepingTank(
            net.neoforged.neoforge.fluids.capability.IFluidHandler inner)
            implements net.neoforged.neoforge.fluids.capability.IFluidHandler {

        @Override
        public int getTanks() {
            return inner.getTanks();
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return inner.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return inner.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return inner.isFluidValid(tank, stack);
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return inner.fill(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(
                net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
    }

    private static long millibucketsIn(
            net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {
        long found = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            found += handler.getFluidInTank(tank).getAmount();
        }
        return found;
    }

    /**
     * <b>Nothing may come into being between two tanks.</b>
     *
     * <p>The same grab as with items and the same price for its order: first
     * the target fills, then the source drains. If it hands over less than in
     * the simulation, the difference is already in the target.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void nothingIsBornBetweenTwoTanks(GameTestHelper helper) {
        var inner = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000);
        inner.fill(new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        net.neoforged.neoforge.fluids.capability.IFluidHandler source = new BoastingTank(inner);
        var target = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000);

        var done = dev.devpanda.factorynetwork.runtime.Handoffs.fluid(source, target,
                new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 1000));

        helper.assertValueEqual(millibucketsIn(source) + millibucketsIn(target), 1000L,
                "aus einem Eimer Wasser sind mehr geworden");
        helper.assertValueEqual(done.moved(), 500L,
                "bewegt ist nur, was die Quelle wirklich hergegeben hat");
        helper.succeed();
    }

    /** And a target that gives nothing back leaves a number behind. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void whatCannotFlowBackIsSaidOutLoud(GameTestHelper helper) {
        var inner = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000);
        inner.fill(new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        net.neoforged.neoforge.fluids.capability.IFluidHandler source = new BoastingTank(inner);
        net.neoforged.neoforge.fluids.capability.IFluidHandler target = new KeepingTank(
                new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000));

        var done = dev.devpanda.factorynetwork.runtime.Handoffs.fluid(source, target,
                new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 1000));

        helper.assertValueEqual(done.moved(), 500L,
                "bewegt ist nur, was die Quelle wirklich hergegeben hat");
        helper.assertValueEqual(done.stranded(), 500L,
                "und was nicht zurückkam, muss dastehen");
        helper.succeed();
    }

    /**
     * <b>Crafting must not build what it has not paid for.</b>
     *
     * <p>The step checks the stock, extracts, and inserts the result. Two of
     * those are views: a storage bus counts foreign inventories too, and they
     * may keep their contents. If the coal stays in the furnace while the coal
     * block comes into being, crafting is a source out of nothing — and
     * because the stock does not follow, an inexhaustible one.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void craftingPaysForWhatItBuilds(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        furnaceStoreAt(helper, controller.east().east());
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                store ofen {
                }

                fn bestellen() {
                    return craft(1 item:coal_block)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.COAL), 64L,
                        "der Bestand muss die Kohle des Ofens zeigen"))
                .thenExecute(() -> entity.callFunction("bestellen", List.of()))
                .thenIdle(120)
                .thenExecute(() -> {
                    long imOfen = 0;
                    if (helper.getBlockEntity(controller.east().east().above())
                            instanceof net.minecraft.world.level.block.entity
                                    .FurnaceBlockEntity furnace) {
                        for (int slot = 0; slot < furnace.getContainerSize(); slot++) {
                            if (furnace.getItem(slot).is(Items.COAL)) {
                                imOfen += furnace.getItem(slot).getCount();
                            }
                        }
                    }
                    // Nine coal per block — the recipe's arithmetic, backwards.
                    long gebaut = entity.storage().count(Items.COAL_BLOCK) * 9;
                    helper.assertValueEqual(imOfen + gebaut, 64L,
                            "aus vierundsechzig Kohle sind mehr geworden");
                })
                .thenSucceed();
    }

    /**
     * And a recipe at a machine does not even start without its ingredient.
     *
     * <p>The same storage that shows and does not hand over, at the other half
     * of crafting. Nothing comes out of nothing here — the network fetches
     * the result from the machine, and a machine without an ingredient
     * delivers none. The damage is a different one: the job would stand on
     * "running" forever, waiting for a delivery that can never come. And the
     * water of a recipe would already have been poured in.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void arecipeWaitsWhenTheStorageOnlyShows(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        furnaceStoreAt(helper, controller.east().east());
        entity.rebuildNetwork();

        // Coal to diamond: vanilla does not know that, so no other path leads
        // to the target than this recipe at this machine.
        helper.assertTrue(entity.deploy("""
                store ofen {
                }

                recipe kohle_pressen at quarry_output {
                    in 9 item:coal
                    out 1 item:diamond
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.requestCraft(Items.DIAMOND, 1);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var job = entity.craftingJobs().get(0);
                    helper.assertTrue(job.running() == null,
                            "ohne Zutat darf kein Schritt laufen: " + job.detail());
                })
                .thenSucceed();
    }

    /**
     * A fluid with data components comes out of no tank.
     *
     * <p>Neither dupe nor loss — simply nothing happens, and that is exactly
     * the unpleasant part: the worker reports "nothing to do" at a full tank,
     * and nobody can tell from that what the cause is.
     *
     * <p><b>The cause:</b> the caller rebuilds the request from the kind —
     * {@code new FluidStack(inside.getFluid(), n)} —, and the components get
     * left behind. A tank compares with {@code isSameFluidSameComponents} and
     * answers a request it does not recognise with nothing.
     *
     * <p>This test run pins down the current state <b>and the fix beside
     * it</b>: the same request with {@code copyWithAmount} goes through. What
     * speaks against simply building it is in {@code naechste-schritte.md} —
     * the network storage is keyed by kind and cannot hold components, so the
     * route into the store would stay closed. Both together are a decision
     * and not a bug fix.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void fluidWithComponentsStaysWhereItIs(GameTestHelper helper) {
        var source = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000);
        var broth = new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 1000);
        broth.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Sud"));
        source.fill(broth,
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var target = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(2000);

        // A copy: with FluidTank, getFluidInTank returns the internals, and
        // those are about to change under our hands.
        var inside = source.getFluidInTank(0).copy();
        helper.assertTrue(!inside.getComponentsPatch().isEmpty(),
                "der Tank hält die Komponenten nicht — dann prüft das hier nichts");

        // This is how the caller asks today.
        var stripped = dev.devpanda.factorynetwork.runtime.Handoffs.fluid(source, target,
                new net.neoforged.neoforge.fluids.FluidStack(inside.getFluid(), 1000));
        helper.assertValueEqual(stripped.moved(), 0L,
                "eine Anfrage ohne Komponenten darf den Tank nicht leeren");
        helper.assertValueEqual(millibucketsIn(target), 0L, "und das Ziel bleibt leer");
        helper.assertValueEqual(millibucketsIn(source), 1000L, "die Brühe steht noch da");

        // And this is how it would work: with what is really inside.
        var faithful = dev.devpanda.factorynetwork.runtime.Handoffs.fluid(source, target,
                inside.copyWithAmount(1000));
        helper.assertValueEqual(faithful.moved(), 1000L,
                "mit den Komponenten geht dieselbe Anfrage durch");
        helper.assertTrue(!target.getFluidInTank(0).getComponentsPatch().isEmpty(),
                "und kommt mit ihnen an");
        helper.succeed();
    }

    /**
     * <b>Every block survives its own update packet.</b>
     *
     * <p>When a block is placed, the server sends a
     * {@code ClientboundBlockEntityDataPacket} with what {@code getUpdateTag}
     * hands over, and the client reads it with {@code loadAdditional}. If the
     * two do not match — one does not write a field that the other reads
     * unconditionally —, the client throws while reading and <b>is kicked out
     * of the world</b>: "Network Protocol Error", in the middle of building.
     *
     * <p>That is exactly what the press did. Its {@code getUpdateTag} left out
     * the energy, its {@code loadAdditional} read it without asking, and
     * NeoForge answers a missing tag with an exception.
     *
     * <p>That is why this run checks <b>all</b> blocks of the mod and not the
     * one: the bug does not live in the press but in the gap between two
     * methods, which every new block can open up again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void everyBlockSurvivesItsOwnUpdateTag(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<net.minecraft.world.level.block.Block> blocks = List.of(
                FnBlocks.PRESS.get(), FnBlocks.BURNER.get(), FnBlocks.CABLE.get(),
                FnBlocks.DENSE_CABLE.get(), FnBlocks.CONTROLLER.get(),
                FnBlocks.CONTROLLER_EXTENSION.get(), FnBlocks.DISPLAY.get(),
                FnBlocks.GATEWAY.get(), FnBlocks.MAST.get(), FnBlocks.ROUTER.get(),
                FnBlocks.DRIVE.get(), FnBlocks.RACK.get(), FnBlocks.FABRICATOR.get(),
                FnBlocks.BRIDGE.get(), FnBlocks.TERMINAL.get());
        BlockPos at = new BlockPos(1, 1, 1);
        int checked = 0;
        for (var block : blocks) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(block).getPath();
            helper.setBlock(at, block);
            // Via the level and not via helper.getBlockEntity: that reports a
            // failure where skipping ahead is the right thing here.
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(at));
            if (entity == null) {
                // A block without a BlockEntity does not send one either.
                continue;
            }
            try {
                // Exactly the client's path: read its own update packet.
                entity.handleUpdateTag(entity.getUpdateTag(registries), registries);
                checked++;
            } catch (RuntimeException broken) {
                helper.fail(name + " überlebt sein eigenes Update-Paket nicht: "
                        + broken, at);
                return;
            }
            helper.setBlock(at, Blocks.AIR);
        }
        // Otherwise the run would be green because it touched almost nothing:
        // a block that setBlock does not equip with its BlockEntity silently
        // falls through above.
        helper.assertTrue(checked >= 12,
                "nur " + checked + " Blöcke geprüft — der Lauf misst zu wenig");
        helper.succeed();
    }

    /**
     * <b>A machine you cannot feed is not one.</b>
     *
     * <p>The press accepts power — that is in FnCapabilities and is tested.
     * Whether it also accepts material was stated nowhere: no test run has
     * ever sent it an iron ingot. And without an item capability a connector
     * finds no inventory, no matter how good the window looks.
     *
     * <p>That is the condition under which this mod is built at all: what a
     * machine can do, a program must be able to trigger.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aMachineTakesItsMaterialFromTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos maschine = controller.east().north().north();
        helper.setBlock(maschine, FnBlocks.PRESS.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 8);

        helper.assertTrue(entity.deploy("""
                worker beschickung {
                    from storage
                    to quarry_output
                    filter item:iron_ingot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(40, () -> {
            if (!(helper.getBlockEntity(maschine)
                    instanceof dev.devpanda.factorynetwork.block.entity.PressBlockEntity presse)) {
                helper.fail("Da steht keine Presse", maschine);
                return;
            }
            long drin = 0;
            for (int slot = 0; slot < dev.devpanda.factorynetwork.block.entity
                    .PressBlockEntity.SLOTS; slot++) {
                if (presse.item(slot).is(Items.IRON_INGOT)) {
                    drin += presse.item(slot).getCount();
                }
            }
            helper.assertTrue(drin > 0,
                    "Der Worker hat der Presse nichts geben können — sie hat kein "
                            + "Inventar nach außen");
            helper.succeed();
        });
    }

    /** The press at this spot, with power and a stamp. */
    private static dev.devpanda.factorynetwork.block.entity.PressBlockEntity press(
            GameTestHelper helper, BlockPos at, net.minecraft.world.item.Item stamp) {
        helper.setBlock(at, FnBlocks.PRESS.get());
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.PressBlockEntity presse)) {
            helper.fail("Da steht keine Presse", at);
            throw new IllegalStateException();
        }
        // <b>Fill in portions.</b> The buffer takes at most two thousand per
        // call — that is the throttle which prevents a press from paying for
        // its work in one tick. Whoever wants to fill it in one go in truth
        // fills it to two thousand, and then it stalls at every recipe that
        // costs more.
        for (int i = 0; i < 30; i++) {
            presse.energy().receiveEnergy(
                    dev.devpanda.factorynetwork.block.entity.PressBlockEntity.CAPACITY, false);
        }
        presse.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_STAMP,
                new ItemStack(stamp));
        return presse;
    }

    /** What lies in the output slot. */
    private static ItemStack pressResult(
            dev.devpanda.factorynetwork.block.entity.PressBlockEntity presse) {
        return presse.item(
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT);
    }

    /**
     * <b>A recipe with three ingredients, inserted in any order.</b>
     *
     * <p>Since 30 Aug the logic core needs a plate, four redstone and one
     * copper. Which ingredient lies in which slot may not matter: whoever
     * stands in front of a machine does not sort by an order that is written
     * nowhere.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thepressTakesThreeIngredientsInAnyOrder(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        var presse = press(helper, at, FnItems.STAMP_LOGIC.get());
        int first = dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL;
        // Deliberately the wrong way round: copper, plate, redstone.
        presse.setItem(first, new ItemStack(Items.COPPER_INGOT, 1));
        presse.setItem(first + 1, new ItemStack(FnItems.PLATE.get(), 1));
        presse.setItem(first + 2, new ItemStack(Items.REDSTONE, 4));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(pressResult(presse).is(FnItems.CORE_LOGIC.get()),
                    "kein Logikkern entstanden — die Reihenfolge darf nicht zählen");
            helper.assertTrue(presse.item(first).isEmpty()
                            && presse.item(first + 1).isEmpty()
                            && presse.item(first + 2).isEmpty(),
                    "jede Zutat muss aus ihrem eigenen Platz verbraucht sein");
            helper.succeed();
        });
    }

    /**
     * If an ingredient is missing, nothing happens — and the others stay put.
     *
     * <p>The cross-check to the test above. Without it, it would not be
     * settled whether the press really demands all three or just happens to
     * produce something.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thepressWaitsForEveryIngredient(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        var presse = press(helper, at, FnItems.STAMP_LOGIC.get());
        int first = dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL;
        presse.setItem(first, new ItemStack(FnItems.PLATE.get(), 1));
        presse.setItem(first + 1, new ItemStack(Items.REDSTONE, 4));
        // The copper is missing.

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(pressResult(presse).isEmpty(),
                    "ohne Kupfer darf kein Kern entstehen");
            helper.assertValueEqual(presse.item(first).getCount(), 1,
                    "und die Platte muss unangetastet liegen bleiben");
            helper.succeed();
        });
    }

    /**
     * Too little of an ingredient is like none at all.
     *
     * <p>The recipe demands four redstone. With three the press must not
     * start — otherwise it would stand there with half-consumed material.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thepressCountsTheAmount(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        var presse = press(helper, at, FnItems.STAMP_LOGIC.get());
        int first = dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL;
        presse.setItem(first, new ItemStack(FnItems.PLATE.get(), 1));
        presse.setItem(first + 1, new ItemStack(Items.REDSTONE, 3));
        presse.setItem(first + 2, new ItemStack(Items.COPPER_INGOT, 1));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(pressResult(presse).isEmpty(),
                    "drei Redstone sind nicht vier");
            helper.assertValueEqual(presse.item(first + 1).getCount(), 3,
                    "und angerührt wurden sie auch nicht");
            helper.succeed();
        });
    }

    /**
     * <b>The batch card makes three crystals instead of one.</b>
     *
     * <p>And it consumes threefold. That is the whole trade: no time saved,
     * but the runs that would otherwise have gone one after the other.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void abatchCardMakesThreeAtOnce(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        var presse = press(helper, at, FnItems.STAMP_PLATE.get());
        // Exactly three: that way at most one run can happen, and the number
        // at the end is that of one run and not that of the waiting.
        presse.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL,
                new ItemStack(FnItems.RAW_CRYSTAL.get(), 3));
        presse.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_UPGRADE,
                new ItemStack(FnItems.BATCH_CARD.get(), 2));

        helper.runAfterDelay(200, () -> {
            helper.assertValueEqual(pressResult(presse).getCount(), 3,
                    "zwei Stapelkarten machen drei Kristalle je Durchlauf");
            helper.assertTrue(presse.item(
                            dev.devpanda.factorynetwork.block.entity
                                    .PressBlockEntity.SLOT_MATERIAL).isEmpty(),
                    "und verbrauchen alle drei Rohkristalle dafür");
            helper.succeed();
        });
    }

    /**
     * The acceleration card arrives earlier.
     *
     * <p>Not the time is measured but the lead: after the same number of
     * ticks the equipped press is done and the bare one is not yet. A
     * measurement in ticks would be one about the arithmetic, and that lives
     * elsewhere.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anaccelerationCardArrivesFirst(GameTestHelper helper) {
        var schnell = press(helper, new BlockPos(1, 1, 1), FnItems.STAMP_PLATE.get());
        var normal = press(helper, new BlockPos(3, 1, 1), FnItems.STAMP_PLATE.get());
        int material = dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL;
        schnell.setItem(material, new ItemStack(Items.IRON_INGOT, 1));
        normal.setItem(material, new ItemStack(Items.IRON_INGOT, 1));
        schnell.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_UPGRADE,
                new ItemStack(FnItems.ACCELERATION_CARD.get(), 4));

        // The bare recipe needs 60 ticks, the accelerated one 41.
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(pressResult(schnell).is(FnItems.PLATE.get()),
                    "die bestückte Presse muss nach 50 Ticks fertig sein");
            helper.assertTrue(pressResult(normal).isEmpty(),
                    "die nackte darf es nicht sein — sonst misst dieser Lauf nichts");
            helper.succeed();
        });
    }

    /** A program with await in if in while — the template for the flow tests. */
    private static final String COUNTING_PROGRAM = """
            event Takt(nummer: Int)

            fn zaehlt() {
                let summe = 0
                let runde = 0
                while runde < 3 {
                    if runde >= 0 {
                        let wert = await Takt
                        summe = summe + wert
                    }
                    runde = runde + 1
                }
                return summe
            }""";

    private static void tick(GameTestHelper helper, ControllerBlockEntity entity, int nummer) {
        entity.fireEvent("Takt", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(nummer)));
    }

    private static dev.devpanda.factorynetwork.runtime.flow.Flow flowOf(
            ControllerBlockEntity entity, long id) {
        return entity.flowEngine().flows().get(id);
    }

    private static long resultOf(dev.devpanda.factorynetwork.runtime.flow.Flow flow) {
        return ((dev.devpanda.factorynetwork.runtime.Value.Int) flow.result()).value();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWaitingFlowSurvivesBeingWrittenDown(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        long id = flow.id();
        tick(helper, entity, 1);
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Nach Runde 1 wartet er");

        // Deploying the same program again re-enacts a restart: write it
        // down, throw the machine away, read it back.
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Erneut übernehmen ging schief");

        var wieder = flowOf(entity, id);
        helper.assertTrue(wieder != null, "Der Ablauf ist beim Aufschreiben verloren gegangen");
        helper.assertTrue(wieder != flow, "Der Ablauf müsste neu aufgebaut worden sein");
        helper.assertValueEqual(wieder.status().name(), "AWAITING",
                "Er muss weiter warten, wo er stand");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) wieder.find("summe")).value(), 1L,
                "Die Summe aus Runde 1 muss den Weg überstehen");

        tick(helper, entity, 2);
        tick(helper, entity, 3);
        helper.assertValueEqual(wieder.status().name(), "DONE", "Er läuft zu Ende");
        helper.assertValueEqual(resultOf(wieder), 6L,
                "Er zählt dort weiter, wo er aufgehört hat");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aChangedProgramMakesWaitingFlowsStale(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        long id = flow.id();
        tick(helper, entity, 1);

        // One line more shifts everything behind it — the flow's counter then
        // points at the wrong statement.
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn zaehlt() {
                    let summe = 0
                    let extra = 0
                    let runde = 0
                    while runde < 3 {
                        if runde >= 0 {
                            let wert = await Takt
                            summe = summe + wert
                        }
                        runde = runde + 1
                    }
                    return summe
                }"""), "Das geänderte Programm wurde nicht übernommen");

        var wieder = flowOf(entity, id);
        helper.assertTrue(wieder != null, "Der Ablauf darf nicht verschwinden");
        helper.assertValueEqual(wieder.status().name(), "STALE",
                "Er muss sich melden statt heimlich weiterzulaufen");

        // A STALE flow no longer moves on its own.
        tick(helper, entity, 2);
        helper.assertValueEqual(wieder.status().name(), "STALE", "Und bleibt liegen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anEditedBodyKeepsWaitingFlowsRunning(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        long id = flow.id();
        tick(helper, entity, 1);

        // Same lines, different arithmetic: the positions stay where they were.
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn zaehlt() {
                    let summe = 0
                    let runde = 0
                    while runde < 3 {
                        if runde >= 0 {
                            let wert = await Takt
                            summe = summe + wert + 10
                        }
                        runde = runde + 1
                    }
                    return summe
                }"""), "Das geänderte Programm wurde nicht übernommen");

        var wieder = flowOf(entity, id);
        helper.assertValueEqual(wieder.status().name(), "AWAITING",
                "Der Ablauf läuft weiter, weil seine Stellen noch stimmen");

        tick(helper, entity, 2);
        tick(helper, entity, 3);
        helper.assertValueEqual(resultOf(wieder), 26L,
                "Runde 1 zählte alt, die Runden 2 und 3 zählen neu");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWaitingFlowSurvivesAServerRestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        long id = flow.id();
        tick(helper, entity, 1);
        tick(helper, entity, 2);
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Er wartet auf Runde 3");

        // The path a server restart takes: the BlockEntity writes itself to
        // disk, and on load a new one arises from the tag.
        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag gespeichert = entity.saveWithFullMetadata(registries);
        var absolut = helper.absolutePos(controller);
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                absolut, helper.getBlockState(controller), gespeichert, registries);
        helper.assertTrue(block instanceof ControllerBlockEntity,
                "Aus dem Tag kam kein Controller zurück");

        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());

        var wieder = flowOf(geladen, id);
        helper.assertTrue(wieder != null, "Der wartende Ablauf hat den Neustart nicht überlebt");
        helper.assertValueEqual(wieder.status().name(), "AWAITING",
                "Er wartet weiter, wo er stand");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Int) wieder.find("summe")).value(), 3L,
                "1 + 2 aus den Runden davor");

        tick(helper, geladen, 3);
        helper.assertValueEqual(wieder.status().name(), "DONE", "Und läuft zu Ende");
        helper.assertValueEqual(resultOf(wieder), 6L, "Über den Neustart hinweg gezählt");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aStaleFlowCanBeLetThrough(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        long id = flow.id();
        tick(helper, entity, 1);

        // A new function appended at the end: the program is a different one,
        // but the positions of the waiting flow are untouched. Exactly the
        // case for which the choice exists.
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM + """


                fn nebenbei() {
                    let x = 1
                }"""), "Das erweiterte Programm wurde nicht übernommen");

        var wieder = flowOf(entity, id);
        helper.assertValueEqual(wieder.status().name(), "STALE", "Erst einmal fragt er nach");
        helper.assertValueEqual(entity.flowEngine().stale().size(), 1,
                "Und steht in der Liste der Wartenden");

        helper.assertTrue(entity.flowEngine().unstale(id), "Weiterlaufen wurde abgelehnt");
        helper.assertValueEqual(wieder.status().name(), "AWAITING",
                "Danach wartet er wieder auf sein Ereignis");

        tick(helper, entity, 2);
        tick(helper, entity, 3);
        helper.assertValueEqual(resultOf(wieder), 6L, "Und zählt zu Ende");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aStaleFlowCanBeAborted(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM), "Programm nicht übernommen");

        long id = entity.startFlow("zaehlt", java.util.List.of()).id();
        tick(helper, entity, 1);
        helper.assertTrue(entity.deploy(COUNTING_PROGRAM + """


                fn nebenbei() {
                    let x = 1
                }"""), "Das erweiterte Programm wurde nicht übernommen");

        helper.assertTrue(entity.flowEngine().abort(id), "Abbrechen wurde abgelehnt");
        helper.assertTrue(flowOf(entity, id) == null, "Der Ablauf ist noch da");
        helper.assertValueEqual(entity.flowEngine().failed().size(), 1,
                "Abgebrochene Abläufe bleiben zum Nachsehen liegen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void emitFromTheLanguageWakesAWaitingFlow(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Fertig(wert: Int)

                fn wartet() {
                    let ergebnis = await Fertig
                    return ergebnis
                }

                fn meldet() {
                    emit Fertig(7)
                }"""), "Das Programm wurde nicht übernommen");

        var wartend = entity.startFlow("wartet", java.util.List.of());
        helper.assertValueEqual(wartend.status().name(), "AWAITING", "Er wartet");

        // The path a player takes: emit is in their program, not in a Java
        // call.
        entity.startFlow("meldet", java.util.List.of());

        helper.assertValueEqual(wartend.status().name(), "DONE",
                "Ein emit aus der Sprache muss wartende Abläufe wecken");
        helper.assertValueEqual(resultOf(wartend), 7L, "Mit dem Wert aus dem emit");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anEventBlockMayWaitItself(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // An on block that waits itself — that did not work as long as events
        // ran to completion inside the interpreter.
        helper.assertTrue(entity.deploy("""
                event Start()
                event Weiter(wert: Int)

                on Start() {
                    let wert = await Weiter
                    emit Fertig(wert)
                }

                event Fertig(wert: Int)

                fn beobachtet() {
                    let ergebnis = await Fertig
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var beobachter = entity.startFlow("beobachtet", java.util.List.of());
        entity.fireEvent("Start", java.util.List.of());
        helper.assertValueEqual(beobachter.status().name(), "AWAITING",
                "Noch hat der on-Block nichts gemeldet");

        entity.fireEvent("Weiter", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(5)));
        helper.assertValueEqual(beobachter.status().name(), "DONE",
                "Der on-Block lief nach seinem Warten weiter und meldete");
        helper.assertValueEqual(resultOf(beobachter), 5L, "Mit dem durchgereichten Wert");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aSleepingFlowSurvivesAServerRestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // All sorts of value kinds, so that the path via disk is not covered
        // with numbers only.
        helper.assertTrue(entity.deploy("""
                fn schlaeft() {
                    let nachricht = "hallo"
                    let dauer = 5s
                    let genau = 1.5
                    let ja = true
                    let sache = item:iron_ingot
                    sleep 30t
                    return 5
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("schlaeft", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "SLEEPING", "Er schläft");

        var registries = helper.getLevel().registryAccess();
        var gespeichert = entity.saveWithFullMetadata(registries);
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                gespeichert, registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Der schlafende Ablauf ist verloren gegangen");
        helper.assertValueEqual(wieder.status().name(), "SLEEPING", "Er schläft weiter");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Text) wieder.find("nachricht")).value(),
                "hallo", "Der Text muss den Weg überstehen");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Duration) wieder.find("dauer")).ticks(),
                100L, "Fünf Sekunden sind hundert Ticks");
        helper.assertValueEqual(
                ((dev.devpanda.factorynetwork.runtime.Value.Bool) wieder.find("ja")).value(),
                true, "Auch der Wahrheitswert");
        helper.assertTrue(wieder.find("sache") != null, "Und die Auswahl");

        helper.runAfterDelay(40, () -> {
            geladen.flowEngine().tick(helper.getLevel().getGameTime());
            helper.assertValueEqual(wieder.status().name(), "DONE",
                    "Nach der Wartezeit wacht er auf, auch nach einem Neustart");
            helper.assertValueEqual(resultOf(wieder), 5L, "Und läuft zu Ende");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aForLoopCanWaitInEveryRound(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // The actual purpose of the thing: kick something off for every
        // machine and wait for its reply before the next one gets its turn.
        //
        // Exactly three kinds in storage, and the loop runs to the end.
        // Previously it ran over tag:minecraft/planks and broke off after
        // three rounds — so nobody checked that it ends at all.
        entity.storage().insert(Items.IRON_ORE, 1);
        entity.storage().insert(Items.COAL, 1);
        entity.storage().insert(Items.COBBLESTONE, 1);

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn reihum() {
                    let summe = 0
                    for sorte in storage.items() {
                        let wert = await Takt
                        summe = summe + wert
                    }
                    return summe
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("reihum", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Schon in der ersten Runde wird gewartet");

        tick(helper, entity, 1);
        tick(helper, entity, 2);
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Und in jeder weiteren");
        tick(helper, entity, 3);

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Nach der dritten Sorte ist die Liste zu Ende");
        helper.assertValueEqual(resultOf(flow), 6L, "Drei Runden, drei Werte");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aForLoopKeepsItsPlaceAcrossARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // The loop runs to the end of the list and returns how many times it
        // went round.
        //
        // <b>Compared against a run without a restart.</b> Previously a
        // "break" after three rounds stood here — which made the position in
        // the list invisible from the outside: if the pointer jumped back to
        // zero on load, three rounds still came out. The test stayed green
        // even if the position was not written down at all. Nobody needs to
        // know how long the list is for that — only that both runs yield the
        // same number.
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn reihum() {
                    let runden = 0
                    for sorte in tag:minecraft/planks {
                        let wert = await Takt
                        runden = runden + 1
                    }
                    return runden
                }"""), "Das Programm wurde nicht übernommen");

        var ungestoert = entity.startFlow("reihum", java.util.List.of());
        long erwartet = runToEnd(helper, entity, ungestoert);
        helper.assertTrue(erwartet >= 3,
                "Die Liste muss mehr als zwei Einträge haben, sonst prüft das hier nichts");

        var flow = entity.startFlow("reihum", java.util.List.of());
        tick(helper, entity, 1);
        // Only the tick really carries the woken run onwards. Without it the
        // pointer would still be on the first entry when saving, and a jump
        // back to zero would be no difference at all.
        entity.serverTick();
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Der Lauf steht jetzt beim zweiten Eintrag");

        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Der Lauf über die Liste ist verloren gegangen");
        helper.assertValueEqual(wieder.status().name(), "AWAITING", "Er wartet weiter");

        // If the run's position were not written down, the list would start
        // from the beginning — and come to more rounds than the undisturbed run.
        long gezaehlt = runToEnd(helper, geladen, wieder);
        helper.assertValueEqual(gezaehlt, erwartet,
                "Über den Neustart hinweg dieselbe Zahl Runden wie ohne");
        helper.succeed();
    }

    /**
     * Ticks until the run is done, and returns its result.
     *
     * <p>The test need not know how long the list is — only that it ends. The
     * limit catches a loop that does not use itself up.
     */
    private static long runToEnd(GameTestHelper helper, ControllerBlockEntity entity,
                                 dev.devpanda.factorynetwork.runtime.flow.Flow flow) {
        for (int takt = 0; takt < 60 && !flow.status().name().equals("DONE"); takt++) {
            tick(helper, entity, 1);
            entity.serverTick();
        }
        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Lauf über die Liste muss enden: " + flow.detail());
        return resultOf(flow);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aDisplayButtonStartsAFlowThatMayWait(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                display leitstand {
                    title "Leitstand"
                    button "Anstoßen" anstossen
                }

                fn anstossen() {
                    let wert = await Takt
                    return wert
                }"""), "Das Programm wurde nicht übernommen");

        // No flow before somebody presses.
        helper.assertValueEqual(entity.flowEngine().flows().size(), 0, "Noch läuft nichts");

        entity.pressDisplayButton("leitstand", 1);
        helper.assertValueEqual(entity.flowEngine().flows().size(), 1,
                "Der Knopf muss einen Ablauf starten");

        var flow = entity.flowEngine().flows().values().iterator().next();
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Ein Knopf darf etwas anstoßen, das wartet");

        tick(helper, entity, 4);
        helper.assertValueEqual(flow.status().name(), "DONE", "Und danach zu Ende laufen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aDisplayButtonIgnoresLinesThatAreNotButtons(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                display leitstand {
                    title "Leitstand"
                    button "Anstoßen" anstossen
                }

                fn anstossen() {
                    return 1
                }"""), "Das Programm wurde nicht übernommen");

        // The heading is not a button, and a number beside it does not exist.
        entity.pressDisplayButton("leitstand", 0);
        entity.pressDisplayButton("leitstand", 99);
        entity.pressDisplayButton("gibtsnicht", 1);
        helper.assertValueEqual(entity.flowEngine().flows().size(), 0,
                "Nichts davon darf etwas auslösen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aCalledFunctionMayWait(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn holt() {
                    let wert = await Takt
                    return wert * 2
                }

                fn ruft() {
                    let erstes = holt()
                    let zweites = holt()
                    return erstes + zweites
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("ruft", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Die gerufene Funktion wartet, also wartet der ganze Ablauf");

        tick(helper, entity, 3);
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Und beim zweiten Aufruf wieder");
        tick(helper, entity, 4);

        helper.assertValueEqual(flow.status().name(), "DONE", "Dann ist er fertig");
        helper.assertValueEqual(resultOf(flow), 14L, "6 + 8 — beide Rückgaben kamen an");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aCalledFunctionCannotSeeItsCallersNames(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn innen() {
                    return geheim
                }

                fn aussen() {
                    let geheim = 1
                    let ergebnis = innen()
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("aussen", java.util.List.of());
        // Otherwise a function's behaviour would depend on who calls it.
        helper.assertValueEqual(flow.status().name(), "FAILED",
                "Der Name des Rufers darf innen nicht sichtbar sein");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWaitingCallSurvivesARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn holt() {
                    let wert = await Takt
                    return wert * 2
                }

                fn ruft() {
                    let erstes = holt()
                    let zweites = holt()
                    return erstes + zweites
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("ruft", java.util.List.of());
        tick(helper, entity, 3);

        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Zwei Rahmen tief war zu tief");
        helper.assertValueEqual(wieder.status().name(), "AWAITING", "Er wartet weiter");

        tick(helper, geladen, 4);
        helper.assertValueEqual(wieder.status().name(), "DONE", "Und läuft zu Ende");
        helper.assertValueEqual(resultOf(wieder), 14L,
                "Auch das Ergebnis des ersten Aufrufs hat den Weg überstanden");
        helper.succeed();
    }

    /**
     * Two plants on one cable — the second with a missing device.
     *
     * <p>The names carry the plant name in front: this way and no other is
     * how a built plant comes about.
     */
    private static ControllerBlockEntity twoPlants(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 5; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        String[] labels = {"werk_1/eingang", "werk_1/ausgang", "werk_2/eingang"};
        for (int i = 0; i < labels.length; i++) {
            BlockPos connector = controller.east(i + 2).above();
            connector(helper, connector, Direction.UP);
            helper.setBlock(connector.above(), Blocks.CHEST);
            name(helper, connector, labels[i]);
        }
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        driveWithFluidCell(helper, controller.below(),
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64);
        return controllerAt(helper, controller);
    }

    /** The chest above the connector at this spot. */
    private static net.minecraft.world.level.block.entity.ChestBlockEntity plantChest(
            GameTestHelper helper, BlockPos controller, int index) {
        BlockPos chest = controller.east(index + 2).above(2);
        if (helper.getBlockEntity(chest)
                instanceof net.minecraft.world.level.block.entity.ChestBlockEntity container) {
            return container;
        }
        helper.fail("Keine Kiste", chest);
        throw new IllegalStateException();
    }

    private static final String PLANT_PROGRAM = """
            multiblock Werk {
                devices {
                    eingang
                    ausgang
                }

                fn schleusen() {
                    move 3 item:cobblestone from eingang to ausgang
                }
            }

            fn los() {
                werk_1.schleusen()
            }""";

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aPlantUsesItsOwnDevices(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoPlants(helper, controller);
        entity.rebuildNetwork();
        plantChest(helper, controller, 0).setItem(0, new ItemStack(Items.COBBLESTONE, 10));

        helper.assertTrue(entity.deploy(PLANT_PROGRAM), "Das Programm wurde nicht übernommen");
        entity.startFlow("los", java.util.List.of());

        // The template says "eingang" — meaning werk_1/eingang.
        helper.assertValueEqual(plantChest(helper, controller, 0).getItem(0).getCount(), 7,
                "Aus dem Eingang der eigenen Anlage");
        helper.assertValueEqual(plantChest(helper, controller, 1).getItem(0).getCount(), 3,
                "In den Ausgang der eigenen Anlage");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anIncompletePlantRefusesCalls(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoPlants(helper, controller);
        entity.rebuildNetwork();
        plantChest(helper, controller, 2).setItem(0, new ItemStack(Items.COBBLESTONE, 10));

        helper.assertTrue(entity.deploy(PLANT_PROGRAM.replace("werk_1.schleusen()",
                "werk_2.schleusen()")), "Das Programm wurde nicht übernommen");
        var flow = entity.startFlow("los", java.util.List.of());

        // werk_2 lacks the output. A half-completed call would be worse than
        // one that does not begin at all.
        helper.assertValueEqual(flow.status().name(), "FAILED",
                "Eine unvollständige Anlage nimmt keine Aufrufe an");
        helper.assertTrue(flow.detail().contains("ausgang"),
                "Und sagt, was fehlt: " + flow.detail());
        helper.assertValueEqual(plantChest(helper, controller, 2).getItem(0).getCount(), 10,
                "Nichts wurde bewegt");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aPlantFlowRemembersItsPlantAcrossARestart(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoPlants(helper, controller);
        entity.rebuildNetwork();
        plantChest(helper, controller, 0).setItem(0, new ItemStack(Items.COBBLESTONE, 10));

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                multiblock Werk {
                    devices {
                        eingang
                        ausgang
                    }

                    fn schleusen() {
                        let wert = await Takt
                        move 3 item:cobblestone from eingang to ausgang
                        return wert
                    }
                }

                fn los() {
                    let ergebnis = werk_1.schleusen()
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("los", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Die Anlage wartet");

        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());
        // In the game the tick takes care of that: without a network the
        // controller knows no devices, and the plant could not be found again.
        geladen.rebuildNetwork();

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Der Ablauf der Anlage ist verloren gegangen");
        tick(helper, geladen, 7);

        helper.assertValueEqual(wieder.status().name(), "DONE",
                "Er läuft zu Ende, sagt aber: " + wieder.detail());
        helper.assertValueEqual(resultOf(wieder), 7L, "Mit dem Wert aus dem Ereignis");
        // Without the recorded plant name it would no longer know where to go.
        helper.assertValueEqual(plantChest(helper, controller, 1).getItem(0).getCount(), 3,
                "Und weiß noch, welche Anlage er bedient");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void deviceEventsFireWhenTheNetworkChanges(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                on device_online(gerät) {
                    setRedstone(depot, 7)
                }

                on device_offline(name) {
                    setRedstone(depot, 3)
                }"""), "Das Programm wurde nicht übernommen");

        // A new connector on the cable: the network knows it at the next rebuild.
        BlockPos weiterer = controller.east().above();
        connector(helper, weiterer, Direction.UP);
        helper.setBlock(weiterer.above(), Blocks.CHEST);
        name(helper, weiterer, "nachzuegler");
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.flowEngine().flows().size()
                + entity.flowEngine().failed().size(), 1,
                "Das Auftauchen muss einen Ereignisblock gestartet haben");

        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theFirstNetworkBuildStaysQuiet(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        helper.assertTrue(entity.deploy("""
                on device_online(gerät) {
                    setRedstone(depot, 7)
                }"""), "Das Programm wurde nicht übernommen");

        // On the very first rebuild nothing has been added — it was just that
        // nothing was known before.
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.flowEngine().flows().size()
                + entity.flowEngine().failed().size(), 0,
                "Ein Sturm bei jedem Serverstart wäre nur Rauschen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidMoveDoesNotTouchItems(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }

        helper.assertTrue(entity.deploy("""
                fn versuch() {
                    move 1000 fluid:water from quarry_output to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.startFlow("versuch", java.util.List.of());

        // A selection that matches nothing must not mean "no filter".
        helper.assertValueEqual(
                ((ChestBlockEntity) helper.getBlockEntity(quelle)).getItem(0).getCount(), 64,
                "Steine haben mit Wasser nichts zu tun");
        helper.succeed();
    }

    /**
     * Two cauldrons on the cable.
     *
     * <p>NeoForge gives every cauldron a tank, and with that there is a fluid
     * test piece without a block of its own. A cauldron holds 1000
     * millibuckets — exactly one bucket.
     */
    private static ControllerBlockEntity twoCauldrons(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 4; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        String[] labels = {"bottich", "kessel"};
        for (int i = 0; i < labels.length; i++) {
            BlockPos connector = controller.east(i + 2).above();
            connector(helper, connector, Direction.UP);
            helper.setBlock(connector.above(), Blocks.CAULDRON);
            name(helper, connector, labels[i]);
        }
        // Since fluids live in cells, a network without a drive no longer
        // stores fluid either — exactly as with items.
        driveWithFluidCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64);
        return controllerAt(helper, controller);
    }

    /** Fills a cauldron with water. */
    private static void fillCauldron(GameTestHelper helper, BlockPos controller, int index) {
        helper.setBlock(controller.east(index + 2).above(2),
                Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
    }

    private static boolean hasWater(GameTestHelper helper, BlockPos controller, int index) {
        return helper.getBlockState(controller.east(index + 2).above(2))
                .is(Blocks.WATER_CAULDRON);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void fluidMovesFromOneTankToAnother(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                fn umfuellen() {
                    move 1000 fluid:water from bottich to kessel
                }"""), "Das Programm wurde nicht übernommen");
        var flow = entity.startFlow("umfuellen", java.util.List.of());

        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf sagt: " + flow.detail());
        helper.assertTrue(!hasWater(helper, controller, 0), "Der Bottich ist leer");
        helper.assertTrue(hasWater(helper, controller, 1), "Der Kessel ist voll");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void fluidGoesIntoTheNetworkAndBack(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                fn einlagern() {
                    move 1000 fluid:water from bottich to storage
                }

                fn auslagern() {
                    move 1000 fluid:water from storage to kessel
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("einlagern", java.util.List.of());
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 1000L,
                "Das Netz hält jetzt einen Eimer");
        helper.assertTrue(!hasWater(helper, controller, 0), "Der Bottich ist leer");

        entity.startFlow("auslagern", java.util.List.of());
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 0L, "Und wieder nichts");
        helper.assertTrue(hasWater(helper, controller, 1), "Dafür steht es im Kessel");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidStockSurvivesARestart(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                fn einlagern() {
                    move 1000 fluid:water from bottich to storage
                }"""), "Das Programm wurde nicht übernommen");
        entity.startFlow("einlagern", java.util.List.of());
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 1000L,
                "Erst einmal muss es überhaupt im Netz sein");

        // The stock now lives in the cell, no longer in the controller — so
        // the drive is saved and read back. When saving, the contents must go
        // from memory into the item; without that it would be the earlier
        // contents after a restart.
        BlockPos drivePos = controller.above();
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        var registries = helper.getLevel().registryAccess();
        var geladen = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(drivePos), helper.getBlockState(drivePos),
                        drive.saveWithFullMetadata(registries), registries);
        helper.assertTrue(geladen != null, "Das Laufwerk kam nicht zurück");
        var inhalt = dev.devpanda.factorynetwork.storage.CellFormat.FLUIDS
                .read(geladen.cell(0), helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault(
                net.minecraft.world.level.material.Fluids.WATER, 0L), 1000L,
                "Ein Bestand, der einen Neustart nicht übersteht, ist keiner");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidWorkerHaulsBetweenTanks(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                worker umfuellen {
                    from bottich
                    to kessel
                    filter fluid:water
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        helper.assertTrue(!hasWater(helper, controller, 0), "Der Bottich ist leer");
        helper.assertTrue(hasWater(helper, controller, 1), "Der Kessel ist voll");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidWorkerFillsTheNetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                worker einlagern {
                    from bottich
                    to storage
                    filter fluid:water
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 1000L,
                "Der Bestand des Netzes");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidWorkerWithoutFilterSaysSo(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        // Without filter this is an item worker — and that finds no inventory
        // at a cauldron. The message must say so, not stay silent.
        helper.assertTrue(entity.deploy("""
                worker unklar {
                    from bottich
                    to kessel
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 3; i++) {
            entity.serverTick();
        }

        var state = entity.runtime().states().get("unklar");
        helper.assertTrue(state != null, "Der Worker hat keinen Zustand");
        helper.assertValueEqual(state.status.name(), "HALTED", "Er muss anhalten");
        helper.assertTrue(hasWater(helper, controller, 0), "Und nichts angefasst haben");
        helper.succeed();
    }

    /**
     * Writes a packet and reads it back.
     *
     * <p>A codec with the wrong number of fields compiles without complaint
     * and only breaks when somebody opens the terminal. This check catches
     * that without a player having to be present for it.
     */
    private static <T> T roundTrip(GameTestHelper helper,
            net.minecraft.network.codec.StreamCodec<
                    net.minecraft.network.RegistryFriendlyByteBuf, T> codec, T packet) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        codec.encode(buffer, packet);
        return codec.decode(buffer);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyPacketSurvivesTheWire(GameTestHelper helper) {
        var netzzustand = new dev.devpanda.factorynetwork.network.packet.NetworkStatePacket(
                java.util.List.of(
                        new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                                "kiste_1", new BlockPos(1, 2, 3)),
                        new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                                "kiste_2", new BlockPos(4, 5, 6))),
                java.util.List.of(
                        new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                                "halle", new BlockPos(7, 8, 9))),
                java.util.List.of("haul: RUNNING"), java.util.List.of("werk_1: Werk"),
                java.util.List.of("water: 1000 mB"),
                java.util.List.of(
                        dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.toFlat(
                                "kiste_1", new DeviceProfile("block.minecraft.chest",
                                        "minecraft", Side.EAST, java.util.Map.of(
                                        Side.EAST, new DeviceProfile.Access(27, 0, false),
                                        Side.ANY, new DeviceProfile.Access(27, 0, false))))));
        var zurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.NetworkStatePacket.STREAM_CODEC,
                netzzustand);
        helper.assertValueEqual(zurueck.fluids().get(0), "water: 1000 mB", "Netzzustand");
        helper.assertValueEqual(zurueck.plants().get(0), "werk_1: Werk", "Anlagen");
        helper.assertValueEqual(zurueck.connectors().size(), 2, "Connectoren");
        helper.assertValueEqual(zurueck.displays().get(0).name(), "halle", "Anzeigen");
        helper.assertValueEqual(zurueck.connectors().get(0).pos(),
                new BlockPos(1, 2, 3), "und die Stelle kommt mit");

        // The profile travels over the wire as a flat list and must be the
        // same device on the other side — including sideless access.
        var profil = dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec
                .fromFlat(zurueck.profiles().get(0));
        helper.assertValueEqual(zurueck.profiles().get(0).name(), "kiste_1", "Name im Profil");
        helper.assertValueEqual(profil.descriptionId(), "block.minecraft.chest", "Maschine");
        helper.assertValueEqual(profil.connectedSide(), Side.EAST, "angeschlossene Seite");
        helper.assertValueEqual(profil.accessAt(Side.EAST).slots(), 27, "Fächer");
        helper.assertValueEqual(profil.accessAt(Side.WEST).slots(), 27,
                "der seitenlose Zugang gilt für jede Seite");

        var ablaeufe = new dev.devpanda.factorynetwork.network.packet.FlowStatePacket(
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Line(7, "zaehlt", "AWAITING", "wartet auf Takt")),
                new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Compute(16, 5, 2, 64, 37, 256),
                new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Supply(0, 12345, 20000, 42, 96),
                java.util.List.of("modus = nacht", "zaehler = 3"));
        var ablaeufeZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.FlowStatePacket.STREAM_CODEC, ablaeufe);
        helper.assertValueEqual(ablaeufeZurueck.flows().get(0).id(), 7L, "Kennung des Ablaufs");
        helper.assertValueEqual(ablaeufeZurueck.compute().threads(), 16, "Plätze im Netz");
        helper.assertValueEqual(ablaeufeZurueck.compute().queued(), 2, "wie viele anstehen");
        helper.assertValueEqual(ablaeufeZurueck.compute().memory(), 64, "Speicher im Netz");
        helper.assertValueEqual(ablaeufeZurueck.compute().program(), 37, "Größe des Programms");
        helper.assertValueEqual(ablaeufeZurueck.compute().disk(), 256, "Platz auf den Trägern");
        helper.assertValueEqual(ablaeufeZurueck.supply().stored(), 12345, "Stromvorrat");
        helper.assertValueEqual(ablaeufeZurueck.supply().draw(), 42, "Bedarf");
        helper.assertValueEqual(ablaeufeZurueck.flows().get(0).detail(), "wartet auf Takt",
                "Grund");
        helper.assertValueEqual(ablaeufeZurueck.globals().size(), 2, "globale Werte");
        helper.assertValueEqual(ablaeufeZurueck.globals().get(0), "modus = nacht",
                "und ihr Stand");

        var wahl = new dev.devpanda.factorynetwork.network.packet.FlowActionPacket(7, true);
        helper.assertValueEqual(roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.FlowActionPacket.STREAM_CODEC, wahl)
                .keep(), true, "Die Wahl");

        var bestand = new dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket(
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .StorageSnapshotPacket.Entry(
                        dev.devpanda.factorynetwork.storage.ItemKey
                                .bare(Items.IRON_ORE), 320)),
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .StorageSnapshotPacket.FluidEntry(
                        net.minecraft.world.level.material.Fluids.WATER, 3000)),
                true, 1, 12, 3);
        var bestandZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket.STREAM_CODEC,
                bestand);
        helper.assertValueEqual(bestandZurueck.fluids().get(0).amount(), 3000L,
                "Die Flüssigkeit im Bestand");
        helper.assertValueEqual(bestandZurueck.freeTypes(), 12, "Freie Artenplätze");
        helper.assertValueEqual(bestandZurueck.freeFluidTypes(), 3,
                "Freie Sortenplätze für Flüssigkeiten");

        var anzeigen = new dev.devpanda.factorynetwork.network.packet.DisplayStatePacket(
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .DisplayStatePacket.Panel("leitstand",
                        java.util.List.of("§fZeile", "§8[Knopf]"),
                        java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                                .DisplayStatePacket.Button(1, 1)))));
        var anzeigenZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.DisplayStatePacket.STREAM_CODEC,
                anzeigen);
        helper.assertValueEqual(anzeigenZurueck.panels().get(0).buttons().get(0).line(), 1,
                "Welche Zeile ein Knopf ist");
        helper.assertValueEqual(anzeigenZurueck.panels().get(0).buttons().get(0).entry(), 1,
                "Und welchen Eintrag sie meint");

        // Crafting takes the same path over the wire. In single-player a
        // broken codec goes unnoticed, on a server it shows immediately.
        var auftraege = new dev.devpanda.factorynetwork.network.packet.CraftingStatePacket(
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .CraftingStatePacket.Line(3, "Truhe", 64, 8, "WAITING",
                        "es fehlt: 8 Eichenholzbretter")));
        var auftraegeZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.CraftingStatePacket.STREAM_CODEC,
                auftraege);
        helper.assertValueEqual(auftraegeZurueck.jobs().get(0).id(), 3L, "Kennung");
        helper.assertValueEqual(auftraegeZurueck.jobs().get(0).done(), 8, "Wie viele fertig");
        helper.assertValueEqual(auftraegeZurueck.jobs().get(0).detail(),
                "es fehlt: 8 Eichenholzbretter", "Der Grund");

        var abbruch = new dev.devpanda.factorynetwork.network.packet.CraftingActionPacket(3);
        helper.assertValueEqual(roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.CraftingActionPacket.STREAM_CODEC,
                abbruch).id(), 3L, "Der Abbruch trifft denselben Auftrag");

        var netz = new dev.devpanda.factorynetwork.network.packet.AnalyserDataPacket(
                java.util.List.of(new dev.devpanda.factorynetwork.analyser.AnalyserData.Node(
                        BlockPos.ZERO,
                        dev.devpanda.factorynetwork.analyser.AnalyserData.NodeState.CONGESTED,
                        "brecher")),
                java.util.List.of(new dev.devpanda.factorynetwork.analyser.AnalyserData.Link(
                        BlockPos.ZERO, BlockPos.ZERO.above(),
                        dev.devpanda.factorynetwork.analyser.AnalyserData.LinkState.FULL, 8, 8)),
                new dev.devpanda.factorynetwork.analyser.AnalyserData.Summary(
                        3, 12, 1, 0, 0, 2, 1));
        var netzZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.AnalyserDataPacket.STREAM_CODEC, netz);
        helper.assertValueEqual(netzZurueck.summary().fullLinks(), 1,
                "Die siebte Zahl der Übersicht — sie passt nicht mehr in composite");
        helper.assertValueEqual(netzZurueck.nodes().get(0).label(), "brecher", "Gerätename");
        helper.assertValueEqual(netzZurueck.links().get(0).load(), 8, "Kanallast der Strecke");

        var druck = new dev.devpanda.factorynetwork.network.packet.DisplayActionPacket(
                "leitstand", 1);
        helper.assertValueEqual(roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.DisplayActionPacket.STREAM_CODEC, druck)
                .display(), "leitstand", "Der gedrückte Knopf");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theControllerCanTellAPlayerEverything(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoPlants(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                display leitstand {
                    title "Leitstand"
                    row "Steine" storage.count(item:cobblestone)
                    progress "Fortschritt" 0.5
                    button "Anstoßen" wartet
                }

                multiblock Werk {
                    devices {
                        eingang
                        ausgang
                    }

                    fn schleusen() {
                        move 3 item:cobblestone from eingang to ausgang
                    }
                }

                fn wartet() {
                    let ergebnis = await Takt
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");
        entity.startFlow("wartet", java.util.List.of());
        entity.fluids().insert(net.minecraft.world.level.material.Fluids.WATER, 1500);

        // What the terminal gets to see is created here. Without this check an
        // error in it would only surface when somebody opens the terminal.
        var anzeigen = entity.displayPanels();
        helper.assertValueEqual(anzeigen.size(), 1, "Eine Anzeige");
        helper.assertValueEqual(anzeigen.get(0).lines().size(), 4, "Vier Zeilen");
        helper.assertValueEqual(anzeigen.get(0).buttons().get(0).line(), 3,
                "Der Knopf steht in der vierten Zeile");
        helper.assertTrue(anzeigen.get(0).lines().get(1).contains("Steine"),
                "Die Zeile mit dem Bestand: " + anzeigen.get(0).lines().get(1));

        var ablaeufe = entity.flowLines();
        helper.assertValueEqual(ablaeufe.size(), 1, "Ein wartender Ablauf");
        helper.assertValueEqual(ablaeufe.get(0).status(), "AWAITING", "Und er wartet");

        helper.assertValueEqual(entity.plants().size(), 2, "Beide Anlagen erscheinen");
        // The order comes from a set and is not guaranteed.
        helper.assertTrue(entity.plants().stream().anyMatch(plant -> plant.contains("fehlt")),
                "Die unvollständige Anlage sagt es: " + entity.plants());
        helper.assertValueEqual(entity.fluidLines().get(0), "water: 1500 mB",
                "Der Flüssigkeitsbestand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aLoopOverFluidsRunsItsRounds(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn zaehlt() {
                    let anzahl = 0
                    for sorte in fluid:water {
                        anzahl = anzahl + 1
                    }
                    return anzahl
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("zaehlt", java.util.List.of());
        // A loop over nothing looks like one that had nothing to do — and is
        // thereby the worst case.
        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf sagt: " + flow.detail());
        helper.assertValueEqual(resultOf(flow), 1L, "Wasser ist eine Sorte");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theDocumentedExpressionsReallyWork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 320);
        // Without a signal the lamp is off, and the check for it proves
        // nothing. A redstone block next to depot is what turns the question
        // into one.
        helper.setBlock(controller.east().south().above(), Blocks.REDSTONE_BLOCK);

        // What is in beispiele.md must not only compile but run. An example
        // with a method name that does not exist is worse than none.
        helper.assertTrue(entity.deploy("""
                display leitstand {
                    title "Erzlinie"
                    row "Eisenerz" storage.count(item:iron_ore)
                    progress "Erzvorrat" storage.count(item:iron_ore) / 640.0
                    indicator "Depot unter Strom" depot.redstone() > 0
                    button "Nachschub" nachschub_starten
                }

                fn nachschub_starten() {
                    log("angestoßen")
                }

                fn mit_variabler_auswahl() {
                    for sorte in tag:minecraft/planks {
                        move 1 sorte from quarry_output to depot
                    }
                }

                on device_offline(name) {
                    log("Verschwunden: " + name)
                }"""), "Das Programm wurde nicht übernommen");

        var zeilen = entity.displayPanels().get(0).lines();
        helper.assertValueEqual(zeilen.size(), 5, "Fünf Zeilen");
        helper.assertTrue(zeilen.get(1).contains("320"),
                "Der Bestand steht da: " + zeilen.get(1));
        // The bar always draws ten blocks, and the label also stands beside a
        // dark lamp: contains("█") and contains("Depot") both matched even when
        // nothing had been evaluated at all. So the boundary between light and
        // dark is checked — a half bar carries its §8 in the middle, an empty
        // one right at the start.
        helper.assertTrue(zeilen.get(2).contains("§a█████§8█████"),
                "320 von 640 sind ein halber Balken: " + zeilen.get(2));
        helper.assertTrue(zeilen.get(2).contains("50 %"),
                "Neben dem Balken steht sein Anteil: " + zeilen.get(2));
        helper.assertTrue(zeilen.get(3).startsWith("§a●"),
                "Der Redstoneblock liegt daneben, das Lämpchen muss leuchten: "
                        + zeilen.get(3));

        // The button and the event block really run.
        // A variable as the selection in move — that is how beispiele.md has it.
        entity.startFlow("mit_variabler_auswahl", java.util.List.of());
        entity.pressDisplayButton("leitstand", 4);
        entity.fireEvent("device_offline", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Text("kiste_9")));
        helper.assertTrue(entity.flowEngine().failed().isEmpty(),
                "Nichts darf dabei scheitern: " + entity.flowEngine().failed());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aChangedInventoryWakesAWaitingFlow(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Built-in events are not declared — like redstone_changed.
        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    let gerät = await device_changed
                    return 1
                }

                on device_changed(gerät) {
                    log("etwas hat sich getan")
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("wartet", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Er wartet");

        BlockPos quelle = controller.east().north().north();
        // Somebody must have looked once first: on the first look nothing is
        // reported, because nothing has changed — it was just that nothing was
        // known.
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(flow.status().name(), "DONE",
                        "Eine Änderung am Inventar muss den Wartenden wecken"))
                .thenSucceed();
    }

    /**
     * A group is a value: it names its members and accepts.
     *
     * <p>In the GameTest because both hang on the network — which devices are
     * in the group is decided by the world and not by the program.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aGroupIsAValue(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.COBBLESTONE, 32);

        helper.assertTrue(entity.deploy("""
                group kisten {
                    members quarry_output, depot
                }

                global anzahl = 0

                fn zaehlt() {
                    anzahl = kisten.members().count()
                }

                fn schickt() {
                    kisten.send(16 item:cobblestone)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        entity.startFlow("zaehlt", List.of());
        entity.startFlow("schickt", List.of());
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.globals().get("anzahl"),
                            new Value.Int(2), "Die Gruppe hat zwei Mitglieder");
                    helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 16L,
                            "Sechzehn sind an ein Mitglied gegangen");
                })
                .thenSucceed();
    }

    /**
     * An amount before a template name really moves something.
     *
     * <p>That {@code send(64 erze)} compiles is stated by the checker for the
     * doc examples. Whether the interpreter also <b>resolves</b> the name at
     * this spot it does not say — it only compiles. That is exactly where the
     * bug was before: the example stood in {@code beispiele.md} and could not
     * even be read.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anamountBeforeAtemplateNameReallyMoves(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 32);

        helper.assertTrue(entity.deploy("""
                filter erze {
                    tag:c/ores
                }

                group kisten {
                    members quarry_output, depot
                }

                fn schickt() {
                    kisten.send(16 erze)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        entity.startFlow("schickt", List.of());
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.IRON_ORE), 16L,
                        "Sechzehn Erze sind über die Vorlage an ein Mitglied gegangen"))
                .thenSucceed();
    }

    /** A constant is read like a global, only never written. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aConstantIsReadableAtRuntime(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                const stapel = 64
                global doppelt = 0

                fn rechnet() {
                    doppelt = stapel * 2
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("rechnet", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(entity.globals().get("doppelt"),
                        new Value.Int(128), "Der Festwert steht in der Rechnung"))
                .thenSucceed();
    }

    /**
     * A global list survives the restart.
     *
     * <p>The reason it exists at all: a queue that vanishes on a server
     * restart is none. The whole path is checked — append via an assignment,
     * save, read back.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aglobalListSurvivesArestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                global warteschlange = []

                fn anstellen() {
                    warteschlange = warteschlange.plus("eisen")
                    warteschlange = warteschlange.plus("gold")
                }"""), "Das Programm wurde nicht übernommen");
        entity.callFunction("anstellen", List.of());

        var registries = helper.getLevel().registryAccess();
        var wieder = (ControllerBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(controller), helper.getBlockState(controller),
                        entity.saveWithFullMetadata(registries), registries);

        helper.assertTrue(wieder != null, "Der Controller kam nicht zurück");
        helper.assertValueEqual(wieder.globals().get("warteschlange").describe(),
                "[eisen, gold]", "die Liste hat den Neustart nicht überlebt");
        helper.succeed();
    }

    /**
     * A list as a constant cannot be changed.
     *
     * <p><b>That comes for free</b> and is the reason for the decision:
     * because appending is an assignment, the same check that reports
     * {@code stapel = 65} also guards {@code sorten = sorten.plus(…)}. A
     * mutating {@code add} would slip past it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aconstListCannotBeChanged(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // The cross-check first: the same program without the assignment goes
        // through. Without it the test would be green even if the list
        // literal itself did not compile — and nothing would be checked.
        helper.assertTrue(entity.deploy("""
                const sorten = ["eisen", "gold"]

                fn dazu() {
                    log(sorten.plus("kupfer"))
                }"""), "Lesen und Anhängen ohne Zuweisung muss erlaubt sein");

        helper.assertTrue(!entity.deploy("""
                const sorten = ["eisen", "gold"]

                fn dazu() {
                    sorten = sorten.plus("kupfer")
                }"""), "Ein Festwert darf sich nicht überschreiben lassen");
        helper.succeed();
    }

    /**
     * An entry from the stock knows its kind and its amount.
     *
     * <p>In the GameTest because a kind without a registry is none: the unit
     * test can only check what can be checked without a world.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aStockEntryKnowsItsKindAndAmount(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 5);
        entity.storage().insert(Items.GOLD_ORE, 70);

        helper.assertTrue(entity.deploy("""
                global grosse_posten = 0
                global kleinster = 0

                fn zaehlt() {
                    grosse_posten = storage.items().where(it.amount > 64).count()
                    kleinster = storage.items().sort(it.amount).first().amount
                    log(storage.items().sort(it.amount).first().item)
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("zaehlt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.globals().get("grosse_posten"),
                            new Value.Int(1), "Nur das Golderz liegt über 64");
                    helper.assertValueEqual(entity.globals().get("kleinster"),
                            new Value.Int(5), "Der kleinste Posten sind die fünf Eisenerze");
                    helper.assertTrue(entity.log().stream().anyMatch(zeile ->
                                    zeile.text().contains("iron_ore")),
                            "it.item muss die Art nennen: " + entity.log());
                })
                .thenSucceed();
    }

    /**
     * {@code gerät.count(…)} counts the device and not the storage.
     *
     * <p>The network storage deliberately stays empty in the test. If both
     * held the same, the test would show nothing — it could not tell the
     * mix-up from the right answer.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void countAtADeviceCountsTheDevice(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.GOLD_ORE, 5));
        }

        helper.assertTrue(entity.deploy("""
                global im_geraet = 0
                global insgesamt = 0
                global im_netz = 0

                fn zaehlt() {
                    im_geraet = quarry_output.count(item:iron_ore)
                    insgesamt = quarry_output.count()
                    im_netz = storage.count(item:iron_ore)
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("zaehlt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.globals().get("im_geraet"),
                            new Value.Int(8), "Acht Eisenerz liegen in der Kiste");
                    helper.assertValueEqual(entity.globals().get("insgesamt"),
                            new Value.Int(13), "Ohne Auswahl zählt alles mit");
                    helper.assertValueEqual(entity.globals().get("im_netz"),
                            new Value.Int(0), "Im Netzspeicher liegt nichts");
                })
                .thenSucceed();
    }

    /**
     * <b>A selection that matches nothing must not move everything.</b>
     *
     * <p>An empty list means "no filter" to {@code move}, and no filter means
     * "everything". As long as the interpreter threw the exception away, a
     * mistyped tag went via the path of the written selection and reported
     * itself; since it resolves, it has to report itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anEmptySelectionMovesNothing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.GOLD_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                fn holt() {
                    move item:gibtsnicht except item:gold_ore from quarry_output to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("holt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                            "Eisenerz steht in keiner Auswahl");
                    helper.assertValueEqual(entity.storage().count(Items.GOLD_ORE), 0L,
                            "Golderz erst recht nicht");
                    helper.assertTrue(!entity.flowEngine().failed().isEmpty(),
                            "Der Ablauf muss sich melden und nicht still nichts tun");
                })
                .thenSucceed();
    }

    /** An amount before a template means in total, not per kind. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anAmountBeforeATemplateMeansTotal(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.COPPER_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                filter erze {
                    item:iron_ore
                    item:copper_ore
                }

                fn holt() {
                    move 3 erze from quarry_output to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("holt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.IRON_ORE)
                                + entity.storage().count(Items.COPPER_ORE), 3L,
                        "Drei zusammen, nicht drei je Art"))
                .thenSucceed();
    }

    /**
     * <b>The exception only worked in the worker.</b> The interpreter
     * evaluated {@code Expr.Except} as its base and threw the exclusions away
     * — in a {@code move} the exception therefore stood there and did
     * nothing, although sprache.md shows it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void exceptWorksInMoveToo(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.GOLD_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                fn holt() {
                    move 64 item:*_ore except item:gold_ore from quarry_output to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("holt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 8L,
                            "Eisenerz trifft das Muster");
                    helper.assertValueEqual(entity.storage().count(Items.GOLD_ORE), 0L,
                            "Golderz ist ausdrücklich ausgenommen");
                })
                .thenSucceed();
    }

    /** The same template as in the worker, but in a move. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void moveUsesAFilterTemplate(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.GOLD_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                filter erze {
                    item:iron_ore
                    item:gold_ore
                    except item:gold_ore
                }

                fn holt() {
                    move erze from quarry_output to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("holt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 8L,
                            "Eisenerz steht in der Vorlage");
                    helper.assertValueEqual(entity.storage().count(Items.GOLD_ORE), 0L,
                            "Golderz nimmt die Ausnahme heraus");
                })
                .thenSucceed();
    }

    /** A template counts even when only reading. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void countUsesAFilterTemplate(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 5);
        entity.storage().insert(Items.GOLD_ORE, 7);

        helper.assertTrue(entity.deploy("""
                filter erze {
                    item:iron_ore
                    item:gold_ore
                }

                global gezaehlt = 0

                fn zaehlt() {
                    gezaehlt = storage.count(erze)
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("zaehlt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(entity.globals().get("gezaehlt"),
                        new Value.Int(12), "Beide Sorten zusammen"))
                .thenSucceed();
    }

    /**
     * A worker filters by a template.
     *
     * <p>Three kinds lie in the chest, two are in the template, one of those
     * it takes out again. Only this way does the test show both: that the
     * template applies and that its exception works.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWorkerFiltersByTemplate(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(1, new ItemStack(Items.GOLD_ORE, 8));
            container.setItem(2, new ItemStack(Items.COPPER_ORE, 8));
        }

        helper.assertTrue(entity.deploy("""
                filter erze {
                    item:iron_ore
                    item:copper_ore
                    except item:copper_ore
                }

                worker holt {
                    from quarry_output
                    to storage
                    filter erze
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 8L,
                            "Eisenerz steht in der Vorlage");
                    helper.assertValueEqual(entity.storage().count(Items.COPPER_ORE), 0L,
                            "Kupfererz nimmt die Ausnahme wieder heraus");
                    helper.assertValueEqual(entity.storage().count(Items.GOLD_ORE), 0L,
                            "Golderz steht gar nicht erst darin");
                })
                .thenSucceed();
    }

    /**
     * A fluid tag resolves against the fluid registry.
     *
     * <p>Vanilla keeps {@code minecraft:water} as a tag over water and flowing
     * water — the only one that can be relied on in an empty world.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aFluidTagResolvesAgainstFluids(GameTestHelper helper) {
        var result = dev.devpanda.factorynetwork.lang.parse.Parser.parse("""
                filter kuehlmittel {
                    fluidtag:minecraft/water
                }""");
        helper.assertFalse(result.hasErrors(), "Die Vorlage wurde nicht gelesen");
        var template = (dev.devpanda.factorynetwork.lang.ast.Decl.FilterTemplate)
                result.program().declarations().get(0);

        var fluids = dev.devpanda.factorynetwork.runtime.FilterTemplates.fluids(template);

        helper.assertTrue(fluids.contains(net.minecraft.world.level.material.Fluids.WATER),
                "Wasser gehört dazu: " + fluids);
        helper.succeed();
    }

    /**
     * A template gathers and excludes.
     *
     * <p>In the GameTest and not as a unit test: which items stand behind a
     * selection is known only to the registry.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aFilterTemplateGathersAndExcludes(GameTestHelper helper) {
        var result = dev.devpanda.factorynetwork.lang.parse.Parser.parse("""
                filter erze {
                    item:iron_ore
                    item:gold_ore
                    item:copper_ore
                    except item:gold_ore
                }""");
        helper.assertFalse(result.hasErrors(), "Die Vorlage wurde nicht gelesen");
        var template = (dev.devpanda.factorynetwork.lang.ast.Decl.FilterTemplate)
                result.program().declarations().get(0);

        List<Item> items = dev.devpanda.factorynetwork.runtime.FilterTemplates.items(template);

        helper.assertValueEqual(items.size(), 2, "Zwei bleiben übrig");
        helper.assertTrue(items.contains(Items.IRON_ORE), "Eisenerz gehört dazu");
        helper.assertTrue(items.contains(Items.COPPER_ORE), "Kupfererz gehört dazu");
        helper.assertFalse(items.contains(Items.GOLD_ORE),
                "Golderz ist ausdrücklich ausgenommen");
        helper.succeed();
    }

    /** If nothing is left after the exceptions, that is no silent idling. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aFilterTemplateThatMatchesNothingSaysSo(GameTestHelper helper) {
        var result = dev.devpanda.factorynetwork.lang.parse.Parser.parse("""
                filter leer {
                    item:iron_ore
                    except item:iron_ore
                }""");
        var template = (dev.devpanda.factorynetwork.lang.ast.Decl.FilterTemplate)
                result.program().declarations().get(0);

        try {
            dev.devpanda.factorynetwork.runtime.FilterTemplates.items(template);
            helper.fail("Eine Vorlage, die nichts trifft, muss sich melden");
        } catch (ScriptError expected) {
            helper.assertTrue(expected.getMessage().contains("leer"),
                    "Die Meldung muss die Vorlage beim Namen nennen: "
                            + expected.getMessage());
        }
        helper.succeed();
    }

    /**
     * The counterpart to {@link #aChangedInventoryWakesAWaitingFlow}: not
     * every stir, only what has been added.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void newContentInADeviceWakesAWaitingFlow(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    let gerät = await device_output
                    return 1
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("wartet", List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Er wartet");

        BlockPos quelle = controller.east().north().north();
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(flow.status().name(), "DONE",
                        "Was im Gerät dazukommt, muss den Wartenden wecken"))
                .thenSucceed();
    }

    /**
     * <b>The reason the event needs a baseline.</b> Without it the network
     * reported its own delivery as output — and a flow that inserts and then
     * waits would be awake again immediately, without the machine having so
     * much as started.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatTheNetworkPutsInIsNoOutput(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.COBBLESTONE, 64);

        // Something already lies in the target: exactly the case on which the
        // simple version failed, the one that compares against empty.
        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(ziel) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        }

        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    let gerät = await device_output
                    return 1
                }

                fn füllt() {
                    move 5 item:cobblestone from storage to depot
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("wartet", List.of());
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> entity.startFlow("füllt", List.of()))
                .thenIdle(25)
                .thenExecute(() -> helper.assertValueEqual(flow.status().name(), "AWAITING",
                        "Was das Netz selbst einlegt, ist keine Ausgabe"))
                .thenSucceed();
    }

    /**
     * <b>The worker writes on its own path.</b> It does not go through the
     * interpreter but inserts itself — and if that spot does not advance the
     * baseline, every delivery of the network reports an output that never
     * existed.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatAWorkerPutsInIsNoOutput(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.COBBLESTONE, 64);

        helper.assertTrue(entity.deploy("""
                worker liefert {
                    from storage
                    to depot
                    rate 8 per 20t
                }

                fn wartet() {
                    let gerät = await device_output
                    return 1
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        BlockPos ziel = controller.east().south().south();
        var flow = entity.startFlow("wartet", List.of());
        // Slow enough that delivery is still going on when the baseline is
        // set: a worker that is done within the first ten ticks would run
        // entirely before the first look, and that never reports anything.
        // The test would be green without ever having checked anything.
        int[] zwischenstand = new int[1];
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> zwischenstand[0] = countIn(helper, ziel))
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(countIn(helper, ziel) > zwischenstand[0],
                            "Der Worker muss noch liefern, sonst prüft der Test nichts"
                                    + " (Stand: " + zwischenstand[0] + ")");
                    helper.assertValueEqual(flow.status().name(), "AWAITING",
                            "Was ein Worker einlegt, ist keine Ausgabe");
                })
                .thenSucceed();
    }

    /** Less is nothing new: taking out must not trigger anything. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void takingSomethingOutIsNoOutput(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        }

        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    let gerät = await device_output
                    return 1
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("wartet", List.of());
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(0, ItemStack.EMPTY);
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(flow.status().name(), "AWAITING",
                        "Entnehmen ist keine Ausgabe"))
                .thenSucceed();
    }

    /**
     * <b>No one-shot.</b> A machine that outputs a batch piece by piece must
     * report every piece — otherwise the rest would stay stuck in it, and
     * that is exactly the loss the event is meant to avoid.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void everyNewStackIsReported(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                global zaehler = 0

                on device_output(gerät) {
                    zaehler = zaehler + 1
                }"""), "Das Programm wurde nicht übernommen");

        BlockPos quelle = controller.east().north().north();
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(1, new ItemStack(Items.COBBLESTONE, 1));
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(entity.globals().get("zaehler"),
                        new Value.Int(2), "Jedes Stück wird gemeldet, nicht nur das erste"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatIsStoredSurvivesATick(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // <b>The simplest question of all, and it had no test.</b> It came to
        // light while sharpening an entirely different test: after storing,
        // three kinds were in the storage, after deploying a program too —
        // after one tick none any more.
        entity.storage().insert(Items.IRON_ORE, 64);
        entity.storage().insert(Items.COAL, 32);
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 64L,
                "Eingelagert ist eingelagert");

        entity.serverTick();
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 64L,
                "Ein Tick darf den Bestand nicht anrühren");
        helper.assertValueEqual(entity.storage().count(Items.COAL), 32L,
                "Auch nicht die zweite Sorte");

        for (int i = 0; i < 20; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 64L,
                "Und zwanzig Ticks auch nicht");
        helper.assertValueEqual(entity.storage().contents().size(), 2,
                "Beide Sorten stehen noch da");

        // And the same path once more with a program that runs over the stock
        // and waits while doing so — exactly the situation in which the stock
        // once stood empty.
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn reihum() {
                    let runden = 0
                    for sorte in storage.items() {
                        let wert = await Takt
                        runden = runden + 1
                    }
                    return runden
                }"""), "Das Programm wurde nicht übernommen");
        entity.startFlow("reihum", java.util.List.of());
        tick(helper, entity, 1);
        entity.serverTick();
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 64L,
                "Auch ein laufender Ablauf über den Bestand rührt ihn nicht an");
        helper.assertValueEqual(entity.storage().contents().size(), 2,
                "Und beide Sorten stehen noch da");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theLogKeepsLevelSourceAndSurvivesARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn schreiben() {
                    debug("Zwischenstand")
                    info("Alles gut")
                    warn("Kohle wird knapp")
                    error("Brecher ist weg")
                    log("Ohne Stufe")
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("schreiben", java.util.List.of());
        var zeilen = entity.log();
        helper.assertValueEqual(zeilen.size(), 5, "Fünf Zeilen: " + zeilen);

        var stufen = zeilen.stream()
                .map(eintrag -> eintrag.level().key())
                .toList();
        helper.assertValueEqual(String.join(",", stufen), "debug,info,warn,error,info",
                "Die Stufen in der Reihenfolge, in der sie geschrieben wurden");
        helper.assertValueEqual(zeilen.get(2).text(), "Kohle wird knapp", "Der Text");
        helper.assertValueEqual(zeilen.get(2).source(), "schreiben",
                "Und wer es geschrieben hat");
        helper.assertTrue(zeilen.get(0).time() > 0, "Mit Zeitstempel");

        // The actual purpose: whoever checks in the morning why the plant
        // stopped at night finds the line even after a restart.
        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;

        helper.assertValueEqual(geladen.log().size(), 5,
                "Das Protokoll überlebt den Neustart: " + geladen.log());
        helper.assertValueEqual(geladen.log().get(3).level().key(), "error",
                "Samt Stufe");
        helper.assertValueEqual(geladen.log().get(3).source(), "schreiben",
                "Und samt Herkunft");

        // Clearing means clearing: whoever wants a clean start for the next
        // attempt should get one — even after a restart.
        entity.clearLog();
        helper.assertTrue(entity.log().isEmpty(),
                "Nach dem Leeren steht nichts mehr da: " + entity.log());
        var nachher = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        helper.assertTrue(((ControllerBlockEntity) nachher).log().isEmpty(),
                "Und es bleibt geleert");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWorkerHintReachesTheLogOnlyOnce(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // maintain without filter — the worker does not know what it is
        // supposed to keep in stock, and says so. Until now the runtime
        // collected this hint and nobody read it.
        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from quarry_output
                    to depot
                    maintain 16
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 60; i++) {
            entity.serverTick();
        }

        var hinweise = entity.log().stream()
                .filter(eintrag -> eintrag.text().contains("maintain ohne filter"))
                .toList();
        helper.assertValueEqual(hinweise.size(), 1,
                "Ein Worker läuft zwanzigmal je Sekunde — der Hinweis gehört einmal ins "
                        + "Protokoll: " + entity.log());
        helper.assertValueEqual(hinweise.get(0).source(), "nachschub",
                "Der Worker steht als Herkunft daneben, nicht im Text");
        helper.assertValueEqual(hinweise.get(0).level().key(), "warn",
                "Er läuft weiter, er tut nur nichts");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFlowReadsAndWritesGlobals(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // <b>The path a player really takes.</b> A button on a display, an on
        // block, an await — all three run through the flow engine, and that
        // did not know the global values. "modus = nacht" in a function threw
        // "Unbekannter Name modus" and advised putting a let in front: that
        // compiles, runs, reports nothing and does not change the global
        // value. Exactly this pattern is in the manual and in beispiele.md.
        helper.assertTrue(entity.deploy("""
                global modus = "tag"
                global runden = 0

                display halle {
                    title "Fabrik"
                    row "Modus" modus
                    button "Umschalten" umschalten
                }

                fn umschalten() {
                    runden = runden + 1
                    if modus == "tag" {
                        modus = "nacht"
                    } else {
                        modus = "tag"
                    }
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("umschalten", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "DONE",
                "Der Ablauf muss durchlaufen: " + flow.detail());
        helper.assertValueEqual(entity.globals().get("modus").describe(), "nacht",
                "Ein Ablauf muss einen globalen Wert ändern können");
        helper.assertValueEqual(entity.globals().get("runden").describe(), "1",
                "Und ihn dabei auch lesen");

        // And via the button, because that is the path from the manual.
        entity.pressDisplayButton("halle", 2);
        helper.assertValueEqual(entity.globals().get("modus").describe(), "tag",
                "Der Knopf schaltet zurück");
        helper.assertValueEqual(entity.globals().get("runden").describe(), "2",
                "Zweimal gelesen und geschrieben");
        helper.assertTrue(entity.flowEngine().failed().isEmpty(),
                "Nichts darf dabei scheitern: " + entity.flowEngine().failed());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anAwaitAloneIsEnoughToBeWatched(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Without an on block — exactly as it stands in beispiele.md, and
        // exactly like that it waited forever: only the blocks were counted,
        // so nobody even looked, so the event never fired. But an await is
        // the same listener.
        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    let gerät = await device_changed
                    return 1
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("wartet", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "Er wartet");

        BlockPos quelle = controller.east().north().north();
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
                        container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                    }
                })
                .thenIdle(15)
                .thenExecute(() -> helper.assertValueEqual(flow.status().name(), "DONE",
                        "Ein wartender Ablauf allein muss reichen, damit hingesehen wird"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutAHandlerNothingIsWatched(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn nichts() {
                    return 1
                }"""), "Das Programm wurde nicht übernommen");

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        }

        // Without on device_changed nobody even looks. With fifty connectors
        // that would otherwise be work for nothing.
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(entity.flowEngine().flows().size(), 0, "Kein Ablauf");
            helper.assertValueEqual(entity.flowEngine().failed().size(), 0, "Und kein Fehler");
            helper.succeed();
        });
    }

    /**
     * Two cauldrons named as a plant.
     *
     * <p>For the case no single check covers: a template that moves fluid and
     * waits while doing so.
     */
    private static ControllerBlockEntity plantWithTanks(GameTestHelper helper,
            BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 4; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        String[] labels = {"sude_1/bottich", "sude_1/kessel"};
        for (int i = 0; i < labels.length; i++) {
            BlockPos connector = controller.east(i + 2).above();
            connector(helper, connector, Direction.UP);
            helper.setBlock(connector.above(), Blocks.CAULDRON);
            name(helper, connector, labels[i]);
        }
        return controllerAt(helper, controller);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aPlantMovesFluidAndWaitsAcrossARestart(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = plantWithTanks(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                event Freigabe(nummer: Int)

                multiblock Sudhaus {
                    devices {
                        bottich
                        kessel
                    }

                    fn sud() {
                        let freigabe = await Freigabe
                        move 1000 fluid:water from bottich to kessel
                        return freigabe
                    }
                }

                fn los() {
                    let ergebnis = sude_1.sud()
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("los", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "Die Anlage wartet auf ihre Freigabe");

        // Reload in the middle of waiting — with everything that was added
        // that night: two frames deep, in a template, before a fluid move.
        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());
        geladen.rebuildNetwork();

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Der Ablauf der Anlage hat den Neustart nicht überlebt");

        geladen.fireEvent("Freigabe", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(4)));

        helper.assertValueEqual(wieder.status().name(), "DONE",
                "Er läuft zu Ende, sagt aber: " + wieder.detail());
        helper.assertValueEqual(resultOf(wieder), 4L, "Der Wert aus der Freigabe");
        helper.assertTrue(!hasWater(helper, controller, 0), "Der Bottich ist leer");
        helper.assertTrue(hasWater(helper, controller, 1),
                "Und der Kessel der richtigen Anlage ist voll");
        helper.succeed();
    }

    /** Three cauldrons: one full, two empty ones as a group. */
    private static ControllerBlockEntity threeCauldrons(GameTestHelper helper,
            BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 5; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        String[] labels = {"bottich", "ziel_a", "ziel_b"};
        for (int i = 0; i < labels.length; i++) {
            BlockPos connector = controller.east(i + 2).above();
            connector(helper, connector, Direction.UP);
            helper.setBlock(connector.above(), Blocks.CAULDRON);
            name(helper, connector, labels[i]);
        }
        return controllerAt(helper, controller);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aFluidWorkerServesAGroup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = threeCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                group ziele {
                    members ziel_*
                    strategy least_filled
                }

                worker verteilen {
                    from bottich
                    to ziele
                    filter fluid:water
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        // A cauldron holds exactly one bucket, so everything lands in one of
        // the two — which one is decided by the distribution.
        boolean a = hasWater(helper, controller, 1);
        boolean b = hasWater(helper, controller, 2);
        helper.assertTrue(a || b, "Ein Mitglied der Gruppe muss etwas bekommen haben");
        helper.assertTrue(!hasWater(helper, controller, 0), "Und der Bottich ist leer");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void exceptWorksForFluidsToo(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        helper.assertTrue(entity.deploy("""
                worker alles_ausser_wasser {
                    from bottich
                    to kessel
                    filter fluid:* except fluid:water
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        // Water is excluded — it stays put.
        helper.assertTrue(hasWater(helper, controller, 0),
                "Was ausgenommen ist, wird nicht bewegt");
        helper.assertTrue(!hasWater(helper, controller, 1), "Und kommt nirgends an");
        helper.succeed();
    }

    /** Reloads the controller as a server restart would. */
    private static ControllerBlockEntity reload(GameTestHelper helper, BlockPos controller,
            ControllerBlockEntity entity) {
        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());
        geladen.rebuildNetwork();
        return geladen;
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whereStillDecidesAfterARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Fertig(id: Int)

                fn wartetAuf(ziel: Int) {
                    let ergebnis = await Fertig where id == ziel
                    return ergebnis
                }"""), "Das Programm wurde nicht übernommen");

        long id = entity.startFlow("wartetAuf",
                java.util.List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(2))).id();

        // where is in the program and not in the flow: after loading it must
        // be found again via the frame's counter.
        ControllerBlockEntity geladen = reload(helper, controller, entity);
        var wieder = flowOf(geladen, id);
        helper.assertTrue(wieder != null, "Der Ablauf hat den Neustart nicht überlebt");

        geladen.fireEvent("Fertig", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(1)));
        helper.assertValueEqual(wieder.status().name(), "AWAITING",
                "Das falsche Ereignis darf ihn auch nach dem Neustart nicht wecken");

        geladen.fireEvent("Fertig", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(2)));
        helper.assertValueEqual(wieder.status().name(), "DONE", "Das richtige schon");
        helper.assertValueEqual(resultOf(wieder), 2L, "Mit dem Wert des Ereignisses");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anElseBranchStillRunsAfterARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Nie()

                fn gibtAuf() {
                    let ergebnis = await Nie timeout 20t else {
                        return 3
                    }
                    return 9
                }"""), "Das Programm wurde nicht übernommen");

        long id = entity.startFlow("gibtAuf", java.util.List.of()).id();
        ControllerBlockEntity geladen = reload(helper, controller, entity);
        var wieder = flowOf(geladen, id);
        helper.assertTrue(wieder != null, "Der wartende Ablauf ist verloren gegangen");

        // The deadline is absolute game time — it expires across the restart,
        // and the else branch is in the program, not in the flow.
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> geladen.flowEngine().tick(helper.getLevel().getGameTime()))
                .thenExecute(() -> {
                    helper.assertValueEqual(wieder.status().name(), "DONE",
                            "Der else-Zweig muss auch nach dem Laden laufen: " + wieder.detail());
                    helper.assertValueEqual(resultOf(wieder), 3L,
                            "Und zwar er, nicht die Zeile dahinter");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theAnalyserSeesTheWholeNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);

        // The controller itself and the two named connectors.
        helper.assertTrue(data.nodes().size() >= 3,
                "Zu wenige Knoten: " + data.nodes().size());
        helper.assertTrue(data.nodes().stream().anyMatch(node -> node.state().name()
                        .equals("CONTROLLER")),
                "Der Controller muss ein Knoten sein");
        helper.assertTrue(data.nodes().stream().anyMatch(node ->
                        node.label().equals("quarry_output")),
                "Die Geräte müssen mit Namen erscheinen");

        // Without links it would be a point cloud instead of a network.
        helper.assertTrue(!data.links().isEmpty(), "Es muss Verbindungen geben");
        helper.assertValueEqual(data.summary().devices(), 2, "Geräte in der Übersicht");
        helper.assertTrue(data.summary().isHealthy(), "Dieses Netz ist in Ordnung");
        helper.succeed();
    }





    /**
     * Places a server rack with an equipped bay next to it.
     *
     * <p>Since server racks exist, a network without one does not compute.
     * Almost every check therefore needs one — just as almost every one
     * needs a drive since cells exist.
     *
     * <p><b>Generously equipped</b>, namely with sixteen slots: a check should
     * fail at what it checks, and not because three flows wanted to run at
     * the same time. Whoever checks the limit itself builds a smaller rack.
     */
    private static final int TEST_CPU = 32;
    private static final int TEST_RAM = 128;
    private static final int TEST_DISK = 4096;

    /**
     * Places a server rack — both halves, as when placing by hand.
     *
     * <p>{@code setBlock} does not go through {@code setPlacedBy}, so the
     * upper half does not arise by itself. Without it the rack would collapse
     * at the next neighbour change from above — and the check would fail at
     * something entirely different from what it checks.
     */
    private static void placeRack(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, FnBlocks.RACK.get().defaultBlockState());
        helper.setBlock(at.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.RackBlock.HALF,
                        net.minecraft.world.level.block.state.properties
                                .DoubleBlockHalf.UPPER));
    }

    /** The item for a part kind and tier. */
    private static ItemStack serverPart(dev.devpanda.factorynetwork.item.ServerPart kind,
                                        int value) {
        for (var item : dev.devpanda.factorynetwork.registry.FnItems.SERVER_PARTS.get(kind)) {
            if (item.get() instanceof dev.devpanda.factorynetwork.item.ServerPartItem part
                    && part.value() == value) {
                return new ItemStack(item.get());
            }
        }
        throw new IllegalArgumentException("Kein " + kind + " der Stufe " + value);
    }

    /** An empty server chassis. */
    private static ItemStack chassis() {
        return new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.SERVER_CHASSIS.get());
    }

    /**
     * Fully equips a bay.
     *
     * <p>The chassis first: without one the bay accepts no parts, and that is
     * the rule that makes the item a server in the first place.
     */
    private static void fillBay(GameTestHelper helper, BlockPos at, int bay,
                                int cpu, int ram, int disk) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.RackBlockEntity rack)) {
            helper.fail("Am Serverschrank hängt keine BlockEntity", at);
            return;
        }
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .chassisSlot(bay), chassis());
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .slotOf(bay, dev.devpanda.factorynetwork.item.ServerPart.CPU),
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, cpu));
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .slotOf(bay, dev.devpanda.factorynetwork.item.ServerPart.RAM),
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.RAM, ram));
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .slotOf(bay, dev.devpanda.factorynetwork.item.ServerPart.DISK),
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.DISK, disk));
    }

    /** A server rack with one generously equipped bay. */
    private static void rackWithServer(GameTestHelper helper, BlockPos at) {
        placeRack(helper, at);
        fillBay(helper, at, 0, TEST_CPU, TEST_RAM, TEST_DISK);
    }

    /**
     * Pushes the first part from the player's inventory into the shelf —
     * like a shift-click in the window.
     */
    private static void intoShelf(GameTestHelper helper, BlockPos at,
                                  net.minecraft.world.entity.player.Player player) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf)) {
            helper.fail("Hier steht kein Regal", at);
            return;
        }
        var menu = dev.devpanda.factorynetwork.client.menu.ShelfMenu
                .of(1, player.getInventory(), shelf, shelf.layout());
        for (int index = shelf.getContainerSize(); index < menu.slots.size(); index++) {
            if (menu.slots.get(index).hasItem()) {
                menu.quickMoveStack(player, index);
                return;
            }
        }
        helper.fail("Im Rucksack liegt nichts zum Einschieben", at);
    }

    /**
     * Takes a part out via the window — like a shift-click.
     *
     * <p>Since there is a window, that is the path. The empty hand on the
     * block opens it instead of pulling something out.
     */
    private static void takeFromShelf(GameTestHelper helper, BlockPos at, int slot,
                                      net.minecraft.world.entity.player.Player player) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf)) {
            helper.fail("Hier steht kein Regal", at);
            return;
        }
        dev.devpanda.factorynetwork.client.menu.ShelfMenu
                .of(1, player.getInventory(), shelf, shelf.layout())
                .quickMoveStack(player, slot);
    }

    /** Places a drive on the cable and inserts a fluid cell. */
    private static void driveWithFluidCell(GameTestHelper helper, BlockPos at,
            dev.devpanda.factorynetwork.storage.FluidCellTier tier) {
        helper.setBlock(at, FnBlocks.DRIVE.get());
        if (helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.DriveBlockEntity drive) {
            drive.setCell(0, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                    .FLUID_CELLS.get(tier).get()));
        } else {
            helper.fail("Am Laufwerk hängt keine BlockEntity", at);
        }
    }

    /** Places a drive on the cable and inserts a cell. */
    private static void driveWithCell(GameTestHelper helper, BlockPos at,
            dev.devpanda.factorynetwork.storage.CellTier tier) {
        helper.setBlock(at, FnBlocks.DRIVE.get());
        if (helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.DriveBlockEntity drive) {
            drive.setCell(0, new ItemStack(
                    dev.devpanda.factorynetwork.registry.FnItems.CELLS.get(tier).get()));
        } else {
            helper.fail("Am Laufwerk hängt keine BlockEntity", at);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withoutADriveNothingIsStored(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Storage space is now something you build. Without a drive there is
        // none — and storing must say so, not swallow it.
        long rest = entity.storage().insert(Items.IRON_INGOT, 64);
        helper.assertValueEqual(rest, 64L, "Ohne Laufwerk passt nichts hinein");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 0L, "Bestand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aCellHoldsWhatFitsIntoIt(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.storage().insert(Items.IRON_INGOT, 64), 0L,
                "Das passt hinein");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 64L, "Bestand");

        // The amount of a 1k cell is eight thousand.
        helper.assertValueEqual(entity.storage().insert(Items.COBBLESTONE, 8_000), 64L,
                "Was über die Menge geht, bleibt draußen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aCellRunsOutOfTypesBeforeAmount(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Eight kinds fit into a 1k cell, the ninth does not — although the
        // amount is far from reached. Exactly that drives you to sort.
        Item[] arten = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG};
        for (Item art : arten) {
            helper.assertValueEqual(entity.storage().insert(art, 1), 0L,
                    "Art " + art + " muss hineinpassen");
        }
        helper.assertValueEqual(entity.storage().insert(Items.STONE, 1), 1L,
                "Die neunte Art findet keinen Platz mehr");

        // Of a kind already present, by contrast, more keeps going in —
        // otherwise you would have to watch every cell individually when
        // tidying up.
        helper.assertValueEqual(entity.storage().insert(Items.IRON_INGOT, 100), 0L,
                "Was schon drin ist, nimmt sie weiter an");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theStoredGoodsRideAlongInTheCell(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos, dev.devpanda.factorynetwork.storage.CellTier.K4);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.DIAMOND, 12);

        // The stock sits in the item, not in the drive. That is the reason a
        // cell is worth something.
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        // The stock lives in the drive and only goes into the item when
        // saving. Whoever reads it before that sees the earlier state.
        drive.flushCells();
        ItemStack cell = drive.cell(0);
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(cell,
                helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault(dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.DIAMOND), 0L), 12L,
                "Die Zelle trägt ihren Inhalt selbst");

        // Cell out: the network has nothing any more.
        drive.setCell(0, ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.storage().count(Items.DIAMOND), 0L,
                "Ohne die Zelle ist der Bestand weg");
        helper.succeed();
    }

    /**
     * A second drive enlarges the storage.
     *
     * <p>That is the answer to "a storage block of its own": it exists, and
     * it is called drive. What is checked is the promise that hangs on it —
     * <b>whoever wants more room adds one</b>. Without it the drive would be
     * a fixed-size storage in a different place.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void asecondDriveEnlargesTheStorage(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        driveWithCell(helper, controller.below(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Eight kinds fill the type slots of a 1k cell.
        Item[] acht = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG};
        for (Item art : acht) {
            helper.assertValueEqual(entity.storage().insert(art, 1), 0L,
                    "Art " + art + " muss hineinpassen");
        }

        // The ninth failed at a single cell. With a second drive it finds room
        // there.
        helper.assertValueEqual(entity.storage().insert(Items.STONE, 1), 0L,
                "Die neunte Art gehört ins zweite Laufwerk");
        helper.assertValueEqual(entity.storage().count(Items.STONE), 1L,
                "und der Bestand zählt über beide zusammen");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 1L,
                "das erste bleibt dabei lesbar");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thePressNeedsPowerAndTime(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, FnBlocks.PRESS.get());
        var press = (dev.devpanda.factorynetwork.block.entity.PressBlockEntity)
                helper.getBlockEntity(at);
        helper.assertTrue(press != null, "An der Presse hängt keine BlockEntity");

        press.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_STAMP,
                new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.STAMP_PLATE.get()));
        press.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL,
                new ItemStack(Items.IRON_INGOT, 4));

        // Without power nothing happens, no matter how long you wait.
        for (int i = 0; i < 200; i++) {
            press.serverTick();
        }
        helper.assertValueEqual(press.progress(), 0, "Ohne Strom darf nichts geschehen");
        helper.assertTrue(press.item(
                        dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT)
                .isEmpty(), "Und nichts herauskommen");

        // With power it runs — but is not done immediately.
        press.energy().receiveEnergy(
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.CAPACITY, false);
        press.serverTick();
        helper.assertValueEqual(press.progress(), 1, "Ein Tick, ein Schritt");

        for (int i = 0; i < 100; i++) {
            press.serverTick();
        }
        ItemStack ergebnis = press.item(
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT);
        helper.assertTrue(!ergebnis.isEmpty(), "Nach der Zeit muss etwas dastehen");
        helper.assertValueEqual(ergebnis.getItem(),
                dev.devpanda.factorynetwork.registry.FnItems.PLATE.get(), "Eine Platte");

        // The stamp stays, the material is consumed.
        helper.assertTrue(!press.item(
                        dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_STAMP)
                .isEmpty(), "Der Stempel ist Werkzeug, keine Zutat");
        helper.assertValueEqual(press.item(
                        dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL)
                .getCount(), 3, "Ein Barren ist verbraucht");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thePressStopsWhenTheOutputIsFull(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, FnBlocks.PRESS.get());
        var press = (dev.devpanda.factorynetwork.block.entity.PressBlockEntity)
                helper.getBlockEntity(at);

        press.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_STAMP,
                new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.STAMP_PLATE.get()));
        press.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL,
                new ItemStack(Items.IRON_INGOT, 64));
        // Output occupied by something foreign: then it must not run, and
        // certainly not consume power for it.
        press.setItem(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT,
                new ItemStack(Items.DIAMOND, 1));
        press.energy().receiveEnergy(
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.CAPACITY, false);
        int vorher = press.energy().getEnergyStored();

        for (int i = 0; i < 100; i++) {
            press.serverTick();
        }
        helper.assertValueEqual(press.progress(), 0, "Bei voller Ausgabe steht sie");
        helper.assertValueEqual(press.energy().getEnergyStored(), vorher,
                "Und verbraucht dabei keinen Strom");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullStorageDoesNotSwallowItems(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Fill the cell up to the amount limit.
        entity.storage().insert(Items.COBBLESTONE, 8_000);
        helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 8_000L, "voll");

        // A chest on the network from which a worker is to store.
        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        }
        helper.assertTrue(entity.deploy("""
                worker einlagern {
                    from quarry_output
                    to storage
                    rate 64 per 1t
                }"""), "Programm nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        // The storage is full — the ingots must still be there, in the chest
        // or on the ground, but not vanished.
        long inKiste = 0;
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.getItem() == Items.IRON_INGOT) {
                    inKiste += stack.getCount();
                }
            }
        }
        long imNetz = entity.storage().count(Items.IRON_INGOT);
        helper.assertValueEqual(inKiste + imNetz, 64L,
                "Kein einziger Barren darf verschwinden");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aCellKeepsItsContentsAcrossSaving(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos, dev.devpanda.factorynetwork.storage.CellTier.K4);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.DIAMOND, 42);

        // The stock lives in the drive's memory. Only when saving does it go
        // into the item — without that it would be gone after a restart.
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        var registries = helper.getLevel().registryAccess();
        var tag = drive.saveWithFullMetadata(registries);

        var geladen = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(drivePos), helper.getBlockState(drivePos),
                        tag, registries);
        helper.assertTrue(geladen != null, "Das Laufwerk kam nicht zurück");
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(geladen.cell(0), helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault(dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.DIAMOND), 0L), 42L,
                "Der Bestand muss das Sichern überstehen");
        helper.succeed();
    }

    // ---- Router ----------------------------------------------------------

    /**
     * The direction from one spot to another, in world coordinates.
     *
     * <p>A test setup may stand rotated. A direction called "north" in the
     * test is then not north in the world — which is why it is computed from
     * two absolute positions instead of written down.
     */
    private static net.minecraft.core.Direction towards(GameTestHelper helper,
                                                        BlockPos from, BlockPos to) {
        BlockPos a = helper.absolutePos(from);
        BlockPos b = helper.absolutePos(to);
        net.minecraft.core.Direction direction = net.minecraft.core.Direction.fromDelta(
                b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        if (direction == null) {
            helper.fail("Die beiden Stellen liegen nicht nebeneinander", from);
        }
        return direction;
    }

    private static void lane(GameTestHelper helper, BlockPos router, BlockPos neighbour,
                             int lane) {
        if (helper.getBlockEntity(router)
                instanceof dev.devpanda.factorynetwork.block.entity.RouterBlockEntity entity) {
            entity.setLane(towards(helper, router, neighbour), lane);
        } else {
            helper.fail("Am Router hängt keine BlockEntity", router);
        }
    }


    /**
     * A closed side also cuts off the path behind it.
     *
     * <p>Not just the side itself: everything that was reachable through it
     * no longer belongs to the network afterwards. Otherwise closing would be
     * a mere display.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aClosedSideCutsTheLineBehindIt(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.DENSE_CABLE.get());

        BlockPos router = controller.east(2);
        helper.setBlock(router, FnBlocks.ROUTER.get());
        BlockPos behind = controller.east(3);
        helper.setBlock(behind, FnBlocks.DENSE_CABLE.get());
        BlockPos device = behind.north();
        connector(helper, device, Direction.NORTH);
        name(helper, device, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isPresent(),
                "der Weg über den Router muss offen sein");
        helper.assertTrue(entity.graph().routers().contains(helper.absolutePos(router)),
                "der Router gehört zum Netz");

        lane(helper, router, behind, dev.devpanda.factorynetwork.block.entity
                .RouterBlockEntity.OFF);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isEmpty(),
                "hinter einer abgeklemmten Seite hängt nichts mehr");
        helper.succeed();
    }



    /**
     * The stock follows when somebody pulls a cell.
     *
     * <p>The network index keeps the stock of all cells together so that not
     * every query adds it up anew. The price for that is exactly this danger:
     * that it stands still while the truth changes. Whoever breaks this test
     * has built a network that reports items it no longer has.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theStockFollowsWhenACellIsPulled(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos,
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        entity.storage().insert(Items.IRON_INGOT, 100);
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 100L,
                "Bestand nach dem Ablegen");
        helper.assertValueEqual(entity.storage().distinctTypes(), 1, "Arten im Netz");

        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        // The same instance, no copy: when taking out, the drive writes the
        // stock back into exactly this item, and the same one travels into the
        // player's hand in the game.
        ItemStack cell = drive.cell(0);
        drive.setCell(0, ItemStack.EMPTY);

        // Without a cell nothing is there — not even if something was there a
        // moment ago. The network was not rebuilt in between.
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 0L,
                "Bestand ohne Zelle");
        helper.assertValueEqual(entity.storage().distinctTypes(), 0, "Arten ohne Zelle");

        drive.setCell(0, cell);
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 100L,
                "Bestand, nachdem die Zelle zurück ist");
        helper.succeed();
    }

    /**
     * Storing and extracting update the index instead of discarding it.
     *
     * <p>An index that throws itself away after every store is none — in a
     * tick with twenty workers it would be rebuilt twenty times. The result
     * is checked: a long sequence of stores and extractions, and at the end
     * the index must say the same as the cells.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theIndexKeepsUpWithManyMoves(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos,
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var storage = entity.storage();
        for (int i = 0; i < 50; i++) {
            storage.insert(Items.IRON_INGOT, 10);
            storage.insert(Items.GOLD_INGOT, 4);
            storage.extract(Items.IRON_INGOT, 3);
        }
        storage.extract(Items.GOLD_INGOT, 200);

        long eisen = 0;
        long gold = 0;
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        for (var cell : drive.inventories()) {
            eisen += cell.count(dev.devpanda.factorynetwork.storage.ItemKey
                    .bare(Items.IRON_INGOT));
            gold += cell.count(dev.devpanda.factorynetwork.storage.ItemKey
                    .bare(Items.GOLD_INGOT));
        }
        helper.assertValueEqual(storage.count(Items.IRON_INGOT), eisen,
                "Eisen im Index gegen Eisen in der Zelle");
        helper.assertValueEqual(storage.count(Items.GOLD_INGOT), gold,
                "Gold im Index gegen Gold in der Zelle");
        helper.assertValueEqual(storage.count(Items.IRON_INGOT), 350L, "Eisen insgesamt");
        helper.assertValueEqual(storage.distinctTypes(), 1,
                "Gold ist ganz weg und darf nicht als Art stehen bleiben");
        helper.succeed();
    }


    /**
     * A cell goes in through the window and out again.
     *
     * <p>A click on the drive opens it — always, no matter what is in hand.
     * Previously a part in hand went straight in; that saved one step but
     * was a rule of its own for two blocks.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aCellGoesIntoTheWindowAndOutAgain(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 2, 1);
        helper.setBlock(drivePos, FnBlocks.DRIVE.get());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                .CELLS.get(dev.devpanda.factorynetwork.storage.CellTier.K1).get()));

        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        // Shift-click from the player's inventory into the shelf and back.
        intoShelf(helper, drivePos, player);
        helper.assertValueEqual(drive.usedSlots(), 1, "Zellen im Laufwerk");

        takeFromShelf(helper, drivePos, 0, player);
        helper.assertValueEqual(drive.usedSlots(), 0, "Zellen nach dem Herausnehmen");
        helper.assertTrue(player.getInventory().contains(
                        stack -> stack.getItem() instanceof dev.devpanda.factorynetwork
                                .storage.StorageCellItem),
                "die Zelle muss im Rucksack landen");
        helper.succeed();
    }

    /**
     * A part goes in through the window and out again.
     *
     * <p>The same window as with the drive: both are a shelf, and whoever can
     * operate one can operate the other.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aProcessorGoesIntoTheWindowAndOutAgain(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(chassis());
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 8));
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.RAM, 32));
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.DISK, 256));

        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        // Every part finds its slot by itself — the shift-click need not know
        // where a disk belongs. The chassis first, because without one the
        // parts may not go in at all.
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.usedSlots(), 1, "erst das Gehäuse");
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.threads(), 0, "ein Rechenwerk allein ist kein Server");
        intoShelf(helper, rackPos, player);
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.usedSlots(), 4, "Plätze im Schrank");
        helper.assertValueEqual(rack.runningBays(), 1, "ein laufender Einschub");
        helper.assertValueEqual(rack.threads(), 8, "sein Rechenwerk");

        takeFromShelf(helper, rackPos, dev.devpanda.factorynetwork.block.entity
                .RackBlockEntity.chassisSlot(0), player);
        helper.assertValueEqual(rack.threads(), 0, "nach dem Herausnehmen");
        helper.assertValueEqual(rack.usedSlots(), 0,
                "und der Einschub ist ganz leer — die Hardware ging mit");
        helper.succeed();
    }

    /** If everything is occupied, the eleventh cell stays in the player's inventory. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anEleventhCellFindsNoSlot(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 2, 1);
        helper.setBlock(drivePos, FnBlocks.DRIVE.get());
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        for (int slot = 0; slot < dev.devpanda.factorynetwork.block.entity
                .DriveBlockEntity.SLOTS; slot++) {
            drive.setCell(slot, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CELLS
                    .get(dev.devpanda.factorynetwork.storage.CellTier.K1).get()));
        }

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                .CELLS.get(dev.devpanda.factorynetwork.storage.CellTier.K4).get()));
        intoShelf(helper, drivePos, player);

        helper.assertValueEqual(drive.usedSlots(), 10, "mehr als zehn passen nicht");
        helper.assertTrue(player.getInventory().contains(
                        stack -> stack.getItem() == dev.devpanda.factorynetwork.registry.FnItems
                                .CELLS.get(dev.devpanda.factorynetwork.storage.CellTier.K4).get()),
                "die überzählige Zelle muss im Rucksack bleiben");
        helper.succeed();
    }

    /**
     * The cell that was taken out brings its stock along.
     *
     * <p>The stock sits in the item, not in the drive. If it were lost when
     * taking the cell out, a cell would be a key and not storage — and every
     * rebuild of a plant would cost half the store.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aPulledCellCarriesItsStock(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos,
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.DIAMOND, 7);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        takeFromShelf(helper, drivePos, 0, player);

        ItemStack pulled = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof dev.devpanda.factorynetwork.storage.StorageCellItem) {
                pulled = stack;
                break;
            }
        }
        helper.assertTrue(!pulled.isEmpty(), "die Zelle muss im Rucksack liegen");
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(pulled, helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault(dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.DIAMOND), 0L), 7L,
                "Bestand in der herausgenommenen Zelle");
        helper.assertValueEqual(entity.storage().count(Items.DIAMOND), 0L,
                "im Netz darf nichts zurückbleiben");
        helper.succeed();
    }


    /**
     * A fluid cell has a limit, and it applies.
     *
     * <p>Previously the network stored fluids without limit in the controller.
     * For iron you needed a drive, for lava you did not — an inequality nobody
     * can explain.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aFluidCellHasALimit(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();

        long voll = dev.devpanda.factorynetwork.storage.FluidCellTier.B64.amount();
        long rest = entity.fluids().insert(
                net.minecraft.world.level.material.Fluids.WATER, voll + 6000);
        helper.assertValueEqual(rest, 6000L, "was nicht mehr hineinpasste");
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), voll,
                "Bestand nach dem Überfüllen");
        helper.assertValueEqual(entity.fluids().room(
                net.minecraft.world.level.material.Fluids.WATER, 1000), 0L,
                "eine volle Zelle nimmt nichts mehr");
        helper.succeed();
    }

    /**
     * If the storage is full, the fluid stays in the tank.
     *
     * <p><b>This is the check it hinges on.</b> An item the storage does not
     * take can be put back; a drained fluid not necessarily — if the tank
     * does not accept it again, it is gone. So ask first, then drain.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullNetworkLeavesTheTankAlone(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        // The cell brimful with lava: water finds no room any more.
        entity.fluids().insert(net.minecraft.world.level.material.Fluids.LAVA,
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64.amount());

        helper.assertTrue(entity.deploy("""
                worker einlagern {
                    from bottich
                    to storage
                    filter fluid:water
                    rate 1000 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }

        helper.assertTrue(hasWater(helper, controller, 0),
                "Der Bottich muss sein Wasser behalten");
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 0L,
                "und im Netz darf nichts auftauchen");
        helper.succeed();
    }

    /**
     * A fluid cell that was taken out takes its stock along.
     *
     * <p>As with items: the stock sits in the item, otherwise the cell would
     * be only a key.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aPulledFluidCellCarriesItsStock(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        entity.fluids().insert(net.minecraft.world.level.material.Fluids.WATER, 3000);

        BlockPos drivePos = controller.above();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        takeFromShelf(helper, drivePos, 0, player);

        ItemStack pulled = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof dev.devpanda.factorynetwork.storage.FluidCellItem) {
                pulled = stack;
                break;
            }
        }
        helper.assertTrue(!pulled.isEmpty(), "die Zelle muss im Rucksack liegen");
        var inhalt = dev.devpanda.factorynetwork.storage.CellFormat.FLUIDS.read(pulled, helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.getOrDefault(
                net.minecraft.world.level.material.Fluids.WATER, 0L), 3000L,
                "Bestand in der herausgenommenen Zelle");
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 0L,
                "im Netz darf nichts zurückbleiben");
        helper.succeed();
    }

    // ---- Server rack ------------------------------------------------------

    /**
     * Without a server rack the network does not compute.
     *
     * <p>Just as a drive is the prerequisite for it storing anything. Every
     * capability of the network hangs on a block you have to build — and the
     * program says which one is missing instead of silently doing nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withoutAServerNothingRuns(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.threads(), 0, "ohne Schrank keine Abläufe");
        helper.assertTrue(!entity.deploy("""
                fn nichts() {
                    log("hallo")
                }"""), "ohne Server darf nichts übernommen werden");
        helper.assertTrue(entity.diagnostics().stream()
                        .anyMatch(d -> d.message().contains("Serverschrank")),
                "die Meldung muss den Schrank nennen: " + entity.diagnostics());

        // Rack in place, and the same program runs.
        rackWithServer(helper, controller.west());
        helper.assertTrue(entity.deploy("""
                fn nichts() {
                    log("hallo")
                }"""), "mit Server muss es gehen");
        helper.assertValueEqual(entity.threads(), TEST_CPU,
                "das Rechenwerk des einen Einschubs");
        helper.succeed();
    }


    /**
     * The computing power is the sum over all racks in the network.
     *
     * <p>Not that of the nearest or largest one: whoever upgrades places a
     * second rack next to it, and that has to count.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void threadsAddUpAcrossRacks(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        rackWithServer(helper, controller.above());

        // In the second rack another small bay on top.
        fillBay(helper, controller.above(), 1, 8, 8, 64);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.threads(), 2 * TEST_CPU + 8,
                "zwei große Einschübe und ein kleiner");
        helper.succeed();
    }

    /**
     * The chassis takes its hardware along — and brings it back.
     *
     * <p>That is the whole purpose of the item: pull out a finished server,
     * carry it away, put it in somewhere else. If the hardware were lost on
     * the way or stayed behind twice, the chassis would be a trap.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achassisCarriesItsHardware(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        fillBay(helper, rackPos, 0, 32, 128, 4096);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        helper.assertValueEqual(rack.threads(), 32, "der Einschub läuft");

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        takeFromShelf(helper, rackPos, dev.devpanda.factorynetwork.block.entity
                .RackBlockEntity.chassisSlot(0), player);

        helper.assertValueEqual(rack.usedSlots(), 0, "im Schrank ist nichts geblieben");
        helper.assertValueEqual(rack.threads(), 0, "und er trägt nichts mehr");

        ItemStack gezogen = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (dev.devpanda.factorynetwork.item.ServerChassis.is(stack)) {
                gezogen = stack;
                break;
            }
        }
        helper.assertTrue(!gezogen.isEmpty(), "das Gehäuse liegt nicht im Rucksack");
        var darin = dev.devpanda.factorynetwork.item.ServerChassis.read(gezogen);
        helper.assertValueEqual(dev.devpanda.factorynetwork.item.ServerPartItem.valueOf(
                        darin.get(dev.devpanda.factorynetwork.item.ServerPart.CPU.ordinal())),
                32, "das Rechenwerk ist mitgekommen");
        helper.assertValueEqual(dev.devpanda.factorynetwork.item.ServerPartItem.valueOf(
                        darin.get(dev.devpanda.factorynetwork.item.ServerPart.DISK.ordinal())),
                4096, "und der Datenträger auch");

        // And back in: the hardware returns to its slots.
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.usedSlots(), 4, "vier Plätze wieder belegt");
        helper.assertValueEqual(rack.threads(), 32, "und der Server läuft wieder");
        helper.assertTrue(dev.devpanda.factorynetwork.item.ServerChassis.isEmpty(
                        rack.getItem(dev.devpanda.factorynetwork.block.entity
                                .RackBlockEntity.chassisSlot(0))),
                "der Gegenstand im Schrank hält nichts mehr — die Plätze tun es");
        helper.succeed();
    }

    /**
     * If the chassis does not fit into the player's inventory, everything stays as it was.
     *
     * <p>The shift-click first takes the item out and then pushes it — and on
     * taking out, the chassis packs up. If the push fails, it must go back
     * and unpack again. <b>Exactly here a duplicated or a lost server would
     * otherwise arise</b>, and you would only notice when the rack suddenly
     * carries less.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void afailedMoveLeavesTheChassisWhereItWas(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        fillBay(helper, rackPos, 0, 32, 128, 4096);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, 64));
        }

        takeFromShelf(helper, rackPos, dev.devpanda.factorynetwork.block.entity
                .RackBlockEntity.chassisSlot(0), player);

        helper.assertValueEqual(rack.usedSlots(), 4, "alles ist geblieben");
        helper.assertValueEqual(rack.threads(), 32, "und der Server läuft weiter");
        helper.assertTrue(dev.devpanda.factorynetwork.item.ServerChassis.isEmpty(
                        rack.getItem(dev.devpanda.factorynetwork.block.entity
                                .RackBlockEntity.chassisSlot(0))),
                "und die Hardware liegt wieder in den Plätzen, nicht doppelt im Gehäuse");
        helper.succeed();
    }

    /**
     * Without a chassis a bay accepts no parts.
     *
     * <p>The rule that makes the item a server. Without it the three slots
     * would already be the server, and the chassis would be something you buy
     * that changes nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void hardwareNeedsAChassis(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 8));

        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        var menu = dev.devpanda.factorynetwork.client.menu.ShelfMenu
                .of(1, player.getInventory(), rack, rack.layout());
        for (int index = rack.getContainerSize(); index < menu.slots.size(); index++) {
            if (menu.slots.get(index).hasItem()) {
                menu.quickMoveStack(player, index);
                break;
            }
        }
        helper.assertValueEqual(rack.usedSlots(), 0, "das Rechenwerk blieb draußen");
        helper.assertTrue(player.getInventory().contains(
                        serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 8)),
                "und liegt noch im Rucksack");
        helper.succeed();
    }

    /**
     * An incomplete bay carries nothing.
     *
     * <p>Not proportionally, nothing at all — otherwise the rack would be a
     * sum of parts and not a row of servers, and the decision which bay gets
     * the big part would not exist.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void anIncompleteBayCarriesNothing(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);

        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .chassisSlot(0), chassis());
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.CPU),
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 128));
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.RAM),
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.RAM, 512));
        helper.assertValueEqual(rack.threads(), 0, "ohne Datenträger kein Server");
        helper.assertValueEqual(rack.runningBays(), 0, "kein laufender Einschub");
        helper.assertValueEqual(rack.incompleteBays(), 1, "einer ist angefangen");

        fillBay(helper, rackPos, 0, 128, 512, 4096);
        helper.assertValueEqual(rack.threads(), 128, "jetzt trägt er");
        helper.assertValueEqual(rack.incompleteBays(), 0, "und ist vollständig");
        helper.succeed();
    }

    /**
     * Every slot takes only its own kind.
     *
     * <p>Otherwise a processor would lie in the disk slot, the bay would be
     * full and still not run — a fault you cannot find by looking.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aSlotTakesOnlyItsOwnKind(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);

        int diskSlot = dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.DISK);
        // Without a chassis nothing goes in at all.
        helper.assertTrue(!rack.canPlaceItem(diskSlot,
                        serverPart(dev.devpanda.factorynetwork.item.ServerPart.DISK, 64)),
                "ohne Gehäuse nimmt der Einschub nichts an");
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .chassisSlot(0), chassis());
        helper.assertTrue(!rack.canPlaceItem(diskSlot,
                        serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 2)),
                "ein Rechenwerk gehört nicht auf den Datenträgerplatz");
        helper.assertTrue(rack.canPlaceItem(diskSlot,
                        serverPart(dev.devpanda.factorynetwork.item.ServerPart.DISK, 64)),
                "ein Datenträger schon");
        helper.succeed();
    }

    /**
     * The rack is two blocks tall and still one device.
     *
     * <p>Whoever cables up at the top cables up the same rack. If the upper
     * half counted on its own, a rack would cost two channels and appear
     * twice in the list — and the second BlockEntity would not even exist.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aTallRackIsStillOneDevice(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        // The rack stands next to the controller: its lower half touches it,
        // its upper half a cable that also hangs off it. Two paths to one
        // device.
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.above(), FnBlocks.CABLE.get());

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().racks().size(), 1,
                "ein Schrank, nicht zwei");
        helper.assertValueEqual(entity.threads(), TEST_CPU, "und er zählt einmal");
        helper.succeed();
    }

    /**
     * If the last rack is broken down, the workers stand still.
     *
     * <p>What is already running runs to completion — killing it midway would
     * mean losing items that are in the hands of a flow right now. But new
     * work no longer starts.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutAServerTheWorkersStandStill(GameTestHelper helper) {
        BlockPos controller = threeChestsSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                worker holen {
                    from quelle
                    to storage
                    rate 1 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        helper.setBlock(controller.west(), Blocks.AIR);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.threads(), 0, "der Schrank ist weg");
        for (int i = 0; i < 10; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                "ohne Server darf kein Worker etwas bewegen");
        helper.succeed();
    }

    /** Controller, cable, a named chest with ore and a drive. */
    private static BlockPos threeChestsSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());
        BlockPos connector = controller.east().north();
        connector(helper, connector, Direction.NORTH);
        helper.setBlock(connector.north(), Blocks.CHEST);
        name(helper, connector, "quelle");
        if (helper.getBlockEntity(connector.north())
                instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_ORE, 16));
        }
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        return controller;
    }

    /**
     * A server rack with exactly as much computing power as given.
     *
     * <p>Memory and disk generous: whoever checks the compute limit should not
     * fail at the memory limit.
     */
    private static void smallRack(GameTestHelper helper, BlockPos at, int cpu) {
        placeRack(helper, at);
        fillBay(helper, at, 0, cpu, TEST_RAM, TEST_DISK);
    }

    /**
     * If the memory is full, the next flow fails visibly.
     *
     * <p>Unlike with the processors, nothing queues up here. A flow for which
     * there is no memory does not wait — it does not fit in, and that must
     * appear among the errors instead of vanishing silently.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullMemoryRejectsTheNextFlow(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        placeRack(helper, controller.west());
        // Plenty of processor, scarce memory: the limit that applies should
        // be the one under test.
        fillBay(helper, controller.west(), 0, 128, 8, TEST_DISK);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.memory(), 8, "der kleinste Speicher trägt acht");

        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    sleep 400t
                }"""), "Programm nicht übernommen");

        for (int i = 0; i < 9; i++) {
            entity.startFlow("wartet", java.util.List.of());
        }
        var engine = entity.flowEngine();
        helper.assertValueEqual(engine.inMemory(), 8, "mehr passt nicht hinein");
        helper.assertTrue(!engine.failed().isEmpty(),
                "der neunte muss unter den Fehlern stehen");
        // "Arbeitsspeicher" and not just "Speicher": the cells in the drive
        // are also called that, and whoever read this message went off to
        // install cells.
        helper.assertTrue(engine.failed().get(0).detail().contains("Arbeitsspeicher"),
                "und der Grund muss den richtigen Speicher nennen: "
                        + engine.failed().get(0).detail());
        helper.succeed();
    }

    /**
     * A program that is too large is not deployed at all.
     *
     * <p>When pressing Deploy, and not a minute later at a factory standing
     * still. And with the number in the message: "too large" alone does not
     * say by how much.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void atooLargeProgramIsRefused(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        placeRack(helper, controller.west());
        fillBay(helper, controller.west(), 0, TEST_CPU, TEST_RAM, 64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.diskSpace(), 64, "der kleinste Datenträger");

        String klein = "fn wenig() {\n" + "    log \"x\"\n".repeat(10) + "}";
        helper.assertTrue(entity.deploy(klein), "ein kleines Programm passt");
        int vorher = entity.programSize();
        helper.assertTrue(vorher <= 64, "und liegt unter der Grenze: " + vorher);

        String gross = "fn viel() {\n" + "    log \"x\"\n".repeat(70) + "}";
        helper.assertTrue(!entity.deploy(gross), "einundsiebzig passen nicht");
        helper.assertTrue(entity.diagnostics().stream()
                        .anyMatch(d -> d.message().contains("zu groß")),
                "die Meldung muss es sagen: " + entity.diagnostics());
        helper.assertTrue(entity.diagnostics().stream()
                        .anyMatch(d -> d.message().contains("64")),
                "und die Grenze nennen: " + entity.diagnostics());
        // The old program keeps running — a rejected one replaces nothing.
        helper.assertValueEqual(entity.programSize(), vorher, "das kleine steht noch");
        helper.succeed();
    }

    /**
     * If the disk is pulled, the network freezes.
     *
     * <p>Neither abort nor cut short — the same answer as on power outage.
     * Checked with two bays: if only one remained, the rack without a disk
     * would no longer be a server at all, and the network would stand still
     * for the old reason instead of the one under test.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void pullingTheDiskFreezesTheNetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        BlockPos rackPos = controller.west();
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        placeRack(helper, rackPos);
        fillBay(helper, rackPos, 0, TEST_CPU, TEST_RAM, 4096);
        fillBay(helper, rackPos, 1, 2, 8, 64);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.diskSpace(), 4160, "beide Datenträger zusammen");

        String programm = "fn viel() {\n" + "    log \"x\"\n".repeat(200) + "}";
        helper.assertTrue(entity.deploy(programm), "zweihunderteins passen auf 4160");
        helper.assertTrue(!entity.flowEngine().isFrozen(), "so läuft es");

        // The large disk out: a server with 64 remains.
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.DISK), ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertTrue(entity.hasServer(), "der zweite Einschub rechnet weiter");
        helper.assertTrue(!entity.programFits(), "das Programm passt nicht mehr");
        helper.assertTrue(entity.flowEngine().isFrozen(), "also steht das Netz");

        // Back in, and it keeps running.
        fillBay(helper, rackPos, 0, TEST_CPU, TEST_RAM, 4096);
        entity.rebuildNetwork();
        helper.assertTrue(!entity.flowEngine().isFrozen(), "und läuft wieder");
        helper.succeed();
    }

    /**
     * The program lives as a file beside the world and can be changed there.
     *
     * <p>The bridge to a proper editor. Two directions in one check, because
     * they are only worth something together: what is deployed in the game
     * is in the file; what is in the file applies in the game.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theProgramLivesInAFileBesideTheWorld(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn eins() {
                    let a = 1
                }"""), "das erste Programm wurde nicht übernommen");

        helper.runAfterDelay(2, () -> {
            java.nio.file.Path folder = entity.programFilePath();
            helper.assertTrue(folder != null, "es gibt keinen Ordner");
            java.nio.file.Path main = folder.resolve(
                    dev.devpanda.factorynetwork.lang.Project.MAIN);
            try {
                helper.assertTrue(java.nio.file.Files.isDirectory(folder),
                        "der Ordner liegt nicht da: " + folder);
                helper.assertTrue(java.nio.file.Files.readString(main).contains("fn eins"),
                        "main.mf enthält das Programm nicht");

                // A second file from outside: from now on it belongs.
                java.nio.file.Files.writeString(folder.resolve("zwei.mf"),
                        "fn zwei() {\n    let b = 2\n}");
            } catch (java.io.IOException failed) {
                helper.fail("Der Ordner ließ sich nicht anfassen: " + failed);
            }

            // Via runAfterDelay and not via a loop of serverTick calls:
            // within one tick the game time stands still, and the controller
            // only checks every twenty ticks.
            helper.runAfterDelay(25, () -> {
                helper.assertValueEqual(entity.project().names().size(), 2,
                        "die neue Datei gehört zum Projekt");
                helper.assertTrue(entity.program().functions().stream()
                                .anyMatch(fn -> fn.name().equals("zwei")),
                        "und ihre Funktion wurde übernommen");
                helper.assertTrue(entity.program().functions().stream()
                                .anyMatch(fn -> fn.name().equals("eins")),
                        "die erste ist dabei nicht verlorengegangen");

                // And gone again: deleting is an intention in a project.
                try {
                    java.nio.file.Files.delete(folder.resolve("zwei.mf"));
                } catch (java.io.IOException failed) {
                    helper.fail("Die Datei ließ sich nicht löschen: " + failed);
                }

                helper.runAfterDelay(25, () -> {
                    helper.assertValueEqual(entity.project().names().size(), 1,
                            "die gelöschte Datei ist weg");
                    helper.assertTrue(entity.program().functions().stream()
                                    .noneMatch(fn -> fn.name().equals("zwei")),
                            "und ihre Funktion mit");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * More flows than threads: the rest queue up.
     *
     * <p>Queued, not rejected. <b>Delay is recoverable, loss is not</b> — a
     * rejected event is gone forever, and the items sit until the next
     * restart in a machine nobody touches any more.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void moreFlowsThanThreadsQueueUp(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        smallRack(helper, controller.west(), 2);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.threads(), 2, "das kleinste Rechenwerk trägt zwei");

        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    sleep 5t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 4; i++) {
            entity.startFlow("wartet", java.util.List.of());
        }
        var engine = entity.flowEngine();
        helper.assertValueEqual(engine.occupied(), 2, "so viele laufen");
        helper.assertValueEqual(engine.queued(), 2, "so viele stehen an");

        // Once the first ones are through, the others move up.
        helper.runAfterDelay(12, () -> {
            for (int i = 0; i < 12; i++) {
                entity.serverTick();
            }
            helper.assertValueEqual(entity.flowEngine().queued(), 0,
                    "die Warteschlange muss leerlaufen");
            helper.succeed();
        });
    }

    /**
     * A queued flow survives being written down.
     *
     * <p>It has not taken a step yet, but it already has its frame. If it were
     * lost on save, work would vanish that nobody knows existed.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aQueuedFlowSurvivesBeingWrittenDown(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        smallRack(helper, controller.west(), 2);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    sleep 200t
                }"""), "Programm nicht übernommen");
        for (int i = 0; i < 3; i++) {
            entity.startFlow("wartet", java.util.List.of());
        }
        helper.assertValueEqual(entity.flowEngine().queued(), 1, "einer steht an");

        var registries = helper.getLevel().registryAccess();
        var block = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(controller), helper.getBlockState(controller),
                entity.saveWithFullMetadata(registries), registries);
        ControllerBlockEntity geladen = (ControllerBlockEntity) block;
        geladen.setLevel(helper.getLevel());
        var zurueck = geladen.flowEngine().flows().values().stream()
                .filter(flow -> flow.status()
                        == dev.devpanda.factorynetwork.runtime.flow.Flow.Status.QUEUED)
                .count();
        helper.assertValueEqual((int) zurueck, 1,
                "der angestellte Ablauf muss zurückkommen");
        helper.succeed();
    }

    /**
     * A queue that is too long fails visibly.
     *
     * <p>An unlimited one would be a plant that accumulates work it never
     * finishes. What goes beyond appears among the last errors — vanishing
     * silently would be the worse of the two.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anOverfullQueueFailsVisibly(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        smallRack(helper, controller.west(), 2);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn wartet() {
                    sleep 400t
                }"""), "Programm nicht übernommen");

        for (int i = 0; i < 40; i++) {
            entity.startFlow("wartet", java.util.List.of());
        }
        var engine = entity.flowEngine();
        helper.assertValueEqual(engine.occupied(), 2, "so viele laufen");
        helper.assertValueEqual(engine.queued(), 32, "mehr als das steht nicht an");
        helper.assertTrue(!engine.failed().isEmpty(),
                "die abgewiesenen müssen unter den Fehlern stehen");
        helper.assertTrue(engine.failed().get(0).detail().contains("stehen an"),
                "und der Grund muss die Warteschlange nennen: "
                        + engine.failed().get(0).detail());
        helper.succeed();
    }

    /**
     * The analyser also shows what provides room and computing power.
     *
     * <p>Drives, server racks and routers already belonged to the network and
     * were still invisible. Whoever looks for why nothing is stored or
     * nothing computes looks for exactly those.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theAnalyserShowsDrivesRacksAndRouters(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        helper.setBlock(controller.east(), FnBlocks.DENSE_CABLE.get());
        helper.setBlock(controller.east(2), FnBlocks.ROUTER.get());

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);

        for (var wanted : java.util.List.of(
                dev.devpanda.factorynetwork.analyser.AnalyserData.NodeState.DRIVE,
                dev.devpanda.factorynetwork.analyser.AnalyserData.NodeState.RACK,
                dev.devpanda.factorynetwork.analyser.AnalyserData.NodeState.ROUTER)) {
            helper.assertTrue(data.nodes().stream().anyMatch(node -> node.state() == wanted),
                    "Im Bild fehlt: " + wanted);
        }
        helper.succeed();
    }

    /**
     * A router's lane assignment survives being written down.
     *
     * <p>It lives in the BlockEntity and not in the block state — so it has
     * to be saved by hand. If it were lost, after a restart everything would
     * lie on lane one again, and two networks that crossed without touching
     * would suddenly be one.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void routerLanesSurviveBeingWrittenDown(GameTestHelper helper) {
        BlockPos routerPos = new BlockPos(1, 2, 1);
        helper.setBlock(routerPos, FnBlocks.ROUTER.get());
        var router = (dev.devpanda.factorynetwork.block.entity.RouterBlockEntity)
                helper.getBlockEntity(routerPos);
        net.minecraft.core.Direction oben = towards(helper, routerPos, routerPos.above());
        net.minecraft.core.Direction unten = towards(helper, routerPos, routerPos.below());
        router.setLane(oben, 3);
        router.setLane(unten, dev.devpanda.factorynetwork.block.entity
                .RouterBlockEntity.OFF);

        var registries = helper.getLevel().registryAccess();
        var geladen = (dev.devpanda.factorynetwork.block.entity.RouterBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(routerPos), helper.getBlockState(routerPos),
                        router.saveWithFullMetadata(registries), registries);
        helper.assertTrue(geladen != null, "Der Router kam nicht zurück");
        helper.assertValueEqual(geladen.lane(oben), 3, "Bahn oben");
        helper.assertValueEqual(geladen.lane(unten),
                dev.devpanda.factorynetwork.block.entity.RouterBlockEntity.OFF,
                "abgeklemmt bleibt abgeklemmt");
        // What nobody touched still lies on the default.
        helper.assertValueEqual(geladen.lane(towards(helper, routerPos, routerPos.north())), 1,
                "unberührte Seite");
        helper.succeed();
    }

    /**
     * The parts survive being written down.
     *
     * <p>Otherwise an empty rack would stand there after a restart, the
     * network would no longer compute, and nobody would think of the parts
     * having been lost on save.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void processorsSurviveBeingWrittenDown(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        smallRack(helper, rackPos, 2);
        fillBay(helper, rackPos, 1, 8, 8, 64);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        helper.assertValueEqual(rack.threads(), 10, "ein kleiner und ein mittlerer Einschub");

        var registries = helper.getLevel().registryAccess();
        var geladen = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(rackPos), helper.getBlockState(rackPos),
                        rack.saveWithFullMetadata(registries), registries);
        helper.assertTrue(geladen != null, "Der Schrank kam nicht zurück");
        helper.assertValueEqual(geladen.threads(), 10, "Rechenleistung nach dem Laden");
        helper.assertValueEqual(geladen.usedSlots(), 8, "belegte Plätze: zwei Gehäuse, sechs Teile");
        helper.succeed();
    }

    /**
     * A broken-down rack gives its parts back.
     *
     * <p>The loot table only sees the block state, not the BlockEntity.
     * Without this path an accidental hit would be the loss of thirty-six
     * parts.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void abrokenRackDropsItsProcessors(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        smallRack(helper, rackPos, 8);
        helper.setBlock(rackPos, Blocks.AIR);

        // <b>Not "some item drops".</b> An empty chassis would have passed
        // this test, and behind it would stand exactly the loss it is meant
        // to prevent: thirty-six parts nobody gets back.
        //
        // What drops is a <b>finished server</b> and not individual parts —
        // the chassis carries its parts inside it (see RackBlock.onRemove).
        // So what is checked is that it is not empty.
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(
                    dev.devpanda.factorynetwork.registry.FnItems.SERVER_CHASSIS.get(),
                    rackPos, 2.0);
            boolean packed = helper.getLevel()
                    .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(helper.absolutePos(rackPos))
                                    .inflate(2.0))
                    .stream()
                    .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                    .filter(stack -> stack.is(dev.devpanda.factorynetwork.registry
                            .FnItems.SERVER_CHASSIS.get()))
                    .anyMatch(stack -> !dev.devpanda.factorynetwork.item.ServerChassis
                            .isEmpty(stack));
            helper.assertTrue(packed,
                    "Das Gehäuse fällt heraus, aber ohne seine Bauteile");
        });
    }

    /**
     * An empty battery holds no window open — and a full one costs.
     *
     * <p>Without this run the charge level would be a number in the tooltip.
     * It pins down both: that power is really deducted, and that a device
     * without charge does not hold the window open.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void anEmptyBatteryClosesTheWindow(GameTestHelper helper) {
        BlockPos mast = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(mast, FnBlocks.MAST.get().defaultBlockState());

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(mast.getX() + 0.5, mast.getY() + 1.0, mast.getZ() + 0.5);

        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        var where = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), mast);
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, where);
        player.getInventory().setItem(0, device);

        var menu = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                1, player.getInventory(), mast, helper.getLevel().dimension(),
                dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL, 0);

        // Empty: nothing can be deducted, and the window must not stay open.
        helper.assertTrue(
                !menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION),
                "aus einem leeren Akku ließ sich Strom nehmen");
        helper.assertTrue(!menu.stillValid(player),
                "das Fenster bleibt mit leerem Akku offen");

        // Charged: it works, and the level drops by exactly that amount.
        var battery = device.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        battery.receiveEnergy(10_000, false);
        int before = dev.devpanda.factorynetwork.item.RemoteDeviceItem.energyOf(device);
        helper.assertTrue(before > 0, "der Akku hat die Ladung nicht angenommen");
        helper.assertTrue(menu.stillValid(player),
                "mit vollem Akku bleibt das Fenster trotzdem zu");

        helper.assertTrue(
                menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION),
                "die Handlung wurde trotz Ladung abgelehnt");
        int after = dev.devpanda.factorynetwork.item.RemoteDeviceItem.energyOf(
                player.getInventory().getItem(0));
        helper.assertTrue(
                after == before - dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION,
                "abgezogen wurden " + (before - after) + " statt "
                        + dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION);

        // And the case in between: too little for one action, but not empty.
        // A deduction that does not survive the refusal would be worse than
        // none at all — the player would lose the rest and get nothing.
        var drained = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(drained, where);
        int tooLittle = dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION - 20;
        drained.getCapability(net.neoforged.neoforge.capabilities.Capabilities
                .EnergyStorage.ITEM).receiveEnergy(tooLittle, false);
        player.getInventory().setItem(1, drained);

        var thin = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                3, player.getInventory(), mast, helper.getLevel().dimension(),
                dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL, 1);
        helper.assertTrue(
                !thin.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION),
                "eine Handlung ging trotz zu wenig Ladung durch");
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.energyOf(
                        player.getInventory().getItem(1)) == tooLittle,
                "der abgelehnte Griff hat trotzdem Strom gekostet");

        // At the block the same costs nothing: there the network pays.
        var fixed = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                2, player.getInventory(), mast);
        helper.assertTrue(fixed.charge(player, 1_000_000),
                "am Block wird der Akku belastet");
        helper.succeed();
    }

    /**
     * The key under which an item lies in the store.
     *
     * <p><b>An id says what something is, but not which one.</b> Two
     * pickaxes are the same item and still different things as soon as one
     * of them carries a name or is half used up.
     *
     * <p>Lives here and not in a pure unit test: {@code ItemStack} needs the
     * registrations, and {@code Bootstrap.bootStrap()} fails outside the game
     * on the mod files.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void anItemKeyKnowsWhichOneItIs(GameTestHelper helper) {
        // Same id, same data: the same key.
        helper.assertTrue(
                key("Kurt").equals(key("Kurt")),
                "zwei gleich benannte Hacken sind verschiedene Schlüssel");
        helper.assertTrue(key("Kurt").hashCode() == key("Kurt").hashCode(),
                "gleiche Schlüssel, verschiedene Hashes");

        // A name makes it a different item. That was exactly the bug: both
        // lay in the store as "Diamantspitzhacke".
        helper.assertTrue(!key("Kurt").equals(key("Erna")),
                "Kurt und Erna gelten als derselbe Gegenstand");
        helper.assertTrue(
                !key("Kurt").equals(dev.devpanda.factorynetwork.storage.ItemKey.bare(
                        net.minecraft.world.item.Items.DIAMOND_PICKAXE)),
                "eine benannte Hacke gilt wie eine nackte");

        // The amount does not count — otherwise three iron and five iron
        // would be two entries.
        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.ItemKey.of(
                        new ItemStack(Items.IRON_INGOT, 3)).equals(
                        dev.devpanda.factorynetwork.storage.ItemKey.of(
                                new ItemStack(Items.IRON_INGOT, 64))),
                "die Menge steckt im Schlüssel");

        // <b>The most dangerous trap of the rework.</b> A key whose hash
        // changes while it lies in a map makes its stock unfindable: it is
        // there and is never found again.
        ItemStack stack = named("Kurt");
        var stable = dev.devpanda.factorynetwork.storage.ItemKey.of(stack);
        int before = stable.hashCode();
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Erna"));
        stack.setCount(17);
        helper.assertTrue(stable.hashCode() == before,
                "der Schlüssel folgt dem Stapel, aus dem er gemacht wurde");
        helper.assertTrue(stable.equals(key("Kurt")),
                "der Schlüssel hat sich hinterher verändert");

        // And what it hands out is a copy.
        ItemStack out = stable.toStack(5);
        helper.assertTrue(out.getCount() == 5, "die Menge stimmt nicht");
        out.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Erna"));
        helper.assertTrue(stable.equals(key("Kurt")),
                "wer den herausgegebenen Stapel ändert, ändert den Schlüssel");

        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.ItemKey.of(ItemStack.EMPTY) == null,
                "Leeres bekommt einen Schlüssel");

        // The stack limit comes from the item: the component can change it,
        // and extracting in the terminal counts on that.
        ItemStack limited = new ItemStack(Items.IRON_INGOT);
        limited.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 8);
        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.ItemKey.of(limited).maxStackSize() == 8,
                "die eigene Stapelgrenze wird übergangen");
        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.IRON_INGOT)
                        .maxStackSize() == 64,
                "die gewöhnliche Stapelgrenze stimmt nicht");
        helper.succeed();
    }

    /** A diamond pickaxe with a name. */
    private static ItemStack named(String name) {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal(name));
        return stack;
    }

    /** Its key. */
    private static dev.devpanda.factorynetwork.storage.ItemKey key(String name) {
        return dev.devpanda.factorynetwork.storage.ItemKey.of(named(name));
    }

    /**
     * A worker now also takes along what carries data.
     *
     * <p><b>Until 28 Aug this run stood on its head:</b> it pinned down that
     * a named pickaxe <i>stays</i> in the chest. That was the emergency brake
     * as long as the store could only have taken it in gutted.
     *
     * <p>Now it travels — name included. A worker empties a chest in the
     * background, and nobody watches; that is exactly why this is the run on
     * which the rework proves itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aWorkerCarriesDataIntoStorage(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Lieblingshacke"));
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, named);
            container.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        }

        helper.assertTrue(entity.deploy("""
                worker haul {
                    from quarry_output
                    to storage
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        var key = dev.devpanda.factorynetwork.storage.ItemKey.of(named);
        helper.runAfterDelay(30, () -> {
            if (!(helper.getBlockEntity(source) instanceof ChestBlockEntity container)) {
                helper.fail("keine Kiste mehr da", source);
                return;
            }
            helper.assertTrue(container.getItem(0).isEmpty(),
                    "die Hacke liegt noch in der Kiste");
            helper.assertValueEqual(entity.storage().count(key), 1L,
                    "die Hacke ist nicht mit ihrem Namen im Lager angekommen");
            helper.assertTrue(entity.storage().count(Items.COBBLESTONE) > 0,
                    "im Lager liegt kein Bruchstein");
            helper.succeed();
        });
    }



    /**
     * Two crafts at two crafting tables do not cross.
     *
     * <p><b>A recipe exists once, not once per crafting table.</b> The
     * {@code RecipeManager} holds exactly one object per JSON, shared across
     * all players and every crafter block. Whoever remembers something in it
     * remembers it for everyone.
     *
     * <p>The case that breaks: player one lays out the finished craft, player
     * two lays out theirs, player one takes it out — and gets player two's
     * half. Both then hold a half whose partner lies elsewhere, and nobody
     * can say why.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void twoCraftsAtOnceDoNotCross(GameTestHelper helper) {
        var recipe = new dev.devpanda.factorynetwork.crafting.EntanglementRecipe(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
        var registries = helper.getLevel().registryAccess();

        // Two crafts interleaved, as at two crafting tables.
        ItemStack ersterBau = recipe.assemble(entanglementInput(), registries);
        ItemStack zweiterBau = recipe.assemble(entanglementInput(), registries);

        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.EntanglementItem.matched(
                        ersterBau, zweiterBau),
                "zwei Bauten ergaben dasselbe Paar");
        helper.assertTrue(ersterBau.getCount() == 2 && zweiterBau.getCount() == 2,
                "ein Bau liefert nicht beide Hälften auf einmal");

        // And every craft carries its two halves itself — no remainder that
        // could be overwritten in between.
        var rest = recipe.getRemainingItems(entanglementInput());
        helper.assertTrue(rest.stream().allMatch(ItemStack::isEmpty),
                "das Rezept legt noch etwas beiseite");
        helper.succeed();
    }

    /** Two network cores and a crystal, as they lie in the crafting table. */
    private static net.minecraft.world.item.crafting.CraftingInput entanglementInput() {
        return net.minecraft.world.item.crafting.CraftingInput.of(3, 1,
                java.util.List.of(
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                                .CORE_NETWORK.get()),
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                                .CRYSTAL.get()),
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                                .CORE_NETWORK.get())));
    }

    /**
     * The router taps individual colours off the trunk.
     *
     * <p><b>The fibre-optic picture.</b> One line carries all colours; the
     * router pulls one of them out and lets the rest run on.
     *
     * <p>Previously it was the opposite: colour-neutral, a mixer. Whatever
     * came together on one lane counted as connected — two separate
     * sub-networks grew together across it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aRouterTapsOneColourOffTheTrunk(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // The trunk: neutral, so all colours.
        BlockPos trunk = controller.east();
        placeCable(helper, trunk, dev.devpanda.factorynetwork.block.CableColour.NONE);

        // The router on it.
        BlockPos router = trunk.east();
        helper.setBlock(router, FnBlocks.ROUTER.get());
        if (!(helper.getBlockEntity(router)
                instanceof dev.devpanda.factorynetwork.block.entity.RouterBlockEntity box)) {
            helper.fail("kein Router", router);
            return;
        }
        // Towards the trunk: everything. To the north: only red.
        box.setFilter(Direction.WEST, null);
        box.setFilter(Direction.NORTH, dev.devpanda.factorynetwork.block.CableColour.RED);
        box.setFilter(Direction.SOUTH, dev.devpanda.factorynetwork.block.CableColour.BLUE);

        helper.assertTrue(box.filter(Direction.WEST) == null,
                "die Seite zum Hauptstrang filtert");
        helper.assertValueEqual(box.filter(Direction.NORTH),
                dev.devpanda.factorynetwork.block.CableColour.RED,
                "die Nordseite führt nicht Rot");
        // An untouched side lets everything through. That is the
        // compatible default: a freshly placed router connects instead of
        // standing still until somebody configures it.
        helper.assertTrue(!box.isOff(Direction.EAST),
                "eine frische Seite ist abgeklemmt");
        helper.assertTrue(box.filter(Direction.EAST) == null,
                "eine frische Seite filtert schon");
        box.turnOff(Direction.EAST);
        helper.assertTrue(box.isOff(Direction.EAST),
                "turnOff klemmt die Seite nicht ab");

        // A red cable to the north, a blue one to the south.
        BlockPos rot = router.north();
        placeCable(helper, rot, dev.devpanda.factorynetwork.block.CableColour.RED);
        if (helper.getBlockEntity(rot)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.NORTH).setLabel("am_roten");
        }
        BlockPos blau = router.south();
        placeCable(helper, blau, dev.devpanda.factorynetwork.block.CableColour.BLUE);
        if (helper.getBlockEntity(blau)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.SOUTH).setLabel("am_blauen");
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Both hang on the network — the trunk carries both colours.
        helper.assertTrue(entity.graph().connectorNames().contains("am_roten"),
                "das rote Teilnetz hängt nicht am Hauptstrang");
        helper.assertTrue(entity.graph().connectorNames().contains("am_blauen"),
                "das blaue Teilnetz hängt nicht am Hauptstrang");

        // <b>And the filter really blocks.</b> That is the test that matters:
        // a blue cable on an output that lets only red through must not be
        // reachable. Without it the filter could be missing and the run would
        // stay green — the trunk is neutral and connects with everything
        // anyway.
        BlockPos falsch = router.west().north();
        placeCable(helper, falsch, dev.devpanda.factorynetwork.block.CableColour.BLUE);
        BlockPos amFalschen = router.east();
        helper.setBlock(amFalschen, FnBlocks.CABLE.get());
        if (helper.getBlockEntity(amFalschen)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.EAST).setLabel("hinter_rot");
        }
        // The east output lets only red through; a neutral cable with a
        // device hangs there. Neutral connects with everything — unless the
        // router does not let it out.
        box.setFilter(Direction.EAST, dev.devpanda.factorynetwork.block.CableColour.GREEN);
        entity.rebuildNetwork();
        helper.assertTrue(!entity.graph().connectorNames().contains("hinter_rot"),
                "hinter einem grünen Ausgang hängt ein Gerät am neutralen Kabel");

        // And with a matching filter it gets through: it is the filter, not
        // the line.
        box.setFilter(Direction.EAST, null);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connectorNames().contains("hinter_rot"),
                "ohne Filter kommt das Gerät immer noch nicht durch");
        helper.succeed();
    }

    /**
     * The analyser shows what really flowed — not what could flow.
     *
     * <p><b>The number comes from the budget, not from an estimate.</b> An
     * analyser that names capacities and claims utilisation would be worse
     * than one that stays silent: you would then search at the spot it shows
     * instead of at the one that is tight.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theAnalyserShowsWhatActuallyFlowed(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        placeCable(helper, cable, dev.devpanda.factorynetwork.block.CableColour.NONE);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var node = new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                helper.absolutePos(cable), dev.devpanda.factorynetwork.block.CableColour.NONE);

        // Fresh: nothing flowed.
        helper.assertValueEqual(entity.runtime().budget().usedAt(node), 0,
                "ein frisches Budget meldet Verkehr");

        // Sent something through.
        // Just under half the cable width.
        int half = dev.devpanda.factorynetwork.network.Bandwidth.CABLE / 2;
        entity.runtime().budget().spend(java.util.List.of(node), half);
        helper.assertValueEqual(entity.runtime().budget().usedAt(node), half,
                "der Verkehr steht nicht im Budget");

        var daten = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        var strecke = daten.links().stream()
                .filter(link -> link.to().equals(helper.absolutePos(cable)))
                .findFirst().orElse(null);
        helper.assertTrue(strecke != null, "der Analysator kennt die Strecke nicht");
        helper.assertValueEqual(strecke.load(), half,
                "der Analysator zeigt nicht, was floss");
        helper.assertValueEqual(strecke.capacity(),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "der Analysator nennt die falsche Kapazität");
        helper.assertValueEqual(strecke.state(),
                dev.devpanda.factorynetwork.analyser.AnalyserData.LinkState.FREE,
                "die halbe Kabelbreite ist noch nicht eng");

        // And full is full.
        entity.runtime().budget().spend(java.util.List.of(node),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE);
        var voll = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity).links().stream()
                .filter(link -> link.to().equals(helper.absolutePos(cable)))
                .findFirst().orElseThrow();
        helper.assertValueEqual(voll.state(),
                dev.devpanda.factorynetwork.analyser.AnalyserData.LinkState.FULL,
                "anderthalb Kabelbreiten gelten nicht als voll");

        // And the display speaks kilobytes: that is the point of the unit.
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.perSecond(strecke.capacity()),
                "25,6 MB/s", "die Kapazität liest sich nicht als Megabyte");
        helper.succeed();
    }

    /**
     * The cable limits what passes through per tick.
     *
     * <p><b>The run the whole change hinges on.</b> Without it the numbers in
     * {@code Throughput} would be decoration: a worker could push as much as
     * it likes through a thin cable, and the limit would exist only in the
     * comment.
     *
     * <p>Checked directly at the budget, not at the result of a program: what
     * a worker moves in one tick hangs on a dozen things — here exactly one
     * of them is meant to count.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void theCableLimitsWhatPassesPerTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var budget = dev.devpanda.factorynetwork.network.TickBudget.create();

        BlockPos thin = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(thin, FnBlocks.CABLE.get().defaultBlockState());
        var node = new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                thin, dev.devpanda.factorynetwork.block.CableColour.NONE);
        var path = java.util.List.of(node);

        // Fresh: the whole cable is available.
        helper.assertValueEqual(budget.free(level, path),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "ein leeres Kabel gibt nicht seinen vollen Durchsatz her");

        // Half consumed: half remains.
        budget.spend(path, dev.devpanda.factorynetwork.network.Bandwidth.CABLE / 2);
        helper.assertValueEqual(budget.free(level, path),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE / 2,
                "der Verbrauch wird nicht abgezogen");

        // Full: nothing free any more — but not negative.
        budget.spend(path, dev.devpanda.factorynetwork.network.Bandwidth.CABLE);
        helper.assertValueEqual(budget.free(level, path), 0,
                "ein überfülltes Kabel meldet keine Null");

        // The new tick starts at zero.
        budget.reset();
        helper.assertValueEqual(budget.free(level, path),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "der Verbrauch überlebt den Tickwechsel");

        // And the bottleneck decides: dense plus thin is thin.
        BlockPos dense = helper.absolutePos(new BlockPos(3, 2, 1));
        level.setBlockAndUpdate(dense, FnBlocks.DENSE_CABLE.get().defaultBlockState());
        var gemischt = java.util.List.of(
                new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                        dense, dev.devpanda.factorynetwork.block.CableColour.NONE),
                node);
        helper.assertValueEqual(budget.free(level, gemischt),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "die Engstelle auf dem Weg wird übergangen");

        // What goes over the mixed path burdens both pieces.
        budget.spend(gemischt, 10);
        helper.assertValueEqual(budget.free(level, path),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE - 10,
                "das dünne Stück wurde nicht belastet");
        helper.succeed();
    }

    /**
     * Every reachable device hangs on the network — without exception.
     *
     * <p><b>This is the core of the change of 29 Aug.</b> Previously a device
     * could be reachable and still stay silent: no channel free any more.
     * That state no longer exists. What hangs on the cable works — only
     * possibly slower when many pull on the same strand.
     *
     * <p>Twenty connectors on an ordinary cable: under the old rule four of
     * them would have gone empty (sixteen channels). Now all twenty are
     * there.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyReachableDeviceIsOnTheNetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // A row of ordinary cables, two connectors on each.
        int gesetzt = 0;
        for (int schritt = 1; schritt <= 10; schritt++) {
            BlockPos cable = controller.east(schritt);
            placeCable(helper, cable, dev.devpanda.factorynetwork.block.CableColour.NONE);
            if (helper.getBlockEntity(cable)
                    instanceof dev.devpanda.factorynetwork.block.entity
                            .CableBusBlockEntity bus) {
                for (Direction side : java.util.List.of(Direction.NORTH, Direction.SOUTH)) {
                    var part = bus.addPart(side);
                    part.setLabel("gerat_" + gesetzt);
                    gesetzt++;
                }
            }
        }
        helper.assertValueEqual(gesetzt, 20, "es wurden nicht zwanzig Anschlüsse gesetzt");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // All twenty, although an ordinary cable used to carry sixteen.
        for (int i = 0; i < gesetzt; i++) {
            helper.assertTrue(entity.graph().connectorNames().contains("gerat_" + i),
                    "gerat_" + i + " hängt nicht am Netz");
        }
        helper.succeed();
    }

    /**
     * The weakest point on the path decides the throughput.
     *
     * <p>A dense cable behind an ordinary one gains nothing — the goods have
     * to pass through both. That is the same rule that used to apply to the
     * channels, only now it counts stacks instead of devices.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theWeakestCableDecidesTheThroughput(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // Dense, then ordinary, then dense again.
        BlockPos erst = controller.east();
        helper.setBlock(erst, FnBlocks.DENSE_CABLE.get());
        BlockPos eng = erst.east();
        placeCable(helper, eng, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos dann = eng.east();
        helper.setBlock(dann, FnBlocks.DENSE_CABLE.get());

        var level = helper.getLevel();
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level,
                        helper.absolutePos(erst)),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "das dichte Kabel trägt nicht den dichten Durchsatz");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level,
                        helper.absolutePos(eng)),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "das gewöhnliche Kabel trägt nicht den gewöhnlichen Durchsatz");

        // A device behind the bottleneck: its path is as good as the weakest
        // piece on it.
        if (helper.getBlockEntity(dann)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.NORTH).setLabel("dahinter");
        }
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var device = entity.graph().positionsOf("dahinter").stream().findFirst().orElse(null);
        helper.assertTrue(device != null, "dahinter hängt nicht am Netz");
        helper.assertValueEqual(entity.graph().throughputTo(level, device),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "die Engstelle auf dem Weg wird übergangen");
        helper.succeed();
    }

    /**
     * Router, gateway and bridge carry as much as a dense cable.
     *
     * <p>They are line and not multiplier. If one of them carried more, it
     * would be the spot where you bypass the limit — and then there would be
     * none.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void junctionsCarryLikeADenseCable(GameTestHelper helper) {
        var level = helper.getLevel();
        record Case(String name, net.minecraft.world.level.block.Block block) { }
        for (Case one : java.util.List.of(
                new Case("Router", FnBlocks.ROUTER.get()),
                new Case("Gateway", FnBlocks.GATEWAY.get()),
                new Case("Quantum-Brücke", FnBlocks.BRIDGE.get()))) {
            BlockPos where = helper.absolutePos(new BlockPos(1, 2, 1));
            level.setBlockAndUpdate(where, one.block().defaultBlockState());
            helper.assertValueEqual(
                    dev.devpanda.factorynetwork.network.Bandwidth.at(level, where),
                    dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                    one.name() + " trägt nicht so viel wie ein dichtes Kabel");
            level.removeBlock(where, false);
        }

        // And the controller does limit — since 30 Aug. Previously UNLIMITED
        // stood here with the reasoning "it is a target, not a route". It is
        // both, and as a route it lies on every path.
        BlockPos controller = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(controller, FnBlocks.CONTROLLER.get().defaultBlockState());
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level, controller),
                dev.devpanda.factorynetwork.network.Bandwidth.CONTROLLER,
                "der Controller ohne Anbau trägt nicht so viel wie ein dichtes Kabel");
        helper.succeed();
    }

    /**
     * An AE2 cell moves into our network — with everything inside it.
     *
     * <p><b>The follow-on benefit of the store rework, redeemed.</b> AE2
     * stores items with everything they carry. Until 28 Aug our store kept
     * only the id — an enchanted book from an AE2 cell would have become an
     * empty one on import. That is exactly what this run checks.
     *
     * <p>It only runs if AE2 is in the pack. In the development setup it is;
     * a pack without AE2 skips it instead of letting it fail — a missing mod
     * is not a bug of this mod.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anAe2CellMovesIn(GameTestHelper helper) {
        if (!dev.devpanda.factorynetwork.compat.ae2.FnAe2.installed()) {
            helper.succeed();
            return;
        }

        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K4);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // A real AE2 cell, filled via AE2's own API.
        ItemStack cell = new ItemStack(appeng.core.definitions.AEItems.ITEM_CELL_64K.asItem());
        helper.assertTrue(
                dev.devpanda.factorynetwork.compat.ae2.Ae2Cells.isCell(cell),
                "AE2 erkennt seine eigene Zelle nicht");

        var inventory = appeng.api.storage.StorageCells.getCellInventory(cell, null);
        helper.assertTrue(inventory != null, "die Zelle lässt sich nicht öffnen");

        ItemStack enchanted = new ItemStack(Items.DIAMOND_PICKAXE);
        enchanted.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Erbstück"));
        var source = appeng.api.networking.security.IActionSource.empty();
        inventory.insert(appeng.api.stacks.AEItemKey.of(enchanted), 1,
                appeng.api.config.Actionable.MODULATE, source);
        inventory.insert(appeng.api.stacks.AEItemKey.of(new ItemStack(Items.IRON_INGOT)), 100,
                appeng.api.config.Actionable.MODULATE, source);
        inventory.persist();

        long moved = dev.devpanda.factorynetwork.compat.ae2.Ae2Cells.drainInto(
                cell, entity.storage());
        helper.assertValueEqual(moved, 101L, "aus der Zelle kam nicht alles");

        var key = dev.devpanda.factorynetwork.storage.ItemKey.of(enchanted);
        helper.assertValueEqual(entity.storage().count(key), 1L,
                "die benannte Hacke ist nicht mit ihrem Namen angekommen");
        helper.assertValueEqual(entity.storage().count(
                        dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.IRON_INGOT)),
                100L, "das Eisen fehlt");

        // And the cell is empty afterwards: it is a move, not a copy.
        helper.assertTrue(
                appeng.api.storage.StorageCells.getCellInventory(cell, null)
                        .getAvailableStacks().isEmpty(),
                "die AE2-Zelle hat ihren Inhalt behalten — das wäre eine Kopie");
        helper.succeed();
    }

    /**
     * Both bridges show whether the link is up.
     *
     * <p><b>Without this display you look for the fault in the cable.</b> A
     * bridge whose partner was broken down otherwise looks like one that is
     * working — and the network ends without visible reason.
     *
     * <p><b>And the far end has to learn of it too.</b> It is not touched
     * when breaking down; whoever only switches their own side leaves a
     * light burning over there with nothing behind it any more.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void bothEndsShowTheLink(GameTestHelper helper) {
        BlockPos hier = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos dort = helper.absolutePos(new BlockPos(4, 2, 4));
        ItemStack paar = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();

        placeBridge(helper, hier, paar.split(1));
        helper.assertTrue(!linked(helper, hier),
                "eine Brücke ohne Partner leuchtet schon");

        placeBridge(helper, dort, paar);
        helper.assertTrue(linked(helper, hier), "die erste Brücke zeigt nichts an");
        helper.assertTrue(linked(helper, dort), "die zweite Brücke zeigt nichts an");

        // Break down the far end: here the light must go out.
        helper.getLevel().removeBlock(dort, false);
        helper.assertTrue(!linked(helper, hier),
                "die verbliebene Brücke leuchtet weiter, obwohl drüben nichts mehr ist");
        helper.succeed();
    }

    /** Does this bridge show a standing link? */
    private static boolean linked(GameTestHelper helper, BlockPos where) {
        var state = helper.getLevel().getBlockState(where);
        return state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock
                && state.getValue(dev.devpanda.factorynetwork.block.BridgeBlock.LINKED);
    }

    /**
     * The network reaches through the bridge — and only through a paired one.
     *
     * <p><b>The proof that the bridge does something.</b> Two cable strands
     * that do not touch each other; only the two bridges in between connect
     * them. Without the jump in the graph the network ends at the first
     * strand, and the second belongs to nobody.
     *
     * <p>The cross-check is built into the setup: check without halves first,
     * then with. A run that only looks at the finished state would mistake a
     * working bridge for two strands that happen to touch after all.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theNetworkReachesThroughTheBridge(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // First strand: controller, cable, bridge.
        BlockPos hier = controller.east();
        placeCable(helper, hier, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos brueckeHier = hier.east();
        helper.setBlock(brueckeHier, FnBlocks.BRIDGE.get());

        // Second strand, far away and without contact: bridge, cable, drive.
        // The drive shows whether the network arrives there.
        BlockPos brueckeDort = new BlockPos(1, 4, 5);
        helper.setBlock(brueckeDort, FnBlocks.BRIDGE.get());
        BlockPos dortKabel = brueckeDort.east();
        placeCable(helper, dortKabel, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos drivePos = dortKabel.east();
        driveWithCell(helper, drivePos, dev.devpanda.factorynetwork.storage.CellTier.K1);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(!entity.graph().contains(helper.absolutePos(drivePos)),
                "das Laufwerk hängt am Netz, obwohl keine Hälften eingesetzt sind");

        // Now the two halves go in.
        ItemStack paar = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
        if (helper.getBlockEntity(brueckeHier)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity eine) {
            eine.setItem(0, paar.split(1));
        }
        if (helper.getBlockEntity(brueckeDort)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity zwei) {
            zwei.setItem(0, paar);
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().bridges().size() == 2,
                "der Graph kennt " + entity.graph().bridges().size() + " Brücken statt zwei");
        helper.assertTrue(entity.graph().contains(helper.absolutePos(drivePos)),
                "das Laufwerk jenseits der Brücke gehört nicht zum Netz");
        helper.assertTrue(entity.graph().contains(helper.absolutePos(dortKabel)),
                "das Kabel jenseits der Brücke gehört nicht zum Netz");
        helper.succeed();
    }

    /**
     * Two bridges with the same number find each other — and only those.
     *
     * <p><b>No searching, registering.</b> A bridge that had to search the
     * world for its partner would scan millions of blocks on every query.
     * Instead it registers itself in a directory on load — the same approach
     * as with the controller.
     *
     * <p>Three rules, and each catches a case that exists in the game:
     * <ol>
     *   <li>A bridge without a half belongs to nobody.</li>
     *   <li>A bridge is never its own partner — otherwise a network would
     *       point through itself at itself.</li>
     *   <li>Three bridges with the same number connect nothing at all. In
     *       creative mode a half can be duplicated, and "two of three, but
     *       which" is not a rule anybody can guess.</li>
     * </ol>
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void bridgesFindTheirPartner(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos hier = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos dort = helper.absolutePos(new BlockPos(4, 2, 4));

        ItemStack paar = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
        ItemStack eine = paar.split(1);

        placeBridge(helper, hier, eine);
        helper.assertTrue(
                dev.devpanda.factorynetwork.network.BridgeRegistry.partnerOf(level, hier)
                        == null,
                "eine Brücke allein hat schon einen Partner");

        placeBridge(helper, dort, paar);
        helper.assertTrue(
                dort.equals(dev.devpanda.factorynetwork.network.BridgeRegistry
                        .partnerOf(level, hier)),
                "die beiden Brücken finden einander nicht");
        helper.assertTrue(
                hier.equals(dev.devpanda.factorynetwork.network.BridgeRegistry
                        .partnerOf(level, dort)),
                "die Verbindung gilt nur in eine Richtung");

        // A third one with the same number: now nothing is unambiguous any more.
        BlockPos dritte = helper.absolutePos(new BlockPos(7, 2, 1));
        ItemStack kopie = eine.copy();
        placeBridge(helper, dritte, kopie);
        helper.assertTrue(
                dev.devpanda.factorynetwork.network.BridgeRegistry.partnerOf(level, hier)
                        == null,
                "drei Brücken mit einer Nummer verbinden trotzdem");

        // The third one gone, and the two find each other again.
        level.removeBlock(dritte, false);
        helper.assertTrue(
                dort.equals(dev.devpanda.factorynetwork.network.BridgeRegistry
                        .partnerOf(level, hier)),
                "nach dem Abbau der dritten bleibt die Verbindung tot");

        // And a foreign number connects nothing.
        BlockPos fremd = helper.absolutePos(new BlockPos(7, 2, 4));
        placeBridge(helper, fremd,
                dev.devpanda.factorynetwork.item.EntanglementItem.newPair());
        helper.assertTrue(
                dev.devpanda.factorynetwork.network.BridgeRegistry.partnerOf(level, fremd)
                        == null,
                "eine fremde Nummer hat einen Partner gefunden");
        helper.succeed();
    }

    /** Places a bridge and puts a half into it. */
    private static void placeBridge(GameTestHelper helper, BlockPos where, ItemStack half) {
        helper.getLevel().setBlockAndUpdate(where,
                FnBlocks.BRIDGE.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(where)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity bridge) {
            bridge.setItem(0, half);
        }
    }

    /**
     * One craft yields both halves, and they belong together.
     *
     * <p>As one stack of two: a recipe has one output slot, and anything that
     * would have to be kept beside it would belong to all crafting tables at
     * once — see {@code twoCraftsAtOnceDoNotCross}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void oneCraftMakesOnePair(GameTestHelper helper) {
        var recipe = new dev.devpanda.factorynetwork.crafting.EntanglementRecipe(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC);

        helper.assertTrue(recipe.matches(entanglementInput(), helper.getLevel()),
                "zwei Netzkerne und ein Kristall ergeben keine Verschränkung");

        ItemStack paar = recipe.assemble(entanglementInput(),
                helper.getLevel().registryAccess());
        helper.assertTrue(paar.getCount() == 2,
                "der Bau liefert " + paar.getCount() + " statt zwei Hälften");
        ItemStack eine = paar.copy();
        eine.setCount(1);
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.EntanglementItem.matched(eine, paar),
                "die Hälften eines Stapels kennen einander nicht");

        // What does not belong in there yields nothing either.
        var falsch = net.minecraft.world.item.crafting.CraftingInput.of(3, 1,
                java.util.List.of(
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                                .CORE_NETWORK.get()),
                        new ItemStack(Items.DIRT),
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                                .CORE_NETWORK.get())));
        helper.assertTrue(!recipe.matches(falsch, helper.getLevel()),
                "Dreck geht als Kristall durch");
        helper.succeed();
    }

    /**
     * Two halves of an entanglement belong together — and only those.
     *
     * <p>The pair arises when crafting, not when clicking: whoever first
     * places two bridges and then links them has to remember which goes
     * where; whoever inserts two halves of the same stack has to remember
     * nothing at all.
     *
     * <p><b>Whether a bridge links to itself is not decided here.</b> The
     * block decides that by position — two places, the same number. A rule
     * on the item would be a second truth about it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void twoHalvesKnowEachOther(GameTestHelper helper) {
        ItemStack paar = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
        helper.assertTrue(paar.getCount() == 2,
                "ein Bau liefert " + paar.getCount() + " statt zwei Hälften");
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.EntanglementItem.idOf(paar) != null,
                "eine frische Hälfte hat keine Kennnummer");

        // Split, both remain the same pair — that is how they get into two
        // bridges.
        ItemStack eine = paar.split(1);
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.EntanglementItem.matched(eine, paar),
                "die beiden Hälften kennen sich nach dem Teilen nicht mehr");

        // Two from different crafts do not belong together. Without this
        // check any half could match any other, and two bridges in the same
        // world would link by accident.
        ItemStack anderes = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.EntanglementItem.matched(eine, anderes),
                "Hälften aus verschiedenen Bauten gelten als Paar");

        // And an item without a number matches nothing, not even a second one
        // without a number.
        ItemStack roh = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.ENTANGLEMENT.get());
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.EntanglementItem.matched(roh, eine),
                "eine unverschränkte Hälfte passt zu einer verschränkten");
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.EntanglementItem.matched(roh, roh.copy()),
                "zwei unverschränkte Hälften gelten als Paar");
        helper.succeed();
    }

    /**
     * And back: what comes out of the store still carries its data.
     *
     * <p><b>The missing half of the circle.</b> That a named tool goes into
     * the store is pinned down by {@code aWorkerCarriesDataIntoStorage}. The
     * way out is a place of its own — there the stack was rebuilt from an
     * id, and the name stayed behind in the store.
     *
     * <p>Two entries of the same id make the check sharp: a worker that takes
     * the bare ones first would never have touched the named one and the run
     * would stay green without showing anything. That is why only the named
     * one lies there.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatLeavesStorageKeepsItsData(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K4);
        entity.rebuildNetwork();

        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Lieblingshacke"));
        entity.storage().insert(named);

        helper.assertTrue(entity.deploy("""
                worker back {
                    from storage
                    to depot
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        BlockPos target = controller.east().south().south();
        helper.runAfterDelay(30, () -> {
            if (!(helper.getBlockEntity(target) instanceof ChestBlockEntity container)) {
                helper.fail("keine Zielkiste", target);
                return;
            }
            ItemStack got = container.getItem(0);
            helper.assertTrue(got.getItem() == Items.DIAMOND_PICKAXE,
                    "in der Kiste liegt keine Hacke, sondern " + got);
            helper.assertTrue(
                    got.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null,
                    "die Hacke kam ohne ihren Namen aus dem Lager");
            helper.succeed();
        });
    }

    /**
     * A cell from before the rework still reads.
     *
     * <p><b>Otherwise the rework would be data loss with the opposite
     * sign.</b> Every cell lying in the ground today writes id and amount and
     * no {@code components} field. An entry without this field must be an
     * item without data of its own — otherwise a whole store would vanish on
     * the first load.
     *
     * <p>The old format is built by hand here, not via today's write path:
     * that would write the new one. Only that way is the check one about the
     * format and not one about itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void anOldCellStillReads(GameTestHelper helper) {
        ItemStack cell = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.CELLS
                        .get(dev.devpanda.factorynetwork.storage.CellTier.K4).get());

        // Exactly what stood in a cell before 28 Aug.
        net.minecraft.nbt.ListTag posten = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag eisen = new net.minecraft.nbt.CompoundTag();
        eisen.putString("Item", "minecraft:iron_ingot");
        eisen.putLong("Count", 128);
        posten.add(eisen);
        net.minecraft.nbt.CompoundTag hacke = new net.minecraft.nbt.CompoundTag();
        hacke.putString("Item", "minecraft:diamond_pickaxe");
        hacke.putLong("Count", 2);
        posten.add(hacke);
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell,
                tag -> tag.put("Cell", posten));

        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(
                cell, helper.getLevel().registryAccess());
        helper.assertValueEqual(inhalt.size(), 2, "die alte Zelle hat Posten verloren");
        helper.assertValueEqual(inhalt.getOrDefault(
                        dev.devpanda.factorynetwork.storage.ItemKey
                                .bare(Items.IRON_INGOT), 0L), 128L,
                "das Eisen aus der alten Zelle fehlt");
        helper.assertValueEqual(inhalt.getOrDefault(
                        dev.devpanda.factorynetwork.storage.ItemKey
                                .bare(Items.DIAMOND_PICKAXE), 0L), 2L,
                "die Hacken aus der alten Zelle fehlen");

        // And whoever writes it anew without changing anything writes the
        // same again: no components field on a bare item. The way back to an
        // older version thus stays open.
        dev.devpanda.factorynetwork.storage.CellContents.write(cell, inhalt,
                helper.getLevel().registryAccess());
        var geschrieben = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag().getList("Cell", net.minecraft.nbt.Tag.TAG_COMPOUND);
        helper.assertValueEqual(geschrieben.size(), 2, "beim Schreiben ging ein Posten verloren");
        for (int i = 0; i < geschrieben.size(); i++) {
            helper.assertTrue(!geschrieben.getCompound(i).contains("components"),
                    "ein nackter Gegenstand bekam ein components-Feld");
            helper.assertTrue(geschrieben.getCompound(i).contains("Count"),
                    "die Menge steht nicht mehr unter Count");
        }
        helper.succeed();
    }

    /**
     * What carries data goes into the store and comes back unchanged.
     *
     * <p><b>The proof run of the whole rework.</b> Until 28 Aug the store
     * kept only id and amount: an enchanted book, a named tool, a half-charged
     * device went in as "one piece of that" and came back bare. Nothing
     * warned, nothing stood out — until somebody fetched their tool back.
     *
     * <p>The whole circle is checked: in, look it up in the stock, out again.
     * At each of these three spots it used to get lost.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void itemsWithDataSurviveStorage(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K4);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Lieblingshacke"));

        helper.assertValueEqual(entity.storage().insert(named), 0L,
                "die benannte Hacke ging nicht ins Lager");

        // In the stock it is an entry of its own — next to a bare one.
        entity.storage().insert(new ItemStack(Items.DIAMOND_PICKAXE, 3));
        var key = dev.devpanda.factorynetwork.storage.ItemKey.of(named);
        helper.assertValueEqual(entity.storage().count(key), 1L,
                "die benannte Hacke steht nicht als eigener Posten im Bestand");
        helper.assertValueEqual(entity.storage().count(
                        dev.devpanda.factorynetwork.storage.ItemKey
                                .bare(Items.DIAMOND_PICKAXE)), 3L,
                "die nackten Hacken sind mit der benannten verschmolzen");

        // And together per id: that is what a program sees.
        helper.assertValueEqual(entity.storage().count(Items.DIAMOND_PICKAXE), 4L,
                "je Kennung zählen nicht alle Ausführungen zusammen");

        // Out again, and the name is still there.
        helper.assertValueEqual(entity.storage().extract(key, 1), 1L,
                "die benannte Hacke kam nicht wieder heraus");
        helper.assertValueEqual(entity.storage().count(key), 0L,
                "sie liegt danach immer noch im Lager");
        ItemStack back = key.toStack(1);
        helper.assertTrue(
                back.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null,
                "sie kam ohne ihren Namen zurück");

        // And the bare ones are untouched: whoever takes a particular variant
        // does not take just any.
        helper.assertValueEqual(entity.storage().count(
                        dev.devpanda.factorynetwork.storage.ItemKey
                                .bare(Items.DIAMOND_PICKAXE)), 3L,
                "das Entnehmen hat die nackten Hacken angefasst");
        helper.succeed();
    }

    /**
     * And the same across a cell, including saving and loading.
     *
     * <p>The stock lives in the item, not in the drive. What went wrong here
     * would only show after a restart of the world — and then it is gone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void dataInACellSurvivesSaving(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        BlockPos drivePos = controller.above();
        driveWithCell(helper, drivePos, dev.devpanda.factorynetwork.storage.CellTier.K4);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Lieblingshacke"));
        entity.storage().insert(named);

        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        drive.flushCells();

        // Read back from the item — that is the path via disk.
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(
                drive.cell(0), helper.getLevel().registryAccess());
        var key = dev.devpanda.factorynetwork.storage.ItemKey.of(named);
        helper.assertValueEqual(inhalt.getOrDefault(key, 0L), 1L,
                "die Zelle hat den Namen beim Schreiben verloren");

        // And a bare pickaxe stays a second entry.
        entity.storage().insert(new ItemStack(Items.DIAMOND_PICKAXE));
        drive.flushCells();
        var wieder = dev.devpanda.factorynetwork.storage.CellContents.read(
                drive.cell(0), helper.getLevel().registryAccess());
        helper.assertValueEqual(wieder.size(), 2,
                "benannte und nackte Hacke liegen in einem Posten");
        helper.succeed();
    }

    /**
     * The open device cannot be stored away.
     *
     * <p><b>Otherwise you pull the ladder up behind you.</b> Whoever puts
     * their wireless terminal into the store from within the terminal has it
     * in the network — and cannot get at it any more without a second device
     * or a way to the terminal block. The window would close in the same
     * moment, and the network would keep the key to itself.
     *
     * <p>The slot is therefore locked as long as its window is open — against
     * the shift-click and against picking it up with the mouse.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void theOpenDeviceCannotBeStoredAway(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos mast = helper.absolutePos(new BlockPos(1, 2, 1));
        var where = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), mast);

        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, where);
        // Slot 0 in the inventory is the first of the hotbar.
        player.getInventory().setItem(0, device);

        var menu = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                1, player.getInventory(), mast, helper.getLevel().dimension(),
                dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL, 0);

        // Three rows of inventory, then the hotbar: slot 0 lies in the window
        // at index 27.
        var slot = menu.getSlot(27);
        helper.assertTrue(slot.getItem() == device,
                "an Stelle 27 liegt nicht das Gerät, sondern " + slot.getItem());
        helper.assertTrue(!slot.mayPickup(player),
                "das offene Gerät lässt sich mit der Maus aufnehmen");

        // And the shift-click does not take it either.
        menu.quickMoveStack(player, 27);
        helper.assertTrue(player.getInventory().getItem(0) == device,
                "der Umschalt-Klick hat das offene Gerät weggeräumt");

        // The cross-check: every other slot remains freely usable. Without it
        // the lock could extend to the whole inventory.
        player.getInventory().setItem(1, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WRENCH.get()));
        helper.assertTrue(menu.getSlot(28).mayPickup(player),
                "auch der Nachbarplatz ist gesperrt");

        // And at the block nothing is locked at all: there is no device there
        // that could be taken away from you.
        var fixed = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                2, player.getInventory(), mast);
        helper.assertTrue(fixed.getSlot(27).mayPickup(player),
                "am Terminal-Block ist ein Platz gesperrt");
        helper.succeed();
    }

    /**
     * What an opener writes, the window reads exactly.
     *
     * <p><b>This is the run that was missing.</b> During the rework to worlds
     * the device wrote one field fewer than the constructor read: the enum
     * was read as a boolean, after that every further field was shifted, and
     * the client was kicked out of the world on opening with an
     * ArrayIndexOutOfBoundsException. No test run could see that — all called
     * the constructor with values instead of with a buffer.
     *
     * <p>So the path over the wire is checked, and at the end the question
     * that matters: <b>is the buffer empty?</b> If something is left over,
     * the reader took too little; if something is missing, too much.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void theWindowReadsExactlyWhatTheOpenerWrote(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos where = helper.absolutePos(new BlockPos(1, 2, 1));

        // The path at the block: only a position.
        var atBlock = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        dev.devpanda.factorynetwork.client.menu.TerminalMenu.writeBlock(atBlock, where);
        var fixed = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                1, player.getInventory(), atBlock);
        helper.assertTrue(atBlock.readableBytes() == 0,
                "am Block blieben " + atBlock.readableBytes() + " Bytes ungelesen");
        helper.assertTrue(fixed.device() == null,
                "der Block meldet ein Ferngerät");
        helper.assertTrue(where.equals(fixed.position()),
                "der Ort ist auf dem Weg verlorengegangen");

        // And the path from afar: position, world, kind, slot.
        for (var kind : dev.devpanda.factorynetwork.upgrade.RemoteDevice.values()) {
            var remote = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            var mast = net.minecraft.core.GlobalPos.of(
                    net.minecraft.world.level.Level.NETHER, where);
            dev.devpanda.factorynetwork.client.menu.TerminalMenu.writeRemote(
                    remote, mast, kind, 7);
            var window = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                    2, player.getInventory(), remote);
            helper.assertTrue(remote.readableBytes() == 0,
                    kind + ": " + remote.readableBytes() + " Bytes ungelesen");
            helper.assertTrue(window.device() == kind,
                    kind + " kam als " + window.device() + " an");
            helper.assertTrue(where.equals(window.position()),
                    kind + ": der Ort stimmt nicht");
        }
        helper.succeed();
    }

    /**
     * Across a dimension boundary only the infinity card reaches.
     *
     * <p>What is checked is the <b>resolution</b>, not the journey: a mock
     * player does not change dimension cleanly, so it stays in the overworld
     * and the mast stands in the Nether. That is exactly the case in
     * question — somebody stands somewhere other than their network.
     *
     * <p><b>Two things show up here that no other run sees:</b> whether the
     * mast is found in its own world instead of the player's, and whether a
     * coordinate alone is enough. It is not — the same number exists in
     * every dimension.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void onlyTheInfinityCardCrossesWorlds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            helper.fail("kein Nether im Prüfserver");
            return;
        }

        // The mast stands in the Nether. setBlockAndUpdate loads that piece
        // of world, so it is reachable too.
        BlockPos far = new BlockPos(64, 70, 64);
        nether.setBlockAndUpdate(far, FnBlocks.MAST.get().defaultBlockState());
        if (!(nether.getBlockEntity(far)
                instanceof dev.devpanda.factorynetwork.block.entity.MastBlockEntity mast)) {
            helper.fail("kein Mast im Nether");
            return;
        }
        var where = net.minecraft.core.GlobalPos.of(
                net.minecraft.world.level.Level.NETHER, far);

        // The player stays in the overworld.
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.LAPTOP.get());
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, where);
        player.getInventory().setItem(0, device);

        helper.assertTrue(
                dev.devpanda.factorynetwork.terminal.RemoteAccess.mastAt(player, where) != null,
                "der Mast im Nether wird nicht gefunden");
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "ohne Grenzenlos-Karte geht der Zugriff über die Dimensionsgrenze");

        // Four range cards change nothing about that: a dimension boundary is
        // not a distance.
        mast.setItem(0, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.RANGE_CARD.get(), 4));
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "vier Reichweitenkarten überbrücken die Dimensionsgrenze");

        // The infinity card does.
        mast.setItem(0, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.INFINITY_CARD.get()));
        helper.assertTrue(
                dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "die Grenzenlos-Karte reicht nicht über die Dimensionsgrenze");

        // And a coordinate alone is not enough: the same position in the
        // overworld is a different mast — or none at all.
        var samePlaceHere = net.minecraft.core.GlobalPos.of(
                net.minecraft.world.level.Level.OVERWORLD, far);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(
                        player, 0, samePlaceHere),
                "dieselbe Koordinate in einer anderen Welt gilt als derselbe Mast");

        nether.removeBlock(far, false);
        helper.succeed();
    }

    /**
     * Every block that belongs to the network also gets an arm from the cable.
     *
     * <p><b>The same truth stood in three places</b>, and each knew a
     * different subset: {@code consumerAt} collects consumers,
     * {@code contains} lists who belongs to the network, and
     * {@code connects} decides where an arm grows. The mast was in the first
     * and missing from the other two — it hung on the network and looked as
     * if it hung on nothing.
     *
     * <p>Before it, the same happened with the drive and the server rack.
     * This run holds all three lists against each other so that it shows up
     * with the next block and not only in the game.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyNetworkBlockGetsAnArm(GameTestHelper helper) {
        record Case(String name, net.minecraft.world.level.block.Block block) { }
        var cases = java.util.List.of(
                new Case("Controller", FnBlocks.CONTROLLER.get()),
                new Case("Terminal", FnBlocks.TERMINAL.get()),
                new Case("Laufwerk", FnBlocks.DRIVE.get()),
                new Case("Serverschrank", FnBlocks.RACK.get()),
                new Case("Anzeige", FnBlocks.DISPLAY.get()),
                new Case("Router", FnBlocks.ROUTER.get()),
                new Case("Fabricator", FnBlocks.FABRICATOR.get()),
                new Case("Sendemast", FnBlocks.MAST.get()),
                new Case("Gateway", FnBlocks.GATEWAY.get()),
                new Case("Anbau", FnBlocks.CONTROLLER_EXTENSION.get()),
                new Case("Quantum-Brücke", FnBlocks.BRIDGE.get()));

        StringBuilder fehlend = new StringBuilder();
        for (Case one : cases) {
            BlockPos cable = new BlockPos(1, 2, 1);
            BlockPos beside = cable.east();
            helper.setBlock(beside, one.block().defaultBlockState());
            placeCable(helper, cable, dev.devpanda.factorynetwork.block.CableColour.NONE);

            var state = helper.getBlockState(cable);
            if (!dev.devpanda.factorynetwork.block.CableBlock.connectionsOf(state)
                    .contains(Direction.EAST)) {
                fehlend.append(one.name()).append(", ");
            }
            helper.setBlock(cable, net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(beside, net.minecraft.world.level.block.Blocks.AIR);
        }
        helper.assertTrue(fehlend.isEmpty(),
                "kein Arm vom Kabel zu: " + fehlend);

        // The cross-check: none grows towards a chest. Without it connects()
        // could simply always say true and the run would stay green.
        BlockPos cable = new BlockPos(1, 2, 1);
        helper.setBlock(cable.east(), net.minecraft.world.level.block.Blocks.CHEST);
        placeCable(helper, cable, dev.devpanda.factorynetwork.block.CableColour.NONE);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.block.CableBlock.connectionsOf(
                        helper.getBlockState(cable)).contains(Direction.EAST),
                "das Kabel wächst einen Arm zu einer Kiste");
        helper.succeed();
    }

    /**
     * A mast on the cable belongs to the network — and is found there too.
     *
     * <p><b>Two questions that fell apart.</b> The graph collected the mast
     * as a consumer and drew power for it, but {@code contains()} did not
     * list it. Everything that goes through
     * {@link dev.devpanda.factorynetwork.network.ControllerRegistry#owning}
     * therefore did not find it: registering a device, opening the window
     * from afar, every display.
     *
     * <p>In the game that looked as if the mast hung on nothing at all.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aMastAtTheCableBelongsToTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // A mast directly on a cable of the network.
        BlockPos mast = controller.above();
        placeCable(helper, mast, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos onTop = mast.above();
        helper.setBlock(onTop, FnBlocks.MAST.get().defaultBlockState());
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().masts().contains(helper.absolutePos(onTop)),
                "der Graph hat den Mast nicht eingesammelt");
        helper.assertTrue(entity.graph().contains(helper.absolutePos(onTop)),
                "der Graph sammelt den Mast ein, zählt ihn aber nicht zum Netz");

        // And that is the question really asked in the game.
        // Registering by hand: in the game onLoad does that, but setBlock in
        // the test run only calls it on the next tick — and what is to be
        // checked is not the registration but what owning makes of it.
        dev.devpanda.factorynetwork.network.ControllerRegistry.add(
                helper.getLevel(), helper.absolutePos(controller));
        helper.assertTrue(
                dev.devpanda.factorynetwork.network.ControllerRegistry.owning(
                        helper.getLevel(), helper.absolutePos(onTop)).isPresent(),
                "kein Controller für den Mast — ein Gerät könnte sich nicht anmelden");

        // A mast without a cable, by contrast, belongs to none: otherwise the
        // check would be worthless because it always said true.
        BlockPos lonely = new BlockPos(5, 1, 5);
        helper.setBlock(lonely, FnBlocks.MAST.get().defaultBlockState());
        entity.rebuildNetwork();
        helper.assertTrue(!entity.graph().contains(helper.absolutePos(lonely)),
                "ein Mast ohne Kabel gilt als Teil des Netzes");
        helper.succeed();
    }

    /**
     * A connector may exist before the cable — and then conducts nothing.
     *
     * <p>The holder is a cable block without a strand. It looks like a
     * connector on a wall, belongs to no network and waits for a cable.
     * <b>Without the second half the first would be dangerous:</b> a holder
     * that conducted would join two networks with nothing in between.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aHolderCarriesNothing(GameTestHelper helper) {
        BlockPos world = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(world,
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.CABLE, false));

        var state = helper.getLevel().getBlockState(world);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.block.CableBlock.carries(state),
                "der Halter hält sich für ein Kabel");

        // A cable next to it does not reach for it.
        BlockPos next = world.east();
        helper.getLevel().setBlockAndUpdate(next, FnBlocks.CABLE.get().defaultBlockState());
        var neighbour = dev.devpanda.factorynetwork.block.CableBlock.withConnections(
                helper.getLevel().getBlockState(next), helper.getLevel(), next);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.block.CableBlock.connectionsOf(neighbour)
                        .contains(Direction.WEST),
                "ein Kabel wächst einen Arm zum Halter, in dem gar nichts liegt");

        // And the holder itself has none on any side — read from the world
        // and not freshly computed.
        //
        // <b>The difference is the point of this check.</b> withConnections
        // is only called by whoever computes themselves; vanilla's neighbour
        // round goes through updateShape. A test that calls the first
        // version checks the path the game does not take.
        var fromWorld = helper.getLevel().getBlockState(world);
        helper.assertTrue(
                dev.devpanda.factorynetwork.block.CableBlock.connectionsOf(fromWorld).isEmpty(),
                "der Halter hat Arme, obwohl kein Kabel in ihm liegt");

        // And this is what it is really about: a holder next to a cable must
        // still be able to take a connector on this face. If a connection bit
        // stood there, it would refuse it.
        if (helper.getLevel().getBlockEntity(world)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity holder) {
            helper.assertTrue(
                    dev.devpanda.factorynetwork.block.CableBlock.hasRoomForPart(
                            fromWorld, helper.getLevel(), world, Direction.EAST),
                    "der Halter nimmt neben einem Kabel keinen Anschluss mehr an");
            holder.addPart(Direction.EAST);
            helper.assertTrue(holder.partAt(Direction.EAST) != null,
                    "der Anschluss zum Kabel hin ging nicht hinein");
        }
        helper.succeed();
    }

    /**
     * A connector in the holder can be named before a cable is there.
     *
     * <p>That was the second half of the wish: place <b>and name</b> before
     * it is connected. The name is checked against the names in the network
     * — and a holder has none. If the check broke on that, exactly what the
     * holder exists for would fail.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aHolderPartTakesAName(GameTestHelper helper) {
        BlockPos world = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(world,
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.CABLE, false));
        if (!(helper.getLevel().getBlockEntity(world)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("kein Kabelbus im Halter");
            return;
        }
        var part = bus.addPart(Direction.NORTH);

        // Without a network there is no graph — exactly the case at the holder.
        var warning = dev.devpanda.factorynetwork.item.ConnectorNaming.check(
                "ofen_1", null);
        helper.assertTrue(warning.isFine(),
                "ein gültiger Name wird ohne Netz abgelehnt: " + warning.kind());

        part.setLabel("ofen_1");
        helper.assertTrue("ofen_1".equals(part.label()),
                "der Name ist nicht am Anschluss angekommen");

        // And an unfit name stays unfit, even without a network.
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.ConnectorNaming.check(
                        "1 ofen", null).isFine(),
                "ein unmöglicher Name geht ohne Netz durch");
        helper.succeed();
    }

    /**
     * If the storage is full, everything stays put — nothing falls on the ground.
     *
     * <p><b>The worst way to fail.</b> Until now a worker first took from the
     * chest and asked afterwards whether the network takes it. If it did not
     * fit and the chest had filled up in the meantime, it fell on the ground
     * — and an item on the ground vanishes after five minutes.
     *
     * <p>The case is rare and therefore dangerous: it strikes exactly when
     * nobody is watching — at night, with a full store, at a chest another
     * worker is filling up right now.
     *
     * <p>Now it asks first. Whoever cannot house anything takes nothing out.
     */
    /**
     * With a full store, crafting waits instead of filling the holding.
     *
     * <p><b>Otherwise the holding would be worse than the ground.</b> A job
     * that keeps producing with a full store fills it without end — every
     * stack a line in the log, all of it in the save file. The ground at
     * least had a self-limit: after five minutes it was gone.
     *
     * <p>The right answer is neither of the two but back-pressure: the job
     * waits, the ingredients stay put.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullStorageStopsCraftingInsteadOfPilingUp(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        entity.rebuildNetwork();

        // Eight kinds are the limit of a 1k cell, and the planks are one of
        // them: after extraction planks remain, the type slot stays occupied
        // — the chest would be the ninth kind and does not fit.
        entity.storage().insert(new ItemStack(Items.OAK_PLANKS, 64));
        for (Item kind : List.of(Items.COBBLESTONE, Items.DIRT, Items.STONE,
                Items.SAND, Items.GRAVEL, Items.GLASS, Items.BRICK)) {
            entity.storage().insert(new ItemStack(kind, 8));
        }

        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    // The actual assertion: nothing was started.
                    helper.assertValueEqual(entity.storage().count(Items.OAK_PLANKS), 64L,
                            "die Zutaten sind weg, obwohl nichts entstehen konnte");
                    helper.assertTrue(entity.held().isEmpty(),
                            "die Fertigung hat die Verwahrung gefüllt");
                    helper.assertValueEqual(entity.storage().count(Items.CHEST), 0L,
                            "eine Kiste ist entstanden, obwohl kein Platz war");
                    var jobs = entity.craftingJobs();
                    helper.assertValueEqual(jobs.size(), 1, "ein Auftrag");
                    helper.assertValueEqual(jobs.get(0).status().name(), "WAITING",
                            "der Auftrag sagt nicht, dass er wartet");
                })
                .thenSucceed();
    }

    /**
     * What is held back survives saving.
     *
     * <p><b>Otherwise the loss would come back through the back door:</b> a
     * chunk that unloads would take along what the controller is holding
     * right now — and that would be exactly the same silent loss the holding
     * is built against, only later.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void heldBackSurvivesSaving(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.holdBack(new ItemStack(Items.DIAMOND, 17));

        var registries = helper.getLevel().registryAccess();
        var tag = entity.saveWithFullMetadata(registries);
        BlockPos absolut = helper.absolutePos(controller);
        var geladen = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                absolut, helper.getLevel().getBlockState(absolut), tag, registries);

        if (!(geladen instanceof ControllerBlockEntity reloaded)) {
            helper.fail("der gespeicherte Controller kam nicht zurück", controller);
            return;
        }
        long held = reloaded.held().stream()
                .filter(stack -> stack.is(Items.DIAMOND))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertValueEqual(held, 17L,
                "das Verwahrte hat das Speichern nicht überstanden");
        helper.succeed();
    }

    /**
     * What finds no room anywhere is held back — not thrown.
     *
     * <p><b>The last path into the world.</b> Since a worker asks before the
     * grab, nothing should ever land here. Should. A machine that answers
     * {@code simulate} differently from the grab exists — and then this path
     * decides whether the goods come back or are gone after five minutes.
     *
     * <p>Both are checked: that nothing lands in the world, and that what is
     * held back goes into the store by itself as soon as there is room.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void heldBackGoesInLaterInsteadOfOntoTheGround(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // A full drive: what comes now finds no room anywhere.
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        entity.rebuildNetwork();
        entity.storage().insert(new ItemStack(Items.COBBLESTONE, 64));
        while (entity.storage().insert(new ItemStack(Items.DIRT, 64)) == 0) {
            // Keep going as long as everything goes in.
        }

        entity.holdBack(new ItemStack(Items.DIAMOND, 64));

        helper.runAfterDelay(10, () -> {
            // Nothing in the world, everything still held back.
            helper.assertItemEntityNotPresent(Items.DIAMOND);
            long held = entity.held().stream()
                    .filter(stack -> stack.is(Items.DIAMOND))
                    .mapToLong(ItemStack::getCount).sum();
            helper.assertValueEqual(held, 64L,
                    "die Diamanten liegen nicht mehr in Verwahrung");

            // Now room is added — a second drive with an empty cell.
            driveWithCell(helper, controller.below(),
                    dev.devpanda.factorynetwork.storage.CellTier.K64);
            entity.rebuildNetwork();

            helper.runAfterDelay(10, () -> {
                helper.assertItemEntityNotPresent(Items.DIAMOND);
                helper.assertValueEqual(entity.storage().count(Items.DIAMOND), 64L,
                        "das Verwahrte ging nicht ins Lager, als Platz da war");
                helper.assertTrue(entity.held().isEmpty(),
                        "die Verwahrung wurde nicht geräumt");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullStorageDropsNothing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // A drive with the smallest cell, driven brimful.
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K1);
        entity.rebuildNetwork();
        entity.storage().insert(new ItemStack(Items.COBBLESTONE, 64));
        while (entity.storage().insert(new ItemStack(Items.DIRT, 64)) == 0) {
            // Keep going as long as everything goes in. If a remainder stays, stop.
        }
        // And full means full. This line is no decoration: without it the
        // test run measures the wrong thing as soon as the cell has room after all.
        helper.assertValueEqual(entity.storage().insert(new ItemStack(Items.DIAMOND, 1)),
                1L, "die Zelle ist nicht voll — der Prüflauf misst das Falsche");

        // <b>A furnace as the source, not a chest.</b> That is the whole test
        // run: from the side a furnace shows only the fuel slot, and it does
        // not accept diamonds there. The way back that rescues a chest is
        // thus not available — what remains is the question whether it asked
        // beforehand.
        BlockPos source = controller.east().north().north();
        helper.setBlock(source, Blocks.FURNACE);
        if (!(helper.getBlockEntity(source)
                instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace)) {
            helper.fail("kein Ofen an der Quelle", source);
            return;
        }
        furnace.setItem(1, new ItemStack(Items.DIAMOND, 64));

        helper.assertTrue(entity.deploy("""
                worker haul {
                    from quarry_output
                    to storage
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(40, () -> {
            // <b>Nothing lies on the ground.</b> That is the actual
            // assertion: where the store is full, the goods stay put.
            helper.assertItemEntityNotPresent(Items.DIAMOND);

            if (!(helper.getBlockEntity(source)
                    instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity found)) {
                helper.fail("kein Ofen mehr da", source);
                return;
            }
            long inFurnace = 0;
            for (int slot = 0; slot < found.getContainerSize(); slot++) {
                if (found.getItem(slot).is(Items.DIAMOND)) {
                    inFurnace += found.getItem(slot).getCount();
                }
            }
            long inStorage = entity.storage().count(Items.DIAMOND);
            // The sum, not the ground: an item can also vanish without ever
            // appearing as an entity.
            helper.assertValueEqual(inFurnace + inStorage, 64L,
                    "von vierundsechzig Diamanten sind welche verschwunden");
            helper.succeed();
        });
    }

    /**
     * A cable can be placed onto a holder — via the in-game path.
     *
     * <p><b>The existing run calls {@code useItemOn} directly.</b> That
     * checks the rule but not the path to it: a player clicks on a block,
     * and only afterwards does Minecraft decide whether the block or the
     * item is asked. That is exactly where it went wrong in the game.
     *
     * <p>Here the whole path runs: {@code useItemOn} on the item, with a hit
     * on the connector's plate — the spot you hit when aiming at it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aCableGoesOntoAHolderTheWayAPlayerClicks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos world = helper.absolutePos(pos);
        helper.getLevel().setBlockAndUpdate(world,
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.CABLE, false));
        if (!(helper.getLevel().getBlockEntity(world)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("kein Kabelbus im Halter");
            return;
        }
        bus.addPart(Direction.NORTH);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack cable = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.CABLES
                        .get(dev.devpanda.factorynetwork.block.CableColour.RED).get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cable);

        // The hit sits on the north plate — where the connector is and where
        // you aim.
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(world).add(0, 0, -0.5),
                Direction.NORTH, world, false);

        // And via the item, not via the block: that is the path Minecraft
        // takes when somebody clicks with something in hand.
        cable.useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                cable, hit));

        var after = helper.getLevel().getBlockState(world);
        helper.assertTrue(dev.devpanda.factorynetwork.block.CableBlock.carries(after),
                "der Klick auf die Anschlussplatte hat kein Kabel eingelegt");
        helper.assertTrue(
                after.getValue(dev.devpanda.factorynetwork.block.CableBlock.COLOUR)
                        == dev.devpanda.factorynetwork.block.CableColour.RED,
                "das Kabel hat seine Farbe nicht mitgebracht");
        helper.assertTrue(bus.partAt(Direction.NORTH) != null,
                "der Anschluss ist beim Einlegen verschwunden");

        // And no second block has appeared next to it: that is exactly what
        // the holder is meant to prevent.
        helper.assertBlockNotPresent(FnBlocks.CABLE.get(), pos.north());
        helper.succeed();
    }

    /**
     * A cable onto a holder turns it into a line.
     *
     * <p>That is the point of the holder: the connector stays seated where it
     * sits. If the cable instead placed a second block next to it, you would
     * have to take the connector off and place it anew — and then you could
     * just as well have placed it later.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aCableTurnsAHolderIntoALine(GameTestHelper helper) {
        BlockPos world = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(world,
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.CABLE, false));
        if (!(helper.getLevel().getBlockEntity(world)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("kein Kabelbus im Halter");
            return;
        }
        bus.addPart(Direction.NORTH);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack cable = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.CABLES
                        .get(dev.devpanda.factorynetwork.block.CableColour.RED).get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cable);

        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(world), Direction.UP, world, false);
        helper.getLevel().getBlockState(world).useItemOn(
                cable, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);

        var after = helper.getLevel().getBlockState(world);
        helper.assertTrue(dev.devpanda.factorynetwork.block.CableBlock.carries(after),
                "aus dem Halter ist keine Leitung geworden");
        helper.assertTrue(
                after.getValue(dev.devpanda.factorynetwork.block.CableBlock.COLOUR)
                        == dev.devpanda.factorynetwork.block.CableColour.RED,
                "das Kabel hat seine Farbe nicht mitgebracht");
        helper.assertTrue(bus.partAt(Direction.NORTH) != null,
                "der Anschluss ist beim Einlegen des Kabels verschwunden");
        helper.succeed();
    }

    /**
     * A holder without connectors vanishes by itself.
     *
     * <p>Otherwise a block with nothing in it would remain: invisible,
     * because it draws neither core nor arms, and unclickable, because its
     * hit surface consists of the plates that no longer exist.
     *
     * <p>AE2 does the same in {@code CableBusContainer.cleanup()}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void anEmptyHolderRemovesItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos world = helper.absolutePos(pos);
        helper.getLevel().setBlockAndUpdate(world,
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.CABLE, false));
        if (!(helper.getLevel().getBlockEntity(world)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("kein Kabelbus im Halter");
            return;
        }
        bus.addPart(Direction.NORTH);
        bus.addPart(Direction.SOUTH);

        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(world).add(0, 0, -0.5),
                Direction.NORTH, world, false);
        dev.devpanda.factorynetwork.item.Wrenches.takePart(helper.getLevel(), world, hit);
        helper.assertBlockPresent(FnBlocks.CABLE.get(), pos);

        // The second and last: now the block holds nothing any more.
        var second = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(world).add(0, 0, 0.5),
                Direction.SOUTH, world, false);
        dev.devpanda.factorynetwork.item.Wrenches.takePart(helper.getLevel(), world, second);
        helper.assertBlockNotPresent(FnBlocks.CABLE.get(), pos);

        // A cable, by contrast, stays even when its last connector comes off
        // — it is a line, and that holds by itself.
        BlockPos line = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.getLevel().setBlockAndUpdate(line, FnBlocks.CABLE.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(line)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity real) {
            real.addPart(Direction.NORTH);
            var onLine = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(line).add(0, 0, -0.5),
                    Direction.NORTH, line, false);
            dev.devpanda.factorynetwork.item.Wrenches.takePart(helper.getLevel(), line, onLine);
        }
        helper.assertBlockPresent(FnBlocks.CABLE.get(), new BlockPos(2, 2, 1));
        helper.succeed();
    }

    /**
     * From afar the terminal lacks the code tab, the laptop does not.
     *
     * <p>That is the whole separation. If it fell away, the laptop would be a
     * more expensive terminal and the reason to build it gone.
     *
     * <p><b>At the block it does not apply:</b> whoever stands in front of
     * the terminal gets at everything. Remote access takes something away,
     * it adds nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void onlyTheLaptopCarriesCode(GameTestHelper helper) {
        var terminal = dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL;
        var laptop = dev.devpanda.factorynetwork.upgrade.RemoteDevice.LAPTOP;
        var inventory = helper.makeMockPlayer(
                net.minecraft.world.level.GameType.SURVIVAL).getInventory();
        BlockPos anywhere = helper.absolutePos(new BlockPos(1, 2, 1));

        var remote = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                1, inventory, anywhere, helper.getLevel().dimension(),
                terminal, 0);
        helper.assertTrue(
                !remote.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE),
                "das Wireless Terminal zeigt den Code-Reiter");
        helper.assertTrue(
                remote.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.STORAGE),
                "das Wireless Terminal zeigt den Speicher nicht");
        helper.assertTrue(
                remote.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.LOG),
                "das Protokoll fehlt — es ist Diagnose und gehört dazu");

        var portable = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                2, inventory, anywhere, helper.getLevel().dimension(),
                laptop, 0);
        for (var tab : dev.devpanda.factorynetwork.terminal.TerminalTab.values()) {
            helper.assertTrue(portable.allows(tab), tab + " fehlt am Laptop");
        }

        // And at the block everything is open.
        var fixed = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                3, inventory, anywhere);
        for (var tab : dev.devpanda.factorynetwork.terminal.TerminalTab.values()) {
            helper.assertTrue(fixed.allows(tab), tab + " fehlt am Terminal-Block");
        }
        helper.succeed();
    }

    /**
     * The window closes when the device is no longer where it was.
     *
     * <p>Four ways to lose it, and each has to count: put it away, swap it
     * for a device on another mast, break down the mast, walk out of range.
     * <b>The rule is checked directly</b> and not the closing of a window —
     * otherwise the run would check the ticker.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aRemoteWindowClosesWhenItShould(GameTestHelper helper) {
        BlockPos mast = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(mast, FnBlocks.MAST.get().defaultBlockState());

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(mast.getX() + 0.5, mast.getY() + 1.0, mast.getZ() + 0.5);

        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        var where = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), mast);
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, where);
        player.getInventory().setItem(0, device);

        helper.assertTrue(
                dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "direkt am Mast ist der Zugriff verwehrt");

        // Put away.
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "das Fenster bleibt offen, obwohl das Gerät weg ist");

        // A different device on a different mast in the same slot: the window
        // hangs on the old network and still has to close.
        ItemStack other = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.LAPTOP.get());
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(other,
                net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), mast.above(5)));
        player.getInventory().setItem(0, other);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "ein getauschtes Gerät hält das Fenster am alten Netz offen");

        // Back to the right device, but too far away.
        player.getInventory().setItem(0, device);
        int reach = dev.devpanda.factorynetwork.upgrade.Range.reach(
                dev.devpanda.factorynetwork.upgrade.Loadout.of(java.util.List.of()),
                dev.devpanda.factorynetwork.upgrade.Loadout.of(java.util.List.of()));
        player.setPos(mast.getX() + reach + 10.0, mast.getY(), mast.getZ() + 0.5);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "die Reichweite hält nicht — " + reach + " Blöcke sollten sie sein");

        // And close again: it is the distance and not something having
        // broken.
        player.setPos(mast.getX() + 0.5, mast.getY() + 1.0, mast.getZ() + 0.5);
        helper.assertTrue(
                dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "aus der Nähe geht es auch nicht mehr");

        // The mast gone: the same.
        helper.getLevel().removeBlock(mast, false);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.terminal.RemoteAccess.allowed(player, 0, where),
                "ohne Mast bleibt der Zugriff offen");
        helper.succeed();
    }

    /**
     * A device binds to a mast — and unbinds on the second click.
     *
     * <p>The way back is the point. Without it a device that once hangs on
     * the wrong mast would be bound to it forever, and the only way out
     * would be: throw it away and build a new one.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aDeviceBindsToAMastAndBackAgain(GameTestHelper helper) {
        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.mastOf(device) == null,
                "ein frisches Gerät hängt schon an einem Mast");

        net.minecraft.core.GlobalPos mast = net.minecraft.core.GlobalPos.of(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, mast),
                "die Anmeldung ist nicht zustandegekommen");
        helper.assertTrue(
                mast.equals(dev.devpanda.factorynetwork.item.RemoteDeviceItem.mastOf(device)),
                "der Mast steht nicht am Gerät");
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.networkOf(device) != null,
                "ohne Namen zeigt der Tooltip nichts an");

        // The same mast once more: that is the way back.
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, mast),
                "der zweite Klick hat nicht abgemeldet");
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.mastOf(device) == null,
                "das Gerät hängt nach dem Abmelden noch am Mast");

        // A different mast, by contrast, rebinds instead of unbinding.
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, mast),
                "die zweite Anmeldung ging nicht");
        net.minecraft.core.GlobalPos other = net.minecraft.core.GlobalPos.of(
                mast.dimension(), mast.pos().above(3));
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.couple(device, other),
                "ein anderer Mast hat abgemeldet statt umgemeldet");
        helper.assertTrue(
                other.equals(dev.devpanda.factorynetwork.item.RemoteDeviceItem.mastOf(device)),
                "die Ummeldung ist nicht angekommen");
        helper.succeed();
    }

    /**
     * Both devices have a battery that foreign mods can fill.
     *
     * <p><b>This is the spot where Powah and Flux Networks dock on.</b> They
     * ask for {@code IEnergyStorage} on the ItemStack — if the registration
     * in {@code FnCapabilities} does not happen, the charge level is a number
     * nobody can fill, and in the game you only notice when you stand there
     * with a charged device that stays empty.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void bothDevicesTakeChargeFromOutside(GameTestHelper helper) {
        for (var held : java.util.List.of(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL,
                dev.devpanda.factorynetwork.registry.FnItems.LAPTOP)) {
            ItemStack device = new ItemStack(held.get());
            var battery = device.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
            helper.assertTrue(battery != null,
                    held.getId() + " hat keinen Akku, an dem eine Fremdmod andocken kann");
            helper.assertTrue(battery.getMaxEnergyStored() > 0,
                    held.getId() + ": in den Akku passt nichts hinein");
            helper.assertTrue(battery.canReceive(),
                    held.getId() + ": der Akku nimmt nichts an");

            int taken = battery.receiveEnergy(5_000, false);
            helper.assertTrue(taken > 0, held.getId() + ": nichts angenommen");
            helper.assertTrue(
                    device.getCapability(net.neoforged.neoforge.capabilities.Capabilities
                            .EnergyStorage.ITEM).getEnergyStored() == taken,
                    held.getId() + ": der Ladestand hat den Stapel nicht erreicht");
        }
        helper.succeed();
    }

    /**
     * The laptop holds more upgrades than the terminal — and what is inside
     * survives the trip through the stack.
     *
     * <p>The upgrade slots live in a data component. If saving went wrong,
     * the cards would be gone at the first look into the inventory — and
     * with them the range you built them for.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void upgradesSurviveTheItemStack(GameTestHelper helper) {
        ItemStack device = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.LAPTOP.get());
        var slots = dev.devpanda.factorynetwork.item.RemoteDeviceItem.slotsOf(device);
        helper.assertTrue(slots.size() == 4,
                "der Laptop hat " + slots.size() + " Plätze statt vier");

        slots.set(0, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.RANGE_CARD.get(), 2));
        dev.devpanda.factorynetwork.item.RemoteDeviceItem.saveSlots(device, slots);

        var read = dev.devpanda.factorynetwork.item.RemoteDeviceItem.slotsOf(device);
        helper.assertTrue(read.get(0).getCount() == 2,
                "die Karten sind auf dem Weg durch den Stapel verlorengegangen");
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.loadoutOf(device)
                        .count(dev.devpanda.factorynetwork.upgrade.Card.RANGE) == 2,
                "die Bestückung zählt die Karten nicht");

        // And the terminal has less room — that is the second reason for the
        // laptop, besides the code.
        ItemStack small = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WIRELESS_TERMINAL.get());
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.RemoteDeviceItem.slotsOf(small).size() == 2,
                "das Terminal hat nicht zwei Plätze");
        helper.succeed();
    }

    /**
     * The wrench takes a connector off and leaves the cable standing.
     *
     * <p>That is the whole promise of this tool. Without it you would have to
     * break the cable block — and with it the strand running through it, and
     * the other five connectors along with it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aWrenchTakesOnlyThePart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        placeCable(helper, pos, dev.devpanda.factorynetwork.block.CableColour.NONE);
        if (!(helper.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("kein Kabelbus");
            return;
        }
        bus.addPart(Direction.NORTH);
        bus.addPart(Direction.SOUTH);
        helper.assertTrue(bus.partAt(Direction.NORTH) != null, "der Anschluss sitzt nicht");

        // The same path the event handler takes — with a hit on the north
        // face.
        BlockPos world = helper.absolutePos(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(world)
                        .add(0, 0, -0.5), Direction.NORTH, world, false);
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.Wrenches.takePart(
                        helper.getLevel(), world, hit),
                "der Schraubenschlüssel hat nichts abgenommen");

        helper.assertTrue(bus.partAt(Direction.NORTH) == null,
                "der Anschluss ist noch da");
        helper.assertTrue(bus.partAt(Direction.SOUTH) != null,
                "der zweite Anschluss ist mit abgegangen");
        helper.assertBlockPresent(FnBlocks.CABLE.get(), pos);

        // And it comes back: the wrench disassembles, it does not destroy.
        helper.succeedWhen(() -> helper.assertItemEntityPresent(
                dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get(),
                pos, 2.0));
    }

    /**
     * And every foreign wrench does it too.
     *
     * <p>The rule is in a convention tag and not in a class. If that broke
     * away, only our own would still work — and nobody would notice until
     * somebody stands in front of it with a tool from Mekanism.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void ourWrenchIsInTheConventionTag(GameTestHelper helper) {
        ItemStack ours = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.WRENCH.get());
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.Wrenches.is(ours),
                "unser Schraubenschlüssel steht nicht in c:tools/wrench");
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.Wrenches.is(
                        new ItemStack(net.minecraft.world.item.Items.STICK)),
                "ein Stock gilt als Schraubenschlüssel");

        // And without sneaking it does not disassemble. Without this check
        // the condition could fall away, and a right-click on a connector
        // would take it off instead of opening its window.
        var player = helper.makeMockPlayer(
                net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        helper.assertTrue(
                !dev.devpanda.factorynetwork.item.Wrenches.disassembling(player, ours),
                "der Schlüssel zerlegt auch ohne Schleichen");
        player.setShiftKeyDown(true);
        helper.assertTrue(
                dev.devpanda.factorynetwork.item.Wrenches.disassembling(player, ours),
                "der Schlüssel zerlegt nicht, obwohl geschlichen wird");
        helper.succeed();
    }

    /**
     * An equipped mast gives its cards back.
     *
     * <p>The loot table does not see them — it knows only the block. Without
     * {@code onRemove} four cards would be gone, and one of them can be the
     * most expensive in the game.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void breakingAMastReturnsItsCards(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, FnBlocks.MAST.get());
        if (!(helper.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.MastBlockEntity mast)) {
            helper.fail("keine BlockEntity am Sendemast");
            return;
        }
        mast.setItem(0, new net.minecraft.world.item.ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.RANGE_CARD.get(), 2));
        mast.setItem(1, new net.minecraft.world.item.ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.INFINITY_CARD.get()));

        // And what is inside it also computes: two cards plus the infinity
        // card means unlimited.
        helper.assertTrue(mast.loadout().unlimited(
                        dev.devpanda.factorynetwork.upgrade.Stat.RANGE),
                "der Mast merkt die Grenzenlos-Karte nicht");
        helper.assertTrue(mast.loadout().count(
                        dev.devpanda.factorynetwork.upgrade.Card.RANGE) == 2,
                "der Mast zählt den Stapel nicht Stück für Stück");

        helper.destroyBlock(pos);
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(
                    dev.devpanda.factorynetwork.registry.FnItems.RANGE_CARD.get(),
                    pos, 2.0);
            helper.assertItemEntityPresent(
                    dev.devpanda.factorynetwork.registry.FnItems.INFINITY_CARD.get(),
                    pos, 2.0);
        });
    }

    /**
     * If one half falls, the other goes with it.
     *
     * <p>Otherwise after an explosion a floating sheet-metal hood would
     * remain, which can do nothing and is no longer recognisable as a rack.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void breakingOneHalfTakesTheOther(GameTestHelper helper) {
        BlockPos unten = new BlockPos(1, 2, 1);
        placeRack(helper, unten);
        helper.assertBlockPresent(FnBlocks.RACK.get(), unten.above());

        // The upper one gone — the lower one goes with it.
        helper.setBlock(unten.above(), Blocks.AIR);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten.above());

        // And the other way round.
        placeRack(helper, unten);
        helper.setBlock(unten, Blocks.AIR);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten.above());
        helper.succeed();
    }

    /**
     * Whoever strikes at the top gets the rack and its contents.
     *
     * <p>The item hangs on the lower half because the loot table only yields
     * something there — otherwise an explosion would make two racks out of
     * one. But a player who breaks the upper half means the rack and should
     * not stand there empty-handed.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void breakingTheUpperHalfStillDropsTheRack(GameTestHelper helper) {
        BlockPos unten = new BlockPos(1, 2, 1);
        placeRack(helper, unten);
        fillBay(helper, unten, 0, 8, 8, 64);

        BlockPos oben = unten.above();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        FnBlocks.RACK.get().playerWillDestroy(helper.getLevel(), helper.absolutePos(oben),
                helper.getBlockState(oben), player);

        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), oben);

        // A rack and three parts lie on the ground.
        java.util.List<net.minecraft.world.entity.item.ItemEntity> liegend =
                helper.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                                helper.absolutePos(unten.offset(-2, -2, -2)),
                                helper.absolutePos(unten.offset(2, 3, 2))));
        int schraenke = 0;
        int bauteile = 0;
        int server = 0;
        for (var eintrag : liegend) {
            ItemStack stack = eintrag.getItem();
            if (stack.getItem()
                    == dev.devpanda.factorynetwork.registry.FnItems.RACK.get()) {
                schraenke += stack.getCount();
            } else if (stack.getItem()
                    instanceof dev.devpanda.factorynetwork.item.ServerPartItem) {
                bauteile += stack.getCount();
            } else if (dev.devpanda.factorynetwork.item.ServerChassis.is(stack)) {
                server++;
                helper.assertTrue(!dev.devpanda.factorynetwork.item.ServerChassis
                                .isEmpty(stack),
                        "das Gehäuse hat seine Hardware mitgenommen");
            }
        }
        helper.assertValueEqual(schraenke, 1, "genau ein Schrank, nicht zwei und nicht null");
        helper.assertValueEqual(server, 1, "ein fertiger Server");
        helper.assertValueEqual(bauteile, 0, "und keine losen Teile daneben");
        helper.succeed();
    }

    /**
     * Without a server no flow starts — but it is not lost either.
     *
     * <p>It queues up and runs as soon as a rack stands again. That is the
     * same answer as with overload: delay is recoverable, loss is not.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutAServerAFlowWaitsInstead(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn kurz() {
                    sleep 1t
                }"""), "Programm nicht übernommen");

        // Rack gone, then kick off a flow.
        helper.setBlock(controller.west(), Blocks.AIR);
        entity.rebuildNetwork();
        var flow = entity.startFlow("kurz", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "QUEUED",
                "ohne Server darf nichts anfangen, aber auch nichts wegfallen");

        // Rack back, and it runs.
        rackWithServer(helper, controller.west());
        entity.rebuildNetwork();
        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(entity.flowEngine().queued(), 0,
                "mit Server muss er nachrücken");
        helper.succeed();
    }

    // ---- Coloured cables --------------------------------------------------

    /** Places a cable as the item would — colour included. */
    private static void placeCable(GameTestHelper helper, BlockPos at,
            dev.devpanda.factorynetwork.block.CableColour colour) {
        helper.setBlock(at, dev.devpanda.factorynetwork.block.CableBlock.withConnections(
                FnBlocks.CABLE.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.CableBlock.COLOUR, colour),
                helper.getLevel(), helper.absolutePos(at)));
    }

    private static boolean connected(GameTestHelper helper, BlockPos from, BlockPos to) {
        net.minecraft.core.Direction side = towards(helper, from, to);
        return helper.getBlockState(from).getValue(
                dev.devpanda.factorynetwork.block.CableBlock.connection(side));
    }

    /**
     * Two cables of different colours do not reach for each other.
     *
     * <p>The colour used to come from the world instead of from the state.
     * On placing, though, the own spot still holds air, and air counts as
     * neutral — a red cable therefore computed its connections as a neutral
     * one and grew an arm to every neighbour, no matter which colour.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void cablesOfDifferentColoursDoNotReachForEachOther(GameTestHelper helper) {
        var rot = dev.devpanda.factorynetwork.block.CableColour.RED;
        var gruen = dev.devpanda.factorynetwork.block.CableColour.GREEN;
        var neutral = dev.devpanda.factorynetwork.block.CableColour.NONE;

        BlockPos links = new BlockPos(1, 2, 1);
        BlockPos rechts = links.east();
        placeCable(helper, links, rot);
        placeCable(helper, rechts, gruen);

        helper.assertTrue(!connected(helper, links, rechts),
                "das rote Kabel darf nicht nach dem grünen greifen");
        helper.assertTrue(!connected(helper, rechts, links),
                "und das grüne nicht nach dem roten");

        // Same colour does.
        BlockPos zweitesRot = links.west();
        placeCable(helper, zweitesRot, rot);
        helper.assertTrue(connected(helper, links, zweitesRot), "rot an rot");
        helper.assertTrue(connected(helper, zweitesRot, links), "und zurück");

        // Neutral connects with everything — that is the point of the default.
        BlockPos oben = links.above();
        placeCable(helper, oben, neutral);
        helper.assertTrue(connected(helper, oben, links), "neutral an rot");
        helper.assertTrue(connected(helper, links, oben), "und zurück");
        helper.succeed();
    }

    /**
     * A broken-down cable gives its colour back.
     *
     * <p>The colour is in the block state, the item is a separate one per
     * colour. The loot table was empty — nothing dropped at all.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void abrokenCableDropsItsColour(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        placeCable(helper, at, dev.devpanda.factorynetwork.block.CableColour.RED);

        var drops = net.minecraft.world.level.block.Block.getDrops(
                helper.getBlockState(at), helper.getLevel(), helper.absolutePos(at), null);
        helper.assertValueEqual(drops.size(), 1, "genau ein Gegenstand");
        helper.assertValueEqual(drops.get(0).getItem(),
                dev.devpanda.factorynetwork.registry.FnItems.CABLES
                        .get(dev.devpanda.factorynetwork.block.CableColour.RED).get(),
                "und zwar das rote Kabel");
        helper.succeed();
    }

    /**
     * Every colour has its own name.
     *
     * <p>A BlockItem otherwise takes its name from the block, and all
     * seventeen point at the same one — the creative tab said "Kabel"
     * seventeen times.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyCableColourHasItsOwnName(GameTestHelper helper) {
        java.util.Set<String> namen = new java.util.HashSet<>();
        for (var colour : dev.devpanda.factorynetwork.block.CableColour.values()) {
            namen.add(new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CABLES
                    .get(colour).get()).getDescriptionId());
            namen.add(new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.DENSE_CABLES
                    .get(colour).get()).getDescriptionId());
        }
        helper.assertValueEqual(namen.size(), 34, "vierunddreißig verschiedene Namen");
        helper.succeed();
    }

    /**
     * Every item of the mod is in the creative tab.
     *
     * <p>The dense cables were registered, had models, names and recipes —
     * and still appeared nowhere, because one line in the tab was missing.
     * Reachable only via {@code /give} means: not there.
     *
     * <p>This check is deliberately general. It does not catch this one case
     * but every future one of the same kind.
     *
     * <p><b>There is one exception, and it is named here explicitly:</b> the
     * dense cables. They have been retired since 30 Aug — no recipe, no place
     * in the tab —, but still registered, because NeoForge 21.1 cannot remap
     * a deleted id in an existing world. By name and not as a pattern: the
     * next retirement should stand out again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyItemIsInTheCreativeTab(GameTestHelper helper) {
        var tab = dev.devpanda.factorynetwork.registry.FnCreativeTabs.MAIN.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        java.util.Set<net.minecraft.world.item.Item> gezeigt = new java.util.HashSet<>();
        tab.getDisplayItems().forEach(stack -> gezeigt.add(stack.getItem()));

        java.util.List<String> fehlend = new java.util.ArrayList<>();
        for (var eintrag : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            if (!eintrag.getKey().location().getNamespace()
                    .equals(dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID)) {
                continue;
            }
            String path = eintrag.getKey().location().getPath();
            if (path.endsWith("dense_cable")) {
                continue;
            }
            if (!gezeigt.contains(eintrag.getValue())) {
                fehlend.add(path);
            }
        }
        helper.assertTrue(fehlend.isEmpty(), "Nicht im Kreativ-Reiter: " + fehlend);
        helper.succeed();
    }

    /**
     * Every item has a name of its own in both languages.
     *
     * <p>Seventeen cables were all called "Kabel". A name two items share is
     * no name — the tab then shows a row you cannot read.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyItemHasItsOwnName(GameTestHelper helper) {
        java.util.Map<String, String> nachSchluessel = new java.util.HashMap<>();
        java.util.List<String> doppelt = new java.util.ArrayList<>();
        for (var eintrag : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            if (!eintrag.getKey().location().getNamespace()
                    .equals(dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID)) {
                continue;
            }
            String pfad = eintrag.getKey().location().getPath();
            String schluessel = new ItemStack(eintrag.getValue()).getDescriptionId();
            String vorher = nachSchluessel.put(schluessel, pfad);
            if (vorher != null) {
                doppelt.add(vorher + " und " + pfad + " teilen " + schluessel);
            }
        }
        helper.assertTrue(doppelt.isEmpty(), "Gleicher Name: " + doppelt);
        helper.succeed();
    }

    // ---- Who costs a channel ----------------------------------------------




    /**
     * A wall of panels writes once, not six times.
     *
     * <p>Six panels with the same text one below the other are not a screen
     * but six notes. Writing is done by the panel at the bottom left, across
     * the whole surface.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void awallOfDisplaysWritesOnce(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        // Three wide, two high, all facing north — and only the first touches
        // the cable.
        BlockPos ecke = controller.east().north();
        java.util.List<BlockPos> tafeln = new java.util.ArrayList<>();
        for (int reihe = 0; reihe < 2; reihe++) {
            for (int spalte = 0; spalte < 3; spalte++) {
                BlockPos at = ecke.east(spalte).above(reihe);
                helper.setBlock(at, FnBlocks.DISPLAY.get().defaultBlockState()
                        .setValue(dev.devpanda.factorynetwork.block.DisplayBlock.FACING,
                                Direction.NORTH));
                tafeln.add(at);
            }
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().displays().size(), 6,
                "alle sechs Tafeln hängen am Netz");

        // Only one gets a name — the wall is still called that.
        var irgendeine = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                helper.getBlockEntity(tafeln.get(4));
        irgendeine.setDisplayName("wand");

        var wand = irgendeine.wall();
        helper.assertValueEqual(wand.columns(), 3, "drei Spalten");
        helper.assertValueEqual(wand.rows(), 2, "zwei Reihen");
        helper.assertValueEqual(wand.members().size(), 6, "sechs Tafeln");

        helper.assertTrue(entity.deploy("""
                display wand {
                    text "hallo"
                }"""), "das Programm wurde nicht übernommen");

        int schreibende = 0;
        for (BlockPos at : tafeln) {
            var tafel = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                    helper.getBlockEntity(at);
            tafel.serverTick();
            if (!tafel.lines().isEmpty()) {
                schreibende++;
                helper.assertTrue(wand.isAnchor(helper.absolutePos(at)),
                        "und zwar die unten links");
            }
        }
        helper.assertValueEqual(schreibende, 1, "genau eine Tafel schreibt");

        // And the frame falls away where a second panel adjoins: the middle
        // one of the bottom row has neighbours left, right and above.
        var mitte = helper.getBlockState(tafeln.get(1));
        helper.assertTrue(mitte.getValue(
                        dev.devpanda.factorynetwork.block.DisplayBlock.JOINED_UP),
                "oben schließt eine an");
        helper.assertTrue(!mitte.getValue(
                        dev.devpanda.factorynetwork.block.DisplayBlock.JOINED_DOWN),
                "unten nicht");
        helper.assertTrue(mitte.getValue(
                        dev.devpanda.factorynetwork.block.DisplayBlock.JOINED_LEFT)
                        && mitte.getValue(
                                dev.devpanda.factorynetwork.block.DisplayBlock.JOINED_RIGHT),
                "und zu beiden Seiten");
        helper.succeed();
    }

    /**
     * A named panel without a program piece is still in the tab.
     *
     * <p>Previously it listed only the declarations. A panel that you named
     * and that the program does not know was thus visible nowhere — it only
     * said so itself on its front, and that may hang three rooms away.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void anunknownDisplayShowsUpInTheList(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        BlockPos tafel = controller.east();
        helper.setBlock(tafel, FnBlocks.DISPLAY.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.DisplayBlock.FACING,
                        Direction.NORTH));
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Without a name: a line saying something nameless hangs there.
        helper.assertTrue(entity.displayPanels().stream()
                        .anyMatch(panel -> panel.lines().stream()
                                .anyMatch(zeile -> zeile.contains("ohne Namen"))),
                "die namenlose Tafel fehlt: " + entity.displayPanels());

        // Named, but without a program piece: with name and hint.
        var panel = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                helper.getBlockEntity(tafel);
        panel.setDisplayName("wand");
        helper.assertTrue(entity.displayPanels().stream()
                        .anyMatch(entry -> entry.name().equals("wand")),
                "die benannte Tafel fehlt: " + entity.displayPanels());

        // As soon as the program knows it, it appears as a display and no
        // longer as a hint.
        helper.assertTrue(entity.deploy("""
                display wand {
                    text "hallo"
                }"""), "das Programm wurde nicht übernommen");
        var eintrag = entity.displayPanels().stream()
                .filter(entry -> entry.name().equals("wand")).findFirst().orElseThrow();
        helper.assertTrue(eintrag.lines().stream()
                        .noneMatch(zeile -> zeile.contains("kennt kein display")),
                "der Hinweis muss weg sein: " + eintrag.lines());
        helper.succeed();
    }

    /**
     * Two colours do not grow together across a display.
     *
     * <p>It conducts with the colour it was reached with. If it were
     * colour-neutral, every wall would carry a hole in the separation that
     * you cannot see while building.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void adisplayDoesNotBridgeColours(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        // Red strand to the east, behind it a display, behind that blue.
        BlockPos rot = controller.east();
        helper.setBlock(rot, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.CableBlock.COLOUR,
                        CableColour.RED));
        BlockPos tafel = rot.east();
        helper.setBlock(tafel, FnBlocks.DISPLAY.get());
        BlockPos blau = tafel.east();
        helper.setBlock(blau, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.CableBlock.COLOUR,
                        CableColour.BLUE));
        BlockPos dahinter = blau.east();
        helper.setBlock(dahinter, FnBlocks.DRIVE.get());

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().displays().size(), 1, "die Tafel hängt am Netz");
        helper.assertValueEqual(entity.graph().drives().size(), 0,
                "hinter dem blauen Kabel endet der rote Strang");
        helper.succeed();
    }


    // ---- Power -------------------------------------------------------------

    /** Short for the network's power values. */
    private static dev.devpanda.factorynetwork.network.NetworkPower powerOf(
            ControllerBlockEntity entity) {
        return entity.power();
    }

    /**
     * Every device on the network costs power.
     *
     * <p>You pay for readiness, not for work: a worker that moves something
     * costs no more than one that waits. Whoever plans their plant wants a
     * number that stands still.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void everyDeviceOnTheNetworkCostsPower(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        connector(helper, cable.north(), Direction.NORTH);
        name(helper, cable.north(), "geraet");
        helper.setBlock(cable.below(), FnBlocks.DISPLAY.get());
        driveWithCell(helper, cable.south(), dev.devpanda.factorynetwork.storage.CellTier.K1);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        int erwartet = dev.devpanda.factorynetwork.network.Power.CONTROLLER
                + dev.devpanda.factorynetwork.network.Power.CONNECTOR
                + dev.devpanda.factorynetwork.network.Power.DISPLAY
                + dev.devpanda.factorynetwork.network.Power.DRIVE
                + dev.devpanda.factorynetwork.network.Power.PER_CELL;
        // Pinned to a number, otherwise the test only checks its own
        // arithmetic: if DISPLAY stood at zero it would stay green — although
        // it just claims that every device costs power.
        helper.assertValueEqual(erwartet, 8, "Vier für den Controller, je eins für den Rest");
        helper.assertValueEqual(entity.powerDraw(), erwartet, "Bedarf des Netzes in FE je Tick");
        helper.succeed();
    }

    /**
     * Without power the network stands still — and carries on where it was.
     *
     * <p>Nothing is aborted: a flow holds no items between two steps, so
     * freezing costs nothing. A power outage should be a pause and not a
     * loss.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutPowerTheNetworkStandsStill(GameTestHelper helper) {
        BlockPos controller = threeChestsSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                worker holen {
                    from quelle
                    to storage
                    rate 1 per 1t
                }"""), "Das Programm wurde nicht übernommen");

        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }
        long bewegt = entity.storage().count(Items.IRON_ORE);
        helper.assertTrue(bewegt > 0, "mit Strom muss der Worker arbeiten");

        powerOf(entity).empty();
        for (int i = 0; i < 20; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(powerOf(entity).state().name(), "OFF", "Zustand ohne Strom");
        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), bewegt,
                "ohne Strom darf sich nichts mehr bewegen");
        helper.succeed();
    }

    /**
     * When the power comes back, the network boots first.
     *
     * <p>Without this time a power outage would be a flicker nobody notices.
     * With it you notice immediately that the supply is not enough.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void powerComingBackMeansBootingUp(GameTestHelper helper) {
        BlockPos controller = threeChestsSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        powerOf(entity).empty();
        entity.serverTick();
        helper.assertValueEqual(powerOf(entity).state().name(), "OFF", "erst einmal aus");

        powerOf(entity).fill(dev.devpanda.factorynetwork.network.Power.CAPACITY);
        entity.serverTick();
        helper.assertValueEqual(powerOf(entity).state().name(), "BOOTING",
                "mit Strom fährt es hoch");
        helper.assertTrue(!entity.isOnline(), "beim Hochfahren läuft noch nichts");

        for (int i = 0; i < dev.devpanda.factorynetwork.network.Power.BOOT_TICKS; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(powerOf(entity).state().name(), "RUNNING", "und dann läuft es");
        helper.assertTrue(entity.isOnline(), "jetzt ist das Netz da");
        helper.succeed();
    }

    /**
     * An undersized supply leaves the network off instead of letting it
     * blink.
     *
     * <p>Without this threshold it would start up, consume the reserve while
     * booting and go out again immediately — a blinking every half minute
     * that looks like a bug instead of like too little power.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anUndersizedSupplyLeavesTheNetworkOff(GameTestHelper helper) {
        BlockPos controller = threeChestsSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        powerOf(entity).empty();

        int schwelle = dev.devpanda.factorynetwork.network.Power
                .restartThreshold(entity.powerDraw());
        powerOf(entity).fill(schwelle / 2);
        for (int i = 0; i < 20; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(powerOf(entity).state().name(), "OFF",
                "die halbe Schwelle reicht nicht zum Hochfahren");

        powerOf(entity).fill(schwelle);
        entity.serverTick();
        helper.assertValueEqual(powerOf(entity).state().name(), "BOOTING",
                "über der Schwelle geht es los");
        helper.succeed();
    }

    /**
     * A flow survives the power cut and carries on afterwards.
     *
     * <p>That is the reason for freezing instead of aborting: whoever leaves
     * a plant without power overnight finds it in the morning where it
     * stopped. An event that arrives during the outage is not lost either —
     * it stays put and arrives as soon as the network runs again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aflowSurvivesAPowerCut(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                event Takt()

                fn wartet() {
                    await Takt
                }"""), "Programm nicht übernommen");

        var flow = entity.startFlow("wartet", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "AWAITING", "er wartet");

        // An event during the outage is not lost, it stays put — and arrives
        // as soon as the network runs again.
        powerOf(entity).empty();
        entity.serverTick();
        entity.fireEvent("Takt", java.util.List.of());
        for (int i = 0; i < 20; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(flow.status().name(), "AWAITING",
                "ohne Strom bleibt er liegen");

        powerOf(entity).fill(dev.devpanda.factorynetwork.network.Power.CAPACITY);
        for (int i = 0; i < dev.devpanda.factorynetwork.network.Power.BOOT_TICKS + 5; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(flow.status().name(), "DONE",
                "und läuft danach zu Ende");
        helper.succeed();
    }

    /**
     * The burner generates power and pushes it into the controller.
     *
     * <p>Without a source in the mod itself the production chain — ore,
     * plate, cores, cells, server parts — could not be completed without a
     * foreign mod. This check is the proof that it can.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theBurnerFeedsTheController(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos burnerPos = controller.east();
        helper.setBlock(burnerPos, FnBlocks.BURNER.get());
        var burner = (dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity)
                helper.getBlockEntity(burnerPos);
        burner.setItem(dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.SLOT_FUEL,
                new ItemStack(Items.COAL, 4));

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.power().empty();

        for (int i = 0; i < 10; i++) {
            dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(burnerPos),
                    helper.getBlockState(burnerPos), burner);
        }

        helper.assertTrue(burner.isBurning(), "sie muss brennen");
        helper.assertTrue(entity.power().stored() > 0,
                "und der Strom muss beim Controller ankommen");
        helper.succeed();
    }

    /**
     * Without a consumer it does not add fuel.
     *
     * <p>Otherwise a coal burns while nobody takes anything off — and you
     * only notice when the coal stack is gone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void thebBurnerDoesNotBurnIntoAFullBuffer(GameTestHelper helper) {
        BlockPos burnerPos = new BlockPos(1, 2, 1);
        helper.setBlock(burnerPos, FnBlocks.BURNER.get());
        var burner = (dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity)
                helper.getBlockEntity(burnerPos);
        burner.setItem(dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.SLOT_FUEL,
                new ItemStack(Items.COAL, 4));
        burner.energy().charge(
                dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.CAPACITY);

        for (int i = 0; i < 20; i++) {
            dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(burnerPos),
                    helper.getBlockState(burnerPos), burner);
        }

        helper.assertTrue(!burner.isBurning(), "bei vollem Vorrat bleibt sie kalt");
        helper.assertValueEqual(burner.getItem(dev.devpanda.factorynetwork.block.entity
                .BurnerBlockEntity.SLOT_FUEL).getCount(), 4, "und verheizt nichts");
        helper.succeed();
    }

    private FactoryNetworkGameTests() {
    }
    /**
     * The draft survives saving and does not stop the factory.
     *
     * <p>The actual purpose of the thing: whoever built on a worker for
     * twenty minutes and then crashes had lost everything before — Deploy
     * would have saved it, but Deploy only works with error-free code, and
     * in the middle of a change it never is.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theDraftSurvivesAndDoesNotRun(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("fn laeuft() { }"),
                "das erste Programm wurde nicht übernommen");

        // A draft that does not compile. Exactly the case that needs saving.
        var kaputt = dev.devpanda.factorynetwork.lang.Project.of("fn halb() { let a =");
        entity.acceptDraft(kaputt, null, null);

        helper.assertValueEqual(entity.draft().source("main.mf"), "fn halb() { let a =",
                "der Entwurf steht im Controller");
        helper.assertTrue(entity.program().functions().stream()
                        .anyMatch(fn -> fn.name().equals("laeuft")),
                "der laufende Stand darf sich davon nicht ändern");
        helper.assertTrue(entity.program().functions().stream()
                        .noneMatch(fn -> fn.name().equals("halb")),
                "ein Entwurf läuft nicht");

        // Through the save format and back.
        net.minecraft.nbt.CompoundTag tag = entity.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        ControllerBlockEntity wieder = new ControllerBlockEntity(
                helper.absolutePos(controller),
                FnBlocks.CONTROLLER.get().defaultBlockState());
        wieder.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertValueEqual(wieder.draft().source("main.mf"), "fn halb() { let a =",
                "und übersteht das Speichern");
        helper.assertTrue(wieder.program().functions().stream()
                        .anyMatch(fn -> fn.name().equals("laeuft")),
                "der laufende Stand auch");

        // Deploying brings the two back together.
        helper.assertTrue(entity.deploy("fn fertig() { }"), "das zweite Übernehmen");
        helper.assertValueEqual(entity.draft().source("main.mf"), "fn fertig() { }",
                "nach dem Übernehmen sind Entwurf und laufender Stand dasselbe");
        helper.succeed();
    }

    /**
     * The panel on the wall shows what is in the program.
     *
     * <p><b>Until now only the tab in the terminal was checked.</b> That the
     * panel itself gets to its lines — via its tick, its name and the
     * controller that knows it — hung on four places that were individually
     * correct and were never measured together.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theWallItselfShowsTheProgram(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        BlockPos tafel = controller.east();
        helper.setBlock(tafel, FnBlocks.DISPLAY.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.DisplayBlock.FACING,
                        Direction.NORTH));
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var panel = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                helper.getBlockEntity(tafel);
        panel.setDisplayName("test");
        helper.assertTrue(entity.deploy("""
                display test {
                    title "Mein Display"
                    text "testo"
                }"""), "das Programm wurde nicht übernommen");

        // The panel computes once per second. Via runAfterDelay and not via a
        // loop: within one tick the game time stands still.
        helper.runAfterDelay(25, () -> {
            var zeilen = panel.lines();
            helper.assertTrue(!zeilen.isEmpty(),
                    "die Tafel ist leer geblieben");
            helper.assertTrue(zeilen.stream().anyMatch(zeile -> zeile.contains("Mein Display")),
                    "die Überschrift fehlt: " + zeilen);
            helper.assertTrue(zeilen.stream().anyMatch(zeile -> zeile.contains("testo")),
                    "die Zeile fehlt: " + zeilen);
            helper.succeed();
        });
    }

    /**
     * And it says itself why it is empty.
     *
     * <p>A black surface leaves open whether the network is up, the name is
     * wrong or the program does not know the panel. Each of these cases has
     * its own sentence — on the panel, because that is where you look.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theWallExplainsWhyItIsEmpty(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        BlockPos tafel = controller.east();
        helper.setBlock(tafel, FnBlocks.DISPLAY.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.DisplayBlock.FACING,
                        Direction.NORTH));
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        var panel = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                helper.getBlockEntity(tafel);

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(panel.lines().stream()
                            .anyMatch(zeile -> zeile.contains("ohne Namen")),
                    "eine namenlose Tafel sagt es: " + panel.lines());

            panel.setDisplayName("test");
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(panel.lines().stream()
                                .anyMatch(zeile -> zeile.contains("kein display")),
                        "und eine ohne Programmstück auch: " + panel.lines());
                helper.succeed();
            });
        });
    }

    /**
     * Two players do not overwrite each other.
     *
     * <p>Both send the whole draft. Without a lock, whoever types last would
     * win — even over a file they did not even have open.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void twoPlayersDoNotOverwriteEachOther(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Ids instead of dummies: a real ServerPlayer in the test triggers
        // the join packets of foreign mods, and what is needed here is an id
        // and a name.
        var anna = java.util.UUID.randomUUID();
        var bert = java.util.UUID.randomUUID();

        var start = new dev.devpanda.factorynetwork.lang.Project(java.util.Map.of(
                "main.mf", "fn eins() { }",
                "worker.mf", "fn zwei() { }"));
        entity.acceptDraft(start, null, null);

        // Anna writes main.mf and thereby holds it.
        entity.acceptDraft(start.with("main.mf", "fn eins() { let a = 1 }"),
                anna, "Anna");
        helper.assertValueEqual(entity.draft().source("main.mf"), "fn eins() { let a = 1 }",
                "Annas Änderung");

        // Bert sends his whole draft — with an old version of main.mf and a
        // change of his own to worker.mf.
        entity.acceptDraft(start.with("worker.mf", "fn zwei() { let b = 2 }"),
                bert, "Bert");

        helper.assertValueEqual(entity.draft().source("main.mf"), "fn eins() { let a = 1 }",
                "Annas Datei bleibt stehen");
        helper.assertValueEqual(entity.draft().source("worker.mf"), "fn zwei() { let b = 2 }",
                "Berts eigene Änderung kommt an");

        // And Bert sees who holds main.mf.
        helper.assertValueEqual(entity.locksFor(bert).get("main.mf"), "Anna",
                "der Halter wird gemeldet");
        // And what stands here is not what Bert sent: his draft carried an
        // old version of main.mf. That is exactly why he gets the state back
        // although he was the sender.
        helper.assertTrue(!entity.draft().source("main.mf").equals("fn eins() { }"),
                "Berts alter Stand darf nicht gewonnen haben");
        helper.assertTrue(entity.locksFor(anna).get("main.mf") == null,
                "die eigene Sperre ist keine Nachricht");
        helper.succeed();
    }

    /**
     * A freshly created file arrives although it is empty.
     *
     * <p>The first attempt compared it with what the server has — and for an
     * unknown file that has the empty text. So every new file looked like an
     * unchanged one and fell through.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFreshEmptyFileStillArrives(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var anna = java.util.UUID.randomUUID();
        var start = dev.devpanda.factorynetwork.lang.Project.of("fn eins() { }");
        entity.acceptDraft(start, null, null);
        entity.acceptDraft(start.with("neu.mf", ""), anna, "Anna");

        helper.assertTrue(entity.draft().files().containsKey("neu.mf"),
                "die neue Datei fehlt: " + entity.draft().names());
        helper.succeed();
    }

    /**
     * A worker with a text condition really switches off.
     *
     * <p><b>The test that was missing for a long time.</b> {@code when} had a
     * small evaluator of its own that could only do numbers — everything
     * else counted as true. {@code when modus == "tag"} thus ran around the
     * clock, and the docs promised the opposite. Here now stands what is
     * meant to apply.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aTextConditionReallySwitchesTheWorkerOff(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 64);

        helper.assertTrue(entity.deploy("""
                global modus = "nacht"

                worker liefern {
                    from storage
                    to depot
                    filter item:iron_ingot
                    when modus == "tag"
                }

                fn tagschicht() {
                    modus = "tag"
                }"""), "das Programm wurde nicht übernommen");

        // First pass: the mode is "nacht", the worker must sleep.
        entity.serverTick();
        var zustand = entity.runtime().states().get("liefern");
        helper.assertTrue(zustand != null, "den Worker gibt es nicht");
        helper.assertTrue(zustand.status == WorkerRuntime.Status.WAITING_CONDITION,
                "erwartet WAITING_CONDITION, war " + zustand.status
                        + " (" + zustand.detail + ")");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 64L,
                "es darf nichts bewegt worden sein");

        // Switch over — now it must run.
        //
        // With a gap: a worker without rate runs every twenty ticks, and a
        // second tick in the same instant would be skipped. It would then
        // keep its old state, and the test would check nothing.
        entity.callFunction("tagschicht", List.of());
        helper.runAfterDelay(25, () -> {
            entity.serverTick();
            WorkerRuntime.WorkerState danach = entity.runtime().states().get("liefern");
            helper.assertTrue(danach.status != WorkerRuntime.Status.WAITING_CONDITION,
                    "nach dem Umschalten darf er nicht mehr auf die Bedingung warten, war "
                            + danach.status + " (" + danach.detail + ")");
            helper.succeed();
        });
    }

    /**
     * An amount before a loop variable really applies.
     *
     * <p><b>The most dangerous bug this language had.</b> {@code move 8 sorte}
     * moved everything instead of eight, because the amount was only set on
     * written selection expressions — a loop variable is a resolved value.
     * The program looked as if it did what it says, and emptied the store.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anAmountBeforeALoopVariableIsKept(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 64);

        helper.assertTrue(entity.deploy("""
                fn verteilen() {
                    for sorte in storage.items() {
                        move 8 sorte from storage to depot
                    }
                }"""), "das Programm wurde nicht übernommen");

        entity.callFunction("verteilen", List.of());

        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 56L,
                "acht sollten weg sein, nicht alle");
        helper.succeed();
    }

    /**
     * A display names a global value by name.
     *
     * <p>The text itself is checked and not whether anything is there at all
     * — a question mark is also something, and that is exactly what came out
     * before.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aDisplayShowsAGlobalValue(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                global modus = "nachtschicht"

                display halle {
                    row "Modus" modus
                }"""), "das Programm wurde nicht übernommen");

        var werte = new dev.devpanda.factorynetwork.runtime.DisplayValues(
                entity.graph(), entity.storage(), entity.runtime(), entity.globals());
        var zeilen = werte.evaluate((dev.devpanda.factorynetwork.lang.ast.Decl.Display)
                entity.program().declarations().stream()
                        .filter(d -> d instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Display)
                        .findFirst().orElseThrow());

        helper.assertValueEqual(zeilen.get(0).value(), "nachtschicht",
                "die Anzeige muss den Wert nennen, nicht ein Fragezeichen");
        helper.succeed();
    }

    /** The analyser says what hangs on a device — and how many slots. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theAnalyserNamesWhatADeviceCan(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos connector = controller.east().north();
        var verbunden = partAt(helper, connector);
        if (verbunden == null) {
            helper.fail("An dieser Stelle sitzt kein Anschluss", connector);
            return;
        }
        var profil = DeviceScan.of(verbunden);

        helper.assertValueEqual(profil.abilities(), "Gegenstände",
                "An einer Kiste hängen Gegenstände");
        helper.succeed();
    }

    /**
     * The slot numbers in the editor are the ones the program addresses.
     *
     * <p>A side shows its slots under numbers of its own; {@code slots(3)}
     * means the third slot of the machine. If the tooltip showed the side's
     * numbers, the information would point somewhere other than where it
     * takes effect.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theSnapshotNumbersMatchTheProgram(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(3, new ItemStack(Items.GOLD_ORE, 5));
        }

        var snapshot = dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket
                .of(entity, "quarry_output");
        helper.assertTrue(snapshot != null, "Kein Blick auf das Gerät");
        helper.assertTrue(snapshot.slots().size() > 3,
                "Die Kiste hat mehr als vier Fächer: " + snapshot.slots().size());
        helper.assertValueEqual(snapshot.slots().get(3).getCount(), 5,
                "Fach 3 im Editor ist Fach 3 im Programm");
        helper.succeed();
    }

    /**
     * <b>Put into a particular slot.</b>
     *
     * <p>The flip side of {@link #movingOnlyFromCertainSlots}: whoever writes
     * a slot number also puts there — on the undivided inventory, so without
     * the machine's side rules. That is exactly what the form is for (one
     * connector per machine, the fuel still goes into the fuel slot), and
     * exactly why the price is stated in sprache.md.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void movingIntoACertainSlot(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.COAL, 8);

        helper.assertTrue(entity.deploy("""
                fn einlegen() {
                    move 8 item:coal from storage to depot.slots(5)
                }"""), "Das Programm wurde nicht übernommen");

        BlockPos ziel = controller.east().south().south();
        entity.startFlow("einlegen", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    if (!(helper.getBlockEntity(ziel) instanceof ChestBlockEntity container)) {
                        helper.fail("Am Ziel hängt keine Kiste", ziel);
                        return;
                    }
                    helper.assertValueEqual(container.getItem(5).getCount(), 8,
                            "Die Kohle liegt in Fach 5 und nirgends sonst");
                    helper.assertTrue(container.getItem(0).isEmpty(),
                            "Fach 0 bleibt leer");
                })
                .thenSucceed();
    }

    /**
     * <b>Clear only the output.</b>
     *
     * <p>The case the slots exist for: a machine that keeps input and output
     * in the same inventory, and a move that leaves the input standing. A
     * second connector on another side is explicitly not the answer — one
     * connector per machine should suffice.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void movingOnlyFromCertainSlots(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            // Slot 0 is the "input", slot 3 the "output".
            container.setItem(0, new ItemStack(Items.IRON_ORE, 8));
            container.setItem(3, new ItemStack(Items.GOLD_ORE, 5));
        }

        helper.assertTrue(entity.deploy("""
                fn abraeumen() {
                    move 64 item:gold_ore from quarry_output.slots(3) to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("abraeumen", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.GOLD_ORE), 5L,
                            "Der Ausgang ist abgeräumt");
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 0L,
                            "Der Eingang bleibt stehen");
                })
                .thenSucceed();
    }

    /**
     * {@code slots(…)} reads individual slots specifically.
     *
     * <p>Over the whole inventory: one connector per machine should suffice,
     * and which slot is meant is decided by the code.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void slotsReadSingleSlots(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 7));
            container.setItem(3, new ItemStack(Items.GOLD_ORE, 5));
        }

        helper.assertTrue(entity.deploy("""
                global vorne = 0
                global hinten = 0
                global posten = 0

                fn zaehlt() {
                    vorne = quarry_output.slots(0..2).sum()
                    hinten = quarry_output.slots(3).sum()
                    posten = quarry_output.slots(0..26).count()
                }"""), "Das Programm wurde nicht übernommen");

        entity.startFlow("zaehlt", List.of());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.globals().get("vorne"),
                            new Value.Int(7), "In den Fächern null bis zwei liegen sieben");
                    helper.assertValueEqual(entity.globals().get("hinten"),
                            new Value.Int(5), "In Fach drei liegen fünf");
                    helper.assertValueEqual(entity.globals().get("posten"),
                            new Value.Int(2), "Leere Fächer fallen weg");
                })
                .thenSucceed();
    }

    /**
     * A panel may look into a machine.
     *
     * <p>The price is one look into a BlockEntity per panel and second — the
     * display reads the network stock at this rate anyway. A `?` on the
     * panel that nobody can explain would be the worse trade.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void adisplayCanLookIntoAMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 7));
        }

        helper.assertTrue(entity.deploy("""
                display halle {
                    row "Im Ger\u00e4t" quarry_output.count(item:iron_ore)
                    row "Im Netz" storage.count(item:iron_ore)
                }"""), "das Programm wurde nicht übernommen");

        var werte = new dev.devpanda.factorynetwork.runtime.DisplayValues(
                entity.graph(), entity.storage(), entity.runtime(), entity.globals(),
                helper.getLevel());
        var zeilen = werte.evaluate((dev.devpanda.factorynetwork.lang.ast.Decl.Display)
                entity.program().declarations().stream()
                        .filter(d -> d instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Display)
                        .findFirst().orElseThrow());

        helper.assertValueEqual(zeilen.get(0).value(), "7",
                "die Tafel muss in die Kiste sehen können");
        helper.assertValueEqual(zeilen.get(1).value(), "0",
                "und den leeren Netzspeicher davon unterscheiden");
        helper.succeed();
    }

    // ---- Distributing power -----------------------------------------------

    /**
     * The network supplies a machine.
     *
     * <p>The press is the only machine of the mod that accepts power — and
     * thus the most honest test track: what arrives here has really arrived.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thenetworkSuppliesAMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos maschine = controller.east().north().north();
        helper.setBlock(maschine, FnBlocks.PRESS.get());
        entity.rebuildNetwork();
        entity.power().fill(10_000);

        helper.assertTrue(entity.deploy("""
                worker versorgung {
                    from network
                    to quarry_output
                    filter power
                    rate 40 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    if (!(helper.getBlockEntity(maschine)
                            instanceof dev.devpanda.factorynetwork.block.entity.PressBlockEntity
                                    presse)) {
                        helper.fail("Da steht keine Presse", maschine);
                        return;
                    }
                    helper.assertTrue(presse.energy().getEnergyStored() > 0,
                            "In der Presse muss Strom angekommen sein");
                })
                .thenSucceed();
    }

    /** And the other way round: from a machine into the network. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void amachineFeedsTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // The creative source is the only power source of this mod that
        // hands anything out: a machine is not a battery, and the press
        // refuses extraction on purpose.
        BlockPos quelle = controller.east().north().north();
        helper.setBlock(quelle, FnBlocks.CREATIVE_SOURCE.get());
        entity.rebuildNetwork();
        // The buffer starts full; without room in it nothing flows in.
        // Taken rather than emptied: empty() switches the network off, and a
        // switched-off network lets no workers run.
        entity.power().take(entity.power().stored() - 2_000);

        helper.assertTrue(entity.deploy("""
                worker einspeisen {
                    from quarry_output
                    to network
                    filter power
                    rate 200 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        int vorher = entity.power().stored();
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> helper.assertTrue(entity.power().stored() > vorher,
                        "Der Vorrat muss gewachsen sein: " + vorher
                                + " → " + entity.power().stored()))
                .thenSucceed();
    }

    /**
     * Under scarcity the order of {@code priority} applies.
     *
     * <p>Two workers, one with precedence, and less power than both together
     * want. The one with the small number runs fully, the other goes empty —
     * not both by half.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void priorityDecidesWhoGetsPower(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos erste = controller.east().north().north();
        BlockPos zweite = controller.east().south().south();
        helper.setBlock(erste, FnBlocks.PRESS.get());
        helper.setBlock(zweite, FnBlocks.PRESS.get());
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker wichtig {
                    from network
                    to quarry_output
                    filter power
                    rate 100 per 1t
                    priority 1
                }

                worker unwichtig {
                    from network
                    to depot
                    filter power
                    rate 100 per 1t
                    priority 9
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenExecute(() -> {
                    // Scarce, but running: what remains is enough for a few
                    // throws and not for both workers.
                    entity.power().take(entity.power().stored() - 300);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    var bevorzugt = (dev.devpanda.factorynetwork.block.entity.PressBlockEntity)
                            helper.getBlockEntity(erste);
                    var nachrangig = (dev.devpanda.factorynetwork.block.entity.PressBlockEntity)
                            helper.getBlockEntity(zweite);
                    helper.assertTrue(bevorzugt.energy().getEnergyStored()
                                    > nachrangig.energy().getEnergyStored(),
                            "Der Worker mit Vorrang bekommt mehr: "
                                    + bevorzugt.energy().getEnergyStored() + " gegen "
                                    + nachrangig.energy().getEnergyStored());
                })
                .thenSucceed();
    }

    /**
     * The output is a number of its own in the network tab.
     *
     * <p>Without it a network that passes forty FE per tick through looks
     * like one that does nothing: the demand does not count it, and the
     * reserve stands still as long as enough comes in.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thesupplyIsItsOwnNumber(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos maschine = controller.east().north().north();
        helper.setBlock(maschine, FnBlocks.PRESS.get());
        entity.rebuildNetwork();
        entity.power().fill(Power.CAPACITY);

        helper.assertTrue(entity.deploy("""
                worker versorgung {
                    from network
                    to quarry_output
                    filter power
                    rate 40 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(entity.power().supplied() > 0,
                        "Die Abgabe muss zu sehen sein, steht aber auf "
                                + entity.power().supplied()))
                .thenSucceed();
    }

    /**
     * {@code list} is an enumeration, not a row.
     *
     * <p>The specification calls it "enumeration, such as stocks or jobs".
     * Until then a single row was drawn as with {@code row} — with a text in
     * it that nobody wanted to read.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void alistShowsOneRowPerEntry(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity tafel) {
            tafel.setDisplayName("lager");
        } else {
            helper.fail("Am Display hängt keine BlockEntity", display);
            return;
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 64);
        entity.storage().insert(Items.GOLD_INGOT, 32);
        helper.assertTrue(entity.deploy("""
                display lager {
                    list "Bestand" storage.items()
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (!(helper.getBlockEntity(display) instanceof DisplayBlockEntity shown)) {
                helper.fail("Display verschwunden", display);
                return;
            }
            var lines = shown.lines();
            // A heading and two entries — not one row for everything.
            helper.assertValueEqual(lines.size(), 3, "Zeilen auf dem Display: " + lines);
            helper.assertTrue(lines.stream().anyMatch(line -> line.contains("64")),
                    "Die Menge des Eisens fehlt: " + lines);
            helper.assertTrue(lines.stream().anyMatch(line -> line.contains("32")),
                    "Die Menge des Goldes fehlt: " + lines);
            helper.succeed();
        });
    }

    /** An empty stock says so instead of drawing an empty row. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void anemptyListSaysSo(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity tafel) {
            tafel.setDisplayName("lager");
        } else {
            helper.fail("Am Display hängt keine BlockEntity", display);
            return;
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                display lager {
                    list "Bestand" storage.items()
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(25, () -> {
            if (!(helper.getBlockEntity(display) instanceof DisplayBlockEntity shown)) {
                helper.fail("Display verschwunden", display);
                return;
            }
            var lines = shown.lines();
            helper.assertValueEqual(lines.size(), 1, "Eine Zeile: " + lines);
            helper.assertTrue(lines.get(0).contains("leer"),
                    "Der leere Bestand muss es sagen: " + lines.get(0));
            helper.succeed();
        });
    }

    /**
     * A button behind an enumeration still hits.
     *
     * <p>The tab takes the number that is in the packet and sends it back —
     * that is exactly what this test checks, instead of inventing a number.
     * <b>As long as every entry was exactly one row, both numbers were the
     * same</b>, and nobody noticed the difference. An enumeration brings
     * several rows, and from then on a row number points at a different
     * entry.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void abuttonBehindAlistStillHits(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 64);
        entity.storage().insert(Items.GOLD_INGOT, 32);

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                display leitstand {
                    title "Leitstand"
                    list "Bestand" storage.items()
                    button "Anstoßen" anstossen
                }

                fn anstossen() {
                    let wert = await Takt
                    return wert
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        var panel = entity.displayPanels().stream()
                .filter(candidate -> candidate.name().equals("leitstand"))
                .findFirst().orElse(null);
        if (panel == null) {
            helper.fail("Die Anzeige fehlt im Reiter");
            return;
        }
        helper.assertValueEqual(panel.buttons().size(), 1, "Ein Knopf");

        // What the tab sends on click.
        entity.pressDisplayButton("leitstand", panel.buttons().get(0).entry());

        helper.assertValueEqual(entity.flowEngine().flows().size(), 1,
                "Der Knopf muss treffen, auch mit einer Aufzählung darüber");
        helper.succeed();
    }

    /**
     * {@code brecher.energy()} reads a machine's energy level.
     *
     * <p>With parentheses like {@code redstone()} and {@code count()}: it is
     * a look into the world and not a name the program knows anyway.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void adeviceTellsItsEnergy(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos maschine = controller.east().north().north();
        helper.setBlock(maschine, FnBlocks.PRESS.get());
        entity.rebuildNetwork();
        if (helper.getBlockEntity(maschine)
                instanceof dev.devpanda.factorynetwork.block.entity.PressBlockEntity presse) {
            presse.energy().charge(4_200);
        } else {
            helper.fail("Da steht keine Presse", maschine);
            return;
        }

        helper.assertTrue(entity.deploy("""
                fn nachsehen() {
                    return quarry_output.energy()
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        Value result = entity.callFunction("nachsehen", List.of());
        helper.assertValueEqual(((Value.Int) result).value(), 4_200L,
                "Der Stromstand der Maschine");
        helper.succeed();
    }

    /** A machine without energy storage reports zero and no error. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void adeviceWithoutEnergySaysZero(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // A chest hangs on quarry_output. It has no energy, and that is not a
        // program error but a chest.
        helper.assertTrue(entity.deploy("""
                fn nachsehen() {
                    return quarry_output.energy()
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        Value result = entity.callFunction("nachsehen", List.of());
        helper.assertValueEqual(((Value.Int) result).value(), 0L,
                "Eine Kiste hat null Strom");
        helper.succeed();
    }

    // ---- Everything from A to B ---------------------------------------------

    /**
     * {@code move all} takes whatever lies in there.
     *
     * <p>A worker without {@code filter} could always do that; in a function
     * there was no notation for it. The gap came to light when striking
     * {@code output()}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void moveAllTakesWhateverIsThere(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos quelle = controller.east().north().north();
        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity kiste) {
            kiste.setItem(0, new ItemStack(Items.IRON_INGOT, 30));
            kiste.setItem(1, new ItemStack(Items.GOLD_INGOT, 12));
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn raeumen() {
                    return move all from quarry_output to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("raeumen", List.of());

        helper.assertValueEqual(countIn(helper, quelle), 0, "Die Quelle ist leer");
        helper.assertValueEqual(countIn(helper, ziel), 42, "Alles ist angekommen");
        helper.succeed();
    }

    /** Also from the network storage — otherwise it says "say what is moved". */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void moveAllEmptiesTheStorage(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos ziel = controller.east().south().south();
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 20);
        entity.storage().insert(Items.GOLD_INGOT, 5);

        helper.assertTrue(entity.deploy("""
                fn raeumen() {
                    return move all from storage to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("raeumen", List.of());

        helper.assertValueEqual(countIn(helper, ziel), 25, "Alles ist angekommen");
        helper.succeed();
    }

    /** With an amount in front: {@code move 8 all} takes eight pieces of anything. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void moveAllTakesAnAmount(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos quelle = controller.east().north().north();
        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity kiste) {
            kiste.setItem(0, new ItemStack(Items.IRON_INGOT, 30));
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn raeumen() {
                    return move 8 all from quarry_output to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("raeumen", List.of());

        helper.assertValueEqual(countIn(helper, ziel), 8, "Acht Stück");
        helper.assertValueEqual(countIn(helper, quelle), 22, "Der Rest bleibt liegen");
        helper.succeed();
    }

    /**
     * {@code all except …} clears everything but one.
     *
     * <p>The most natural use of {@code all}, and the grammar allows it:
     * {@code selection = selTerm { 'except' selTerm }}. The exception is
     * resolved against what is really there — against the registry it would
     * not work, because "everything" there is every item of the pack.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void moveAllCanSpareSomething(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos quelle = controller.east().north().north();
        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity kiste) {
            kiste.setItem(0, new ItemStack(Items.IRON_INGOT, 30));
            kiste.setItem(1, new ItemStack(Items.GOLD_INGOT, 12));
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn raeumen() {
                    return move all except item:gold_ingot from quarry_output to depot
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("raeumen", List.of());

        helper.assertValueEqual(countIn(helper, ziel), 30, "Das Eisen ist weg");
        helper.assertValueEqual(countIn(helper, quelle), 12, "Das Gold bleibt liegen");
        helper.succeed();
    }

    /** {@code filter all} in a worker is the same as no filter. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aworkerWithFilterAllTakesEverything(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos quelle = controller.east().north().north();
        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity kiste) {
            kiste.setItem(0, new ItemStack(Items.IRON_INGOT, 30));
            kiste.setItem(1, new ItemStack(Items.GOLD_INGOT, 12));
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker raeumen {
                    from quarry_output
                    to depot
                    filter all
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> helper.assertValueEqual(countIn(helper, ziel), 42,
                        "Ein Worker mit filter all nimmt alles"))
                .thenSucceed();
    }

    /** {@code insert(all)} puts in what the storage hands over. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void insertAllPutsInWhatThereIs(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos ziel = controller.east().south().south();
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_INGOT, 20);

        helper.assertTrue(entity.deploy("""
                fn fuellen() {
                    return depot.insert(all)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.callFunction("fuellen", List.of());

        helper.assertValueEqual(countIn(helper, ziel), 20, "Alles ist angekommen");
        helper.succeed();
    }

    /**
     * The acceptance probe also applies to tanks.
     *
     * <p>The same question as with the slots, the same answer: an
     * {@code IFluidHandler} cannot say what it accepts. So it is asked — with
     * the fluids that are in the draft, and with {@code fill(…, SIMULATE)},
     * which moves nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theProbeAlsoAsksTheTanks(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // In front of the connector stands an empty cauldron instead of the chest.
        helper.setBlock(controller.east().south().south(), Blocks.CAULDRON);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker kuehlen {
                    from storage
                    to depot
                    filter fluid:water
                }"""), "das Programm wurde nicht übernommen");

        var snapshot = DeviceSnapshotPacket.of(entity, "depot");
        helper.assertTrue(snapshot != null, "es kam keine Antwort");
        helper.assertTrue(snapshot.levels().tanks().stream()
                        .anyMatch(line -> line.contains("nimmt")),
                "der Kessel muss sagen, dass er Wasser nimmt: "
                        + snapshot.levels().tanks());
        helper.succeed();
    }

    /** A chest has no tanks and says nothing about them. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void achestSaysNothingAboutTanks(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker kuehlen {
                    from storage
                    to depot
                    filter fluid:water
                }"""), "das Programm wurde nicht übernommen");

        var snapshot = DeviceSnapshotPacket.of(entity, "depot");
        helper.assertTrue(snapshot != null, "es kam keine Antwort");
        helper.assertTrue(snapshot.levels().tanks().isEmpty(),
                "eine Kiste hat keine Behälter: " + snapshot.levels().tanks());
        helper.succeed();
    }

    // ---- What a pattern resolves to -----------------------------------------

    /**
     * A pattern says what it hits.
     *
     * <p>Without this information {@code maintain 64 tag:c/ores} is a promise
     * into the blue: sixty-four are kept <b>per kind</b>, and how many kinds
     * that is only the pack knows.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void apatternSaysWhatItHits(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("item:*_ore"));

        helper.assertTrue(!summary.isEmpty(), "es kam keine Auskunft");
        helper.assertTrue(summary.get(0).startsWith("trifft "),
                "die erste Zeile nennt die Zahl: " + summary.get(0));
        helper.assertTrue(summary.size() > 1, "und dann die Namen: " + summary);
        helper.succeed();
    }

    /** A single item hits exactly one kind. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void asingleItemHitsOne(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("item:iron_ore"));

        helper.assertValueEqual(summary.get(0), "trifft 1 Art", "die Zahl");
        helper.succeed();
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

        long gegeben = dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores
                .fillIntoHandler(entity.chemicals(), handler,
                        List.of("mekanism:hydrogen"), 1000);
        helper.assertValueEqual(gegeben, 1000L, "Tausend gehen in den Behälter");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 2000L,
                "und fehlen im Netz");

        long geholt = dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores
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
                        net.minecraft.resources.ResourceLocation.parse("mekanism:hydrogen")),
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

        long geholt = dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores
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

    /** Places a drive on the cable and inserts a chemical cell. */
    private static void driveWithChemicalCell(GameTestHelper helper, BlockPos at,
            dev.devpanda.factorynetwork.storage.ChemicalCellTier tier) {
        helper.setBlock(at, FnBlocks.DRIVE.get());
        if (helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.DriveBlockEntity drive) {
            drive.setCell(0, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                    .CHEMICAL_CELLS.get(tier).get()));
        } else {
            helper.fail("Am Laufwerk hängt keine BlockEntity", at);
        }
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

    /** Without a drive the network stores no chemicals. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withoutAdriveNochemicalsAreStored(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.chemicals().insert("mekanism:hydrogen", 500), 500L,
                "ohne Laufwerk passt nichts hinein");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 0L, "Bestand");
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
                helper.getBlockEntity(drivePos);
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

    /**
     * What hits nothing says so.
     *
     * <p>The most common cause is a tag this pack does not know — and that
     * looks like any other in the editor.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void whatHitsNothingSaysSo(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("item:gibtsnicht"));

        helper.assertValueEqual(summary.get(0), "trifft nichts", "die Auskunft");
        helper.succeed();
    }

    /** Fluids go the same way. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void fluidsGoTheSameWay(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("fluid:water"));

        helper.assertValueEqual(summary.get(0), "trifft 1 Art", "die Zahl");
        helper.assertTrue(summary.size() > 1, "und der Name: " + summary);
        helper.succeed();
    }

    // ---- Crafting ----------------------------------------------------------

    /**
     * A job for sixty-four chests draws planks and delivers chests.
     *
     * <p><b>Single-stage</b>: the fabricator builds what it can build from
     * the storage. If planks are missing, it does not make any from logs —
     * that comes later and is a deliberate cut, not a shortcoming.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void afabricatorCraftsFromStock(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // Eight planks per chest, sixty-four chests.
        entity.storage().insert(Items.OAK_PLANKS, 512);

        entity.requestCraft(Items.CHEST, 64);

        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.CHEST), 64L,
                            "Truhen im Speicher");
                    helper.assertValueEqual(entity.storage().count(Items.OAK_PLANKS), 0L,
                            "Bretter verbraucht");
                })
                .thenSucceed();
    }

    /**
     * Without ingredients the job waits and says what is missing.
     *
     * <p>The same honesty as a worker in front of a full chest: a job that
     * does nothing must name the reason.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void ajobWithoutIngredientsSaysWhatIsMissing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();

        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var jobs = entity.craftingJobs();
                    helper.assertValueEqual(jobs.size(), 1, "ein Auftrag");
                    helper.assertValueEqual(jobs.get(0).status().name(), "WAITING",
                            "er wartet, statt zu scheitern");
                    helper.assertTrue(!jobs.get(0).detail().isEmpty(),
                            "und sagt, was fehlt");
                })
                .thenSucceed();
    }

    /** Without a fabricator in the network nothing is crafted at all. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutAfabricatorNothingIsCrafted(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 512);

        entity.requestCraft(Items.CHEST, 8);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.CHEST), 0L,
                            "ohne Fabricator entsteht nichts");
                    // Checked on the reason and not on the state: WAITING is
                    // there from creation, the sentence only after the first
                    // tick.
                    helper.assertValueEqual(entity.craftingJobs().get(0).detail(),
                            "kein Fabricator im Netz", "der Auftrag sagt, woran es liegt");
                })
                .thenSucceed();
    }

    /** A recipe that does not exist is not accepted in the first place. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void arequestWithoutArecipeIsRefused(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();

        // Cobblestone has no recipe — it comes from the world.
        helper.assertTrue(entity.requestCraft(Items.COBBLESTONE, 1) == null,
                "ohne Rezept darf kein Auftrag entstehen");
        helper.assertTrue(entity.craftingJobs().isEmpty(), "und keiner in der Liste stehen");
        helper.succeed();
    }

    /**
     * A job survives the restart.
     *
     * <p>That is the whole reason it lives on the controller and not on the
     * device: whoever places an order for ten thousand ingots and restarts
     * the server wants to find it again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void ajobSurvivesArestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 16);
        entity.requestCraft(Items.CHEST, 64);

        var registries = helper.getLevel().registryAccess();
        var wieder = (ControllerBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(controller), helper.getBlockState(controller),
                        entity.saveWithFullMetadata(registries), registries);

        helper.assertTrue(wieder != null, "Der Controller kam nicht zurück");
        helper.assertValueEqual(wieder.craftingJobs().size(), 1,
                "Der Auftrag hat den Neustart nicht überlebt");
        helper.assertValueEqual(wieder.craftingJobs().get(0).wanted(), 64,
                "und auch nicht seine Menge");
        helper.assertValueEqual(wieder.craftingJobs().get(0).target(), Items.CHEST,
                "und nicht sein Ziel");
        helper.succeed();
    }

    /** A finished job announces itself as an event. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void afinishedJobFiresAnEvent(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 8);

        helper.assertTrue(entity.deploy("""
                global fertig = 0

                on crafting_finished(auftrag) {
                    fertig = 1
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.globals().get("fertig").describe(), "1",
                        "crafting_finished muss ausgelöst haben"))
                .thenSucceed();
    }

    /**
     * {@code craft(64 item:chest)} orders from the program.
     *
     * <p>The path that belongs to this mod: a network does nothing by itself,
     * so an order too is written and not clicked. The tab afterwards shows
     * what became of it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void craftOrdersFromCode(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 64);

        helper.assertTrue(entity.deploy("""
                fn bestellen() {
                    return craft(8 item:chest)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        Value kennung = entity.callFunction("bestellen", List.of());
        helper.assertTrue(((Value.Int) kennung).value() > 0,
                "craft muss die Kennung des Auftrags liefern");
        helper.assertValueEqual(entity.craftingJobs().size(), 1, "ein Auftrag");

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.CHEST), 8L, "acht Truhen"))
                .thenSucceed();
    }

    /** Without a recipe {@code craft} returns null and creates nothing. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void craftWithoutArecipeGivesZero(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn bestellen() {
                    return craft(1 item:cobblestone)
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        Value kennung = entity.callFunction("bestellen", List.of());
        helper.assertValueEqual(((Value.Int) kennung).value(), 0L,
                "ohne Rezept keine Kennung");
        helper.assertTrue(entity.craftingJobs().isEmpty(), "und kein Auftrag");
        helper.succeed();
    }

    /**
     * The tab gets the jobs as finished lines.
     *
     * <p>Not the drawing is checked but what goes across: the name of the
     * target as text, the numbers, the state and the reason.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void thecraftingTabGetsItsLines(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.requestCraft(Items.CHEST, 12);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var lines = entity.craftingLines();
                    helper.assertValueEqual(lines.size(), 1, "ein Auftrag");
                    helper.assertValueEqual(lines.get(0).wanted(), 12, "die Menge");
                    helper.assertValueEqual(lines.get(0).done(), 0, "noch nichts gebaut");
                    helper.assertTrue(!lines.get(0).target().isBlank(),
                            "der Name des Ziels fehlt");
                    helper.assertValueEqual(lines.get(0).detail(),
                            "kein Fabricator im Netz", "und der Grund");
                })
                .thenSucceed();
    }

    /** A cancelled job is gone — what was built stays. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void acancelledJobIsGone(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        var job = entity.requestCraft(Items.CHEST, 12);

        helper.assertTrue(entity.cancelCraft(job.id()), "der Abbruch muss greifen");
        helper.assertTrue(entity.craftingJobs().isEmpty(), "und der Auftrag weg sein");
        helper.assertTrue(!entity.cancelCraft(job.id()),
                "ein zweiter Abbruch findet nichts mehr");
        helper.succeed();
    }

    /**
     * Places a furnace with fuel at a connector of the network.
     *
     * <p>The fuel belongs to the player: the network inserts the ingredient
     * and fetches the result, but it does not heat. Whoever wants it to heat
     * writes a worker.
     */
    private static void furnaceWithFuel(GameTestHelper helper, BlockPos connector,
                                        net.minecraft.world.level.block.Block kind) {
        BlockPos machine = connector.north();
        helper.setBlock(machine, kind);
        if (helper.getBlockEntity(machine)
                instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity oven) {
            oven.setItem(1, new ItemStack(Items.COAL, 8));
        } else {
            helper.fail("Am Ofen hängt keine BlockEntity", machine);
        }
    }

    /**
     * A furnace in the network smelts for a crafting job.
     *
     * <p>That is the difference between crafting table and machine: a
     * crafting-table recipe is done in one go, a furnace recipe takes time.
     * The job inserts, waits and collects — and in between does nothing
     * else.
     */
    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void anovenInThenetworkSmeltsForAjob(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // quarry_output faces north; a chest used to stand there.
        furnaceWithFuel(helper, controller.east().north(), Blocks.FURNACE);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.RAW_IRON, 2);

        entity.requestCraft(Items.IRON_INGOT, 2);

        helper.startSequence()
                .thenIdle(600)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.IRON_INGOT), 2L,
                        "zwei Barren aus dem Ofen"))
                .thenSucceed();
    }

    /**
     * What lies in the furnace survives the restart.
     *
     * <p><b>The difference from the plan.</b> The controller recomputes that
     * every tick, because it is only an intention. A running step is a fact
     * about the world: the ingredients lie in the furnace. Whoever forgets
     * that has lost them and puts in new ones next time.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatIsIntheovenSurvivesArestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        furnaceWithFuel(helper, controller.east().north(), Blocks.FURNACE);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.RAW_IRON, 2);
        entity.requestCraft(Items.IRON_INGOT, 2);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    var job = entity.craftingJobs().get(0);
                    helper.assertTrue(job.running() != null,
                            "es muss etwas im Ofen liegen: " + job.detail());

                    var registries = helper.getLevel().registryAccess();
                    var wieder = (ControllerBlockEntity)
                            net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                                    helper.absolutePos(controller),
                                    helper.getBlockState(controller),
                                    entity.saveWithFullMetadata(registries), registries);

                    helper.assertTrue(wieder != null, "Der Controller kam nicht zurück");
                    var zurueck = wieder.craftingJobs().get(0).running();
                    helper.assertTrue(zurueck != null,
                            "der laufende Schritt hat den Neustart nicht überlebt");
                    helper.assertValueEqual(zurueck.result(), Items.IRON_INGOT,
                            "und weiß noch, worauf er wartet");
                    helper.assertValueEqual(zurueck.device(), "quarry_output",
                            "und an welcher Maschine");
                })
                .thenSucceed();
    }

    /**
     * A recipe from the program draws its ingredients into the machine.
     *
     * <p>Checked on a chest, and that is deliberate: it is no machine, but it
     * accepts and hands out — exactly the two properties a declared recipe
     * relies on. A real foreign-mod machine is available in no test run;
     * what counts here is the path of the items.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void adeclaredRecipeFeedsItsMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 4);

        helper.assertTrue(entity.deploy("""
                recipe erz_mahlen at quarry_output {
                    in 1 item:iron_ore
                    out 2 item:iron_nugget
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.requestCraft(Items.IRON_NUGGET, 2);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    // The ore has travelled from the storage into the chest,
                    // and the job waits for what is supposed to come back.
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 3L,
                            "ein Erz ist in die Maschine gegangen");
                    var job = entity.craftingJobs().get(0);
                    helper.assertTrue(job.running() != null,
                            "der Auftrag muss auf die Maschine warten: " + job.detail());
                    helper.assertValueEqual(job.running().device(), "quarry_output",
                            "und wissen, an welcher");

                    // Now the "machine" delivers: what it hands out, the
                    // network collects by itself.
                    if (helper.getBlockEntity(controller.east().north().north())
                            instanceof ChestBlockEntity chest) {
                        chest.setItem(1, new ItemStack(Items.IRON_NUGGET, 2));
                    } else {
                        helper.fail("Keine Kiste zum Beliefern");
                    }
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.IRON_NUGGET), 2L,
                        "das Ergebnis holt das Netz selbst ab"))
                .thenSucceed();
    }

    /**
     * If a recipe's fluid is missing, the job waits and touches nothing.
     *
     * <p>The cut in question: a recipe may say {@code in 1000 fluid:water}.
     * The planner does not count on it — fluids are not procured —, but the
     * executor has to pour it in when starting. And if it cannot, it must not
     * have already put the items in: a machine with four ores and no water
     * never starts, and the ore would have vanished from the network.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void arecipeWaitsForItsFluid(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 4);

        helper.assertTrue(entity.deploy("""
                recipe erz_waschen at quarry_output {
                    in 1 item:iron_ore
                    in 1000 fluid:water
                    out 2 item:iron_nugget
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.requestCraft(Items.IRON_NUGGET, 2);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var job = entity.craftingJobs().get(0);
                    helper.assertValueEqual(job.status().name(), "WAITING",
                            "der Auftrag muss warten: " + job.detail());
                    // In German it is called Wasser, in the test run without
                    // language files Water — both are checked, as with the furnace.
                    helper.assertTrue(job.detail().toLowerCase().contains("wasser")
                                    || job.detail().toLowerCase().contains("water"),
                            "und sagen, welche Sorte fehlt: " + job.detail());
                    helper.assertTrue(job.detail().contains("1000"),
                            "und wie viel: " + job.detail());
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 4L,
                            "und das Erz nicht angefasst haben");
                })
                .thenSucceed();
    }

    /**
     * And likewise if the machine does not take the fluid at all.
     *
     * <p>Checked on a chest: it accepts items and has no tank — the case in
     * which somebody writes {@code fluid:} on a device that can do nothing
     * with it. Then too both stay where they lie, and the message names the
     * kind instead of just "does not work".
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void arecipeWaitsIfTheMachineTakesNofluid(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        driveWithFluidCell(helper, controller.east().east(),
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64);
        entity.rebuildNetwork();
        entity.storage().insert(Items.IRON_ORE, 4);
        entity.fluids().insert(net.minecraft.world.level.material.Fluids.WATER, 4000);

        helper.assertTrue(entity.deploy("""
                recipe erz_waschen at quarry_output {
                    in 1 item:iron_ore
                    in 1000 fluid:water
                    out 2 item:iron_nugget
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();
        entity.requestCraft(Items.IRON_NUGGET, 2);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var job = entity.craftingJobs().get(0);
                    helper.assertValueEqual(job.status().name(), "WAITING",
                            "der Auftrag muss warten: " + job.detail());
                    helper.assertTrue(job.detail().toLowerCase().contains("wasser")
                                    || job.detail().toLowerCase().contains("water"),
                            "und sagen, woran es liegt: " + job.detail());
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 4L,
                            "das Erz bleibt im Netz");
                    helper.assertValueEqual(entity.fluids().count(
                                    net.minecraft.world.level.material.Fluids.WATER), 4000L,
                            "und kein Tropfen ist weg");
                })
                .thenSucceed();
    }

    /** A recipe at a device that does not exist is reported on deploy. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void arecipeAtAnunknownDeviceIsReported(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                recipe erz_mahlen at gibtsnicht {
                    in 1 item:iron_ore
                    out 2 item:iron_nugget
                }"""), "Eine Warnung hält das Programm nicht auf");

        helper.assertTrue(entity.diagnostics().stream().anyMatch(problem ->
                        problem.message().contains("gibtsnicht")),
                "Der unbekannte Gerätename muss auffallen: " + entity.diagnostics());
        helper.succeed();
    }

    /** Without a furnace in the network the job waits and says so. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void withoutAnovenThejobWaitsAndSaysSo(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.RAW_IRON, 2);

        entity.requestCraft(Items.IRON_INGOT, 1);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var job = entity.craftingJobs().get(0);
                    helper.assertValueEqual(job.status().name(), "WAITING", "er wartet");
                    helper.assertTrue(job.detail().toLowerCase().contains("ofen")
                                    || job.detail().toLowerCase().contains("furnace"),
                            "und sagt, welche Maschine fehlt: " + job.detail());
                })
                .thenSucceed();
    }

    /**
     * What is missing, the network builds itself.
     *
     * <p>The cut that used to lie here: a job for one chest stood still and
     * reported "8 planks missing", while logs lay in the drive and the way
     * there was a single recipe.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void amissingIngredientIsCraftedInTurn(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // Two logs are eight planks are one chest.
        entity.storage().insert(Items.OAK_LOG, 2);

        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.CHEST), 1L,
                            "die Truhe");
                    helper.assertValueEqual(entity.storage().count(Items.OAK_LOG), 0L,
                            "die Stämme sind verbraucht");
                })
                .thenSucceed();
    }

    /**
     * The network takes the wood it has.
     *
     * <p>An ingredient is a selection — {@code #planks} and not "oak planks".
     * Whoever commits to the first kind when planning tells a player with a
     * drive full of spruce logs that they are missing oak planks.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thenetworkTakesTheWoodItHas(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // No oak anywhere.
        entity.storage().insert(Items.SPRUCE_LOG, 2);

        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.storage().count(Items.CHEST), 1L,
                            "die Truhe aus Fichte");
                    helper.assertValueEqual(entity.storage().count(Items.SPRUCE_LOG), 0L,
                            "die Fichtenstämme sind verbraucht");
                })
                .thenSucceed();
    }

    /**
     * The missing line names the raw material, not the intermediate.
     *
     * <p>"8 planks missing" helps nobody who can make planks. What is wanted
     * is what somebody has to put in.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatIsMissingIsNamedDownToTheRawMaterial(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();

        entity.requestCraft(Items.CHEST, 1);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    String detail = entity.craftingJobs().get(0).detail().toLowerCase();
                    helper.assertTrue(detail.contains("log") || detail.contains("wood")
                                    || detail.contains("holz"),
                            "die Fehlzeile muss den Stamm nennen: " + detail);
                    helper.assertTrue(!detail.contains("plank") && !detail.contains("brett"),
                            "und nicht die Bretter, die das Netz selbst macht: " + detail);
                })
                .thenSucceed();
    }

    /**
     * Resupply is the same worker.
     *
     * <p>The reason {@code from} names a source and not a mode of operation:
     * "fetch it from the store" and "have it made" get the same form.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void acraftingWorkerOrdersWhatIsMissing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 64);

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to storage
                    filter item:chest
                    maintain 4
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(160)
                .thenExecute(() -> helper.assertValueEqual(
                        entity.storage().count(Items.CHEST), 4L, "vier Truhen im Vorrat"))
                .thenSucceed();
    }

    /**
     * And it orders exactly once.
     *
     * <p>The stock only rises when the job is done. A worker that only looks
     * at the stock reorders every round in the meantime — and "keep four in
     * stock" becomes forty.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void acraftingWorkerOrdersOnlyOnce(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // No fabricator: the job stands and never finishes.
        entity.rebuildNetwork();
        entity.storage().insert(Items.OAK_PLANKS, 64);

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to storage
                    filter item:chest
                    maintain 4
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(140)
                .thenExecute(() -> {
                    helper.assertValueEqual(entity.craftingJobs().size(), 1,
                            "genau ein Auftrag, nicht einer je Runde");
                    helper.assertValueEqual(entity.craftingJobs().get(0).wanted(), 4,
                            "über die vier, die fehlen");
                })
                .thenSucceed();
    }

    /** If the stock is there, it orders nothing. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void acraftingWorkerWithAfullStockOrdersNothing(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.CHEST, 8);

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to storage
                    filter item:chest
                    maintain 4
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(entity.craftingJobs().isEmpty(), "kein Auftrag");
                    helper.assertValueEqual(
                            entity.runtime().states().get("nachschub").status.name(), "IDLE",
                            "und der Worker ruht");
                })
                .thenSucceed();
    }

    /**
     * Crafting goes into the storage, not into a machine.
     *
     * <p>The path there already exists: a second worker fetches it from the
     * store and puts it into the machine. Pulling both into one line would
     * mean teaching the fabricator a target it does not have.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void acraftingWorkerDeliversOnlyToStorage(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to brecher
                    filter item:chest
                    maintain 4
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var state = entity.runtime().states().get("nachschub");
                    helper.assertValueEqual(state.status.name(), "HALTED",
                            "der Worker muss anhalten");
                    helper.assertTrue(state.detail.contains("storage"),
                            "und auf storage zeigen: " + state.detail);
                    helper.assertTrue(entity.craftingJobs().isEmpty(), "und nichts bestellen");
                })
                .thenSucceed();
    }

    /** Without {@code maintain} nobody knows how much to order. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void acraftingWorkerWithoutMaintainStops(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to storage
                    filter item:chest
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var state = entity.runtime().states().get("nachschub");
                    helper.assertValueEqual(state.status.name(), "HALTED",
                            "der Worker muss anhalten");
                    helper.assertTrue(state.detail.contains("maintain"),
                            "und maintain nennen: " + state.detail);
                })
                .thenSucceed();
    }

    /** What has no recipe is not ordered — and the worker says so. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void whatHasNorecipeIsNotOrdered(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                worker nachschub {
                    from crafting
                    to storage
                    filter item:cobblestone
                    maintain 64
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(entity.craftingJobs().isEmpty(), "kein Auftrag");
                    var state = entity.runtime().states().get("nachschub");
                    helper.assertTrue(state.detail.contains("kein Rezept"),
                            "der Grund fehlt: " + state.detail);
                })
                .thenSucceed();
    }

    /**
     * The bridge's way back: what the game knows is written next to the files.
     *
     * <p>The way there has long worked — whoever saves in VS Code has their
     * program deployed by the controller. Until now nothing came back: an
     * error stood in the terminal, and whoever was not in the game saw a
     * file that silently did not run.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thegameWritesWhatItKnowsNextToThefiles(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // A program with a device name that does not exist: the compiler
        // warns, and exactly that should arrive outside.
        helper.assertTrue(entity.deploy("""
                fn holen() {
                    move 64 item:iron_ore from kist to depot
                }"""), "Eine Warnung hält das Programm nicht auf");

        helper.startSequence()
                // The folder only comes into being at the first look, and
                // that comes once a second.
                .thenIdle(45)
                .thenExecute(() -> {
                    java.nio.file.Path folder = entity.programFilePath();
                    helper.assertTrue(folder != null, "Der Ordner neben der Welt fehlt");

                    var status = dev.devpanda.factorynetwork.lang.ProgramStatus.read(folder);
                    helper.assertTrue(status.connectors().contains("quarry_output"),
                            "Die Gerätenamen fehlen: " + status.connectors());

                    var inMain = status.diagnostics().get("main.mf");
                    helper.assertTrue(inMain != null && !inMain.isEmpty(),
                            "Die Warnung fehlt: " + status.diagnostics());
                    helper.assertTrue(inMain.get(0).message().contains("kist"),
                            "und nennt nicht, worum es geht: " + inMain.get(0).message());
                    helper.assertTrue(inMain.get(0).line() > 0,
                            "ohne Zeile kann kein Editor sie anzeigen");
                })
                .thenSucceed();
    }

    // ---- The extension on the controller -----------------------------------

    /**
     * A cable on the extension belongs to the network.
     *
     * <p>The extension brings sides, and a strand hangs on one of its sides
     * as on any side of the controller. Without that it would be a
     * decorative block.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void acableOnTheExtensionBelongsToTheNetwork(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // To the north: extension, cable, connector — the controller itself
        // is never touched in the process.
        BlockPos anbau = controller.north();
        helper.setBlock(anbau, FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(anbau.north(), FnBlocks.CABLE.get());
        BlockPos connector = anbau.north().north();
        connector(helper, connector, Direction.NORTH);
        helper.setBlock(connector.north(), Blocks.CHEST);
        name(helper, connector, "am_anbau");
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("am_anbau"),
                "Der Connector am Anbau muss im Netz hängen, gefunden wurden: "
                        + entity.graph().connectorNames());
        helper.succeed();
    }

    /**
     * An extension without a controller next to it does nothing.
     *
     * <p><b>The extension has to touch the controller</b> — directly or via
     * other extensions. If it could be connected via a cable, it would be a
     * channel multiplier placeable any number of times: six new sides for
     * one block, and the channel limit would mean nothing any more.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anextensionAtTheEndOfACableIsNothing(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // A cable from the controller to the extension, and from the
        // extension it is supposed to go on — but must not.
        BlockPos anbau = controller.north().north();
        helper.setBlock(controller.north(), FnBlocks.CABLE.get());
        helper.setBlock(anbau, FnBlocks.CONTROLLER_EXTENSION.get());
        // Upwards and not to the east: to the east lies the base setup, and
        // since a connector sits on a cable, that spot is itself a line — the
        // strand would run past the extension, and the test run would check
        // something other than what it claims.
        helper.setBlock(anbau.above(), FnBlocks.CABLE.get());
        BlockPos connector = anbau.above().above();
        connector(helper, connector, Direction.EAST);
        helper.setBlock(connector.east(), Blocks.CHEST);
        name(helper, connector, "hinter_dem_anbau");
        entity.rebuildNetwork();

        helper.assertFalse(entity.graph().connectorNames().contains("hinter_dem_anbau"),
                "Hinter einem angekabelten Anbau darf nichts hängen");
        helper.succeed();
    }

    /** Two extensions in a row pass the sides on. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extensionsChain(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos erster = controller.north();
        BlockPos zweiter = erster.north();
        helper.setBlock(erster, FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(zweiter, FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(zweiter.east(), FnBlocks.CABLE.get());
        BlockPos connector = zweiter.east().east();
        connector(helper, connector, Direction.EAST);
        helper.setBlock(connector.east(), Blocks.CHEST);
        name(helper, connector, "am_zweiten");
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("am_zweiten"),
                "Auch der zweite Anbau bringt Seiten mit");
        helper.succeed();
    }

    /**
     * The controller lies on every path — otherwise it limits nothing.
     *
     * <p><b>This is the assertion the whole limit hangs on.</b> The budget
     * computes over the nodes of a path; if the controller is not on it, the
     * budget never sees it, and however fine a number in {@code Bandwidth}
     * would do nothing at all.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyPathRunsThroughTheController(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var device = entity.graph().connector("quarry_output").orElse(null);
        helper.assertTrue(device != null, "quarry_output fehlt im Graphen");
        var path = entity.graph().pathTo(device);
        helper.assertTrue(path.stream()
                        .anyMatch(node -> node.pos().equals(helper.absolutePos(controller))),
                "der Controller liegt nicht auf dem Weg des Geräts");
        helper.succeed();
    }

    /**
     * A full controller makes the stretches behind it full.
     *
     * <p><b>Otherwise you look for the bottleneck in the wrong place.</b> A
     * cable stretch carries as much as a cable — but everything that goes
     * over it came through the controller. If that is brimful, so is the
     * stretch, no matter what the cable could do.
     *
     * <p>Without the cap the analyser reported "free" for every stretch
     * while nothing flows any more.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void afullControllerMakesTheStretchesFull(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Drive the controller brimful — only it, not the cables.
        entity.runtime().budget().spend(java.util.List.of(
                        new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                                helper.absolutePos(controller),
                                dev.devpanda.factorynetwork.block.CableColour.NONE)),
                entity.bandwidth());

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        helper.assertTrue(!data.links().isEmpty(), "der Analysator kennt keine Strecke");
        for (var link : data.links()) {
            helper.assertValueEqual(link.state(),
                    dev.devpanda.factorynetwork.analyser.AnalyserData.LinkState.FULL,
                    "eine Strecke meldet frei, obwohl der Controller davor voll ist");
            // The capacity remains that of the cable: what this stretch could
            // do does not change because nothing gets through before it.
            helper.assertValueEqual(link.capacity(),
                    dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                    "die Strecke gibt eine andere Kapazität an als das Kabel");
        }
        helper.succeed();
    }

    /**
     * Both cable kinds carry the same.
     *
     * <p><b>The dense cable has been a legacy since 30 Aug.</b> It bundled
     * channels, and channels no longer exist since 29 Aug — so it was the
     * answer to a question nobody asks any more. It has vanished from the
     * creative menu and the recipes.
     *
     * <p><b>It is not deleted</b>, and the reason is in NeoForge: 21.1 knows
     * no way to remap a vanished id in an existing world. Whoever has one in
     * the ground would have air afterwards. So the block stays and carries
     * what a cable carries.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void bothCableKindsCarryTheSame(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos plain = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos dense = helper.absolutePos(new BlockPos(2, 2, 1));
        level.setBlockAndUpdate(plain, FnBlocks.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(dense, FnBlocks.DENSE_CABLE.get().defaultBlockState());

        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level, plain),
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE,
                "das Kabel trägt nicht, was ein Kabel trägt");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level, dense),
                dev.devpanda.factorynetwork.network.Bandwidth.at(level, plain),
                "die beiden Kabelsorten tragen verschieden viel");
        helper.succeed();
    }

    /**
     * A setup in which two routers stand in the way.
     *
     * <p>Controller, cable, router, router, cable, connector, chest — in a
     * row to the east. The chest is called {@code quarry_output} as
     * everywhere else, so that the same programs fit it.
     */
    private static BlockPos twoRoutersSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        placeCable(helper, controller.east(), dev.devpanda.factorynetwork.block.CableColour.NONE);
        helper.setBlock(controller.east(2), FnBlocks.ROUTER.get());
        helper.setBlock(controller.east(3), FnBlocks.ROUTER.get());
        placeCable(helper, controller.east(4), dev.devpanda.factorynetwork.block.CableColour.NONE);

        BlockPos connector = controller.east(5);
        connector(helper, connector, Direction.EAST);
        helper.setBlock(connector.east(), Blocks.CHEST);
        name(helper, connector, "quarry_output");
        return controller;
    }

    /**
     * Two routers on the way are two ticks of latency.
     *
     * <p><b>Per device, not per block.</b> Light needs sixty nanoseconds for
     * twenty blocks — against a tick of fifty milliseconds that is nothing.
     * What costs time in a real network is the unpacking at every node.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void eachRouterOnTheWayCostsATick(GameTestHelper helper) {
        BlockPos controller = twoRoutersSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var device = entity.graph().connector("quarry_output").orElse(null);
        helper.assertTrue(device != null, "quarry_output fehlt im Graphen");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Latency.of(
                        helper.getLevel(), entity.graph().pathTo(device)),
                2 * dev.devpanda.factorynetwork.network.Latency.PER_HOP,
                "zwei Router auf dem Weg kosten nicht zwei Ticks");

        // And the cable in between costs nothing: otherwise it would be
        // distance that counts, and that is exactly the mistake this number
        // avoids.
        BlockPos far = new BlockPos(1, 1, 1);
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Latency.of(
                        helper.getLevel(), java.util.List.of(
                                new dev.devpanda.factorynetwork.network.FactoryGraph.Node(
                                        helper.absolutePos(far.east()),
                                        dev.devpanda.factorynetwork.block.CableColour.NONE))),
                0, "ein Kabel kostet Latenz");
        helper.succeed();
    }

    /**
     * Latency delays the start, not the rate.
     *
     * <p><b>This is the assertion the whole design hangs on.</b> If every
     * grab were delayed by the latency, a worker behind two routers would run
     * at a third — the latency would be a bandwidth penalty in disguise, and
     * whoever separates their network cleanly would be punished for it.
     *
     * <p>So the sustained rate is measured: after forty ticks almost
     * everything a worker with {@code rate 64 per 1t} manages must be
     * through.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void latencyDelaysTheStartNotTheRate(GameTestHelper helper) {
        BlockPos controller = twoRoutersSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east(6);
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity chest) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
        helper.assertTrue(entity.deploy("""
                worker haul {
                    from quarry_output
                    to storage
                    rate 64 per 1t
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.runAfterDelay(40, () -> {
            long moved = entity.storage().count(Items.COBBLESTONE);
            // Twenty full grabs in forty ticks: two go to the latency, a few
            // to the start-up. With a throttle per grab it would be a third
            // of that.
            helper.assertTrue(moved >= 20 * 64,
                    "nur " + moved + " Steine in vierzig Ticks — die Latenz drosselt");
            helper.succeed();
        });
    }

    /**
     * A bridge is a piece of the way, not a hole in it.
     *
     * <p><b>The same trap as with the controller.</b> {@code Bandwidth.at}
     * has long known the bridge — but if it stands on no path, the budget
     * never sees it, and its traffic is booked nowhere. The analyser hides
     * that: its stretches come from the edges, not from the path.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void abridgeIsAStretchOnTheWay(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos near = controller.east();
        placeCable(helper, near, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos bridgeNear = near.east();
        helper.setBlock(bridgeNear, FnBlocks.BRIDGE.get());

        BlockPos bridgeFar = new BlockPos(1, 4, 5);
        helper.setBlock(bridgeFar, FnBlocks.BRIDGE.get());
        BlockPos farCable = bridgeFar.east();
        placeCable(helper, farCable, dev.devpanda.factorynetwork.block.CableColour.NONE);
        BlockPos far = farCable.east();
        connector(helper, far, Direction.EAST);
        helper.setBlock(far.east(), Blocks.CHEST);
        name(helper, far, "jenseits");

        ItemStack pair = dev.devpanda.factorynetwork.item.EntanglementItem.newPair();
        if (helper.getBlockEntity(bridgeNear)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity one) {
            one.setItem(0, pair.split(1));
        }
        if (helper.getBlockEntity(bridgeFar)
                instanceof dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity two) {
            two.setItem(0, pair);
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var device = entity.graph().connector("jenseits").orElse(null);
        helper.assertTrue(device != null, "das Gerät jenseits der Brücke fehlt im Graphen");
        var path = entity.graph().pathTo(device);
        helper.assertTrue(path.stream().anyMatch(node ->
                        node.pos().equals(helper.absolutePos(bridgeNear))
                                || node.pos().equals(helper.absolutePos(bridgeFar))),
                "keine Brücke liegt auf dem Weg — ihr Verkehr wird nirgends gebucht");
        helper.succeed();
    }

    /**
     * The controller finds its own load again.
     *
     * <p><b>It rebuilds its node itself</b> to look it up in the budget — and
     * a node that is only almost the same silently yields a zero. That is
     * exactly what this run checks: booked via the path, read via the
     * rebuilt node.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theControllerFindsItsOwnLoad(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var device = entity.graph().connector("quarry_output").orElse(null);
        helper.assertTrue(device != null, "quarry_output fehlt im Graphen");
        entity.runtime().budget().spend(entity.graph().pathTo(device),
                dev.devpanda.factorynetwork.network.Bandwidth.PER_ITEM);

        helper.assertValueEqual(entity.bandwidthUsed(),
                dev.devpanda.factorynetwork.network.Bandwidth.PER_ITEM,
                "der Controller findet seinen eigenen Knoten nicht wieder");
        helper.succeed();
    }

    /**
     * Without an extension the controller carries a dense cable, with two
     * more.
     *
     * <p><b>The extension is the upgrade</b> — no slot, no card. You can see
     * on a large network that it is large.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extensionsRaiseTheControllerLimit(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.bandwidth(),
                dev.devpanda.factorynetwork.network.Bandwidth.CONTROLLER,
                "ohne Anbau trägt der Controller nicht ein dichtes Kabel");

        helper.setBlock(controller.north(), FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(controller.north().north(), FnBlocks.CONTROLLER_EXTENSION.get());
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.bandwidth(),
                dev.devpanda.factorynetwork.network.Bandwidth.CONTROLLER
                        + 2 * dev.devpanda.factorynetwork.network.Bandwidth.EXTENSION,
                "zwei Anbauten heben die Grenze nicht");

        // And the number also arrives where the budget reads it.
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.network.Bandwidth.at(
                        helper.getLevel(), helper.absolutePos(controller)),
                entity.bandwidth(),
                "Bandwidth.at liest eine andere Zahl als der Controller selbst");
        helper.succeed();
    }

    /** The extension costs power like every other part on the network. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anextensionCostsPower(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        int ohne = entity.powerDraw();

        helper.setBlock(controller.north(), FnBlocks.CONTROLLER_EXTENSION.get());
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.powerDraw(),
                ohne + dev.devpanda.factorynetwork.network.Power.EXTENSION,
                "Der Anbau kostet Strom");
        helper.succeed();
    }

    // ---- Energy cells ------------------------------------------------------

    /** The drive in the standard setup, with an energy cell in the second slot. */
    private static dev.devpanda.factorynetwork.storage.EnergyCellView energyCell(
            GameTestHelper helper, BlockPos controller,
            dev.devpanda.factorynetwork.storage.EnergyCellTier tier) {
        BlockPos at = controller.above();
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.DriveBlockEntity drive)) {
            helper.fail("Über dem Controller steht kein Laufwerk", at);
            return null;
        }
        drive.setCell(1, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.ENERGY_CELLS.get(tier).get()));
        return drive.energyCells().get(0);
    }

    /**
     * An energy cell enlarges the reserve.
     *
     * <p>The buffer in the controller stays what it was — the cell is added.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anenergyCellEnlargesTheSupply(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        int ohne = entity.power().capacity();

        var tier = dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K;
        energyCell(helper, controller, tier);
        entity.rebuildNetwork();

        helper.assertTrue(entity.power().capacity() == ohne + tier.capacity(),
                "Der Vorrat muss um die Zelle wachsen: " + ohne + " → "
                        + entity.power().capacity());
        helper.succeed();
    }

    /**
     * The network also runs when only the cell has something.
     *
     * <p><b>The core of the whole thing.</b> A reserve that only counts in
     * the controller buffer lets a network with full cells go out — in the
     * middle of operation, without any number anywhere standing at zero.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thenetworkRunsOnACellAlone(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        // The buffer at zero, the cell full: everything the network has lies
        // in the drive.
        entity.power().empty();
        if (zelle != null) {
            zelle.fill(64_000);
        }

        helper.startSequence()
                .thenIdle(Power.BOOT_TICKS + 40)
                .thenExecute(() -> helper.assertTrue(entity.power().isRunning(),
                        "Das Netz muss auf der Zelle laufen, steht aber auf "
                                + entity.power().state() + " mit "
                                + entity.power().stored() + " FE"))
                .thenSucceed();
    }

    /** What the network consumes, the cell pays for in the end. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theCellPaysWhenTheBufferIsEmpty(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        entity.power().empty();
        if (zelle == null) {
            return;
        }
        zelle.fill(64_000);

        int vorher = zelle.stored();
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(zelle.stored() < vorher,
                        "Die Zelle muss den Betrieb zahlen: " + vorher
                                + " → " + zelle.stored()))
                .thenSucceed();
    }

    /**
     * Whoever fills the buffer fills the cells afterwards.
     *
     * <p>Otherwise an energy cell would stay empty forever: power comes from
     * outside through the port on the controller, and without this path
     * that ends at the edge of the buffer.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void powerFlowsOnIntoTheCell(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        if (zelle == null) {
            return;
        }
        // First to zero, then the buffer brimful: the setup lets the network
        // run and has already almost filled it in the process.
        entity.power().empty();
        entity.power().fill(Power.CAPACITY);

        // The path from outside: the same a foreign cable takes.
        int angenommen = entity.power().port().receiveEnergy(1_000, false);

        helper.assertTrue(angenommen == 1_000,
                "Bei vollem Puffer nimmt die Zelle an: " + angenommen);
        helper.assertTrue(zelle.stored() == 1_000,
                "In der Zelle müssen 1000 FE liegen, es sind " + zelle.stored());
        helper.succeed();
    }

    /** A cell that goes out takes its charge along. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void acellCarriesItsChargeOut(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        if (zelle == null) {
            return;
        }
        zelle.fill(12_345);

        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(controller.above());
        // The item itself, no copy: the write-back only happens on removal,
        // and an earlier copy would not have received it. In the game exactly
        // this item travels into the hand.
        ItemStack heraus = drive.cell(1);
        drive.setCell(1, ItemStack.EMPTY);

        // The item in hand: what is in it now is all that remains of the
        // charge.
        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.EnergyCellItem.chargeOf(heraus) == 12_345,
                "Die Ladung muss im Gegenstand stehen, es sind "
                        + dev.devpanda.factorynetwork.storage.EnergyCellItem.chargeOf(heraus));
        helper.succeed();
    }

    /**
     * Whoever drains a cell tells the drive.
     *
     * <p>The charge lives in memory and only goes into the item on save.
     * Without this notice Minecraft does not know that the chunk has to be
     * saved — and a drive in a different chunk from the controller would
     * have the earlier charge after a restart.
     *
     * <p>The same reason as with the store contents, see
     * {@code NetworkStorage.markChanged}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void drainingACellMarksTheDrive(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        if (zelle == null) {
            return;
        }
        entity.power().empty();
        zelle.fill(1_000);

        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(controller.above()));
        chunk.setUnsaved(false);
        // The buffer is empty, so the cell pays — and nothing else runs
        // between these two lines.
        entity.power().take(50);

        helper.assertTrue(chunk.isUnsaved(),
                "Das Laufwerk muss als geändert gelten, sonst geht die Ladung "
                        + "beim Entladen des Chunks verloren");
        helper.succeed();
    }

    // ---- Device members ----------------------------------------------------

    /**
     * {@code insert} and {@code items} in a real world.
     *
     * <p>The unit test checks the language against a world made of paper;
     * here the question is whether items really travel.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void insertPutsItemsIntoTheMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Something into the network storage so that insert has something to fetch.
        entity.storage().insert(Items.IRON_INGOT, 30);

        helper.assertTrue(entity.deploy("""
                fn füllen() {
                    log(depot.insert(20 item:iron_ingot))
                }

                fn zeigen() {
                    log(depot.items())
                }"""), "das Programm wurde nicht übernommen");

        entity.callFunction("füllen", List.of());

        var connector = entity.graph().connectors().get("depot");
        var port = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                helper.getLevel(), connector.pos(), connector.side());
        IItemHandler chest = port.machineInventory();
        helper.assertTrue(chest != null, "hinter depot steht keine Kiste");

        int inChest = 0;
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            inChest += chest.getStackInSlot(slot).getCount();
        }
        helper.assertValueEqual(inChest, 20, "so viel sollte in der Kiste liegen");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 10L,
                "und so viel im Netzspeicher übrig sein");

        entity.callFunction("zeigen", List.of());
        helper.succeed();
    }

    /**
     * What does not fit in is no error.
     *
     * <p>A full machine reports zero, and the program carries on — the same
     * rule as with {@code move}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void insertIntoNothingIsZeroAndNoError(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // <b>The return value was the whole question and was never
        // checked.</b> The test called the function and was content that
        // nothing failed — nobody looked at the zero in the name. A program
        // that insert reports 20 to, although nothing arrived, writes off
        // stock it never moved.
        helper.assertTrue(entity.deploy("""
                fn füllen() {
                    return depot.insert(20 item:iron_ingot)
                }"""), "das Programm wurde nicht übernommen");

        // Nothing in storage: there is nothing to insert.
        long ohneBestand = ((dev.devpanda.factorynetwork.runtime.Value.Int)
                entity.callFunction("füllen", List.of())).value();
        helper.assertValueEqual(ohneBestand, 0L, "Aus einem leeren Speicher kommt nichts");

        // Five in storage, twenty wanted: it can only become five.
        entity.storage().insert(Items.IRON_INGOT, 5);
        long mitFuenf = ((dev.devpanda.factorynetwork.runtime.Value.Int)
                entity.callFunction("füllen", List.of())).value();
        helper.assertValueEqual(mitFuenf, 5L, "Gemeldet wird, was wirklich ankam");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 0L,
                "Und der Speicher ist sie los");

        BlockPos ziel = controller.east().south().south();
        if (helper.getBlockEntity(ziel) instanceof ChestBlockEntity kiste) {
            int gefunden = 0;
            for (int slot = 0; slot < kiste.getContainerSize(); slot++) {
                if (kiste.getItem(slot).getItem() == Items.IRON_INGOT) {
                    gefunden += kiste.getItem(slot).getCount();
                }
            }
            helper.assertValueEqual(gefunden, 5, "Die fünf liegen wirklich im Depot");
        } else {
            helper.fail("Keine Kiste am Depot", ziel);
        }
        helper.succeed();
    }

    // ---- Global values -----------------------------------------------------

    /**
     * A global value survives the server restart.
     *
     * <p>Checked via the same path a restart takes: write down, new
     * BlockEntity, read back. A value that says which mode the factory runs
     * in would be pointless after a restart if it stood at the initial value
     * again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aGlobalSurvivesARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        helper.assertTrue(entity.deploy("""
                global modus = "tag"

                fn nachtschicht() {
                    modus = "nacht"
                }"""), "das Programm wurde nicht übernommen");

        helper.assertValueEqual(entity.globals().get("modus").describe(), "tag",
                "der Anfangswert");

        entity.callFunction("nachtschicht", List.of());
        helper.assertValueEqual(entity.globals().get("modus").describe(), "nacht",
                "nach dem Aufruf");

        // The path through a server restart.
        var registries = helper.getLevel().registryAccess();
        var saved = entity.saveWithFullMetadata(registries);
        ControllerBlockEntity reborn = new ControllerBlockEntity(
                controller, entity.getBlockState());
        reborn.loadWithComponents(saved, registries);

        helper.assertTrue(reborn.globals().containsKey("modus"),
                "der Wert ist beim Speichern verlorengegangen");
        helper.assertValueEqual(reborn.globals().get("modus").describe(), "nacht",
                "nach dem Neustart gilt, was zuletzt gesetzt wurde");
        helper.succeed();
    }

    /**
     * On a program change, what still fits stays.
     *
     * <p>The same stance as with the worker states: whoever set the mode to
     * "nacht" and then changes a worker does not want to start at "tag"
     * again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aGlobalSurvivesADeploy(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        entity.deploy("""
                global modus = "tag"

                fn nachtschicht() {
                    modus = "nacht"
                }""");
        entity.callFunction("nachtschicht", List.of());

        // The same program with one additional line.
        entity.deploy("""
                global modus = "tag"
                global zaehler = 0

                fn nachtschicht() {
                    modus = "nacht"
                }""");

        helper.assertValueEqual(entity.globals().get("modus").describe(), "nacht",
                "gleicher Name, gleiche Art — der Wert bleibt");
        helper.assertValueEqual(entity.globals().get("zaehler").describe(), "0",
                "der neue Name bekommt seinen Anfangswert");

        // And the same name with a different kind starts afresh.
        entity.deploy("""
                global modus = 0

                fn nachtschicht() {
                }""");

        helper.assertValueEqual(entity.globals().get("modus").describe(), "0",
                "ein Text, der zur Zahl wird, ist kein erhaltenswerter Zustand");
        helper.assertFalse(entity.globals().containsKey("zaehler"),
                "was nicht mehr erklärt wird, wird vergessen");
        helper.succeed();
    }

    // ---- Device recognition ------------------------------------------------

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aChestIsRecognisedFromEverySide(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        BlockPos chest = connector.east();
        helper.setBlock(chest, Blocks.CHEST);
        connector(helper, connector, Direction.EAST);

        DeviceProfile profile = DeviceScan.of(partAt(helper, connector));

        helper.assertTrue(profile.reachable(), "die Kiste wurde nicht erkannt");
        helper.assertTrue(profile.descriptionId().contains("chest"),
                "falscher Übersetzungsschlüssel: " + profile.descriptionId());
        helper.assertTrue(profile.connectedSide() == Side.EAST,
                "die angeschlossene Seite stimmt nicht: " + profile.connectedSide());
        helper.assertTrue(profile.hasItems(Side.EAST),
                "eine Kiste nimmt an jeder Seite Gegenstände an");
        helper.assertTrue(profile.accessAt(Side.EAST).slots() == 27,
                "eine Kiste hat 27 Fächer, gezählt wurden "
                        + profile.accessAt(Side.EAST).slots());
        helper.assertFalse(profile.hasFluids(Side.EAST),
                "eine Kiste hat keinen Tank");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSnapshotReportsWhatIsInTheChest(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Two ingots into the chest behind quarry_output.
        var connector = entity.graph().connectors().get("quarry_output");
        helper.assertTrue(connector != null, "quarry_output fehlt im Netz");
        var port = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                helper.getLevel(), connector.pos(), connector.side());
        IItemHandler chest = port.machineInventory();
        helper.assertTrue(chest != null, "hinter quarry_output steht keine Kiste");
        chest.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);

        DeviceSnapshotPacket snapshot =
                DeviceSnapshotPacket.of(entity, "quarry_output");

        helper.assertTrue(snapshot != null, "es kam keine Antwort");
        helper.assertValueEqual(snapshot.slots().size(), 27, "Fächer der Kiste");
        helper.assertValueEqual(snapshot.slotsOmitted(), 0, "bei 27 Fächern wird nichts gekürzt");
        helper.assertValueEqual(snapshot.slots().get(0).getCount(), 2, "Inhalt des ersten Fachs");
        helper.assertTrue(snapshot.profile().descriptionId().contains("chest"),
                "die Antwort trägt kein Profil der Kiste: "
                        + snapshot.profile().descriptionId());
        helper.succeed();
    }

    /**
     * The whole path of a request: terminal open, name asked, answer there.
     *
     * <p>Without this test only {@code DeviceSnapshotPacket.of} was checked —
     * that is, precisely the part that goes wrong least anyway. The chain
     * before it (is the player standing at a terminal, does the menu find its
     * controller) could otherwise only be traced by hand in the running
     * game.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aRequestFromAnOpenTerminalIsAnswered(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos terminalPos = controller.below();
        helper.setBlock(terminalPos, FnBlocks.TERMINAL.get());

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.containerMenu = new dev.devpanda.factorynetwork.client.menu.TerminalMenu(
                0, player.getInventory(), helper.absolutePos(terminalPos));

        var answer = dev.devpanda.factorynetwork.network.packet.DeviceSnapshotRequestPacket
                .answerFor(player, "quarry_output");

        helper.assertTrue(answer != null,
                "das offene Terminal hätte antworten müssen");
        helper.assertValueEqual(answer.connector(), "quarry_output", "gefragtes Gerät");
        helper.assertValueEqual(answer.slots().size(), 27, "Fächer der Kiste");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aRequestWithoutAnOpenTerminalIsRefused(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        controllerAt(helper, controller).rebuildNetwork();

        // No terminal open — the player has their own inventory in front of
        // them. An answer here would be a way to read the network without
        // standing in front of it.
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        helper.assertTrue(dev.devpanda.factorynetwork.network.packet
                        .DeviceSnapshotRequestPacket.answerFor(player, "quarry_output") == null,
                "ohne offenes Terminal darf es keine Antwort geben");
        helper.succeed();
    }

    /**
     * The acceptance probe against the items from the draft.
     *
     * <p>A chest accepts everything — so the test does not check whether the
     * probe is clever but whether it runs at all: that the candidates are
     * read from the program and that every slot gets an answer.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theProbeUsesTheItemsFromTheDraft(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // The draft names an item — that becomes the candidate.
        helper.assertTrue(entity.deploy("""
                worker mahlen {
                    from storage
                    to depot
                    filter item:iron_ingot
                }"""), "das Programm wurde nicht übernommen");

        var snapshot = DeviceSnapshotPacket.of(entity, "depot");
        helper.assertTrue(snapshot != null, "es kam keine Antwort");
        helper.assertValueEqual(snapshot.probes().size(), 27, "eine Auskunft je Fach");

        var erstes = snapshot.probes().get(0);
        helper.assertTrue(erstes.takes(), "eine Kiste nimmt an");
        helper.assertTrue(!erstes.accepts().isEmpty(),
                "der Barren aus dem Entwurf muss als passend erkannt werden");
        helper.assertTrue(erstes.accepts().get(0).contains("iron_ingot"),
                "gemeldet wurde: " + erstes.accepts());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSnapshotOfAnUnknownNameIsRefused(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(DeviceSnapshotPacket.of(entity, "gibt_es_nicht") == null,
                "auf einen unbekannten Namen darf es keine Antwort geben");
        helper.succeed();
    }

    /**
     * A connector pointing into the void.
     *
     * <p><b>Air is an answer, not a missing one.</b> The test once stood the
     * other way round here — it demanded that "nothing known" be reported
     * about air, and thereby cemented a bug: in the game "Nicht geladen" then
     * stood in front of a player who was standing right there. Whoever points
     * into the void should learn exactly that.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aConnectorPointingAtAirSaysSo(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        connector(helper, connector, Direction.EAST);

        DeviceProfile profile = DeviceScan.of(partAt(helper, connector));

        helper.assertTrue(profile.reachable(),
                "über Luft ist sehr wohl etwas bekannt: dass dort nichts steht");
        helper.assertTrue(profile.descriptionId().endsWith(".air"),
                "dort steht Luft, gemeldet wurde " + profile.descriptionId());
        helper.assertTrue(profile.access().isEmpty(),
                "an Luft ist nichts anzuschließen");
        helper.succeed();
    }


    /**
     * A chemical does not travel on the item path.
     *
     * <p><b>The worst bug this language can have</b>, in the release of
     * 2026-08-26: {@code move} decided the path by the kind, and for that it
     * asked only the written selection. An already resolved one — that is
     * how it comes out of a loop and out of every {@code it} — had received
     * an addendum for fluids, not for chemicals. So a chemical landed in the
     * item resolution, hit nothing there, and no selection means
     * <i>everything</i> there: the chest ended up empty.
     *
     * <p>The test run needs no chemical tank for that. It is enough that the
     * selection resolves — what is supposed to happen afterwards is nothing.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aresolvedChemicalDoesNotTravelOnTheItemPath(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos source = controller.east().north().north();
        if (helper.getBlockEntity(source) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 32));
        } else {
            helper.fail("Keine Kiste an der Quelle", source);
        }
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                fn versuchen() {
                    for gas in chemical:mekanism/hydrogen {
                        move 100 gas from quarry_output to depot
                    }
                }"""), "Das Programm wurde nicht übernommen");

        entity.callFunction("versuchen", List.of());

        helper.assertValueEqual(countIn(helper, source), 32,
                "Das Erz muss liegen bleiben — eine Chemikalie meint kein Erz");
        helper.assertValueEqual(countIn(helper, controller.east().south().south()), 0,
                "und in der Zielkiste darf nichts angekommen sein");
        helper.succeed();
    }

    /**
     * A saved item is still called the same on disk.
     *
     * <p>The same promise as in {@code ValueCodecFormatTest}, only for the two
     * kinds that need a registry: a waiting flow from an old world must find
     * its variables again. Built by hand and not via a round trip — a round
     * trip always agrees with itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void asavedItemKeepsItsNameOnDisk(GameTestHelper helper) {
        net.minecraft.nbt.CompoundTag single = new net.minecraft.nbt.CompoundTag();
        single.putString("t", "item");
        single.putString("v", "minecraft:iron_ore");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(
                        dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(single)),
                single, "die Form eines gespeicherten Gegenstands");

        net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
        items.add(net.minecraft.nbt.StringTag.valueOf("minecraft:iron_ore"));
        items.add(net.minecraft.nbt.StringTag.valueOf("minecraft:copper_ore"));
        net.minecraft.nbt.CompoundTag selection = new net.minecraft.nbt.CompoundTag();
        selection.putString("t", "sel");
        selection.put("i", items);
        selection.putLong("a", 64);
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(
                        dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(selection)),
                selection, "die Form einer gespeicherten Gegenstandsauswahl");

        net.minecraft.nbt.CompoundTag fluid = new net.minecraft.nbt.CompoundTag();
        fluid.putString("t", "fluid");
        fluid.putString("v", "minecraft:water");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(
                        dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(fluid)),
                fluid, "die Form einer gespeicherten Flüssigkeit");

        net.minecraft.nbt.ListTag fluids = new net.minecraft.nbt.ListTag();
        fluids.add(net.minecraft.nbt.StringTag.valueOf("minecraft:water"));
        net.minecraft.nbt.CompoundTag fluidSelection = new net.minecraft.nbt.CompoundTag();
        fluidSelection.putString("t", "fluidsel");
        fluidSelection.put("i", fluids);
        fluidSelection.putLong("a", -1);
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(
                        dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(fluidSelection)),
                fluidSelection, "die Form einer gespeicherten Flüssigkeitsauswahl");
        helper.succeed();
    }

    /**
     * The three stores answer the same questions in the same way.
     *
     * <p>Cut 2 from `ressourcenarten.md`: items, fluids and chemicals all
     * lie in cells in drives, and the network's view of them was three times
     * the same class with different types. Now it is an interface, and here
     * stands its contract — written down once and run through three times.
     *
     * <p><b>Via a reference of the interface type.</b> Whoever addressed the
     * concrete classes would check three paths instead of one and would not
     * notice when one of them drifts. That is exactly what had happened in
     * cut 1.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void thethreeStoresKeepTheSameContract(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        BlockPos drive = controller.above();
        helper.setBlock(drive, FnBlocks.DRIVE.get());
        if (helper.getBlockEntity(drive)
                instanceof dev.devpanda.factorynetwork.block.entity.DriveBlockEntity bay) {
            bay.setCell(0, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CELLS
                    .get(dev.devpanda.factorynetwork.storage.CellTier.K64).get()));
            bay.setCell(1, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.FLUID_CELLS
                    .get(dev.devpanda.factorynetwork.storage.FluidCellTier.B256).get()));
            bay.setCell(2, new ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                    .CHEMICAL_CELLS.get(
                            dev.devpanda.factorynetwork.storage.ChemicalCellTier.K256).get()));
        } else {
            helper.fail("Am Laufwerk hängt keine BlockEntity", drive);
        }
        entity.rebuildNetwork();

        keepsTheContract(helper, entity.store(
                dev.devpanda.factorynetwork.runtime.ResourceKinds.ITEM),
                dev.devpanda.factorynetwork.storage.ItemKey.bare(Items.IRON_INGOT),
                64, "Gegenstände");
        keepsTheContract(helper, entity.store(
                dev.devpanda.factorynetwork.runtime.ResourceKinds.FLUID),
                net.minecraft.world.level.material.Fluids.WATER, 1000, "Flüssigkeiten");
        keepsTheContract(helper, entity.store(
                dev.devpanda.factorynetwork.runtime.ResourceKinds.CHEMICAL),
                "mekanism:hydrogen", 500, "Chemikalien");
        helper.succeed();
    }

    /**
     * In, look, out again.
     *
     * <p>The four questions every store must answer, plus the fifth that
     * exists only because some things cannot be put back: {@code room} is
     * asked <b>before</b> a tank is drained.
     */
    private static void keepsTheContract(GameTestHelper helper,
            dev.devpanda.factorynetwork.network.ResourceStore store,
            Object key, long amount, String what) {
        helper.assertTrue(store.hasDrives(), what + ": ein Laufwerk hängt am Netz");
        helper.assertValueEqual(store.room(key, amount), amount,
                what + ": so viel ginge hinein");
        helper.assertValueEqual(store.insert(key, amount), 0L,
                what + ": und geht auch wirklich hinein");
        helper.assertValueEqual(store.count(key), amount,
                what + ": danach liegt es da");
        helper.assertValueEqual(store.contents().get(key), amount,
                what + ": und steht im Bestand");
        helper.assertValueEqual(store.extract(key, amount), amount,
                what + ": und kommt wieder heraus");
        helper.assertValueEqual(store.count(key), 0L,
                what + ": danach liegt nichts mehr da");
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

    /** How much of one kind lies in the chest at this spot. */
    private static int countIn(GameTestHelper helper, BlockPos pos, Item wanted) {
        if (!(helper.getBlockEntity(pos) instanceof ChestBlockEntity container)) {
            return 0;
        }
        int found = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(wanted)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * Two storage buses side by side, and the priority decides.
     *
     * <p>Cut 4 from {@code speicherbus.md}. The three cuts before it were
     * checked with <b>one</b> bus — that several together yield one stock
     * and that the order when storing is right was a promise without proof.
     * The same gap as with the second drive (5.2).
     *
     * <p>Both halves are checked: the stock is the <b>sum</b> of both chests,
     * and what the network stores goes into the one with the higher
     * {@code priority} — not into the one that happens to come first in the
     * program.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void twoStoresAddUpAndPriorityDecides(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos erste = controller.east().north().north();
        BlockPos zweite = controller.east().south().south();
        entity.rebuildNetwork();

        if (helper.getBlockEntity(erste) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_ORE, 12));
        } else {
            helper.fail("Keine Kiste an quarry_output", erste);
        }
        if (helper.getBlockEntity(zweite) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        } else {
            helper.fail("Keine Kiste an depot", zweite);
        }

        // quarry_output comes first in the program and has the lower
        // priority — that way the test says something about the order and
        // not about the order of writing.
        helper.assertTrue(entity.deploy("""
                store quarry_output {
                }

                store depot {
                    priority 10
                }"""), "Das Programm wurde nicht übernommen");
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 12L,
                "die erste Kiste zählt zum Bestand");
        helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 7L,
                "die zweite auch — ein Bestand ist die Summe aller Busse");

        // No drive in the setup: what goes in has to go into one of the chests.
        long rest = entity.storage().insert(Items.GOLD_INGOT, 5);

        helper.assertValueEqual(rest, 0L, "fünf Barren passen in eine Kiste");
        helper.assertValueEqual(countIn(helper, zweite, Items.GOLD_INGOT), 5,
                "und gehen in die Kiste mit der höheren priority");
        helper.assertValueEqual(countIn(helper, erste, Items.GOLD_INGOT), 0,
                "die andere bleibt unberührt");
        helper.succeed();
    }

    /**
     * In the running game the list of resource kinds is closed.
     *
     * <p>The registry is open — but only during loading. That is not a
     * request to foreign mods but the promise everything else rests on:
     * <b>what a program means does not depend on when somebody registers
     * something.</b> Without it a mod could claim a prefix in the middle of
     * the game, and the same program would mean something different before
     * and after.
     *
     * <p>So the closed door is checked and not the open one: that you
     * <i>can</i> register is shown by {@code ForeignResourceKindTest}. That it
     * no longer works afterwards can only be shown by a running game — the
     * call that closes hangs on the mod loading process.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void theresourceKindsAreClosedInArunningGame(GameTestHelper helper) {
        var kinds = dev.devpanda.factorynetwork.runtime.ResourceKinds.all();
        // Three built-in ones and Source from compat/ars. The fourth is not
        // here as decoration: it is the proof that a foreign kind is really
        // in a running game — registered on load, like every other.
        helper.assertValueEqual(kinds.size(), 4, "drei eingebaute und Source");
        helper.assertTrue(dev.devpanda.factorynetwork.runtime.ResourceKinds
                        .byPrefix("source") != null,
                "Source muss angemeldet sein, auch ohne Ars Nouveau: sonst hieße "
                        + "source:source keine Ressourcenart statt diese Mod fehlt");

        boolean gemeldet = false;
        try {
            dev.devpanda.factorynetwork.runtime.ResourceKinds.register(
                    dev.devpanda.factorynetwork.runtime.ResourceKinds.ITEM);
        } catch (IllegalStateException error) {
            gemeldet = true;
            helper.assertTrue(error.getMessage().contains("Laden"),
                    "Die Meldung soll sagen, wann angemeldet wird: " + error.getMessage());
        }
        helper.assertTrue(gemeldet,
                "Nach dem Laden darf keine Art mehr dazukommen — sonst hinge die "
                        + "Bedeutung eines Programms am Zeitpunkt");
        helper.succeed();
    }

    /**
     * The connector sends its name to the client.
     *
     * <p><b>Found on the first play.</b> The "name device" window stood empty
     * in front of a connector that had long been called {@code kiste_1}. The
     * reason was not in the window: it reads the name from the BlockEntity
     * on the client side, and it never arrived there.
     *
     * <p>{@code setLabel} calls {@code sendBlockUpdated} — but that only sends
     * what {@code getUpdatePacket()} returns, and that was not overridden.
     * The default returns {@code null}, so nothing went out. On the display
     * the method had always been there; the connector was the copy nobody
     * kept up to date.
     *
     * <p>The packet is checked and not the window: a test run has no client.
     * What is shown here is the spot where it got stuck — without a packet
     * nothing arrives at the client, with a packet the name is in it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aconnectorSendsItsNameToTheClient(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        connector(helper, connector, Direction.EAST);
        var entity = partAt(helper, connector);
        if (entity == null) {
            helper.fail("An dieser Stelle sitzt kein Anschluss", connector);
            return;
        }

        entity.setLabel("kiste_1");

        // The packet comes from the cable block: it carries the connectors,
        // so it sends them too.
        var packet = helper.getBlockEntity(connector).getUpdatePacket();
        helper.assertTrue(packet != null,
                "Ohne Paket erfährt der Client nie, wie das Gerät heißt");
        helper.assertTrue(packet
                        instanceof net.minecraft.network.protocol.game
                                .ClientboundBlockEntityDataPacket data
                        && data.getTag() != null
                        && labelledIn(data.getTag(), "kiste_1"),
                "und im Paket muss der Name stehen");
        helper.succeed();
    }

    /** Is this name on one of the connectors in the packet? */
    private static boolean labelledIn(net.minecraft.nbt.CompoundTag tag, String label) {
        var parts = tag.getList("Parts", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < parts.size(); i++) {
            if (label.equals(parts.getCompound(i).getString("Label"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A gateway gives its surroundings the plant name.
     *
     * <p>So far a plant comes about solely through labelling —
     * {@code werk_1/eingang} on every single device. That stays, but it
     * demands that you repeat the name twelve times and walk to twelve places
     * when renaming.
     *
     * <p>The gateway is the other answer and starts from what a plant really
     * is in the game: <b>something contiguous</b>. What hangs on the cable
     * behind it belongs to it — without the name standing on a single
     * connector.
     *
     * <p>What is checked is the name the <b>network</b> knows, not the one on
     * the block: that is exactly what the interpreter, the plant recognition
     * and both editors look at.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void agatewayNamesWhatHangsBehindIt(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Controller — cable — gateway — cable — connector.
        BlockPos cable = controller.east();
        BlockPos gateway = cable.east();
        BlockPos beyond = gateway.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(gateway, FnBlocks.GATEWAY.get());
        helper.setBlock(beyond, FnBlocks.CABLE.get());

        BlockPos connector = beyond.north();
        connector(helper, connector, Direction.NORTH);
        helper.setBlock(connector.north(), Blocks.CHEST);
        name(helper, connector, "eingang");

        if (helper.getBlockEntity(gateway)
                instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity entity) {
            entity.setInstance("werk_1");
        } else {
            helper.fail("Am Gateway hängt keine BlockEntity", gateway);
        }

        ControllerBlockEntity net = controllerAt(helper, controller);
        net.rebuildNetwork();

        helper.assertTrue(net.graph().connectorNames().contains("werk_1/eingang"),
                "Das Netz muss das Gerät als werk_1/eingang kennen: "
                        + net.graph().connectorNames());
        helper.assertTrue(!net.graph().connectorNames().contains("eingang"),
                "und nicht daneben noch einmal ohne Anlage");
        helper.succeed();
    }

    /**
     * The label wins against the gateway.
     *
     * <p>If the slash is already in the name, the plant is stated. A block
     * placed nearby may not silently shift anything about that — that is
     * the kind of surprise you search for the longest.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void thelabelWinsAgainstTheGateway(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos gateway = controller.east();
        helper.setBlock(gateway, FnBlocks.GATEWAY.get());
        BlockPos cable = gateway.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        BlockPos connector = cable.north();
        connector(helper, connector, Direction.NORTH);
        helper.setBlock(connector.north(), Blocks.CHEST);
        name(helper, connector, "werk_2/ausgang");

        if (helper.getBlockEntity(gateway)
                instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity entity) {
            entity.setInstance("werk_1");
        }

        ControllerBlockEntity net = controllerAt(helper, controller);
        net.rebuildNetwork();

        helper.assertTrue(net.graph().connectorNames().contains("werk_2/ausgang"),
                "Der geschriebene Name bleibt: " + net.graph().connectorNames());
        helper.assertTrue(!net.graph().connectorNames().contains("werk_1/werk_2/ausgang"),
                "und wird nicht noch einmal vorangestellt");
        helper.succeed();
    }


    /**
     * A connector on the cable block counts toward the network.
     *
     * <p>Until now a separate connector block stood <b>next to</b> the cable
     * for every machine — a wall of machines cost six blocks where one
     * suffices. Since the cable bus the connector sits on a face of the
     * cable, as in AE2.
     *
     * <p>What is checked is what the network knows: the connector must appear
     * under its name in the graph and reach the machine behind it — both
     * used to depend on a block of its own standing there.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aparIsSeenOnTheCableItself(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        // The chest lies to the north; the connector sits on the same face of
        // the cable — no block of its own in between.
        helper.setBlock(cable.north(), Blocks.CHEST);

        if (helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.NORTH).setLabel("kiste_1");
        } else {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
            return;
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("kiste_1"),
                "Das Netz muss den Anschluss am Kabel kennen: "
                        + entity.graph().connectorNames());

        var part = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                helper.getLevel(), helper.absolutePos(cable), Direction.NORTH);
        helper.assertTrue(part != null, "und er muss über Ort und Seite zu finden sein");
        helper.assertTrue(part.machineInventory() != null,
                "und die Kiste dahinter erreichen");
        helper.succeed();
    }

    /**
     * Two connectors on one cable block are two devices.
     *
     * <p>That is the gain it was about: one block, two machines. The graph
     * does not distinguish them yet — it remembers a position and no side —,
     * and that is exactly why this test is here: it pins down what the next
     * cut has to solve.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void twopartsOnOneBlockAreTwoDevices(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);
        helper.setBlock(cable.south(), Blocks.CHEST);

        if (!(helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("nord");
        bus.addPart(Direction.SOUTH).setLabel("sued");

        helper.assertValueEqual(bus.parts().size(), 2, "zwei Anschlüsse an einem Block");
        var level = helper.getLevel();
        BlockPos here = helper.absolutePos(cable);
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.block.entity.Connectors
                        .at(level, here, Direction.NORTH).label(), "nord",
                "der nördliche");
        helper.assertValueEqual(
                dev.devpanda.factorynetwork.block.entity.Connectors
                        .at(level, here, Direction.SOUTH).label(), "sued",
                "und der südliche");
        // Without a side there is no answer — no guessing.
        helper.assertTrue(dev.devpanda.factorynetwork.block.entity.Connectors
                        .at(level, here) == null,
                "und ohne Seite gibt es keine Antwort");
        helper.succeed();
    }

    /**
     * A program moves items through a connector on the cable.
     *
     * <p>The proof that it does not stop at bookkeeping: the graph knows the
     * connector, and the runtime reaches through it to the machine. Up to
     * here both were bound to a connector block of its own.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void amoveRunsThroughApartOnTheCable(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        BlockPos chest = cable.north();
        helper.setBlock(chest, Blocks.CHEST);
        if (helper.getBlockEntity(chest) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(Items.IRON_ORE, 12));
        } else {
            helper.fail("Keine Kiste", chest);
            return;
        }
        if (helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            bus.addPart(Direction.NORTH).setLabel("kiste_1");
        } else {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
            return;
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn holen() {
                    move all from kiste_1 to storage
                }"""), "Das Programm wurde nicht übernommen");

        entity.callFunction("holen", List.of());

        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 12L,
                "Das Erz muss durch den Anschluss am Kabel ins Netz gekommen sein");
        helper.succeed();
    }

    /**
     * Two named connectors on one cable block are two devices.
     *
     * <p>That is the gain AE2's block form is about: one block, two machines.
     * Up to here the graph remembered <b>one position</b> — and a position
     * with two connectors was therefore not one device but none at all:
     * {@code Connectors.at(level, pos)} gives no answer without a side, and
     * the channel allocation skipped the position. Both names were missing
     * from the network.
     *
     * <p>What is checked is not only that both names are there but that each
     * hits <b>its own</b> machine: the northern connector fetches ore, the
     * southern fetches coal, and neither fetches both.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void btwoNamedPartsOnOneCableAreTwoDevices(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        fillChest(helper, cable.north(), Items.IRON_ORE, 12);
        fillChest(helper, cable.south(), Items.COAL, 7);

        if (!(helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("erzkiste");
        bus.addPart(Direction.SOUTH).setLabel("kohlekiste");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("erzkiste"),
                "Der nördliche Anschluss fehlt im Netz: "
                        + entity.graph().connectorNames());
        helper.assertTrue(entity.graph().connectorNames().contains("kohlekiste"),
                "Der südliche Anschluss fehlt im Netz: "
                        + entity.graph().connectorNames());

        helper.assertTrue(entity.deploy("""
                fn holen() {
                    move all from erzkiste to storage
                }"""), "Das Programm wurde nicht übernommen");
        entity.callFunction("holen", List.of());

        helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 12L,
                "Der nördliche Anschluss muss die nördliche Kiste treffen");
        helper.assertValueEqual(entity.storage().count(Items.COAL), 0L,
                "und nicht die südliche mit");
        helper.succeed();
    }

    /**
     * Every connector gives its redstone to its own machine.
     *
     * <p>The connector block gives the same to all sides — it has only one
     * face, after all. On the cable block that would not work: six
     * connectors with a shared strength would be six machines on one switch,
     * and {@code setRedstone} would lose its meaning.
     *
     * <p>The rule that means the same for both block forms: <b>a face with a
     * connector gives exactly its strength. A free face gives the
     * strongest</b> — otherwise no lamp could be switched at a cable any
     * more, and the connector block would yield something different from
     * before.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bapartEmitsRedstoneTowardsItsOwnMachine(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());

        if (!(helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
            return;
        }
        bus.addPart(Direction.NORTH).setEmittedRedstone(15);
        bus.addPart(Direction.SOUTH).setEmittedRedstone(3);

        var level = helper.getLevel();
        BlockPos here = helper.absolutePos(cable);
        // Whoever stands to the north asks with SOUTH: the direction points
        // from them to the cable. The part that faces them answers — the one
        // on the north face.
        helper.assertValueEqual(level.getSignal(here, Direction.SOUTH), 15,
                "Nach Norden geht die Stärke des nördlichen Anschlusses");
        helper.assertValueEqual(level.getSignal(here, Direction.NORTH), 3,
                "und nach Süden die des südlichen");
        helper.assertValueEqual(level.getSignal(here, Direction.UP), 15,
                "Eine freie Fläche gibt die stärkste — sonst schaltete ein "
                        + "Kabel kein Lämpchen mehr");
        helper.succeed();
    }

    /** A chest with contents, because three tests needed the same six lines. */
    private static void fillChest(GameTestHelper helper, BlockPos at,
                                  net.minecraft.world.item.Item what, int amount) {
        helper.setBlock(at, Blocks.CHEST);
        if (helper.getBlockEntity(at) instanceof ChestBlockEntity container) {
            container.setItem(0, new ItemStack(what, amount));
        } else {
            helper.fail("Keine Kiste", at);
        }
    }

    /**
     * A connector on the cable belongs to the gateway's plant.
     *
     * <p>Plant recognition walks from block to block and stopped at
     * everything that is not a cable — a cable <b>was</b> only a line. Since
     * it carries connectors on its faces, it is both: the strand runs
     * through, and what hangs on it belongs.
     *
     * <p>Without this step the device would still be called {@code eingang}
     * — and every program that writes {@code werk_1/eingang} would not find
     * it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bagatewayNamesApartOnTheCable(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Controller — cable — gateway — cable with a connector on the north side.
        BlockPos cable = controller.east();
        BlockPos gateway = cable.east();
        BlockPos beyond = gateway.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(gateway, FnBlocks.GATEWAY.get());
        helper.setBlock(beyond, FnBlocks.CABLE.get());
        helper.setBlock(beyond.north(), Blocks.CHEST);

        if (!(helper.getBlockEntity(beyond)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            helper.fail("Am Kabel hängt keine BlockEntity für Teile", beyond);
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("eingang");

        if (helper.getBlockEntity(gateway)
                instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity entity) {
            entity.setInstance("werk_1");
        } else {
            helper.fail("Am Gateway hängt keine BlockEntity", gateway);
            return;
        }

        ControllerBlockEntity net = controllerAt(helper, controller);
        net.rebuildNetwork();

        helper.assertTrue(net.graph().connectorNames().contains("werk_1/eingang"),
                "Auch ein Anschluss am Kabel gehört zur Anlage: "
                        + net.graph().connectorNames());
        helper.succeed();
    }

    // ---- Placing and removing connectors on a cable face --------------------

    /** The cable block with its BlockEntity, or the test fails. */
    private static dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity busAt(
            GameTestHelper helper, BlockPos cable) {
        if (helper.getBlockEntity(cable)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus) {
            return bus;
        }
        helper.fail("Am Kabel hängt keine BlockEntity für Teile", cable);
        return null;
    }

    /** A hit in the middle of a face of the block. */
    private static net.minecraft.world.phys.BlockHitResult hitOn(
            GameTestHelper helper, BlockPos block, Direction face) {
        BlockPos here = helper.absolutePos(block);
        return new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(here).relative(face, 0.5),
                face, here, false);
    }

    /**
     * A connector is placed on a cable face.
     *
     * <p>That is the move AE2's block form is about: the connector does not
     * go <b>next to</b> the cable but <b>onto</b> it. Up to here a part could
     * only be created in the test run — in the game there was no way to it.
     *
     * <p>It is checked at the same time that an occupied face accepts
     * nothing: where the cable already continues there is no room, and a
     * second part on the same face does not exist.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bapartIsPlacedOnAcableFace(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);
        // To the east the cable continues — there is no room there.
        helper.setBlock(cable.east(), FnBlocks.CABLE.get());

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(FnItems.CONNECTOR.get());
        stack.setCount(2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);

        helper.getBlockState(cable).useItemOn(stack, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                hitOn(helper, cable, Direction.NORTH));

        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        helper.assertTrue(bus.partAt(Direction.NORTH) != null,
                "an der geklickten Fläche muss ein Anschluss sitzen");
        helper.assertValueEqual(stack.getCount(), 1, "und einer aus der Hand");

        // The same face once more: it is occupied.
        helper.getBlockState(cable).useItemOn(stack, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                hitOn(helper, cable, Direction.NORTH));
        helper.assertValueEqual(stack.getCount(), 1, "eine besetzte Fläche nimmt nichts");

        // And the face where the cable continues.
        helper.getBlockState(cable).useItemOn(stack, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                hitOn(helper, cable, Direction.EAST));
        helper.assertTrue(bus.partAt(Direction.EAST) == null,
                "wo das Kabel weiterläuft, ist kein Platz");
        helper.succeed();
    }

    /**
     * Sneaking with an empty hand takes the connector off again.
     *
     * <p>Without this path you could only get your part back by breaking
     * the whole cable — and with it all the other connectors on it.
     *
     * <p>The empty click without sneaking remains the naming window. The
     * distinction works because Minecraft calls {@code useWithoutItem} even
     * when sneaking, as long as both hands are empty.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bapartComesOffAgainWhenSneaking(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);
        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        helper.getBlockState(cable).useWithoutItem(helper.getLevel(), player,
                hitOn(helper, cable, Direction.NORTH));

        helper.assertTrue(bus.partAt(Direction.NORTH) == null,
                "der Anschluss muss ab sein");
        helper.assertItemEntityPresent(FnItems.CONNECTOR.get(), cable, 2.0);
        helper.succeed();
    }

    /**
     * Whoever breaks the cable gets its connectors back.
     *
     * <p>Otherwise they would vanish with the block — two items gone,
     * without anything pointing to it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void btwopartsDropWithTheCable(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH);
        bus.addPart(Direction.SOUTH);

        helper.destroyBlock(cable);

        helper.assertItemEntityCountIs(FnItems.CONNECTOR.get(), cable, 2.0, 2);
        helper.succeed();
    }

    /**
     * A neighbour that is added costs no connector.
     *
     * <p><b>The classic at this spot:</b> {@code onRemove} fires on every
     * state change, and a cable changes its state with every neighbour that
     * appears or disappears. Without the check whether a different block
     * really goes there, the parts would drop out on every connection update
     * — while building the line, not while breaking it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bapartSurvivesAnewNeighbour(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);
        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("kiste_1");

        // The line keeps growing: the cable's state changes.
        helper.setBlock(cable.east(), FnBlocks.CABLE.get());
        helper.setBlock(cable.west(), FnBlocks.CABLE.get());

        var after = busAt(helper, cable);
        helper.assertTrue(after != null && after.partAt(Direction.NORTH) != null,
                "der Anschluss muss den Nachbarn überleben");
        helper.assertValueEqual(after.partAt(Direction.NORTH).label(), "kiste_1",
                "mitsamt seinem Namen");
        helper.assertItemEntityNotPresent(FnItems.CONNECTOR.get(), cable, 2.0);
        helper.succeed();
    }

    /**
     * What you get caught on is the connector too.
     *
     * <p>Without a hit surface of its own you would reach right through it:
     * the click would hit the cable behind, and nothing could be named.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bapartHasItsOwnHitBox(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);

        var shape = helper.getBlockState(cable).getShape(
                helper.getLevel(), helper.absolutePos(cable));
        helper.assertTrue(shape.min(Direction.Axis.Z) > 0.0,
                "ohne Anschluss endet das Kabel vor der Blockkante");

        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH);

        var withPart = helper.getBlockState(cable).getShape(
                helper.getLevel(), helper.absolutePos(cable));
        helper.assertValueEqual(withPart.min(Direction.Axis.Z), 0.0,
                "mit Anschluss reicht die Trefferfläche bis an die Blockkante");
        helper.succeed();
    }

    /**
     * Two connectors on one block are one point in the picture.
     *
     * <p>The analyser draws points into space, and a cable block is
     * <b>one</b> point — even if six connectors hang on it. Two nodes at the
     * same spot would mean two labels on top of each other: neither would be
     * readable.
     *
     * <p>So the point names both.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void btwopartsAreOnePointInThePicture(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);
        helper.setBlock(cable.south(), Blocks.CHEST);

        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("erzkiste");
        bus.addPart(Direction.SOUTH).setLabel("kohlekiste");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        BlockPos here = helper.absolutePos(cable);
        var atCable = data.nodes().stream()
                .filter(node -> node.pos().equals(here))
                .toList();

        helper.assertValueEqual(atCable.size(), 1, "ein Punkt je Stelle");
        String label = atCable.get(0).label();
        helper.assertTrue(label.contains("erzkiste") && label.contains("kohlekiste"),
                "und er nennt beide Namen: " + label);
        helper.succeed();
    }

    // ---- The network state at the connector ----------------------------------

    /**
     * A connector knows how things stand for it.
     *
     * <p>Four states have looked the same in the game so far: named and
     * reachable, without a name, assigned twice, without a free channel.
     * Whoever takes the last for a typo searches for a long time — and
     * whoever stands in front of it sees nothing on the block.
     *
     * <p>The state is a shadow of the graph and no truth of its own: the
     * controller stamps it on rebuild, it is computed there.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bstatusTellsTheNetworkState(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("kiste_1");
        bus.addPart(Direction.SOUTH).setLabel("");
        bus.addPart(Direction.UP).setLabel("doppelt");
        bus.addPart(Direction.DOWN).setLabel("doppelt");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(bus.partAt(Direction.NORTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.ONLINE,
                "benannt und erreichbar");
        helper.assertValueEqual(bus.partAt(Direction.SOUTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.UNNAMED,
                "ohne Namen");
        helper.assertValueEqual(bus.partAt(Direction.UP).state(),
                dev.devpanda.factorynetwork.network.DeviceState.DUPLICATE,
                "doppelt vergeben");
        helper.assertValueEqual(bus.partAt(Direction.DOWN).state(),
                dev.devpanda.factorynetwork.network.DeviceState.DUPLICATE,
                "und der zweite genauso");
        helper.succeed();
    }

    /**
     * On the network and not on the network are two different things.
     *
     * <p><b>Until 29 Aug there were three.</b> In between lay "in the
     * network, but without a free channel" — a device that was reachable and
     * still stayed silent. With throughput this case no longer exists:
     * whoever is reachable works.
     *
     * <p>What remains is the difference that really counts: connected or
     * not.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bstatusTellsCutOffFromWithoutAnetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        var bus = busAt(helper, cable);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("dabei");
        bus.addPart(Direction.SOUTH).setLabel("auch_dabei");

        // And one that has nothing to do with the network.
        BlockPos allein = new BlockPos(1, 1, 1);
        helper.setBlock(allein, FnBlocks.CABLE.get());
        var fern = busAt(helper, allein);
        if (fern == null) {
            return;
        }
        fern.addPart(Direction.NORTH).setLabel("einsam");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Both on the same thin cable — and both online. Previously the
        // second would have gone empty as soon as the first filled the strand.
        helper.assertValueEqual(bus.partAt(Direction.NORTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.ONLINE,
                "der erste hängt nicht am Netz");
        helper.assertValueEqual(bus.partAt(Direction.SOUTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.ONLINE,
                "der zweite am selben Kabel hängt nicht am Netz");
        helper.assertValueEqual(fern.partAt(Direction.NORTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.OFFLINE,
                "gar nicht am Netz");
        helper.succeed();
    }

    /**
     * Whoever drops out of the network learns of it too.
     *
     * <p>This is the spot where a state as a shadow can go wrong: a
     * connector the graph no longer knows is told nothing by anybody any
     * more — and would stand on its last state forever. A green lamp on a
     * cut-off device would be worse than none at all.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bstatusGoesOfflineWhenTheCableIsCut(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        BlockPos mitte = controller.east();
        BlockPos ende = mitte.east();
        helper.setBlock(mitte, FnBlocks.CABLE.get());
        helper.setBlock(ende, FnBlocks.CABLE.get());
        helper.setBlock(ende.north(), Blocks.CHEST);

        var bus = busAt(helper, ende);
        if (bus == null) {
            return;
        }
        bus.addPart(Direction.NORTH).setLabel("kiste_1");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(bus.partAt(Direction.NORTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.ONLINE,
                "am Netz und benannt");

        // The line in between gone — the connector hangs on nothing any more.
        helper.destroyBlock(mitte);
        entity.rebuildNetwork();

        helper.assertValueEqual(bus.partAt(Direction.NORTH).state(),
                dev.devpanda.factorynetwork.network.DeviceState.OFFLINE,
                "abgeschnitten, und das muss dranstehen");
        helper.succeed();
    }

    /**
     * The cable grows an arm to its connector.
     *
     * <p>Up to here the opposite rule applied: a face with a connector did
     * not connect, so that no arm ran right through the plate. The price was
     * a grey stalk between plate and core — a foreign body in a line that
     * otherwise runs through everywhere.
     *
     * <p>Now the arm itself carries what the stalk carried. That creates a
     * visible junction at the cable, and the line ends where it belongs: at
     * the connector.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void bacableGrowsAnArmToItsPart(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), Blocks.CHEST);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(FnItems.CONNECTOR.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(cable).useItemOn(stack, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                hitOn(helper, cable, Direction.NORTH));

        helper.assertTrue(helper.getBlockState(cable).getValue(
                        dev.devpanda.factorynetwork.block.CableBlock.connection(Direction.NORTH)),
                "das Kabel muss einen Arm zum Anschluss wachsen lassen");
        // To the south lies nothing — there it stays at the bare core.
        helper.assertFalse(helper.getBlockState(cable).getValue(
                        dev.devpanda.factorynetwork.block.CableBlock.connection(Direction.SOUTH)),
                "und nur dorthin");
        helper.succeed();
    }
}
