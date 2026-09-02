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

import dev.devpanda.factorynetwork.network.packet.NetworkStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Opens the editor.
 *
 * <p>The terminal itself holds nothing: program and storage live in the
 * controller. It finds the controller over the network and works on its data
 * — so even with several terminals there is only one source of truth.
 */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider {

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.TERMINAL.get(), pos, state);
    }

    /** Looks for the controller in the neighbourhood. */
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

    /** Sends source code, connectors and worker state to the editor. */
    public void sendStateTo(ServerPlayer player) {
        Optional<ControllerBlockEntity> controller = controller();
        if (controller.isEmpty()) {
            // A terminal without a network sends emptiness, not nothing: the
            // client should get rid of its old lists.
            PacketDistributor.sendToPlayer(player,
                    new NetworkStatePacket(List.of(), List.of(), List.of(), List.of(),
                            List.of(), List.of()));
            return;
        }
        controller.get().sendNetworkStateTo(player);
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
