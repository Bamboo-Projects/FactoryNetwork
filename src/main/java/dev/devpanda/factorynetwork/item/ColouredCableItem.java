package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.entity.CableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
     * Ein Kabel auf ein Kabel setzen legt einen Strang dazu, statt daneben zu
     * bauen.
     *
     * <p>Das ist der Kern der Bündel: Vier Netze passen durch dieselbe Wand.
     * Wer stattdessen wirklich danebenbauen will, schleicht dabei — dieselbe
     * Ausnahme, die Minecraft überall für „nicht das Naheliegende" benutzt.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            return super.useOn(context);
        }
        if (!(level.getBlockState(pos).getBlock() instanceof CableBlock)) {
            return super.useOn(context);
        }
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable)) {
            return super.useOn(context);
        }
        if (level.isClientSide) {
            return cable.has(colour) || cable.isFull()
                    ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (cable.has(colour)) {
            say(player, "message.factorynetwork.cable.already_there");
            return InteractionResult.CONSUME;
        }
        if (cable.isFull()) {
            say(player, "message.factorynetwork.cable.full", CableBlockEntity.MAX_STRANDS);
            return InteractionResult.CONSUME;
        }
        cable.addStrand(colour);
        // Die Verbindungen neu bestimmen: Der neue Strang kann Nachbarn
        // erreichen, die vorher keine passende Farbe fanden.
        refreshNeighbours(level, pos);
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /** Stößt die Nachbarn an, damit ihre Arme stimmen. */
    private static void refreshNeighbours(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.setBlock(pos, state, 3);
        level.updateNeighborsAt(pos, state.getBlock());
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            level.neighborChanged(neighbour, state.getBlock(), pos);
        }
    }

    private static void say(Player player, String key, Object... arguments) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, arguments), true);
        }
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(CableBlock.COLOUR, colour);
    }

    @Override
    public String getDescriptionId() {
        // Jede Farbe hat ihren eigenen Namen im Sprachdokument.
        return colour == CableColour.NONE
                ? "item.factorynetwork.cable"
                : "item.factorynetwork." + colour.getSerializedName() + "_cable";
    }
}
