package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.runtime.Handoffs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Real item registries require a loader-backed game test, not a JUnit bootstrap. */
@GameTestHolder(FactoryNetwork.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandoffGameTests {
    private HandoffGameTests() {}

    @GameTest(template = "empty")
    public static void sourceRefusalDoesNotReportAFullTargetOrTouchIt(GameTestHelper helper) {
        var source = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        };
        source.setStackInSlot(0, new ItemStack(Items.COAL, 16));
        int[] offers = { 0 };
        var target = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                offers[0]++;
                return super.insertItem(slot, stack, simulate);
            }
        };
        var result = Handoffs.items(source, target, List.of(), 16);
        helper.assertValueEqual(result.moved(), 0L, "No extraction means no transfer");
        helper.assertTrue(!result.targetFull(), "Source refusal must not be reported as a full target");
        helper.assertValueEqual(offers[0], 0, "Nothing must be offered to the target");
        helper.assertValueEqual(source.getStackInSlot(0).getCount(), 16, "Source contents stay unchanged");
        helper.assertTrue(target.getStackInSlot(0).isEmpty(), "Target stays empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aLockedSourceSlotDoesNotHideLaterExtractableSlots(GameTestHelper helper) {
        var source = new ItemStackHandler(2) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.COAL, 16));
        source.setStackInSlot(1, new ItemStack(Items.COAL, 8));
        var target = new ItemStackHandler(1);
        var result = Handoffs.items(source, target, List.of(), 16);
        helper.assertValueEqual(result.moved(), 8L, "Later extractable slot still transfers");
        helper.assertTrue(!result.targetFull(), "Empty accepted target was not full");
        helper.assertValueEqual(source.getStackInSlot(0).getCount(), 16, "Locked slot stays untouched");
        helper.assertTrue(source.getStackInSlot(1).isEmpty(), "Extractable slot was consumed");
        helper.assertValueEqual(target.getStackInSlot(0).getCount(), 8, "Target receives exactly eight items");
        helper.succeed();
    }
}
