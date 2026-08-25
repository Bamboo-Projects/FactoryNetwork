package dev.devpanda.factorynetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Die Einstellungen der Mod.
 *
 * <p>Bis hierher hatte sie keine — und damit war jede Grenze für Nutzercode
 * eine Zahl im Quelltext. Für den Serverbetreiber eines Packs ist das die
 * falsche Stelle: Er kennt seine Spieler und seine Hardware, die Mod nicht.
 *
 * <p><b>Nur Serverseite, und nur Grenzen.</b> Was hier steht, entscheidet
 * über die Welt und nicht über den Bildschirm; ein Clientteil kommt, wenn es
 * etwas gibt, das ihn braucht (siehe {@code entscheidungen.md}, „Die Brücke
 * zu VS Code"). Leere Abschnitte auf Vorrat wären Fragen an den Betreiber,
 * die niemand beantworten kann.
 *
 * <p>Die Zahlen der Bereitschaft — was ein Connector an Strom kostet, wie
 * viele Kanäle ein Kabel trägt — stehen bewusst nicht hier. Sie sind
 * Spielinhalt und gehören zum Ausgleich der Mod; wer sie ändert, ändert das
 * Spiel und nicht seine Serverlast.
 */
public final class FnConfig {

    /**
     * Wie viele Schritte ein Durchlauf darf.
     *
     * <p>Die Zahl, die vorher als {@code MAX_STEPS} im Interpreter stand.
     * Eine Konfiguration darf das Spiel nicht nebenbei ändern: Wer nichts
     * einstellt, bekommt genau das, was die Mod vorher tat.
     */
    public static final int DEFAULT_STEP_BUDGET = 10_000;

    /** Und wie weit der Aufbau des Netzgraphen sucht. */
    public static final int DEFAULT_NETWORK_NODES = 4_096;

    /**
     * Wie viele Rezepte tief eine Fertigung sucht.
     *
     * <p>Acht Ebenen reichen für alles, was ein Pack an Ketten kennt — Erz zu
     * Barren zu Platte zu Bauteil zu Maschine sind fünf. Wer tiefer sucht,
     * findet keine neuen Wege, sondern nur längere.
     */
    public static final int DEFAULT_CRAFTING_DEPTH = 8;

    /** Und wie viele Bedarfe sie dabei ansehen darf. */
    public static final int DEFAULT_CRAFTING_BUDGET = 512;

    private static final ModConfigSpec.IntValue STEP_BUDGET;
    private static final ModConfigSpec.IntValue NETWORK_NODES;
    private static final ModConfigSpec.IntValue CRAFTING_DEPTH;
    private static final ModConfigSpec.IntValue CRAFTING_BUDGET;
    private static final ModConfigSpec.EnumValue<FnProtection.Mode> PROTECTION;

    public static final ModConfigSpec SERVER_SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Grenzen für das, was Programme im Spiel tun dürfen.",
                        "Sie schützen den Server vor einer Endlosschleife im Code eines",
                        "Spielers — nicht vor einem teuren Programm, das seine Arbeit tut.")
                .push("limits");
        STEP_BUDGET = builder
                .comment("Wie viele Anweisungen ein einzelner Durchlauf ausführen darf,",
                        "bevor er mit einer Meldung abbricht. Eine Endlosschleife läuft",
                        "damit höchstens so lange. Wer sie erhöht, verlängert im",
                        "Ernstfall den Serverstillstand.")
                .defineInRange("stepBudget", DEFAULT_STEP_BUDGET, 100, 10_000_000);
        NETWORK_NODES = builder
                .comment("Wie viele Blöcke der Aufbau des Netzes höchstens besucht.",
                        "Ein Netz darüber hinaus wird abgeschnitten und meldet das.",
                        "Die Zahl begrenzt die Suche, nicht die Zahl der Geräte —",
                        "dafür gibt es die Kanäle.")
                .defineInRange("networkNodes", DEFAULT_NETWORK_NODES, 256, 1_000_000);
        CRAFTING_DEPTH = builder
                .comment("Wie viele Rezepte tief ein Fertigungsauftrag sucht, wenn eine",
                        "Zutat fehlt. Bei 1 baut das Netz nur aus dem, was dasteht.",
                        "Was jenseits der Grenze liegt, steht als fehlend im Auftrag —",
                        "abgebrochen wird nichts.")
                .defineInRange("craftingDepth", DEFAULT_CRAFTING_DEPTH, 1, 64);
        CRAFTING_BUDGET = builder
                .comment("Wie viele Bedarfe eine solche Suche ansehen darf. Die Grenze",
                        "greift bei Rezeptbäumen, die sich in viele erlaubte Sorten",
                        "verzweigen — dort wächst die Suche schneller als ihre Tiefe.")
                .defineInRange("craftingBudget", DEFAULT_CRAFTING_BUDGET, 16, 100_000);
        builder.pop();
        builder.comment("Wer eine fremde Fabrik umbauen darf.")
                .push("protection");
        PROTECTION = builder
                .comment("OFF: jeder darf — der Stand vor dieser Einstellung.",
                        "OWNER: nur wer den Controller gesetzt hat, und Operatoren.",
                        "OPS: nur Operatoren.",
                        "Betroffen ist, was eine Anlage umbaut: ein Programm",
                        "übernehmen, einen Entwurf speichern, einen Fertigungsauftrag",
                        "abbrechen. Zusehen und Knöpfe drücken bleibt allen offen —",
                        "das ist Benutzen und nicht Umbauen.",
                        "Die Beschriftungspistole ist nicht dabei: Sie ändert die Welt,",
                        "und dafür gibt es Schutzmods.")
                .defineEnum("programs", FnProtection.Mode.OFF);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private FnConfig() {
    }

    /**
     * Wie viele Schritte ein Durchlauf darf.
     *
     * <p><b>Mit Rückfall auf die Vorgabe.</b> Ein Einheitstest lädt keine
     * Konfigurationsdatei, ein Datengenerator auch nicht, und ein Wert, der
     * dann wirft, macht aus einer Einstellung einen Absturz an Stellen, die
     * mit Einstellungen nichts zu tun haben.
     */
    public static int stepBudget() {
        return SERVER_SPEC.isLoaded() ? STEP_BUDGET.get() : DEFAULT_STEP_BUDGET;
    }

    /** Wie weit der Aufbau des Netzgraphen sucht. */
    public static int networkNodes() {
        return SERVER_SPEC.isLoaded() ? NETWORK_NODES.get() : DEFAULT_NETWORK_NODES;
    }

    /** Wie viele Rezepte tief eine Fertigung sucht. */
    public static int craftingDepth() {
        return SERVER_SPEC.isLoaded() ? CRAFTING_DEPTH.get() : DEFAULT_CRAFTING_DEPTH;
    }

    /** Und wie viele Bedarfe sie dabei ansieht. */
    public static int craftingBudget() {
        return SERVER_SPEC.isLoaded() ? CRAFTING_BUDGET.get() : DEFAULT_CRAFTING_BUDGET;
    }

    /**
     * Wer eine fremde Fabrik umbauen darf.
     *
     * <p>Ohne geladene Konfiguration: jeder. Eine Sperre, die aus einem
     * fehlenden Wert entsteht, ist die falsche Richtung — sie stünde genau
     * dann da, wenn niemand sie eingerichtet hat.
     */
    public static FnProtection.Mode protection() {
        return SERVER_SPEC.isLoaded() ? PROTECTION.get() : FnProtection.Mode.OFF;
    }
}
