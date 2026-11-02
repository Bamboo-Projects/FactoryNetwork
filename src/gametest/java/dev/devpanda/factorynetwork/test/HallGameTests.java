package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.hall.HallBlockEntity;
import dev.devpanda.factorynetwork.hall.HallTier;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The factory hall: in and out, and the number that survives breaking the block. */
public final class HallGameTests {

    private HallGameTests() {
    }

    private static HallBlockEntity placeHall(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, FnBlocks.HALLS.get(HallTier.SMALL).get());
        return (HallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    /**
     * The broken block keeps its hall, and placed again it opens the same one.
     *
     * <p>Going in and out needs the hall dimension, which the game test server
     * does not load (it only builds the dimensions of its own preset); that
     * part is checked by the acceptance client on a real server.
     */
    @GameTest(template = FactoryNetworkGameTests.EMPTY, timeoutTicks = 40)
    public static void theHallSurvivesBreakingTheBlock(GameTestHelper helper) {
        HallBlockEntity door = placeHall(helper, new BlockPos(1, 2, 1));
        ItemStack carried = new ItemStack(FnBlocks.HALLS.get(HallTier.SMALL).get());
        carried.set(FnComponents.HALL.get(), 7);
        door.applyComponentsFromItemStack(carried);
        helper.assertValueEqual(door.hall(), 7, "Nummer im gesetzten Block");

        List<ItemStack> drops = Block.getDrops(door.getBlockState(), helper.getLevel(),
                door.getBlockPos(), door);
        helper.assertValueEqual(drops.size(), 1, "ein Block fällt heraus");
        helper.assertValueEqual(drops.getFirst().get(FnComponents.HALL.get()), 7, "Nummer am Item");
        helper.succeed();
    }

    /** A block that was never entered drops without a number: no hall is used up. */
    @GameTest(template = FactoryNetworkGameTests.EMPTY, timeoutTicks = 40)
    public static void anUnusedBlockCostsNoHall(GameTestHelper helper) {
        HallBlockEntity door = placeHall(helper, new BlockPos(1, 2, 1));
        List<ItemStack> drops = Block.getDrops(door.getBlockState(), helper.getLevel(),
                door.getBlockPos(), door);
        helper.assertTrue(drops.getFirst().get(FnComponents.HALL.get()) == null,
                "ein nie betretener Block trägt keine Nummer");
        helper.succeed();
    }

    /** The pickaxe counts as the right tool for this mod's machines, or they drop nothing. */
    @GameTest(template = FactoryNetworkGameTests.EMPTY, timeoutTicks = 40)
    public static void aPickaxeHarvestsMachines(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        helper.assertTrue(pickaxe.isCorrectToolForDrops(FnBlocks.CONTROLLER.get().defaultBlockState()),
                "Controller mit der Spitzhacke");
        helper.assertTrue(pickaxe.isCorrectToolForDrops(
                        FnBlocks.HALLS.get(HallTier.SMALL).get().defaultBlockState()),
                "Halle mit der Spitzhacke");
        helper.succeed();
    }

    /**
     * A blueprint copies blocks and the settings of the blocks it knows, and
     * asks for the connector parts on top of the cable.
     *
     * <p>Read and built in the test world at the places halls 1 and 2 would
     * have: the geometry is the same, only the dimension is not.
     */
    @GameTest(template = FactoryNetworkGameTests.EMPTY, timeoutTicks = 60)
    public static void aBlueprintCopiesBlocksAndSettings(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos from = dev.devpanda.factorynetwork.hall.Halls.origin(1);
        BlockPos to = dev.devpanda.factorynetwork.hall.Halls.origin(2);
        BlockPos cable = from.offset(2, 1, 2);
        level.setBlockAndUpdate(cable, FnBlocks.CABLE.get().defaultBlockState());
        var bus = (dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity) level.getBlockEntity(cable);
        bus.addPart(net.minecraft.core.Direction.NORTH).setLabel("ofen_1");
        level.setBlockAndUpdate(from.offset(4, 1, 2), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());

        var blueprint = dev.devpanda.factorynetwork.hall.Blueprint.capture(level, 1, HallTier.SMALL);
        helper.assertTrue(blueprint != null, "der Bauplan muss entstehen");
        helper.assertValueEqual(blueprint.blocks().size(), 2, "Blöcke im Bauplan");
        var materials = blueprint.materials();
        helper.assertValueEqual(materials.get(dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()), 1,
                "der Connector wird mitgezählt");
        helper.assertValueEqual(materials.get(Items.FURNACE), 1, "der Ofen wird gezählt");

        blueprint.build(level, 2);
        var copy = (dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity)
                level.getBlockEntity(to.offset(2, 1, 2));
        helper.assertTrue(copy != null && copy.partAt(net.minecraft.core.Direction.NORTH) != null,
                "der Connector muss nachgebaut sein");
        helper.assertValueEqual(copy.partAt(net.minecraft.core.Direction.NORTH).label(), "ofen_1",
                "der Name muss mitkommen");
        helper.assertTrue(level.getBlockState(to.offset(4, 1, 2)).is(net.minecraft.world.level.block.Blocks.FURNACE),
                "der Ofen muss stehen");

        level.setBlockAndUpdate(cable, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(from.offset(4, 1, 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(to.offset(2, 1, 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(to.offset(4, 1, 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** The hall wall is this mod's own block and cannot be broken, pushed or eaten by a wither. */
    @GameTest(template = FactoryNetworkGameTests.EMPTY, timeoutTicks = 40)
    public static void theHallWallStands(GameTestHelper helper) {
        var wall = FnBlocks.HALL_WALL.get().defaultBlockState();
        helper.assertTrue(wall.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) < 0, "unzerstörbar");
        helper.assertTrue(wall.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK,
                "Kolben schieben die Wand nicht");
        helper.assertTrue(wall.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE), "der Wither frisst sie nicht");

        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, wall);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var event = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(helper.getLevel(),
                helper.absolutePos(at), wall, player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "auch im Kreativmodus nicht abbaubar");
        helper.succeed();
    }
}
