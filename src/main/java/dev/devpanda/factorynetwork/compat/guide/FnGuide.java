package dev.devpanda.factorynetwork.compat.guide;

import dev.devpanda.factorynetwork.FactoryNetwork;
import guideme.Guide;
import net.minecraft.resources.ResourceLocation;

/**
 * Das Handbuch im Spiel.
 *
 * <p><b>Warum GuideME und nicht Patchouli:</b> Der Inhalt ist Markdown — also
 * dieselbe Schreibweise wie alles in {@code docs/} —, gerendert wird er
 * drinnen. Das löst den Widerspruch, an dem jede Doku-Entscheidung hängt:
 * Markdown ist schnell zu schreiben und zu pflegen, aber im Spiel liest es
 * niemand. Patchoulis JSON wäre der umgekehrte Handel.
 *
 * <p>Dazu kommt, was selbst zu bauen Wochen kostete: Volltextsuche, ein Griff
 * auf <b>G</b>, interaktive 3D-Szenen aus Structure-Dateien und eine
 * Live-Vorschau beim Schreiben. Die Szenen sind für diese Mod der eigentliche
 * Gewinn — ein Netz ist eine räumliche Sache, und wer den Aufbau im Buch
 * drehen kann, versteht in zehn Sekunden, wofür ein Absatz nicht reicht.
 *
 * <p>GuideME stammt von Applied Energistics, läuft aber ohne AE2 und steht
 * unter LGPL-3.0. Siehe {@code docs/entscheidungen.md}, „Die Dokumentation
 * für Spieler läuft über GuideME".
 *
 * <p><b>Optional wie Jade:</b> Die Klasse wird nur angefasst, wenn GuideME
 * geladen ist — sonst startet die Mod ohne Handbuch, statt gar nicht.
 */
public final class FnGuide {

    /** Wo die Seiten liegen: {@code assets/factorynetwork/guide/…}. */
    private static final String FOLDER = "guide";

    private FnGuide() {
    }

    /** Legt das Handbuch an. Aufgerufen aus dem Mod-Konstruktor. */
    public static void register() {
        Guide.builder(ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "guide"))
                .folder(FOLDER)
                .defaultNamespace(FactoryNetwork.MOD_ID)
                .startPage(ResourceLocation.fromNamespaceAndPath(
                        FactoryNetwork.MOD_ID, "index.md"))
                .build();
    }
}
