package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a cable in its colour.
 *
 * <p>All seventeen items place the same block — only with a different state.
 * The difference is no trifle: which cables connect depends on the colour.
 */
public class ColouredCableItem extends BlockItem {

    private final CableColour colour;

    public ColouredCableItem(Block block, CableColour colour, Properties properties) {
        super(block, properties);
        this.colour = colour;
    }

    public CableColour colour() {
        return colour;
    }

    /**
     * Each of the seventeen items has a different name.
     *
     * <p>A {@link BlockItem} otherwise takes its name from the block, and all
     * seventeen point at the same one — in the creative tab it said "Cable"
     * seventeen times. Here the item's name counts, not the block's.
     */
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    /**
     * Ask the block first, then place beside it.
     *
     * <p><b>A {@link BlockItem} otherwise does not ask at all.</b> It looks
     * for a free spot and places there — and a holder that already has a
     * connector sitting in it counts as occupied. The click landed beside it
     * instead of putting the cable into the holder.
     *
     * <p>The same order the connector takes: the block gets the first chance,
     * and only if it declines is a block placed.
     *
     * <p><b>Except when sneaking.</b> Whoever clicks while sneaking wants to
     * build beside it — that is the gesture that everywhere in Minecraft
     * means "ignore the block".
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(
            net.minecraft.world.item.context.UseOnContext context) {
        var player = context.getPlayer();
        if (player != null && !player.isSecondaryUseActive()) {
            var level = context.getLevel();
            var pos = context.getClickedPos();
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof CableBlock && !CableBlock.carries(state)) {
                // Build the hit result ourselves: UseOnContext holds it but
                // does not hand it out. It has the three values that matter.
                var hit = new net.minecraft.world.phys.BlockHitResult(
                        context.getClickLocation(), context.getClickedFace(), pos, false);
                var result = state.useItemOn(context.getItemInHand(), level, player,
                        context.getHand(), hit);
                if (result.consumesAction()) {
                    return result.result();
                }
            }
        }
        return super.useOn(context);
    }

    /**
     * On placement the block takes the item's colour.
     *
     * <p>Without this the neutral cable would stand everywhere: the state
     * comes from the block, not the item, and otherwise the two do not know
     * each other.
     */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) {
            return null;
        }
        // Colour first, then let the connections be computed. The other way
        // round they would be those of a neutral cable — and a red one would
        // reach for every neighbour, whatever its colour.
        return CableBlock.withConnections(state.setValue(CableBlock.COLOUR, colour),
                context.getLevel(), context.getClickedPos());
    }
}
