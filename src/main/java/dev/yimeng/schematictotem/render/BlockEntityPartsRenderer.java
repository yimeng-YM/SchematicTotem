package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.LoadedBlockEntityModels;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Draws a miniature's chests, beds, signs... with the game's item-form block-entity renderers, one per block. */
public final class BlockEntityPartsRenderer implements SpecialModelRenderer<List<Miniature.BlockEntityPart>> {
    public static final BlockEntityPartsRenderer INSTANCE = new BlockEntityPartsRenderer();

    private BlockEntityPartsRenderer() {
    }

    @Override
    public void render(@Nullable List<Miniature.BlockEntityPart> parts, ItemDisplayContext displayContext, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
        if (parts == null || parts.isEmpty()) return;
        LoadedBlockEntityModels models = MinecraftClient.getInstance().getBakedModelManager().getBlockEntityModelsSupplier();
        for (Miniature.BlockEntityPart part : parts) {
            matrices.push();
            matrices.translate(part.x(), part.y(), part.z());
            matrices.scale(part.scale(), part.scale(), part.scale());
            matrices.multiply(part.rotation());
            matrices.translate(-0.5F, -0.5F, -0.5F);
            models.render(part.state().getBlock(), displayContext, matrices, queue, light, overlay, outlineColor);
            matrices.pop();
        }
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer) {
        // bounds come from the quad layers
    }

    @Override
    public @Nullable List<Miniature.BlockEntityPart> getData(ItemStack stack) {
        return null;
    }
}
