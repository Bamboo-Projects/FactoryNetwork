package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The connector you can place even without a cable.
 *
 * <p><b>Why this works.</b> Whoever builds an installation places the
 * machines first and runs the wiring afterwards. If the cable had to be
 * there first, the order would be prescribed — and whoever reverses it
 * places connectors a second time.
 *
 * <p>What arises in the process is a cable block without a cable run: a
 * holder in which the connector sits and which hangs on no network. A cable
 * on it turns that into a line, without the connector having to be placed
 * anew.
 *
 * <p>That is how AE2 does it. There the block is a cable bus, and the cable
 * is only one of the parts in it — which is why a bus may exist without one
 * too.
 */
public class ConnectorItem extends Item {

    public ConnectorItem(Properties properties) {
        super(properties);
    }

    /**
     * Places a holder in front of the clicked face.
     *
     * <p>The connector points at the block that was clicked — that is the
     * gesture you make anyway: click the device you want to connect.
     *
     * <p>Clicking on a cable or an existing holder does not go through here:
     * {@link CableBlock} handles that itself, and the preview sits there too.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockState(clicked).getBlock() instanceof CableBlock) {
            return InteractionResult.PASS;
        }
        Direction face = context.getClickedFace();
        BlockPos target = clicked.relative(face);
        if (!level.getBlockState(target).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // A holder: the same block as a cable, only without a cable run in it.
        level.setBlock(target, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.CABLE, false), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(target) instanceof CableBusBlockEntity bus)) {
            // Should not happen; if it does, do not leave an empty block
            // standing — it would be invisible and impossible to click.
            level.removeBlock(target, false);
            return InteractionResult.FAIL;
        }
        bus.addPart(face.getOpposite());
        if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, target, SoundEvents.NETHERITE_BLOCK_PLACE,
                SoundSource.BLOCKS, 0.8F, 1.2F);
        ControllerRegistry.refreshAround(level, target);
        return InteractionResult.CONSUME;
    }
}
