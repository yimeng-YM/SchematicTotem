package dev.yimeng.schematictotem.compat;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import java.util.List;

/** Keeps the meshing algorithm independent of the vanilla block-model container changes. */
public class CompatBlockModels {
    private final BlockModels models;
    public CompatBlockModels(BlockModels models) { this.models = models; }
    public Model getModel(BlockState state) { return new VanillaModel(models.getModel(state), state); }
    public interface Model {
        void addParts(Random random, List<Part> parts);
        Sprite particleSprite();
    }
    public interface Part {
        List<CompatQuad> getQuads(Direction face);
        default boolean useAmbientOcclusion() { return true; }
        default Sprite particleSprite() { return null; }
    }
    private record VanillaModel(BakedModel model, BlockState state) implements Model {
        public void addParts(Random random, List<Part> parts) { parts.add(new VanillaPart(model, state, random.nextLong())); }
        public Sprite particleSprite() { return model.getParticleSprite(); }
    }
    private record VanillaPart(BakedModel model, BlockState state, long seed) implements Part {
        public List<CompatQuad> getQuads(Direction face) {
            return model.getQuads(state, face, Random.create(seed)).stream().map(CompatQuad::fromVanilla).toList();
        }
    }
}
