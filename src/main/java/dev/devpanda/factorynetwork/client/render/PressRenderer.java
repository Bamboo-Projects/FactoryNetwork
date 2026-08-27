package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.MachineLayouts;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Zeichnet den Stempel der Presse — das eine Teil, das sich bewegt.
 *
 * <p><b>Warum nicht im Blockmodell.</b> Der Stempel fährt herunter und wieder
 * hoch, solange gearbeitet wird. Ein Blockmodell steht still, und ein
 * Blockzustand je Zwischenstellung wären dreißig Zustände für eine Bewegung.
 * Er ist deshalb ein eigenes Modell, das hier gezeichnet und dabei
 * verschoben wird — dasselbe Verfahren wie bei den Anschlüssen am Kabel.
 *
 * <p><b>Warum die Bewegung aus der Spielzeit kommt und nicht aus dem
 * Fortschritt.</b> Der Fortschritt steht in der BlockEntity und erreicht den
 * Client nur, wenn ein Blockupdate gesendet wird — jeden Tick eines zu
 * schicken, nur damit ein Stempel flüssig läuft, wäre Netzlast für nichts.
 * Der Client fragt deshalb nur, <i>ob</i> gearbeitet wird, und rechnet die
 * Bewegung selbst. Ein Schlag je Sekunde, gleichmäßig — was die Presse
 * gerade wie weit fertig hat, sagt ohnehin die Oberfläche und nicht der
 * Stempel.
 */
public class PressRenderer implements BlockEntityRenderer<PressBlockEntity> {

    /** Das Modell des Stempels, das FnClient eigens anmeldet. */
    public static final ModelResourceLocation RAM = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "block/press_ram"));

    /** Ein Schlag je Sekunde. */
    private static final float TICKS_PER_STROKE = 20.0F;

    private final Minecraft client = Minecraft.getInstance();

    public PressRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PressBlockEntity press, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        if (press.getLevel() == null) {
            return;
        }

        poses.pushPose();
        // Die Presse steht in vier Richtungen; ihr Modell dreht der
        // Blockzustand, der Stempel muss selbst mitdrehen.
        Direction facing = press.getBlockState()
                .getValue(HorizontalDirectionalBlock.FACING);
        poses.translate(0.5F, 0.5F, 0.5F);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -facing.toYRot() + 180.0F));
        poses.translate(-0.5F, -0.5F + drop(press, partialTick), -0.5F);

        client.getBlockRenderer().getModelRenderer().renderModel(
                poses.last(), buffers.getBuffer(RenderType.cutout()),
                press.getBlockState(), client.getModelManager().getModel(RAM),
                1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY,
                RenderType.cutout());
        poses.popPose();
    }

    /**
     * Wie tief der Stempel gerade steht, in Blöcken.
     *
     * <p>Null, solange nichts läuft — dann hängt er unter der Decke. Sonst
     * ein weicher Weg nach unten und zurück, dessen Tiefe genau der Abstand
     * bis auf den Amboss ist.
     */
    private static float drop(PressBlockEntity press, float partialTick) {
        if (press.progress() <= 0 || press.getLevel() == null) {
            return 0.0F;
        }
        float phase = (press.getLevel().getGameTime() % (long) TICKS_PER_STROKE
                + partialTick) / TICKS_PER_STROKE;
        float wave = 0.5F - 0.5F * (float) Math.cos(2.0 * Math.PI * phase);
        return -MachineLayouts.pressStroke() / 16.0F * wave;
    }
}
