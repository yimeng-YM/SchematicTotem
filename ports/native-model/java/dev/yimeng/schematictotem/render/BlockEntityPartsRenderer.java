package dev.yimeng.schematictotem.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import java.util.List;

/** The unified block-model resolver handles both regular and special block geometry. */
public final class BlockEntityPartsRenderer {
    public static final BlockEntityPartsRenderer INSTANCE = new BlockEntityPartsRenderer();
    private BlockEntityPartsRenderer() { }
    public void render(List<Miniature.BlockEntityPart> parts, PoseStack matrices, SubmitNodeCollector queue,
                       int light, int overlay, int outlineColor) {
        Minecraft client = Minecraft.getInstance();
        BlockModelResolver resolver = new BlockModelResolver(client.getModelManager());
        for (Miniature.BlockEntityPart part : parts) {
            BlockModelRenderState state = new BlockModelRenderState();
            resolver.update(state, part.state(), BlockDisplayContext.create());
            matrices.pushPose();
            matrices.translate(part.x(), part.y(), part.z());
            matrices.scale(part.scale(), part.scale(), part.scale());
            // The placed-block resolver already applies the block state's facing.
            matrices.translate(-0.5F, -0.5F, -0.5F);
            state.submit(matrices, queue, light, overlay, outlineColor);
            matrices.popPose();
        }
    }
}
