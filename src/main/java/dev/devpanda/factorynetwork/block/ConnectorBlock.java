package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Gibt der Maschine, an der er hängt, einen Namen im Netzwerk.
 *
 * <p>Der Connector zeigt in die Richtung der Maschine. Ohne Namen ist er im
 * Netz unsichtbar — ein Bus tut nichts, solange kein Code für ihn existiert,
 * und ohne Namen kann kein Code ihn ansprechen.
 */
public class ConnectorBlock extends Block implements EntityBlock {

    public static final MapCodec<ConnectorBlock> CODEC = simpleCodec(ConnectorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Zeigt auf den Block, an den er gesetzt wurde.
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ConnectorBlockEntity)) {
            return InteractionResult.PASS;
        }
        // Auch dieser Block nennt seine Fläche. Er hat nur eine, aber
        // dahinter läuft derselbe Weg wie am Kabelblock — und der kennt
        // keinen Anschluss ohne Seite.
        Direction side = machineSide(state);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                                .NameMenu(id, pos, side),
                        Component.translatable("screen.factorynetwork.name.title.connector")),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(side.get3DDataValue());
                });
        return InteractionResult.CONSUME;
    }

    /**
     * Gibt Redstone aus, wenn das Programm es verlangt.
     *
     * <p>Nach allen Seiten gleich. Eine Richtung anzugeben wäre genauer, aber
     * der Connector zeigt schon in eine — er hätte dann zwei Richtungen, und
     * niemand wüsste, welche gemeint ist.
     */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                            BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector
                ? connector.emittedRedstone() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** Die Seite, an der die Maschine sitzt. */
    public static Direction machineSide(BlockState state) {
        return state.getValue(FACING);
    }
}
