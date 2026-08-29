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
 * Was Jade über einen Router sagt.
 *
 * <p>Nach Bahnen gruppiert, nicht nach Seiten: Die Frage vor dem Block ist
 * „was hängt hier zusammen", und die beantwortet eine Zeile je Bahn mit den
 * Seiten darin. Sechs Zeilen mit je einer Seite wären dieselbe Auskunft, nur
 * müsste man sie selbst sortieren.
 */
public enum RouterInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_LANES = "FnRouterLanes";

    /**
     * Trennzeichen der Zeilen zwischen Server und Client.
     *
     * <p>Ein Semikolon, weil {@code split} einen regulären Ausdruck nimmt:
     * Ein senkrechter Strich hieße dort „oder" und müsste geschützt werden.
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
            // Ohne Controller in Reichweite gibt es keine Kanalzahlen: Eine
            // Bahn ohne Netz trägt nichts, das ist nicht dasselbe wie null.
            // Was eine Bahn trägt, ist ihr Durchsatz je Tick — nicht,
            // wie viele Geräte dahinter hängen.
            String load = String.valueOf(
                    dev.devpanda.factorynetwork.network.Bandwidth.DENSE);
            lines.add(StringTag.valueOf(lane + SEPARATOR + load + SEPARATOR
                    + dev.devpanda.factorynetwork.network.Bandwidth.DENSE
                    + SEPARATOR + String.join(",", sides)));
            // Die Kapazität steht als ganze Zahl da: Ein Router trägt immer
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
            if (parts.length < 4) {
                continue;
            }
            MutableComponent sides = sideList(parts[3]);
            if ("0".equals(parts[0])) {
                tooltip.add(Component.translatable("jade.factorynetwork.router.off", sides)
                        .withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            boolean full = parts[2].equals(parts[1]);
            tooltip.add(Component.translatable("jade.factorynetwork.router.lane",
                            parts[0], parts[1], parts[2], sides)
                    .withStyle(full ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
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
