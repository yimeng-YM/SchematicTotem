package dev.yimeng.schematictotem.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * A one-biome stand-in world for block color providers, so grass, leaves and water get real biome colors
 * (vanilla falls back to white water without a world).
 */
final class TintView implements BlockAndTintGetter {
    private final Biome biome;
    private final @Nullable LevelLightEngine lighting;

    TintView(Biome biome, @Nullable LevelLightEngine lighting) {
        this.biome = biome;
        this.lighting = lighting;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return colorResolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lighting;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }
}
