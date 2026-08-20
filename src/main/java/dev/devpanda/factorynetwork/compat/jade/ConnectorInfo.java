package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
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
 * Was Jade über einen Connector sagt.
 *
 * <p>Vier Zustände sind zu unterscheiden, und drei davon sehen im Spiel
 * gleich aus: benannt und erreichbar, unbenannt, doppelt vergeben, oder ohne
 * freien Kanal. Wer den letzten für einen Tippfehler hält, sucht lange.
 */
public enum ConnectorInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_LABEL = "FnLabel";
    private static final String KEY_STATE = "FnState";
    private static final String KEY_COST = "FnCost";

    private static final int STATE_FINE = 0;
    private static final int STATE_UNNAMED = 1;
    private static final int STATE_DUPLICATE = 2;
    private static final int STATE_NO_CHANNEL = 3;
    private static final int STATE_NO_NETWORK = 4;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ConnectorBlockEntity connector)) {
            return;
        }
        String label = connector.label();
        data.putString(KEY_LABEL, label == null ? "" : label);
        data.putInt(KEY_COST, connector.channelCost());

        var pos = accessor.getPosition();
        var owner = ControllerRegistry.owning(accessor.getLevel(), pos);
        if (owner.isEmpty()) {
            data.putInt(KEY_STATE, STATE_NO_NETWORK);
            return;
        }
        var graph = owner.get().graph();
        int state;
        if (graph.isStarved(pos)) {
            state = STATE_NO_CHANNEL;
        } else if (label == null || label.isBlank()) {
            state = STATE_UNNAMED;
        } else if (graph.isAmbiguous(label)) {
            state = STATE_DUPLICATE;
        } else {
            state = STATE_FINE;
        }
        data.putInt(KEY_STATE, state);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_STATE)) {
            return;
        }
        String label = data.getString(KEY_LABEL);
        int state = data.getInt(KEY_STATE);

        switch (state) {
            case STATE_FINE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.named", label)
                    .withStyle(ChatFormatting.GREEN));
            case STATE_UNNAMED -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.unnamed")
                    .withStyle(ChatFormatting.GRAY));
            case STATE_DUPLICATE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.duplicate", label)
                    .withStyle(ChatFormatting.RED));
            case STATE_NO_CHANNEL -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.no_channel")
                    .withStyle(ChatFormatting.RED));
            default -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.no_network")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        int cost = data.getInt(KEY_COST);
        if (cost > 1) {
            tooltip.add(Component.translatable("jade.factorynetwork.connector.cost", cost)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.CONNECTOR;
    }
}
