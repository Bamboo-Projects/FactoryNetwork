package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
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
 * Was Jade über die Presse sagt.
 *
 * <p>Vor allem eines: <b>warum sie steht</b>. Eine Maschine ohne Strom und
 * eine ohne Material sehen gleich aus, und der Unterschied entscheidet, wo man
 * suchen muss.
 */
public enum PressInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_ENERGY = "FnEnergy";
    private static final String KEY_CAPACITY = "FnCapacity";
    private static final String KEY_PROGRESS = "FnProgress";
    private static final String KEY_REQUIRED = "FnRequired";
    private static final String KEY_HAS_STAMP = "FnStamp";
    private static final String KEY_HAS_MATERIAL = "FnMaterial";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PressBlockEntity press)) {
            return;
        }
        data.putInt(KEY_ENERGY, press.energy().getEnergyStored());
        data.putInt(KEY_CAPACITY, PressBlockEntity.CAPACITY);
        data.putInt(KEY_PROGRESS, press.progress());
        data.putInt(KEY_REQUIRED, press.required());
        data.putBoolean(KEY_HAS_STAMP, !press.item(PressBlockEntity.SLOT_STAMP).isEmpty());
        data.putBoolean(KEY_HAS_MATERIAL, !press.item(PressBlockEntity.SLOT_MATERIAL).isEmpty());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_ENERGY)) {
            return;
        }
        int energie = data.getInt(KEY_ENERGY);
        int fassung = Math.max(1, data.getInt(KEY_CAPACITY));
        tooltip.add(Component.translatable("jade.factorynetwork.press.energy",
                        String.format(java.util.Locale.GERMANY, "%,d", energie),
                        energie * 100 / fassung)
                .withStyle(energie > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));

        int fortschritt = data.getInt(KEY_PROGRESS);
        int noetig = data.getInt(KEY_REQUIRED);
        if (fortschritt > 0 && noetig > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.press.progress",
                    fortschritt * 100 / noetig).withStyle(ChatFormatting.GREEN));
            return;
        }

        // Steht sie, dann sagen warum — in der Reihenfolge, in der man es
        // beheben würde.
        String grund = !data.getBoolean(KEY_HAS_STAMP) ? "no_stamp"
                : !data.getBoolean(KEY_HAS_MATERIAL) ? "no_material"
                : energie <= 0 ? "no_power"
                : "no_recipe";
        tooltip.add(Component.translatable("jade.factorynetwork.press." + grund)
                .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.PRESS;
    }
}
