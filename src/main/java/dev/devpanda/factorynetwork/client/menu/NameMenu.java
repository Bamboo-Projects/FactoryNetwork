package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Das Fenster, in dem ein Block seinen Namen bekommt.
 *
 * <p>Eines für Connector und Anzeige: Es ist dieselbe Handlung — einem Block
 * im Netz sagen, wie er heißt. Zwei Fassungen wären zwei Orte, an denen die
 * Namensregeln auseinanderlaufen.
 *
 * <p><b>Warum überhaupt ein Fenster am Block?</b> Bisher ging Benennen nur
 * mit der Beschriftungspistole, und das hieß: Ohne gebaute Pistole lässt sich
 * kein Gerät ansprechen. Ein Rechtsklick sagte einem nur den Namen, den man
 * ohnehin sah. Die Pistole bleibt — sie kann etwas, das ein Fenster nicht
 * kann: einen Namen einmal tippen und zwanzigmal vergeben.
 *
 * <p>Keine Plätze: Ein Name ist eine Zeichenkette und kein Gegenstand. Was
 * hin und her geht, ist eine Position und ein Wort.
 */
public class NameMenu extends AbstractContainerMenu {

    /** So weit darf man sich vom Block entfernen, bevor es zugeht. */
    private static final double REACH = 8.0;

    private final BlockPos position;
    /**
     * Die Fläche, an der der Anschluss sitzt — {@code null} bei allem, was
     * ein ganzer Block ist.
     *
     * <p>An einem Kabelblock hängen bis zu sechs Anschlüsse. Welcher gemeint
     * ist, weiß nur der Klick, der das Fenster geöffnet hat: Danach ist es
     * nicht mehr zu erfahren.
     */
    private final @org.jetbrains.annotations.Nullable Direction side;

    public NameMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, buffer.readBlockPos(), sideOf(buffer.readByte()));
    }

    public NameMenu(int id, BlockPos position) {
        this(id, position, null);
    }

    public NameMenu(int id, BlockPos position,
                    @org.jetbrains.annotations.Nullable Direction side) {
        super(FnMenus.NAME.get(), id);
        this.position = position;
        this.side = side;
    }

    private static @org.jetbrains.annotations.Nullable Direction sideOf(byte written) {
        return written < 0 ? null : Direction.from3DDataValue(written);
    }

    public BlockPos position() {
        return position;
    }

    public @org.jetbrains.annotations.Nullable Direction side() {
        return side;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Solange man in der Nähe steht.
     *
     * <p>Nicht über {@code stillValid(access, player, block)} wie die anderen
     * Fenster: Das Menü kennt den Block nicht, für den es aufgeht — Connector
     * und Anzeige teilen es sich.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5,
                position.getZ() + 0.5) <= REACH * REACH;
    }
}
