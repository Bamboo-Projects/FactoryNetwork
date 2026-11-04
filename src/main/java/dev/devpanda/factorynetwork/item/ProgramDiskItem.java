package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.platform.Nbt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A program to carry: every file of a network's project on one disk.
 *
 * <p>Right-click on a network block writes the running program onto the
 * disk; sneak and right-click reads the disk into that network's draft. Into
 * the draft and not straight into the running program: whoever loads a
 * stranger's program looks at it first and adopts it with Apply — as with
 * any change in the editor.
 */
public class ProgramDiskItem extends Item {

    private static final String KEY = "FactoryNetworkProgram";

    /** The lore shows at most this many file names; more would be a wall of text. */
    private static final int LORE_FILES = 6;

    public ProgramDiskItem(Properties properties) {
        super(properties);
    }

    /** The program on this disk, or {@code null} if it is blank. */
    public static @Nullable Project read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) {
            return null;
        }
        CompoundTag files = Nbt.getCompound(tag, KEY);
        Map<String, String> read = new LinkedHashMap<>();
        for (String name : files.keySet()) {
            read.put(name, Nbt.getString(files, name));
        }
        return read.isEmpty() ? null : new Project(read);
    }

    /** Writes the program onto the disk and lists its files on it. */
    public static void write(ItemStack stack, Project project) {
        CompoundTag files = new CompoundTag();
        project.files().forEach(files::putString);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(KEY, files);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        List<Component> lines = new ArrayList<>();
        int lineCount = 0;
        for (String source : project.files().values()) {
            lineCount += source.split("\n", -1).length;
        }
        lines.add(Component.translatable("item.factorynetwork.program_disk.summary",
                project.files().size(), lineCount));
        project.files().keySet().stream().sorted().limit(LORE_FILES)
                .forEach(name -> lines.add(Component.literal("§7" + name)));
        if (project.files().size() > LORE_FILES) {
            lines.add(Component.literal("§8…"));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }
}
