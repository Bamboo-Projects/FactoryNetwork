package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DeviceScan;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.network.Power;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import dev.devpanda.factorynetwork.registry.FnBlocks;
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
        rackWithServer(helper, controller.west());

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

        // Ein Laufwerk mit großer Zelle: Seit es Zellen gibt, lagert ein Netz
        // ohne Laufwerk nichts. Wer den Speicher selbst prüft, nimmt
        // bareSetup und stellt sich sein Laufwerk hin.
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        return controller;
    }

    /** Derselbe Aufbau ohne Laufwerk — für die Prüfungen am Speicher selbst. */
    private static BlockPos bareSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
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

    /** Wie viel in der Kiste an dieser Stelle liegt, über alle Fächer. */
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
     * Füllt den Stromvorrat und fährt das Netz hoch.
     *
     * <p>In der Prüfwelt steht kein Generator. Ohne diesen Griff wäre jede
     * Prüfung eine über den Stromausfall — und keine über das, was sie
     * eigentlich prüfen will. Gegangen wird der echte Weg: erst füllen, dann
     * die Hochfahrzeit abwarten.
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

        // Ein Redstoneblock neben dem Connector gibt Signal.
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

        // Der Speicher ist leer, die Bedingung verlangt mehr als 100.
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

        // Zwei verschiedene Baumstämme — derselbe Tag.
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

        // Genau der Fall aus AllTheOres: Die Steinart steht als Vorsilbe,
        // die Form als Nachsilbe. Ein Muster nur am Rand fände nicht beides.
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

        // Zwei Arten im selben Tag. Bei "insgesamt" kämen zusammen 8 an,
        // bei "je Art" acht von jeder — das unterscheidet die beiden Lesarten.
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

        // Vorher fiel eine Auswahl, die kein einzelner Gegenstand ist, still
        // auf "alles" zurück — das Erz wäre mitgewandert.
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
        // Beide Connectoren auf denselben Namen setzen.
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

        // quarry_output und depot sind vergeben; ein Ofen soll furnace_1
        // heissen, und beim zweiten Mal furnace_2.
        entity.rebuildNetwork();
        String first = ConnectorNaming.nextFree("furnace", entity.graph());
        helper.assertValueEqual(first, "furnace_1", "erster freier Name");

        // Jetzt furnace_1 belegen — der Vorschlag muss weiterzählen.
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

        // Zerlegte Form setzen, wie sie manche Texteingabe liefert.
        String decomposed = "ofen_su\u0308d";
        if (helper.getBlockEntity(connector) instanceof ConnectorBlockEntity entity) {
            entity.setLabel(ConnectorNaming.normalize(decomposed));
        }

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
        controllerEntity.rebuildNetwork();

        // Gesucht wird mit der zusammengesetzten Form.
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

        // Die Anzeige kann veraltet sein: Wer 64 anfordert, bekommt die 3,
        // die wirklich da sind — und nicht mehr.
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

        // Ein Eintrag aus einer Mod, die es nicht mehr gibt, darf beim Lesen
        // nicht als Luft im Bestand landen. Der Bestand liegt jetzt in der
        // Zelle, also wird dort geprüft.
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

        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(cell);
        helper.assertValueEqual(inhalt.size(), 1,
                "nur der bekannte Gegenstand darf überleben");
        helper.assertValueEqual(inhalt.getOrDefault(Items.IRON_INGOT, 0L), 5L, "Bestand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cablesOfDifferentColoursDoNotConnect(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Grüner Strang zum ersten Connector
        BlockPos green = controller.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        BlockPos reachable = green.east();
        helper.setBlock(reachable, FnBlocks.CONNECTOR.get());
        name(helper, reachable, "erreichbar");

        // Roter Strang, an den grünen angesetzt — darf nicht durchleiten
        BlockPos red = green.above();
        helper.setBlock(red, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.RED));
        BlockPos hidden = red.east();
        helper.setBlock(hidden, FnBlocks.CONNECTOR.get());
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

        // Standardfarbe zwischen Controller und einem blauen Strang
        BlockPos plain = controller.east();
        helper.setBlock(plain, FnBlocks.CABLE.get());
        BlockPos blue = plain.east();
        helper.setBlock(blue, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.BLUE));
        BlockPos target = blue.east();
        helper.setBlock(target, FnBlocks.CONNECTOR.get());
        name(helper, target, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isPresent(),
                "Die Standardfarbe muss sich mit jeder Farbe verbinden");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aBundleDoesNotBridgeColours(GameTestHelper helper) {
        // Der eigentliche Test: Ein Bündel darf zwei gleichfarbige Stränge
        // nicht über eine fremde Farbe hinweg verbinden.
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Grün vom Controller weg
        BlockPos green = controller.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));

        // Ein Bündel mit nur Rot dahinter — Grün endet hier
        BlockPos redOnly = green.east();
        helper.setBlock(redOnly, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.RED));

        BlockPos behind = redOnly.east();
        helper.setBlock(behind, FnBlocks.CONNECTOR.get());
        name(helper, behind, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isEmpty(),
                "Grün darf nicht über einen roten Block hinweg weiterlaufen");
        helper.succeed();
    }

    /** Legt eine Reihe Kabel und hängt an jedes Ende einen Connector. */
    private static void line(GameTestHelper helper, BlockPos from, int length) {
        for (int i = 0; i < length; i++) {
            helper.setBlock(from.east(i), FnBlocks.CABLE.get());
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aCableCarriesSixteenChannels(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Eine Kabelreihe nach Osten, darüber und darunter je vier Connectoren
        line(helper, controller.east(), 5);
        int placed = 0;
        for (int i = 0; i < 5 && placed < 9; i++) {
            for (BlockPos side : new BlockPos[]{
                    controller.east(i + 1).above(), controller.east(i + 1).below()}) {
                if (placed >= 9) {
                    break;
                }
                helper.setBlock(side, FnBlocks.CONNECTOR.get());
                name(helper, side, "gerät_" + placed);
                // Zwei Kanäle je Gerät: Neun Geräte wollen achtzehn, das
                // Kabel trägt sechzehn. Das neunte geht leer aus.
                if (helper.getBlockEntity(side) instanceof ConnectorBlockEntity connector) {
                    connector.setChannelCost(2);
                }
                placed++;
            }
        }
        helper.assertValueEqual(placed, 9, "aufgestellte Geräte");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.graph().starvedConnectors().size(), 1,
                "Geräte ohne Kanal");
        helper.assertValueEqual(entity.graph().connectorNames().size(), 8,
                "Geräte mit Kanal");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void loadIsCountedPerStrand(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        line(helper, controller.east(), 3);

        // Zwei Geräte am Ende der Reihe — über und unter dem letzten Kabel,
        // nicht auf ihm: Ein Connector an dessen Stelle nähme ihm den Platz.
        BlockPos last = controller.east(3);
        helper.setBlock(last.above(), FnBlocks.CONNECTOR.get());
        name(helper, last.above(), "ende_0");
        helper.setBlock(last.below(), FnBlocks.CONNECTOR.get());
        name(helper, last.below(), "ende_1");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Beide Wege laufen über das erste Kabel: dort zwei Kanäle belegt.
        int atStart = entity.graph().channelLoad(
                helper.absolutePos(controller.east(1)), CableColour.NONE);
        helper.assertValueEqual(atStart, 2, "Kanäle auf dem ersten Kabel");
        helper.assertValueEqual(entity.graph().channelsFree(helper.getLevel(),
                helper.absolutePos(controller.east(1)), CableColour.NONE),
                dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_THIN - 2,
                "freie Kanäle dort");
        helper.succeed();
    }

    /** Baut Controller, Kabelreihe und drei benannte Kisten. */
    private static ControllerBlockEntity threeChests(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 4; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.CABLE.get());
        }
        for (int i = 0; i < 3; i++) {
            BlockPos connector = controller.east(i + 2).above();
            helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                            net.minecraft.core.Direction.UP));
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
            // Reihum heißt: Nicht alles landet in derselben Kiste.
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
        // Erst das Netz aufbauen: Ohne das kennt der Speicher sein Laufwerk
        // nicht, und dann lagert er nichts.
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
            // 1234 wird zu 1,2k gekürzt — große Zahlen sind sonst nicht zu lesen
            helper.assertTrue(lines.get(1).contains("1,2k"),
                    "Der Bestand fehlt: " + lines.get(1));
            helper.succeed();
        });
    }

    /**
     * {@code scale} macht die Schrift größer, und die Zeile bleibt eine Zeile.
     *
     * <p>Der Maßstab gehört zur Tafel und nicht in ihren Text: Er geht mit
     * den Zeilen hinüber, damit der Client die Sprache nicht kennen muss —
     * aber er schreibt nichts hin.
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

    /** Ein unsinniger Maßstab wird auf das Machbare gezogen. */
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
        // Null wäre eine unsichtbare Tafel, tausend ein Buchstabe über der
        // halben Wand. Beides ist kein Fehler im Programm, sondern eine Zahl,
        // die niemand so gemeint hat.
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
                // Eine leere Fläche ließe offen, ob das Netz steht oder der
                // Name falsch ist. Das Display sagt es selbst.
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

        if (helper.getBlockEntity(connector) instanceof ConnectorBlockEntity emitter) {
            helper.assertValueEqual(emitter.emittedRedstone(), 15, "gesetzte Stärke");
        } else {
            helper.fail("Kein Connector", connector);
            return;
        }
        // Und der Block gibt es auch wirklich nach außen weiter.
        helper.assertTrue(helper.getLevel().getBestNeighborSignal(
                helper.absolutePos(connector.above())) > 0,
                "Das Signal muss beim Nachbarn ankommen");

        entity.callFunction("alarm", java.util.List.of(
                new dev.devpanda.factorynetwork.runtime.Value.Int(0)));
        if (helper.getBlockEntity(connector) instanceof ConnectorBlockEntity afterwards) {
            helper.assertValueEqual(afterwards.emittedRedstone(), 0, "wieder aus");
        }
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
        // Die Zahl von vor dem Warten muss den Halt überstehen.
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

        // await steckt hier in einem if in einer while — genau die
        // Verschachtelung, an der sich das Wiederfinden der Rahmen bewährt.
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
     * {@code network.power} liest den Vorrat des laufenden Netzes.
     *
     * <p>Die Rechnung dahinter steht im Einheitstest; hier geht es um die
     * <b>Verdrahtung</b>. Der Controller hält den Vorrat, der Ausdruck fragt
     * den Host, und ein vergessenes {@code setPower} fiele sonst nirgends
     * auf: Ohne Welt meldet sich der Ausdruck ehrlich, mit Welt aber sähe man
     * dieselbe Meldung und hielte sie für richtig.
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
     * Und ein Worker darf danach fragen: {@code when network.power > …}.
     *
     * <p>Das Handbuch verspricht genau diese Zeile — ein Worker, der aufhört,
     * bevor das Netz ausgeht. Sie hängt an derselben Verdrahtung wie oben,
     * aber an einem anderen Weg dorthin: Die Bedingung eines Workers wertet
     * die Laufzeit aus, nicht ein Ablauf.
     *
     * <p><b>Geprüft wird der Status und nicht nur der Stillstand.</b> Ein
     * Worker, der die Bedingung gar nicht auswerten kann, steht auch still —
     * dann aber auf {@code HALTED}. Der Unterschied ist der ganze Punkt.
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

        // Eine Schwelle, die kein Netz erreicht: Der Worker muss sie ablesen
        // können und darf trotzdem nicht arbeiten.
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

    /** Und {@code network.capacity}, wie viel hineinpasst. */
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
     * {@code hebel.click()} fasst die Maschine an, auf die der Connector zeigt.
     *
     * <p>Manche Maschinen tun nichts, bis jemand sie anfasst — und für die
     * gab es bisher keinen Griff. <b>Kein zweiter Block:</b> Ein- und Ausgang
     * trennt hier schon der Code und nicht die Bauform, und für eine dritte
     * Fähigkeit gilt dasselbe.
     *
     * <p>Geprüft an einem Hebel, und das ist Absicht: Er ist die einzige
     * Vanilla-Antwort auf einen Klick, die man <b>sehen</b> kann, ohne ein
     * Fenster zu öffnen — sein Zustand kippt, und das steht im Block.
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
     * Und an einem Block, den ein Klick nicht interessiert, meldet er das.
     *
     * <p>Kein Fehler: Ein Stein, der auf einen Rechtsklick nicht reagiert,
     * ist kein kaputtes Programm. Aber {@code false} statt {@code true},
     * damit ein Ablauf, der auf Wirkung wartet, das merkt.
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
     * Eine fremde Leitung darf am Controller <b>ziehen</b>, nicht nur einspeisen.
     *
     * <p>Bisher nahm der Anschluss nur an. Wer Strom aus dem Netz in ein
     * anderes System wollte, brauchte einen Worker in einen Energiewürfel und
     * daran den fremden Anschluss — und genau dieser Umweg kostet
     * Übertragungsrate, denn der Würfel hat seine eigene.
     *
     * <p>Jetzt geht es direkt: Ein Flux Plug, ein Kabel, ein Verbraucher zieht
     * am Controller. <b>Ohne Ratengrenze</b> — die galt der Aufnahme, und für
     * die Abgabe wäre sie genau das, was hier stört.
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
     * Und er hört auf, bevor das Netz sich selbst abschaltet.
     *
     * <p>Die eine Grenze, die bleibt — und sie ist keine Rate, sondern ein
     * Boden. Ohne ihn zöge eine fremde Leitung das Netz bis unter die
     * Anlaufschwelle, es ginge aus, führe drei Sekunden hoch, ginge wieder
     * aus: ein Flackern, das wie ein Fehler aussieht und keiner ist.
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
     * {@code store kiste_1 { }} — was in der Kiste liegt, gehört dem Netz.
     *
     * <p>Der Speicherbus, wie AE2 ihn hat, nur ohne eigenen Block. Der
     * Unterschied zu einem gewöhnlichen Gerät ist der ganze Punkt: Ohne diese
     * Zeile ist die Kiste etwas, aus dem man mit {@code move} holt; mit ihr
     * zählt ihr Inhalt zum Bestand, den jeder Auftrag und jede Anzeige sieht.
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
        helper.assertTrue(entity.storage().contents().containsKey(Items.IRON_ORE),
                "und steht im Bestand, den das Terminal zeigt");
        helper.succeed();
    }

    /**
     * Und was jemand hineinlegt, sieht das Netz beim nächsten Blick.
     *
     * <p>Der Unterschied zwischen einer Kopie und einer Sicht: Eine Kiste
     * ändert sich ohne das Netz — ein Spieler räumt sie aus, ein Trichter
     * füllt sie. Ein Bestand, der das erst nach einem Neuaufbau merkt, wäre
     * falsch, und ein Auftrag, der darauf rechnet, hinterließe halbe Arbeit.
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
     * Was das Netz einlagert, darf in der Kiste landen.
     *
     * <p>Die andere Hälfte des Speicherbusses, und ohne sie wäre die erste
     * gefährlich: Ein Bestand, den man sieht und nicht anfassen kann, bringt
     * jeden Auftrag durcheinander, der damit rechnet.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void astoreTakesWhatTheNetworkPutsAway(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Kein Laufwerk, keine Zelle — die Kiste ist der ganze Speicher.
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

    /** Und das Netz holt es dort auch wieder heraus. */
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
     * Ein {@code filter} sagt, was in die Kiste darf.
     *
     * <p>Wie in AE2: Der Bus nimmt nur an, was dasteht. Was schon drinliegt,
     * zählt trotzdem zum Bestand — es zu verschweigen, weil es nicht zum
     * Filter passt, wäre eine Lüge über etwas, das jeder sehen kann.
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

    /** Was schon drinliegt, zählt auch gegen den Filter. */
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

    /** Ein Programm mit await in if in while — die Vorlage der Ablauf-Tests. */
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

        // Dasselbe Programm noch einmal übernehmen stellt einen Neustart nach:
        // aufschreiben, Maschine wegwerfen, zurücklesen.
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

        // Eine Zeile mehr verschiebt alles dahinter — der Zähler des Ablaufs
        // zeigt dann auf die falsche Anweisung.
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

        // Ein STALE-Ablauf rührt sich nicht mehr von selbst.
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

        // Gleiche Zeilen, andere Rechnung: Die Stellen bleiben, wo sie waren.
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

        // Der Weg, den ein Serverneustart nimmt: Die BlockEntity schreibt sich
        // auf die Platte, und beim Laden entsteht aus dem Tag eine neue.
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

        // Eine neue Funktion hinten dran: Das Programm ist ein anderes, die
        // Stellen des wartenden Ablaufs sind aber unberührt. Genau der Fall,
        // für den es die Wahl gibt.
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

        // Der Weg, den ein Spieler nimmt: emit steht in seinem Programm, nicht
        // in einem Java-Aufruf.
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

        // Ein on-Block, der selbst wartet — das ging nicht, solange Ereignisse
        // im Interpreter zu Ende liefen.
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

        // Allerlei Wertarten, damit der Weg über die Platte nicht nur mit
        // Zahlen belegt ist.
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

        // Der eigentliche Zweck der Sache: für jede Maschine etwas anstoßen
        // und auf ihre Rückmeldung warten, bevor die nächste drankommt.
        //
        // Genau drei Sorten im Speicher, und die Schleife läuft bis ans Ende.
        // Vorher lief sie über tag:minecraft/planks und brach nach drei
        // Runden ab — dass sie überhaupt endet, prüfte damit niemand.
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

        // Die Schleife läuft bis ans Ende der Liste und gibt zurück, wie oft
        // sie herumkam.
        //
        // <b>Verglichen wird mit einem Lauf ohne Neustart.</b> Vorher stand
        // hier ein „break" nach drei Runden — damit war die Stelle in der
        // Liste von außen unsichtbar: Sprang der Zeiger beim Laden auf null
        // zurück, kamen trotzdem drei Runden heraus. Der Test blieb grün,
        // auch wenn der Stand gar nicht mitgeschrieben wurde. Wie lang die
        // Liste ist, muss dafür niemand wissen — nur, dass beide Läufe
        // dieselbe Zahl ergeben.
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
        // Erst der Tick führt den geweckten Lauf wirklich weiter. Ohne ihn
        // stünde der Zeiger beim Speichern noch auf dem ersten Eintrag, und
        // ein Rücksprung auf null wäre gar kein Unterschied.
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

        // Wäre der Stand des Laufs nicht mitgeschrieben, begänne die Liste von
        // vorn — und käme auf mehr Runden als der ungestörte Lauf.
        long gezaehlt = runToEnd(helper, geladen, wieder);
        helper.assertValueEqual(gezaehlt, erwartet,
                "Über den Neustart hinweg dieselbe Zahl Runden wie ohne");
        helper.succeed();
    }

    /**
     * Taktet, bis der Lauf fertig ist, und liefert sein Ergebnis.
     *
     * <p>Wie lang die Liste ist, muss der Test nicht wissen — nur, dass sie
     * endet. Die Grenze fängt eine Schleife ab, die sich nicht aufbraucht.
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

        // Kein Ablauf, bevor jemand drückt.
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

        // Die Überschrift ist kein Knopf, und eine Nummer daneben gibt es nicht.
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
        // Sonst hinge das Verhalten einer Funktion davon ab, wer sie ruft.
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
     * Zwei Anlagen an einem Kabel — die zweite mit einem fehlenden Gerät.
     *
     * <p>Die Namen tragen den Anlagennamen vorn: So und nicht anders entsteht
     * eine gebaute Anlage.
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
            helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                            net.minecraft.core.Direction.UP));
            helper.setBlock(connector.above(), Blocks.CHEST);
            name(helper, connector, labels[i]);
        }
        driveWithCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);
        driveWithFluidCell(helper, controller.below(),
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64);
        return controllerAt(helper, controller);
    }

    /** Die Kiste über dem Connector an dieser Stelle. */
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

        // In der Vorlage steht "eingang" — gemeint ist werk_1/eingang.
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

        // werk_2 fehlt der Ausgang. Ein halb durchlaufener Aufruf wäre
        // schlimmer als einer, der gar nicht erst beginnt.
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
        // Im Spiel besorgt das der Tick: Ohne Netz kennt der Controller keine
        // Geräte, und die Anlage wäre nicht wiederzufinden.
        geladen.rebuildNetwork();

        var wieder = flowOf(geladen, flow.id());
        helper.assertTrue(wieder != null, "Der Ablauf der Anlage ist verloren gegangen");
        tick(helper, geladen, 7);

        helper.assertValueEqual(wieder.status().name(), "DONE",
                "Er läuft zu Ende, sagt aber: " + wieder.detail());
        helper.assertValueEqual(resultOf(wieder), 7L, "Mit dem Wert aus dem Ereignis");
        // Ohne den mitgeschriebenen Anlagennamen wüsste er nicht mehr, wohin.
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

        // Ein neuer Connector am Kabel: Das Netz kennt ihn beim nächsten Aufbau.
        BlockPos weiterer = controller.east().above();
        helper.setBlock(weiterer, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                        net.minecraft.core.Direction.UP));
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

        // Beim allerersten Aufbau ist nichts dazugekommen — es war nur vorher
        // nichts bekannt.
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

        // Eine Auswahl, die nichts trifft, darf nicht "kein Filter" bedeuten.
        helper.assertValueEqual(
                ((ChestBlockEntity) helper.getBlockEntity(quelle)).getItem(0).getCount(), 64,
                "Steine haben mit Wasser nichts zu tun");
        helper.succeed();
    }

    /**
     * Zwei Kessel am Kabel.
     *
     * <p>NeoForge gibt jedem Kessel einen Tank, und damit gibt es ein
     * Prüfstück für Flüssigkeiten ohne eigenen Block. Ein Kessel fasst
     * 1000 Millibucket — genau einen Eimer.
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
            helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                            net.minecraft.core.Direction.UP));
            helper.setBlock(connector.above(), Blocks.CAULDRON);
            name(helper, connector, labels[i]);
        }
        // Seit Flüssigkeiten in Zellen liegen, lagert ein Netz ohne Laufwerk
        // auch keine Flüssigkeit mehr — genau wie bei den Gegenständen.
        driveWithFluidCell(helper, controller.above(),
                dev.devpanda.factorynetwork.storage.FluidCellTier.B64);
        return controllerAt(helper, controller);
    }

    /** Füllt einen Kessel mit Wasser. */
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

        // Der Bestand liegt jetzt in der Zelle, nicht mehr im Controller —
        // also wird das Laufwerk gesichert und zurückgelesen. Beim Sichern
        // muss der Inhalt aus dem Arbeitsspeicher in den Gegenstand; ohne das
        // wäre er nach einem Neustart der von vorhin.
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
                .read(geladen.cell(0));
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

        // Ohne filter ist das ein Gegenstands-Worker — und der findet an einem
        // Kessel kein Inventar. Die Meldung muss davon sprechen, nicht schweigen.
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
     * Schreibt ein Paket und liest es zurück.
     *
     * <p>Ein Codec mit der falschen Zahl an Feldern übersetzt anstandslos und
     * bricht erst, wenn jemand das Terminal öffnet. Diese Prüfung fängt das
     * ab, ohne dass ein Spieler dafür anwesend sein muss.
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

        // Das Profil kommt als flache Liste über die Leitung und muss drüben
        // wieder dasselbe Gerät sein — samt seitenlosem Zugang.
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
                        .StorageSnapshotPacket.Entry(Items.IRON_ORE, 320)),
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

        // Die Fertigung geht denselben Weg über die Leitung. Im Einzelspieler
        // fällt ein kaputter Codec nicht auf, auf einem Server sofort.
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
                        dev.devpanda.factorynetwork.analyser.AnalyserData.NodeState.STARVED,
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

        // Was das Terminal zu sehen bekommt, entsteht hier. Ohne diese Prüfung
        // fiele ein Fehler darin erst auf, wenn jemand das Terminal öffnet.
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
        // Die Reihenfolge kommt aus einer Menge und ist nicht zugesagt.
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
        // Eine Schleife über nichts sieht aus wie eine, die nichts zu tun
        // hatte — und ist damit der schlimmste Fall.
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
        // Ohne Signal ist das Lämpchen aus, und die Prüfung darauf beweist
        // nichts. Ein Redstoneblock neben depot macht die Frage erst zu einer.
        helper.setBlock(controller.east().south().above(), Blocks.REDSTONE_BLOCK);

        // Was in beispiele.md steht, muss nicht nur übersetzen, sondern laufen.
        // Ein Beispiel mit einem Methodennamen, den es nicht gibt, ist
        // schlimmer als keines.
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
        // Der Balken zeichnet immer zehn Blöcke, und das Label steht auch an
        // einem dunklen Lämpchen: contains("█") und contains("Depot") trafen
        // beide auch dann, wenn gar nichts ausgewertet wurde. Geprüft wird
        // deshalb die Grenze zwischen hell und dunkel — ein halber Balken
        // trägt sein §8 in der Mitte, ein leerer gleich am Anfang.
        helper.assertTrue(zeilen.get(2).contains("§a█████§8█████"),
                "320 von 640 sind ein halber Balken: " + zeilen.get(2));
        helper.assertTrue(zeilen.get(2).contains("50 %"),
                "Neben dem Balken steht sein Anteil: " + zeilen.get(2));
        helper.assertTrue(zeilen.get(3).startsWith("§a●"),
                "Der Redstoneblock liegt daneben, das Lämpchen muss leuchten: "
                        + zeilen.get(3));

        // Der Knopf und der Ereignisblock laufen wirklich.
        // Eine Variable als Auswahl in move — steht so in beispiele.md.
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

        // Eingebaute Ereignisse werden nicht deklariert — wie redstone_changed.
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
        // Erst muss einmal hingeschaut worden sein: Beim ersten Blick wird
        // nichts gemeldet, denn da hat sich nichts geändert — es war nur
        // nichts bekannt.
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
     * Eine Gruppe ist ein Wert: Sie nennt ihre Mitglieder und nimmt an.
     *
     * <p>Im GameTest, weil beides am Netz hängt — welche Geräte in der Gruppe
     * sind, entscheidet die Welt und nicht das Programm.
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
     * Eine Menge vor einem Vorlagennamen bewegt auch wirklich etwas.
     *
     * <p>Dass {@code send(64 erze)} übersetzt, sagt der Prüfer für die
     * Doku-Beispiele. Ob der Interpreter den Namen an dieser Stelle auch
     * <b>auflöst</b>, sagt er nicht — er übersetzt nur. Genau dort lag der
     * Fehler vorher: Das Beispiel stand in {@code beispiele.md} und ließ sich
     * nicht einmal lesen.
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

    /** Ein Festwert wird gelesen wie ein globaler, nur nie geschrieben. */
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
     * Eine globale Liste übersteht den Neustart.
     *
     * <p>Der Grund, warum es sie überhaupt gibt: Eine Warteschlange, die beim
     * Serverneustart verschwindet, ist keine. Geprüft wird der ganze Weg —
     * anhängen über eine Zuweisung, speichern, zurücklesen.
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
     * Eine Liste als Festwert lässt sich nicht ändern.
     *
     * <p><b>Das ist geschenkt</b> und der Grund für die Entscheidung: Weil
     * Anhängen eine Zuweisung ist, bewacht dieselbe Prüfung, die
     * {@code stapel = 65} meldet, auch {@code sorten = sorten.plus(…)}. Ein
     * änderndes {@code add} liefe daran vorbei.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aconstListCannotBeChanged(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Erst die Gegenprobe: Dasselbe Programm ohne die Zuweisung geht
        // durch. Ohne sie wäre der Test auch dann grün, wenn schon das
        // Listenliteral nicht übersetzt — und geprüft wäre gar nichts.
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
     * Ein Posten aus dem Bestand kennt seine Art und seine Menge.
     *
     * <p>Im GameTest, weil eine Art ohne Registry keine ist: Der Einheitstest
     * kann nur prüfen, was ohne Welt zu prüfen ist.
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
     * {@code gerät.count(…)} zählt das Gerät und nicht den Speicher.
     *
     * <p>Der Netzspeicher bleibt im Test ausdrücklich leer. Läge in beiden
     * dasselbe, zeigte der Test nichts — er könnte die Verwechslung nicht von
     * der richtigen Antwort unterscheiden.
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
     * <b>Eine Auswahl, die nichts trifft, darf nicht alles bewegen.</b>
     *
     * <p>Eine leere Liste heißt für {@code move} „kein Filter", und kein
     * Filter heißt „alles". Solange der Interpreter die Ausnahme wegwarf,
     * ging ein vertippter Tag über den Weg der geschriebenen Auswahl und
     * meldete sich; seit er auflöst, muss er selbst melden.
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

    /** Eine Menge vor einer Vorlage heißt insgesamt, nicht je Art. */
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
     * <b>Die Ausnahme wirkte nur im Worker.</b> Der Interpreter wertete
     * {@code Expr.Except} als seine Grundlage aus und warf die Ausschlüsse
     * weg — in einem {@code move} stand die Ausnahme also da und tat nichts,
     * obwohl sprache.md sie zeigt.
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

    /** Dieselbe Vorlage wie im Worker, aber in einem move. */
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

    /** Eine Vorlage zählt auch, wenn nur gelesen wird. */
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
     * Ein Worker filtert nach einer Vorlage.
     *
     * <p>Drei Sorten liegen in der Kiste, zwei stehen in der Vorlage, eine
     * davon nimmt sie wieder heraus. Nur so zeigt der Test beides: dass die
     * Vorlage greift und dass ihre Ausnahme wirkt.
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
     * Ein Flüssigkeits-Tag löst sich gegen die Fluid-Registry auf.
     *
     * <p>Vanilla führt {@code minecraft:water} als Tag über Wasser und
     * fließendes Wasser — der einzige, auf den in einer leeren Welt Verlass
     * ist.
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
     * Eine Vorlage legt zusammen und nimmt heraus.
     *
     * <p>Im GameTest und nicht als Einheitstest: Welche Gegenstände hinter
     * einer Auswahl stehen, weiß erst die Registry.
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

    /** Bleibt nach den Ausnahmen nichts übrig, ist das kein stiller Leerlauf. */
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
     * Das Gegenstück zu {@link #aChangedInventoryWakesAWaitingFlow}: Nicht
     * jede Regung, sondern nur, was dazugekommen ist.
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
     * <b>Der Grund, warum das Ereignis eine Grundlinie braucht.</b> Ohne sie
     * meldete das Netz seine eigene Lieferung als Ausgabe — und ein Ablauf,
     * der einlegt und dann wartet, wäre sofort wieder wach, ohne dass die
     * Maschine auch nur angefangen hätte.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void whatTheNetworkPutsInIsNoOutput(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        entity.storage().insert(Items.COBBLESTONE, 64);

        // Im Ziel liegt schon etwas: genau der Fall, an dem die einfache
        // Fassung scheiterte, die gegen leer vergleicht.
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
     * <b>Der Worker schreibt auf eigenem Weg.</b> Er geht nicht durch den
     * Interpreter, sondern legt selbst ein — und wenn diese Stelle die
     * Grundlinie nicht nachzieht, meldet jede Lieferung des Netzes eine
     * Ausgabe, die es nie gab.
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
        // Langsam genug, dass noch geliefert wird, wenn die Grundlinie steht:
        // Ein Worker, der in den ersten zehn Ticks fertig ist, liefe ganz vor
        // dem ersten Blick ab, und der meldet nie etwas. Der Test wäre grün,
        // ohne je etwas geprüft zu haben.
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

    /** Weniger ist nichts Neues: Entnehmen darf nichts auslösen. */
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
     * <b>Kein Einmalschuss.</b> Eine Maschine, die eine Ladung stückweise
     * ausgibt, muss jedes Stück melden — sonst bliebe der Rest in ihr stehen,
     * und genau das ist der Verlust, den das Ereignis vermeiden soll.
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

        // <b>Die einfachste Frage überhaupt, und sie hatte keinen Test.</b>
        // Aufgefallen ist das beim Schärfen eines ganz anderen Tests: Nach
        // dem Einlagern standen drei Sorten im Speicher, nach dem Übernehmen
        // eines Programms auch — nach einem Tick keine mehr.
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

        // Und derselbe Weg noch einmal mit einem Programm, das über den
        // Bestand läuft und dabei wartet — genau die Lage, in der der
        // Bestand einmal leer dastand.
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

        // Der eigentliche Zweck: Wer morgens nachsieht, warum die Anlage
        // nachts stehen blieb, findet die Zeile auch nach einem Neustart.
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

        // Leeren heißt leeren: Wer einen sauberen Anfang für den nächsten
        // Versuch will, soll ihn bekommen — auch nach einem Neustart.
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

        // maintain ohne filter — der Worker weiß nicht, was er vorhalten
        // soll, und sagt es. Bisher sammelte die Laufzeit diesen Hinweis und
        // niemand las ihn.
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

        // <b>Der Weg, den ein Spieler wirklich nimmt.</b> Ein Knopf auf einer
        // Anzeige, ein on-Block, ein await — alles drei läuft über die
        // Ablaufmaschine, und die kannte die globalen Werte nicht. „modus =
        // nacht" in einer Funktion warf „Unbekannter Name modus" und riet
        // dazu, ein let davorzusetzen: Das übersetzt, läuft, meldet nichts
        // und ändert den globalen Wert nicht. Genau dieses Muster steht im
        // Handbuch und in beispiele.md.
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

        // Und über den Knopf, weil das der Weg aus dem Handbuch ist.
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

        // Ohne on-Block — genau so steht es in beispiele.md, und genau so
        // wartete es für immer: Gezählt wurden nur die Blöcke, also wurde gar
        // nicht erst hingesehen, also fiel das Ereignis nie. Ein await ist
        // aber derselbe Zuhörer.
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

        // Ohne on device_changed wird gar nicht erst hingeschaut. Bei fünfzig
        // Connectoren wäre das sonst Arbeit für nichts.
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(entity.flowEngine().flows().size(), 0, "Kein Ablauf");
            helper.assertValueEqual(entity.flowEngine().failed().size(), 0, "Und kein Fehler");
            helper.succeed();
        });
    }

    /**
     * Zwei Kessel als Anlage benannt.
     *
     * <p>Für den Fall, den keine Einzelprüfung abdeckt: eine Vorlage, die
     * Flüssigkeit bewegt und dabei wartet.
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
            helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                            net.minecraft.core.Direction.UP));
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

        // Mitten im Warten neu laden — mit allem, was diese Nacht dazukam:
        // zwei Rahmen tief, in einer Vorlage, vor einer Flüssigkeitsbewegung.
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

    /** Drei Kessel: einer voll, zwei leere als Gruppe. */
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
            helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                    .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                            net.minecraft.core.Direction.UP));
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

        // Ein Kessel fasst genau einen Eimer, also landet alles in einem der
        // beiden — welcher es ist, entscheidet die Verteilung.
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

        // Wasser ist ausgenommen — es bleibt liegen.
        helper.assertTrue(hasWater(helper, controller, 0),
                "Was ausgenommen ist, wird nicht bewegt");
        helper.assertTrue(!hasWater(helper, controller, 1), "Und kommt nirgends an");
        helper.succeed();
    }

    /** Lädt den Controller neu, wie es ein Serverneustart täte. */
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

        // where steht im Programm und nicht im Ablauf: Nach dem Laden muss es
        // sich über den Zähler des Rahmens wiederfinden lassen.
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

        // Die Frist ist absolute Spielzeit — sie läuft über den Neustart hinweg
        // ab, und der else-Zweig steht im Programm, nicht im Ablauf.
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

        // Der Controller selbst und die beiden benannten Connectoren.
        helper.assertTrue(data.nodes().size() >= 3,
                "Zu wenige Knoten: " + data.nodes().size());
        helper.assertTrue(data.nodes().stream().anyMatch(node -> node.state().name()
                        .equals("CONTROLLER")),
                "Der Controller muss ein Knoten sein");
        helper.assertTrue(data.nodes().stream().anyMatch(node ->
                        node.label().equals("quarry_output")),
                "Die Geräte müssen mit Namen erscheinen");

        // Ohne Verbindungen wäre es eine Punktwolke statt eines Netzes.
        helper.assertTrue(!data.links().isEmpty(), "Es muss Verbindungen geben");
        helper.assertValueEqual(data.summary().devices(), 2, "Geräte in der Übersicht");
        helper.assertTrue(data.summary().isHealthy(), "Dieses Netz ist in Ordnung");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theAnalyserMarksDevicesWithoutAChannel(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        line(helper, controller.east(), 5);

        // Mehr Geräte als Kanäle: Die überzähligen müssen auffallen.
        int placed = 0;
        for (int i = 0; i < 5 && placed < 9; i++) {
            for (BlockPos side : new BlockPos[]{
                    controller.east(i + 1).above(), controller.east(i + 1).below()}) {
                if (placed >= 9) {
                    break;
                }
                helper.setBlock(side, FnBlocks.CONNECTOR.get());
                name(helper, side, "gerät_" + placed);
                if (helper.getBlockEntity(side) instanceof ConnectorBlockEntity connector) {
                    connector.setChannelCost(2);
                }
                placed++;
            }
        }
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        helper.assertValueEqual(data.summary().starved(), 1, "Ein Gerät geht leer aus");
        helper.assertTrue(!data.summary().isHealthy(),
                "Ein Netz mit einem hungrigen Gerät ist nicht in Ordnung");
        helper.assertTrue(data.nodes().stream().anyMatch(node ->
                        node.state().name().equals("STARVED")),
                "Das hungrige Gerät muss als solches erscheinen");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theAnalyserMarksFullCables(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        line(helper, controller.east(), 5);
        for (int i = 0; i < 8; i++) {
            BlockPos side = i < 4 ? controller.east(i + 1).above()
                    : controller.east(i - 3).below();
            helper.setBlock(side, FnBlocks.CONNECTOR.get());
            name(helper, side, "gerät_" + i);
            if (helper.getBlockEntity(side) instanceof ConnectorBlockEntity connector) {
                connector.setChannelCost(2);
            }
        }
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        // Acht Geräte zu je zwei Kanälen füllen ein gewöhnliches Kabel genau
        // aus: sechzehn von sechzehn.
        //
        // <b>Vorher stand hier ein ODER über voll und eng</b>, und damit
        // bestand der Test auch, wenn die Strecke nur als „eng" durchging —
        // also genau dann, wenn die Erkennung nicht mehr stimmt. Geprüft wird
        // deshalb die eine Antwort, die richtig ist, und dazu der Gegenfall
        // mit halber Last.
        helper.assertValueEqual(data.summary().fullLinks() > 0, true,
                "Sechzehn von sechzehn Kanälen sind ein volles Kabel");
        helper.assertValueEqual(data.summary().isHealthy(), false,
                "Ein volles Kabel ist kein Netz in Ordnung — es hat keine Reserve");

        // Dieselbe Anlage mit vier Geräten: acht von sechzehn, und nichts ist
        // voll. Ohne diesen Gegenfall bestünde der Test auch, wenn jede
        // Strecke als voll gälte.
        for (int i = 4; i < 8; i++) {
            BlockPos side = controller.east(i - 3).below();
            helper.setBlock(side, Blocks.AIR);
        }
        entity.rebuildNetwork();
        var halb = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        helper.assertValueEqual(halb.summary().fullLinks(), 0,
                "Acht von sechzehn Kanälen sind kein volles Kabel");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aDenseCableCarriesFourTimesAsMuch(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 0; i < 5; i++) {
            helper.setBlock(controller.east(i + 1), FnBlocks.DENSE_CABLE.get());
        }

        // Dieselben neun Geräte zu je zwei Kanälen wie am dünnen Kabel — dort
        // ging eines leer aus, hier trägt das dichte alle mit Leichtigkeit.
        int placed = 0;
        for (int i = 0; i < 5 && placed < 9; i++) {
            for (BlockPos side : new BlockPos[]{
                    controller.east(i + 1).above(), controller.east(i + 1).below()}) {
                if (placed >= 9) {
                    break;
                }
                helper.setBlock(side, FnBlocks.CONNECTOR.get());
                name(helper, side, "gerät_" + placed);
                if (helper.getBlockEntity(side) instanceof ConnectorBlockEntity connector) {
                    connector.setChannelCost(2);
                }
                placed++;
            }
        }
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.graph().starvedConnectors().size(), 0,
                "Am dichten Kabel darf keines leer ausgehen");
        helper.assertValueEqual(entity.graph().connectorNames().size(), 9, "Geräte mit Kanal");
        helper.assertValueEqual(entity.graph().channelsFree(helper.getLevel(),
                        helper.absolutePos(controller.east(1)), CableColour.NONE),
                dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE - 18,
                "Von vierundsechzig sind achtzehn belegt");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theWeakestCableOnThePathDecides(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());

        // Dicht, dann ein gewöhnliches Stück, dann wieder dicht: Der Engpass
        // in der Mitte begrenzt, auch wenn davor und dahinter Platz wäre.
        helper.setBlock(controller.east(1), FnBlocks.DENSE_CABLE.get());
        helper.setBlock(controller.east(2), FnBlocks.CABLE.get());
        for (int i = 3; i <= 5; i++) {
            helper.setBlock(controller.east(i), FnBlocks.DENSE_CABLE.get());
        }

        // Sechs Geräte zu je drei Kanälen wollen achtzehn — alle hinter dem
        // dünnen Stück, das nur sechzehn trägt.
        int placed = 0;
        for (int i = 3; i <= 5 && placed < 6; i++) {
            for (BlockPos side : new BlockPos[]{
                    controller.east(i).above(), controller.east(i).below()}) {
                if (placed >= 6) {
                    break;
                }
                helper.setBlock(side, FnBlocks.CONNECTOR.get());
                name(helper, side, "gerät_" + placed);
                if (helper.getBlockEntity(side) instanceof ConnectorBlockEntity connector) {
                    connector.setChannelCost(3);
                }
                placed++;
            }
        }
        helper.assertValueEqual(placed, 6, "aufgestellte Geräte");
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().starvedConnectors().size() >= 1,
                "Das schwächste Stück muss begrenzen — leer ausgegangen ist aber keines");
        helper.succeed();
    }

    /**
     * Setzt einen Serverschrank mit einem bestückten Einschub daneben.
     *
     * <p>Seit es Serverschränke gibt, rechnet ein Netz ohne einen davon
     * nicht. Fast jede Prüfung braucht deshalb einen — genau wie fast jede
     * ein Laufwerk braucht, seit es Zellen gibt.
     *
     * <p><b>Reichlich bestückt</b>, nämlich mit sechzehn Plätzen: Eine
     * Prüfung soll an dem scheitern, was sie prüft, und nicht daran, dass
     * drei Abläufe gleichzeitig laufen wollten. Wer die Grenze selbst prüft,
     * baut sich einen kleineren Schrank.
     */
    private static final int TEST_CPU = 32;
    private static final int TEST_RAM = 128;
    private static final int TEST_DISK = 4096;

    /**
     * Setzt einen Serverschrank — beide Hälften, wie beim Setzen von Hand.
     *
     * <p>{@code setBlock} geht nicht durch {@code setPlacedBy}, also entsteht
     * die obere Hälfte nicht von selbst. Ohne sie fiele der Schrank beim
     * nächsten Nachbarwechsel von oben in sich zusammen — und die Prüfung
     * scheiterte an etwas ganz anderem als dem, was sie prüft.
     */
    private static void placeRack(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, FnBlocks.RACK.get().defaultBlockState());
        helper.setBlock(at.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.RackBlock.HALF,
                        net.minecraft.world.level.block.state.properties
                                .DoubleBlockHalf.UPPER));
    }

    /** Der Gegenstand zu Bauteilart und Stufe. */
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

    /** Ein leeres Servergehäuse. */
    private static ItemStack chassis() {
        return new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.SERVER_CHASSIS.get());
    }

    /**
     * Bestückt einen Einschub vollständig.
     *
     * <p>Das Gehäuse zuerst: Ohne eines nimmt der Einschub keine Bauteile an,
     * und das ist die Regel, die den Gegenstand überhaupt zu einem Server
     * macht.
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

    /** Ein Serverschrank mit einem reichlich bestückten Einschub. */
    private static void rackWithServer(GameTestHelper helper, BlockPos at) {
        placeRack(helper, at);
        fillBay(helper, at, 0, TEST_CPU, TEST_RAM, TEST_DISK);
    }

    /**
     * Schiebt das erste Bauteil aus dem Rucksack ins Regal — wie ein
     * Umschalt-Klick im Fenster.
     */
    private static void intoShelf(GameTestHelper helper, BlockPos at,
                                  net.minecraft.world.entity.player.Player player) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf)) {
            helper.fail("Hier steht kein Regal", at);
            return;
        }
        var menu = dev.devpanda.factorynetwork.client.menu.ShelfMenu
                .of(1, player.getInventory(), shelf);
        for (int index = shelf.getContainerSize(); index < menu.slots.size(); index++) {
            if (menu.slots.get(index).hasItem()) {
                menu.quickMoveStack(player, index);
                return;
            }
        }
        helper.fail("Im Rucksack liegt nichts zum Einschieben", at);
    }

    /**
     * Nimmt ein Bauteil über das Fenster heraus — wie ein Umschalt-Klick.
     *
     * <p>Seit es ein Fenster gibt, ist das der Weg. Die leere Hand am Block
     * macht es auf, statt etwas herauszuziehen.
     */
    private static void takeFromShelf(GameTestHelper helper, BlockPos at, int slot,
                                      net.minecraft.world.entity.player.Player player) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf)) {
            helper.fail("Hier steht kein Regal", at);
            return;
        }
        dev.devpanda.factorynetwork.client.menu.ShelfMenu
                .of(1, player.getInventory(), shelf)
                .quickMoveStack(player, slot);
    }

    /** Setzt ein Laufwerk ans Kabel und steckt eine Flüssigkeitszelle hinein. */
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

    /** Setzt ein Laufwerk ans Kabel und steckt eine Zelle hinein. */
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

        // Lagerraum ist jetzt etwas, das man baut. Ohne Laufwerk gibt es
        // keinen — und das Einlagern muss das sagen, nicht schlucken.
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

        // Die Menge einer 1k-Zelle ist achttausend.
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

        // Acht Arten passen in eine 1k-Zelle, die neunte nicht — obwohl die
        // Menge bei weitem nicht erreicht ist. Genau das treibt zum Sortieren.
        Item[] arten = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG};
        for (Item art : arten) {
            helper.assertValueEqual(entity.storage().insert(art, 1), 0L,
                    "Art " + art + " muss hineinpassen");
        }
        helper.assertValueEqual(entity.storage().insert(Items.STONE, 1), 1L,
                "Die neunte Art findet keinen Platz mehr");

        // Von einer schon vorhandenen Art geht dagegen weiter etwas hinein —
        // sonst müsste man beim Aufräumen jede Zelle einzeln im Blick haben.
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

        // Der Bestand steckt im Gegenstand, nicht im Laufwerk. Das ist der
        // Grund, warum eine Zelle etwas wert ist.
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        // Der Bestand lebt im Laufwerk und geht erst beim Sichern in den
        // Gegenstand. Wer ihn vorher liest, sieht den Stand von vorhin.
        drive.flushCells();
        ItemStack cell = drive.cell(0);
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(cell);
        helper.assertValueEqual(inhalt.getOrDefault(Items.DIAMOND, 0L), 12L,
                "Die Zelle trägt ihren Inhalt selbst");

        // Zelle heraus: Das Netz hat nichts mehr.
        drive.setCell(0, ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.storage().count(Items.DIAMOND), 0L,
                "Ohne die Zelle ist der Bestand weg");
        helper.succeed();
    }

    /**
     * Ein zweites Laufwerk vergrößert den Speicher.
     *
     * <p>Das ist die Antwort auf „ein eigener Speicherblock": Er steht, und
     * er heißt Laufwerk. Geprüft wird die Zusage, die daran hängt — <b>wer
     * mehr Platz will, stellt eines dazu</b>. Ohne sie wäre das Laufwerk ein
     * Speicher mit fester Größe an anderer Stelle.
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

        // Acht Arten füllen die Artenplätze einer 1k-Zelle.
        Item[] acht = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG};
        for (Item art : acht) {
            helper.assertValueEqual(entity.storage().insert(art, 1), 0L,
                    "Art " + art + " muss hineinpassen");
        }

        // Die neunte scheiterte an einer einzelnen Zelle. Mit einem zweiten
        // Laufwerk findet sie dort Platz.
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

        // Ohne Strom passiert nichts, egal wie lange man wartet.
        for (int i = 0; i < 200; i++) {
            press.serverTick();
        }
        helper.assertValueEqual(press.progress(), 0, "Ohne Strom darf nichts geschehen");
        helper.assertTrue(press.item(
                        dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT)
                .isEmpty(), "Und nichts herauskommen");

        // Mit Strom läuft sie — aber nicht sofort fertig.
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

        // Der Stempel bleibt, das Material wird verbraucht.
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
        // Ausgabe mit etwas Fremdem belegt: Dann darf sie nicht laufen und
        // schon gar nicht Strom dafür verbrauchen.
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

        // Die Zelle bis an die Mengengrenze füllen.
        entity.storage().insert(Items.COBBLESTONE, 8_000);
        helper.assertValueEqual(entity.storage().count(Items.COBBLESTONE), 8_000L, "voll");

        // Eine Kiste am Netz, aus der ein Worker einlagern soll.
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

        // Der Speicher ist voll — die Barren müssen noch da sein, in der
        // Kiste oder auf dem Boden, aber nicht verschwunden.
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

        // Der Bestand lebt im Speicher des Laufwerks. Erst beim Sichern geht
        // er in den Gegenstand — ohne das wäre er nach einem Neustart weg.
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getBlockEntity(drivePos);
        var registries = helper.getLevel().registryAccess();
        var tag = drive.saveWithFullMetadata(registries);

        var geladen = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(drivePos), helper.getBlockState(drivePos),
                        tag, registries);
        helper.assertTrue(geladen != null, "Das Laufwerk kam nicht zurück");
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(geladen.cell(0));
        helper.assertValueEqual(inhalt.getOrDefault(Items.DIAMOND, 0L), 42L,
                "Der Bestand muss das Sichern überstehen");
        helper.succeed();
    }

    // ---- Router ----------------------------------------------------------

    /**
     * Die Richtung von einer Stelle zur anderen, in Weltkoordinaten.
     *
     * <p>Ein Testaufbau darf gedreht stehen. Eine Richtung, die im Test
     * „Norden" heißt, ist es in der Welt dann nicht — deshalb wird sie aus
     * zwei absoluten Stellen gerechnet statt hingeschrieben.
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
     * Der Router verbindet nur, was auf derselben Bahn liegt.
     *
     * <p>Das ist der ganze Block in einem Test: gleiche Bahn verbunden,
     * andere Bahn nicht, abgeklemmt gar nicht. <b>Und die Bahn gilt auch für
     * Geräte</b>, nicht nur für weiterführende Kabel — genau das ist die
     * Stelle, an der eine zu früh gesetzte Abkürzung falsch läge.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void routerJoinsOnlyWhatSharesALane(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.DENSE_CABLE.get());

        BlockPos router = controller.east(2);
        helper.setBlock(router, FnBlocks.ROUTER.get());

        BlockPos north = router.north();
        helper.setBlock(north, FnBlocks.CONNECTOR.get());
        name(helper, north, "nord");
        BlockPos above = router.above();
        helper.setBlock(above, FnBlocks.CONNECTOR.get());
        name(helper, above, "oben");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Frisch gesetzt liegt alles auf Bahn eins: Beide hängen am Netz.
        helper.assertTrue(entity.graph().connector("nord").isPresent(),
                "nord muss auf derselben Bahn erreichbar sein");
        helper.assertTrue(entity.graph().connector("oben").isPresent(),
                "oben muss auf derselben Bahn erreichbar sein");
        helper.assertValueEqual(entity.graph().laneLoad(helper.absolutePos(router), 1),
                2, "Kanäle auf Bahn eins");

        // Andere Bahn: Der Weg kreuzt sich berührungslos.
        lane(helper, router, north, 2);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("nord").isEmpty(),
                "nord liegt auf einer anderen Bahn und darf nicht dazugehören");
        helper.assertTrue(entity.graph().connector("oben").isPresent(),
                "oben liegt weiter auf Bahn eins");
        helper.assertValueEqual(entity.graph().laneLoad(helper.absolutePos(router), 1),
                1, "Kanäle auf Bahn eins, nachdem nord ausgeschert ist");
        helper.assertValueEqual(entity.graph().laneLoad(helper.absolutePos(router), 2), 0,
                "Bahn zwei führt zu keinem Controller und trägt nichts");

        // Abgeklemmt: gar nichts mehr.
        lane(helper, router, above, dev.devpanda.factorynetwork.block.entity
                .RouterBlockEntity.OFF);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("oben").isEmpty(),
                "eine abgeklemmte Seite darf nichts durchlassen");
        helper.assertValueEqual(entity.graph().connectorCount(), 0,
                "Geräte am Netz");
        helper.succeed();
    }

    /**
     * Eine abgeklemmte Seite trennt auch den Weg dahinter.
     *
     * <p>Nicht nur die Seite selbst: Alles, was über sie erreichbar war,
     * gehört danach nicht mehr zum Netz. Sonst wäre Abklemmen bloß eine
     * Anzeige.
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
        helper.setBlock(device, FnBlocks.CONNECTOR.get());
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
     * Eine Bahn des Routers trägt so viel wie ein dickes Kabel.
     *
     * <p>Nicht unbegrenzt: Ließe man den Router aus der Wegrechnung heraus,
     * wäre eine Kreuzung die Stelle, an der die Kanalgrenze aufhört zu
     * gelten — und damit die Stelle, an der man sie umgeht.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aRouterLaneCarriesSixtyFourChannels(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.DENSE_CABLE.get());
        BlockPos router = controller.east(2);
        helper.setBlock(router, FnBlocks.ROUTER.get());

        // Ein einziges Gerät, das die ganze Bahn füllt, und eins mehr.
        BlockPos first = router.north();
        helper.setBlock(first, FnBlocks.CONNECTOR.get());
        name(helper, first, "gross");
        if (helper.getBlockEntity(first) instanceof ConnectorBlockEntity connector) {
            connector.setChannelCost(
                    dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE);
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Allein passt es genau: Die Bahn trägt so viel, wie das Gerät zieht.
        // Stünde der Router nicht im Weg, bliebe die Bahn hier auf null —
        // dann zählte die Kreuzung nichts und wäre unbegrenzt.
        helper.assertValueEqual(entity.graph().laneLoad(helper.absolutePos(router), 1),
                dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE,
                "Kanäle auf der Bahn");
        helper.assertTrue(entity.graph().starvedConnectors().isEmpty(),
                "allein bekommt das Gerät seinen Platz");

        // Eins mehr passt nicht. Welches der beiden leer ausgeht, entscheidet
        // die Suchreihenfolge — dass eines leer ausgeht, entscheidet die
        // Grenze.
        BlockPos second = router.above();
        helper.setBlock(second, FnBlocks.CONNECTOR.get());
        name(helper, second, "klein");
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().starvedConnectors().size(), 1,
                "Geräte ohne Kanal");
        helper.succeed();
    }

    /**
     * Der Analysator liest die Kapazität an der Stelle, nicht aus einer
     * festen Zahl.
     *
     * <p>Vorher stand dort die des gewöhnlichen Kabels. Eine dichte Strecke
     * meldete damit ab dem sechzehnten Kanal „voll" und wies auf eine Enge
     * hin, die es nicht gab — die schlimmste Sorte Anzeige, weil man ihr
     * hinterherbaut.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theAnalyserReadsTheCapacityFromTheCable(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        for (int i = 1; i <= 3; i++) {
            helper.setBlock(controller.east(i), FnBlocks.DENSE_CABLE.get());
        }
        BlockPos device = controller.east(3).above();
        helper.setBlock(device, FnBlocks.CONNECTOR.get());
        name(helper, device, "ziel");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        var data = dev.devpanda.factorynetwork.analyser.AnalyserScan.of(entity);
        helper.assertTrue(!data.links().isEmpty(), "der Analysator muss Strecken finden");
        for (var link : data.links()) {
            helper.assertValueEqual(link.capacity(),
                    dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE,
                    "Kapazität einer dichten Strecke");
            helper.assertTrue(
                    link.state() != dev.devpanda.factorynetwork.analyser
                            .AnalyserData.LinkState.FULL,
                    "ein Gerät an einem dichten Kabel füllt es nicht");
        }
        helper.succeed();
    }

    /**
     * Der Bestand folgt, wenn jemand eine Zelle herauszieht.
     *
     * <p>Der Netzindex hält den Bestand aller Zellen zusammen, damit ihn nicht
     * jede Frage neu zusammenzählt. Der Preis dafür ist genau diese Gefahr:
     * dass er stehen bleibt, während sich die Wahrheit ändert. Wer diesen Test
     * kaputt macht, hat ein Netz gebaut, das Gegenstände meldet, die es nicht
     * mehr hat.
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
        // Dieselbe Instanz, keine Kopie: Beim Herausnehmen schreibt das
        // Laufwerk den Bestand in genau diesen Gegenstand zurück, und
        // derselbe wandert im Spiel in die Hand des Spielers.
        ItemStack cell = drive.cell(0);
        drive.setCell(0, ItemStack.EMPTY);

        // Ohne Zelle ist nichts da — auch dann nicht, wenn eben noch etwas da
        // war. Das Netz wurde dazwischen nicht neu aufgebaut.
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 0L,
                "Bestand ohne Zelle");
        helper.assertValueEqual(entity.storage().distinctTypes(), 0, "Arten ohne Zelle");

        drive.setCell(0, cell);
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 100L,
                "Bestand, nachdem die Zelle zurück ist");
        helper.succeed();
    }

    /**
     * Ablegen und Entnehmen rechnen den Index fort, statt ihn zu verwerfen.
     *
     * <p>Ein Index, der sich nach jeder Ablage selbst wegwirft, ist keiner —
     * in einem Tick mit zwanzig Workern wäre er zwanzigmal neu gebaut. Geprüft
     * wird das Ergebnis: eine lange Folge von Ablagen und Entnahmen, und am
     * Ende muss der Index dasselbe sagen wie die Zellen.
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
            eisen += cell.count(Items.IRON_INGOT);
            gold += cell.count(Items.GOLD_INGOT);
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
     * Eine Zelle geht über das Fenster hinein und wieder heraus.
     *
     * <p>Ein Klick auf das Laufwerk macht auf — immer, egal was in der Hand
     * liegt. Vorher ging ein Bauteil in der Hand direkt hinein; das ersparte
     * einen Griff, war aber eine eigene Regel für zwei Blöcke.
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
        // Umschalt-Klick aus dem Rucksack ins Regal und zurück.
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
     * Ein Bauteil geht über das Fenster hinein und wieder heraus.
     *
     * <p>Dasselbe Fenster wie beim Laufwerk: Beide sind ein Regal, und wer
     * eines bedienen kann, kann auch das andere.
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
        // Jedes Teil findet seinen Platz von selbst — der Umschalt-Klick muss
        // nicht wissen, wohin ein Datenträger gehört. Das Gehäuse zuerst,
        // weil die Bauteile ohne eines gar nicht hineindürfen.
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

    /** Ist alles belegt, bleibt die elfte Zelle im Rucksack. */
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
     * Die herausgenommene Zelle bringt ihren Bestand mit.
     *
     * <p>Der Bestand steckt im Gegenstand, nicht im Laufwerk. Ginge er beim
     * Herausnehmen verloren, wäre eine Zelle ein Schlüssel und kein Speicher —
     * und jeder Umbau einer Anlage kostete das halbe Lager.
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
        var inhalt = dev.devpanda.factorynetwork.storage.CellContents.read(pulled);
        helper.assertValueEqual(inhalt.getOrDefault(Items.DIAMOND, 0L), 7L,
                "Bestand in der herausgenommenen Zelle");
        helper.assertValueEqual(entity.storage().count(Items.DIAMOND), 0L,
                "im Netz darf nichts zurückbleiben");
        helper.succeed();
    }


    /**
     * Eine Flüssigkeitszelle hat eine Grenze, und die gilt.
     *
     * <p>Vorher lagerte das Netz Flüssigkeiten unbegrenzt im Controller. Für
     * Eisen brauchte man ein Laufwerk, für Lava nicht — eine Ungleichheit, die
     * niemand erklären kann.
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
     * Ist der Speicher voll, bleibt die Flüssigkeit im Tank.
     *
     * <p><b>Das ist die Prüfung, an der es hängt.</b> Ein Gegenstand, den der
     * Speicher nicht nimmt, lässt sich zurücklegen; eine gezogene Flüssigkeit
     * nicht unbedingt — nimmt der Tank sie nicht wieder an, ist sie weg. Also
     * wird erst gefragt und dann gezogen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullNetworkLeavesTheTankAlone(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        ControllerBlockEntity entity = twoCauldrons(helper, controller);
        entity.rebuildNetwork();
        fillCauldron(helper, controller, 0);

        // Die Zelle randvoll mit Lava: Wasser findet keinen Platz mehr.
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
     * Eine herausgenommene Flüssigkeitszelle nimmt ihren Bestand mit.
     *
     * <p>Wie bei den Gegenständen: Der Bestand steckt im Gegenstand, sonst
     * wäre die Zelle nur ein Schlüssel.
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
        var inhalt = dev.devpanda.factorynetwork.storage.CellFormat.FLUIDS.read(pulled);
        helper.assertValueEqual(inhalt.getOrDefault(
                net.minecraft.world.level.material.Fluids.WATER, 0L), 3000L,
                "Bestand in der herausgenommenen Zelle");
        helper.assertValueEqual(entity.fluids().count(
                net.minecraft.world.level.material.Fluids.WATER), 0L,
                "im Netz darf nichts zurückbleiben");
        helper.succeed();
    }

    // ---- Serverschrank ----------------------------------------------------

    /**
     * Ohne Serverschrank rechnet das Netz nicht.
     *
     * <p>So wie ein Laufwerk die Voraussetzung dafür ist, dass es lagert.
     * Jede Fähigkeit des Netzes hängt an einem Block, den man bauen muss —
     * und das Programm sagt, welcher fehlt, statt still nichts zu tun.
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

        // Schrank hin, und dasselbe Programm läuft.
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
     * Die Rechenleistung ist die Summe über alle Schränke im Netz.
     *
     * <p>Nicht die des nächsten oder größten: Wer nachrüstet, stellt einen
     * zweiten Schrank daneben, und das muss zählen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void threadsAddUpAcrossRacks(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        rackWithServer(helper, controller.above());

        // Im zweiten Schrank noch ein kleiner Einschub dazu.
        fillBay(helper, controller.above(), 1, 8, 8, 64);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.threads(), 2 * TEST_CPU + 8,
                "zwei große Einschübe und ein kleiner");
        helper.succeed();
    }

    /**
     * Das Gehäuse nimmt seine Hardware mit — und bringt sie wieder.
     *
     * <p>Das ist der ganze Zweck des Gegenstands: einen fertigen Server
     * herausziehen, wegtragen, woanders einsetzen. Ginge die Hardware dabei
     * verloren oder bliebe sie doppelt zurück, wäre das Gehäuse eine Falle.
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

        // Und wieder hinein: die Hardware kommt zurück in die Plätze.
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
     * Passt das Gehäuse nicht in den Rucksack, bleibt alles, wie es war.
     *
     * <p>Der Umschalt-Klick nimmt den Gegenstand erst heraus und schiebt ihn
     * dann — und beim Herausnehmen packt das Gehäuse ein. Schlägt das
     * Schieben fehl, muss es zurück und wieder auspacken. <b>Genau hier
     * entstünde sonst ein doppelter oder ein verlorener Server</b>, und man
     * merkte es erst, wenn der Schrank plötzlich weniger trägt.
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
     * Ohne Gehäuse nimmt ein Einschub keine Bauteile an.
     *
     * <p>Die Regel, die den Gegenstand zu einem Server macht. Ohne sie wären
     * die drei Plätze schon der Server, und das Gehäuse wäre etwas, das man
     * kauft und das nichts ändert.
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
                .of(1, player.getInventory(), rack);
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
     * Ein unvollständiger Einschub trägt nichts.
     *
     * <p>Nicht anteilig, gar nichts — sonst wäre der Schrank eine Summe von
     * Bauteilen und keine Reihe von Servern, und die Entscheidung, welcher
     * Einschub das große Teil bekommt, gäbe es nicht.
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
     * Jeder Platz nimmt nur seine eigene Art.
     *
     * <p>Sonst läge ein Rechenwerk auf dem Datenträgerplatz, der Einschub
     * wäre voll und liefe trotzdem nicht — ein Fehler, den man beim Ansehen
     * nicht findet.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aSlotTakesOnlyItsOwnKind(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        placeRack(helper, rackPos);
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);

        int diskSlot = dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.DISK);
        // Ohne Gehäuse geht gar nichts hinein.
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
     * Der Schrank ist zwei Blöcke hoch und trotzdem ein Gerät.
     *
     * <p>Wer oben ankabelt, kabelt denselben Schrank an. Zählte die obere
     * Hälfte für sich, kostete ein Schrank zwei Kanäle und stünde zweimal in
     * der Liste — und die zweite BlockEntity gäbe es gar nicht.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aTallRackIsStillOneDevice(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        // Der Schrank steht neben dem Controller: Seine untere Hälfte
        // berührt ihn, seine obere ein Kabel, das ebenfalls an ihm hängt.
        // Zwei Wege zu einem Gerät.
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
     * Wird der letzte Schrank abgebaut, stehen die Worker still.
     *
     * <p>Was schon läuft, läuft zu Ende — es mittendrin zu töten hieße,
     * Gegenstände zu verlieren, die gerade in der Hand eines Ablaufs sind.
     * Aber neue Arbeit fängt nicht mehr an.
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

    /** Controller, Kabel, eine benannte Kiste mit Erz und ein Laufwerk. */
    private static BlockPos threeChestsSetup(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());
        BlockPos connector = controller.east().north();
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING,
                        Direction.NORTH));
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
     * Ein Serverschrank mit genau so viel Rechenleistung, wie angegeben.
     *
     * <p>Speicher und Datenträger reichlich: Wer die Rechengrenze prüft,
     * soll nicht an der Speichergrenze scheitern.
     */
    private static void smallRack(GameTestHelper helper, BlockPos at, int cpu) {
        placeRack(helper, at);
        fillBay(helper, at, 0, cpu, TEST_RAM, TEST_DISK);
    }

    /**
     * Ist der Speicher voll, scheitert der nächste Ablauf sichtbar.
     *
     * <p>Anders als bei den Rechenwerken wird hier nicht angestellt. Ein
     * Ablauf, für den kein Speicher da ist, wartet nicht — er passt nicht
     * hinein, und das muss unter den Fehlern stehen, statt lautlos zu
     * verschwinden.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void afullMemoryRejectsTheNextFlow(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        placeRack(helper, controller.west());
        // Reichlich Rechenwerk, knapper Speicher: Die Grenze, die greift,
        // soll die geprüfte sein.
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
        // „Arbeitsspeicher" und nicht nur „Speicher": Die Zellen im Laufwerk
        // heißen auch so, und wer diese Meldung las, ging Zellen einbauen.
        helper.assertTrue(engine.failed().get(0).detail().contains("Arbeitsspeicher"),
                "und der Grund muss den richtigen Speicher nennen: "
                        + engine.failed().get(0).detail());
        helper.succeed();
    }

    /**
     * Ein zu großes Programm wird gar nicht erst übernommen.
     *
     * <p>Beim Drücken auf Übernehmen und nicht eine Minute später an einer
     * Fabrik, die stillsteht. Und mit der Zahl in der Meldung: „zu groß"
     * allein sagt nicht, um wie viel.
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
        // Das alte Programm läuft weiter — ein abgelehntes ersetzt nichts.
        helper.assertValueEqual(entity.programSize(), vorher, "das kleine steht noch");
        helper.succeed();
    }

    /**
     * Wird der Datenträger gezogen, friert das Netz ein.
     *
     * <p>Nicht abbrechen und nicht kürzen — dieselbe Antwort wie bei
     * Stromausfall. Geprüft mit zwei Einschüben: Bliebe nur einer, wäre der
     * Schrank ohne Datenträger gar kein Server mehr, und das Netz stünde
     * aus dem alten Grund still statt aus dem geprüften.
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

        // Den großen Datenträger heraus: Es bleibt ein Server mit 64.
        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        rack.setItem(dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                .slotOf(0, dev.devpanda.factorynetwork.item.ServerPart.DISK), ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertTrue(entity.hasServer(), "der zweite Einschub rechnet weiter");
        helper.assertTrue(!entity.programFits(), "das Programm passt nicht mehr");
        helper.assertTrue(entity.flowEngine().isFrozen(), "also steht das Netz");

        // Wieder hinein, und es läuft weiter.
        fillBay(helper, rackPos, 0, TEST_CPU, TEST_RAM, 4096);
        entity.rebuildNetwork();
        helper.assertTrue(!entity.flowEngine().isFrozen(), "und läuft wieder");
        helper.succeed();
    }

    /**
     * Das Programm liegt als Datei neben der Welt und lässt sich dort ändern.
     *
     * <p>Die Brücke zu einem richtigen Editor. Zwei Richtungen in einer
     * Prüfung, weil sie nur zusammen etwas taugen: Was im Spiel übernommen
     * wird, steht in der Datei; was in der Datei steht, gilt im Spiel.
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

                // Eine zweite Datei von außen: Sie gehört ab jetzt dazu.
                java.nio.file.Files.writeString(folder.resolve("zwei.mf"),
                        "fn zwei() {\n    let b = 2\n}");
            } catch (java.io.IOException failed) {
                helper.fail("Der Ordner ließ sich nicht anfassen: " + failed);
            }

            // Über runAfterDelay und nicht über eine Schleife von
            // serverTick-Aufrufen: Innerhalb eines Ticks steht die Spielzeit
            // still, und der Controller sieht nur alle zwanzig Ticks nach.
            helper.runAfterDelay(25, () -> {
                helper.assertValueEqual(entity.project().names().size(), 2,
                        "die neue Datei gehört zum Projekt");
                helper.assertTrue(entity.program().functions().stream()
                                .anyMatch(fn -> fn.name().equals("zwei")),
                        "und ihre Funktion wurde übernommen");
                helper.assertTrue(entity.program().functions().stream()
                                .anyMatch(fn -> fn.name().equals("eins")),
                        "die erste ist dabei nicht verlorengegangen");

                // Und wieder weg: Löschen ist in einem Projekt eine Absicht.
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
     * Mehr Abläufe als Plätze: Der Rest stellt sich an.
     *
     * <p>Angestellt, nicht abgelehnt. <b>Verzögerung ist wiederherstellbar,
     * Verlust nicht</b> — ein abgelehntes Ereignis ist für immer weg, und die
     * Gegenstände stehen bis zum nächsten Neustart in einer Maschine, die
     * niemand mehr anfasst.
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

        // Sind die ersten durch, rücken die anderen nach.
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
     * Ein angestellter Ablauf übersteht das Aufschreiben.
     *
     * <p>Er hat noch keinen Schritt gemacht, aber seinen Rahmen schon. Ginge
     * er beim Sichern verloren, verschwände Arbeit, von der niemand weiß,
     * dass es sie gab.
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
     * Eine zu lange Warteschlange scheitert sichtbar.
     *
     * <p>Eine unbegrenzte wäre eine Anlage, die Arbeit ansammelt, die sie nie
     * abarbeitet. Was darüber hinausgeht, steht unter den letzten Fehlern —
     * still zu verschwinden wäre das Schlimmste von beidem.
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
     * Der Analysator zeigt auch, was Platz und Rechenleistung bereitstellt.
     *
     * <p>Laufwerke, Serverschränke und Kreuzungen gehörten schon zum Netz und
     * waren trotzdem unsichtbar. Wer sucht, warum nichts lagert oder nichts
     * rechnet, sucht genau danach.
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
     * Die Bahnzuordnung eines Routers übersteht das Aufschreiben.
     *
     * <p>Sie steht in der BlockEntity und nicht im Blockzustand — also muss
     * sie von Hand gesichert werden. Ginge sie verloren, läge nach einem
     * Neustart alles wieder auf Bahn eins, und zwei Netze, die sich
     * berührungslos kreuzten, wären plötzlich eines.
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
        // Was niemand angefasst hat, liegt weiter auf der Vorgabe.
        helper.assertValueEqual(geladen.lane(towards(helper, routerPos, routerPos.north())), 1,
                "unberührte Seite");
        helper.succeed();
    }

    /**
     * Die Bauteile überstehen das Aufschreiben.
     *
     * <p>Sonst stünde nach einem Neustart ein leerer Schrank da, das Netz
     * rechnete nicht mehr, und niemand käme auf den Gedanken, dass die
     * Bauteile beim Sichern verlorengingen.
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
     * Ein abgebauter Schrank gibt seine Bauteile zurück.
     *
     * <p>Die Loot-Tabelle sieht nur den Blockzustand, nicht die BlockEntity.
     * Ohne diesen Weg wäre ein versehentlicher Schlag der Verlust von
     * sechsunddreißig Bauteilen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void abrokenRackDropsItsProcessors(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 2, 1);
        smallRack(helper, rackPos, 8);
        helper.setBlock(rackPos, Blocks.AIR);

        // <b>Nicht „irgendein Gegenstand fällt".</b> Ein leeres Gehäuse hätte
        // diesen Test bestanden, und dahinter stünde genau der Verlust, den er
        // verhindern soll: sechsunddreißig Bauteile, die niemand
        // wiederbekommt.
        //
        // Herausfallen tut ein <b>fertiger Server</b> und keine Einzelteile —
        // das Gehäuse trägt seine Bauteile in sich (siehe RackBlock.onRemove).
        // Geprüft wird deshalb, dass es nicht leer ist.
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
     * Fällt eine Hälfte, geht die andere mit.
     *
     * <p>Sonst bliebe nach einer Explosion eine schwebende Blechhaube
     * stehen, die nichts kann und die man auch nicht mehr als Schrank
     * erkennt.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void breakingOneHalfTakesTheOther(GameTestHelper helper) {
        BlockPos unten = new BlockPos(1, 2, 1);
        placeRack(helper, unten);
        helper.assertBlockPresent(FnBlocks.RACK.get(), unten.above());

        // Die obere weg — die untere geht mit.
        helper.setBlock(unten.above(), Blocks.AIR);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten.above());

        // Und andersherum.
        placeRack(helper, unten);
        helper.setBlock(unten, Blocks.AIR);
        helper.assertBlockNotPresent(FnBlocks.RACK.get(), unten.above());
        helper.succeed();
    }

    /**
     * Wer oben zuschlägt, bekommt den Schrank und seinen Inhalt.
     *
     * <p>Der Gegenstand hängt an der unteren Hälfte, weil die Loot-Tabelle
     * nur dort etwas hergibt — sonst machte eine Explosion aus einem
     * Schrank zwei. Ein Spieler, der die obere Hälfte abbaut, meint aber den
     * Schrank und soll nicht mit leeren Händen dastehen.
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

        // Ein Schrank und drei Bauteile liegen auf dem Boden.
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
     * Ohne Server fängt kein Ablauf an — aber er geht auch nicht verloren.
     *
     * <p>Er stellt sich an und läuft, sobald wieder ein Schrank steht. Das ist
     * dieselbe Antwort wie bei der Überlast: Verzögerung ist
     * wiederherstellbar, Verlust nicht.
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

        // Schrank weg, dann einen Ablauf anstoßen.
        helper.setBlock(controller.west(), Blocks.AIR);
        entity.rebuildNetwork();
        var flow = entity.startFlow("kurz", java.util.List.of());
        helper.assertValueEqual(flow.status().name(), "QUEUED",
                "ohne Server darf nichts anfangen, aber auch nichts wegfallen");

        // Schrank zurück, und er läuft.
        rackWithServer(helper, controller.west());
        entity.rebuildNetwork();
        for (int i = 0; i < 5; i++) {
            entity.serverTick();
        }
        helper.assertValueEqual(entity.flowEngine().queued(), 0,
                "mit Server muss er nachrücken");
        helper.succeed();
    }

    // ---- Farbige Kabel ----------------------------------------------------

    /** Setzt ein Kabel so, wie es der Gegenstand täte — samt Farbe. */
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
     * Zwei Kabel verschiedener Farbe greifen nicht nacheinander.
     *
     * <p>Die Farbe kam vorher aus der Welt statt aus dem Zustand. Beim Setzen
     * steht an der eigenen Stelle aber noch Luft, und Luft gilt als neutral —
     * ein rotes Kabel rechnete sich seine Verbindungen deshalb als neutrales
     * aus und wuchs einen Arm zu jedem Nachbarn, egal welcher Farbe.
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

        // Gleiche Farbe schon.
        BlockPos zweitesRot = links.west();
        placeCable(helper, zweitesRot, rot);
        helper.assertTrue(connected(helper, links, zweitesRot), "rot an rot");
        helper.assertTrue(connected(helper, zweitesRot, links), "und zurück");

        // Neutral verbindet sich mit allem — das ist der Sinn der Vorgabe.
        BlockPos oben = links.above();
        placeCable(helper, oben, neutral);
        helper.assertTrue(connected(helper, oben, links), "neutral an rot");
        helper.assertTrue(connected(helper, links, oben), "und zurück");
        helper.succeed();
    }

    /**
     * Ein abgebautes Kabel gibt seine Farbe zurück.
     *
     * <p>Die Farbe steht im Blockzustand, der Gegenstand ist je Farbe ein
     * eigener. Die Loot-Tabelle war leer — es fiel gar nichts heraus.
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
     * Jede Farbe hat ihren eigenen Namen.
     *
     * <p>Ein BlockItem nimmt seinen Namen sonst vom Block, und alle siebzehn
     * zeigen auf denselben — im Kreativ-Reiter stand siebzehnmal „Kabel".
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
     * Jeder Gegenstand der Mod steht im Kreativ-Reiter.
     *
     * <p>Die dichten Kabel waren angemeldet, hatten Modelle, Namen und
     * Rezepte — und standen trotzdem nirgends, weil eine Zeile im Reiter
     * fehlte. Nur über {@code /give} zu erreichen heißt: nicht da.
     *
     * <p>Diese Prüfung ist bewusst allgemein. Sie fängt nicht diesen einen
     * Fall, sondern jeden künftigen derselben Art.
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
            if (!gezeigt.contains(eintrag.getValue())) {
                fehlend.add(eintrag.getKey().location().getPath());
            }
        }
        helper.assertTrue(fehlend.isEmpty(), "Nicht im Kreativ-Reiter: " + fehlend);
        helper.succeed();
    }

    /**
     * Jeder Gegenstand hat einen eigenen Namen in beiden Sprachen.
     *
     * <p>Siebzehn Kabel hießen alle „Kabel". Ein Name, den zwei Gegenstände
     * teilen, ist kein Name — im Reiter steht dann eine Reihe, die man nicht
     * lesen kann.
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

    // ---- Wer einen Kanal kostet -------------------------------------------

    /**
     * Jedes Gerät am Netz kostet einen Kanal, eine Anzeige ein Viertel.
     *
     * <p>Die Regel in einem Satz: Was am Netz etwas tut, kostet einen Kanal.
     * Vorher zahlten nur Connectoren — Laufwerk und Serverschrank hingen
     * gratis daran, und ein Netz aus zwanzig Laufwerken kam mit einem
     * einzigen Kanal aus.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void everyDeviceOnTheNetworkCostsAChannel(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        helper.setBlock(cable.north(), FnBlocks.CONNECTOR.get());
        name(helper, cable.north(), "geraet");
        helper.setBlock(cable.south(), FnBlocks.DRIVE.get());
        helper.setBlock(cable.above(), FnBlocks.RACK.get());
        helper.setBlock(cable.below(), FnBlocks.DISPLAY.get());

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Connector, Laufwerk und Schrank je einen Kanal — die Anzeige
        // keinen, denn sie liest nur mit.
        int erwartet = dev.devpanda.factorynetwork.network.Channels.CONNECTOR
                + dev.devpanda.factorynetwork.network.Channels.DRIVE
                + dev.devpanda.factorynetwork.network.Channels.RACK
                + dev.devpanda.factorynetwork.network.Channels.DISPLAY;
        helper.assertValueEqual(erwartet, 3, "drei Geräte, eine Anzeige umsonst");
        helper.assertValueEqual(entity.graph().channelLoad(
                        helper.absolutePos(cable), CableColour.NONE), erwartet,
                "Kanäle auf dem Kabel");
        helper.succeed();
    }

    /**
     * Anzeigen kosten keinen Kanal.
     *
     * <p>Eine Anzeige liest nur mit und schiebt nichts — dieselbe Begründung
     * wie beim Router. Vorher kostete sie ein Viertel, damit vier sich einen
     * teilen; einen Viertelkanal versteht aber niemand, und ein Netz sollte
     * nicht in Brüchen rechnen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void displaysCostNoChannel(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        for (BlockPos at : java.util.List.of(cable.north(), cable.south(),
                cable.above(), cable.below())) {
            helper.setBlock(at, FnBlocks.DISPLAY.get());
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().displays().size(), 4, "Anzeigen am Netz");
        helper.assertValueEqual(entity.graph().channelLoad(
                        helper.absolutePos(cable), CableColour.NONE),
                0,
                "und das Kabel trägt trotzdem nichts");
        helper.succeed();
    }

    /**
     * Eine Wand aus Anzeigen braucht ein Kabel und nicht sechs.
     *
     * <p>Anzeigen leiten weiter: Wer eine Tafel ans Kabel hängt, kann die
     * nächste daneben setzen. Hinter jede ein Kabel zu legen wäre Arbeit
     * ohne Entscheidung — und eine Wand, deren Ecken dunkel bleiben, sieht
     * aus wie ein Fehler.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void awallOfDisplaysNeedsOneCable(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        // Eine Reihe von drei, davon berührt nur die erste das Kabel, und
        // eine vierte darüber — die hängt an der zweiten.
        BlockPos erste = cable.north();
        helper.setBlock(erste, FnBlocks.DISPLAY.get());
        helper.setBlock(erste.north(), FnBlocks.DISPLAY.get());
        helper.setBlock(erste.north().north(), FnBlocks.DISPLAY.get());
        helper.setBlock(erste.north().above(), FnBlocks.DISPLAY.get());

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.graph().displays().size(), 4,
                "alle vier Tafeln gehören zum Netz");
        helper.assertValueEqual(entity.graph().channelLoad(
                        helper.absolutePos(cable), CableColour.NONE),
                0,
                "und kosten dabei keinen Kanal");
        helper.succeed();
    }

    /**
     * Eine Wand aus Tafeln schreibt einmal, nicht sechsmal.
     *
     * <p>Sechs Tafeln mit demselben Text untereinander sind kein
     * Bildschirm, sondern sechs Zettel. Geschrieben wird von der Tafel
     * unten links, und zwar über die ganze Fläche.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void awallOfDisplaysWritesOnce(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        // Drei breit, zwei hoch, alle nach Norden — und nur die erste
        // berührt das Kabel.
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

        // Nur eine bekommt einen Namen — die Wand heißt trotzdem so.
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

        // Und der Rahmen fällt weg, wo eine zweite Tafel anschließt: Die
        // mittlere der unteren Reihe hat links, rechts und oben Nachbarn.
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
     * Eine benannte Tafel ohne Programmstück steht trotzdem im Reiter.
     *
     * <p>Vorher listete er nur die Deklarationen. Eine Tafel, die man
     * benannt hat und die das Programm nicht kennt, war damit nirgends zu
     * sehen — sie sagte es nur selbst auf ihrer Front, und die hängt
     * womöglich drei Räume weiter.
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

        // Ohne Namen: eine Zeile, dass da etwas namenlos hängt.
        helper.assertTrue(entity.displayPanels().stream()
                        .anyMatch(panel -> panel.lines().stream()
                                .anyMatch(zeile -> zeile.contains("ohne Namen"))),
                "die namenlose Tafel fehlt: " + entity.displayPanels());

        // Benannt, aber ohne Programmstück: mit Namen und Hinweis.
        var panel = (dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity)
                helper.getBlockEntity(tafel);
        panel.setDisplayName("wand");
        helper.assertTrue(entity.displayPanels().stream()
                        .anyMatch(entry -> entry.name().equals("wand")),
                "die benannte Tafel fehlt: " + entity.displayPanels());

        // Sobald das Programm sie kennt, steht sie als Anzeige da und nicht
        // mehr als Hinweis.
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
     * Über eine Anzeige wachsen zwei Farben nicht zusammen.
     *
     * <p>Sie leitet mit der Farbe, mit der sie erreicht wurde. Wäre sie
     * farbneutral, hinge an jeder Wand ein Loch in der Trennung, das man
     * beim Bauen nicht sieht.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void adisplayDoesNotBridgeColours(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        // Roter Strang nach Osten, dahinter eine Anzeige, dahinter blau.
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

    /**
     * Ein Laufwerk ohne Kanal lagert nichts.
     *
     * <p>Das ist die Folge daraus, dass es einen kostet: Wer den Strang voll
     * macht, hängt zwar noch ein Laufwerk daran, aber es gehört nicht mehr
     * zum Netz. Sonst wäre die Kanalgrenze für Lagerraum eine Anzeige ohne
     * Wirkung.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void adriveWithoutAChannelStoresNothing(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());

        // Ein Gerät, das den dünnen Strang allein füllt.
        helper.setBlock(cable.north(), FnBlocks.CONNECTOR.get());
        name(helper, cable.north(), "vielfrass");
        if (helper.getBlockEntity(cable.north()) instanceof ConnectorBlockEntity gross) {
            gross.setChannelCost(dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_THIN);
        }
        driveWithCell(helper, cable.south(),
                dev.devpanda.factorynetwork.storage.CellTier.K64);

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertValueEqual(entity.graph().drives().size(), 0,
                "das Laufwerk bekommt keinen Kanal mehr");
        helper.assertTrue(entity.graph().starvedConnectors()
                        .contains(helper.absolutePos(cable.south())),
                "und steht als leer ausgegangen im Bild");
        helper.assertValueEqual(entity.storage().insert(Items.IRON_INGOT, 5), 5L,
                "ohne Kanal nimmt es nichts an");
        helper.succeed();
    }

    // ---- Strom -------------------------------------------------------------

    /** Kurz für die Stromwerte des Netzes. */
    private static dev.devpanda.factorynetwork.network.NetworkPower powerOf(
            ControllerBlockEntity entity) {
        return entity.power();
    }

    /**
     * Jedes Gerät am Netz kostet Strom.
     *
     * <p>Gezahlt wird für Bereitschaft, nicht für Arbeit: Ein Worker, der
     * etwas bewegt, kostet nicht mehr als einer, der wartet. Wer seine Anlage
     * plant, will eine Zahl, die stillsteht.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void everyDeviceOnTheNetworkCostsPower(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        BlockPos cable = controller.east();
        helper.setBlock(cable, FnBlocks.CABLE.get());
        helper.setBlock(cable.north(), FnBlocks.CONNECTOR.get());
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
        // Auf eine Zahl festgenagelt, sonst prüft der Test nur seine eigene
        // Rechnung: Stünde DISPLAY auf null, bliebe er grün — obwohl er
        // gerade behauptet, jedes Gerät koste Strom.
        helper.assertValueEqual(erwartet, 8, "Vier für den Controller, je eins für den Rest");
        helper.assertValueEqual(entity.powerDraw(), erwartet, "Bedarf des Netzes in FE je Tick");
        helper.succeed();
    }

    /**
     * Ohne Strom steht das Netz still — und läuft weiter, wo es war.
     *
     * <p>Nichts wird abgebrochen: Ein Ablauf hält zwischen zwei Schritten
     * keine Gegenstände, also kostet das Einfrieren nichts. Ein Stromausfall
     * soll eine Pause sein und kein Verlust.
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
     * Kommt der Strom zurück, fährt das Netz erst hoch.
     *
     * <p>Ohne diese Zeit wäre ein Stromausfall ein Flackern, das niemand
     * bemerkt. Mit ihr merkt man sofort, dass die Versorgung nicht reicht.
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
     * Eine zu schwache Versorgung lässt das Netz aus, statt es blinken zu
     * lassen.
     *
     * <p>Ohne diese Schwelle liefe es an, verbrauchte den Vorrat beim
     * Hochfahren und ginge sofort wieder aus — ein Blinken im
     * Halbminutentakt, das wie ein Fehler aussieht statt wie zu wenig Strom.
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
     * Ein Ablauf übersteht den Stromausfall und macht danach weiter.
     *
     * <p>Das ist der Grund fürs Einfrieren statt Abbrechen: Wer eine Anlage
     * nachts ohne Strom lässt, findet sie morgens dort, wo sie
     * stehengeblieben ist. Auch ein Ereignis, das während des Ausfalls
     * eintrifft, geht nicht verloren — es bleibt liegen und kommt an, sobald
     * das Netz wieder läuft.
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

        // Ein Ereignis während des Ausfalls geht nicht verloren, es bleibt
        // liegen — und kommt an, sobald das Netz wieder läuft.
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
     * Die Brennkammer erzeugt Strom und schiebt ihn in den Controller.
     *
     * <p>Ohne eine Quelle in der Mod selbst wäre die Fertigungskette — Erz,
     * Platte, Kerne, Zellen, Serverbauteile — ohne Fremdmod nicht zu
     * durchlaufen. Diese Prüfung ist der Nachweis, dass sie es ist.
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
     * Ohne Abnehmer legt sie nicht nach.
     *
     * <p>Sonst verbrennt eine Kohle, während niemand etwas abnimmt — und das
     * merkt man erst, wenn der Kohlenstapel weg ist.
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
     * Der Entwurf überlebt das Speichern und hält die Fabrik nicht an.
     *
     * <p>Der eigentliche Zweck der Sache: Wer zwanzig Minuten an einem Worker
     * gebaut hat und dann abstürzt, hatte vorher alles verloren — Übernehmen
     * hätte es gesichert, aber Übernehmen geht nur bei fehlerfreiem Code, und
     * mitten in einer Änderung ist er das nie.
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

        // Ein Entwurf, der nicht übersetzt. Genau der Fall, den es zu
        // sichern gilt.
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

        // Über das Speicherformat und zurück.
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

        // Übernehmen führt beide wieder zusammen.
        helper.assertTrue(entity.deploy("fn fertig() { }"), "das zweite Übernehmen");
        helper.assertValueEqual(entity.draft().source("main.mf"), "fn fertig() { }",
                "nach dem Übernehmen sind Entwurf und laufender Stand dasselbe");
        helper.succeed();
    }

    /**
     * Die Tafel an der Wand zeigt, was im Programm steht.
     *
     * <p><b>Geprüft war bisher nur der Reiter im Terminal.</b> Dass die
     * Tafel selbst zu ihren Zeilen kommt — über ihren Takt, ihren Namen und
     * den Controller, der sie kennt — hing an vier Stellen, die einzeln
     * stimmten und zusammen nie nachgemessen wurden.
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

        // Die Tafel rechnet einmal je Sekunde. Über runAfterDelay und nicht
        // über eine Schleife: Innerhalb eines Ticks steht die Spielzeit still.
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
     * Und sie sagt selbst, warum sie leer ist.
     *
     * <p>Eine schwarze Fläche lässt offen, ob das Netz steht, der Name
     * falsch ist oder das Programm die Tafel nicht kennt. Jeder dieser Fälle
     * hat einen eigenen Satz — auf der Tafel, denn dort sieht man hin.
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
     * Zwei Spieler überschreiben einander nicht.
     *
     * <p>Beide schicken den ganzen Entwurf. Ohne Sperre gewänne, wer zuletzt
     * tippt — auch über eine Datei, die er gar nicht offen hatte.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void twoPlayersDoNotOverwriteEachOther(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
        rackWithServer(helper, controller.west());
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Kennungen statt Attrappen: Ein echter ServerPlayer im Test löst
        // die Beitrittspakete fremder Mods aus, und gebraucht werden hier
        // eine Kennung und ein Name.
        var anna = java.util.UUID.randomUUID();
        var bert = java.util.UUID.randomUUID();

        var start = new dev.devpanda.factorynetwork.lang.Project(java.util.Map.of(
                "main.mf", "fn eins() { }",
                "worker.mf", "fn zwei() { }"));
        entity.acceptDraft(start, null, null);

        // Anna schreibt main.mf und hält sie damit.
        entity.acceptDraft(start.with("main.mf", "fn eins() { let a = 1 }"),
                anna, "Anna");
        helper.assertValueEqual(entity.draft().source("main.mf"), "fn eins() { let a = 1 }",
                "Annas Änderung");

        // Bert schickt seinen ganzen Entwurf — mit einem alten Stand von
        // main.mf und einer eigenen Änderung an worker.mf.
        entity.acceptDraft(start.with("worker.mf", "fn zwei() { let b = 2 }"),
                bert, "Bert");

        helper.assertValueEqual(entity.draft().source("main.mf"), "fn eins() { let a = 1 }",
                "Annas Datei bleibt stehen");
        helper.assertValueEqual(entity.draft().source("worker.mf"), "fn zwei() { let b = 2 }",
                "Berts eigene Änderung kommt an");

        // Und Bert sieht, wer main.mf hält.
        helper.assertValueEqual(entity.locksFor(bert).get("main.mf"), "Anna",
                "der Halter wird gemeldet");
        // Und was hier steht, ist nicht, was Bert geschickt hat: Sein
        // Entwurf trug einen alten Stand von main.mf. Genau deshalb bekommt
        // er den Zustand zurück, obwohl er der Absender war.
        helper.assertTrue(!entity.draft().source("main.mf").equals("fn eins() { }"),
                "Berts alter Stand darf nicht gewonnen haben");
        helper.assertTrue(entity.locksFor(anna).get("main.mf") == null,
                "die eigene Sperre ist keine Nachricht");
        helper.succeed();
    }

    /**
     * Eine frisch angelegte Datei kommt an, obwohl sie leer ist.
     *
     * <p>Der erste Anlauf verglich sie mit dem, was der Server hat — und der
     * hat für eine unbekannte Datei den leeren Text. Damit sah jede neue
     * Datei wie eine unveränderte aus und fiel durch.
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
     * Ein Worker mit einer Textbedingung schaltet wirklich ab.
     *
     * <p><b>Der Test, der lange gefehlt hat.</b> {@code when} hatte einen
     * eigenen kleinen Auswerter, der nur Zahlen konnte — alles andere galt
     * als wahr. {@code when modus == "tag"} lief damit rund um die Uhr, und
     * die Doku versprach das Gegenteil. Hier steht jetzt, was gelten soll.
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

        // Erster Durchgang: Der Modus ist „nacht", der Worker muss schlafen.
        entity.serverTick();
        var zustand = entity.runtime().states().get("liefern");
        helper.assertTrue(zustand != null, "den Worker gibt es nicht");
        helper.assertTrue(zustand.status == WorkerRuntime.Status.WAITING_CONDITION,
                "erwartet WAITING_CONDITION, war " + zustand.status
                        + " (" + zustand.detail + ")");
        helper.assertValueEqual(entity.storage().count(Items.IRON_INGOT), 64L,
                "es darf nichts bewegt worden sein");

        // Umschalten — jetzt muss er laufen.
        //
        // Mit Abstand: Ein Worker ohne rate läuft alle zwanzig Ticks, und ein
        // zweiter Tick im selben Augenblick würde übersprungen. Er behielte
        // dann seinen alten Zustand, und der Test prüfte nichts.
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
     * Eine Menge vor einer Schleifenvariablen gilt wirklich.
     *
     * <p><b>Der gefährlichste Fehler, den diese Sprache hatte.</b>
     * {@code move 8 sorte} bewegte alles statt acht, weil die Menge nur auf
     * geschriebene Auswahlausdrücke gesetzt wurde — eine Schleifenvariable
     * ist ein aufgelöster Wert. Das Programm sah dabei aus, als täte es, was
     * dasteht, und räumte das Lager leer.
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
     * Eine Anzeige nennt einen globalen Wert beim Namen.
     *
     * <p>Geprüft wird der Text selbst und nicht, ob überhaupt etwas dasteht —
     * ein Fragezeichen ist auch etwas, und genau das kam vorher heraus.
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

    /** Der Analysator sagt, was an einem Gerät hängt — und wie viele Fächer. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theAnalyserNamesWhatADeviceCan(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos connector = controller.east().north();
        if (!(helper.getBlockEntity(connector) instanceof ConnectorBlockEntity verbunden)) {
            helper.fail("Am Connector hängt keine BlockEntity", connector);
            return;
        }
        var profil = DeviceScan.of(verbunden);

        helper.assertValueEqual(profil.abilities(), "Gegenstände",
                "An einer Kiste hängen Gegenstände");
        helper.succeed();
    }

    /**
     * Die Fachnummern im Editor sind die, die das Programm anspricht.
     *
     * <p>Eine Seite zeigt ihre Fächer unter eigenen Nummern; {@code slots(3)}
     * meint das dritte Fach der Maschine. Zeigte der Tooltip die Nummern der
     * Seite, wiese die Auskunft woandershin, als sie greift.
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
     * <b>In ein bestimmtes Fach legen.</b>
     *
     * <p>Die Kehrseite von {@link #movingOnlyFromCertainSlots}: Wer eine
     * Fachnummer schreibt, legt auch dorthin — am ungeteilten Inventar, also
     * ohne die Seitenregeln der Maschine. Genau dafür ist die Form da (ein
     * Anschluss je Maschine, der Brennstoff kommt trotzdem ins Brennstofffach),
     * und genau deshalb steht der Preis in sprache.md.
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
     * <b>Nur den Ausgang abräumen.</b>
     *
     * <p>Der Fall, für den es die Fächer gibt: Eine Maschine, die Eingang und
     * Ausgang im selben Inventar hält, und ein move, das den Eingang stehen
     * lässt. Ein zweiter Connector an einer anderen Seite ist ausdrücklich
     * nicht die Antwort — ein Anschluss je Maschine soll reichen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void movingOnlyFromCertainSlots(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        BlockPos quelle = controller.east().north().north();
        if (helper.getBlockEntity(quelle) instanceof ChestBlockEntity container) {
            // Fach 0 ist der „Eingang", Fach 3 der „Ausgang".
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
     * {@code slots(…)} liest gezielt einzelne Fächer.
     *
     * <p>Über das ganze Inventar: Ein Anschluss je Maschine soll reichen,
     * und welches Fach gemeint ist, entscheidet der Code.
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
     * Eine Tafel darf in eine Maschine sehen.
     *
     * <p>Der Preis ist ein Blick in eine BlockEntity je Tafel und Sekunde —
     * die Anzeige liest den Netzbestand ohnehin in diesem Takt. Ein `?` auf
     * der Tafel, das niemand erklären kann, wäre der schlechtere Tausch.
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

    // ---- Strom verteilen ---------------------------------------------------

    /**
     * Das Netz versorgt eine Maschine.
     *
     * <p>Die Presse ist die einzige Maschine der Mod, die Strom annimmt — und
     * damit die ehrlichste Prüfstrecke: Was hier ankommt, ist wirklich
     * angekommen.
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

    /** Und andersherum: aus einer Maschine ins Netz. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void amachineFeedsTheNetwork(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // Die Kreativquelle ist die einzige Stromquelle dieser Mod, die
        // etwas hergibt: Eine Maschine ist kein Akku, und die Presse
        // verweigert die Entnahme mit Absicht.
        BlockPos quelle = controller.east().north().north();
        helper.setBlock(quelle, FnBlocks.CREATIVE_SOURCE.get());
        entity.rebuildNetwork();
        // Der Puffer startet voll; ohne Platz darin fließt nichts hinein.
        // Entnommen statt geleert: empty() schaltet das Netz ab, und ein
        // abgeschaltetes Netz lässt keine Worker laufen.
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
     * Bei Knappheit gilt die Reihenfolge der {@code priority}.
     *
     * <p>Zwei Worker, einer mit Vorrang, und weniger Strom, als beide
     * zusammen wollen. Der mit der kleinen Zahl läuft voll, der andere geht
     * leer aus — nicht beide halb.
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
                    // Knapp, aber laufend: Was übrig bleibt, reicht für ein
                    // paar Würfe und nicht für beide Worker.
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
     * Die Abgabe steht als eigene Zahl im Netz-Reiter.
     *
     * <p>Ohne sie sieht ein Netz, das vierzig FE je Tick durchreicht, aus wie
     * eines, das nichts tut: Der Bedarf zählt sie nicht mit, und der Vorrat
     * steht still, solange genug nachkommt.
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
     * {@code list} ist eine Aufzählung, keine Zeile.
     *
     * <p>Die Spezifikation nennt es „Aufzählung, etwa Bestände oder
     * Aufträge". Gezeichnet wurde bis dahin eine einzelne Zeile wie bei
     * {@code row} — mit einem Text darin, den niemand lesen wollte.
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
            // Eine Überschrift und zwei Posten — nicht eine Zeile für alles.
            helper.assertValueEqual(lines.size(), 3, "Zeilen auf dem Display: " + lines);
            helper.assertTrue(lines.stream().anyMatch(line -> line.contains("64")),
                    "Die Menge des Eisens fehlt: " + lines);
            helper.assertTrue(lines.stream().anyMatch(line -> line.contains("32")),
                    "Die Menge des Goldes fehlt: " + lines);
            helper.succeed();
        });
    }

    /** Ein leerer Bestand sagt es, statt eine leere Zeile zu zeichnen. */
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
     * Ein Knopf hinter einer Aufzählung trifft trotzdem.
     *
     * <p>Der Reiter nimmt die Nummer, die im Paket steht, und schickt sie
     * zurück — genau das prüft dieser Test, statt eine Nummer zu erfinden.
     * <b>Solange jeder Eintrag genau eine Zeile war, waren beide Nummern
     * dieselbe</b>, und der Unterschied fiel niemandem auf. Eine Aufzählung
     * bringt mehrere Zeilen mit, und ab da zeigt eine Zeilennummer auf einen
     * anderen Eintrag.
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

        // Was der Reiter beim Klicken schickt.
        entity.pressDisplayButton("leitstand", panel.buttons().get(0).entry());

        helper.assertValueEqual(entity.flowEngine().flows().size(), 1,
                "Der Knopf muss treffen, auch mit einer Aufzählung darüber");
        helper.succeed();
    }

    /**
     * {@code brecher.energy()} liest den Stromstand einer Maschine.
     *
     * <p>Mit Klammern wie {@code redstone()} und {@code count()}: Es ist ein
     * Blick in die Welt und kein Name, den das Programm ohnehin kennt.
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

    /** Eine Maschine ohne Stromspeicher meldet null und keinen Fehler. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void adeviceWithoutEnergySaysZero(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // An quarry_output hängt eine Kiste. Sie hat keinen Strom, und das
        // ist kein Programmfehler, sondern eine Kiste.
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

    // ---- Alles von A nach B ------------------------------------------------

    /**
     * {@code move all} nimmt, was auch immer darin liegt.
     *
     * <p>Ein Worker ohne {@code filter} konnte das seit jeher; in einer
     * Funktion gab es keine Schreibweise dafür. Aufgefallen ist die Lücke beim
     * Streichen von {@code output()}.
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

    /** Auch aus dem Netzspeicher — dort steht sonst „sag, was bewegt wird". */
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

    /** Mit Menge davor: {@code move 8 all} nimmt acht Stück von irgendwas. */
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
     * {@code all except …} räumt alles bis auf eines ab.
     *
     * <p>Der natürlichste Gebrauch von {@code all}, und die Grammatik erlaubt
     * ihn: {@code selection = selTerm { 'except' selTerm }}. Aufgelöst wird
     * die Ausnahme gegen das, was wirklich dasteht — gegen die Registry ginge
     * es nicht, denn „alles" ist dort jeder Gegenstand des Packs.
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

    /** {@code filter all} in einem Worker ist dasselbe wie kein Filter. */
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

    /** {@code insert(all)} legt hinein, was der Speicher hergibt. */
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
     * Die Annahme-Probe gilt auch für Behälter.
     *
     * <p>Dieselbe Frage wie bei den Fächern, dieselbe Antwort: Ein
     * {@code IFluidHandler} kann nicht sagen, was er annimmt. Also wird
     * gefragt — mit den Flüssigkeiten, die im Entwurf stehen, und mit
     * {@code fill(…, SIMULATE)}, das nichts bewegt.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theProbeAlsoAsksTheTanks(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // Vor dem Connector steht ein leerer Kessel statt der Kiste.
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

    /** Eine Kiste hat keine Behälter und sagt dazu nichts. */
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

    // ---- Worauf sich ein Muster auflöst -------------------------------------

    /**
     * Ein Muster sagt, was es trifft.
     *
     * <p>Ohne diese Auskunft ist {@code maintain 64 tag:c/ores} eine Zusage
     * ins Blaue: Gehalten werden vierundsechzig <b>je Art</b>, und wie viele
     * Arten das sind, weiß nur das Pack.
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

    /** Ein einzelner Gegenstand trifft genau eine Art. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void asingleItemHitsOne(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("item:iron_ore"));

        helper.assertValueEqual(summary.get(0), "trifft 1 Art", "die Zahl");
        helper.succeed();
    }

    /**
     * Mit Mekanism geht eine Chemikalien-Auswahl durch.
     *
     * <p>Der Test hieß einmal „ohne Mekanism sagt die Meldung, dass Mekanism
     * fehlt", und er hat zweimal die Wahrheit gewechselt: erst, als die
     * Abhängigkeit in den Prüflauf kam, dann, als die Anbindung stand. Jetzt
     * prüft er, was ab hier gilt — mit Mekanism ist {@code chemical:} eine
     * Auswahl wie jede andere.
     *
     * <p>Der Fall ohne Mekanism steht als Einheitstest in
     * {@code FilterCheckTest}, wo keine Modliste geladen wird.
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

        // An depot hängt eine Kiste und kein Tank — es kommt nichts, aber es
        // wirft auch nichts. Ein Gerät ohne Chemikalien ist keine
        // Fehlermeldung, sondern ein leeres Gerät.
        // An depot hängt eine Kiste, also kommt nichts — aber es wirft auch
        // nichts, und das ist der Unterschied zu vorher.
        entity.callFunction("holen", List.of());

        // Und eine Vorlage nimmt chemical: jetzt an, statt es abzulehnen.
        helper.assertTrue(entity.deploy("""
                filter gase {
                    chemical:mekanism/hydrogen
                }"""), "Mit Mekanism gehört chemical: in eine Vorlage");
        helper.succeed();
    }

    /**
     * Eine Chemikalie ist ein Wert und nicht nur eine Auswahl.
     *
     * <p>Was hier zählt, ist die <b>Auflösung gegen die Registry</b>: Sie
     * gehört Mekanism, und ohne die Mod gibt es sie nicht — deshalb steht
     * dieser Fall im Prüflauf und nicht im Einheitstest. Was danach kommt,
     * die Sorte an einem Posten abzulesen, steht dort.
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
     * Und ein Ausschluss darüber liefert eine Chemikalie, keine Zahl.
     *
     * <p>{@code except} ist der Weg, auf dem eine Chemikalienauswahl schon
     * vor dem Bewegen aufgelöst wird. Bleibt genau eine Sorte übrig, steht
     * sie mit {@code .chemical} da — dieselbe Regel wie bei
     * {@code it.item} und {@code it.fluid}.
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
                                == dev.devpanda.factorynetwork.runtime.ResourceKind.CHEMICAL,
                "und eine Chemikalie liefern: " + flow.result().describe());
        helper.succeed();
    }

    /**
     * Chemikalien gehen aus dem Netz in einen Behälter und wieder zurück.
     *
     * <p><b>Der Behälter ist keiner in der Welt, sondern einer aus Mekanisms
     * API.</b> Das ist kein Ausweichen, sondern die Folge einer Messung: Ein
     * Chemikalientank, den ein Prüflauf per {@code setBlock} hinstellt, gibt
     * an <b>keiner</b> Seite eine Capability heraus und nimmt auch ungeteilt
     * nichts an — ihm fehlt die Seitenkonfiguration, die ein Spieler beim
     * Platzieren mitbringt. Nachgemessen mit allen sechs Seiten.
     *
     * <p>Was hier eigen ist und deshalb geprüft wird, ist das Hin und Her mit
     * dem Netzspeicher: erst proben, dann entnehmen, und was der Behälter
     * doch nicht nimmt, zurücklegen. Wie der Behälter gefunden wird, ist
     * derselbe Weg wie bei Flüssigkeiten.
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
     * Was der Speicher nicht fasst, bleibt im Behälter.
     *
     * <p>Die Zusage, die bei Flüssigkeiten schon gilt: Ein Gas, das draußen
     * ist und nirgends hineinpasst, wäre weg.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void whatThestoreCannotHoldStaysInThetank(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // Die kleinste Zelle: 64.000 mB.
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
     * Ein Chemikalien-Worker braucht ein filter.
     *
     * <p>Dieselbe Regel wie bei Flüssigkeiten und aus demselben Grund: Ein
     * Behälter hält meist genau eine Sorte, und die falsche zu ziehen ist
     * teurer als bei Gegenständen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void achemicalWorkerNeedsAfilter(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Ohne filter ist der Worker gar kein Chemikalien-Worker — er fällt
        // in den Gegenstandszweig. Also mit filter, aber ohne Ziel im Netz:
        // Auch das muss eine Meldung geben und keinen stillen Stillstand.
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
     * Und er geht nur zwischen Gerät und Speicher.
     *
     * <p>Von Gerät zu Gerät läuft es über den Speicher; dafür schreibt man
     * zwei Worker. Ein dritter Weg für denselben Vorgang wäre eine dritte
     * Stelle, an der eine Menge unterwegs verlorengehen kann.
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

    /** Setzt ein Laufwerk ans Kabel und steckt eine Chemikalienzelle hinein. */
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
     * Eine Chemikalienzelle hält Chemikalien.
     *
     * <p>Dieselben zwei Grenzen wie überall — so viele Sorten, so viel Menge —
     * und dieselbe Rechnung dahinter: Sie stand seit den Flüssigkeiten offen
     * für den Typ, und Chemikalien haben nichts daran geändert.
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

        // Die kleinste Zelle fasst 64.000 mB; was darüber geht, bleibt draußen.
        helper.assertValueEqual(entity.chemicals().insert("mekanism:oxygen", 64_000), 1000L,
                "was über die Menge geht, bleibt draußen");

        helper.assertValueEqual(entity.chemicals().extract("mekanism:hydrogen", 400), 400L,
                "und es kommt wieder heraus");
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 600L,
                "der Rest bleibt liegen");
        helper.succeed();
    }

    /** Ohne Laufwerk lagert das Netz keine Chemikalien. */
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
     * Der Inhalt fährt in der Zelle mit.
     *
     * <p>Der Grund, warum eine Zelle etwas wert ist — und derselbe wie bei den
     * anderen beiden Arten.
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
                .read(drive.cell(0));
        helper.assertValueEqual(inhalt.getOrDefault("mekanism:hydrogen", 0L), 2500L,
                "Die Zelle trägt ihren Inhalt selbst");

        drive.setCell(0, ItemStack.EMPTY);
        entity.rebuildNetwork();
        helper.assertValueEqual(entity.chemicals().count("mekanism:hydrogen"), 0L,
                "Ohne die Zelle ist der Bestand weg");
        helper.succeed();
    }

    /**
     * Eine Chemikalien-Auswahl löst sich auf.
     *
     * <p>Mekanism liegt im Prüflauf, also gibt es Wasserstoff. Ohne Mekanism
     * ist die Liste leer und die Meldung sagt es — der Fall steht als
     * Einheitstest da.
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

    /** Ein Muster trifft mehrere, und ein Name, den es nicht gibt, keine. */
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

    /** Und der Editor zeigt, worauf sie sich auflöst. */
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
     * Was nichts trifft, sagt das.
     *
     * <p>Die häufigste Ursache ist ein Tag, den dieses Pack nicht kennt — und
     * der sieht im Editor aus wie jeder andere.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void whatHitsNothingSaysSo(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("item:gibtsnicht"));

        helper.assertValueEqual(summary.get(0), "trifft nichts", "die Auskunft");
        helper.succeed();
    }

    /** Flüssigkeiten gehen denselben Weg. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void fluidsGoTheSameWay(GameTestHelper helper) {
        var summary = dev.devpanda.factorynetwork.runtime.SelectionSummary.of(
                dev.devpanda.factorynetwork.lang.Selectors.parse("fluid:water"));

        helper.assertValueEqual(summary.get(0), "trifft 1 Art", "die Zahl");
        helper.assertTrue(summary.size() > 1, "und der Name: " + summary);
        helper.succeed();
    }

    // ---- Fertigung ---------------------------------------------------------

    /**
     * Ein Auftrag über vierundsechzig Truhen zieht Bretter und liefert Truhen.
     *
     * <p><b>Einstufig</b>: Der Fabricator baut, was er aus dem Speicher bauen
     * kann. Fehlen Bretter, macht er keine aus Stämmen — das kommt später und
     * ist ein bewusster Schnitt, kein Mangel.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void afabricatorCraftsFromStock(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // Acht Bretter je Truhe, vierundsechzig Truhen.
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
     * Ohne Zutaten wartet der Auftrag und sagt, was fehlt.
     *
     * <p>Dieselbe Ehrlichkeit wie bei einem Worker vor einer vollen Kiste:
     * Ein Auftrag, der nichts tut, muss den Grund nennen.
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

    /** Ohne Fabricator im Netz wird gar nichts gefertigt. */
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
                    // Auf den Grund geprüft und nicht auf den Zustand: WAITING
                    // steht schon beim Anlegen da, der Satz erst nach dem
                    // ersten Takt.
                    helper.assertValueEqual(entity.craftingJobs().get(0).detail(),
                            "kein Fabricator im Netz", "der Auftrag sagt, woran es liegt");
                })
                .thenSucceed();
    }

    /** Ein Rezept, das es nicht gibt, wird gar nicht erst angenommen. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void arequestWithoutArecipeIsRefused(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();

        // Bruchstein hat kein Rezept — er kommt aus der Welt.
        helper.assertTrue(entity.requestCraft(Items.COBBLESTONE, 1) == null,
                "ohne Rezept darf kein Auftrag entstehen");
        helper.assertTrue(entity.craftingJobs().isEmpty(), "und keiner in der Liste stehen");
        helper.succeed();
    }

    /**
     * Ein Auftrag übersteht den Neustart.
     *
     * <p>Das ist der ganze Grund, warum er am Controller lebt und nicht am
     * Gerät: Wer eine Bestellung über zehntausend Barren aufgibt und den
     * Server neu startet, will sie wiederfinden.
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

    /** Ein fertiger Auftrag meldet sich als Ereignis. */
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
     * {@code craft(64 item:chest)} bestellt aus dem Programm.
     *
     * <p>Der Weg, der zu dieser Mod gehört: Ein Netz tut nichts von selbst,
     * also wird auch eine Bestellung geschrieben und nicht geklickt. Der
     * Reiter zeigt danach, was daraus wurde.
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

    /** Ohne Rezept liefert {@code craft} eine Null und legt nichts an. */
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
     * Der Reiter bekommt die Aufträge als fertige Zeilen.
     *
     * <p>Geprüft wird nicht das Zeichnen, sondern das, was hinübergeht: Der
     * Name des Ziels als Text, die Zahlen, der Zustand und der Grund.
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

    /** Ein abgebrochener Auftrag ist weg — das Gebaute bleibt. */
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
     * Stellt einen Ofen mit Brennstoff an einen Connector des Netzes.
     *
     * <p>Der Brennstoff gehört dem Spieler: Das Netz legt die Zutat ein und
     * holt das Ergebnis, aber es heizt nicht. Wer will, dass es heizt,
     * schreibt einen Worker.
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
     * Ein Ofen im Netz schmilzt für einen Fertigungsauftrag.
     *
     * <p>Das ist der Unterschied zwischen Werkbank und Maschine: Ein
     * Werkbank-Rezept ist in einem Zug erledigt, ein Ofenrezept braucht
     * Zeit. Der Auftrag legt ein, wartet und holt ab — und dazwischen tut er
     * nichts anderes.
     */
    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void anovenInThenetworkSmeltsForAjob(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // quarry_output zeigt nach Norden; dort stand eine Kiste.
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
     * Was im Ofen liegt, übersteht den Neustart.
     *
     * <p><b>Der Unterschied zum Plan.</b> Den rechnet der Controller bei
     * jedem Takt neu, weil er nur eine Absicht ist. Ein laufender Schritt ist
     * eine Tatsache über die Welt: Die Zutaten liegen im Ofen. Wer das
     * vergisst, hat sie verloren und legt beim nächsten Mal neue nach.
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
     * Ein Rezept aus dem Programm zieht seine Zutaten in die Maschine.
     *
     * <p>Geprüft an einer Kiste, und das ist Absicht: Sie ist keine Maschine,
     * aber sie nimmt an und gibt heraus — genau die beiden Eigenschaften, auf
     * die sich ein erklärtes Rezept verlässt. Eine echte Fremdmod-Maschine
     * steht in keinem Prüflauf zur Verfügung; was hier zählt, ist der Weg der
     * Gegenstände.
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
                    // Das Erz ist aus dem Speicher in die Kiste gewandert, und
                    // der Auftrag wartet auf das, was zurückkommen soll.
                    helper.assertValueEqual(entity.storage().count(Items.IRON_ORE), 3L,
                            "ein Erz ist in die Maschine gegangen");
                    var job = entity.craftingJobs().get(0);
                    helper.assertTrue(job.running() != null,
                            "der Auftrag muss auf die Maschine warten: " + job.detail());
                    helper.assertValueEqual(job.running().device(), "quarry_output",
                            "und wissen, an welcher");

                    // Jetzt liefert die „Maschine": Was sie herausgibt, holt
                    // das Netz von selbst ab.
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
     * Fehlt die Flüssigkeit eines Rezepts, wartet der Auftrag und rührt nichts an.
     *
     * <p>Der Schnitt, um den es geht: Ein Rezept darf {@code in 1000
     * fluid:water} sagen. Der Planner rechnet damit nicht — Flüssigkeiten
     * werden nicht beschafft —, aber der Ausführende muss sie beim Anfangen
     * einfüllen. Und wenn er das nicht kann, darf er die Gegenstände nicht
     * schon einmal hineinlegen: Eine Maschine mit vier Erzen und ohne Wasser
     * fängt nie an, und das Erz wäre aus dem Netz verschwunden.
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
                    // Auf Deutsch heißt sie Wasser, im Prüflauf ohne
                    // Sprachdateien Water — geprüft wird beides, wie beim Ofen.
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
     * Und ebenso, wenn die Maschine die Flüssigkeit gar nicht nimmt.
     *
     * <p>Geprüft an einer Kiste: Sie nimmt Gegenstände an und hat keinen
     * Tank — der Fall, in dem jemand {@code fluid:} an ein Gerät schreibt,
     * das damit nichts anfangen kann. Auch dann bleibt beides liegen, wo es
     * liegt, und die Meldung nennt die Sorte statt nur „geht nicht".
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

    /** Ein Rezept an einem Gerät, das es nicht gibt, meldet sich beim Übernehmen. */
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

    /** Ohne Ofen im Netz wartet der Auftrag und sagt es. */
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
     * Was fehlt, baut das Netz selbst.
     *
     * <p>Der Schnitt, der vorher hier lag: Ein Auftrag über eine Truhe stand
     * still und meldete „es fehlen 8 Bretter", während im Laufwerk Stämme
     * lagen und der Weg dahin ein einziges Rezept war.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void amissingIngredientIsCraftedInTurn(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // Zwei Stämme sind acht Bretter sind eine Truhe.
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
     * Das Netz nimmt das Holz, das es hat.
     *
     * <p>Eine Zutat ist eine Auswahl — {@code #planks} und nicht
     * „Eichenbrett". Wer sich beim Planen auf die erste Sorte festlegt,
     * meldet einem Spieler mit einem Laufwerk voll Fichtenstämmen, es fehlten
     * ihm Eichenbretter.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thenetworkTakesTheWoodItHas(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        helper.setBlock(controller.east().above(), FnBlocks.FABRICATOR.get());
        entity.rebuildNetwork();
        // Keine Eiche weit und breit.
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
     * Die Fehlzeile nennt den Grundstoff, nicht die Zwischenstufe.
     *
     * <p>„Es fehlen 8 Bretter" hilft niemandem, der Bretter herstellen kann.
     * Gesucht ist das, was jemand hinlegen muss.
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
     * Nachschub ist derselbe Worker.
     *
     * <p>Der Grund, warum {@code from} eine Quelle nennt und keine Betriebsart:
     * „hol es aus dem Lager" und „lass es herstellen" bekommen dieselbe Form.
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
     * Und er bestellt genau einmal.
     *
     * <p>Der Bestand steigt erst, wenn der Auftrag fertig ist. Ein Worker, der
     * nur den Bestand ansieht, bestellt in der Zwischenzeit jede Runde neu —
     * und aus „halte vier vor" werden vierzig.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void acraftingWorkerOrdersOnlyOnce(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        // Kein Fabricator: Der Auftrag steht und wird nie fertig.
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

    /** Steht der Vorrat, bestellt er nichts. */
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
     * Gefertigt wird in den Speicher, nicht in eine Maschine.
     *
     * <p>Der Weg dahin steht schon: Ein zweiter Worker holt es aus dem Lager
     * und legt es in die Maschine. Beides in eine Zeile zu ziehen hieße, dem
     * Fabricator ein Ziel beizubringen, das er nicht hat.
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

    /** Ohne {@code maintain} weiß niemand, wie viel bestellt werden soll. */
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

    /** Was kein Rezept hat, wird nicht bestellt — und der Worker sagt es. */
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
     * Der Rückweg der Brücke: Was das Spiel weiß, steht neben den Dateien.
     *
     * <p>Hin funktioniert sie längst — wer in VS Code speichert, dessen
     * Programm übernimmt der Controller. Zurück kam bisher nichts: Ein Fehler
     * stand im Terminal, und wer nicht im Spiel war, sah eine Datei, die
     * stumm nicht lief.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thegameWritesWhatItKnowsNextToThefiles(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Ein Programm mit einem Gerätenamen, den es nicht gibt: Der
        // Übersetzer warnt, und genau das soll draußen ankommen.
        helper.assertTrue(entity.deploy("""
                fn holen() {
                    move 64 item:iron_ore from kist to depot
                }"""), "Eine Warnung hält das Programm nicht auf");

        helper.startSequence()
                // Der Ordner entsteht erst beim ersten Blick, und der kommt
                // im Sekundentakt.
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

    // ---- Der Anbau am Controller -------------------------------------------

    /**
     * Ein Kabel am Anbau gehört zum Netz.
     *
     * <p>Der Anbau bringt Seiten mit, und an einer Seite hängt ein Strang wie
     * an jeder Seite des Controllers. Ohne das wäre er ein Zierblock.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void acableOnTheExtensionBelongsToTheNetwork(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // Nach Norden: Anbau, Kabel, Connector — der Controller selbst wird
        // dabei nie berührt.
        BlockPos anbau = controller.north();
        helper.setBlock(anbau, FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(anbau.north(), FnBlocks.CABLE.get());
        BlockPos connector = anbau.north().north();
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING, Direction.NORTH));
        helper.setBlock(connector.north(), Blocks.CHEST);
        name(helper, connector, "am_anbau");
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("am_anbau"),
                "Der Connector am Anbau muss im Netz hängen, gefunden wurden: "
                        + entity.graph().connectorNames());
        helper.succeed();
    }

    /**
     * Ein Anbau ohne Controller daneben tut nichts.
     *
     * <p><b>Der Anbau muss den Controller berühren</b> — unmittelbar oder über
     * andere Anbauten. Ließe er sich über ein Kabel anschließen, wäre er ein
     * beliebig oft setzbarer Kanalvermehrer: sechs neue Seiten für einen
     * Block, und die Kanalgrenze bedeutete nichts mehr.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anextensionAtTheEndOfACableIsNothing(GameTestHelper helper) {
        BlockPos controller = bareSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);

        // Ein Kabel vom Controller zum Anbau, und vom Anbau soll es
        // weitergehen — soll es aber nicht.
        BlockPos anbau = controller.north().north();
        helper.setBlock(controller.north(), FnBlocks.CABLE.get());
        helper.setBlock(anbau, FnBlocks.CONTROLLER_EXTENSION.get());
        helper.setBlock(anbau.east(), FnBlocks.CABLE.get());
        BlockPos connector = anbau.east().east();
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING, Direction.EAST));
        helper.setBlock(connector.east(), Blocks.CHEST);
        name(helper, connector, "hinter_dem_anbau");
        entity.rebuildNetwork();

        helper.assertFalse(entity.graph().connectorNames().contains("hinter_dem_anbau"),
                "Hinter einem angekabelten Anbau darf nichts hängen");
        helper.succeed();
    }

    /** Zwei Anbauten hintereinander reichen die Seiten weiter. */
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
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(dev.devpanda.factorynetwork.block.ConnectorBlock.FACING, Direction.EAST));
        helper.setBlock(connector.east(), Blocks.CHEST);
        name(helper, connector, "am_zweiten");
        entity.rebuildNetwork();

        helper.assertTrue(entity.graph().connectorNames().contains("am_zweiten"),
                "Auch der zweite Anbau bringt Seiten mit");
        helper.succeed();
    }

    /** Der Anbau kostet Strom wie jedes andere Bauteil am Netz. */
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

    // ---- Energiezellen -----------------------------------------------------

    /** Das Laufwerk im Standardaufbau, samt einer Energiezelle im zweiten Platz. */
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
     * Eine Energiezelle vergrößert den Vorrat.
     *
     * <p>Der Puffer im Controller bleibt, was er war — die Zelle kommt dazu.
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
     * Das Netz läuft auch, wenn nur die Zelle etwas hat.
     *
     * <p><b>Der Kern der ganzen Sache.</b> Ein Vorrat, der nur im
     * Controllerpuffer zählt, lässt ein Netz mit vollen Zellen ausgehen — und
     * zwar mitten im Betrieb, ohne dass irgendwo eine Zahl auf null steht.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thenetworkRunsOnACellAlone(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        var zelle = energyCell(helper, controller,
                dev.devpanda.factorynetwork.storage.EnergyCellTier.FE64K);
        entity.rebuildNetwork();
        // Der Puffer auf null, die Zelle voll: Alles, was das Netz hat, liegt
        // im Laufwerk.
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

    /** Was das Netz verbraucht, zahlt am Ende die Zelle. */
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
     * Wer den Puffer füllt, füllt danach die Zellen.
     *
     * <p>Sonst bliebe eine Energiezelle für immer leer: Strom kommt von außen
     * durch den Anschluss am Controller, und der endet ohne diesen Weg am
     * Rand des Puffers.
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
        // Erst auf null, dann den Puffer randvoll: Der Aufbau lässt das Netz
        // laufen und hat ihn dabei schon fast gefüllt.
        entity.power().empty();
        entity.power().fill(Power.CAPACITY);

        // Der Weg von außen: derselbe, den ein fremdes Kabel nimmt.
        int angenommen = entity.power().port().receiveEnergy(1_000, false);

        helper.assertTrue(angenommen == 1_000,
                "Bei vollem Puffer nimmt die Zelle an: " + angenommen);
        helper.assertTrue(zelle.stored() == 1_000,
                "In der Zelle müssen 1000 FE liegen, es sind " + zelle.stored());
        helper.succeed();
    }

    /** Eine Zelle, die herausgeht, nimmt ihre Ladung mit. */
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
        // Der Gegenstand selbst, keine Kopie: Das Zurückschreiben geschieht
        // erst beim Herausnehmen, und eine Kopie von vorher hätte es nicht
        // mitbekommen. Im Spiel wandert genau dieser Gegenstand in die Hand.
        ItemStack heraus = drive.cell(1);
        drive.setCell(1, ItemStack.EMPTY);

        // Der Gegenstand in der Hand: Was jetzt darin steht, ist alles, was
        // von der Ladung bleibt.
        helper.assertTrue(
                dev.devpanda.factorynetwork.storage.EnergyCellItem.chargeOf(heraus) == 12_345,
                "Die Ladung muss im Gegenstand stehen, es sind "
                        + dev.devpanda.factorynetwork.storage.EnergyCellItem.chargeOf(heraus));
        helper.succeed();
    }

    /**
     * Wer eine Zelle leert, sagt dem Laufwerk Bescheid.
     *
     * <p>Die Ladung liegt im Arbeitsspeicher und geht erst beim Sichern in den
     * Gegenstand. Ohne diese Meldung weiß Minecraft nicht, dass der Chunk
     * gesichert werden muss — und ein Laufwerk in einem anderen Chunk als der
     * Controller hätte nach einem Neustart die Ladung von vorhin.
     *
     * <p>Derselbe Grund wie beim Lagerbestand, siehe
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
        // Der Puffer ist leer, also zahlt die Zelle — und zwischen diesen
        // beiden Zeilen läuft nichts anderes.
        entity.power().take(50);

        helper.assertTrue(chunk.isUnsaved(),
                "Das Laufwerk muss als geändert gelten, sonst geht die Ladung "
                        + "beim Entladen des Chunks verloren");
        helper.succeed();
    }

    // ---- Gerätemitglieder --------------------------------------------------

    /**
     * {@code insert} und {@code items} in einer echten Welt.
     *
     * <p>Der Einheitstest prüft die Sprache gegen eine Welt aus Papier; hier
     * geht es um die Frage, ob wirklich Gegenstände wandern.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void insertPutsItemsIntoTheMachine(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Etwas in den Netzspeicher, damit insert etwas zu holen hat.
        entity.storage().insert(Items.IRON_INGOT, 30);

        helper.assertTrue(entity.deploy("""
                fn füllen() {
                    log(depot.insert(20 item:iron_ingot))
                }

                fn zeigen() {
                    log(depot.items())
                }"""), "das Programm wurde nicht übernommen");

        entity.callFunction("füllen", List.of());

        BlockPos connector = entity.graph().connectors().get("depot");
        ConnectorBlockEntity port =
                (ConnectorBlockEntity) helper.getLevel().getBlockEntity(connector);
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
     * Was nicht hineinpasst, ist kein Fehler.
     *
     * <p>Eine volle Maschine meldet Null, und das Programm läuft weiter —
     * dieselbe Regel wie bei {@code move}.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void insertIntoNothingIsZeroAndNoError(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // <b>Der Rückgabewert war die ganze Frage und wurde nie geprüft.</b>
        // Der Test rief die Funktion auf und war zufrieden, dass nichts
        // scheiterte — die Null im Namen sah sich niemand an. Ein Programm,
        // dem insert 20 meldet, obwohl nichts ankam, bucht Bestand ab, den es
        // nie bewegt hat.
        helper.assertTrue(entity.deploy("""
                fn füllen() {
                    return depot.insert(20 item:iron_ingot)
                }"""), "das Programm wurde nicht übernommen");

        // Nichts im Speicher: Es gibt nichts einzulegen.
        long ohneBestand = ((dev.devpanda.factorynetwork.runtime.Value.Int)
                entity.callFunction("füllen", List.of())).value();
        helper.assertValueEqual(ohneBestand, 0L, "Aus einem leeren Speicher kommt nichts");

        // Fünf im Speicher, zwanzig gewünscht: Es können nur fünf werden.
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

    // ---- Globale Werte -----------------------------------------------------

    /**
     * Ein globaler Wert überlebt den Serverneustart.
     *
     * <p>Geprüft wird über denselben Weg, den auch ein Neustart geht:
     * aufschreiben, neue BlockEntity, zurücklesen. Ein Wert, der sagt, in
     * welchem Modus die Fabrik läuft, wäre nach einem Neustart sinnlos, wenn
     * er wieder auf dem Anfangswert stünde.
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

        // Der Weg durch einen Serverneustart.
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
     * Beim Programmwechsel bleibt, was noch passt.
     *
     * <p>Dieselbe Haltung wie bei den Worker-Zuständen: Wer den Modus auf
     * „nacht" gestellt hat und dann einen Worker ändert, will nicht wieder
     * bei „tag" anfangen.
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

        // Dasselbe Programm mit einer zusätzlichen Zeile.
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

        // Und derselbe Name mit anderer Art fängt neu an.
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

    // ---- Geräteerkennung ---------------------------------------------------

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aChestIsRecognisedFromEverySide(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        BlockPos chest = connector.east();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.EAST));

        ConnectorBlockEntity entity =
                (ConnectorBlockEntity) helper.getBlockEntity(connector);
        DeviceProfile profile = DeviceScan.of(entity);

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

        // Zwei Barren in die Kiste hinter quarry_output.
        BlockPos connector = entity.graph().connectors().get("quarry_output");
        helper.assertTrue(connector != null, "quarry_output fehlt im Netz");
        ConnectorBlockEntity port =
                (ConnectorBlockEntity) helper.getLevel().getBlockEntity(connector);
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
     * Der ganze Weg einer Anfrage: Terminal offen, Name gefragt, Antwort da.
     *
     * <p>Ohne diesen Test prüfte nur {@code DeviceSnapshotPacket.of} — also
     * gerade der Teil, der ohnehin am wenigsten schiefgeht. Die Kette davor
     * (steht der Spieler vor einem Terminal, findet das Menü seinen
     * Controller) ließe sich sonst nur im laufenden Spiel von Hand
     * nachvollziehen.
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

        // Kein Terminal offen — der Spieler hat sein eigenes Inventar vor
        // sich. Eine Antwort wäre hier ein Weg, das Netz auszulesen, ohne
        // davorzustehen.
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        helper.assertTrue(dev.devpanda.factorynetwork.network.packet
                        .DeviceSnapshotRequestPacket.answerFor(player, "quarry_output") == null,
                "ohne offenes Terminal darf es keine Antwort geben");
        helper.succeed();
    }

    /**
     * Die Annahme-Probe gegen die Gegenstände aus dem Entwurf.
     *
     * <p>Eine Kiste nimmt alles an — der Test prüft deshalb nicht, ob die
     * Probe klug ist, sondern ob sie überhaupt läuft: dass die Kandidaten aus
     * dem Programm gelesen werden und dass jedes Fach eine Auskunft bekommt.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theProbeUsesTheItemsFromTheDraft(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Der Entwurf nennt einen Gegenstand — daraus wird der Kandidat.
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
     * Ein Connector, der ins Leere zeigt.
     *
     * <p><b>Luft ist eine Auskunft, keine fehlende.</b> Der Test stand hier
     * einmal andersherum — er verlangte, dass über Luft „nichts bekannt" sei,
     * und schrieb damit einen Fehler fest: Im Spiel stand dann „Nicht
     * geladen" vor einem Spieler, der davorstand. Wer ins Leere zeigt, soll
     * genau das erfahren.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aConnectorPointingAtAirSaysSo(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.EAST));

        ConnectorBlockEntity entity =
                (ConnectorBlockEntity) helper.getBlockEntity(connector);
        DeviceProfile profile = DeviceScan.of(entity);

        helper.assertTrue(profile.reachable(),
                "über Luft ist sehr wohl etwas bekannt: dass dort nichts steht");
        helper.assertTrue(profile.descriptionId().endsWith(".air"),
                "dort steht Luft, gemeldet wurde " + profile.descriptionId());
        helper.assertTrue(profile.access().isEmpty(),
                "an Luft ist nichts anzuschließen");
        helper.succeed();
    }


    /**
     * Eine Chemikalie fährt nicht auf dem Gegenstandsweg.
     *
     * <p><b>Der schlimmste Fehler, den diese Sprache haben kann</b>, in der
     * Ausgabe von 2026-08-26: {@code move} entschied den Weg an der Art, und
     * es fragte dafür nur die geschriebene Auswahl. Eine schon aufgelöste —
     * so kommt sie aus einer Schleife und aus jedem {@code it} — hatte für
     * Flüssigkeiten einen Nachtrag bekommen, für Chemikalien nicht. Damit
     * landete eine Chemikalie in der Gegenstandsauflösung, traf dort nichts,
     * und keine Auswahl heißt dort <i>alles</i>: Die Kiste ging leer aus.
     *
     * <p>Der Prüflauf braucht dafür keinen Chemikalientank. Es genügt, dass
     * die Auswahl sich auflöst — was danach passieren soll, ist nichts.
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
     * Ein gespeicherter Gegenstand heißt auf der Platte weiterhin so.
     *
     * <p>Dieselbe Zusage wie in {@code ValueCodecFormatTest}, nur für die
     * beiden Arten, die eine Registry brauchen: Ein wartender Ablauf aus
     * einer alten Welt muss seine Variablen wiederfinden. Von Hand gebaut
     * und nicht über einen Rundlauf — ein Rundlauf ist mit sich selbst
     * immer einig.
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
     * Die drei Speicher beantworten dieselben Fragen auf dieselbe Weise.
     *
     * <p>Schnitt 2 aus `ressourcenarten.md`: Gegenstände, Flüssigkeiten und
     * Chemikalien liegen alle in Zellen in Laufwerken, und die Sicht des
     * Netzes darauf war dreimal dieselbe Klasse mit anderen Typen. Jetzt ist
     * es eine Schnittstelle, und hier steht ihr Vertrag — einmal
     * hingeschrieben und dreimal durchlaufen.
     *
     * <p><b>Über eine Referenz vom Typ der Schnittstelle.</b> Wer die
     * konkreten Klassen anspräche, prüfte drei Wege statt einen und merkte
     * nicht, wenn einer davon abdriftet. Genau das war in Schnitt 1 passiert.
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
                dev.devpanda.factorynetwork.runtime.ResourceKind.ITEM),
                Items.IRON_INGOT, 64, "Gegenstände");
        keepsTheContract(helper, entity.store(
                dev.devpanda.factorynetwork.runtime.ResourceKind.FLUID),
                net.minecraft.world.level.material.Fluids.WATER, 1000, "Flüssigkeiten");
        keepsTheContract(helper, entity.store(
                dev.devpanda.factorynetwork.runtime.ResourceKind.CHEMICAL),
                "mekanism:hydrogen", 500, "Chemikalien");
        helper.succeed();
    }

    /**
     * Hinein, nachsehen, wieder heraus.
     *
     * <p>Die vier Fragen, die jeder Speicher beantworten muss, dazu die
     * fünfte, die es nur gibt, weil manches nicht zurückgelegt werden kann:
     * {@code room} wird gefragt, <b>bevor</b> ein Behälter geleert wird.
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

}
