package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.lang.Signatures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Completions for the editor.
 *
 * <p>The point where it is decided whether the language is usable. In a pack
 * with twenty thousand kinds of item an alphabetical list is worthless — it
 * has to select, not enumerate.
 *
 * <p>That is why it distinguishes by the position in the code: after
 * {@code from} and {@code to} come devices, after {@code filter} items, at
 * the start of a line in a worker its arguments. A completion that shows the
 * same thing everywhere helps nowhere.
 *
 * <p><b>What may stand where is not here but in {@link Signatures}.</b> This
 * class only decides which position is currently in play, and fetches from
 * there what fits it. That separation is the reason the same information also
 * feeds the hint line in the editor — and one day a language server for VS
 * Code.
 */
public final class Completions {

    /** No more than that is shown; anything else is scrolling for nothing. */
    private static final int MAX = 8;

    /**
     * A complete list is not truncated.
     *
     * <p>The limit applies to the names from the world: twenty thousand
     * items, three hundred devices — there truncating is right. What the
     * language itself provides is finite and must stand in full. It came to
     * light at the ninth device member: {@code click} dropped off the end,
     * and the editor hid a capability instead of scrolling.
     */
    private static List<Entry> whole(List<Entry> entries) {
        return entries;
    }

    /**
     * A completion.
     *
     * @param text   what appears in the list and what is compared against
     * @param insert what is inserted when it is accepted
     * @param kind   the colour
     * @param detail what belongs after it — {@code string expr} after
     *               {@code row}. <b>This is the answer to "what do I have to
     *               give and where".</b> Without it a completion is a word
     *               without a form, and you end up laying the docs beside it.
     */
    public record Entry(String text, String insert, Kind kind, String detail) {

        public Entry(String text, String insert, Kind kind) {
            this(text, insert, kind, "");
        }

        public enum Kind { KEYWORD, CONNECTOR, ITEM, TAG, BUILTIN }
    }

    /** The same list VS Code also gets — it lives in Signatures. */
    private static final List<String> DECLARATIONS = Signatures.DECLARATIONS;

    /**
     * A completion that brings its own brackets.
     *
     * <p><b>Noticed on the first playthrough:</b> the completion inserted the
     * name and nothing more, and you typed {@code ()} in by hand every time —
     * for {@code log}, for {@code count()}, on every call.
     *
     * <p>Whether any belong is held in the <b>shape</b> and not in a second
     * list: {@code redstone() int} has them, {@code power int} does not. That
     * way the answer cannot diverge from the truth, and a new member brings
     * it along by itself.
     */
    private static Entry callable(Signatures.Member member, Entry.Kind kind) {
        boolean call = member.shape().contains("(");
        return new Entry(member.name(), call ? member.name() + "()" : member.name(),
                kind, member.shape());
    }

    /**
     * The built-in names the interpreter <b>really</b> knows.
     *
     * <p>The language also parses {@code world}, {@code network},
     * {@code workers} and {@code multiblocks} — it can evaluate none of them.
     * Offering them meant: whoever writes {@code to network} gets "Only a
     * name works as a target", a message that contradicts the completion that
     * triggered it.
     *
     * <p>They come back as soon as they do something. {@code crafting} went
     * this way and lives in {@link #SOURCES} — as a source, because a target
     * it is not.
     */
    private static final List<String> BUILTINS = List.of("storage");

    /** What only works as a source — {@code from crafting} orders what is missing. */
    private static final List<String> SOURCES = List.of("crafting");

    /**
     * What fits at this position.
     *
     * @param lines      all lines of the program
     * @param lineIndex  line the cursor is on
     * @param column     column of the cursor
     */
    public static List<Entry> at(List<String> lines, int lineIndex, int column) {
        String line = lines.get(lineIndex);
        String upToCursor = line.substring(0, Math.min(column, line.length()));
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length()).trim();

        List<Entry> entries = new ArrayList<>();

        // "display halle " — the name is there, next comes the brace.
        if (afterDeclarationName(upToCursor, prefix)) {
            return List.of();
        }

        // After a dot behind a device name: what a device has.
        //
        // Before the check for the position in the argument list, because
        // "to crusher_1." would otherwise be read as a started target name —
        // and then the connectors would stand there again.
        //
        // <b>And the dot ends the list in every case.</b> If no known
        // connector stands before it, there is simply nothing: before, this
        // case fell through as far as the expression position and offered
        // "storage, crafting, world …" — after a dot none of those makes a
        // sentence. After "…items()." there is a list and not a device.
        // Checked before afterDot, because there a name before the dot is
        // expected — here there is a closing bracket.
        //
        // The editor knows no types, but this one case it recognizes from the
        // text, and it is the only one that occurs today.
        if (afterListCall(upToCursor)) {
            for (Signatures.Member candidate : Signatures.LIST_MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(callable(candidate, Entry.Kind.BUILTIN));
                }
            }
            return whole(entries);
        }
        if (afterDot(upToCursor)) {
            // <b>it is not a device.</b> It stands for an entry, and other
            // things stand on it. Before, it fell through memberPrefix, which
            // only lets known connectors pass — so after "it." nothing came
            // at all.
            String receiver = wordBeforeDot(upToCursor);
            // The network is not a device: on it stand power and capacity,
            // and nothing else. Before the check for a connector, because
            // "network" is not one and would otherwise fall to the empty
            // list.
            if ("network".equals(receiver)) {
                for (Signatures.Member candidate : Signatures.NETWORK_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(callable(candidate, Entry.Kind.BUILTIN));
                    }
                }
                return whole(entries);
            }
            // A group is not a device: on it stand members and send, and
            // nothing else.
            if (isGroupName(receiver, lines)) {
                for (Signatures.Member candidate : Signatures.GROUP_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(callable(candidate, Entry.Kind.BUILTIN));
                    }
                }
                return whole(entries);
            }
            if ("it".equals(wordBeforeDot(upToCursor))) {
                for (Signatures.Member candidate : Signatures.ENTRY_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(callable(candidate, Entry.Kind.BUILTIN));
                    }
                }
                return whole(entries);
            }
            if (memberPrefix(upToCursor) == null) {
                return List.of();
            }
            for (Signatures.Member candidate : Signatures.MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(callable(candidate, Entry.Kind.BUILTIN));
                }
            }
            return whole(entries);
        }

        // After display: the walls that stand in the world.
        //
        // "display NAME { … }" requires the name the board carries. Whoever
        // spells it wrong gets not a program that fails to compile, but a
        // wall that stays black — the bug you hunt for the longest.
        if (before.endsWith("display") && indentOf(line) == 0) {
            addDisplays(entries, prefix);
            return limit(entries);
        }

        // After on: the events you can listen for.
        //
        // The four of the network stand in no file — whoever doesn't know
        // them by heart looks them up in the docs. And a mistyped name is
        // especially costly here: an on needs no declaration, the block is
        // accepted and never runs. The editor can prevent this error instead
        // of reporting it after the fact.
        if (before.endsWith("on") && indentOf(line) == 0) {
            addEvents(entries, prefix, lines);
            return limit(entries);
        }

        // <b>The core.</b> If the cursor is behind a keyword with a shape,
        // the completion follows the position that is currently in play — and
        // no longer the block. Before, the editor offered "title, row, text
        // …" again behind "row", exactly what does not belong there.
        Signatures.Where where = whereAt(lines, lineIndex, column);
        if (where != null) {
            return limit(forSlot(where, entries, prefix, lines));
        }

        // A started selection expression.
        if (prefix.contains(":")) {
            addItems(entries, prefix);
            return limit(entries);
        }
        return limit(structural(entries, lines, lineIndex, prefix));
    }

    /**
     * Where the cursor sits within an argument list, or {@code null}.
     *
     * <p>Public, because the editor has to know the same: from it it draws
     * the hint line with the whole shape and the marked position.
     */
    public static Signatures.Where whereAt(List<String> lines, int lineIndex, int column) {
        String line = lines.get(lineIndex);
        String upToCursor = line.substring(0, Math.min(column, line.length()));
        return Signatures.at(enclosingBlock(lines, lineIndex), upToCursor);
    }

    /** What may stand at this position in an argument list. */
    private static List<Entry> forSlot(Signatures.Where where, List<Entry> entries,
                                       String prefix, List<String> lines) {
        Signatures.Slot slot = where.slot();
        if (slot == null) {
            // The argument list is full. Suggesting nothing is the answer
            // here: the line is done.
            return entries;
        }
        switch (slot.kind()) {
            case TARGET -> {
                addConnectors(entries, prefix);
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
                // crafting is a source and not a target: crafting goes into
                // storage, and from there a second worker picks it up.
                // Suggesting it at "to" led into a message that contradicts
                // the completion that triggered it.
                if ("from".equals(where.signature().keyword())) {
                    addAll(entries, SOURCES, prefix, Entry.Kind.BUILTIN);
                }
            }
            case EXPR -> {
                // A started selection expression is an expression too.
                // Without this branch "let x = minecraft:" would fall
                // through: the check for the colon is further down and is not
                // reached here at all.
                if (prefix.contains(":")) {
                    addItems(entries, prefix);
                    return entries;
                }
                addConnectors(entries, prefix);
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
            }
            case SELECTION -> {
                // The templates first: whoever has created one usually means
                // it here and not the single item.
                addTemplates(entries, prefix, lines);
                addItems(entries, prefix);
                // The two without a colon don't show up in the item search —
                // they have to be offered specially, or nobody finds them.
                addAll(entries, List.of("power", "all"), prefix, Entry.Kind.KEYWORD);
            }
            case STRATEGY -> addAll(entries, Signatures.STRATEGIES, prefix,
                    Entry.Kind.KEYWORD);
            case MEMBERS -> addConnectors(entries, prefix);
            case FUNCTION -> addFunctions(entries, prefix, lines);
            case EVENT -> addEvents(entries, prefix, lines);
            case LITERAL -> {
                addAll(entries, List.of(slot.label()), prefix, Entry.Kind.KEYWORD);
                // If this position may be dropped, the word after it is
                // allowed too: after "move 64 " comes "from", or "to"
                // directly.
                if (slot.optional()) {
                    Signatures.Slot next =
                            where.signature().slotAt(where.slotIndex() + 2);
                    if (next != null && next.kind() == Signatures.Kind.LITERAL) {
                        addAll(entries, List.of(next.label()), prefix,
                                Entry.Kind.KEYWORD);
                    }
                }
            }
            // A name that only comes into being here can't be suggested.
            case NEW_NAME -> { }
            // For a text, a number or a duration there is nothing to suggest.
            // The hint line still says what goes here — that is what it is
            // for.
            default -> { }
        }
        return entries;
    }

    /** The project's events — for {@code emit}. */
    private static void addEvents(List<Entry> entries, String prefix, List<String> lines) {
        // The four of the network first: they stand in no file and would
        // otherwise be found nowhere — you have to know them by heart or look
        // them up in the docs.
        addAll(entries,
                List.copyOf(new java.util.TreeSet<>(
                        dev.devpanda.factorynetwork.lang.BuiltinEvents.ARITY.keySet())),
                prefix, Entry.Kind.KEYWORD);
        addDeclaredNames(entries, prefix, lines, "event ", "event");
    }

    /** The project's functions — for a display's button. */
    private static void addFunctions(List<Entry> entries, String prefix, List<String> lines) {
        addDeclaredNames(entries, prefix, lines, "fn ", "fn");
    }

    /**
     * The same functions, but as a call.
     *
     * <p>The difference from {@link #addFunctions} is the position: at a
     * display's {@code button} stands the <b>name</b> of a function, in a
     * body stands its <b>call</b>. A completion that inserts the same thing
     * both times is wrong in one of the two places.
     */
    private static void addCalls(List<Entry> entries, String prefix, List<String> lines) {
        List<Entry> named = new ArrayList<>();
        addDeclaredNames(named, prefix, lines, "fn ", "fn");
        for (Entry entry : named) {
            entries.add(new Entry(entry.text(), entry.text() + "()",
                    entry.kind(), entry.detail()));
        }
    }

    /** Is a group in the project named this? */
    private static boolean isGroupName(String word, List<String> lines) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        List<Entry> groups = new ArrayList<>();
        addDeclaredNames(groups, "", lines, "group ", "group");
        return groups.stream().anyMatch(entry -> entry.text().equals(word));
    }

    /** The project's filter templates. */
    private static void addTemplates(List<Entry> entries, String prefix, List<String> lines) {
        addDeclaredNames(entries, prefix, lines, "filter ", "filter");
    }

    /**
     * Names the project assigns — from <b>all</b> files.
     *
     * <p>All files share one namespace: an {@code fn} in {@code werte.mf} is
     * called in {@code main.mf} without an import standing anywhere. Whoever
     * reads only the open file fails to suggest exactly the names the editor
     * is most needed for — those from a file you do <b>not</b> currently have
     * in front of you.
     *
     * <p><b>The open file still comes from the text</b> and not from the
     * draft on the client: it holds what is being typed right now, and a
     * function should be callable as soon as its name is there — not only
     * after saving.
     *
     * <p>Via the text and not via the tree, as in
     * {@code Definitions.references}: unfinished code has no tree, and that
     * is exactly where completion should help.
     */
    private static void addDeclaredNames(List<Entry> entries, String prefix,
                                         List<String> lines, String keyword, String detail) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        collectNames(seen, lines, keyword);

        dev.devpanda.factorynetwork.lang.Project project =
                dev.devpanda.factorynetwork.client.ClientProjectState.draft();
        for (String file : project.names()) {
            collectNames(seen, List.of(project.source(file).split("\n", -1)), keyword);
        }

        for (String name : seen) {
            if (matches(name, prefix)) {
                entries.add(new Entry(name, name, Entry.Kind.KEYWORD, detail));
            }
        }
    }

    /** The first of the two brackets, or -1. */
    private static int firstOf(String text, char one, char other) {
        int first = text.indexOf(one);
        int second = text.indexOf(other);
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static void collectNames(java.util.Set<String> into, List<String> lines,
                                     String keyword) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(keyword)) {
                continue;
            }
            // The name ends at the bracket — for a function at the round one,
            // for a template at the curly one. Without the second, the
            // template was named "erze {".
            int open = firstOf(trimmed, '(', '{');
            String name = (open < 0 ? trimmed.substring(keyword.length())
                    : trimmed.substring(keyword.length(), open)).trim();
            // An "fn" in a multiblock counts too: it is a declaration like
            // any other, just indented.
            if (!name.isEmpty()) {
                into.add(name);
            }
        }
    }

    /** Completions that depend on the position in the structure. */
    private static List<Entry> structural(List<Entry> entries, List<String> lines,
                                          int lineIndex, String prefix) {
        // At the top level stand declarations. Most of them open a block and
        // have no shape; global is the exception and brings its own.
        if (indentOf(lines.get(lineIndex)) == 0) {
            for (String word : DECLARATIONS) {
                if (!matches(word, prefix)) {
                    continue;
                }
                Signatures.Signature shape = Signatures.find(null, word);
                String detail = shape == null ? ""
                        : shape.shape().substring(word.length()).trim();
                entries.add(new Entry(word, word, Entry.Kind.KEYWORD, detail));
            }
            return entries;
        }
        // In a block: its arguments or its statements, each with its shape.
        String block = enclosingBlock(lines, lineIndex);
        List<Signatures.Signature> shapes = Signatures.forBlock(block);
        if (!shapes.isEmpty()) {
            for (Signatures.Signature signature : shapes) {
                if (matches(signature.keyword(), prefix)) {
                    String detail = signature.shape()
                            .substring(signature.keyword().length()).trim();
                    entries.add(new Entry(signature.keyword(), signature.keyword(),
                            Entry.Kind.KEYWORD, detail));
                }
            }
            // In a template every line is a selection. Without that only
            // "except" would stand there to choose — the word for the
            // exception, but nothing for the normal case.
            if ("filter".equals(block)) {
                addItems(entries, prefix);
                return entries;
            }
            if (isCodeBlock(block)) {
                // In a function an expression is also a statement — so the
                // stocks and the connectors belong there too. And the three
                // words without a shape of their own.
                addAll(entries, List.of("else", "break", "continue"), prefix,
                        Entry.Kind.KEYWORD);
                // The functions without a receiver: log and the three levels
                // beside it. They stood nowhere and were therefore never
                // suggested — you had to know they exist.
                for (Signatures.Member function : Signatures.FREE_FUNCTIONS) {
                    if (matches(function.name(), prefix)) {
                        entries.add(callable(function, Entry.Kind.BUILTIN));
                    }
                }
                // The project's own functions. They didn't stand here, and
                // whoever wanted to call their own typed the name out in full
                // — even though the editor knows it, because it has long
                // offered it in the jump target and in VS Code.
                addCalls(entries, prefix, lines);
                addConnectors(entries, prefix);
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
            }
            return entries;
        }
        // Outside any known block: connectors and built-ins.
        addConnectors(entries, prefix);
        addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
        return entries;
    }

    /** Does this block contain statements instead of fixed arguments? */
    private static boolean isCodeBlock(String declaration) {
        return "fn".equals(declaration) || "on".equals(declaration)
                || "multiblock".equals(declaration);
    }

    /**
     * Which declaration encloses the cursor, or {@code null}.
     *
     * <p>Backwards to the first line that starts at column zero with a
     * declaration word — that is enough, because declarations don't nest.
     *
     * <p>Before, this spot only asked "is this a worker", and everything else
     * fell into the same pot: in a display you were offered a function's
     * statements. But every kind of block has its own arguments, and outside
     * them they are wrong.
     */
    static String enclosingBlock(List<String> lines, int lineIndex) {
        for (int i = lineIndex; i >= 0; i--) {
            String line = lines.get(i);
            if (indentOf(line) != 0) {
                continue;
            }
            String trimmed = line.trim();
            for (String declaration : DECLARATIONS) {
                if (trimmed.startsWith(declaration + " ")) {
                    return declaration;
                }
            }
        }
        return null;
    }

    /**
     * Is there already a named declaration on this line?
     *
     * <p>Then the curly brace comes next, and for that there is nothing to
     * suggest. As long as the name is still being typed, this doesn't hold —
     * you can tell by the fact that behind the keyword stands exactly the
     * word being started.
     */
    private static boolean afterDeclarationName(String upToCursor, String prefix) {
        String trimmed = upToCursor.trim();
        for (String declaration : DECLARATIONS) {
            if (trimmed.startsWith(declaration + " ")) {
                return !trimmed.substring(declaration.length()).trim().equals(prefix);
            }
        }
        return false;
    }

    /**
     * Is the cursor behind a dot that follows a name?
     *
     * <p>{@code 3.5} doesn't count: before it stands a number and not a name,
     * and so it is not a dot access.
     */
    private static boolean afterDot(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        if (!before.endsWith(".")) {
            return false;
        }
        String name = currentWord(before.substring(0, before.length() - 1));
        return !name.isEmpty() && !Character.isDigit(name.charAt(0));
    }

    /**
     * Is there a call before the dot that returns a list?
     *
     * <p>Today there is exactly one: {@code items()}. Recognizing this from
     * the text is crude, but more honest than the alternative — the editor
     * knows no types, and building half a type checker for completion would
     * be the wrong answer to a question that can be answered with six
     * characters.
     */
    private static boolean afterListCall(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        return before.endsWith("items().");
    }

    /**
     * The device name before the dot, or {@code null}.
     *
     * <p>Only if a connector really stands before it: {@code storage.} is
     * something else, and what nobody has named that has no members either.
     */
    private static String memberPrefix(String upToCursor) {
        if (!afterDot(upToCursor)) {
            return null;
        }
        String name = wordBeforeDot(upToCursor);
        return ClientNetworkState.connectors().contains(name) ? name : null;
    }

    /** The word before the dot, without checking whether it means anything. */
    private static String wordBeforeDot(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        return currentWord(before.substring(0, before.length() - 1));
    }

    /**
     * What a device can do, in one line.
     *
     * <p>Summarized across all sides and not per side: in the completion list
     * there is room for a few words, and the question there is "is this any
     * use at all". Which side exactly, hovering shows.
     */
    public static String abilities(DeviceProfile profile) {
        return profile.abilities();
    }

    /**
     * The connectors, each with the machine behind it.
     *
     * <p>The {@code detail} stood empty here for a long time. It is the
     * cheapest spot with the greatest benefit: it appears in every completion
     * list without anyone having to do anything for it.
     */
    private static void addConnectors(List<Entry> entries, String prefix) {
        for (String connector : ClientNetworkState.connectors()) {
            if (!matches(connector, prefix)) {
                continue;
            }
            DeviceProfile profile = ClientNetworkState.profile(connector);
            String detail = profile.reachable()
                    ? net.minecraft.network.chat.Component
                            .translatable(profile.descriptionId()).getString()
                            + " · " + abilities(profile)
                    : "";
            entries.add(new Entry(connector, connector, Entry.Kind.CONNECTOR, detail));
        }
    }

    private static void addDisplays(List<Entry> entries, String prefix) {
        for (String display : ClientNetworkState.displays()) {
            if (matches(display, prefix)) {
                entries.add(new Entry(display, display, Entry.Kind.CONNECTOR));
            }
        }
    }

    /**
     * Items from the registry.
     *
     * <p>Only from three characters on, and only the first hits: running over
     * twenty thousand entries while someone is typing is exactly the spot
     * where an editor starts to stutter.
     */
    private static void addItems(List<Entry> entries, String prefix) {
        String search = prefix.startsWith("item:") ? prefix.substring(5)
                : prefix.startsWith("tag:") ? prefix.substring(4)
                : prefix;
        boolean asTag = prefix.startsWith("tag:");
        if (search.length() < 2 && !prefix.contains(":")) {
            // From the registry and not from a list here: since 26.08. other
            // mods may register their own kinds, and what the editor offers
            // must be the same as what the compiler accepts.
            //
            // The list here stood four entries long and didn't know
            // "chemical:" — even though it has existed since 26.08. That is
            // exactly what happens with a copy that nobody keeps in sync.
            //
            // "tag:" and "fluidtag:" are not kinds but spellings for two of
            // them; selectorPrefixes() brings both together.
            new java.util.TreeSet<>(dev.devpanda.factorynetwork.runtime.ResourceKinds
                    .selectorPrefixes())
                    .forEach(known -> entries.add(
                            new Entry(known + ":", known + ":", Entry.Kind.KEYWORD)));
            return;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        int found = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (found >= MAX) {
                break;
            }
            String path = id.getPath();
            if (!path.contains(needle)) {
                continue;
            }
            String written = "minecraft".equals(id.getNamespace())
                    ? "item:" + path
                    : "item:" + id.getNamespace() + "/" + path;
            entries.add(new Entry(written, written,
                    asTag ? Entry.Kind.TAG : Entry.Kind.ITEM));
            found++;
        }
    }

    private static void addAll(List<Entry> entries, List<String> candidates, String prefix,
                               Entry.Kind kind) {
        for (String candidate : candidates) {
            if (matches(candidate, prefix)) {
                entries.add(new Entry(candidate, candidate, kind));
            }
        }
    }

    /**
     * Does this completion fit the word being started?
     *
     * <p><b>What already stands there in full is not a completion.</b>
     * Whoever has typed {@code halle} to the end was, until just now, offered
     * {@code halle} — a list with one entry that changes nothing, and it
     * covers the line below.
     */
    private static boolean matches(String candidate, String prefix) {
        if (candidate.equalsIgnoreCase(prefix)) {
            return false;
        }
        return prefix.isEmpty()
                || candidate.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static List<Entry> limit(List<Entry> entries) {
        return entries.size() <= MAX ? entries : entries.subList(0, MAX);
    }

    /** The word being started, to the left of the cursor. */
    public static String currentWord(String upToCursor) {
        int start = upToCursor.length();
        while (start > 0) {
            char c = upToCursor.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/' || c == '*') {
                start--;
            } else {
                break;
            }
        }
        return upToCursor.substring(start);
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private Completions() {
    }
}
