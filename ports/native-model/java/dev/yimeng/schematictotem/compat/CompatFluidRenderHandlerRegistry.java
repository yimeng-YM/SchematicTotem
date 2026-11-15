package dev.yimeng.schematictotem.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;

public final class CompatFluidRenderHandlerRegistry {
    public static final CompatFluidRenderHandlerRegistry INSTANCE = new CompatFluidRenderHandlerRegistry();
    public CompatFluidRenderHandler get(Fluid fluid) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getModelManager() == null) return null;
        return new CompatFluidRenderHandler(client.getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState()));
    }
}
