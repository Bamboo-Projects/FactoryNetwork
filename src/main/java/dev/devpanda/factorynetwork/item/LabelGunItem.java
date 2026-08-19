package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Vergibt einem Connector seinen Namen.
 *
 * <p>Der Name steht im Anzeigenamen des Gegenstands — wer die Gun in einem
 * Amboss umbenennt, benennt damit den nächsten Connector. Das spart eine
 * eigene Oberfläche und ist im Spiel sofort verständlich.
 */
public class LabelGunItem extends Item {

    public LabelGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity entity = level.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof ConnectorBlockEntity connector)) {
            return InteractionResult.PASS;
        }
        ItemStack gun = context.getItemInHand();
        Component custom = gun.get(DataComponents.CUSTOM_NAME);
        if (custom == null) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.factorynetwork.label_gun.unnamed"), true);
            }
            return InteractionResult.CONSUME;
        }
        String label = custom.getString();
        connector.setLabel(label);
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.factorynetwork.label_gun.applied", label), true);
        }
        return InteractionResult.CONSUME;
    }
}
