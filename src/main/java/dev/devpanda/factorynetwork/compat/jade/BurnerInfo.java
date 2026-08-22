package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity;
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
 * Was Jade über die Brennkammer sagt.
 *
 * <p>Die zweite Zeile ist die, um die es geht: <b>Ein voller Vorrat heißt,
 * dass niemand abnimmt.</b> Eine Kammer, die brennt und deren Vorrat oben
 * ansteht, verheizt Kohle für nichts — und das sieht man ihr sonst nicht an.
 */
public enum BurnerInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_BURNING = "FnBurning";
    private static final String KEY_STORED = "FnBurnerStored";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BurnerBlockEntity burner)) {
            return;
        }
        data.putBoolean(KEY_BURNING, burner.isBurning());
        data.putInt(KEY_STORED, burner.stored());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_STORED)) {
            return;
        }
        boolean burning = data.getBoolean(KEY_BURNING);
        tooltip.add(Component.translatable(burning
                        ? "jade.factorynetwork.burner.burning"
                        : "jade.factorynetwork.burner.idle",
                BurnerBlockEntity.PER_TICK)
                .withStyle(burning ? ChatFormatting.GRAY : ChatFormatting.YELLOW));

        int stored = data.getInt(KEY_STORED);
        tooltip.add(Component.translatable("jade.factorynetwork.burner.stored",
                        grouped(stored), grouped(BurnerBlockEntity.CAPACITY))
                .withStyle(ChatFormatting.GRAY));
        if (stored >= BurnerBlockEntity.CAPACITY) {
            tooltip.add(Component.translatable("jade.factorynetwork.burner.full")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String grouped(int value) {
        return String.format(java.util.Locale.GERMANY, "%,d", value);
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.BURNER;
    }
}
