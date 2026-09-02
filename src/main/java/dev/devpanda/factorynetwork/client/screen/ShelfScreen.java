package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.network.ServerBay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * A rack's screen.
 *
 * <p>Two layouts, one screen: the drive has two columns of five rows like its
 * slots, the cabinet twelve bays of four slots each — the chassis and its
 * three components.
 *
 * <p>What the cabinet additionally shows is the <b>state per bay</b>: a little
 * lamp behind each, green when it runs, yellow when a chassis is in place and
 * still nothing computes. Without that, a bay missing its disk looks just like
 * a finished one.
 */
public class ShelfScreen extends AbstractContainerScreen<ShelfMenu> {

    private static final ResourceLocation DRIVE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/drive.png");
    private static final ResourceLocation RACK = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/rack.png");
    private static final ResourceLocation MAST = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/mast.png");

    /** The image sits on a 512 sheet, like all of the mod's interfaces. */
    private static final int SHEET = 512;

    /** The same shades as on the block's front. */
    private static final int RUNNING = 0xFF78DC8C;
    private static final int INCOMPLETE = 0xFFE8AC3E;

    private static final int LAMP = 5;

    public ShelfScreen(ShelfMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.layout().width();
        this.imageHeight = menu.layout().height();
        this.inventoryLabelY = menu.layout().inventoryY() - 11;
        // The inventory label sits above its own left edge and not above the
        // window's — otherwise it dangles in empty space on the wide cabinet
        // window.
        this.inventoryLabelX = menu.layout().inventoryX() - 1;
    }

    private boolean isRack() {
        return menu.layout() == ShelfMenu.RACK;
    }

    /**
     * What is in a bay — the contents sit in the slots.
     *
     * <p>Nothing without a chassis, exactly as in the cabinet itself.
     * Otherwise the window would show a running bay where the server computes
     * what it does not compute.
     */
    private ServerBay bay(int bay) {
        if (!hasChassis(bay)) {
            return ServerBay.EMPTY;
        }
        return ServerBay.of(
                menu.slots.get(RackBlockEntity.slotOf(bay, ServerPart.CPU)).getItem(),
                menu.slots.get(RackBlockEntity.slotOf(bay, ServerPart.RAM)).getItem(),
                menu.slots.get(RackBlockEntity.slotOf(bay, ServerPart.DISK)).getItem());
    }

    private boolean hasChassis(int bay) {
        return !menu.slots.get(RackBlockEntity.chassisSlot(bay)).getItem().isEmpty();
    }

    /** The top-left corner of a bay's lamp, in the window. */
    private int lampX(int bay) {
        int column = (bay % 2) * RackBlockEntity.SLOTS_PER_BAY
                + RackBlockEntity.SLOTS_PER_BAY - 1;
        return menu.layout().columnX(column) + 18 + 2;
    }

    private int lampY(int bay) {
        return menu.layout().rowY(bay / 2) + 6;
    }

    /** The background image for this layout. */
    private ResourceLocation background() {
        if (menu.layout() == ShelfMenu.RACK) {
            return RACK;
        }
        return menu.layout() == ShelfMenu.MAST ? MAST : DRIVE;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(background(),
                leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
        if (!isRack()) {
            return;
        }
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            // A chassis on its own already glows yellow: it looks like a
            // server and is none, and that is the piece of information this is
            // about.
            if (!hasChassis(bay)) {
                continue;
            }
            int x = leftPos + lampX(bay);
            int y = topPos + lampY(bay);
            graphics.fill(x, y, x + LAMP, y + LAMP,
                    bay(bay).complete() ? RUNNING : INCOMPLETE);
        }
    }

    /**
     * The total sits next to the name.
     *
     * <p>Three numbers in the title line: processes, memory, instructions.
     * Whoever swaps a disk sees at once what they gain from it — otherwise
     * they would have to close the window and look at the block.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (!isRack()) {
            return;
        }
        Component summary = FnFonts.mono(summary());
        graphics.drawString(font, summary,
                imageWidth - 8 - font.width(summary), titleLabelY, Widgets.CASE_TEXT, false);
    }

    private String summary() {
        ServerBay total = ServerBay.EMPTY;
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            total = total.plus(bay(bay).contribution());
        }
        return total.cpu() + "·" + total.ram() + "·" + total.disk();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isRack() && hoveredSlot == null) {
            bayTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * On hover over a lamp, shows what the bay is missing.
     *
     * <p>Only when a slot is not already under the cursor anyway: two
     * explanations on top of each other are none.
     */
    private void bayTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            int x = leftPos + lampX(bay);
            int y = topPos + lampY(bay);
            // A lamp is five pixels wide; nobody hits that. The area therefore
            // spans the full height of the row.
            if (mouseX < x - 2 || mouseX > x + LAMP + 2
                    || mouseY < y - 6 || mouseY > y + LAMP + 6) {
                continue;
            }
            ServerBay contents = bay(bay);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.factorynetwork.rack.bay", bay + 1));
            if (!hasChassis(bay)) {
                lines.add(Component.translatable("screen.factorynetwork.rack.no_chassis"));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
            if (contents.complete()) {
                lines.add(Component.translatable("screen.factorynetwork.rack.running",
                        contents.cpu(), contents.ram(), contents.disk()));
            } else {
                lines.add(Component.translatable("screen.factorynetwork.rack.missing",
                        Component.translatable("screen.factorynetwork.rack.part."
                                + contents.missing().prefix())));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }
}
