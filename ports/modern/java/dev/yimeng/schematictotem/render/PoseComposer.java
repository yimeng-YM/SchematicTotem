package dev.yimeng.schematictotem.render;

import dev.yimeng.schematictotem.config.TotemConfig;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Builds the display transforms of one miniature: the configured pose of each context, combined with the
 * schematic's own orientation (which side is its front, extra yaw / pitch / roll), its size and anchor.
 * <p>
 * Vanilla applies a transform as translate(t) · rotate(R) · scale(s) · translate(-0.5). Turning the model about its
 * centre first (O), scaling it by k and moving it by c gives translate(t + s·R·c) · rotate(R·O) · scale(s·k) ·
 * translate(-0.5), so everything folds into one vanilla {@link ItemTransform} per context - no re-bake needed.
 * <p>
 * Sizes are fitted to the model's actual outline (its mesh vertices), not to its bounding box, so a figure or a
 * round build fills the inventory slot instead of leaving the corners of its box empty.
 */
public final class PoseComposer {
    /** The inventory icon's outline fills this share of its slot. */
    public static final float GUI_FILL = 0.92F;
    /** Item frames and the totem pop animation, relative to a flat item in the same place. */
    public static final float FIXED_FILL = 0.95F;

    private PoseComposer() {
    }

    /**
     * @param firstPersonModRight first person drawn by Punchy-like mods (third-person context inside the hand pass)
     */
    public record Composed(ItemTransforms transforms, ItemTransform totemPop, ItemTransform firstPersonModRight,
                           ItemTransform firstPersonModLeft) {
    }

    /**
     * @param points model vertices in item space (the unit cube), at least the corners of the model's box
     */
    public static Composed compose(TotemConfig.Poses poses, TotemConfig.ModelOptions options, String autoFront, Vector3fc[] points) {
        Quaternionf orientation = orientation(options, autoFront);
        // the model's points relative to its centre, turned so its front faces +Z
        Vector3f[] turned = new Vector3f[points.length];
        for (int i = 0; i < points.length; i++) {
            turned[i] = new Vector3f(points[i]).sub(0.5F, 0.5F, 0.5F).rotate(orientation);
        }
        ItemTransforms transforms = new ItemTransforms(
                fit3d(poses.thirdPersonLeft, orientation, turned, options, true),
                fit3d(poses.thirdPersonRight, orientation, turned, options, false),
                fit3d(poses.firstPersonLeft, orientation, turned, options, true),
                fit3d(poses.firstPersonRight, orientation, turned, options, false),
                fit3d(poses.head, orientation, turned, options, false),
                fitProjected(poses.gui, orientation, turned, GUI_FILL, 1.0F),
                fit3d(poses.ground, orientation, turned, options, false),
                fitProjected(poses.fixed, orientation, turned, FIXED_FILL, options.size),
                fitProjected(poses.onShelf, orientation, turned, FIXED_FILL, options.size));
        return new Composed(transforms, fitProjected(poses.totemPop, orientation, turned, FIXED_FILL, options.size),
                fit3d(poses.firstPersonModRight, orientation, turned, options, false),
                fit3d(poses.firstPersonModLeft, orientation, turned, options, true));
    }

    /** Rotation turning the chosen side of the build towards +Z, then the extra yaw / pitch / roll. */
    public static Quaternionf orientation(TotemConfig.ModelOptions options, String autoFront) {
        String front = "auto".equals(options.front) ? autoFront : options.front;
        Quaternionf face = switch (front) {
            case "north" -> new Quaternionf().rotationY(Mth.PI);
            case "east" -> new Quaternionf().rotationY(-Mth.HALF_PI);
            case "west" -> new Quaternionf().rotationY(Mth.HALF_PI);
            case "up" -> new Quaternionf().rotationX(Mth.HALF_PI);
            case "down" -> new Quaternionf().rotationX(-Mth.HALF_PI);
            default -> new Quaternionf();
        };
        return new Quaternionf()
                .rotationY(options.yaw * Mth.DEG_TO_RAD)
                .rotateX(options.pitch * Mth.DEG_TO_RAD)
                .rotateZ(options.roll * Mth.DEG_TO_RAD)
                .mul(face);
    }

    /**
     * Hands, ground, head: the turned model is fitted into the item's unit cube, then scaled by size and the pose.
     * <p>
     * Left hands: vanilla mirrors a left-hand transform (negates translation x and rotation y / z), which would also
     * mirror the model's own turn and show its back. So the left hand gets the pre-mirrored turn and offset, and
     * vanilla's mirror brings them back: the pose is mirrored, the model still faces forward.
     */
    private static ItemTransform fit3d(TotemConfig.Pose pose, Quaternionf orientation, Vector3f[] turned, TotemConfig.ModelOptions options,
                                        boolean leftHand) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Vector3f v : turned) {
            min.min(v);
            max.max(v);
        }
        float extent = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));
        float k = (extent > 1e-4F ? 1.0F / extent : 1.0F) * options.size;
        Vector3f offset = new Vector3f(min).add(max).mul(-0.5F * k); // centre of the turned outline -> centre of the cube
        if ("bottom".equals(options.anchor)) offset.y = -0.5F - min.y * k; // stand on the bottom of the cube
        Quaternionf turn = orientation;
        if (leftHand) {
            turn = mirrorX(orientation);
            offset.x = -offset.x;
        }
        Quaternionf poseRotation = rotation(pose);
        Vector3f translation = new Vector3f(pose.translation[0], pose.translation[1], pose.translation[2]).div(16.0F)
                .add(new Vector3f(offset).mul(pose.scale).rotate(poseRotation));
        float s = pose.scale * k;
        return new ItemTransform(degrees(new Quaternionf(poseRotation).mul(turn)), translation, new Vector3f(s, s, s));
    }

    /**
     * GUI, item frames, the totem pop animation: the model is seen along the pose's view axis, so its projected
     * outline (x/y after the pose rotation) is centred and scaled to fill a share of the item's square.
     */
    private static ItemTransform fitProjected(TotemConfig.Pose pose, Quaternionf orientation, Vector3f[] turned, float fill, float size) {
        Quaternionf poseRotation = rotation(pose);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        Vector3f v = new Vector3f();
        for (Vector3f t : turned) {
            v.set(t).rotate(poseRotation);
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
            minZ = Math.min(minZ, v.z);
            maxZ = Math.max(maxZ, v.z);
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        float s = fill * size * pose.scale / (extent > 1e-4F ? extent : 1.0F);
        // translate(-0.5) puts the model centre on the origin; after the rotation its projected box is centred here
        Vector3f translation = new Vector3f(pose.translation[0], pose.translation[1], pose.translation[2]).div(16.0F)
                .sub((minX + maxX) * 0.5F * s, (minY + maxY) * 0.5F * s, (minZ + maxZ) * 0.5F * s);
        return new ItemTransform(degrees(new Quaternionf(poseRotation).mul(orientation)), translation, new Vector3f(s, s, s));
    }

    /** The same rotation mirrored across the YZ plane (what vanilla's left-hand handling does to a transform). */
    static Quaternionf mirrorX(Quaternionf q) {
        return new Quaternionf(q.x, -q.y, -q.z, q.w);
    }

    /** Same rotation as Transformation.apply builds from the pose's degrees. */
    public static Quaternionf rotation(TotemConfig.Pose pose) {
        return new Quaternionf().rotationXYZ(pose.rotation[0] * Mth.DEG_TO_RAD, pose.rotation[1] * Mth.DEG_TO_RAD,
                pose.rotation[2] * Mth.DEG_TO_RAD);
    }

    private static Vector3f degrees(Quaternionf q) {
        return q.getEulerAnglesXYZ(new Vector3f()).mul(Mth.RAD_TO_DEG);
    }
}
