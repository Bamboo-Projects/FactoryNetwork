package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.platform.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** The storage link: which system it is attached to, and whether it is reachable. */
public enum StorageLinkInfo implements IBlockComponentProvider,
        snownee.jade.api.IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_SYSTEM = "FnLinkSystem";
    private static final String KEY_REACHABLE = "FnLinkReachable";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof StorageLinkBlockEntity link)) {
            return;
        }
        var adapter = link.adapter();
        data.putString(KEY_SYSTEM, adapter == null ? "" : adapter.systemName());
        data.putBoolean(KEY_REACHABLE, adapter != null && adapter.items() != null);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_SYSTEM)) {
            return;
        }
        String system = Nbt.getString(data, KEY_SYSTEM);
        if (system.isEmpty()) {
            tooltip.add(Component.translatable("message.factorynetwork.storage_link.none")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(Component.translatable("message.factorynetwork.storage_link.attached", system)
                .withStyle(ChatFormatting.GRAY));
        if (!Nbt.getBoolean(data, KEY_REACHABLE)) {
            // AE2 without a free channel or power, RS without energy.
            tooltip.add(Component.translatable("jade.factorynetwork.storage_link.offline")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.STORAGE_LINK;
    }
}
