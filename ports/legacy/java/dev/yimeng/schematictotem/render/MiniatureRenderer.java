package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jspecify.annotations.Nullable;

/** Direct item-render bridge for the versions preceding vanilla item render states. */
public final class MiniatureRenderer {
    private MiniatureRenderer() { }
    public static boolean render(ItemStack stack, ModelTransformationMode context, @Nullable LivingEntity held,
                                 @Nullable Entity holder, boolean leftHand, MatrixStack matrices, VertexConsumerProvider consumers,
                                 int light, int overlay, Miniature miniature, TotemConfig config, PoseComposer.Composed transforms) {
        boolean pop = context == ModelTransformationMode.FIXED && held == null && holder == null;
        boolean modFirst = TotemModels.handPassDepth > 0 &&
                (context == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND || context == ModelTransformationMode.THIRD_PERSON_LEFT_HAND);
        int lod = pop || modFirst ? 0 : level(context, held != null ? held : holder, miniature, config.detailPixels);
        Transformation transform = pop ? transforms.totemPop() : modFirst
                ? (leftHand ? transforms.firstPersonModLeft() : transforms.firstPersonModRight())
                : transforms.transforms().getTransformation(context);
        MinecraftClient client = MinecraftClient.getInstance();
        if (held == client.player || context.isFirstPerson() || modFirst) {
            PoseKind pose = modFirst ? (leftHand ? PoseKind.FIRST_PERSON_MOD_LEFT : PoseKind.FIRST_PERSON_MOD_RIGHT) : switch (context) {
                case FIRST_PERSON_RIGHT_HAND -> PoseKind.FIRST_PERSON_RIGHT;
                case FIRST_PERSON_LEFT_HAND -> PoseKind.FIRST_PERSON_LEFT;
                case THIRD_PERSON_RIGHT_HAND -> PoseKind.THIRD_PERSON_RIGHT;
                case THIRD_PERSON_LEFT_HAND -> PoseKind.THIRD_PERSON_LEFT;
                case GUI -> PoseKind.GUI;
                default -> null;
            };
            if (pose != null) TotemModels.markPoseUsed(pose);
        }
        matrices.push();
        try {
            transform.apply(leftHand, matrices);
            matrices.translate(-0.5F, -0.5F, -0.5F);
            MiniatureMeshRenderer.render(miniature.levels[lod], miniature.tints(client.world, config.tintBiome),
                    miniature.tintVersion(), matrices, consumers, light, overlay);
        } finally { matrices.pop(); }
        return true;
    }
    private static int level(ModelTransformationMode context, @Nullable Entity holder, Miniature miniature, float detailPixels) {
        if (context == ModelTransformationMode.GUI || context.isFirstPerson()) return 0;
        if (holder == null) return 1;
        MinecraftClient client = MinecraftClient.getInstance();
        double distance = Math.max(0.5, Math.sqrt(holder.getPos().squaredDistanceTo(client.gameRenderer.getCamera().getPos())));
        double fov = MathHelper.clamp(client.options.getFov().getValue(), 30, 110) * Math.PI / 180.0;
        double size = context == ModelTransformationMode.GROUND ? 0.35 : context == ModelTransformationMode.FIXED ? 0.45 : 0.5;
        double resolution = size * client.getWindow().getFramebufferHeight() / (2 * Math.tan(fov / 2) * distance * Math.max(0.5, detailPixels));
        for (int i = 0; i < miniature.levels.length; i++) if (miniature.levels[i].resolution() <= resolution) return i;
        return miniature.levels.length - 1;
    }
}
