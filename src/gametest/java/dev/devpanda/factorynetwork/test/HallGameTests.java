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
}
