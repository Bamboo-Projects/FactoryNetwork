package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Was ein Router je Seite durchlässt.
 *
 * <p><b>Das Glasfaser-Bild.</b> Eine Leitung trägt alle Farben; der Router
 * zieht einzelne heraus. Eine Seite ohne Filter ist der Anschluss an den
 * Hauptstrang — dort geht alles durch. Eine Seite mit Farbe greift genau
 * diese ab.
 *
 * <p><b>Bis zum 29.08. war es umgekehrt.</b> Der Router trug Bahnnummern und
 * war farbneutral: Was auf einer Bahn zusammenkam, galt als verbunden. Er war
 * ein Mischer, kein Splitter — zwei getrennte Teilnetze wuchsen über ihn
 * zusammen.
 *
 * <p>Die alte Aufgabe kann er weiterhin: Zwei Seiten auf dieselbe Farbe
 * gestellt sind verbunden, verschiedene kreuzen sich berührungslos. Das
 * Kreuzen ist damit ein Sonderfall des Filterns.
 *
 * <p>Die Zuordnung steht in der BlockEntity und nicht im Blockzustand: Sechs
 * Seiten mit je fünf Werten wären 15625 Zustände, und die legt Minecraft
 * alle beim Start an.
 */
public class RouterBlockEntity extends BlockEntity {

    /** Diese Seite ist abgeklemmt: Es geht nichts hinein und nichts heraus. */
    public static final int OFF = 0;

    /**
     * Vier Bahnen.
     *
     * <p>Mehr Kreuzungen in einem einzigen Block sind nicht mehr zu lesen —
     * bei sechs Seiten und sechs Bahnen wäre fast jede Seite für sich, und
     * dafür braucht es keinen Block.
     */
    /**
     * Diese Seite lässt alles durch — der Anschluss an den Hauptstrang.
     *
     * <p>Der Wert ist die alte Bahn 1: Ein Router aus einer älteren Welt hat
     * danach eine Seite, die alles durchlässt, statt einer auf Bahn 1. Das
     * ist die verträglichere Vorgabe — er verbindet weiter, was er verband.
     */
    public static final int ALL = 1;

    /** So viele Einstellungen gibt es je Seite: aus, alles, und je Farbe eine. */
    public static final int LANES = ALL
            + dev.devpanda.factorynetwork.block.CableColour.values().length;

    private static final String KEY_LANES = "Lanes";

    /**
     * Bahn je Seite, in der Reihenfolge von {@link Direction#values()}.
     *
     * <p>Frisch gesetzt liegt alles auf Bahn eins: Ein Router, den man
     * hinstellt und nicht anfasst, verhält sich wie ein Stück Kabel. Wer
     * trennen will, sagt es — nicht umgekehrt.
     */
    private final byte[] lanes = new byte[Direction.values().length];

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.ROUTER.get(), pos, state);
        java.util.Arrays.fill(lanes, (byte) 1);
    }

    /**
     * Was diese Seite durchlässt.
     *
     * <p>{@code null} heißt <b>alles</b> — die Seite zum Hauptstrang. Eine
     * Farbe heißt: nur diese. Ob die Seite überhaupt an ist, fragt
     * {@link #isOff}.
     */
    public dev.devpanda.factorynetwork.block.CableColour filter(Direction side) {
        int wert = lanes[side.ordinal()];
        if (wert == OFF || wert == ALL) {
            return null;
        }
        // Die Farbwerte fangen bei zwei an, weil eins schon „alles"
        // heißt. Ein Router aus einer Welt von vor dem 29.08. hat danach
        // Farben statt Bahnen — die Trennung bleibt dieselbe, nur heißt sie
        // anders.
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        return farben[Math.min(wert - 2, farben.length - 1)];
    }

    /**
     * Die rohe Einstellung dieser Seite.
     *
     * <p>Für Anzeige und Speicherung: aus, alles, oder eine Farbe als Zahl.
     * Wer wissen will, <i>was</i> durchgeht, fragt {@link #filter}.
     */
    public int lane(Direction side) {
        return lanes[side.ordinal()];
    }

    /** Ist diese Seite abgeklemmt? */
    public boolean isOff(Direction side) {
        return lanes[side.ordinal()] == OFF;
    }

    /** Setzt den Filter: {@code null} für alles. */
    public void setFilter(Direction side,
                          dev.devpanda.factorynetwork.block.CableColour colour) {
        lanes[side.ordinal()] = (byte) (colour == null ? ALL : colour.ordinal() + 2);
        update();
    }

    /**
     * Setzt die rohe Einstellung.
     *
     * <p>Für das Fenster, das sich durch die Werte klickt, und für die
     * Speicherung. Wer eine Farbe meint, nimmt {@link #setFilter}.
     */
    public void setLane(Direction side, int lane) {
        lanes[side.ordinal()] = (byte) Math.max(OFF, Math.min(LANES, lane));
        update();
    }

    /** Klemmt diese Seite ab. */
    public void turnOff(Direction side) {
        lanes[side.ordinal()] = (byte) OFF;
        update();
    }

    private void update() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Schaltet eine Seite eine Bahn weiter: 1, 2, 3, 4, aus, wieder 1.
     *
     * <p>Nur vorwärts. Rückwärts über die Schleichtaste wäre schneller am
     * Ziel, aber die Schleichtaste ist beim Anklicken eines Blocks schon
     * belegt, und fünf Klicks bis zurück sind auszuhalten.
     */
    public int cycle(Direction side) {
        int next = lanes[side.ordinal()] + 1;
        if (next > LANES) {
            next = OFF;
        }
        lanes[side.ordinal()] = (byte) next;
        update();
        return next;
    }

    /**
     * Welche Bahn diese Seite führt — null, wenn dort gar kein Router steht.
     *
     * <p>Der Graph fragt so, weil er über die Welt läuft und nicht über
     * BlockEntities: Für ihn ist eine abgeklemmte Seite dasselbe wie gar
     * kein Router.
     */
    public static int laneAt(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RouterBlockEntity router
                ? router.lanes[side.ordinal()] : OFF;
    }

    /**
     * Was diese Seite durchlässt — für den Graphen, der über die Welt läuft.
     *
     * <p>Gibt {@code null} zurück, wenn alles durchgeht <b>oder</b> wenn dort
     * kein Router steht. Ob die Seite überhaupt offen ist, fragt der Graph
     * mit {@link #laneAt} — zwei Fragen, zwei Antworten.
     */
    public static dev.devpanda.factorynetwork.block.CableColour filterAt(
            BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RouterBlockEntity router
                ? router.filter(side) : null;
    }

    /**
     * Das Fenster zum Router.
     *
     * <p>Die Bahnlasten holt es sich beim Controller, der das Netz kennt —
     * der Router selbst weiß nur, welche Seite auf welcher Bahn liegt.
     */
    public net.minecraft.world.MenuProvider menu() {
        return new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, player) -> new dev.devpanda.factorynetwork.client.menu.RouterMenu(
                        id,
                        dev.devpanda.factorynetwork.client.menu.RouterMenu.dataOf(this,
                                this::laneLoad, this::laneCapacity),
                        net.minecraft.world.inventory.ContainerLevelAccess.create(
                                level, worldPosition)),
                getBlockState().getBlock().getName());
    }

    private int laneLoad(int lane) {
        if (level == null) {
            return 0;
        }
        // Seit dem 29.08. gibt es keine Kanallast mehr — was eine Bahn
        // trägt, ist ihr Durchsatz, und der hängt nicht davon ab, wie viele
        // Geräte dahinter liegen.
        return dev.devpanda.factorynetwork.network.Bandwidth.DENSE;
    }

    /** Was eine Bahn trägt: so viel wie ein dichtes Kabel. */
    private int laneCapacity() {
        return dev.devpanda.factorynetwork.network.Bandwidth.DENSE;
    }

    /** Wie viele Seiten überhaupt angeschlossen sind. */
    public int connectedSides() {
        int count = 0;
        for (byte lane : lanes) {
            if (lane != OFF) {
                count++;
            }
        }
        return count;
    }

    /** Wie viele verschiedene Bahnen der Router gerade führt. */
    public int usedLanes() {
        boolean[] seen = new boolean[LANES + 1];
        for (byte lane : lanes) {
            seen[lane] = true;
        }
        int count = 0;
        for (int lane = 1; lane <= LANES; lane++) {
            if (seen[lane]) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] stored = tag.getByteArray(KEY_LANES);
        for (int i = 0; i < lanes.length; i++) {
            // Kürzere Felder aus älteren Ständen sollen nicht abstürzen; was
            // fehlt, bleibt auf der Vorgabe.
            if (i < stored.length) {
                lanes[i] = (byte) Math.max(OFF, Math.min(LANES, stored[i]));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray(KEY_LANES, lanes.clone());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
