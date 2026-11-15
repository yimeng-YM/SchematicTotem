package dev.yimeng.schematictotem.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marks the first-person hand pass (vanilla's, and Iris's, which calls the same method). First-person mods such as
 * Punchy draw their own arms here and then the held item with a third-person context; inside this pass that means
 * "first person", so the miniature uses its first-person-mod pose. Wrapping the whole method keeps the mark correct
 * even if another mod cancels the vanilla code.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @WrapMethod(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V")
    private void schematictotem$markHandPass(float tickProgress, PoseStack matrices, SubmitNodeCollector queue, LocalPlayer player,
                                             int light, Operation<Void> original) {
        TotemModels.handPassDepth++;
        try {
            original.call(tickProgress, matrices, queue, player, light);
        } finally {
            TotemModels.handPassDepth--;
        }
    }
}
