package dev.devpanda.factorynetwork.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Wer ein Schraubenschlüssel ist, und wann er zerlegt.
 *
 * <p><b>Ein Tag und keine Klasse.</b> {@code c:tools/wrench} ist die
 * Konvention, die Mekanism, Create, Thermal und Immersive Engineering
 * bedienen. Wer einen von ihnen dabeihat, kann damit hier arbeiten, ohne dass
 * diese Mod eine einzige davon kennt — und unser eigener steht daneben im
 * selben Tag, damit es auch ohne Fremdmod geht.
 *
 * <p>Dasselbe Vorgehen wie bei den Connectoren: die Standard-Schnittstelle
 * nehmen, und alles, was sie spricht, kommt gratis mit.
 *
 * <p><b>Die Geste ist Schleichen plus Rechtsklick.</b> Abgeschaut von AE2, und
 * zwar nicht aus Bequemlichkeit: Ein Spieler lernt eine Geste einmal. Bedeutet
 * sie in der einen Mod „zerlegen" und in der nächsten „drehen", lernt er sie
 * dreimal und vergisst sie zweimal.
 */
public final class Wrenches {

    /** Das Tag der Konvention, auf das alle hören. */
    public static final TagKey<Item> WRENCH = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    /** Ist das ein Schraubenschlüssel? */
    public static boolean is(ItemStack stack) {
        return stack.is(WRENCH);
    }

    /**
     * Will der Spieler damit gerade zerlegen?
     *
     * <p>Nur mit Schlüssel <b>und</b> im Schleichen. Ohne Schleichen soll
     * derselbe Klick tun, was er sonst tut — ein Fenster öffnen zum Beispiel.
     */
    public static boolean disassembling(Player player, ItemStack stack) {
        return player.isShiftKeyDown() && is(stack);
    }

    /**
     * Nimmt den Anschluss ab, den dieser Klick meint.
     *
     * <p>Steht hier und nicht im Ereignisbehandler, damit ein Prüflauf sie
     * aufrufen kann: Ein Test, der stattdessen {@code removePart} ruft,
     * prüft den Behälter und nicht den Schraubenschlüssel.
     *
     * @return ob etwas abgenommen wurde
     */
    public static boolean takePart(net.minecraft.world.level.Level level,
                                   net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block
                .entity.CableBusBlockEntity bus)) {
            return false;
        }
        net.minecraft.core.Direction side = dev.devpanda.factorynetwork.block.CableBlock
                .partSideAt(level, pos, hit);
        if (side == null || bus.partAt(side) == null) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        bus.removePart(side);
        // Der Anschluss kommt zurück, sonst wäre der Schlüssel ein
        // Zerstörwerkzeug und kein Schraubenschlüssel.
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()));
        // War es ein Halter und war das sein letzter Anschluss, bleibt
        // nichts übrig, das noch etwas hielte — dann geht der Block mit.
        if (!dev.devpanda.factorynetwork.block.CableBlock.carries(level.getBlockState(pos))
                && bus.parts().isEmpty()) {
            level.removeBlock(pos, false);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
            return true;
        }
        // Der Arm zum Anschluss steht im Blockzustand: Ohne diese Zeile
        // bliebe er stehen und zeigte ins Leere.
        level.setBlock(pos, dev.devpanda.factorynetwork.block.CableBlock.withConnections(
                        level.getBlockState(pos), level, pos),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    private Wrenches() {
    }
}
