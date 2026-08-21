package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Setzt ein Kabel in seiner Farbe.
 *
 * <p>Alle siebzehn Gegenstände setzen denselben Block — nur mit
 * unterschiedlichem Zustand. Der Unterschied ist keine Kleinigkeit: An der
 * Farbe hängt, welche Kabel sich verbinden.
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
     * Beim Setzen bekommt der Block die Farbe des Gegenstands.
     *
     * <p>Ohne das stünde überall das neutrale Kabel: Der Zustand kommt vom
     * Block, nicht vom Gegenstand, und beide kennen einander sonst nicht.
     */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(CableBlock.COLOUR, colour);
    }
}
