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

/** The blocks of the mod. */
public final class FnBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(FactoryNetwork.MOD_ID);

    // Almost every block here carries noOcclusion. The reason is the same as
    // for the first one: as soon as a model no longer fills the whole cube,
    // Minecraft omits the neighbours' faces one sees through the gaps — and
    // then a black hole sits in the gap.

    /** Root of a network: holds program, storage and the runtime. */
    public static final DeferredBlock<Block> CONTROLLER = BLOCKS.register("controller",
            // noOcclusion, because the body recedes between the cover plates:
            // without the setting the neighbours lack the face one sees in the
            // seam.
            () -> new ControllerBlock(machineProperties().noOcclusion()));

    /**
     * More outer faces for cables, and nothing else.
     *
     * <p>It holds neither program nor storage nor power — that is why the
     * master role cannot migrate. See {@link
     * dev.devpanda.factorynetwork.block.ControllerExtensionBlock}.
     */
    public static final DeferredBlock<Block> CONTROLLER_EXTENSION =
            BLOCKS.register("controller_extension",
                    () -> new dev.devpanda.factorynetwork.block.ControllerExtensionBlock(
                            machineProperties().noOcclusion()));

    /**
     * Builds what the network orders.
     *
     * <p>Without pattern items: every crafting-table recipe is already in the
     * server.
     */
    public static final DeferredBlock<Block> FABRICATOR = BLOCKS.register("fabricator",
            () -> new dev.devpanda.factorynetwork.block.FabricatorBlock(
                            machineProperties().noOcclusion()));

    /**
     * Gives its surroundings a plant name.
     *
     * <p>A piece of cable with a name tag: whatever hangs on the cable behind
     * it belongs to its plant. It does not multiply channels.
     */
    public static final DeferredBlock<Block> GATEWAY = BLOCKS.register("gateway",
            // noOcclusion, because the archway is no longer a full cube:
            // otherwise Minecraft omits the neighbours' faces one sees through
            // the openings, and a black hole sits in the gate.
            () -> new dev.devpanda.factorynetwork.block.GatewayBlock(
                    machineProperties().noOcclusion()));

    /** Connects blocks into a network. */
    public static final DeferredBlock<Block> CABLE = BLOCKS.register("cable",
            () -> new CableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.6F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Sixty-four channels instead of sixteen, and ten block-pixels instead of six. */
    public static final DeferredBlock<Block> DENSE_CABLE = BLOCKS.register("dense_cable",
            () -> new DenseCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /**
     * Junction for thick cables: each side gets a lane.
     *
     * <p>Harder than a cable, but not a machine housing — it stands in the
     * line and should be removable along with it.
     */
    public static final DeferredBlock<Block> ROUTER = BLOCKS.register("router",
            () -> new RouterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.2F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /**
     * The ore that everything comes from.
     *
     * <p>Two versions, because below Y=0 Minecraft has deepslate instead of
     * stone — an ore with the wrong base colour stands out immediately in a
     * cave.
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
     * Power from furnace fuel — deliberately mediocre.
     *
     * <p>It is not meant to compete with other mods' generators, but to ensure
     * that the mod's production chain gets going without a foreign mod.
     */
    public static final DeferredBlock<Block> BURNER = BLOCKS.register("burner",
            () -> new BurnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BurnerBlock.LIT) ? 13 : 0)
                    .noOcclusion()));

    /** Presses components — the entry into the production chain. */
    public static final DeferredBlock<Block> PRESS = BLOCKS.register("press",
            () -> new PressBlock(machineProperties().noOcclusion()));

    /**
     * Power without fuel — just for trying out.
     *
     * <p>The mod generates no power. But for building and testing there is no
     * pack alongside, and without a source every network stalls at once.
     */
    public static final DeferredBlock<Block> CREATIVE_SOURCE =
            BLOCKS.register("creative_source", () -> new CreativeSourceBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(-1.0F, 3600000.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 10)
                            .noOcclusion()));

    /** Takes twelve servers — without it the network does not compute. */
    public static final DeferredBlock<Block> RACK = BLOCKS.register("server_rack",
            () -> new RackBlock(machineProperties()));

    /** Takes storage cells — the network's storage room. */
    public static final DeferredBlock<Block> DRIVE = BLOCKS.register("drive",
            // noOcclusion, because the housing stands on feet and the fascia
            // juts out sideways beyond it: without the setting Minecraft omits
            // the neighbours' faces one sees between them.
            () -> new DriveBlock(machineProperties().noOcclusion()));

    /** Shows on the wall what goes on in the network. */
    public static final DeferredBlock<Block> DISPLAY = BLOCKS.register("display",
            () -> new DisplayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    /**
     * From here the network broadcasts.
     *
     * <p>Between base and arms there is air — hence noOcclusion, as with every
     * block here that is not a full cube.
     */
    public static final DeferredBlock<Block> MAST = BLOCKS.register("mast",
            () -> new dev.devpanda.factorynetwork.block.MastBlock(
                    machineProperties().noOcclusion()));

    /** One end of a line without cable in between. */
    public static final DeferredBlock<Block> BRIDGE = BLOCKS.register("bridge",
            () -> new dev.devpanda.factorynetwork.block.BridgeBlock(
                    machineProperties().noOcclusion()));

    /** Access to the code editor. */
    public static final DeferredBlock<Block> TERMINAL = BLOCKS.register("terminal",
            // noOcclusion, because console and frame protrude and the housing
            // is narrower at the sides.
            () -> new TerminalBlock(machineProperties().noOcclusion()));

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
