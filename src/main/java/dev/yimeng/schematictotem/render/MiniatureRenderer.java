package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.mesh.Miniature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

/** Fills an item render state from a {@link Miniature}: picks the detail level and adds one layer that draws it. */
public final class MiniatureRenderer {
    private MiniatureRenderer() {
    }

    /**
     * GUI, first person and the totem pop animation get the full level. Totems held by others, in item frames or on
     * the ground get the finest level whose cells still cover {@code config.detailPixels} pixels on screen.
     * Returns false when there is nothing to draw.
     *
     * @param transformKey changes whenever the display transforms change, so cached GUI renders are redrawn
     */
    public static boolean render(ItemRenderState state, ItemStack stack, ItemDisplayContext context, @Nullable HeldItemContext held,
                                 @Nullable Entity holder, Miniature miniature, TotemConfig config, PoseComposer.Composed transforms,
                                 Object transformKey) {
        boolean pop = isTotemPop(context, held, holder);
        // Punchy-like mods draw first person themselves and pass the held item a third-person context
        boolean modFirstPerson = TotemModels.handPassDepth > 0
                && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        int level = pop || modFirstPerson ? 0 : level(context, held, holder, miniature, config.detailPixels);
        Miniature.Level lod = miniature.levels[level];
        if (lod.quadCount() == 0 && lod.blockEntities.isEmpty()) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        int[] tints = miniature.tints(client.world, config.tintBiome);
        // The GUI renders each distinct model key once into its item atlas and reuses it until the key changes.
        state.addModelKey(miniature);
        state.addModelKey(miniature.tintVersion());
        state.addModelKey(transformKey);
        state.addModelKey(level);
        Transformation transform;
        if (pop) {
            transform = transforms.totemPop();
        } else if (modFirstPerson) {
            transform = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND ? transforms.firstPersonModRight() : transforms.firstPersonModLeft();
        } else {
            transform = transforms.transforms().getTransformation(context);
        }
        if (held != null && held.getEntity() == client.player) {
            PoseKind used = modFirstPerson
                    ? (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND ? PoseKind.FIRST_PERSON_MOD_RIGHT : PoseKind.FIRST_PERSON_MOD_LEFT)
                    : switch (context) {
                        case FIRST_PERSON_RIGHT_HAND -> PoseKind.FIRST_PERSON_RIGHT;
                        case FIRST_PERSON_LEFT_HAND -> PoseKind.FIRST_PERSON_LEFT;
                        case THIRD_PERSON_RIGHT_HAND -> PoseKind.THIRD_PERSON_RIGHT;
                        case THIRD_PERSON_LEFT_HAND -> PoseKind.THIRD_PERSON_LEFT;
                        case GUI -> PoseKind.GUI;
                        default -> null;
                    };
            if (used != null) TotemModels.markPoseUsed(used);
        }
        ItemRenderState.LayerRenderState layer = state.newLayer();
        layer.setSpecialModel(MiniatureMeshRenderer.INSTANCE, new MiniatureMeshRenderer.Data(lod, tints, miniature.tintVersion()));
        layer.setTransform(transform);
        // Only the GUI reads this flag: side-lit items get vanilla's ITEMS_3D lights, which come from behind / above to
        // suit a block's top and sides and leave a model seen from the front at ~45% brightness. Front-lit (ITEMS_FLAT)
        // puts the front at ~94%, the top at 100% and the sides at ~50%, so the icon stays readable.
        layer.setUseLight(context != ItemDisplayContext.GUI);
        layer.setVertices(miniature.corners);
        if (miniature.particle != null) layer.setParticle(miniature.particle);
        return true;
    }

    /** The floating totem in the middle of the screen: FIXED with no entity around it (item frames always have one). */
    public static boolean isTotemPop(ItemDisplayContext context, @Nullable HeldItemContext held, @Nullable Entity holder) {
        return context == ItemDisplayContext.FIXED && held == null && holder == null;
    }

    /** Approximate size of the shown miniature in blocks, per context (vanilla's own scaling plus our default poses). */
    private static float worldSize(ItemDisplayContext context) {
        return switch (context) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.5F;
            case GROUND -> 0.35F;
            case FIXED -> 0.45F;
            case ON_SHELF -> 0.4F;
            case HEAD -> 0.9F;
            default -> 0.5F;
        };
    }

    static int level(ItemDisplayContext context, @Nullable HeldItemContext held, @Nullable Entity holder, Miniature miniature, float detailPixels) {
        if (context == ItemDisplayContext.GUI || context.isFirstPerson()) return 0;
        Vec3d pos = held != null ? held.getEntityPos() : holder != null ? holder.getEntityPos() : null;
        if (pos == null) return 1; // shelves and other renderers without an entity
        MinecraftClient client = MinecraftClient.getInstance();
        double distance = Math.max(0.5, Math.sqrt(pos.squaredDistanceTo(client.gameRenderer.getCamera().getCameraPos())));
        double fov = MathHelper.clamp(client.options.getFov().getValue(), 30, 110) * (Math.PI / 180.0);
        double pixelsPerBlock = client.getWindow().getFramebufferHeight() / (2.0 * Math.tan(fov / 2.0) * distance);
        double pixels = worldSize(context) * pixelsPerBlock;
        double maxResolution = pixels / Math.max(0.5F, detailPixels); // cells across that still get detailPixels each
        for (int i = 0; i < miniature.levels.length; i++) {
            if (miniature.levels[i].resolution() <= maxResolution) return i;
        }
        return miniature.levels.length - 1;
    }
}
