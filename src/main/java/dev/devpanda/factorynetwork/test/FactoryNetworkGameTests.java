package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.CableBlockEntity;
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

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void twoStrandsInOneBlockStayApart(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // Ein Block, zwei Stränge: grün und rot.
        BlockPos bundle = controller.east();
        helper.setBlock(bundle, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        if (helper.getBlockEntity(bundle) instanceof CableBlockEntity cable) {
            cable.addStrand(CableColour.RED);
            helper.assertValueEqual(cable.count(), 2, "Stränge im Block");
        } else {
            helper.fail("Am Kabel hängt keine BlockEntity", bundle);
        }

        // Grüner Zweig weiter nach Osten
        BlockPos green = bundle.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        BlockPos onGreen = green.east();
        helper.setBlock(onGreen, FnBlocks.CONNECTOR.get());
        name(helper, onGreen, "am_gruenen");

        // Roter Zweig nach oben
        BlockPos red = bundle.above();
        helper.setBlock(red, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.RED));
        BlockPos onRed = red.above();
        helper.setBlock(onRed, FnBlocks.CONNECTOR.get());
        name(helper, onRed, "am_roten");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Der Controller hängt am Bündel und erreicht darüber beide Stränge —
        // er ist farbneutral. Getrennt sind die Stränge erst weiter draußen.
        helper.assertTrue(entity.graph().connector("am_gruenen").isPresent(),
                "Der grüne Strang muss durchleiten");
        helper.assertTrue(entity.graph().connector("am_roten").isPresent(),
                "Der rote Strang auch — beide hängen am Controller");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aBundleDoesNotBridgeColours(GameTestHelper helper) {
        // Der eigentliche Test: Ein Bündel darf zwei gleichfarbige Stränge
        // nicht über eine fremde Farbe hinweg verbinden.
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

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

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void breakingABundleDropsEveryStrand(GameTestHelper helper) {
        BlockPos bundle = new BlockPos(1, 1, 1);
        helper.setBlock(bundle, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.BLUE));
        if (helper.getBlockEntity(bundle) instanceof CableBlockEntity cable) {
            cable.addStrand(CableColour.YELLOW);
            cable.addStrand(CableColour.NONE);
        }
        net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(bundle);
        java.util.List<net.minecraft.world.item.ItemStack> drops =
                net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(),
                        helper.absolutePos(bundle), helper.getBlockEntity(bundle));
        helper.assertValueEqual(drops.size(), 3, "Anzahl gefallener Kabel");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void breakingOneStrandLeavesTheOthers(GameTestHelper helper) {
        BlockPos bundle = new BlockPos(1, 1, 1);
        helper.setBlock(bundle, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        if (!(helper.getBlockEntity(bundle) instanceof CableBlockEntity cable)) {
            helper.fail("Am Kabel hängt keine BlockEntity", bundle);
            return;
        }
        cable.addStrand(CableColour.RED);
        cable.addStrand(CableColour.BLUE);
        helper.assertValueEqual(cable.count(), 3, "Stränge vor dem Abbauen");

        // Einen Strang herausnehmen — der Block bleibt mit den übrigen stehen.
        helper.assertTrue(cable.removeStrand(CableColour.RED), "Rot muss weichen");
        helper.assertValueEqual(cable.count(), 2, "Stränge danach");
        helper.assertTrue(cable.has(CableColour.GREEN), "Grün muss bleiben");
        helper.assertTrue(cable.has(CableColour.BLUE), "Blau muss bleiben");
        helper.assertFalse(cable.has(CableColour.RED), "Rot darf weg sein");

        // Und der Blockzustand zieht mit, damit das Modell stimmt.
        helper.assertValueEqual(helper.getBlockState(bundle).getValue(CableBlock.STRANDS), 2,
                "Strangzahl im Blockzustand");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void removingAStrandReconnectsTheRest(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // Bündel mit Grün und Rot, dahinter ein grüner Strang zum Connector
        BlockPos bundle = controller.east();
        helper.setBlock(bundle, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        if (helper.getBlockEntity(bundle) instanceof CableBlockEntity cable) {
            cable.addStrand(CableColour.RED);
        }
        BlockPos green = bundle.east();
        helper.setBlock(green, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.COLOUR, CableColour.GREEN));
        BlockPos target = green.east();
        helper.setBlock(target, FnBlocks.CONNECTOR.get());
        name(helper, target, "dahinter");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isPresent(),
                "Vor dem Abbauen muss der Weg stehen");

        // Grün aus dem Bündel nehmen — der Weg muss abreißen.
        if (helper.getBlockEntity(bundle) instanceof CableBlockEntity cable) {
            cable.removeStrand(CableColour.GREEN);
        }
        entity.rebuildNetwork();
        helper.assertTrue(entity.graph().connector("dahinter").isEmpty(),
                "Ohne den grünen Strang darf kein Weg mehr bestehen");
        helper.succeed();
    }

    /** Legt eine Reihe Kabel und hängt an jedes Ende einen Connector. */
    private static void line(GameTestHelper helper, BlockPos from, int length) {
        for (int i = 0; i < length; i++) {
            helper.setBlock(from.east(i), FnBlocks.CABLE.get());
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aStrandCarriesEightChannels(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

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
                placed++;
            }
        }
        helper.assertValueEqual(placed, 9, "aufgestellte Geräte");

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Acht Kanäle je Strang: Das neunte Gerät geht leer aus.
        helper.assertValueEqual(entity.graph().starvedConnectors().size(), 1,
                "Geräte ohne Kanal");
        helper.assertValueEqual(entity.graph().connectorNames().size(), 8,
                "Geräte mit Kanal");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void twoStrandsCarryEightEach(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());

        // Dieselbe Reihe, aber jeder Block trägt zwei Stränge: grün und rot.
        for (int i = 0; i < 5; i++) {
            BlockPos pos = controller.east(i + 1);
            helper.setBlock(pos, FnBlocks.CABLE.get().defaultBlockState()
                    .setValue(CableBlock.COLOUR, CableColour.GREEN));
            if (helper.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cable.addStrand(CableColour.RED);
            }
        }
        // Zehn Geräte — mehr als ein Strang trägt, aber zwei tragen sie.
        int placed = 0;
        for (int i = 0; i < 5 && placed < 10; i++) {
            for (BlockPos side : new BlockPos[]{
                    controller.east(i + 1).above(), controller.east(i + 1).below()}) {
                if (placed >= 10) {
                    break;
                }
                helper.setBlock(side, FnBlocks.CONNECTOR.get());
                name(helper, side, "gerät_" + placed);
                placed++;
            }
        }

        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Kanäle zählen je Strang, nicht je Block: Zehn Geräte passen durch
        // zwei Stränge, obwohl einer nur acht trüge.
        helper.assertValueEqual(entity.graph().starvedConnectors().size(), 0,
                "Kein Gerät darf leer ausgehen");
        helper.assertValueEqual(entity.graph().connectorNames().size(), 10,
                "Geräte mit Kanal");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void loadIsCountedPerStrand(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 2, 1);
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
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
        helper.assertValueEqual(entity.graph().channelsFree(
                helper.absolutePos(controller.east(1)), CableColour.NONE), 6,
                "freie Kanäle dort");
        helper.succeed();
    }

    /** Baut Controller, Kabelreihe und drei benannte Kisten. */
    private static ControllerBlockEntity threeChests(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, FnBlocks.CONTROLLER.get());
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
        helper.setBlock(controller.east(), FnBlocks.CABLE.get());

        BlockPos display = controller.east().east();
        helper.setBlock(display, FnBlocks.DISPLAY.get());
        if (helper.getBlockEntity(display) instanceof DisplayBlockEntity entity) {
            entity.setDisplayName("lager");
        } else {
            helper.fail("Am Display hängt keine BlockEntity", display);
            return;
        }

        ControllerBlockEntity controllerEntity = controllerAt(helper, controller);
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

    private FactoryNetworkGameTests() {
    }
}
