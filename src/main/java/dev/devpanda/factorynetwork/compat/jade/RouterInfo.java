package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * What Jade says about a router.
 *
 * <p>Grouped by lanes, not by sides: the question in front of the block is
 * "what belongs together here", and one line per lane with the sides in it
 * answers it. Six lines with one side each would be the same information, only
 * you'd have to sort them yourself.
 */
public enum RouterInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_LANES = "FnRouterLanes";

    /**
     * Line separator between server and client.
     *
     * <p>A semicolon, because {@code split} takes a regular expression: a
     * vertical bar would mean "or" there and would have to be escaped.
     */
    private static final String SEPARATOR = ";";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof RouterBlockEntity router)) {
            return;
        }
        var level = accessor.getLevel();
        var pos = accessor.getPosition();
        var controller = ControllerRegistry.owning(level, pos);
        ListTag lines = new ListTag();

        for (int lane = 1; lane <= RouterBlockEntity.LANES; lane++) {
            List<String> sides = sidesOn(router, lane);
            if (sides.isEmpty()) {
                continue;
            }
            final int current = lane;
            // With no controller in range there are no channel counts: a lane
            // without a network carries nothing, which is not the same as
            // zero. What a lane carries is its throughput per tick — not how
            // many devices hang behind it.
            // Raw over the wire, formatted at display time: the viewer's
            // language file decides what a number looks like, not the
            // server's.
            int durchsatzProTick = dev.devpanda.factorynetwork.network.Bandwidth.CABLE;
            lines.add(StringTag.valueOf(lane + SEPARATOR + durchsatzProTick
                    + SEPARATOR + String.join(",", sides)));
            // The capacity is given as a whole number: a router always carries
        }
        List<String> off = sidesOn(router, RouterBlockEntity.OFF);
        if (!off.isEmpty()) {
            lines.add(StringTag.valueOf("0" + SEPARATOR + SEPARATOR + SEPARATOR
                    + String.join(",", off)));
        }
        data.put(KEY_LANES, lines);
    }

    private static List<String> sidesOn(RouterBlockEntity router, int lane) {
        List<String> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (router.lane(side) == lane) {
                sides.add(side.getSerializedName());
            }
        }
        return sides;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ListTag lines = accessor.getServerData().getList(KEY_LANES, Tag.TAG_STRING);
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.getString(i).split(SEPARATOR, -1);
            if (parts.length < 3) {
                continue;
            }
            MutableComponent sides = sideList(parts[2]);
            if ("0".equals(parts[0])) {
                tooltip.add(Component.translatable("jade.factorynetwork.router.off", sides)
                        .withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            // What this side lets through, as a name — a number has said
            // nothing since 29 Aug: the router carries colours.
            int wert = Integer.parseInt(parts[0]);
            Component filter = wert == dev.devpanda.factorynetwork.block.entity
                    .RouterBlockEntity.ALL
                    ? Component.translatable("screen.factorynetwork.router.all")
                    : colourName(wert);
            int durchsatz = Integer.parseInt(parts[1]);
            tooltip.add(Component.translatable("jade.factorynetwork.router.lane",
                            filter,
                            dev.devpanda.factorynetwork.network.Bandwidth.perSecond(durchsatz),
                            sides)
                    .withStyle(ChatFormatting.GRAY));
        }
        // And what it costs. Filtering takes time — here is the cause of a
        // delay you would otherwise only feel.
        if (!lines.isEmpty()) {
            tooltip.add(Component.translatable("jade.factorynetwork.router.latency",
                            dev.devpanda.factorynetwork.network.Latency.PER_HOP)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The name of the colour behind a setting number. */
    private static Component colourName(int wert) {
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        var farbe = farben[Math.max(0, Math.min(wert - 2, farben.length - 1))];
        return Component.translatable("colour.factorynetwork." + farbe.getSerializedName());
    }

    private static MutableComponent sideList(String joined) {
        MutableComponent result = Component.empty();
        String[] names = joined.split(",");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(Component.translatable("side.factorynetwork." + names[i]));
        }
        return result;
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.ROUTER;
    }
}
