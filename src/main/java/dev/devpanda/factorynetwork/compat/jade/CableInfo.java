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
 * What Jade says about a cable.
 *
 * <p>One line per strand: colour and channel count. This is the very
 * information Jade is here for — how many of the eight wires are still free at
 * this spot is otherwise invisible on the cable.
 */
public enum CableInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_STRANDS = "FnStrands";

    /**
     * The server fetches the numbers: the graph lives at the controller, and
     * the client doesn't know it.
     */
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var level = accessor.getLevel();
        var pos = accessor.getPosition();
        ListTag lines = new ListTag();

        var controller = ControllerRegistry.owning(level, pos);
        CableColour colour = CableBlock.colourOf(level.getBlockState(pos));
        // The capacity is a property of the cable, not of the mod: a thick one
        // carries sixty-four, an ordinary one sixteen. With a fixed number, a
        // dense cable would report "full" from the sixteenth channel on.
        // Throughput per tick, not channel load: since 29 Aug what is limited
        // is how much passes through, and not how many devices hang behind it.
        // In bytes per second, not as a raw per-tick number: "500" says
        // nothing, "10 KB/s" does.
        String load = dev.devpanda.factorynetwork.network.Bandwidth.perSecond(
                dev.devpanda.factorynetwork.network.Bandwidth.at(level, pos));
        lines.add(StringTag.valueOf(colour.getSerializedName() + " " + load));
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
                    "jade.factorynetwork.colour." + parts[0]);
            String load = parts.length > 1 ? parts[1] : "";
            // With no controller in range, a dash stands in place of a number;
            // a cable without a network has no throughput, not zero.
            ChatFormatting colour = load.startsWith("—")
                    ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY;
            tooltip.add(Component.translatable("jade.factorynetwork.cable.colour", name, load)
                    .withStyle(colour));
        }
    }

    /** In "12/16", is the number on the left the same as the one on the right? */
    private static boolean isFull(String load) {
        int slash = load.indexOf('/');
        return slash > 0 && load.substring(0, slash).equals(load.substring(slash + 1));
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.CABLE;
    }
}
