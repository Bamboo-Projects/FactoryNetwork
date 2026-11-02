package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;

import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.blockEntity;
import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.buildSetup;
import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.controllerAt;

/**
 * The storage link on a real ME network.
 *
 * <p>AE2 is looked up by name: the tests compile without it, and where it is
 * not installed they pass without checking anything.
 */
public final class StorageLinkGameTests {

    private static final String EMPTY = "empty";

    private StorageLinkGameTests() {
    }

    /**
     * The ME network as an ordinary device: a worker takes from it by sort.
     *
     * <p>Setup: the source chest of the standard setup is replaced by a link,
     * and behind the link stand an ME drive with an item and a fluid cell and
     * a creative energy cell. Without a controller AE2 builds an ad-hoc
     * network of up to eight channels; the link takes one.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aworkerTakesFromAnMeNetworkBySort(GameTestHelper helper) {
        aworkerTakesFromAforeignSystem(helper, "ae2", "Applied Energistics 2", link -> {
            helper.setBlock(link.north(), block("ae2", "drive"));
            helper.setBlock(link.west(), block("ae2", "creative_energy_cell"));
            return new ItemStack[] {item("ae2", "item_storage_cell_1k"), item("ae2", "fluid_storage_cell_1k")};
        });
    }

    /** The same with Refined Storage: a creative controller and a disk drive. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aworkerTakesFromArefinedStorageNetworkBySort(GameTestHelper helper) {
        aworkerTakesFromAforeignSystem(helper, "refinedstorage", "Refined Storage", link -> {
            // Storage blocks rather than disks: a fresh disk has no storage
            // of its own until a player has held it.
            helper.setBlock(link.north(), block("refinedstorage", "1k_storage_block"));
            helper.setBlock(link.above(), block("refinedstorage", "64b_fluid_storage_block"));
            helper.setBlock(link.west(), block("refinedstorage", "creative_controller"));
            return new ItemStack[0];
        });
    }

    static void aworkerTakesFromAforeignSystem(GameTestHelper helper, String mod, String systemName,
            java.util.function.Function<BlockPos, ItemStack[]> build) {
        if (!net.neoforged.fml.ModList.get().isLoaded(mod)) {
            helper.succeed();
            return;
        }
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos link = controller.east().north().north();
        helper.setBlock(link, FnBlocks.STORAGE_LINK.get());
        ItemStack[] cells = build.apply(link);
        BlockPos depot = controller.east().south().south();

        // The cells go into the drive through its own inventory.
        if (cells.length > 0) {
            var drive = dev.devpanda.factorynetwork.platform.Caps.items(helper.getLevel(),
                    helper.absolutePos(link.north()), null);
            helper.assertTrue(drive != null, "Das Laufwerk hat kein Inventar");
            helper.assertTrue(drive.insertItem(0, cells[0], false).isEmpty(),
                    "Die Gegenstandszelle passt nicht ins Laufwerk");
            helper.assertTrue(drive.insertItem(1, cells[1], false).isEmpty(),
                    "Die Flüssigkeitszelle passt nicht ins Laufwerk");
        }

        // Our drive gets a fluid cell next to its item cell, so that fluid
        // can arrive in our storage.
        if (blockEntity(helper, controller.above()) instanceof DriveBlockEntity ours) {
            ours.setCell(1, new ItemStack(FnItems.FLUID_CELLS
                    .get(dev.devpanda.factorynetwork.storage.FluidCellTier.B256).get()));
        }

        helper.startSequence()
                // AE2 boots its network over a few ticks.
                .thenWaitUntil(() -> {
                    if (!(blockEntity(helper, link) instanceof StorageLinkBlockEntity found)
                            || found.adapter() == null || found.adapter().items() == null
                            // Disks and cells reach the network a few ticks
                            // after they went into the drive.
                            || found.adapter().items().insert(ItemKey.bare(Items.IRON_INGOT), 1, true) <= 0) {
                        throw helper.assertionException("Der Lager-Anschluss hängt noch an keinem Netz von " + systemName);
                    }
                })
                .thenExecute(() -> {
                    var adapter = ((StorageLinkBlockEntity) blockEntity(helper, link)).adapter();
                    helper.assertValueEqual(adapter.systemName(), systemName, "System");
                    long put = adapter.items().insert(ItemKey.bare(Items.IRON_INGOT), 500, false);
                    helper.assertValueEqual(put, 500L, "Eisen ins fremde Lager");
                    adapter.items().insert(ItemKey.bare(Items.GOLD_INGOT), 100, false);
                    long water = adapter.fluids().insert(Fluids.WATER, 4_000, false);
                    helper.assertValueEqual(water, 4_000L, "Wasser ins fremde Lager");
                    entity.rebuildNetwork();
                    helper.assertTrue(entity.deploy("""
                            worker eisen {
                                from quarry_output
                                to depot
                                filter item:iron_ingot
                            }
                            worker wasser {
                                from quarry_output
                                to storage
                                filter fluid:water
                            }"""), "Das Programm wurde nicht übernommen: " + entity.diagnostics());
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    var adapter = ((StorageLinkBlockEntity) blockEntity(helper, link)).adapter();
                    int inDepot = 0;
                    int gold = 0;
                    if (blockEntity(helper, depot) instanceof ChestBlockEntity chest) {
                        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                            ItemStack stack = chest.getItem(slot);
                            if (stack.is(Items.IRON_INGOT)) {
                                inDepot += stack.getCount();
                            } else if (stack.is(Items.GOLD_INGOT)) {
                                gold += stack.getCount();
                            }
                        }
                    }
                    long[] left = {0};
                    adapter.items().find(java.util.List.of(Items.IRON_INGOT), (key, amount) -> left[0] += amount);
                    helper.assertTrue(inDepot > 0, "Kein Eisen aus dem fremden Lager angekommen");
                    helper.assertValueEqual(gold, 0, "Der Filter ließ Gold durch");
                    helper.assertValueEqual(left[0] + inDepot, 500L, "Eisen ging verloren oder entstand");
                    long water = entity.fluids().count(Fluids.WATER);
                    helper.assertTrue(water > 0, "Kein Wasser aus dem fremden Lager im Speicher");
                })
                .thenSucceed();
    }

    private static net.minecraft.world.level.block.Block block(String mod, String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(mod, path));
    }

    private static ItemStack item(String mod, String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(mod, path)));
    }
}
