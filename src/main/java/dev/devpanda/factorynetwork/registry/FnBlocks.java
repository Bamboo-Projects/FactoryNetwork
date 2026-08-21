package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.DenseCableBlock;
import dev.devpanda.factorynetwork.block.DriveBlock;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
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

    /** Nimmt Speicherzellen auf — der Lagerraum des Netzes. */
    public static final DeferredBlock<Block> DRIVE = BLOCKS.register("drive",
            () -> new DriveBlock(machineProperties()));

    /** Gibt einer angrenzenden Maschine einen Namen im Netzwerk. */
    public static final DeferredBlock<Block> CONNECTOR = BLOCKS.register("connector",
            () -> new ConnectorBlock(machineProperties()));

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
