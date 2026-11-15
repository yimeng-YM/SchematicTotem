package dev.yimeng.schematictotem.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yimeng.schematictotem.mesh.Miniature;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws a miniature's chests, beds, signs... with the game's item-form block-entity renderers, one per block. */
public final class BlockEntityPartsRenderer implements SpecialModelRenderer<List<Miniature.BlockEntityPart>> {
    public static final BlockEntityPartsRenderer INSTANCE = new BlockEntityPartsRenderer();

    private BlockEntityPartsRenderer() {
    }

    @Override
    public void render(@Nullable List<Miniature.BlockEntityPart> parts, ItemDisplayContext displayContext, PoseStack matrices,
                       SubmitNodeCollector queue, int light, int overlay, boolean glint, int outlineColor) {
        if (parts == null || parts.isEmpty()) return;
        SpecialBlockModelRenderer models = Minecraft.getInstance().getModelManager().specialBlockModelRenderer();
        for (Miniature.BlockEntityPart part : parts) {
            matrices.pushPose();
            matrices.translate(part.x(), part.y(), part.z());
            matrices.scale(part.scale(), part.scale(), part.scale());
            matrices.mulPose(part.rotation());
            matrices.translate(-0.5F, -0.5F, -0.5F);
            models.renderByBlock(part.state().getBlock(), displayContext, matrices, queue, light, overlay, outlineColor);
            matrices.popPose();
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        // bounds come from the quad layers
    }

    @Override
    public @Nullable List<Miniature.BlockEntityPart> extractArgument(ItemStack stack) {
        return null;
    }
}
