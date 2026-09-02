package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import dev.devpanda.factorynetwork.client.LabelGunScreenOpener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gives connectors their names.
 *
 * <p>The interaction model follows SuperFactoryManager, the data model is
 * reversed: <b>there the gun carries the names, here the connector does.</b>
 * That is why there is no transfer between gun and controller — the world is
 * the truth, and what sits in the gun is a clipboard, not a database.
 *
 * <p>Controls:
 * <ul>
 *   <li><b>Right-click</b> on a connector assigns the prepared name. If none
 *       is prepared, one is suggested from the machine behind it and
 *       numbered.
 *   <li><b>Right-click</b> on a connector that is already called that takes
 *       the name away again.
 *   <li><b>Sneak + right-click</b> picks up the connector's name into the
 *       clipboard.
 * </ul>
 */
public class LabelGunItem extends Item {

    private static final String KEY_ACTIVE = "ActiveLabel";
    private static final String KEY_CONTROLLER = "Controller";
    private static final String KEY_RECENT = "Recent";
    /** The clipboard remembers no more than this. */
    private static final int MAX_RECENT = 16;

    public LabelGunItem(Properties properties) {
        super(properties);
    }

    // ---- Clipboard --------------------------------------------------------

    /** The name that will be assigned on the next click. */
    public static String activeLabel(ItemStack gun) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(KEY_ACTIVE);
    }

    public static void setActiveLabel(ItemStack gun, String label) {
        String normalized = ConnectorNaming.normalize(label);
        CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> {
            tag.putString(KEY_ACTIVE, normalized);
            if (!normalized.isBlank()) {
                List<String> recent = readRecent(tag);
                recent.remove(normalized);
                recent.add(0, normalized);
                while (recent.size() > MAX_RECENT) {
                    recent.remove(recent.size() - 1);
                }
                writeRecent(tag, recent);
            }
        });
    }

    /** The most recently used names — for cycling through. */
    public static List<String> recentLabels(ItemStack gun) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        return data == null ? List.of() : List.copyOf(readRecent(data.copyTag()));
    }

    /** Steps on by {@code direction} and returns the new name. */
    public static String cycle(ItemStack gun, int direction) {
        List<String> recent = recentLabels(gun);
        if (recent.isEmpty()) {
            return "";
        }
        String active = activeLabel(gun);
        int index = recent.indexOf(active);
        int next = Math.floorMod(index + direction, recent.size());
        String label = recent.get(next);
        CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putString(KEY_ACTIVE, label));
        return label;
    }

    private static List<String> readRecent(CompoundTag tag) {
        List<String> recent = new ArrayList<>();
        net.minecraft.nbt.ListTag list = tag.getList(KEY_RECENT, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            recent.add(list.getString(i));
        }
        return recent;
    }

    private static void writeRecent(CompoundTag tag, List<String> recent) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        recent.forEach(entry -> list.add(net.minecraft.nbt.StringTag.valueOf(entry)));
        tag.put(KEY_RECENT, list);
    }

    // ---- Using ------------------------------------------------------------

    /**
     * Opens the name input.
     *
     * <p>Only triggers when no block at all is targeted — so inside a
     * building almost never. That is why clicking a block that is neither
     * connector nor controller opens the same window too; see
     * {@link #useOn}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (level.isClientSide) {
            LabelGunScreenOpener.open(gun);
        }
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockEntity entity = level.getBlockEntity(pos);
        ItemStack gun = context.getItemInHand();

        // Right-click on the controller links the gun to the network.
        if (entity instanceof ControllerBlockEntity controller) {
            controller.rebuildNetwork();
            link(gun, pos);
            if (player != null) {
                say(player, "message.factorynetwork.label_gun.linked",
                        controller.graph().connectorCount(),
                        controller.graph().unnamedConnectors().size());
            }
            return InteractionResult.CONSUME;
        }

        // A display gets its name from the same gun: it is the same action —
        // telling a block in the network what it is called.
        if (entity instanceof DisplayBlockEntity display) {
            String wanted = activeLabel(gun);
            if (wanted.isBlank()) {
                return InteractionResult.PASS;
            }
            // Onto all panels of the wall: whoever labels a wall has
            // labelled the wall and not the one panel they hit. Otherwise
            // you would have to know which of them is the writing one — and
            // you cannot tell that by looking at it.
            for (net.minecraft.core.BlockPos member : display.wall().members()) {
                if (level.getBlockEntity(member) instanceof DisplayBlockEntity panel) {
                    panel.setDisplayName(wanted);
                }
            }
            if (player != null) {
                say(player, "message.factorynetwork.display.named", wanted);
            }
            return InteractionResult.CONSUME;
        }

        // The hit face first, then the block: a cable block has up to six
        // connectors sitting on it, and the click says which one is meant.
        // On the dedicated connector block you often hit a face other than
        // the one it faces — there "exactly one present" still applies.
        var connector = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                level, pos, context.getClickedFace());
        if (connector == null) {
            connector = dev.devpanda.factorynetwork.block.entity.Connectors.at(level, pos);
        }
        if (connector == null) {
            // Clicked on a machine next to a connector: that is the most
            // common mistake, and silently doing nothing does not help.
            if (player != null && hasAdjacentConnector(level, pos)) {
                say(player, "message.factorynetwork.label_gun.aim_at_connector");
                return InteractionResult.CONSUME;
            }
            // Otherwise the click is the prompt to enter a name. Allowing it
            // only into empty space would mean: inside a building, never.
            return InteractionResult.PASS;
        }

        FactoryGraph graph = linkedGraph(gun, level);

        // Sneaking picks up the name instead of assigning it.
        if (player != null && player.isShiftKeyDown()) {
            String existing = connector.label();
            if (existing.isBlank()) {
                say(player, "message.factorynetwork.label_gun.nothing_to_pick");
            } else {
                setActiveLabel(gun, existing);
                say(player, "message.factorynetwork.label_gun.picked", existing);
            }
            return InteractionResult.CONSUME;
        }

        // Without a link the gun lacks the network knowledge: it can neither
        // number nor warn about duplicate names. This is the moment to say
        // so — not only once two equal names stand in the terminal.
        if (!isLinked(gun) && player != null) {
            say(player, "message.factorynetwork.label_gun.not_linked");
            return InteractionResult.CONSUME;
        }

        String wanted = activeLabel(gun);
        if (wanted.isBlank()) {
            wanted = ConnectorNaming.suggestFor(
                    level.getBlockState(pos.relative(connector.facing())), graph);
        }
        return apply(connector, gun, graph, wanted, player);
    }

    private InteractionResult apply(
            dev.devpanda.factorynetwork.block.entity.ConnectorPart connector, ItemStack gun,
            FactoryGraph graph, String wanted, Player player) {
        // The same name again: the click takes it away again.
        if (wanted.equals(connector.label())) {
            connector.setLabel("");
            if (player != null) {
                say(player, "message.factorynetwork.label_gun.cleared", wanted);
            }
            return InteractionResult.CONSUME;
        }

        ConnectorNaming.Warning warning = ConnectorNaming.check(wanted, graph);
        if (player != null) {
            switch (warning.kind()) {
                case TAKEN -> {
                    // Two connectors with the same name make both unusable.
                    // That is why nothing is assigned here; instead the next
                    // free name is suggested.
                    say(player, "message.factorynetwork.label_gun.taken",
                            wanted, warning.suggestion());
                    setActiveLabel(gun, warning.suggestion());
                    return InteractionResult.CONSUME;
                }
                case NOT_AN_IDENTIFIER -> {
                    say(player, "message.factorynetwork.label_gun.invalid", wanted);
                    return InteractionResult.CONSUME;
                }
                case EMPTY -> {
                    say(player, "message.factorynetwork.label_gun.empty");
                    return InteractionResult.CONSUME;
                }
                case KEYWORD ->
                    // Allowed, but the name needs backticks in the code.
                    say(player, "message.factorynetwork.label_gun.keyword", wanted);
                case NONE -> { }
            }
        }

        connector.setLabel(wanted);
        setActiveLabel(gun, wanted);
        if (player != null) {
            say(player, "message.factorynetwork.label_gun.applied", wanted);
        }
        return InteractionResult.CONSUME;
    }

    // ---- Surroundings -----------------------------------------------------

    /**
     * The graph of the network the gun is linked to.
     *
     * <p>Linking happens through a right-click on the controller. That
     * replaces the transfer of names between gun and manager that
     * SuperFactoryManager needs: there the data travels back and forth, here
     * the gun simply points at the network and reads what stands there
     * anyway.
     *
     * <p>The alternative would be to search for the controller in the
     * surroundings on every click. With a radius large enough for a grown
     * network, that is over fifty thousand block positions — per click.
     */
    private static FactoryGraph linkedGraph(ItemStack gun, Level level) {
        return linkedController(gun, level)
                .map(ControllerBlockEntity::graph)
                .orElse(FactoryGraph.empty());
    }

    public static Optional<ControllerBlockEntity> linkedController(ItemStack gun, Level level) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY_CONTROLLER)) {
            return Optional.empty();
        }
        BlockPos position = BlockPos.of(tag.getLong(KEY_CONTROLLER));
        if (!level.isLoaded(position)) {
            return Optional.empty();
        }
        return level.getBlockEntity(position) instanceof ControllerBlockEntity controller
                ? Optional.of(controller) : Optional.empty();
    }

    public static boolean isLinked(ItemStack gun) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(KEY_CONTROLLER);
    }

    private static void link(ItemStack gun, BlockPos controller) {
        CustomData.update(DataComponents.CUSTOM_DATA, gun,
                tag -> tag.putLong(KEY_CONTROLLER, controller.asLong()));
    }

    private static boolean hasAdjacentConnector(Level level, BlockPos pos) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (dev.devpanda.factorynetwork.block.entity.Connectors
                    .any(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static void say(Player player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable(key, arguments), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        String active = activeLabel(stack);
        if (active.isBlank()) {
            lines.add(Component.translatable("item.factorynetwork.label_gun.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.factorynetwork.label_gun.tooltip.active", active)
                    .withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("item.factorynetwork.label_gun.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
