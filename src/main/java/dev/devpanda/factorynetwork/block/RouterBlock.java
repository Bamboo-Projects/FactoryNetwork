package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Kreuzung für dicke Kabel.
 *
 * <p>Beim dünnen Kabel trennt die Farbe, und vier dünne Stränge passen
 * nebeneinander in einen Block. Beim dicken passt das nicht — zehn Blockpixel
 * füllen den Block fast aus. Statt zu bündeln steht hier ein Block, an dem
 * jede Seite einer Bahn zugewiesen wird: <b>gleiche Bahn heißt verbunden,
 * verschiedene Bahnen kreuzen sich berührungslos.</b>
 *
 * <p>Anklicken schaltet die angeklickte Seite eine Bahn weiter. Eine Seite
 * auf „aus" ist abgeklemmt — so trennt man ein Netz, ohne das Kabel
 * abzureißen.
 *
 * <p><b>Der Router ist farbneutral.</b> Wer ein rotes und ein grünes Kabel auf
 * dieselbe Bahn legt, hat sie verbunden — das ist Absicht und der Unterschied
 * zu zwei Kabeln, die sich bloß einen Block teilen: Hier hat es jemand
 * eingestellt.
 */
public class RouterBlock extends Block implements EntityBlock {

    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    public RouterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    /**
     * Ein Klick schaltet die angeklickte Seite weiter.
     *
     * <p>Danach wird das Netz sofort neu aufgebaut, statt auf den nächsten
     * Turnus zu warten. Fünf Sekunden zwischen Klick und Wirkung sind zu
     * lang, um noch als Ursache erkannt zu werden — der Spieler klickt in der
     * Zeit dreimal weiter und weiß am Ende nicht, was gerade gilt.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
            return InteractionResult.PASS;
        }
        Direction side = hit.getDirection();
        int lane = router.cycle(side);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.3F, lane == RouterBlockEntity.OFF ? 0.5F : 0.6F + lane * 0.15F);
        player.displayClientMessage(lane == RouterBlockEntity.OFF
                ? net.minecraft.network.chat.Component.translatable(
                        "message.factorynetwork.router.off", sideName(side))
                : net.minecraft.network.chat.Component.translatable(
                        "message.factorynetwork.router.lane", sideName(side), lane), true);
        ControllerRegistry.refreshAround(level, pos);
        return InteractionResult.CONSUME;
    }

    private static net.minecraft.network.chat.Component sideName(Direction side) {
        return net.minecraft.network.chat.Component.translatable(
                "side.factorynetwork." + side.getSerializedName());
    }

    /**
     * Ein abgerissener Router nimmt das Netz mit, das über ihn lief.
     *
     * <p>Ohne das bliebe der Graph bis zum nächsten Turnus bei der alten
     * Auskunft, und der Netzanalysator zeichnete Strecken durch einen Block,
     * den es nicht mehr gibt.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) {
            ControllerRegistry.refreshAround(level, pos);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            ControllerRegistry.refreshAround(level, pos);
        }
    }
}
