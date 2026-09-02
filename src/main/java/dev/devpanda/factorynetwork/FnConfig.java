package dev.devpanda.factorynetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings.
 *
 * <p>Until now it had none — and so every limit for user code was a number in
 * the source. For the server operator of a pack that is the wrong place: they
 * know their players and their hardware, the mod does not.
 *
 * <p><b>Server side only, and limits only.</b> What stands here decides over
 * the world and not over the screen; a client part will come once there is
 * something that needs it (see {@code entscheidungen.md}, „Die Brücke zu
 * VS Code"). Empty sections kept in reserve would be questions to the operator
 * that no one can answer.
 *
 * <p>The provisioning numbers — what a connector costs in power, how many
 * channels a cable carries — deliberately do not stand here. They are game
 * content and belong to the mod's balance; whoever changes them changes the
 * game and not their server load.
 */
public final class FnConfig {

    /**
     * How many steps a run may take.
     *
     * <p>The number that previously stood as {@code MAX_STEPS} in the
     * interpreter. A configuration must not change the game as a side effect:
     * whoever sets nothing gets exactly what the mod did before.
     */
    public static final int DEFAULT_STEP_BUDGET = 10_000;

    /** And how far building the network graph searches. */
    public static final int DEFAULT_NETWORK_NODES = 4_096;

    /**
     * How many recipes deep a crafting run searches.
     *
     * <p>Eight levels are enough for anything a pack knows in the way of
     * chains — ore to ingot to plate to component to machine is five. Whoever
     * searches deeper finds no new paths, only longer ones.
     */
    public static final int DEFAULT_CRAFTING_DEPTH = 8;

    /** And how many demands it may look at while doing so. */
    public static final int DEFAULT_CRAFTING_BUDGET = 512;

    /**
     * How long a global list value may grow.
     *
     * <p>It is the only value a program can let grow in a loop and that
     * survives a restart. Without a cap it would be the only way to blow up a
     * world file with three lines of user code.
     */
    public static final int DEFAULT_GLOBAL_LIST = 256;

    private static final ModConfigSpec.IntValue STEP_BUDGET;
    private static final ModConfigSpec.IntValue NETWORK_NODES;
    private static final ModConfigSpec.IntValue CRAFTING_DEPTH;
    private static final ModConfigSpec.IntValue CRAFTING_BUDGET;
    private static final ModConfigSpec.IntValue GLOBAL_LIST;
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
        GLOBAL_LIST = builder
                .comment("Wie viele Einträge ein globaler Listenwert tragen darf.",
                        "Darüber hinaus hält das Programm mit einer Meldung an.",
                        "Er ist der einzige Wert, der in einer Schleife wachsen kann",
                        "und den Neustart übersteht.")
                .defineInRange("globalListSize", DEFAULT_GLOBAL_LIST, 8, 100_000);
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
     * How many steps a run may take.
     *
     * <p><b>With a fallback to the default.</b> A unit test loads no
     * configuration file, nor does a data generator, and a value that then
     * throws turns a setting into a crash in places that have nothing to do
     * with settings.
     */
    public static int stepBudget() {
        return SERVER_SPEC.isLoaded() ? STEP_BUDGET.get() : DEFAULT_STEP_BUDGET;
    }

    /** How far building the network graph searches. */
    public static int networkNodes() {
        return SERVER_SPEC.isLoaded() ? NETWORK_NODES.get() : DEFAULT_NETWORK_NODES;
    }

    /** How many recipes deep a crafting run searches. */
    public static int craftingDepth() {
        return SERVER_SPEC.isLoaded() ? CRAFTING_DEPTH.get() : DEFAULT_CRAFTING_DEPTH;
    }

    /** And how many demands it looks at while doing so. */
    public static int craftingBudget() {
        return SERVER_SPEC.isLoaded() ? CRAFTING_BUDGET.get() : DEFAULT_CRAFTING_BUDGET;
    }

    /** How long a global list value may grow. */
    public static int globalListSize() {
        return SERVER_SPEC.isLoaded() ? GLOBAL_LIST.get() : DEFAULT_GLOBAL_LIST;
    }

    /**
     * Who may rebuild someone else's factory.
     *
     * <p>Without a loaded configuration: everyone. A lock that arises from a
     * missing value is the wrong direction — it would stand there precisely
     * when no one has set it up.
     */
    public static FnProtection.Mode protection() {
        return SERVER_SPEC.isLoaded() ? PROTECTION.get() : FnProtection.Mode.OFF;
    }
}
