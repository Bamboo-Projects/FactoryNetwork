package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /fnhalls}: every hall of the world, and which have no block any more.
 *
 * <p>An orphaned hall is kept, not deleted: a block blown up by a creeper
 * should not take a whole factory with it. This list is how an operator finds
 * them; the block's item can be given again with the hall's number.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class HallCommands {

    private HallCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fnhalls")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> list(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        Map<Integer, Halls.Entry> all = new TreeMap<>(Halls.of(source.getServer()).all());
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.factorynetwork.halls.none"), false);
            return 0;
        }
        all.forEach((id, entry) -> {
            GlobalPos door = entry.door().orElse(null);
            boolean stands = door != null && standsAt(source, door, id);
            int size = entry.size().size();
            Component line = stands
                    ? Component.translatable("command.factorynetwork.halls.entry", id, size,
                            door.dimension().identifier().toString(), door.pos().toShortString())
                    : Component.translatable("command.factorynetwork.halls.orphan", id, size);
            source.sendSuccess(() -> line, false);
        });
        return all.size();
    }

    private static boolean standsAt(CommandSourceStack source, GlobalPos door, int id) {
        ServerLevel level = source.getServer().getLevel(door.dimension());
        return level != null && level.getBlockEntity(door.pos()) instanceof HallBlockEntity block
                && block.hall() == id;
    }
}
