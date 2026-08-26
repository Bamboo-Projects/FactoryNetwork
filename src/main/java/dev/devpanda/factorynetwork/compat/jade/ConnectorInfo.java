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

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var connector = partAt(accessor);
        if (connector == null) {
            return;
        }
        String label = connector.label();
        data.putString(KEY_LABEL, label == null ? "" : label);
        data.putInt(KEY_COST, connector.channelCost());
        // <b>Derselbe Zustand, den das Lämpchen zeigt.</b> Vorher rechnete
        // Jade ihn hier ein zweites Mal aus dem Graphen — dieselbe Frage,
        // zwei Antworten, und die eine bekam Verbesserungen, die der anderen
        // fehlten. Gerechnet wird er beim Neuaufbau, und der läuft bei jeder
        // Änderung am Netz.
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
            case STARVED -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.no_channel")
                    .withStyle(ChatFormatting.RED));
            case OFFLINE -> tooltip.add(Component.translatable(
                    "jade.factorynetwork.connector.no_network")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        int cost = data.getInt(KEY_COST);
        if (cost > 1) {
            tooltip.add(Component.translatable("jade.factorynetwork.connector.cost", cost)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Der Anschluss, auf den der Spieler gerade sieht.
     *
     * <p>Am Kabelblock entscheidet die getroffene Fläche: Dort hängen bis zu
     * sechs, und ohne sie stünde im Fenster irgendeiner davon. Am eigenen
     * Connectorblock gibt es nur einen, und der Weg über die Fläche fällt
     * still auf ihn zurück.
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
