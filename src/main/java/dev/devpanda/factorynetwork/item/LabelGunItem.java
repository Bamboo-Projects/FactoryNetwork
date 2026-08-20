package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
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
 * Vergibt Connectoren ihre Namen.
 *
 * <p>Das Bedienmodell folgt SuperFactoryManager, das Datenmodell ist
 * umgedreht: <b>Dort trägt die Gun die Namen, hier trägt sie der Connector.</b>
 * Deshalb gibt es kein Übertragen zwischen Gun und Controller — die Welt ist
 * die Wahrheit, und was in der Gun steckt, ist eine Zwischenablage, keine
 * Datenbank.
 *
 * <p>Bedienung:
 * <ul>
 *   <li><b>Rechtsklick</b> auf einen Connector vergibt den bereitgelegten
 *       Namen. Ist keiner bereitgelegt, wird einer aus der Maschine dahinter
 *       vorgeschlagen und durchnummeriert.
 *   <li><b>Rechtsklick</b> auf einen Connector, der schon so heißt, nimmt den
 *       Namen wieder weg.
 *   <li><b>Schleichen + Rechtsklick</b> übernimmt den Namen des Connectors in
 *       die Zwischenablage.
 * </ul>
 */
public class LabelGunItem extends Item {

    private static final String KEY_ACTIVE = "ActiveLabel";
    private static final String KEY_CONTROLLER = "Controller";
    private static final String KEY_RECENT = "Recent";
    /** Mehr merkt sich die Zwischenablage nicht. */
    private static final int MAX_RECENT = 16;

    public LabelGunItem(Properties properties) {
        super(properties);
    }

    // ---- Zwischenablage ---------------------------------------------------

    /** Der Name, der beim nächsten Klick vergeben wird. */
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

    /** Die zuletzt benutzten Namen — zum Durchblättern. */
    public static List<String> recentLabels(ItemStack gun) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        return data == null ? List.of() : List.copyOf(readRecent(data.copyTag()));
    }

    /** Blättert um {@code direction} weiter und liefert den neuen Namen. */
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

    // ---- Benutzen ---------------------------------------------------------

    /**
     * Öffnet die Namenseingabe.
     *
     * <p>Greift nur, wenn gar kein Block anvisiert ist — in einem Gebäude
     * also so gut wie nie. Deshalb öffnet auch der Klick auf einen Block, der
     * weder Connector noch Controller ist, dasselbe Fenster; siehe
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

        // Rechtsklick auf den Controller verknüpft die Gun mit dem Netz.
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

        if (!(entity instanceof ConnectorBlockEntity connector)) {
            // Auf eine Maschine neben einem Connector geklickt: Das ist der
            // häufigste Irrtum, und stillschweigend nichts zu tun hilft nicht.
            if (player != null && hasAdjacentConnector(level, pos)) {
                say(player, "message.factorynetwork.label_gun.aim_at_connector");
                return InteractionResult.CONSUME;
            }
            // Sonst ist der Klick die Aufforderung, einen Namen einzugeben.
            // Ihn nur ins Leere zuzulassen hieße: in einem Gebäude nie.
            return InteractionResult.PASS;
        }

        FactoryGraph graph = linkedGraph(gun, level);

        // Schleichen übernimmt den Namen statt ihn zu vergeben.
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

        // Ohne Verknüpfung fehlt der Gun das Netzwissen: Sie kann weder
        // durchnummerieren noch vor doppelten Namen warnen. Das ist der
        // Moment, es zu sagen — nicht erst, wenn im Terminal zwei gleiche
        // Namen stehen.
        if (!isLinked(gun) && player != null) {
            say(player, "message.factorynetwork.label_gun.not_linked");
            return InteractionResult.CONSUME;
        }

        String wanted = activeLabel(gun);
        if (wanted.isBlank()) {
            wanted = ConnectorNaming.suggestFor(
                    level.getBlockState(pos.relative(
                            dev.devpanda.factorynetwork.block.ConnectorBlock
                                    .machineSide(connector.getBlockState()))),
                    graph);
        }
        return apply(connector, gun, graph, wanted, player);
    }

    private InteractionResult apply(ConnectorBlockEntity connector, ItemStack gun,
                                    FactoryGraph graph, String wanted, Player player) {
        // Nochmal derselbe Name: Der Klick nimmt ihn wieder weg.
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
                    // Zwei Connectoren mit demselben Namen machen beide
                    // unbrauchbar. Deshalb wird hier nicht vergeben, sondern
                    // der nächste freie Name vorgeschlagen.
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
                    // Erlaubt, aber im Code braucht der Name Rückstriche.
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

    // ---- Umgebung ---------------------------------------------------------

    /**
     * Der Graph des Netzes, mit dem die Gun verknüpft ist.
     *
     * <p>Verknüpft wird durch einen Rechtsklick auf den Controller. Das
     * ersetzt das Übertragen von Namen zwischen Gun und Manager, das
     * SuperFactoryManager braucht: Dort wandern die Daten hin und her, hier
     * zeigt die Gun einfach auf das Netz und liest, was ohnehin dort steht.
     *
     * <p>Die Alternative wäre, den Controller bei jedem Klick in der Umgebung
     * zu suchen. Bei einem Umkreis, der für ein gewachsenes Netz reicht, sind
     * das über fünfzigtausend Blockpositionen — pro Klick.
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
            if (level.getBlockEntity(pos.relative(direction)) instanceof ConnectorBlockEntity) {
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
