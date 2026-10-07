package dev.devpanda.factorynetwork.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves are read through {@link Nbt}, and {@link Nbt} keeps its promises.
 *
 * <p><b>Two halves.</b> The first pins what a missing or wrongly typed entry
 * reads as: every version's implementation of the class has to give these
 * answers, or a world would mean something else after an update. The second
 * keeps the readers from going around the class again.
 *
 * <p><b>What the second half cannot see.</b> A plain {@code tag.getString(…)}
 * looks like a hundred other calls of that name, and telling them apart needs
 * the types. The check therefore covers the forms that are unmistakable: the
 * typed {@code contains} and {@code getList}, {@code getAllKeys}, the UUID
 * helpers and reads straight off {@code copyTag()}. The complete check is
 * compiling against a later Minecraft version; see
 * {@code docs/versionen/Nbt-1.21.5.java.txt}.
 */
class NbtBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java/dev/devpanda/factorynetwork");

    private static final List<Pattern> AROUND = List.of(
            Pattern.compile("(?<!Nbt)\\.(contains|getList)\\([^()]*,\\s*(net\\.minecraft\\.nbt\\.)?Tag\\.TAG_"),
            Pattern.compile("\\.getAllKeys\\(\\)"),
            Pattern.compile("(?<!Nbt)\\.(hasUUID|putUUID)\\("),
            Pattern.compile("copyTag\\(\\)\\.get[A-Z]"));

    @Test
    @DisplayName("A missing entry reads as empty, not as an error")
    void missingReadsAsEmpty() {
        CompoundTag tag = new CompoundTag();
        assertEquals("", Nbt.getString(tag, "x"));
        assertEquals(0, Nbt.getInt(tag, "x"));
        assertEquals(0L, Nbt.getLong(tag, "x"));
        assertFalse(Nbt.getBoolean(tag, "x"));
        assertTrue(Nbt.getCompound(tag, "x").isEmpty());
        assertTrue(Nbt.getList(tag, "x", Tag.TAG_COMPOUND).isEmpty());
        assertEquals(0, Nbt.getByteArray(tag, "x").length);
        assertFalse(Nbt.has(tag, "x", Tag.TAG_INT));
        assertFalse(Nbt.hasUUID(tag, "x"));
        assertTrue(Nbt.keys(tag).isEmpty());
    }

    @Test
    @DisplayName("An entry of another type reads as empty too")
    void wrongTypeReadsAsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.putString("x", "seven");
        assertEquals(0, Nbt.getInt(tag, "x"));
        assertTrue(Nbt.getCompound(tag, "x").isEmpty());
        assertFalse(Nbt.has(tag, "x", Tag.TAG_INT));
        assertTrue(Nbt.has(tag, "x", Tag.TAG_STRING));
    }

    @Test
    @DisplayName("A number is found under 'any number', whatever its width")
    void anyNumber() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("n", 5L);
        assertTrue(Nbt.has(tag, "n", Tag.TAG_ANY_NUMERIC));
        assertFalse(Nbt.has(tag, "n", Tag.TAG_INT));
    }

    @Test
    @DisplayName("A list is only handed out if its elements have the asked type")
    void listOfAnotherTypeIsEmpty() {
        CompoundTag tag = new CompoundTag();
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("a"));
        tag.put("l", strings);
        assertTrue(Nbt.getList(tag, "l", Tag.TAG_COMPOUND).isEmpty());
        ListTag read = Nbt.getList(tag, "l", Tag.TAG_STRING);
        assertEquals(1, read.size());
        assertEquals("a", Nbt.stringAt(read, 0));
        assertTrue(Nbt.compoundAt(read, 0).isEmpty());
    }

    @Test
    @DisplayName("A UUID comes back as it went in")
    void uuidRoundTrip() {
        CompoundTag tag = new CompoundTag();
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Nbt.putUUID(tag, "id", id);
        assertTrue(Nbt.hasUUID(tag, "id"));
        assertEquals(id, Nbt.getUUID(tag, "id"));
    }

    @Test
    @DisplayName("Nobody reads a save around the class, in the forms that can be told apart")
    void nobodyGoesAround() throws IOException {
        List<String> breaches = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> sources = Files.walk(MAIN)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (source.getFileName().toString().equals("Nbt.java")) {
                    continue;
                }
                checked++;
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String code = lines.get(i).strip();
                    if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")) {
                        continue;
                    }
                    for (Pattern around : AROUND) {
                        Matcher found = around.matcher(lines.get(i));
                        if (found.find()) {
                            breaches.add("%s:%d — %s".formatted(
                                    MAIN.relativize(source), i + 1, found.group()));
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100, "the walk found barely any sources; the path is probably wrong");
        assertTrue(breaches.isEmpty(),
                "These lines read a save directly; they belong behind platform.Nbt:\n  "
                        + String.join("\n  ", breaches));
    }
}
