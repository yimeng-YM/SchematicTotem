package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.item.ModelTransformationMode;

public final class StateModelBridge {
    private StateModelBridge() { }
    public static void configure(ItemRenderState state, Miniature miniature, int lod, int[] tints, int tintVersion,
                                 Transformation transform, ModelTransformationMode context, Object key) {
        ItemRenderState.LayerRenderState layer = state.newLayer();
        layer.setSpecialModel(StateMeshRenderer.INSTANCE, new StateMeshRenderer.Data(miniature.levels[lod], tints, tintVersion));
        layer.setTransform(transform);
        layer.setUseLight(context != ModelTransformationMode.GUI);
        layer.setVector(miniature.legacyCorners);
        if (miniature.particle != null) layer.setParticle(miniature.particle);
    }
}
