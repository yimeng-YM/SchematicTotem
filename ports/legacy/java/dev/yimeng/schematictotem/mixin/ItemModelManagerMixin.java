package dev.yimeng.schematictotem.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V", at = @At("HEAD"), cancellable = true)
    private void schematictotem$render(ItemStack stack, ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
                                      VertexConsumerProvider consumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (dev.yimeng.schematictotem.compat.RenderGuard.partsActive()) return;
        Entity owner = TotemModels.trackedEntity;
        if (TotemModels.INSTANCE.tryRender(stack, mode, owner instanceof LivingEntity living ? living : null,
                leftHand, matrices, consumers, light, overlay)) ci.cancel();
    }
    @WrapMethod(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V")
    private void schematictotem$owner(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHand,
                                     MatrixStack matrices, VertexConsumerProvider consumers, World world, int light, int overlay,
                                     int seed, Operation<Void> original) {
        Entity previous = TotemModels.trackedEntity;
        TotemModels.trackedEntity = entity;
        try { original.call(entity, stack, mode, leftHand, matrices, consumers, world, light, overlay, seed); }
        finally { TotemModels.trackedEntity = previous; }
    }
}
