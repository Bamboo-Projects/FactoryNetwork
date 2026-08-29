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
     * Jeder der siebzehn Gegenstände heißt anders.
     *
     * <p>Ein {@link BlockItem} nimmt seinen Namen sonst vom Block, und alle
     * siebzehn zeigen auf denselben — im Kreativ-Reiter stand siebzehnmal
     * „Kabel". Hier zählt der Name des Gegenstands, nicht der des Blocks.
     */
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    /**
     * Erst den Block fragen, dann daneben setzen.
     *
     * <p><b>Ein {@link BlockItem} fragt sonst gar nicht.</b> Es sucht sich
     * eine freie Stelle und setzt dorthin — und ein Halter, in dem schon ein
     * Anschluss sitzt, gilt als besetzt. Der Klick landete daneben, statt
     * das Kabel in den Halter zu legen.
     *
     * <p>Dieselbe Reihenfolge, die der Connector nimmt: Der Block bekommt
     * die erste Gelegenheit, und nur wenn er ablehnt, wird gesetzt.
     *
     * <p><b>Außer beim Schleichen.</b> Wer schleichend klickt, will
     * daneben bauen — das ist die Geste, die in Minecraft überall
     * „ignoriere den Block" bedeutet.
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
                // Den Treffer selbst bauen: UseOnContext hält ihn, gibt ihn
                // aber nicht heraus. Die drei Angaben, die zählen, hat er.
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
     * Beim Setzen bekommt der Block die Farbe des Gegenstands.
     *
     * <p>Ohne das stünde überall das neutrale Kabel: Der Zustand kommt vom
     * Block, nicht vom Gegenstand, und beide kennen einander sonst nicht.
     */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) {
            return null;
        }
        // Erst färben, dann die Verbindungen rechnen lassen. Umgekehrt wären
        // sie die eines neutralen Kabels — und ein rotes griffe nach jedem
        // Nachbarn, egal welcher Farbe.
        return CableBlock.withConnections(state.setValue(CableBlock.COLOUR, colour),
                context.getLevel(), context.getClickedPos());
    }
}
