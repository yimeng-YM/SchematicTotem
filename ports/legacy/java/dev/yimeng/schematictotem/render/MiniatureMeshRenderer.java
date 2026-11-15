package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class MiniatureMeshRenderer {
    private MiniatureMeshRenderer() { }
    public static void render(Miniature.Level level, int[] tints, int tintVersion, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        level.solid.emit(matrices.peek(), consumers.getBuffer(MiniatureLayers.CUTOUT), light, overlay, tints, tintVersion);
        level.translucent.emit(matrices.peek(), consumers.getBuffer(MiniatureLayers.TRANSLUCENT), light, overlay, tints, tintVersion);
        dev.yimeng.schematictotem.compat.SpriteActivity.mark(level.solid.animatedSprites);
        dev.yimeng.schematictotem.compat.SpriteActivity.mark(level.translucent.animatedSprites);
        dev.yimeng.schematictotem.compat.RenderGuard.beginParts();
        try { BlockEntityPartsRenderer.INSTANCE.render(level.blockEntities, matrices, consumers, light, overlay); }
        finally { dev.yimeng.schematictotem.compat.RenderGuard.endParts(); }
    }
}
