package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.platform.Chat;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The blueprint of a factory hall.
 *
 * <p>Empty, a right-click on a hall block reads its hall. Filled, a
 * right-click on a <b>new</b> hall block (never entered) builds the same hall
 * there: the blocks come from the player's inventory first, then from the
 * store of the network the block touches. If anything is missing, it says
 * what and how much, and builds nothing — half a factory is worse than none.
 */
public class BlueprintItem extends Item {

    public BlueprintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof HallBlockEntity door)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Blueprint blueprint = stack.get(FnComponents.BLUEPRINT.get());
        if (blueprint == null || player.isShiftKeyDown() && door.hall() != HallBlockEntity.NONE) {
            record(level, player, door, stack);
        } else {
            build(level, player, door, blueprint);
        }
        return InteractionResult.CONSUME;
    }

    private static void record(ServerLevel level, Player player, HallBlockEntity door, ItemStack stack) {
        if (door.hall() == HallBlockEntity.NONE) {
            Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.empty_hall"), true);
            return;
        }
        Halls.Entry entry = Halls.of(level.getServer()).get(door.hall());
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        if (entry == null || hall == null) {
            return;
        }
        Blueprint blueprint = Blueprint.capture(hall, door.hall(), entry.size());
        if (blueprint == null) {
            Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.too_large",
                    Blueprint.MAX_BLOCKS), false);
            return;
        }
        stack.set(FnComponents.BLUEPRINT.get(), blueprint);
        Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.recorded",
                blueprint.blocks().size()), true);
    }

    private static void build(ServerLevel level, Player player, HallBlockEntity door, Blueprint blueprint) {
        if (level.dimension() == Halls.DIMENSION) {
            Chat.tell(player, Component.translatable("message.factorynetwork.hall.nested"), true);
            return;
        }
        if (door.hall() != HallBlockEntity.NONE) {
            Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.not_new"), false);
            return;
        }
        if (door.tier().size() < blueprint.size().size()) {
            Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.too_small",
                    blueprint.size().size()), false);
            return;
        }
        Map<Item, Integer> needed = blueprint.materials();
        NetworkStorage store = store(level, door.getBlockPos());
        if (!player.isCreative()) {
            List<Component> missing = new ArrayList<>();
            needed.forEach((item, count) -> {
                long have = inInventory(player, item) + (store == null ? 0 : store.count(item));
                if (have < count) {
                    missing.add(Component.literal((count - have) + "× ").append(item.getDefaultInstance().getHoverName()));
                }
            });
            if (!missing.isEmpty()) {
                MutableComponent line = Component.translatable("message.factorynetwork.blueprint.missing");
                for (int i = 0; i < missing.size() && i < 8; i++) {
                    line.append(i == 0 ? " " : ", ").append(missing.get(i));
                }
                if (missing.size() > 8) {
                    line.append(" …");
                }
                Chat.tell(player, line.withStyle(ChatFormatting.RED), false);
                return;
            }
            needed.forEach((item, count) -> {
                int left = takeFromInventory(player, item, count);
                if (left > 0 && store != null) {
                    store.extract(item, left);
                }
            });
        }

        Halls halls = Halls.of(level.getServer());
        int id = halls.create(door.tier());
        door.setHall(id);
        halls.setDoor(id, GlobalPos.of(level.dimension(), door.getBlockPos()));
        HallLoading.hold(door, true);
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        Halls.buildRoom(hall, id, door.tier(), FnBlocks.HALL_WALL.get().defaultBlockState());
        blueprint.build(hall, id);
        Chat.tell(player, Component.translatable("message.factorynetwork.blueprint.built",
                blueprint.blocks().size(), id), false);
    }

    /** The store of a network the hall block touches, or {@code null}. */
    private static @Nullable NetworkStorage store(ServerLevel level, BlockPos door) {
        for (Direction direction : Direction.values()) {
            var owner = ControllerRegistry.owning(level, door.relative(direction));
            if (owner.isPresent()) {
                return owner.map(ControllerBlockEntity::storage).orElse(null);
            }
        }
        return null;
    }

    private static int inInventory(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int takeFromInventory(Player player, Item item, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (left > 0 && stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> adder,
                                net.minecraft.world.item.TooltipFlag flag) {
        List<Component> lines = dev.devpanda.factorynetwork.platform.Tooltips.lines(adder);
        Blueprint blueprint = stack.get(FnComponents.BLUEPRINT.get());
        if (blueprint == null) {
            lines.add(Component.translatable("item.factorynetwork.blueprint.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        int size = blueprint.size().size();
        lines.add(Component.translatable("item.factorynetwork.blueprint.filled", size, size, size,
                blueprint.blocks().size()).withStyle(ChatFormatting.GRAY));
        blueprint.materials().entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(6)
                .forEach(entry -> lines.add(Component.literal("  " + entry.getValue() + "× ")
                        .append(entry.getKey().getDefaultInstance().getHoverName()).withStyle(ChatFormatting.DARK_GRAY)));
        if (blueprint.skipped() > 0) {
            lines.add(Component.translatable("item.factorynetwork.blueprint.skipped", blueprint.skipped())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("item.factorynetwork.blueprint.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
