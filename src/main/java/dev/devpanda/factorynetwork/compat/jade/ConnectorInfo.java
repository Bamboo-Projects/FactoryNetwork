package dev.devpanda.factorynetwork.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What Jade says about a connector.
 *
 * <p>Four states must be told apart, and three of them look the same in-game:
 * named and reachable, unnamed, duplicated, or without a free channel. Anyone
 * who mistakes the last one for a typo will search a long time.
 */
public enum ConnectorInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_LABEL = "FnLabel";
    private static final String KEY_STATE = "FnState";
    private static final String KEY_COST = "FnCost";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var connector = partAt(accessor);
        if (connector == null) {
            return;
        }
        String label = connector.label();
        data.putString(KEY_LABEL, label == null ? "" : label);
        data.putInt(KEY_COST, connector.channelCost());
        // <b>The same state the indicator light shows.</b> Previously Jade
        // computed it here a second time from the graph — the same question,
        // two answers, and one of them got improvements the other lacked. It
        // is computed on rebuild, which runs on every change to the network.
        data.putInt(KEY_STATE, connector.state().id());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_STATE)) {
            return;
        }
        String label = data.getString(KEY_LABEL);
        int state = data.getInt(KEY_STATE);

        switch (dev.devpanda.factorynetwork.network.DeviceState.byId(state)) {
            case ONLINE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.named", label)
                    .withStyle(ChatFormatting.GREEN));
            case UNNAMED -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.unnamed")
                    .withStyle(ChatFormatting.GRAY));
            case DUPLICATE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.duplicate", label)
                    .withStyle(ChatFormatting.RED));
            // Only from old worlds now: since 29 Aug this state is no longer
            // assigned, and on the first network build the device gets its
            // correct one.
            case STARVED -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.saturated")
                    .withStyle(ChatFormatting.GOLD));
            case OFFLINE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.no_network")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

    }

    /**
     * The connection the player is currently looking at.
     *
     * <p>On the cable block the face that was hit decides: up to six hang
     * there, and without it any one of them would show in the window. On the
     * dedicated connector block there is only one, and the face-based path
     * quietly falls back to it.
     */
    private static dev.devpanda.factorynetwork.block.entity.ConnectorPart partAt(BlockAccessor accessor) {
        var level = accessor.getLevel();
        var pos = accessor.getPosition();
        var side = dev.devpanda.factorynetwork.block.CableBlock.partSideAt(
                level, pos, accessor.getHitResult());
        return side == null
                ? dev.devpanda.factorynetwork.block.entity.Connectors.at(level, pos)
                : dev.devpanda.factorynetwork.block.entity.Connectors.at(level, pos, side);
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.CONNECTOR;
    }
}
