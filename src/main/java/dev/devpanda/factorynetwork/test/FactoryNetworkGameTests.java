package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Prüft in einer echten Welt, was Einheitstests nicht können: dass Blöcke
 * zusammenfinden, dass der Graph die Connectoren sieht und dass ein Worker
 * wirklich Gegenstände bewegt.
 *
 * <p>Aufbau in allen Tests: Controller, ein Kabel, zwei Connectoren, davor je
 * eine Kiste. Dieselbe Anordnung, die auch im Konzept als Beispiel steht.
 */
@GameTestHolder(FactoryNetwork.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactoryNetworkGameTests {

    private static final String EMPTY = "empty";

    /** Setzt den Aufbau und liefert die Position des Controllers. */
    private static BlockPos buildSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // Kabel nach Osten, daran zwei Connectoren.
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        BlockPos sourceConnector = cable.north();
        BlockPos targetConnector = cable.south();
        helper.setBlock(sourceConnector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING, Direction.NORTH));
        helper.setBlock(targetConnector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING, Direction.SOUTH));

        helper.setBlock(sourceConnector.north(), Blocks.CHEST);
        helper.setBlock(targetConnector.south(), Blocks.CHEST);

        name(helper, sourceConnector, "quarry_output");
        name(helper, targetConnector, "depot");
        return controller;
    }

    private static void name(GameTestHelper helper, BlockPos pos, String label) {
        if (helper.getBlockEntity(pos) instanceof ConnectorBlockEntity connector) {
            connector.setLabel(label);
        } else {
            helper.fail("Am Connector hängt keine BlockEntity", pos);
        }
    }

    private static ControllerBlockEntity controllerAt(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            return controller;
        }
        helper.fail("Am Controller hängt keine BlockEntity", pos);
        throw new IllegalStateException();
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
        // Ein dritter Connector ohne Namen darf im Netz nicht auftauchen.
        BlockPos extra = controller.east().above();
        helper.setBlock(extra, FnBlocks.CONNECTOR.get());

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

        // Der Worker braucht ein paar Ticks, dann muss das Erz im Speicher sein.
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

    private FactoryNetworkGameTests() {
    }
}
