package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Was Jade über ein Kabel sagt.
 *
 * <p>Je Strang eine Zeile: Farbe und Kanalstand. Das ist die Auskunft, für
 * die es Jade hier überhaupt gibt — wie viele der acht Drähte an dieser
 * Stelle noch frei sind, sieht man dem Kabel sonst nicht an.
 */
public enum CableInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_STRANDS = "FnStrands";

    /**
     * Die Zahlen holt der Server: Der Graph liegt beim Controller, und den
     * kennt der Client nicht.
     */
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var level = accessor.getLevel();
        var pos = accessor.getPosition();
        ListTag lines = new ListTag();

        var controller = ControllerRegistry.owning(level, pos);
        for (CableColour strand : CableBlock.strandsAt(level, pos)) {
            String load = controller
                    .map(entity -> {
                        FactoryGraph graph = entity.graph();
                        return graph.channelLoad(pos, strand) + "/"
                                + FactoryGraph.CHANNELS_PER_STRAND;
                    })
                    .orElse("—");
            lines.add(StringTag.valueOf(strand.getSerializedName() + " " + load));
        }
        data.put(KEY_STRANDS, lines);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ListTag lines = accessor.getServerData().getList(KEY_STRANDS, Tag.TAG_STRING);
        if (lines.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.getString(i).split(" ", 2);
            Component name = Component.translatable(
                    "jade.factorynetwork.strand." + parts[0]);
            String load = parts.length > 1 ? parts[1] : "";
            // Ohne Controller in Reichweite steht ein Strich statt einer Zahl;
            // ein Kabel ohne Netz hat keine Kanäle, nicht null.
            ChatFormatting colour = load.startsWith("8/") ? ChatFormatting.RED
                    : load.startsWith("—") ? ChatFormatting.DARK_GRAY
                    : ChatFormatting.GRAY;
            tooltip.add(Component.translatable("jade.factorynetwork.cable.strand", name, load)
                    .withStyle(colour));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.CABLE;
    }
}
