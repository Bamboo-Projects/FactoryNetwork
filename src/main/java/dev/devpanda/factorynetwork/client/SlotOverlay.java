package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.network.SlotMap;
import dev.devpanda.factorynetwork.network.packet.SlotMapPacket;
import dev.devpanda.factorynetwork.network.packet.SlotMapRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "#" button on block GUIs: shows each slot's number for {@code slots(n)}
 * and, on hover, from which sides a connector reaches it.
 *
 * <p>A menu does not know its block, so the block is the one the player
 * right-clicked just before the GUI opened. GUIs opened any other way (a
 * key, an item) get no button; that is better than numbers for the wrong
 * block.
 *
 * <p>All drawing for this lives here, so the port to the older GUI code is
 * this one file.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class SlotOverlay {

    /** How many ticks after the click a GUI still counts as opened by it. */
    private static final long CLICK_WINDOW = 20;
    private static final int BUTTON_SIZE = 14;

    private static BlockPos clickedPos;
    private static long clickedAt = Long.MIN_VALUE;

    private static int boundContainer = -1;
    private static final Map<Integer, SlotMap.Entry> entries = new HashMap<>();
    private static Button button;

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
            boundContainer = -1;
            if (clickedPos == null || minecraft.level == null
                    || minecraft.level.getGameTime() - clickedAt > CLICK_WINDOW) {
                return;
            }
            boundContainer = containerId;
            PacketDistributor.sendToServer(new SlotMapRequestPacket(containerId, clickedPos));
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
        if (button != null) {
            button.visible = !entries.isEmpty();
        }
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
    public static void draw(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu().containerId != boundContainer || entries.isEmpty()
                || !FnClientConfig.showSlotNumbers()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        List<Slot> slots = screen.getMenu().slots;
        graphics.pose().pushPose();
        // Above the items, which draw at a depth of up to 232.
        graphics.pose().translate(0, 0, 300);
        for (SlotMap.Entry entry : entries.values()) {
            if (entry.menuSlot() < 0 || entry.menuSlot() >= slots.size()) {
                continue;
            }
            Slot slot = slots.get(entry.menuSlot());
            if (!slot.isActive()) {
                continue;
            }
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            graphics.fill(x, y, x + 16, y + 16, 0xB0000000);
            String text = entry.number() >= 0 ? Integer.toString(entry.number()) : "?";
            int colour = entry.estimated() ? 0xFFFFC060 : 0xFFFFFF60;
            graphics.drawString(font, text, x + (17 - font.width(text)) / 2, y + 4, colour, true);
        }
        graphics.pose().popPose();
        Slot hovered = screen.getSlotUnderMouse();
        SlotMap.Entry entry = hovered == null ? null : entries.get(slots.indexOf(hovered));
        if (entry != null) {
            graphics.renderComponentTooltip(font, tooltip(entry), event.getMouseX(), event.getMouseY());
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
        return lines;
    }

    /** Above the GUI's top right corner, where GUIs rarely put anything. */
    private static void place(AbstractContainerScreen<?> screen) {
        button.setX(screen.getGuiLeft() + screen.getXSize() - BUTTON_SIZE);
        button.setY(screen.getGuiTop() - BUTTON_SIZE - 1);
        button.visible = !entries.isEmpty();
    }

    /** This mod's own GUIs have their own way of showing what is where. */
    private static boolean ownScreen(AbstractContainerScreen<?> screen) {
        return screen.getClass().getName().startsWith("dev.devpanda.factorynetwork.");
    }
}
