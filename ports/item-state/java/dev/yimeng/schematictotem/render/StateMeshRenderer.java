package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;

public final class StateMeshRenderer implements SpecialModelRenderer<StateMeshRenderer.Data> {
    public static final StateMeshRenderer INSTANCE = new StateMeshRenderer();
    public record Data(Miniature.Level level, int[] tints, int tintVersion) { }
    @Override public void render(Data data, ModelTransformationMode mode, MatrixStack matrices,
                                  VertexConsumerProvider consumers, int light, int overlay, boolean glint) {
        MiniatureMeshRenderer.render(data.level(), data.tints(), data.tintVersion(), matrices, consumers, light, overlay);
    }
    @Override public Data getData(ItemStack stack) { return null; }
    // Actual extents are supplied by the miniature layer rather than this stateless renderer.
    public void collectVertices(java.util.Set<org.joml.Vector3f> vertices) { }
}
