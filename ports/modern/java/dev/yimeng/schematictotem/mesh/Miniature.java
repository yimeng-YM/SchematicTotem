package dev.yimeng.schematictotem.mesh;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** A baked miniature: up to four detail levels sharing one tint table, built off-thread and then read-only. */
public final class Miniature {
    /** Quad tint source: a block color provider (state + tint index) or a fluid color. */
    public record TintKey(BlockState state, int tintIndex, boolean fluid) {
    }

    /** A block-entity block drawn by its item-form renderer at a model-space centre with a rotation. */
    public record BlockEntityPart(BlockState state, float x, float y, float z, float scale, Quaternionf rotation) {
    }

    public static final class Level {
        /** Blocks per cell along each axis (1 = full detail). */
        public final float factor;
        public final int gridX, gridY, gridZ;
        public final Mesh solid;
        public final Mesh translucent;
        public final List<BlockEntityPart> blockEntities;

        Level(float factor, int gridX, int gridY, int gridZ, Mesh solid, Mesh translucent, List<BlockEntityPart> blockEntities) {
            this.factor = factor;
            this.gridX = gridX;
            this.gridY = gridY;
            this.gridZ = gridZ;
            this.solid = solid;
            this.translucent = translucent;
            this.blockEntities = blockEntities;
        }

        public int quadCount() {
            return solid.size() + translucent.size();
        }

        /** Cells along the longest side: how fine this level is. */
        public int resolution() {
            return Math.max(gridX, Math.max(gridY, gridZ));
        }
    }

    public record Stats(String name, int sizeX, int sizeY, int sizeZ, long blocks, int paletteSize, long bakeMillis, long volumeBytes) {
    }

    public final Level[] levels;
    public final Supplier<Vector3fc[]> corners;
    /** Corners of the model's box in item space (the unit cube), before any display transform. */
    public final Vector3fc[] cornerArray;
    /** Vertices of the most detailed level (sampled), for fitting display sizes to the real outline. */
    public final Vector3fc[] outline;
    public final @Nullable TextureAtlasSprite particle;
    /** Identity of the model set the quads were baked against; a resource reload replaces it. */
    public final Object resourceToken;
    public final Stats stats;
    /** Side the build probably faces (south/north/east/west/up), used while the player has not chosen one. */
    public final String suggestedFront;
    private final TintKey[] tintKeys;

    // Render-thread state
    private int[] tints;
    private @Nullable Object tintWorld = this; // never equal to a real world -> computed on first use
    private @Nullable String tintBiome;
    private int tintVersion;

    Miniature(Level[] levels, TintKey[] tintKeys, Vector3fc[] corners, Vector3fc[] outline, @Nullable TextureAtlasSprite particle, Object resourceToken,
              Stats stats, String suggestedFront) {
        this.levels = levels;
        this.outline = outline;
        this.suggestedFront = suggestedFront;
        this.cornerArray = corners;
        this.tintKeys = tintKeys;
        this.corners = () -> corners;
        this.particle = particle;
        this.resourceToken = resourceToken;
        this.stats = stats;
        this.tints = new int[tintKeys.length];
    }

    public int tintCount() {
        return tintKeys.length;
    }

    /** Tint colors for the current world, recomputed only when the world or biome setting changes (render thread). */
    public int[] tints(@Nullable ClientLevel world, String biomeId) {
        if (tintWorld != world || !biomeId.equals(tintBiome)) {
            tints = computeTints(world, biomeId);
            tintWorld = world;
            tintBiome = biomeId;
            tintVersion++;
        }
        return tints;
    }

    public int tintVersion() {
        return tintVersion;
    }

    private int[] computeTints(@Nullable ClientLevel world, String biomeId) {
        int[] out = new int[tintKeys.length];
        if (out.length == 0) return out;
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        TintView view = null;
        if (world != null) {
            Registry<Biome> biomes = world.registryAccess().lookupOrThrow(Registries.BIOME);
            Identifier id = Identifier.tryParse(biomeId);
            Biome biome = id == null ? null : biomes.getOptional(id).orElse(null);
            if (biome == null) biome = biomes.getOptional(Biomes.PLAINS).orElse(null);
            if (biome != null) view = new TintView(biome, world.getLightEngine());
        }
        BlockPos pos = view == null ? null : BlockPos.ZERO;
        for (int i = 0; i < out.length; i++) {
            TintKey key = tintKeys[i];
            int color;
            try {
                if (key.fluid()) {
                    FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(key.state().getFluidState().getType());
                    color = handler == null ? -1 : handler.getFluidColor(view, pos, key.state().getFluidState());
                } else {
                    color = colors.getColor(key.state(), view, pos, key.tintIndex());
                }
            } catch (RuntimeException e) {
                color = -1;
            }
            // Block colors are RGB; the item renderer takes alpha from the tint, so force it opaque.
            out[i] = color | 0xFF000000;
        }
        return out;
    }
}
