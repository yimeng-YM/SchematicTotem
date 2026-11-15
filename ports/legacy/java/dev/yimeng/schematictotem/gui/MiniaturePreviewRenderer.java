package dev.yimeng.schematictotem.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;

public final class MiniaturePreviewRenderer {
    private MiniaturePreviewRenderer() { }
    public static void draw(DrawContext context, ItemStack stack, ModelTransformationMode mode, Quaternionf view,
                            int x, int y, int size, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.draw();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + size * 0.5, y + size * 0.5, 150);
        matrices.scale(scale, -scale, scale);
        matrices.multiply(view);
        RenderSystem.enableDepthTest();
        if (mode == ModelTransformationMode.GUI) DiffuseLighting.disableGuiDepthLighting();
        else DiffuseLighting.enableGuiDepthLighting();
        try {
            var consumers = client.getBufferBuilders().getEntityVertexConsumers();
            client.getItemRenderer().renderItem(stack, mode, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV, matrices, consumers, client.world, 0);
            consumers.draw();
        } finally {
            matrices.pop();
            DiffuseLighting.enableGuiDepthLighting();
        }
    }
}
