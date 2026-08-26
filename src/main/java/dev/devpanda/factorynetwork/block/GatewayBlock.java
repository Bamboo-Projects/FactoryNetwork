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
import org.jetbrains.annotations.Nullable;

/**
 * Ein Kabelstück, das seiner Umgebung einen Anlagennamen gibt.
 *
 * <p><b>Eine Anlage entsteht bisher allein über die Beschriftung:</b>
 * {@code werk_1/eingang}, {@code werk_1/ausgang}. Das funktioniert und bleibt
 * — aber es verlangt, dass man den Anlagennamen an jedem einzelnen Gerät
 * wiederholt, und wer ihn ändern will, geht sie alle noch einmal ab.
 *
 * <p>Dieser Block ist die andere Antwort auf dieselbe Frage: <b>Eine Anlage
 * ist etwas Zusammenhängendes.</b> Was hinter dem Gateway am Kabel hängt,
 * gehört zu seiner Anlage — ohne dass an einem einzigen Gerät der Name
 * dasteht. Ein Umbenennen ist dann ein Block und nicht zwölf.
 *
 * <p><b>Er vermehrt keine Kanäle.</b> Er reicht den Strang durch, auf dem er
 * sitzt, und ein dichtes Kabel trägt vierundsechzig — mehr wird es dadurch
 * nicht. Das ist dieselbe Regel, an der auch der Controller-Anbau hängt: Ein
 * Kanalvermehrer zum Hinstellen machte die Kanalgrenze bedeutungslos.
 *
 * <p><b>Die Beschriftung gewinnt.</b> Trägt ein Gerät selbst einen
 * Schrägstrich, gilt der. Sonst hätte ein hingestellter Block still
 * geändert, was ein Programm über ein Gerät sagt — und das ist die Sorte
 * Überraschung, die man am längsten sucht.
 */
public class GatewayBlock extends Block implements EntityBlock {

    public GatewayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GatewayBlockEntity(pos, state);
    }

    /**
     * Rechtsklick benennt die Anlage — dasselbe Fenster wie am Connector.
     *
     * <p>Ein eigenes wäre eine zweite Maske für dieselbe Handlung. Was
     * darin steht, ist hier nur das obere Feld: Ein Gateway hat eine
     * Anlage und keine Rolle.
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
                buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
}
