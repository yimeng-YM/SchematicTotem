package dev.yimeng.schematictotem.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @WrapMethod(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V")
    private void schematictotem$markHands(float tick, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                         ClientPlayerEntity player, int light, Operation<Void> original) {
        TotemModels.handPassDepth++;
        try { original.call(tick, matrices, consumers, player, light); }
        finally { TotemModels.handPassDepth--; }
    }
}
