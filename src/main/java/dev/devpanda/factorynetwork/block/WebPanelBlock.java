package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.WebPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Eine Web-Fläche an der Wand.
 *
 * <p>Derselbe Gedanke wie beim Display — man soll im Vorbeigehen sehen, wie es
 * der Fabrik geht, ohne etwas anzuklicken —, nur zeigt diese Tafel eine Seite
 * und keine Textzeilen. Was für eine, entscheidet der Spieler, der sie setzt.
 *
 * <p><b>Flach an der Wand und nicht als Würfel.</b> Zwei Pixel tief, wie das
 * Display: Eine Anzeige hängt, sie steht nicht im Raum. Die Bauform ist
 * dieselbe, damit sich beide nebeneinander setzen lassen, ohne dass eine
 * hervorsteht.
 */
public class WebPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<WebPanelBlock> CODEC = simpleCodec(WebPanelBlock::new);

    /** Zwei Pixel tief — dieselben Maße wie beim Display. */
    private static final VoxelShape NORTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WEST = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 2, 16, 16);

    public WebPanelBlock(Properties properties) {
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Die Vorderseite zeigt den Spieler an, nicht von ihm weg: Wer eine
        // Tafel setzt, will sie ansehen.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Ein benannter Gegenstand macht eine benannte Tafel.
     *
     * <p>Wie bei einer Kiste oder einem Ofen: Wer im Amboss einen Namen
     * vergibt, findet ihn danach an der Tafel wieder — im Protokoll und in
     * Chromiums Liste unter dem Fernwartungsport. Eine eigene Oberfläche dafür
     * wäre eine zweite Art, dasselbe zu tun.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof WebPanelBlockEntity panel) {
            panel.setName(stack.getHoverName().getString());
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WebPanelBlockEntity(pos, state);
    }
}
