package dev.yimeng.schematictotem.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marks the first-person hand pass (vanilla's, and Iris's, which calls the same method). First-person mods such as
 * Punchy draw their own arms here and then the held item with a third-person context; inside this pass that means
 * "first person", so the miniature uses its first-person-mod pose. Wrapping the whole method keeps the mark correct
 * even if another mod cancels the vanilla code.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @WrapMethod(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V")
    private void schematictotem$markHandPass(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player,
                                             int light, Operation<Void> original) {
        TotemModels.handPassDepth++;
        try {
            original.call(tickProgress, matrices, queue, player, light);
        } finally {
            TotemModels.handPassDepth--;
        }
    }
}
