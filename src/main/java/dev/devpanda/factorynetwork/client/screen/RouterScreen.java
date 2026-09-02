package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.client.menu.RouterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The router's screen: six sides, five buttons per side.
 *
 * <p>The same colours as the rings on the block — whoever has seen the front
 * recognises the same lane here again. And the same order: off, then one
 * through four.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/router.png");

    /** Disconnected: the same dark grey as on the block. */
    private static final int OFF_COLOUR = 0xFF34383C;

    /** Everything through: a neutral light grey, no hue. */
    private static final int ALL_COLOUR = 0xFFB0B4B8;

    /**
     * The colour of a button.
     *
     * <p><b>From the dye, not from a list.</b> There used to be five numbers
     * here, written down twice — once here, once in the paint script. Since
     * the router carries colours instead of lanes there are seventeen, and
     * seventeen numbers kept in two places drift apart.
     */
    private static int colourOf(int wert) {
        if (wert == RouterBlockEntity.OFF) {
            return OFF_COLOUR;
        }
        if (wert == RouterBlockEntity.ALL) {
            return ALL_COLOUR;
        }
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        var farbe = farben[Math.min(wert - 2, farben.length - 1)];
        return farbe.dye() == null ? ALL_COLOUR
                : 0xFF000000 | farbe.dye().getTextureDiffuseColor();
    }

    private static final int ROW_TOP = 32;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_LEFT = 62;

    /**
     * One button per side, not one per setting.
     *
     * <p>Eighteen settings times six sides would be a hundred and eight
     * buttons. Instead each row shows <b>its</b> value, and a click steps
     * onward — the same gesture as on the block.
     */
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_STEP = 20;

    /** What a setting is called: off, all, or a colour. */
    private static Component labelOf(int wert) {
        if (wert == RouterBlockEntity.OFF) {
            return Component.translatable("screen.factorynetwork.router.off");
        }
        if (wert == RouterBlockEntity.ALL) {
            return Component.translatable("screen.factorynetwork.router.all");
        }
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        var farbe = farben[Math.min(wert - 2, farben.length - 1)];
        return Component.translatable("colour.factorynetwork." + farbe.getSerializedName());
    }

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 162;
        // A router takes nothing in; the "Inventory" label would be a heading
        // with no content.
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);

        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            graphics.drawString(font, Component.translatable(
                            "side.factorynetwork." + side.getSerializedName()),
                    leftPos + 9, y + 4, Widgets.CASE_TEXT, false);

            int aktiv = menu.lane(side);
            int x = leftPos + BUTTON_LEFT;
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1,
                    colourOf(aktiv));

            // The name beside it: with seventeen colours a colour swatch alone
            // can no longer be told apart — light blue and cyan are two pixels
            // apart.
            graphics.drawString(font, labelOf(aktiv),
                    x + BUTTON_SIZE + 6, y + 4, Widgets.CASE_TEXT, false);
        }

        // Below the sides, what the router costs: one tick. Without this line
        // the delay would be an effect with no visible cause — and someone who
        // cleanly separates their network would not know what they pay for.
        graphics.drawString(font, Component.translatable(
                        "screen.factorynetwork.router.latency",
                        dev.devpanda.factorynetwork.network.Latency.PER_HOP),
                leftPos + 9, topPos + ROW_TOP + Direction.values().length * ROW_HEIGHT + 4,
                Widgets.CASE_TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Above the buttons stands what the lane carries — the number you
        // would otherwise have to read off each side one at a time.
        int lane = laneAt(mouseX, mouseY);
        if (lane > 0) {
            graphics.renderTooltip(font, List.of(
                            labelOf(lane),
                            Component.translatable("screen.factorynetwork.router.load",
                                    menu.formatLoad(lane))),
                    java.util.Optional.empty(), mouseX, mouseY);
        } else if (lane == 0) {
            graphics.renderTooltip(font,
                    Component.translatable("screen.factorynetwork.router.off"), mouseX, mouseY);
        }
    }

    /** Which setting sits under the cursor, or -1. */
    private int laneAt(int mouseX, int mouseY) {
        Direction side = sideAt(mouseX, mouseY);
        return side == null ? -1 : menu.lane(side);
    }

    /** Which row the cursor is over, or {@code null}. */
    private Direction sideAt(double mouseX, double mouseY) {
        int x = leftPos + BUTTON_LEFT;
        if (mouseX < x || mouseX >= x + BUTTON_SIZE) {
            return null;
        }
        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + BUTTON_SIZE) {
                return side;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Direction side = sideAt(mouseX, mouseY);
        if (side != null) {
            // A click steps onward instead of hitting one of eighteen buttons
            // — the same gesture as on the block. Right-click goes back:
            // whoever overshoots a colour otherwise waits seventeen clicks.
            int naechste = button == 1
                    ? (menu.lane(side) + RouterBlockEntity.LANES) % (RouterBlockEntity.LANES + 1)
                    : (menu.lane(side) + 1) % (RouterBlockEntity.LANES + 1);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    RouterMenu.buttonFor(side, naechste));
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
