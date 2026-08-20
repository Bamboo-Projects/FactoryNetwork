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

    private String displayName = "";
    private List<String> lines = List.of();
    /**
     * Anfangs so gesetzt, dass der erste Tick sofort rechnet.
     *
     * <p>Nicht {@code Long.MIN_VALUE}: Die Differenz zur Spielzeit
     * läuft dann über und wird negativ — die Abfrage feuert nie.
     */
    private long lastRefresh = -REFRESH_INTERVAL;

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

    public void serverTick() {
        if (level == null || level.getGameTime() - lastRefresh < REFRESH_INTERVAL) {
            return;
        }
        lastRefresh = level.getGameTime();

        List<String> fresh = compute();
        if (!fresh.equals(lines)) {
            lines = fresh;
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
    private List<String> compute() {
        if (displayName.isBlank()) {
            return List.of("§7ohne Namen");
        }
        var owner = ControllerRegistry.owning(level, worldPosition);
        if (owner.isEmpty()) {
            return List.of("§8an keinem Netz");
        }
        var controller = owner.get();
        Decl.Display declaration = controller.program().declarations().stream()
                .filter(Decl.Display.class::isInstance)
                .map(Decl.Display.class::cast)
                .filter(candidate -> candidate.name().equals(displayName))
                .findFirst()
                .orElse(null);
        if (declaration == null) {
            return List.of("§ckein display " + displayName);
        }

        DisplayValues values = new DisplayValues(controller.graph(), controller.storage(),
                controller.runtime());
        List<String> rendered = new ArrayList<>();
        for (DisplayValues.Line line : values.evaluate(declaration)) {
            rendered.add(format(line));
        }
        return rendered;
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
