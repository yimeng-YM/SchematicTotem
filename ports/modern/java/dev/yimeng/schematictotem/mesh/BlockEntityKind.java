package dev.yimeng.schematictotem.mesh;

import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.joml.Quaternionf;

/**
 * Blocks drawn by block-entity renderers rather than block models. They are rendered with the game's own item-style
 * special renderers ({@code LoadedBlockEntityModels}), turned to match the block state. Each rotation below was derived
 * from the matching vanilla block-entity renderer versus its item form.
 */
public enum BlockEntityKind {
    CHEST, BED, SHULKER_BOX, DECORATED_POT, SIGN, BANNER, SKULL, CONDUIT;

    public static BlockEntityKind of(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AbstractChestBlock<?>) return CHEST;
        if (block instanceof BedBlock) return BED;
        if (block instanceof ShulkerBoxBlock) return SHULKER_BOX;
        if (block instanceof DecoratedPotBlock) return DECORATED_POT;
        if (block instanceof StandingSignBlock) return SIGN;       // standing signs only; wall signs have no item form without a post
        if (block instanceof BannerBlock) return BANNER;   // standing banners only
        if (block instanceof SkullBlock) return SKULL;     // floor skulls / heads only
        if (block instanceof ConduitBlock) return CONDUIT;
        return null;
    }

    /** Rotation about the block centre that turns the item-form model into the placed block, or null to draw nothing. */
    public Quaternionf rotation(BlockState state) {
        return switch (this) {
            case CHEST -> yaw(-horizontal(state).toYRot());
            case BED -> state.getOptionalValue(BedBlock.PART).orElse(BedPart.HEAD) == BedPart.HEAD
                    ? yaw(-horizontal(state).toYRot()) : null; // the item form already holds both halves
            case SHULKER_BOX -> state.getOptionalValue(BlockStateProperties.FACING).orElse(Direction.UP).getRotation();
            case DECORATED_POT -> yaw(180.0F - horizontal(state).toYRot());
            case SIGN, BANNER -> yaw(-RotationSegment.convertToDegrees(state.getOptionalValue(BlockStateProperties.ROTATION_16).orElse(0)));
            case SKULL -> yaw(180.0F - RotationSegment.convertToDegrees(state.getOptionalValue(BlockStateProperties.ROTATION_16).orElse(0)));
            case CONDUIT -> new Quaternionf();
        };
    }

    private static Direction horizontal(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.SOUTH);
    }

    private static Quaternionf yaw(float degrees) {
        return Axis.YP.rotationDegrees(degrees);
    }
}
