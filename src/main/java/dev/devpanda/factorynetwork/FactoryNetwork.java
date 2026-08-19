package dev.devpanda.factorynetwork;

import net.neoforged.fml.common.Mod;

/**
 * Einstiegspunkt der Mod.
 *
 * <p>Die eigentliche Arbeit liegt in den Unterpaketen: {@code lang} enthält
 * Manifold — Lexer, Parser und Prüfung —, {@code network} das Netzwerk aus
 * Connectoren, und {@code runtime} die Ausführung von Workern und Abläufen.
 */
@Mod(FactoryNetwork.MOD_ID)
public final class FactoryNetwork {
    public static final String MOD_ID = "factorynetwork";

    public FactoryNetwork() {
    }
}
