package dev.devpanda.factorynetwork.terminal;

import dev.devpanda.factorynetwork.block.entity.MastBlockEntity;
import dev.devpanda.factorynetwork.item.RemoteDeviceItem;
import dev.devpanda.factorynetwork.upgrade.Range;
import dev.devpanda.factorynetwork.upgrade.RemoteDevice;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ob ein Fenster aus der Ferne offen bleiben darf.
 *
 * <p>Am Block ist die Frage einfach: Steht der Block noch da und ist der
 * Spieler nah genug? Aus der Ferne sind es drei Fragen, und alle drei können
 * mitten im Spiel umschlagen.
 *
 * <p><b>Sie steht hier und nicht im Menü</b>, damit ein Prüflauf sie stellen
 * kann. Ein Test, der stattdessen auf das Zugehen des Fensters wartet, prüft
 * den Ticker und nicht die Regel.
 */
public final class RemoteAccess {

    /**
     * Darf der Spieler mit diesem Gerät weiterarbeiten?
     *
     * <p>Drei Bedingungen, in dieser Reihenfolge:
     *
     * <ol>
     *   <li><b>Das Gerät liegt noch dort, wo es war.</b> Wer es weglegt, in
     *       eine Kiste tut oder fallen lässt, hat keines mehr in der Hand.
     *       Ohne diese Prüfung bliebe das Fenster offen, während der
     *       Gegenstand längst in einer Kiste liegt.</li>
     *   <li><b>Es ist noch dasselbe Netz.</b> Wer ein zweites Gerät in den
     *       Platz tauscht, das an einem anderen Mast hängt, hielte sonst ein
     *       Fenster auf ein Netz offen, zu dem das Gerät in seiner Hand gar
     *       nicht gehört.</li>
     *   <li><b>Der Mast steht noch — und sein Stück Welt ist geladen.</b>
     *       Er kann abgebaut worden sein, während das Fenster offen war.</li>
     *   <li><b>Der Spieler ist in Reichweite.</b> Sie kommt aus den Karten in
     *       Mast und Gerät — das ist die Stelle, an der die Reichweite
     *       überhaupt etwas tut. Steht er in einer anderen Welt, reicht nur
     *       die Grenzenlos-Karte.</li>
     * </ol>
     *
     * @param slot der Platz im Inventar, an dem das Gerät beim Öffnen lag
     * @param expected der Mast, an dem dieses Fenster hängt
     */
    public static boolean allowed(Player player, int slot, GlobalPos expected) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return false;
        }
        ItemStack device = player.getInventory().getItem(slot);
        RemoteDevice kind = RemoteDeviceItem.deviceOf(device);
        if (kind == null) {
            return false;
        }
        GlobalPos mast = RemoteDeviceItem.mastOf(device);
        if (mast == null || !mast.equals(expected)) {
            return false;
        }
        MastBlockEntity standing = mastAt(player, mast);
        if (standing == null) {
            return false;
        }
        boolean sameLevel = player.level().dimension().equals(mast.dimension());
        // Der Abstand zählt vom Mast, nicht vom Controller: Wer einen
        // zweiten Mast aufstellt, verlängert damit seine Reichweite, und
        // genau dafür baut man ihn. In einer anderen Welt gibt es keinen
        // Abstand — dort entscheidet allein die Karte.
        double distance = sameLevel
                ? Math.sqrt(player.distanceToSqr(mast.pos().getX() + 0.5,
                        mast.pos().getY() + 0.5, mast.pos().getZ() + 0.5))
                : 0;
        return Range.covers(standing.loadout(), RemoteDeviceItem.loadoutOf(device),
                sameLevel, distance);
    }

    /**
     * Der Mast an dieser Stelle — auch in einer anderen Welt.
     *
     * <p><b>Erst fragen, ob das Stück Welt geladen ist.</b>
     * {@code getBlockEntity} lädt es sonst nach, und diese Frage wird für
     * jedes offene Fenster in jedem Tick gestellt — ein Netz am anderen Ende
     * der Welt hielte damit dauerhaft Land offen, das niemand betritt.
     *
     * <p>Ein nicht geladener Mast gilt als nicht erreichbar. Das ist ehrlich:
     * Was dort geschieht, rechnet niemand aus, solange kein Spieler in der
     * Nähe ist.
     */
    public static MastBlockEntity mastAt(Player player, GlobalPos mast) {
        Level level = player.level();
        if (!level.dimension().equals(mast.dimension())) {
            if (player.getServer() == null) {
                return null;
            }
            level = player.getServer().getLevel(mast.dimension());
        }
        if (level == null || !level.isLoaded(mast.pos())) {
            return null;
        }
        return level.getBlockEntity(mast.pos()) instanceof MastBlockEntity standing
                ? standing : null;
    }

    /**
     * Wo im Inventar dieses Gerät liegt, oder -1.
     *
     * <p>Gesucht wird der Gegenstand selbst, nicht ein gleicher: Zwei
     * Terminals im Inventar sind verschiedene Geräte mit verschiedenen
     * Masten, und wer das zweite herausnimmt, soll nicht das erste schließen.
     */
    public static int slotOf(Player player, ItemStack device) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == device) {
                return slot;
            }
        }
        return -1;
    }

    private RemoteAccess() {
    }
}
