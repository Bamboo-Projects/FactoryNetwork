package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.runtime.ScriptError;
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
        helper.assertValueEqual(atStart, dev.devpanda.factorynetwork.network.Channels.quarters(2), "Kanäle auf dem ersten Kabel");
        helper.assertValueEqual(entity.graph().channelsFree(helper.getLevel(),
                helper.absolutePos(controller.east(1)), CableColour.NONE),
                dev.devpanda.factorynetwork.network.Channels.quarters(
                        dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_THIN - 2),
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
        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn reihum() {
                    let summe = 0
                    let runden = 0
                    for sorte in tag:minecraft/planks {
                        let wert = await Takt
                        summe = summe + wert
                        runden = runden + 1
                        if runden >= 3 {
                            break
                        }
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

        helper.assertValueEqual(flow.status().name(), "DONE", "Nach dem break ist Schluss");
        helper.assertValueEqual(resultOf(flow), 6L, "Drei Runden, drei Werte");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aForLoopKeepsItsPlaceAcrossARestart(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(entity.deploy("""
                event Takt(nummer: Int)

                fn reihum() {
                    let summe = 0
                    let runden = 0
                    for sorte in tag:minecraft/planks {
                        let wert = await Takt
                        summe = summe + wert
                        runden = runden + 1
                        if runden >= 3 {
                            break
                        }
                    }
                    return summe
                }"""), "Das Programm wurde nicht übernommen");

        var flow = entity.startFlow("reihum", java.util.List.of());
        tick(helper, entity, 1);

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
        // vorn — und täte alles ein zweites Mal.
        tick(helper, geladen, 2);
        tick(helper, geladen, 3);
        helper.assertValueEqual(wieder.status().name(), "DONE", "Drei Runden, dann Schluss");
        helper.assertValueEqual(resultOf(wieder), 6L, "Über den Neustart hinweg gezählt");
        helper.succeed();
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
                "fn test() { }", java.util.List.of("kiste_1", "kiste_2"),
                java.util.List.of("haul: RUNNING"), java.util.List.of("werk_1: Werk"),
                java.util.List.of("water: 1000 mB"));
        var zurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.NetworkStatePacket.STREAM_CODEC,
                netzzustand);
        helper.assertValueEqual(zurueck.fluids().get(0), "water: 1000 mB", "Netzzustand");
        helper.assertValueEqual(zurueck.plants().get(0), "werk_1: Werk", "Anlagen");
        helper.assertValueEqual(zurueck.connectors().size(), 2, "Connectoren");

        var ablaeufe = new dev.devpanda.factorynetwork.network.packet.FlowStatePacket(
                java.util.List.of(new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Line(7, "zaehlt", "AWAITING", "wartet auf Takt")),
                new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Compute(16, 5, 2, 64, 37, 256),
                new dev.devpanda.factorynetwork.network.packet
                        .FlowStatePacket.Supply(0, 12345, 20000, 42));
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
                        java.util.List.of("§fZeile", "§8[Knopf]"), java.util.List.of(1))));
        var anzeigenZurueck = roundTrip(helper,
                dev.devpanda.factorynetwork.network.packet.DisplayStatePacket.STREAM_CODEC,
                anzeigen);
        helper.assertValueEqual(anzeigenZurueck.panels().get(0).buttons().get(0), 1,
                "Welche Zeile ein Knopf ist");

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
        helper.assertValueEqual(anzeigen.get(0).buttons().get(0), 3,
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

        // Was in beispiele.md steht, muss nicht nur übersetzen, sondern laufen.
        // Ein Beispiel mit einem Methodennamen, den es nicht gibt, ist
        // schlimmer als keines.
        helper.assertTrue(entity.deploy("""
                display leitstand {
                    title "Erzlinie"
                    row "Eisenerz" storage.count(item:iron_ore)
                    progress "Kohlevorrat" storage.count(item:iron_ore) / 640.0
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
        helper.assertTrue(zeilen.get(2).contains("█") || zeilen.get(2).contains("0,5")
                        || zeilen.get(2).contains("0.5"),
                "Der halbe Balken: " + zeilen.get(2));
        helper.assertTrue(zeilen.get(3).contains("Depot"),
                "Das Lämpchen: " + zeilen.get(3));

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
                    strategy emptiest
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
        // Acht Geräte zu je zwei Kanälen füllen ein gewöhnliches Kabel aus.
        helper.assertTrue(data.summary().fullLinks() > 0 || data.summary().tightLinks() > 0,
                "Die Strecke am Controller muss als eng erkannt werden");
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
                dev.devpanda.factorynetwork.network.Channels.quarters(
                        dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE - 18),
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

    /** Bestückt einen Einschub vollständig. */
    private static void fillBay(GameTestHelper helper, BlockPos at, int bay,
                                int cpu, int ram, int disk) {
        if (!(helper.getBlockEntity(at)
                instanceof dev.devpanda.factorynetwork.block.entity.RackBlockEntity rack)) {
            helper.fail("Am Serverschrank hängt keine BlockEntity", at);
            return;
        }
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
                dev.devpanda.factorynetwork.network.Channels.quarters(2), "Kanäle auf Bahn eins");

        // Andere Bahn: Der Weg kreuzt sich berührungslos.
        lane(helper, router, north, 2);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("nord").isEmpty(),
                "nord liegt auf einer anderen Bahn und darf nicht dazugehören");
        helper.assertTrue(entity.graph().connector("oben").isPresent(),
                "oben liegt weiter auf Bahn eins");
        helper.assertValueEqual(entity.graph().laneLoad(helper.absolutePos(router), 1),
                dev.devpanda.factorynetwork.network.Channels.quarters(1), "Kanäle auf Bahn eins, nachdem nord ausgeschert ist");
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
                dev.devpanda.factorynetwork.network.Channels.quarters(
                        dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE),
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
                    dev.devpanda.factorynetwork.network.Channels.quarters(
                            dev.devpanda.factorynetwork.block.CableBlock.CHANNELS_DENSE),
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
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.CPU, 8));
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.RAM, 32));
        player.getInventory().add(
                serverPart(dev.devpanda.factorynetwork.item.ServerPart.DISK, 256));

        var rack = (dev.devpanda.factorynetwork.block.entity.RackBlockEntity)
                helper.getBlockEntity(rackPos);
        // Jedes Bauteil findet seinen Platz von selbst — der Umschalt-Klick
        // muss nicht wissen, wohin ein Datenträger gehört.
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.threads(), 0, "ein Rechenwerk allein ist kein Server");
        intoShelf(helper, rackPos, player);
        intoShelf(helper, rackPos, player);
        helper.assertValueEqual(rack.usedSlots(), 3, "Plätze im Schrank");
        helper.assertValueEqual(rack.runningBays(), 1, "ein laufender Einschub");
        helper.assertValueEqual(rack.threads(), 8, "sein Rechenwerk");

        takeFromShelf(helper, rackPos, 0, player);
        helper.assertValueEqual(rack.threads(), 0, "nach dem Herausnehmen");
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
                    log "hallo"
                }"""), "ohne Server darf nichts übernommen werden");
        helper.assertTrue(entity.diagnostics().stream()
                        .anyMatch(d -> d.message().contains("Serverschrank")),
                "die Meldung muss den Schrank nennen: " + entity.diagnostics());

        // Schrank hin, und dasselbe Programm läuft.
        rackWithServer(helper, controller.west());
        helper.assertTrue(entity.deploy("""
                fn nichts() {
                    log "hallo"
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
        helper.assertTrue(engine.failed().get(0).detail().contains("Speicher ist voll"),
                "und der Grund muss den Speicher nennen: "
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
            java.nio.file.Path path = entity.programFilePath();
            helper.assertTrue(path != null, "es gibt keine Datei");
            try {
                helper.assertTrue(java.nio.file.Files.exists(path),
                        "die Datei liegt nicht da: " + path);
                helper.assertTrue(java.nio.file.Files.readString(path).contains("fn eins"),
                        "und enthält das Programm nicht");

                // Von außen etwas anderes hineinschreiben, mit frischem
                // Zeitstempel: Innerhalb einer Millisekunde wäre er derselbe,
                // und dann sähe der Controller die Änderung nicht.
                java.nio.file.Files.writeString(path, "fn zwei() {\n    let b = 2\n}");
                java.nio.file.Files.setLastModifiedTime(path,
                        java.nio.file.attribute.FileTime.fromMillis(
                                System.currentTimeMillis() + 5000L));
            } catch (java.io.IOException failed) {
                helper.fail("Die Datei ließ sich nicht anfassen: " + failed);
            }

            helper.runAfterDelay(25, () -> {
                helper.assertTrue(entity.source().contains("fn zwei"),
                        "die Änderung von außen kam nicht an: " + entity.source());
                helper.assertTrue(entity.program().functions().stream()
                                .anyMatch(fn -> fn.name().equals("zwei")),
                        "und wurde nicht übernommen");
                try {
                    java.nio.file.Files.deleteIfExists(entity.programFilePath());
                } catch (java.io.IOException ignored) {
                    // Eine liegengebliebene Prüfdatei stört niemanden.
                }
                helper.succeed();
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
        helper.assertValueEqual(geladen.usedSlots(), 6, "belegte Plätze");
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
        helper.succeedWhenEntityPresent(net.minecraft.world.entity.EntityType.ITEM, rackPos);
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

        // Connector, Laufwerk und Schrank je ein ganzer Kanal, die Anzeige
        // ein Viertel.
        int erwartet = dev.devpanda.factorynetwork.network.Channels.CONNECTOR
                + dev.devpanda.factorynetwork.network.Channels.DRIVE
                + dev.devpanda.factorynetwork.network.Channels.RACK
                + dev.devpanda.factorynetwork.network.Channels.DISPLAY;
        helper.assertValueEqual(entity.graph().channelLoad(
                        helper.absolutePos(cable), CableColour.NONE), erwartet,
                "Kanäle auf dem Kabel, in Vierteln");
        helper.assertValueEqual(dev.devpanda.factorynetwork.network.Channels.format(erwartet),
                "3¼", "so steht es in der Anzeige");
        helper.succeed();
    }

    /**
     * Vier Anzeigen teilen sich einen Kanal.
     *
     * <p>Eine Anzeige liest nur mit und schiebt nichts. Eine Leitstandwand
     * soll kein halbes Netz auffressen.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void fourDisplaysShareOneChannel(GameTestHelper helper) {
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
                dev.devpanda.factorynetwork.network.Channels.quarters(1),
                "vier Anzeigen sind zusammen ein Kanal");
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
                dev.devpanda.factorynetwork.network.Channels.quarters(1),
                "und zusammen kosten sie einen Kanal");
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
}
