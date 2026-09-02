package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Access to the code editor. */
public class TerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** The hitbox, once for each of the four directions. */
    private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape>
            SHAPES = FacingShapes.horizontal(TerminalLayout.boxes());

    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal
                && player instanceof ServerPlayer serverPlayer) {
            // Send the state first, then open: the editor should already have
            // the connector list when it draws for the first time.
            terminal.sendStateTo(serverPlayer);
            // The same three fields as for remote access: position, whether a
            // device is in play, and where it is. The menu constructor reads
            // them in this order, and a missing field shifts all that follow.
            serverPlayer.openMenu(terminal, buffer ->
                    dev.devpanda.factorynetwork.client.menu.TerminalMenu
                            .writeBlock(buffer, pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING),
                net.minecraft.world.phys.shapes.Shapes.block());
    }
}
