package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every item render (GUI, hands, other entities, item frames, dropped items, the totem pop animation, bundles)
 * resolves its model through ItemModelManager.update, so replacing the model here covers all of them - and works
 * whatever item_model a server or resource pack gives the totem.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void schematictotem$useMiniature(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                             @Nullable Level world, @Nullable ItemOwner heldItemContext, int seed, CallbackInfo ci) {
        if (TotemModels.INSTANCE.tryRender(renderState, stack, displayContext, heldItemContext)) ci.cancel();
    }

    /** Item frames and dropped items pass no holder; remember the entity so the level of detail can use its distance. */
    @Inject(method = "updateForNonLiving", at = @At("HEAD"))
    private void schematictotem$trackEntity(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity,
                                            CallbackInfo ci) {
        TotemModels.trackedEntity = entity;
    }

    @Inject(method = "updateForNonLiving", at = @At("RETURN"))
    private void schematictotem$untrackEntity(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity,
                                              CallbackInfo ci) {
        TotemModels.trackedEntity = null;
    }
}
