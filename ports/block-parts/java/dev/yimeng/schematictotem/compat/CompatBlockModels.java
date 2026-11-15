package dev.yimeng.schematictotem.compat;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import java.util.ArrayList;
import java.util.List;

public class CompatBlockModels {
    private final BlockModels models;
    public CompatBlockModels(BlockModels models) { this.models = models; }
    public Model getModel(BlockState state) { return new VanillaModel(models.getModel(state)); }
    public interface Model {
        void addParts(Random random, List<Part> parts);
        Sprite particleSprite();
    }
    public interface Part {
        List<CompatQuad> getQuads(Direction side);
        default boolean useAmbientOcclusion() { return true; }
        default Sprite particleSprite() { return null; }
    }
    private record VanillaModel(BlockStateModel model) implements Model {
        public void addParts(Random random, List<Part> parts) {
            List<BlockModelPart> vanilla = new ArrayList<>();
            model.addParts(random, vanilla);
            for (BlockModelPart part : vanilla) parts.add(new VanillaPart(part));
        }
        public Sprite particleSprite() { return model.particleSprite(); }
    }
    private record VanillaPart(BlockModelPart part) implements Part {
        public List<CompatQuad> getQuads(Direction side) {
            return part.getQuads(side).stream().map(CompatQuad::fromVanilla).toList();
        }
    }
}
