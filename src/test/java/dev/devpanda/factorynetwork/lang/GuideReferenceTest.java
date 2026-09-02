package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Builds the manual's reference page from {@link Signatures}.
 *
 * <p><b>Why generated and not written by hand:</b> a reference is the
 * page nobody reads until it is needed — and then it has to be
 * right. Maintained by hand it would, by the third new entry, be the
 * version from the week before last, and you would fail to notice exactly
 * when you rely on it.
 *
 * <p>The same procedure as with {@code signatures.json}: the check builds the
 * page from the code, compares it with the checked-in one, rewrites it on
 * any difference and fails anyway — the new version is then already
 * there and waiting to be checked in.
 *
 * <p>The prose in between lives in the generator too. It belongs to the page and
 * not in a second file that would then end up being kept in step by hand
 * after all.
 */
class GuideReferenceTest {

    private static final Path TARGET = Path.of("src", "main", "resources", "assets",
            "factorynetwork", "guide", "referenz.md");

    @Test
    @DisplayName("The reference page is the one from the code")
    void thereferencePageMatchesTheTable() throws IOException {
        String expected = build();
        // Line endings normalised, for the same reason as when exporting the
        // signature table: Git checks out CRLF on Windows.
        String actual = Files.exists(TARGET)
                ? Files.readString(TARGET, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";

        if (!expected.equals(actual)) {
            Files.createDirectories(TARGET.getParent());
            Files.writeString(TARGET, expected, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual,
                "referenz.md war nicht auf dem Stand und wurde neu geschrieben — "
                        + "bitte einchecken.");
    }

    private static String build() {
        StringBuilder out = new StringBuilder();
        out.append("""
                ---
                navigation:
                  title: Referenz
                  position: 90
                ---

                # Referenz

                Alles, was die Sprache kennt, auf einer Seite. Zum Nachschlagen, nicht
                zum Lesen — wie etwas gemeint ist, steht bei *Programmieren*.

                Diese Seite wird aus dem Quelltext erzeugt. Was hier steht, kann die
                Mod auch.

                """);

        signatures(out, "Oberste Ebene", """
                Was ganz links in einer Datei stehen darf.
                """, Signatures.TOP_LEVEL);

        signatures(out, "In einem `worker`", """
                Ein Worker ist eine dauerhafte Zusage: Solange er dasteht, hält das Netz
                sie ein.
                """, Signatures.WORKER);

        signatures(out, "In einer `group`", """
                Eine Gruppe fasst Geräte zusammen. Wer dazugehört, entscheidet das Netz
                und nicht das Programm.
                """, Signatures.GROUP);

        signatures(out, "In einem `filter`", """
                Eine Auswahl mit Namen, überall verwendbar, wo eine Auswahl steht.
                """, Signatures.FILTER);

        signatures(out, "Anweisungen", """
                Was in einer Funktion oder einem `on`-Block steht.
                """, Signatures.STATEMENT);

        signatures(out, "Auf einer Anzeige", """
                Je Zeile eine Angabe. Gezeichnet wird von oben nach unten.
                """, Signatures.DISPLAY);

        members(out, "Freie Funktionen", """
                Ohne Punkt davor, überall aufrufbar.
                """, Signatures.FREE_FUNCTIONS);

        members(out, "Was ein Gerät hat", """
                Hinter dem Namen eines Connectors: `brecher_1.count(item:iron_ore)`.
                """, Signatures.MEMBERS);

        members(out, "Was an einer Liste steht", """
                `where` und `sort` werten ihren Ausdruck je Eintrag aus, mit `it` als
                diesem Eintrag.
                """, Signatures.LIST_MEMBERS);

        members(out, "Was am Netz steht", """
                Hinter `network`, ohne Klammern. Der Stand einer einzelnen Maschine ist
                `geraet.energy()`.
                """, Signatures.NETWORK_MEMBERS);

        members(out, "Was an einer Gruppe steht", """
                Hinter dem Namen einer Gruppe.
                """, Signatures.GROUP_MEMBERS);

        members(out, "Was an einem Posten steht", """
                Ein Posten ist ein Eintrag einer Bestandsliste — das, was `it` in einem
                `where` gerade ist.
                """, Signatures.ENTRY_MEMBERS);

        out.append("## Verteilstrategien\n\n")
                .append("Wohin ein Worker liefert, wenn das Ziel eine Gruppe ist.\n\n");
        for (String strategy : Signatures.STRATEGIES) {
            out.append("- `").append(strategy).append("`\n");
        }
        return out.toString();
    }

    private static void signatures(StringBuilder out, String title, String intro,
                                   List<Signatures.Signature> entries) {
        out.append("## ").append(title).append("\n\n").append(intro).append('\n');
        out.append("| Form | Bedeutung |\n|---|---|\n");
        for (Signatures.Signature entry : entries) {
            out.append("| `").append(entry.shape()).append("` | ")
                    .append(entry.help()).append(" |\n");
        }
        out.append('\n');
    }

    private static void members(StringBuilder out, String title, String intro,
                                List<Signatures.Member> entries) {
        out.append("## ").append(title).append("\n\n").append(intro).append('\n');
        out.append("| Form | Bedeutung |\n|---|---|\n");
        for (Signatures.Member entry : entries) {
            out.append("| `").append(entry.shape()).append("` | ")
                    .append(entry.help()).append(" |\n");
        }
        out.append('\n');
    }
}
