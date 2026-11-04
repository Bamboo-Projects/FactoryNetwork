package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.upgrade.Arm;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Tool;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An arm upgrade for a connector: combat or work.
 *
 * <p><b>It is the tool itself.</b> The combat upgrade hits like an iron
 * sword, the work upgrade mines like iron pickaxe, axe, shovel and hoe in
 * one. Both take enchantments at the table and the anvil, and they act as
 * they would on a tool: sharpness and looting on the one, efficiency,
 * fortune and silk touch on the other. A program that hands over its own
 * tool from the store uses that one instead. They never wear out — an
 * upgrade that breaks inside a connector would vanish where nobody sees it.
 */
public class ArmUpgradeItem extends Item {

    private final Arm arm;

    public ArmUpgradeItem(Properties properties, Arm arm) {
        super(properties);
        this.arm = arm;
    }

    public Arm arm() {
        return arm;
    }

    /** Which arm sits in this stack, or {@code null}. */
    public static @Nullable Arm armOf(ItemStack stack) {
        return stack.getItem() instanceof ArmUpgradeItem item ? item.arm() : null;
    }

    /** The combat upgrade: an iron sword that does not break. */
    public static Properties combat(Properties properties) {
        return properties.sword(ToolMaterial.IRON, 3.0F, -2.4F)
                .stacksTo(1)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
    }

    /** The work upgrade: pickaxe, axe, shovel and hoe of iron in one. */
    public static Properties work(Properties properties) {
        var blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        ToolMaterial material = ToolMaterial.IRON;
        return properties.stacksTo(1)
                .enchantable(material.enchantmentValue())
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.deniesDrops(blocks.getOrThrow(material.incorrectBlocksForDrops())),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), material.speed()),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), material.speed()),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), material.speed()),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), material.speed())),
                        1.0F, 1, true));
    }
}
