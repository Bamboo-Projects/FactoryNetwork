package dev.devpanda.factorynetwork.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.network.SlotLine;
import dev.devpanda.factorynetwork.network.SlotMap;
import dev.devpanda.factorynetwork.network.packet.SlotMapPacket;
import dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "#" button on block GUIs: shows each slot's number for {@code slots(n)}
 * and, on hover, from which sides a connector reaches it.
 *
 * <p>A menu does not know its block, so the block is the one the player
 * right-clicked just before the GUI opened. GUIs opened any other way (a
 * key, an item) get no button; that is better than numbers for the wrong
 * block.
 *
 * <p>Like Super Factory Manager's inspector, Ctrl+I toggles the numbers too,
 * and Ctrl+click on a numbered slot copies a {@code move} line for it.
 *
 * <p>All drawing for this lives here, so the port to the older GUI code is
 * this one file.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class SlotOverlay {

    /** How many ticks after the click a GUI still counts as opened by it. */
    private static final long CLICK_WINDOW = 20;
    private static final int BUTTON_SIZE = 14;
    /** How long the note about a copied line stays above the GUI. */
    private static final long NOTE_MILLIS = 2500;

    /**
     * Ctrl+I, as in Super Factory Manager. GUI context only: in the world the
     * key stays free for whatever else wants it.
     */
    static final KeyMapping TOGGLE_KEY = new KeyMapping("key.factorynetwork.toggle_slot_numbers",
            KeyConflictContext.GUI, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I,
            RemoteKey.CATEGORY);

    private static BlockPos clickedPos;
    private static long clickedAt = Long.MIN_VALUE;

    private static int boundContainer = -1;
    private static BlockPos boundPos;
    private static final Map<Integer, SlotMap.Entry> entries = new HashMap<>();
    /** The label of a connector at the bound block; empty if none is named. */
    private static String connector = "";
    private static Button button;

    private static final List<Component> note = new ArrayList<>();
    private static long noteUntil;
    /** The copying click's release, which the GUI must not see either. */
    private static boolean swallowRelease;

    private SlotOverlay() {
    }

    @SubscribeEvent
    public static void rememberClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.level == null) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            remember(block.getBlockPos());
        }
    }

    /** The block whose GUI opens next; also how a scripted client clicks. */
    public static void remember(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            clickedPos = pos;
            clickedAt = minecraft.level.getGameTime();
        }
    }

    @SubscribeEvent
    public static void addButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || ownScreen(screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int containerId = screen.getMenu().containerId;
        if (containerId != boundContainer) {
            entries.clear();
            connector = "";
            note.clear();
            boundContainer = -1;
            if (clickedPos == null || minecraft.level == null
                    || minecraft.level.getGameTime() - clickedAt > CLICK_WINDOW) {
                return;
            }
            boundContainer = containerId;
            boundPos = clickedPos;
            ClientPacketDistributor.sendToServer(new SlotMapRequestPacket(containerId, clickedPos));
            clickedPos = null;
        }
        button = Button.builder(Component.literal("#"), pressed -> FnClientConfig.setShowSlotNumbers(
                        !FnClientConfig.showSlotNumbers()))
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.translatable("screen.factorynetwork.slots.toggle")))
                .build();
        place(screen);
        event.addListener(button);
    }

    /** The answer arrived: the button appears if there is anything to show. */
    public static void receive(SlotMapPacket packet) {
        if (packet.containerId() != boundContainer) {
            return;
        }
        entries.clear();
        for (SlotMap.Entry entry : packet.entries()) {
            entries.put(entry.menuSlot(), entry);
        }
        connector = packet.connector();
        if (button != null) {
            button.visible = !entries.isEmpty();
        }
    }

    /** Ctrl+I flips the numbers; cancelled so the GUI does not see the I. */
    @SubscribeEvent
    public static void toggleByKey(ScreenEvent.KeyPressed.Pre event) {
        if (bound(event.getScreen()) != null
                && TOGGLE_KEY.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) {
            FnClientConfig.setShowSlotNumbers(!FnClientConfig.showSlotNumbers());
            event.setCanceled(true);
        }
    }

    /**
     * Ctrl+click on a numbered slot copies a line instead of moving the item.
     *
     * <p>Only while the numbers show: with them hidden, a Ctrl+click is the
     * GUI's business. A slot without a number lets the click through, since
     * no line could name it.
     */
    @SubscribeEvent
    public static void copyLine(ScreenEvent.MouseButtonPressed.Pre event) {
        AbstractContainerScreen<?> screen = bound(event.getScreen());
        if (screen == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !event.getMouseButtonEvent().hasControlDown() || !FnClientConfig.showSlotNumbers()) {
            return;
        }
        Slot hovered = screen.getHoveredSlot();
        SlotMap.Entry entry = hovered == null ? null : entries.get(screen.getMenu().slots.indexOf(hovered));
        if (entry == null || entry.number() < 0) {
            return;
        }
        ItemStack stack = hovered.getItem();
        String itemId = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String device = SlotLine.deviceName(deviceLabel());
        String line = SlotLine.of(device, itemId, entry.number());
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(line);

        // The action bar and the chat sit behind an open GUI, so the note is
        // drawn above the GUI as well; the chat keeps the hint for later.
        note.clear();
        Component copied = Component.translatable("screen.factorynetwork.slots.copied", line);
        note.add(copied);
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(copied);
        }
        if (connector.isBlank()) {
            Component hint = Component.translatable("screen.factorynetwork.slots.unnamed", device);
            note.add(hint);
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(hint);
            }
        }
        noteUntil = System.currentTimeMillis() + NOTE_MILLIS;
        swallowRelease = true;
        event.setCanceled(true);
    }

    /**
     * A release without its press could still end a drag or pick up items
     * by double click in some GUIs; the copying click leaves no trace.
     */
    @SubscribeEvent
    public static void dropRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swallowRelease = false;
            event.setCanceled(true);
        }
    }

    /**
     * The connector's label, or the block's name as a stand-in.
     *
     * <p>The stand-in is what the label gun would propose, without its
     * number: it reads like the machine, and the hint says to replace it.
     */
    private static String deviceLabel() {
        if (!connector.isBlank()) {
            return connector;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || boundPos == null) {
            return "device";
        }
        return ConnectorNaming.baseName(minecraft.level.getBlockState(boundPos));
    }

    /** The screen, if the numbers belong to it and there are any. */
    private static AbstractContainerScreen<?> bound(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId == boundContainer && !entries.isEmpty()
                ? container : null;
    }

    /**
     * Follows the GUI each frame: the recipe book moves it without telling
     * anyone.
     */
    @SubscribeEvent
    public static void follow(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen && button != null
                && screen.children().contains(button)) {
            place(screen);
        }
    }

    @SubscribeEvent
    /*
     * Drawn inside the screen's own frame, after the slots and before the
     * tooltip: from Render.Post the numbers lay on top of the tooltip.
     */
    public static void draw(ScreenEvent.Render.Foreground event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu().containerId != boundContainer || entries.isEmpty()
                || !FnClientConfig.showSlotNumbers()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        List<Slot> slots = screen.getMenu().slots;
        graphics.nextStratum();
        for (SlotMap.Entry entry : entries.values()) {
            if (entry.menuSlot() < 0 || entry.menuSlot() >= slots.size()) {
                continue;
            }
            Slot slot = slots.get(entry.menuSlot());
            if (!slot.isActive()) {
                continue;
            }
            int x = screen.getLeftPos() + slot.x;
            int y = screen.getTopPos() + slot.y;
            graphics.fill(x, y, x + 16, y + 16, 0xB0000000);
            String text = entry.number() >= 0 ? Integer.toString(entry.number()) : "?";
            int colour = entry.estimated() ? 0xFFFFC060 : 0xFFFFFF60;
            graphics.text(font, text, x + (17 - font.width(text)) / 2, y + 4, colour, true);
        }
        if (!note.isEmpty() && System.currentTimeMillis() < noteUntil) {
            // Wrapped to the GUI's width: a long line ran off the screen and
            // under the item list beside the GUI.
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component line : note) {
                wrapped.addAll(font.split(line, screen.getImageWidth()));
            }
            int left = screen.getLeftPos();
            int y = Math.max(2, screen.getTopPos() - BUTTON_SIZE - 2 - 10 * wrapped.size());
            graphics.fill(left - 2, y - 2, left + screen.getImageWidth() + 2, y + 10 * wrapped.size(), 0xC0000000);
            for (net.minecraft.util.FormattedCharSequence line : wrapped) {
                graphics.text(font, line, left, y, 0xFFFFFF60, true);
                y += 10;
            }
        }
    }

    /**
     * The slot's tooltip, set before the screen sets the item's own.
     *
     * <p>Here and not in {@code Render.Post}: by then the frame's tooltip is
     * already drawn, and one set afterwards is lost. The screen does not
     * replace a tooltip that is already set.
     */
    @SubscribeEvent
    public static void slotTooltip(ScreenEvent.Render.Foreground event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu().containerId != boundContainer || entries.isEmpty()
                || !FnClientConfig.showSlotNumbers()) {
            return;
        }
        Slot hovered = screen.getHoveredSlot();
        SlotMap.Entry entry = hovered == null ? null : entries.get(screen.getMenu().slots.indexOf(hovered));
        if (entry != null) {
            event.getGuiGraphics().setTooltipForNextFrame(Minecraft.getInstance().font,
                    tooltip(entry).stream().map(Component::getVisualOrderText).toList(),
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    event.getMouseX(), event.getMouseY(), true);
        }
    }

    private static List<Component> tooltip(SlotMap.Entry entry) {
        List<Component> lines = new ArrayList<>();
        if (entry.number() >= 0) {
            lines.add(Component.literal("slots(" + entry.number() + ")").withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("screen.factorynetwork.slots.unknown")
                    .withStyle(ChatFormatting.YELLOW));
        }
        MutableComponent sides = null;
        for (Direction direction : Direction.values()) {
            if ((entry.sides() & (1 << direction.get3DDataValue())) == 0) {
                continue;
            }
            Component name = Component.translatable("side.factorynetwork." + direction.getSerializedName());
            sides = sides == null ? name.copy() : sides.append(", ").append(name);
        }
        lines.add(sides == null
                ? Component.translatable("screen.factorynetwork.slots.no_side").withStyle(ChatFormatting.GRAY)
                : Component.translatable("screen.factorynetwork.slots.sides", sides)
                        .withStyle(ChatFormatting.GRAY));
        if (entry.number() >= 0) {
            lines.add(Component.translatable("screen.factorynetwork.slots.any_side")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (entry.estimated()) {
            lines.add(Component.translatable("screen.factorynetwork.slots.estimated")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (entry.number() >= 0) {
            lines.add(Component.translatable("screen.factorynetwork.slots.copy_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Above the GUI's top right corner, where GUIs rarely put anything. */
    private static void place(AbstractContainerScreen<?> screen) {
        button.setX(screen.getLeftPos() + screen.getImageWidth() - BUTTON_SIZE);
        button.setY(screen.getTopPos() - BUTTON_SIZE - 1);
        button.visible = !entries.isEmpty();
    }

    /** This mod's own GUIs have their own way of showing what is where. */
    private static boolean ownScreen(AbstractContainerScreen<?> screen) {
        return screen.getClass().getName().startsWith("dev.devpanda.factorynetwork.");
    }
}
