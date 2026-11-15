package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import java.util.List;

public final class StateModelBridge {
    private StateModelBridge() { }
    public static void configure(ItemRenderState state, Miniature miniature, int lod, int[] tints, int tintVersion,
                                 Transformation transform, ModelTransformationMode context, Object key) {
        BakedModel model = new BakedModel() {
            private final ModelTransformation transformations = new ModelTransformation(transform, transform, transform, transform,
                    transform, transform, transform, transform);
            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) { return List.of(); }
            @Override public boolean useAmbientOcclusion() { return false; }
            @Override public boolean hasDepth() { return true; }
            @Override public boolean isSideLit() { return context != ModelTransformationMode.GUI; }
            @Override public Sprite getParticleSprite() { return miniature.particle; }
            @Override public ModelTransformation getTransformation() { return transformations; }
        };
        state.newLayer().setSpecialModel(StateMeshRenderer.INSTANCE,
                new StateMeshRenderer.Data(miniature.levels[lod], tints, tintVersion), model);
    }
}
