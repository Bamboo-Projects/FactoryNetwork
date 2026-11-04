package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ConnectorPart;
import dev.devpanda.factorynetwork.block.entity.Connectors;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.platform.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The wrench remembers a connector's settings and hands them on: one
 * connector set up, the next ten by a click each.
 *
 * <p><b>Left-click reads, right-click writes.</b> Sneak plus right-click
 * stays what it is with every wrench — taking the connector off. Only this
 * mod's own wrench carries settings; a foreign one has its own clicks.
 *
 * <p>The name is never copied one to one: two devices of the same name are
 * both unusable. The copy counts on — {@code ofen_1} becomes {@code ofen_2},
 * the next free number.
 */
public final class SettingsCopy {

    private static final String KEY = "FactoryNetworkConnector";
    private static final String KEY_BASE = "Base";
    private static final String KEY_COST = "ChannelCost";
    private static final String KEY_REDSTONE = "Redstone";

    private SettingsCopy() {
    }

    /** Whether this stack can carry settings: this mod's wrench only. */
    public static boolean canCarry(ItemStack stack) {
        return stack.is(FnItems.WRENCH.get());
    }

    /** Whether this wrench carries settings. */
    public static boolean carries(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(KEY);
    }

    /** The connector this click means: on a cable the one under the cursor, else the block's own. */
    public static @Nullable ConnectorPart partAt(Level level, BlockPos pos, @Nullable BlockHitResult hit) {
        if (hit != null && level.getBlockEntity(pos) instanceof CableBusBlockEntity bus) {
            var side = CableBlock.partSideAt(level, pos, hit);
            if (side != null) {
                return bus.partAt(side);
            }
        }
        return Connectors.at(level, pos);
    }

    /** Reads the connector into the wrench. */
    public static void copy(Player player, ItemStack wrench, ConnectorPart from) {
        CompoundTag settings = new CompoundTag();
        settings.putString(KEY_BASE, ConnectorNaming.stripSuffix(from.label()));
        settings.putInt(KEY_COST, from.channelCost());
        settings.putInt(KEY_REDSTONE, from.emittedRedstone());
        CompoundTag tag = wrench.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(KEY, settings);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.sendOverlayMessage(Component.translatable("message.factorynetwork.wrench.copied",
                from.label().isBlank() ? "?" : from.label()));
    }

    /** Writes the wrench's settings onto a connector; returns the name it got. */
    public static String paste(Player player, ItemStack wrench, ConnectorPart to) {
        CustomData data = wrench.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return to.label();
        }
        CompoundTag settings = Nbt.getCompound(data.copyTag(), KEY);
        String base = Nbt.getString(settings, KEY_BASE);
        to.setChannelCost(Math.max(1, Nbt.getInt(settings, KEY_COST)));
        to.setEmittedRedstone(Nbt.getInt(settings, KEY_REDSTONE));
        if (!base.isBlank()) {
            FactoryGraph graph = to.level() == null ? null
                    : ControllerRegistry.owning(to.level(), to.pos())
                            .map(controller -> controller.graph()).orElse(null);
            // Already counted from this base: it keeps its name, else a
            // second click on the same connector would give it the next.
            if (!ConnectorNaming.stripSuffix(to.label()).equals(base) || to.label().equals(base)) {
                to.setLabel(graph == null ? base + "_1" : ConnectorNaming.nextFree(base, graph));
            }
        }
        player.sendOverlayMessage(Component.translatable("message.factorynetwork.wrench.pasted",
                to.label().isBlank() ? "?" : to.label()));
        return to.label();
    }
}
