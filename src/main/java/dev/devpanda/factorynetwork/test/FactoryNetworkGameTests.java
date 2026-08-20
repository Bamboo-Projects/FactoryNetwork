package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
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
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.storage().insert(Items.IRON_INGOT, 5);

        // Ein Eintrag aus einer Mod, die es nicht mehr gibt, darf beim Laden
        // nicht als Luft im Bestand landen.
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        entity.storage().save(tag, helper.getLevel().registryAccess());
        net.minecraft.nbt.ListTag entries =
                tag.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.CompoundTag ghost = new net.minecraft.nbt.CompoundTag();
        ghost.putString("Item", "verschwundene_mod:zauberstab");
        ghost.putLong("Count", 99);
        entries.add(ghost);

        dev.devpanda.factorynetwork.network.NetworkStorage reloaded =
                new dev.devpanda.factorynetwork.network.NetworkStorage();
        reloaded.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(reloaded.distinctTypes(), 1,
                "nur der bekannte Gegenstand darf überleben");
        helper.assertValueEqual(reloaded.count(Items.IRON_INGOT), 5L, "Bestand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cablesOfDifferentColoursDoNotConnect(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

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

    private FactoryNetworkGameTests() {
    }
}
