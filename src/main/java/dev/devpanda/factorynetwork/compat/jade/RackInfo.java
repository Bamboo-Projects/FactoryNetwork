package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
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
 * Was Jade über einen Serverschrank sagt: wie viele Prozessoren stecken und
 * wie viele Abläufe damit gleichzeitig laufen dürfen.
 *
 * <p>Die zweite Zahl ist die, um die es geht. Ein Schrank mit acht kleinen
 * Prozessoren und einer mit zwei Co-Prozessoren sehen gleich voll aus und
 * tragen doch verschieden viel.
 */
public enum RackInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_SLOTS = "FnRackSlots";
    private static final String KEY_THREADS = "FnRackThreads";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof RackBlockEntity rack)) {
            return;
        }
        data.putInt(KEY_SLOTS, rack.usedSlots());
        data.putInt(KEY_THREADS, rack.threads());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_SLOTS)) {
            return;
        }
        int slots = data.getInt(KEY_SLOTS);
        if (slots == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.rack.empty")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(Component.translatable("jade.factorynetwork.rack.slots",
                slots, RackBlockEntity.SLOTS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("jade.factorynetwork.rack.threads",
                data.getInt(KEY_THREADS)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.RACK;
    }
}
