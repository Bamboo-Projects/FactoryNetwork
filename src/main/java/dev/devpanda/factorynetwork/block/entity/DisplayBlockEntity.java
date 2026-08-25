package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.runtime.DisplayValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Display an der Wand.
 *
 * <p>Es hält seinen Namen und die zuletzt berechneten Zeilen. Gerechnet wird
 * auf dem Server, gezeichnet auf dem Client — und dazwischen gehen fertige
 * Zeichenketten, keine Ausdrücke. Das ist Absicht: Der Client soll nicht
 * wissen müssen, was {@code storage.count} bedeutet.
 *
 * <p>Aktualisiert wird im Sekundentakt, nicht in jedem Tick. An einer Wand
 * hängen schnell dreißig Displays, und niemand liest schneller.
 */
public class DisplayBlockEntity extends BlockEntity {

    /** Zwanzig Ticks — einmal je Sekunde. */
    private static final int REFRESH_INTERVAL = 20;

    private static final String KEY_NAME = "DisplayName";
    private static final String KEY_LINES = "Lines";
    private static final String KEY_SCALE = "TextScale";

    /**
     * Die größte Schrift, die zugelassen wird.
     *
     * <p>Achtmal so groß ist auf einer einzelnen Tafel nicht mehr als eine
     * Zeile mit anderthalb Buchstaben. Wer mehr will, baut breiter — und
     * darum geht es ja.
     */
    public static final int MAX_SCALE = 8;

    private String displayName = "";
    private List<String> lines = List.of();

    /**
     * Wie groß die Schrift ist; 1 ist normal.
     *
     * <p><b>Sie steht hier und nicht beim Zeichnen</b>, weil sie aus dem
     * Programm kommt: {@code scale 4} im Display-Block. Der Client bekommt
     * sie mit den Zeilen und muss die Sprache nicht kennen.
     */
    private int textScale = 1;
    /**
     * Anfangs so gesetzt, dass der erste Tick sofort rechnet.
     *
     * <p>Nicht {@code Long.MIN_VALUE}: Die Differenz zur Spielzeit
     * läuft dann über und wird negativ — die Abfrage feuert nie.
     */
    private long lastRefresh = -REFRESH_INTERVAL;

    /** Die zuletzt gefundene Wand, und wann. */
    private dev.devpanda.factorynetwork.block.DisplayWall cachedWall;
    private long wallComputedAt;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.DISPLAY.get(), pos, state);
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        this.displayName = name == null ? "" : name.trim();
        lastRefresh = -REFRESH_INTERVAL;
        setChanged();
    }

    /** Die fertigen Zeilen, wie sie gezeichnet werden. */
    public List<String> lines() {
        return lines;
    }

    /** Wie groß die Schrift ist; 1 ist normal. */
    public int textScale() {
        return textScale;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() - lastRefresh < REFRESH_INTERVAL) {
            return;
        }
        lastRefresh = level.getGameTime();

        Rendered fresh = compute();
        if (!fresh.lines().equals(lines) || fresh.scale() != textScale) {
            lines = fresh.lines();
            textScale = fresh.scale();
            setChanged();
            // Nur bei Änderung übertragen — ein Display, dessen Zahlen
            // stillstehen, soll keine Pakete erzeugen.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Berechnet, was auf dem Display steht.
     *
     * <p>Gesucht wird der Controller, dessen Netz dieses Display kennt — und
     * darin die Deklaration mit passendem Namen. Findet sich keine, sagt das
     * Display es selbst; eine leere Fläche ließe den Spieler im Unklaren,
     * ob das Netz steht oder der Name falsch ist.
     */
    /** Was auf der Tafel steht, und wie groß. */
    private record Rendered(List<String> lines, int scale) {

        static Rendered of(List<String> lines) {
            return new Rendered(lines, 1);
        }
    }

    /** Ein Maßstab, der sich zeichnen lässt. */
    private static int clampScale(int wanted) {
        return Math.max(1, Math.min(MAX_SCALE, wanted));
    }

    private Rendered compute() {
        // Nur die schreibende Tafel der Wand rechnet. Die anderen bleiben
        // leer — sonst stünde derselbe Text sechsmal untereinander, und
        // genau das soll eine Wand ja nicht sein.
        dev.devpanda.factorynetwork.block.DisplayWall wall = wall();
        if (!wall.isAnchor(worldPosition)) {
            return Rendered.of(List.of());
        }
        String name = wallName(wall);
        if (name.isBlank()) {
            return Rendered.of(List.of("§7ohne Namen"));
        }
        var owner = ControllerRegistry.owning(level, worldPosition);
        if (owner.isEmpty()) {
            return Rendered.of(List.of("§8an keinem Netz"));
        }
        var controller = owner.get();
        Decl.Display declaration = controller.program().declarations().stream()
                .filter(Decl.Display.class::isInstance)
                .map(Decl.Display.class::cast)
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElse(null);
        if (declaration == null) {
            return Rendered.of(List.of("§ckein display " + name));
        }

        DisplayValues values = new DisplayValues(controller.graph(), controller.storage(),
                controller.runtime(), controller.globals(), level);
        List<String> rendered = new ArrayList<>();
        for (DisplayValues.Line line : values.evaluate(declaration)) {
            rendered.add(format(line));
        }
        return new Rendered(rendered, scaleOf(declaration));
    }

    /**
     * Der Maßstab, den das Programm für diese Tafel nennt.
     *
     * <p>Der <b>letzte</b> gewinnt, wenn jemand zwei hinschreibt — dieselbe
     * Regel wie bei jeder doppelten Angabe: Was weiter unten steht, hat der
     * Schreibende zuletzt gemeint.
     */
    private static int scaleOf(Decl.Display declaration) {
        int found = 1;
        for (Decl.Display.Entry entry : declaration.entries()) {
            if (entry.kind() == Decl.Display.Entry.Kind.SCALE
                    && entry.value() instanceof dev.devpanda.factorynetwork.lang.ast.Expr.IntLit
                            number) {
                found = clampScale((int) number.value());
            }
        }
        return found;
    }

    /**
     * Die Wand, zu der diese Tafel gehört — auch wenn sie allein steht.
     *
     * <p><b>Höchstens einmal je Sekunde gerechnet.</b> Der Renderer fragt
     * für jedes Bild und für jede Tafel, auch für die leeren: Ohne den
     * Zwischenspeicher liefe bei einer Wand aus zwanzig Tafeln
     * zwanzigmal je Bild eine Breitensuche mit frischen Listen — genau der
     * Aufwand, gegen den es die Entfernungsgrenze überhaupt gibt.
     *
     * <p>Eine Sekunde Verzug ist dieselbe Kadenz, in der die Tafel auch
     * ihren Text neu rechnet. Der Rahmen hängt am Blockzustand und ändert
     * sich sofort; nur die Aufteilung des Textes hinkt kurz nach, und das
     * fällt schon deshalb nicht auf, weil der Text es auch tut.
     */
    public dev.devpanda.factorynetwork.block.DisplayWall wall() {
        long now = level == null ? 0L : level.getGameTime();
        if (cachedWall != null && now - wallComputedAt < REFRESH_INTERVAL) {
            return cachedWall;
        }
        wallComputedAt = now;
        cachedWall = dev.devpanda.factorynetwork.block.DisplayWall.around(level, worldPosition,
                getBlockState().getValue(
                        net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
        return cachedWall;
    }

    /**
     * Wie die Wand heißt.
     *
     * <p>Der erste Name, der in Leserichtung auftaucht. <b>Nicht der der
     * schreibenden Tafel:</b> Wer eine Wand baut und dann eine davon
     * beschriftet, hat sie beschriftet — welche es war, sollte keine Rolle
     * spielen. Die Beschriftungspistole setzt den Namen ohnehin auf alle,
     * das hier fängt nur die Fälle auf, in denen sie es nicht war.
     */
    public String wallName(dev.devpanda.factorynetwork.block.DisplayWall wall) {
        for (net.minecraft.core.BlockPos member : wall.members()) {
            if (level.getBlockEntity(member) instanceof DisplayBlockEntity panel
                    && !panel.displayName.isBlank()) {
                return panel.displayName;
            }
        }
        return "";
    }

    /**
     * Bringt eine Zeile in die Form, in der sie gezeichnet wird.
     *
     * <p>Die Formatierung geschieht hier und nicht beim Zeichnen: So geht
     * über die Leitung, was am Ende dasteht, und der Client muss die Sprache
     * nicht kennen.
     */
    public static String format(DisplayValues.Line line) {
        return switch (line.kind()) {
            case TITLE -> "§f§n" + line.label();
            case ROW -> "§7" + line.label() + " §f" + line.value();
            case TEXT -> "§f" + line.value();
            case PROGRESS -> "§7" + line.label() + " §a" + bar(line.fraction())
                    + " §f" + line.value();
            case INDICATOR -> (line.flag() ? "§a● " : "§8● ") + "§7" + line.label();
            case LIST -> "§7" + line.label() + " §f" + line.value();
            case BUTTON -> "§8[" + line.label() + "]";
            // Kommt hier nie an — scale erzeugt keine Zeile.
            case SCALE -> "";
        };
    }

    /** Ein Balken aus Blöcken — Minecrafts Schrift hat keine feineren Mittel. */
    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
        return "█".repeat(filled) + "§8" + "█".repeat(10 - filled);
    }

    // ---- Speichern und Übertragen -----------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayName = tag.getString(KEY_NAME);
        // Ohne Angabe die Vorgabe: Eine Welt von gestern hat den Schlüssel
        // nicht, und eine Tafel mit Maßstab null wäre unsichtbar.
        textScale = tag.contains(KEY_SCALE) ? clampScale(tag.getInt(KEY_SCALE)) : 1;
        ListTag list = tag.getList(KEY_LINES, Tag.TAG_STRING);
        List<String> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            loaded.add(list.getString(i));
        }
        lines = List.copyOf(loaded);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_NAME, displayName);
        tag.putInt(KEY_SCALE, textScale);
        ListTag list = new ListTag();
        lines.forEach(line -> list.add(StringTag.valueOf(line)));
        tag.put(KEY_LINES, list);
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
