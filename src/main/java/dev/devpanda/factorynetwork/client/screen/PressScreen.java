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
 * The press's window.
 *
 * <p>Two gauges carry the interface: the arrow shows how far the operation
 * has come, the bar on the left how much power there is. Both matter more than
 * they look — a machine standing idle must be able to say why, or the player
 * looks for the fault in themselves.
 */
public class PressScreen extends AbstractContainerScreen<PressMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/press.png");

    /** Where the filled variants sit in the atlas. */
    private static final int ARROW_U = 180;
    private static final int ARROW_V = 0;
    private static final int ENERGY_U = 180;
    private static final int ENERGY_V = 20;

    /** How tall the power bar is, in pixels. */
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

        // Progress: the arrow grows from left to right.
        int required = menu.required();
        if (required > 0 && menu.progress() > 0) {
            int breite = Math.min(30, menu.progress() * 30 / required);
            graphics.blit(TEXTURE, leftPos + 85, topPos + 40, ARROW_U, ARROW_V,
                    breite, 14, 512, 512);
        }

        // Power: the bar grows from the bottom up, like a tank.
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
     * On hover, the bar says how much is really in there.
     *
     * <p>A bar alone does not answer the question "is that still enough?" —
     * for that you need the number, and you do not want it on screen all the
     * time.
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
