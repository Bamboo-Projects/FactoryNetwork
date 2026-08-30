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
 * Der Bildschirm eines Regals.
 *
 * <p>Zwei Zuschnitte, ein Bildschirm: Das Laufwerk hat zwei Spalten zu fünf
 * Reihen wie seine Schächte, der Schrank zwölf Einschübe zu je vier Plätzen —
 * das Gehäuse und seine drei Bauteile.
 *
 * <p>Was der Schrank zusätzlich zeigt, ist der <b>Zustand je Einschub</b>:
 * ein Lämpchen hinter jedem, grün wenn er läuft, gelb wenn ein Gehäuse
 * steckt und trotzdem nichts rechnet. Ohne das sieht ein Einschub, dem der
 * Datenträger fehlt, genauso aus wie ein fertiger.
 */
public class ShelfScreen extends AbstractContainerScreen<ShelfMenu> {

    private static final ResourceLocation DRIVE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/drive.png");
    private static final ResourceLocation RACK = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/rack.png");
    private static final ResourceLocation MAST = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/mast.png");

    /** Das Bild liegt auf einem 512er Blatt, wie alle Oberflächen der Mod. */
    private static final int SHEET = 512;

    /** Dieselben Töne wie an der Front des Blocks. */
    private static final int RUNNING = 0xFF78DC8C;
    private static final int INCOMPLETE = 0xFFE8AC3E;

    private static final int LAMP = 5;

    public ShelfScreen(ShelfMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.layout().width();
        this.imageHeight = menu.layout().height();
        this.inventoryLabelY = menu.layout().inventoryY() - 11;
        // Die Beschriftung des Inventars steht über dessen linker Kante und
        // nicht über der des Fensters — sonst hängt sie beim breiten
        // Schrankfenster im Nichts.
        this.inventoryLabelX = menu.layout().inventoryX() - 1;
    }

    private boolean isRack() {
        return menu.layout() == ShelfMenu.RACK;
    }

    /**
     * Was in einem Einschub steckt — der Inhalt steht in den Plätzen.
     *
     * <p>Ohne Gehäuse nichts, genau wie im Schrank selbst. Sonst zeigte das
     * Fenster einen laufenden Einschub, wo der Server rechnet, was er nicht
     * rechnet.
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

    /** Die linke obere Ecke des Lämpchens eines Einschubs, im Fenster. */
    private int lampX(int bay) {
        int column = (bay % 2) * RackBlockEntity.SLOTS_PER_BAY
                + RackBlockEntity.SLOTS_PER_BAY - 1;
        return menu.layout().columnX(column) + 18 + 2;
    }

    private int lampY(int bay) {
        return menu.layout().rowY(bay / 2) + 6;
    }

    /** Die Fläche zu diesem Zuschnitt. */
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
            // Ein Gehäuse allein leuchtet schon gelb: Es sieht aus wie ein
            // Server und ist keiner, und das ist die Auskunft, um die es
            // hier geht.
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
     * Die Summe steht neben dem Namen.
     *
     * <p>Drei Zahlen in der Titelzeile: Abläufe, Speicher, Anweisungen. Wer
     * einen Datenträger tauscht, sieht sofort, was er davon hat — sonst
     * müsste er das Fenster schließen und den Block anschauen.
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
     * Zeigt beim Zeigen auf ein Lämpchen, was dem Einschub fehlt.
     *
     * <p>Nur, wenn nicht ohnehin ein Platz unter dem Zeiger liegt: Zwei
     * Erklärungen übereinander sind keine.
     */
    private void bayTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int bay = 0; bay < RackBlockEntity.BAYS; bay++) {
            int x = leftPos + lampX(bay);
            int y = topPos + lampY(bay);
            // Ein Lämpchen ist fünf Pixel breit; das trifft niemand. Der
            // Bereich reicht deshalb über die volle Höhe der Reihe.
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
