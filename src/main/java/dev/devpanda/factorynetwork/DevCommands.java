package dev.devpanda.factorynetwork;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test commands for filling a network with stock, for UI work.
 *
 * <p>{@code /fn fill [types] [amount]} puts {@code amount} of each of
 * {@code types} random items into the network nearest to the player.
 * What does not fit into the cells is dropped, as with any other insert;
 * the reply says how much arrived.
 */
public final class DevCommands {

    private DevCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fn")
                .requires(source -> source.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("fill")
                        .executes(context -> fill(context, 200, 1000))
                        .then(Commands.argument("types", IntegerArgumentType.integer(1, 5000))
                                .executes(context -> fill(context,
                                        IntegerArgumentType.getInteger(context, "types"), 1000))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> fill(context,
                                                IntegerArgumentType.getInteger(context, "types"),
                                                IntegerArgumentType.getInteger(context, "amount")))))));
    }

    private static int fill(CommandContext<CommandSourceStack> context, int types, int amount) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        ControllerBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : ControllerRegistry.in(level)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ControllerBlockEntity controller
                    && pos.distSqr(here) < best) {
                best = pos.distSqr(here);
                nearest = controller;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("Kein Netz in dieser Dimension gefunden."));
            return 0;
        }

        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        Collections.shuffle(items, new java.util.Random(level.getRandom().nextLong()));

        long stored = 0;
        int used = 0;
        for (Item item : items.subList(0, Math.min(types, items.size()))) {
            long left = nearest.storage().insert(item, amount);
            if (left < amount) {
                used++;
            }
            stored += amount - left;
        }
        nearest.setChanged();
        long total = stored;
        int kinds = used;
        String where = nearest.getBlockPos().toShortString();
        source.sendSuccess(() -> Component.literal(total + " Items in " + kinds
                + " Sorten eingelagert, Netz bei " + where), true);
        return kinds;
    }
}
