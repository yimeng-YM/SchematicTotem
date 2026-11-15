package dev.yimeng.schematictotem.test.mixin;

import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.test.HandFrameProbe;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: the matrix a held totem is drawn in during the first-person hand pass, before its display transform. */
@Mixin(HeldItemRenderer.class)
public abstract class HandFrameProbeMixin {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void schematictotemtest$probe(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack matrices,
                                          OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (TotemModels.handPassDepth > 0 && stack.isOf(Items.TOTEM_OF_UNDYING)) HandFrameProbe.record(context, matrices.peek().getPositionMatrix());
    }
}
