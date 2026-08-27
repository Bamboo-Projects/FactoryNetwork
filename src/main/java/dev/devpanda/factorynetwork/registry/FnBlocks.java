package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.DenseCableBlock;
import dev.devpanda.factorynetwork.block.DriveBlock;
import dev.devpanda.factorynetwork.block.PressBlock;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.RouterBlock;
import dev.devpanda.factorynetwork.block.BurnerBlock;
import dev.devpanda.factorynetwork.block.CreativeSourceBlock;
import dev.devpanda.factorynetwork.block.ControllerBlock;
import dev.devpanda.factorynetwork.block.DisplayBlock;
import dev.devpanda.factorynetwork.block.TerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Die Blöcke der Mod. */
public final class FnBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(FactoryNetwork.MOD_ID);

    /** Wurzel eines Netzwerks: hält Programm, Speicher und die Laufzeit. */
    public static final DeferredBlock<Block> CONTROLLER = BLOCKS.register("controller",
            () -> new ControllerBlock(machineProperties()));

    /**
     * Mehr Außenflächen für Kabel, und sonst nichts.
     *
     * <p>Er hält weder Programm noch Speicher noch Strom — deshalb kann die
     * Master-Rolle nicht wandern. Siehe {@link
     * dev.devpanda.factorynetwork.block.ControllerExtensionBlock}.
     */
    public static final DeferredBlock<Block> CONTROLLER_EXTENSION =
            BLOCKS.register("controller_extension",
                    () -> new dev.devpanda.factorynetwork.block.ControllerExtensionBlock(
                            machineProperties()));

    /**
     * Baut, was das Netz bestellt.
     *
     * <p>Ohne Muster-Items: Jedes Werkbank-Rezept steht schon im Server.
     */
    public static final DeferredBlock<Block> FABRICATOR = BLOCKS.register("fabricator",
            () -> new dev.devpanda.factorynetwork.block.FabricatorBlock(machineProperties()));

    /**
     * Gibt seiner Umgebung einen Anlagennamen.
     *
     * <p>Ein Kabelstück mit Namensschild: Was hinter ihm am Kabel hängt,
     * gehört zu seiner Anlage. Kanäle vermehrt er nicht.
     */
    public static final DeferredBlock<Block> GATEWAY = BLOCKS.register("gateway",
            // noOcclusion, weil der Torbogen kein voller Würfel mehr ist:
            // Sonst lässt Minecraft die Flächen der Nachbarn weg, die man
            // durch die Öffnungen sieht, und im Tor steht ein schwarzes Loch.
            () -> new dev.devpanda.factorynetwork.block.GatewayBlock(
                    machineProperties().noOcclusion()));

    /** Verbindet Blöcke zu einem Netzwerk. */
    public static final DeferredBlock<Block> CABLE = BLOCKS.register("cable",
            () -> new CableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.6F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Vierundsechzig Kanäle statt sechzehn, und zehn Blockpixel statt sechs. */
    public static final DeferredBlock<Block> DENSE_CABLE = BLOCKS.register("dense_cable",
            () -> new DenseCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /**
     * Kreuzung für dicke Kabel: Jede Seite bekommt eine Bahn.
     *
     * <p>Härter als ein Kabel, aber kein Maschinengehäuse — er steht in der
     * Leitung und soll sich mit ihr abbauen lassen.
     */
    public static final DeferredBlock<Block> ROUTER = BLOCKS.register("router",
            () -> new RouterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.2F)
                    .sound(SoundType.METAL)));

    /**
     * Das Erz, aus dem alles wird.
     *
     * <p>Zwei Fassungen, weil Minecraft unter Y=0 Deepslate statt Stein hat —
     * ein Erz mit der falschen Grundfarbe fällt in einer Höhle sofort auf.
     */
    public static final DeferredBlock<Block> CRYSTAL_ORE = BLOCKS.register("crystal_ore",
            () -> new net.minecraft.world.level.block.DropExperienceBlock(
                    net.minecraft.util.valueproviders.UniformInt.of(2, 5),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.0F, 3.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DEEPSLATE_CRYSTAL_ORE =
            BLOCKS.register("deepslate_crystal_ore",
                    () -> new net.minecraft.world.level.block.DropExperienceBlock(
                            net.minecraft.util.valueproviders.UniformInt.of(2, 5),
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.DEEPSLATE)
                                    .strength(4.5F, 3.0F)
                                    .sound(SoundType.DEEPSLATE)
                                    .requiresCorrectToolForDrops()));

    /**
     * Strom aus Ofenbrennstoff — absichtlich mittelmäßig.
     *
     * <p>Sie soll nicht mit Generatoren anderer Mods konkurrieren, sondern
     * dafür sorgen, dass die Fertigungskette der Mod ohne Fremdmod anläuft.
     */
    public static final DeferredBlock<Block> BURNER = BLOCKS.register("burner",
            () -> new BurnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BurnerBlock.LIT) ? 13 : 0)));

    /** Presst Bauteile — der Einstieg in die Fertigungskette. */
    public static final DeferredBlock<Block> PRESS = BLOCKS.register("press",
            () -> new PressBlock(machineProperties()));

    /**
     * Strom ohne Brennstoff — nur zum Ausprobieren.
     *
     * <p>Die Mod erzeugt keinen Strom. Zum Bauen und Prüfen steht aber kein
     * Pack daneben, und ohne Quelle steht jedes Netz sofort still.
     */
    public static final DeferredBlock<Block> CREATIVE_SOURCE =
            BLOCKS.register("creative_source", () -> new CreativeSourceBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(-1.0F, 3600000.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 10)));

    /** Nimmt zwölf Server auf — ohne ihn rechnet das Netz nicht. */
    public static final DeferredBlock<Block> RACK = BLOCKS.register("server_rack",
            () -> new RackBlock(machineProperties()));

    /** Nimmt Speicherzellen auf — der Lagerraum des Netzes. */
    public static final DeferredBlock<Block> DRIVE = BLOCKS.register("drive",
            () -> new DriveBlock(machineProperties()));

    /** Zeigt an der Wand, was im Netz vorgeht. */
    public static final DeferredBlock<Block> DISPLAY = BLOCKS.register("display",
            () -> new DisplayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    /** Zugang zum Code-Editor. */
    public static final DeferredBlock<Block> TERMINAL = BLOCKS.register("terminal",
            () -> new TerminalBlock(machineProperties()));

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    private FnBlocks() {
    }
}
