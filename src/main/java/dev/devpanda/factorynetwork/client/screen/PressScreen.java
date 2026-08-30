package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.PressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Das Fenster der Presse.
 *
 * <p>Zwei Anzeigen tragen die Bedienung: der Pfeil zeigt, wie weit der
 * Vorgang ist, der Balken links, wie viel Strom da ist. Beide sind wichtiger
 * als sie aussehen — eine Maschine, die stillsteht, muss sagen können warum,
 * sonst sucht der Spieler den Fehler bei sich.
 */
public class PressScreen extends AbstractContainerScreen<PressMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/press.png");

    /** Wo die gefüllten Fassungen im Atlas liegen. */
    private static final int ARROW_U = 180;
    private static final int ARROW_V = 0;
    private static final int ENERGY_U = 180;
    private static final int ENERGY_V = 20;

    /** Wie hoch der Strombalken ist, in Bildpunkten. */
    private static final int BAR = 66;

    public PressScreen(PressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);

        // Fortschritt: der Pfeil wächst von links nach rechts.
        int required = menu.required();
        if (required > 0 && menu.progress() > 0) {
            int breite = Math.min(30, menu.progress() * 30 / required);
            graphics.blit(TEXTURE, leftPos + 85, topPos + 40, ARROW_U, ARROW_V,
                    breite, 14, 512, 512);
        }

        // Strom: der Balken wächst von unten nach oben, wie ein Tank.
        int hoehe = (int) Math.min(BAR, (long) menu.energy() * BAR / menu.capacity());
        if (hoehe > 0) {
            graphics.blit(TEXTURE, leftPos + 8, topPos + 17 + (BAR - hoehe),
                    ENERGY_U, ENERGY_V + (BAR - hoehe), 8, hoehe, 512, 512);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderEnergyTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Der Balken sagt beim Überfahren, wie viel wirklich drin ist.
     *
     * <p>Ein Balken allein beantwortet die Frage „reicht das noch?" nicht —
     * dafür braucht es die Zahl, und die will man nicht dauerhaft im Bild
     * haben.
     */
    private void renderEnergyTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + 8;
        int y = topPos + 17;
        if (mouseX < x || mouseX >= x + 8 || mouseY < y || mouseY >= y + BAR) {
            return;
        }
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.factorynetwork.press.energy",
                        String.format(java.util.Locale.GERMANY, "%,d", menu.energy()),
                        String.format(java.util.Locale.GERMANY, "%,d", menu.capacity())),
                Component.translatable(menu.energy() > 0
                                ? "gui.factorynetwork.press.powered"
                                : "gui.factorynetwork.press.unpowered")
                        .withStyle(menu.energy() > 0
                                ? net.minecraft.ChatFormatting.GRAY
                                : net.minecraft.ChatFormatting.RED)),
                mouseX, mouseY);
    }
}
