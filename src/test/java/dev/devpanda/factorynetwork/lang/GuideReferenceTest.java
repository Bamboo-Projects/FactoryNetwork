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
 * Erzeugt die Referenzseite des Handbuchs aus {@link Signatures}.
 *
 * <p><b>Warum erzeugt und nicht geschrieben:</b> Eine Referenz ist die
 * Seite, die niemand liest, bis sie gebraucht wird — und dann muss sie
 * stimmen. Von Hand gepflegt wäre sie nach der dritten neuen Angabe die
 * Fassung von vorletzter Woche, und man merkte es genau dann nicht, wenn man
 * sich darauf verlässt.
 *
 * <p>Dasselbe Verfahren wie bei {@code signatures.json}: Die Prüfung baut die
 * Seite aus dem Code, vergleicht sie mit der eingecheckten, schreibt sie bei
 * Abweichung neu und scheitert trotzdem — die neue Fassung liegt dann schon
 * da und will eingecheckt werden.
 *
 * <p>Der Fließtext dazwischen steht mit im Erzeuger. Er gehört zur Seite und
 * nicht in eine zweite Datei, die dann doch wieder von Hand nachgezogen
 * würde.
 */
class GuideReferenceTest {

    private static final Path TARGET = Path.of("src", "main", "resources", "assets",
            "factorynetwork", "guide", "referenz.md");

    @Test
    @DisplayName("Die Referenzseite ist die aus dem Code")
    void thereferencePageMatchesTheTable() throws IOException {
        String expected = build();
        // Zeilenenden angeglichen, aus demselben Grund wie beim Export der
        // Signaturtabelle: Git checkt unter Windows CRLF aus.
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
