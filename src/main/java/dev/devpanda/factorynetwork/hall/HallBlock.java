package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The block of a factory hall: a right-click leads inside.
 *
 * <p>One block per size, not one block with a size property: the three are
 * crafted, listed and named apart, and a property would only move that
 * distinction into every recipe and tooltip.
 */
public class HallBlock extends Block implements EntityBlock {

    private final HallTier tier;

    public HallBlock(HallTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public HallTier tier() {
        return tier;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HallBlockEntity(pos, state);
    }

    /**
     * A block with a number placed again: its hall runs from here on. A copy
     * whose original still stands changes nothing; entering it says so.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.@Nullable LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && server.dimension() != Halls.DIMENSION
                && level.getBlockEntity(pos) instanceof HallBlockEntity door
                && door.hall() != HallBlockEntity.NONE) {
            Halls halls = Halls.of(server.getServer());
            Halls.Entry entry = halls.get(door.hall());
            if (entry != null && entry.door().isEmpty()) {
                halls.setDoor(door.hall(), net.minecraft.core.GlobalPos.of(server.dimension(), pos));
                HallLoading.hold(door, true);
            }
        }
    }

    /** With a blueprint in hand the item decides; otherwise the door opens as usual. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack held, BlockState state, Level level,
                                          BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (held.getItem() instanceof BlueprintItem) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HallBlockEntity door)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            HallDoor.enter(server, door);
        }
        return InteractionResult.CONSUME;
    }

    /** The item's tooltip: size, and the number once there is one. */
    public static void describe(net.minecraft.world.item.ItemStack stack, HallTier tier,
                                java.util.List<Component> lines) {
        lines.add(Component.translatable("item.factorynetwork.hall.size",
                tier.size(), tier.size(), tier.size()).withStyle(ChatFormatting.GRAY));
        Integer hall = stack.get(FnComponents.HALL.get());
        lines.add(hall == null
                ? Component.translatable("item.factorynetwork.hall.new").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("item.factorynetwork.hall.number", hall).withStyle(ChatFormatting.AQUA));
    }
}
