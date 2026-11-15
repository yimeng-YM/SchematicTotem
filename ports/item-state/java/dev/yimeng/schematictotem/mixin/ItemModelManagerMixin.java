package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void schematictotem$replace(ItemRenderState state, ItemStack stack, ModelTransformationMode context, World world,
                                       LivingEntity owner, int seed, CallbackInfo ci) {
        if (dev.yimeng.schematictotem.compat.RenderGuard.partsActive()) return;
        if (TotemModels.INSTANCE.tryRender(state, stack, context, owner)) ci.cancel();
    }
    @Inject(method = "updateForNonLivingEntity", at = @At("HEAD"))
    private void schematictotem$owner(ItemRenderState state, ItemStack stack, ModelTransformationMode context, Entity entity, CallbackInfo ci) {
        TotemModels.trackedEntity = entity;
    }
    @Inject(method = "updateForNonLivingEntity", at = @At("RETURN"))
    private void schematictotem$clear(ItemRenderState state, ItemStack stack, ModelTransformationMode context, Entity entity, CallbackInfo ci) {
        TotemModels.trackedEntity = null;
    }
}
