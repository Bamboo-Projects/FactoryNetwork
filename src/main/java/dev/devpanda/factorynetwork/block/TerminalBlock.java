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

/** Zugang zum Code-Editor. */
public class TerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Die Trefferfläche, für jede der vier Richtungen einmal. */
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
            // Erst den Zustand schicken, dann öffnen: Der Editor soll die
            // Connectorliste schon haben, wenn er das erste Mal zeichnet.
            terminal.sendStateTo(serverPlayer);
            // Dieselben drei Felder wie beim Fernzugriff: Position, ob ein
            // Gerät im Spiel ist, und wo es liegt. Der Menü-Konstruktor liest
            // sie in dieser Reihenfolge, und ein fehlendes Feld verschiebt
            // alle folgenden.
            serverPlayer.openMenu(terminal, buffer -> {
                buffer.writeBlockPos(pos);
                // Keine fremde Welt und kein Gerät: Der Block steht dort, wo
                // der Spieler steht. Die Felder müssen trotzdem geschrieben
                // werden — der Menü-Konstruktor liest sie in dieser
                // Reihenfolge, und ein fehlendes verschiebt alle folgenden.
                buffer.writeBoolean(false);
                buffer.writeBoolean(false);
                buffer.writeVarInt(-1);
            });
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
