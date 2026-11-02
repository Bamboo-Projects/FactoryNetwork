package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.network.SlotMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The slot numbers on foreign block GUIs: the number is the one
 * {@code slots(n)} uses, the sides are the ones a hopper or connector reaches.
 */
@GameTestHolder(dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlotMapGameTests {

    private SlotMapGameTests() {
    }

    private static int bit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    private static List<SlotMap.Entry> mapOf(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var provider = (MenuProvider) helper.getLevel().getBlockEntity(pos);
        var menu = provider.createMenu(1, player.getInventory(), player);
        return SlotMap.of(menu, helper.getLevel(), pos);
    }

    /**
     * The furnace: input from the top, fuel from the sides and the bottom,
     * the result only from the bottom; the numbers are the container's.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aFurnaceShowsItsSlotsAndSides(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(1, 2, 1);
        helper.setBlock(furnace, Blocks.FURNACE);
        List<SlotMap.Entry> map = mapOf(helper, furnace);

        helper.assertValueEqual(map.size(), 3, "Fächer des Ofens ohne den Rucksack");
        int sides = bit(Direction.NORTH) | bit(Direction.SOUTH) | bit(Direction.WEST) | bit(Direction.EAST);
        helper.assertValueEqual(map.get(0), new SlotMap.Entry(0, 0, bit(Direction.UP), false), "Eingang");
        helper.assertValueEqual(map.get(1), new SlotMap.Entry(1, 1, sides | bit(Direction.DOWN), false),
                "Brennstoff");
        helper.assertValueEqual(map.get(2), new SlotMap.Entry(2, 2, bit(Direction.DOWN), false), "Ergebnis");
        helper.succeed();
    }

    /** A chest has no side rules: every slot from everywhere. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aChestIsReachedFromEverySide(GameTestHelper helper) {
        BlockPos chest = new BlockPos(1, 2, 1);
        helper.setBlock(chest, Blocks.CHEST);
        List<SlotMap.Entry> map = mapOf(helper, chest);

        helper.assertValueEqual(map.size(), 27, "Fächer der Truhe");
        for (int i = 0; i < 27; i++) {
            SlotMap.Entry entry = map.get(i);
            helper.assertValueEqual(entry.number(), i, "Nummer von Fach " + i);
            helper.assertValueEqual(entry.sides(), 0b111111, "Seiten von Fach " + i);
            helper.assertFalse(entry.estimated(), "Fach " + i + " ist genau bekannt");
        }
        helper.succeed();
    }

    /** Asking for another menu than the open one gets no answer. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void onlyTheOpenMenuIsAnswered(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(1, 2, 1);
        helper.setBlock(furnace, Blocks.FURNACE);
        BlockPos pos = helper.absolutePos(furnace);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getCenter().add(0, 1, 0));
        var provider = (MenuProvider) helper.getLevel().getBlockEntity(pos);
        player.containerMenu = provider.createMenu(7, player.getInventory(), player);

        var right = dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket.answerFor(player,
                new dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket(7, pos));
        var wrong = dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket.answerFor(player,
                new dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket(8, pos));
        helper.assertValueEqual(right.size(), 3, "Antwort für das offene Menü");
        helper.assertTrue(wrong.isEmpty(), "keine Antwort für ein anderes Menü");
        helper.succeed();
    }
}
