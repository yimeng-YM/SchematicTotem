package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow @Final private Minecraft minecraft;
    @Inject(method = "onDrop", at = @At("HEAD"), cancellable = true)
    private void schematictotem$drop(long window, List<String> names, CallbackInfo ci) {
        List<Path> paths = new ArrayList<>();
        for (String name : names) {
            try { paths.add(Path.of(name)); } catch (RuntimeException ignored) { }
        }
        List<Path> rest = TotemModels.INSTANCE.handleDroppedFiles(paths);
        if (rest.size() == paths.size()) return;
        ci.cancel();
        if (!rest.isEmpty() && minecraft.gui.screen() != null) minecraft.gui.screen().onFilesDrop(rest);
    }
}
