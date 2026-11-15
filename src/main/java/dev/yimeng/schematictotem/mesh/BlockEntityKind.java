package dev.yimeng.schematictotem.mesh;

import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConduitBlock;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.RotationPropertyHelper;
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
        if (block instanceof SignBlock) return SIGN;       // standing signs only; wall signs have no item form without a post
        if (block instanceof BannerBlock) return BANNER;   // standing banners only
        if (block instanceof SkullBlock) return SKULL;     // floor skulls / heads only
        if (block instanceof ConduitBlock) return CONDUIT;
        return null;
    }

    /** Rotation about the block centre that turns the item-form model into the placed block, or null to draw nothing. */
    public Quaternionf rotation(BlockState state) {
        return switch (this) {
            case CHEST -> yaw(-horizontal(state).getPositiveHorizontalDegrees());
            case BED -> state.getOrEmpty(BedBlock.PART).orElse(BedPart.HEAD) == BedPart.HEAD
                    ? yaw(-horizontal(state).getPositiveHorizontalDegrees()) : null; // the item form already holds both halves
            case SHULKER_BOX -> state.getOrEmpty(Properties.FACING).orElse(Direction.UP).getRotationQuaternion();
            case DECORATED_POT -> yaw(180.0F - horizontal(state).getPositiveHorizontalDegrees());
            case SIGN, BANNER -> yaw(-RotationPropertyHelper.toDegrees(state.getOrEmpty(Properties.ROTATION).orElse(0)));
            case SKULL -> yaw(180.0F - RotationPropertyHelper.toDegrees(state.getOrEmpty(Properties.ROTATION).orElse(0)));
            case CONDUIT -> new Quaternionf();
        };
    }

    private static Direction horizontal(BlockState state) {
        return state.getOrEmpty(Properties.HORIZONTAL_FACING).orElse(Direction.SOUTH);
    }

    private static Quaternionf yaw(float degrees) {
        return RotationAxis.POSITIVE_Y.rotationDegrees(degrees);
    }
}
