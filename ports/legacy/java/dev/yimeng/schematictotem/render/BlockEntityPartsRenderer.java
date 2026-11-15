package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import java.util.List;

/** Item renderers supply beds, chests and the other non-quad parts of the schematic. */
public final class BlockEntityPartsRenderer {
    public static final BlockEntityPartsRenderer INSTANCE = new BlockEntityPartsRenderer();
    private BlockEntityPartsRenderer() { }
    public void render(List<Miniature.BlockEntityPart> parts, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Miniature.BlockEntityPart part : parts) {
            matrices.push();
            matrices.translate(part.x(), part.y(), part.z());
            matrices.scale(part.scale(), part.scale(), part.scale());
            matrices.multiply(part.rotation());
            client.getItemRenderer().renderItem(new ItemStack(part.state().getBlock()), ModelTransformationMode.NONE,
                    light, overlay, matrices, consumers, client.world, 0);
            matrices.pop();
        }
    }
}
