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
 * A display on the wall.
 *
 * <p>It holds its name and the most recently computed lines. Computing happens
 * on the server, drawing on the client — and between them go finished strings,
 * not expressions. That is intentional: the client should not need to know
 * what {@code storage.count} means.
 *
 * <p>Refreshed once a second, not on every tick. A wall soon carries thirty
 * displays, and no one reads faster.
 */
public class DisplayBlockEntity extends BlockEntity {

    /** Twenty ticks — once per second. */
    private static final int REFRESH_INTERVAL = 20;

    private static final String KEY_NAME = "DisplayName";
    private static final String KEY_LINES = "Lines";
    private static final String KEY_SCALE = "TextScale";

    /**
     * The largest font that is allowed.
     *
     * <p>Eight times as large is, on a single panel, no more than one line of
     * a letter and a half. Whoever wants more builds wider — and that is the
     * whole point.
     */
    public static final int MAX_SCALE = 8;

    private String displayName = "";
    private List<String> lines = List.of();

    /**
     * How large the font is; 1 is normal.
     *
     * <p><b>It lives here and not in the drawing code</b>, because it comes
     * from the program: {@code scale 4} in the display block. The client
     * receives it with the lines and need not know the language.
     */
    private int textScale = 1;
    /**
     * Set initially so that the first tick computes at once.
     *
     * <p>Not {@code Long.MIN_VALUE}: the difference from the game time would
     * then overflow and turn negative — the check would never fire.
     */
    private long lastRefresh = -REFRESH_INTERVAL;

    /** The wall last found, and when. */
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

    /** The finished lines, as they are drawn. */
    public List<String> lines() {
        return lines;
    }

    /** How large the font is; 1 is normal. */
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
            // Transmit only on change — a display whose numbers stand still
            // should produce no packets.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Computes what stands on the display.
     *
     * <p>It looks for the controller whose network knows this display — and
     * within it the declaration with a matching name. If none is found, the
     * display says so itself; a blank surface would leave the player unsure
     * whether the network is down or the name is wrong.
     */
    /** What stands on the panel, and how large. */
    private record Rendered(List<String> lines, int scale) {

        static Rendered of(List<String> lines) {
            return new Rendered(lines, 1);
        }
    }

    /** A scale that can actually be drawn. */
    private static int clampScale(int wanted) {
        return Math.max(1, Math.min(MAX_SCALE, wanted));
    }

    private Rendered compute() {
        // Only the wall's writing panel computes. The others stay empty —
        // otherwise the same text would stand six times one below the other,
        // and that is exactly what a wall should not be.
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
     * The scale the program names for this panel.
     *
     * <p>The <b>last</b> one wins if someone writes two — the same rule as for
     * any duplicate entry: what stands further down is what the writer meant
     * last.
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
     * The wall this panel belongs to — even if it stands alone.
     *
     * <p><b>Computed at most once a second.</b> The renderer asks for every
     * frame and for every panel, the empty ones included: without the cache,
     * a wall of twenty panels would run a breadth-first search with fresh
     * lists twenty times per frame — exactly the work the distance limit
     * exists to prevent.
     *
     * <p>A second of lag is the same cadence at which the panel recomputes
     * its text. The frame hangs on the block state and changes at once; only
     * the layout of the text lags briefly behind, and that goes unnoticed
     * precisely because the text does too.
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
     * What the wall is called.
     *
     * <p>The first name that turns up in reading order. <b>Not that of the
     * writing panel:</b> whoever builds a wall and then labels one of them
     * has labeled it — which one it was should not matter. The labeling gun
     * sets the name on all of them anyway; this only catches the cases where
     * it was not the gun.
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
     * Brings a line into the form in which it is drawn.
     *
     * <p>The formatting happens here and not in the drawing code: this way
     * what finally stands there goes over the wire, and the client need not
     * know the language.
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
            // Never reaches here — scale produces no line.
            case SCALE -> "";
        };
    }

    /** A bar made of blocks — Minecraft's font has no finer means. */
    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
        return "█".repeat(filled) + "§8" + "█".repeat(10 - filled);
    }

    // ---- Saving and transmitting ------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayName = tag.getString(KEY_NAME);
        // Default when unspecified: a world from yesterday does not have the
        // key, and a panel with a scale of zero would be invisible.
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
