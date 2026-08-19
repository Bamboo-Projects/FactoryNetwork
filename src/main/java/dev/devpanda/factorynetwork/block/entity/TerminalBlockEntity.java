package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Öffnet den Editor.
 *
 * <p>Das Terminal hält selbst nichts: Programm und Speicher liegen im
 * Controller. Es sucht sich den Controller über das Netzwerk und arbeitet auf
 * dessen Daten — so bleibt auch bei mehreren Terminals nur eine Wahrheit.
 */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider {

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.TERMINAL.get(), pos, state);
    }

    /** Sucht den Controller in der Nachbarschaft. */
    public Optional<ControllerBlockEntity> controller() {
        if (level == null) {
            return Optional.empty();
        }
        for (Direction direction : Direction.values()) {
            BlockPos candidate = worldPosition.relative(direction);
            if (level.isLoaded(candidate)
                    && level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.factorynetwork.terminal");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TerminalMenu(id, inventory, worldPosition);
    }
}
