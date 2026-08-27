package dev.devpanda.factorynetwork.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Anbau am Controller: mehr Außenflächen für Kabel, sonst nichts.
 *
 * <p>Der Controller hat sechs Seiten, an jeder hängt höchstens ein Strang, und
 * ein dichtes Kabel trägt vierundsechzig Kanäle — macht 384 Geräte je Netz.
 * Wer mehr braucht, setzt Anbauten daneben; jeder bringt eigene Seiten mit.
 *
 * <p><b>Er hält nichts.</b> Keine BlockEntity, kein Zustand, kein Programm.
 * Das ist der ganze Grund, warum es ihn als eigenen Blocktyp gibt statt als
 * zweiten Controller: Sobald mehrere gleiche Blöcke ein Netz bilden, muss
 * einer der Master sein, und die Frage „welcher" hat keine gute Antwort. Das
 * Programm liegt als Datei neben der Welt und heißt nach der Position des
 * Controllers — ein wandernder Master benennt die Datei um, und wer sie in VS
 * Code offen hat, schreibt ab da in ein Programm, das niemand mehr liest.
 *
 * <p>Ohne Controller in der Gruppe tut er nichts. Er ist keine Leitung: Kabel
 * verbindet er nicht, Strom nimmt er nicht an. Wer zwei Anbauten
 * aneinanderstellt, bekommt Seiten und keinen Draht.
 *
 * <p>Die Begründung im Ganzen steht in {@code docs/entscheidungen.md} unter
 * „Der Controller bleibt ein Block".
 */
public class ControllerExtensionBlock extends Block {

    /** Der Umriss aus den Kästen des Modells. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(MachineLayouts.extension());

    public ControllerExtensionBlock(Properties properties) {
        super(properties);
    }
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
