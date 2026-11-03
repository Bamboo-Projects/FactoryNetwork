package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.platform.Chat;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Turns a piece of hall wall into a port.
 *
 * <p>Right-click on the inside of a hall wall: that block becomes a port, and
 * a cable placed on it is connected to the network outside the hall. As many
 * as wanted, wherever they are needed.
 */
public class HallPortItem extends Item {

    public HallPortItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(FnBlocks.HALL_WALL.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Halls.DIMENSION || Halls.hallAt(pos) == 0) {
            if (context.getPlayer() != null) {
                Chat.tell(context.getPlayer(), Component.translatable("message.factorynetwork.hall_port.outside"), true);
            }
            return InteractionResult.FAIL;
        }
        level.setBlockAndUpdate(pos, FnBlocks.HALL_PORT.get().defaultBlockState());
        if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
