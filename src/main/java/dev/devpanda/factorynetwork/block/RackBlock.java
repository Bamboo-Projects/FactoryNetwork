package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.item.ProcessorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Der Serverschrank: Ohne ihn rechnet das Netz nicht.
 *
 * <p>So wie ein Laufwerk die Voraussetzung dafür ist, dass das Netz lagert,
 * ist der Schrank die Voraussetzung dafür, dass es rechnet. Jede Fähigkeit
 * des Netzes hängt an einem Block, den man bauen muss — das ist das Bild,
 * das die Mod durchgehend trägt.
 *
 * <p>Bedient wie das Laufwerk: Prozessor in der Hand hineinklicken, leere
 * Hand nimmt den letzten heraus. Wer eines bedienen kann, kann auch das
 * andere.
 */
public class RackBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RackBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(stack.getItem() instanceof ProcessorItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) {
            return ItemInteractionResult.FAIL;
        }
        int slot = rack.firstFreeSlot();
        if (slot < 0) {
            player.displayClientMessage(
                    Component.translatable("message.factorynetwork.rack.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        // Der ganze Stapel geht hinein: Prozessoren stapeln sich, und acht
        // Plätze wären sonst acht Klicks für acht Stück desselben.
        rack.setProcessor(slot, stack.copyAndClear());
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS,
                0.7F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.factorynetwork.rack.inserted", rack.threads()), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) {
            return InteractionResult.PASS;
        }
        int slot = rack.lastUsedSlot();
        if (slot < 0) {
            player.displayClientMessage(
                    Component.translatable("message.factorynetwork.rack.empty"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack taken = rack.processor(slot);
        rack.setProcessor(slot, ItemStack.EMPTY);
        if (!player.getInventory().add(taken)) {
            popResource(level, pos, taken);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS,
                0.7F, 1.2F);
        player.displayClientMessage(Component.translatable(
                "message.factorynetwork.rack.removed", rack.threads()), true);
        return InteractionResult.CONSUME;
    }

    /** Beim Abbauen fallen die Prozessoren heraus. Die Loot-Tabelle sieht sie nicht. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            for (ItemStack stack : rack.contents()) {
                if (!stack.isEmpty()) {
                    popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
