package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.block.ControllerBlock;
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

    /** Gibt einer angrenzenden Maschine einen Namen im Netzwerk. */
    public static final DeferredBlock<Block> CONNECTOR = BLOCKS.register("connector",
            () -> new ConnectorBlock(machineProperties()));

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
