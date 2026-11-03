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
    public static final DeferredBlock<Block> CONTROLLER = BLOCKS.registerBlock("controller",
            // noOcclusion, because the body recedes between the cover plates:
            // without the setting the neighbours lack the face one sees in the
            // seam.
            props -> new ControllerBlock(props.noOcclusion()), FnBlocks::machineProperties);

    /**
     * More outer faces for cables, and nothing else.
     *
     * <p>It holds neither program nor storage nor power — that is why the
     * master role cannot migrate. See {@link
     * dev.devpanda.factorynetwork.block.ControllerExtensionBlock}.
     */
    public static final DeferredBlock<Block> CONTROLLER_EXTENSION =
            BLOCKS.registerBlock("controller_extension",
                    props -> new dev.devpanda.factorynetwork.block.ControllerExtensionBlock(
                            props.noOcclusion()), FnBlocks::machineProperties);

    /**
     * Builds what the network orders.
     *
     * <p>Without pattern items: every crafting-table recipe is already in the
     * server.
     */
    public static final DeferredBlock<Block> FABRICATOR = BLOCKS.registerBlock("fabricator",
            props -> new dev.devpanda.factorynetwork.block.FabricatorBlock(
                            props.noOcclusion()), FnBlocks::machineProperties);

    /**
     * Gives its surroundings a plant name.
     *
     * <p>A piece of cable with a name tag: whatever hangs on the cable behind
     * it belongs to its plant. It does not multiply channels.
     */
    public static final DeferredBlock<Block> GATEWAY = BLOCKS.registerBlock("gateway",
            // noOcclusion, because the archway is no longer a full cube:
            // otherwise Minecraft omits the neighbours' faces one sees through
            // the openings, and a black hole sits in the gate.
            props -> new dev.devpanda.factorynetwork.block.GatewayBlock(
                    props.noOcclusion()), FnBlocks::machineProperties);

    /** Connects blocks into a network. */
    public static final DeferredBlock<Block> CABLE = BLOCKS.registerBlock("cable",
            props -> new CableBlock(props
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.6F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Sixty-four channels instead of sixteen, and ten block-pixels instead of six. */
    public static final DeferredBlock<Block> DENSE_CABLE = BLOCKS.registerBlock("dense_cable",
            props -> new DenseCableBlock(props
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
    public static final DeferredBlock<Block> ROUTER = BLOCKS.registerBlock("router",
            props -> new RouterBlock(props
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
    public static final DeferredBlock<Block> CRYSTAL_ORE = BLOCKS.registerBlock("crystal_ore",
            props -> new net.minecraft.world.level.block.DropExperienceBlock(
                    net.minecraft.util.valueproviders.UniformInt.of(2, 5),
                    props
                            .mapColor(MapColor.STONE)
                            .strength(3.0F, 3.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DEEPSLATE_CRYSTAL_ORE =
            BLOCKS.registerBlock("deepslate_crystal_ore",
                    props -> new net.minecraft.world.level.block.DropExperienceBlock(
                            net.minecraft.util.valueproviders.UniformInt.of(2, 5),
                            props
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
    public static final DeferredBlock<Block> BURNER = BLOCKS.registerBlock("burner",
            props -> new BurnerBlock(props
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BurnerBlock.LIT) ? 13 : 0)
                    .noOcclusion()));

    /** Presses components — the entry into the production chain. */
    public static final DeferredBlock<Block> PRESS = BLOCKS.registerBlock("press",
            props -> new PressBlock(props.noOcclusion()), FnBlocks::machineProperties);

    /**
     * Power without fuel — just for trying out.
     *
     * <p>The mod generates no power. But for building and testing there is no
     * pack alongside, and without a source every network stalls at once.
     */
    public static final DeferredBlock<Block> CREATIVE_SOURCE =
            BLOCKS.registerBlock("creative_source", props -> new CreativeSourceBlock(
                    props
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(-1.0F, 3600000.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 10)
                            .noOcclusion()));

    /** Takes twelve servers — without it the network does not compute. */
    public static final DeferredBlock<Block> RACK = BLOCKS.registerBlock("server_rack",
            props -> new RackBlock(props), FnBlocks::machineProperties);

    /** A foreign storage system — AE2, Refined Storage — as one device. */
    public static final DeferredBlock<Block> STORAGE_LINK = BLOCKS.registerBlock("storage_link",
            props -> new dev.devpanda.factorynetwork.block.StorageLinkBlock(props),
            FnBlocks::machineProperties);

    /** Takes storage cells — the network's storage room. */
    public static final DeferredBlock<Block> DRIVE = BLOCKS.registerBlock("drive",
            // noOcclusion, because the housing stands on feet and the fascia
            // juts out sideways beyond it: without the setting Minecraft omits
            // the neighbours' faces one sees between them.
            props -> new DriveBlock(props.noOcclusion()), FnBlocks::machineProperties);

    /** Shows on the wall what goes on in the network. */
    public static final DeferredBlock<Block> DISPLAY = BLOCKS.registerBlock("display",
            props -> new DisplayBlock(props
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
    public static final DeferredBlock<Block> MAST = BLOCKS.registerBlock("mast",
            props -> new dev.devpanda.factorynetwork.block.MastBlock(
                    props.noOcclusion()), FnBlocks::machineProperties);

    /** One end of a line without cable in between. */
    public static final DeferredBlock<Block> BRIDGE = BLOCKS.registerBlock("bridge",
            props -> new dev.devpanda.factorynetwork.block.BridgeBlock(
                    props.noOcclusion()), FnBlocks::machineProperties);

    /** Access to the code editor. */
    public static final DeferredBlock<Block> TERMINAL = BLOCKS.registerBlock("terminal",
            // noOcclusion, because console and frame protrude and the housing
            // is narrower at the sides.
            props -> new TerminalBlock(props.noOcclusion()), FnBlocks::machineProperties);

    /** The factory halls, one block per size. */
    public static final java.util.Map<dev.devpanda.factorynetwork.hall.HallTier,
            DeferredBlock<dev.devpanda.factorynetwork.hall.HallBlock>> HALLS = registerHalls();

    private static java.util.Map<dev.devpanda.factorynetwork.hall.HallTier,
            DeferredBlock<dev.devpanda.factorynetwork.hall.HallBlock>> registerHalls() {
        java.util.Map<dev.devpanda.factorynetwork.hall.HallTier,
                DeferredBlock<dev.devpanda.factorynetwork.hall.HallBlock>> halls =
                new java.util.EnumMap<>(dev.devpanda.factorynetwork.hall.HallTier.class);
        for (var tier : dev.devpanda.factorynetwork.hall.HallTier.values()) {
            halls.put(tier, BLOCKS.registerBlock(tier.id(),
                    props -> new dev.devpanda.factorynetwork.hall.HallBlock(tier, props),
                    FnBlocks::machineProperties));
        }
        return java.util.Collections.unmodifiableMap(halls);
    }

    /** The wall of a hall: unbreakable, and the way out. */
    public static final DeferredBlock<Block> HALL_WALL = BLOCKS.registerBlock("hall_wall",
            dev.devpanda.factorynetwork.hall.HallWallBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(-1.0F, 3_600_000.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .noLootTable()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15));

    /** A piece of hall wall that carries the cable through. */
    public static final DeferredBlock<Block> HALL_PORT = BLOCKS.registerBlock("hall_port",
            dev.devpanda.factorynetwork.hall.HallPortBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(-1.0F, 3_600_000.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .noLootTable()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15));

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
