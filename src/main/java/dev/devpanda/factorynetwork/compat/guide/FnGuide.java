package dev.devpanda.factorynetwork.compat.guide;

import dev.devpanda.factorynetwork.FactoryNetwork;
import guideme.Guide;
import net.minecraft.resources.ResourceLocation;

/**
 * The in-game manual.
 *
 * <p><b>Why GuideME and not Patchouli:</b> the content is Markdown — the same
 * notation as everything in {@code docs/} — and it is rendered in-game. That
 * resolves the contradiction every documentation decision hangs on: Markdown
 * is quick to write and to maintain, but no one reads it in-game. Patchouli's
 * JSON would be the opposite trade.
 *
 * <p>On top of that comes what would have taken weeks to build ourselves:
 * full-text search, a press of <b>G</b>, interactive 3D scenes from structure
 * files, and a live preview while writing. The scenes are the real win for
 * this mod — a network is a spatial thing, and being able to rotate the layout
 * in the book conveys in ten seconds what a paragraph cannot.
 *
 * <p>GuideME comes from Applied Energistics, but runs without AE2 and is
 * licensed under LGPL-3.0. See {@code docs/entscheidungen.md}, "Player
 * documentation goes through GuideME".
 *
 * <p><b>Optional, like Jade:</b> the class is touched only when GuideME is
 * loaded — otherwise the mod starts without a manual, rather than not at all.
 */
public final class FnGuide {

    /** Where the pages live: {@code assets/factorynetwork/guide/…}. */
    private static final String FOLDER = "guide";

    private FnGuide() {
    }

    /** Creates the manual. Called from the mod constructor. */
    public static void register() {
        Guide.builder(ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "guide"))
                .folder(FOLDER)
                .defaultNamespace(FactoryNetwork.MOD_ID)
                .startPage(ResourceLocation.fromNamespaceAndPath(
                        FactoryNetwork.MOD_ID, "index.md"))
                .build();
    }
}
