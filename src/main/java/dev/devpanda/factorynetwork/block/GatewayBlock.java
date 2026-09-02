package dev.devpanda.factorynetwork.block;

import dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of cable that gives its surroundings an installation name.
 *
 * <p><b>An installation has so far come about through the labelling alone:</b>
 * {@code werk_1/eingang}, {@code werk_1/ausgang}. That works and stays — but it
 * demands that you repeat the installation name on every single device, and
 * whoever wants to change it walks them all over again.
 *
 * <p>This block is the other answer to the same question: <b>An installation is
 * something contiguous.</b> Whatever hangs on the cable behind the gateway
 * belongs to its installation — without the name standing on a single device.
 * A rename is then one block and not twelve.
 *
 * <p><b>It does not multiply channels.</b> It passes through the run it sits
 * on, and a dense cable carries sixty-four — that does not grow because of it.
 * This is the same rule the controller extension hangs on too: a placeable
 * channel multiplier would make the channel limit meaningless.
 *
 * <p><b>The labelling wins.</b> If a device itself carries a slash, that one
 * applies. Otherwise a placed block would have silently changed what a program
 * says about a device — and that is the kind of surprise you spend the longest
 * hunting for.
 */
public class GatewayBlock extends Block implements EntityBlock {

    /**
     * The archway as a hitbox — the same boxes as in the model.
     *
     * <p>Without it you reach into the openings and still hit the whole cube.
     * That barely shows when breaking and shows at once when aiming:
     * the frame stands there visibly, and the hand holds one block pixel
     * beside it.
     */
    private static final VoxelShape SHAPE = FacingShapes.whole(GatewayLayout.boxes());

    public GatewayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GatewayBlockEntity(pos, state);
    }

    /**
     * Right-clicking names the installation — the same screen as on the
     * connector.
     *
     * <p>A dedicated one would be a second form for the same action. What
     * shows in it is only the upper field here: a gateway has an
     * installation and no role.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GatewayBlockEntity)) {
            return InteractionResult.PASS;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                                .NameMenu(id, pos),
                        Component.translatable("screen.factorynetwork.name.title.gateway")),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(dev.devpanda.factorynetwork.network.packet.SetBlockNamePacket.NO_SIDE);
                });
        return InteractionResult.CONSUME;
    }
}
