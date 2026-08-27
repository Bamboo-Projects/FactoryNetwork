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
 * Der Anschluss, den man auch ohne Kabel setzen kann.
 *
 * <p><b>Warum das geht.</b> Wer eine Anlage baut, stellt erst die Maschinen
 * hin und zieht die Leitung danach. Müsste das Kabel zuerst da sein, wäre die
 * Reihenfolge vorgeschrieben — und wer sie umdreht, setzt Anschlüsse ein
 * zweites Mal.
 *
 * <p>Was dabei entsteht, ist ein Kabelblock ohne Strang: ein Halter, in dem
 * der Anschluss sitzt und der an keinem Netz hängt. Ein Kabel darauf macht
 * daraus eine Leitung, ohne dass der Anschluss neu gesetzt werden müsste.
 *
 * <p>So macht es AE2. Dort ist der Block ein Kabelbus, und das Kabel ist nur
 * eines der Teile darin — deshalb darf ein Bus auch ohne bestehen.
 */
public class ConnectorItem extends Item {

    public ConnectorItem(Properties properties) {
        super(properties);
    }

    /**
     * Setzt einen Halter vor die geklickte Fläche.
     *
     * <p>Der Anschluss zeigt auf den Block, den man angeklickt hat — das ist
     * die Geste, die man ohnehin macht: an das Gerät klicken, das man
     * anschließen will.
     *
     * <p>Auf ein Kabel oder einen bestehenden Halter zu klicken, führt hier
     * nicht durch: Das erledigt {@link CableBlock} selbst, und dort sitzt
     * auch die Vorschau.
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
        // Ein Halter: derselbe Block wie ein Kabel, nur ohne Strang darin.
        level.setBlock(target, FnBlocks.CABLE.get().defaultBlockState()
                .setValue(CableBlock.CABLE, false), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(target) instanceof CableBusBlockEntity bus)) {
            // Sollte nicht vorkommen; wenn doch, keinen leeren Block
            // stehenlassen — der wäre unsichtbar und nicht anzuklicken.
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
