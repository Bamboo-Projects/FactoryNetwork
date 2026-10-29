package dev.devpanda.factorynetwork.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Lines and boxes that show through walls.
 *
 * <p>Until 1.21.1 the overlays switched the depth test off around their
 * drawing. Since 1.21.5 that state belongs to the pipeline, so these are two
 * pipelines without a depth test: the same shaders as Minecraft's lines and
 * debug boxes, only always in front.
 */
public final class FnRenderTypes {

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "pipeline/lines_see_through"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static final RenderPipeline BOXES_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "pipeline/boxes_see_through"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /** Lines (position, colour, normal, line width), always visible. */
    public static final RenderType LINES_SEE_THROUGH = RenderType.create(
            "factorynetwork_lines_see_through",
            RenderSetup.builder(LINES_PIPELINE).createRenderSetup());

    /** Coloured quads (position, colour), always visible. */
    public static final RenderType BOXES_SEE_THROUGH = RenderType.create(
            "factorynetwork_boxes_see_through",
            RenderSetup.builder(BOXES_PIPELINE).sortOnUpload().createRenderSetup());

    private FnRenderTypes() {
    }
}
