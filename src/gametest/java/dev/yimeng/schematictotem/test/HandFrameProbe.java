package dev.yimeng.schematictotem.test;

import net.minecraft.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Test only: the view-space frame a first-person mod (or vanilla) draws the held totem in, and the pose that makes a
 * model stand upright in it facing the camera.
 */
public final class HandFrameProbe {
    private static final Map<ItemDisplayContext, Matrix4f> FRAMES = new EnumMap<>(ItemDisplayContext.class);
    /** Scale of the matrix the miniature itself was last drawn with in the hand pass, per hand (right, left). */
    private static final float[] DRAWN_SCALE = new float[2];

    public static synchronized void recordDrawn(boolean rightHand, Matrix4fc matrix) {
        DRAWN_SCALE[rightHand ? 0 : 1] = matrix.getScale(new Vector3f()).x;
    }

    public static synchronized float drawnScale(boolean rightHand) {
        return DRAWN_SCALE[rightHand ? 0 : 1];
    }

    private HandFrameProbe() {
    }

    public static synchronized void record(ItemDisplayContext context, Matrix4fc frame) {
        FRAMES.put(context, new Matrix4f(frame));
    }

    public static synchronized @Nullable Matrix4f frame(ItemDisplayContext context) {
        Matrix4f m = FRAMES.get(context);
        return m == null ? null : new Matrix4f(m);
    }

    public static synchronized void clear() {
        FRAMES.clear();
    }

    /**
     * Pose rotation (degrees, XYZ like display transforms) that turns the model's front towards the camera, turned by
     * yaw about the view's up axis. Left hands get vanilla's mirror undone: the applied rotation is mirrorX(pose).
     */
    public static Vector3f facingCamera(Matrix4fc frame, float yawDegrees, boolean leftHand) {
        Quaternionf hand = frame.getUnnormalizedRotation(new Quaternionf());
        Quaternionf wanted = new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
        Quaternionf pose = hand.conjugate().mul(wanted);
        if (leftHand) pose = new Quaternionf(pose.x, -pose.y, -pose.z, pose.w);
        return pose.getEulerAnglesXYZ(new Vector3f()).mul((float) (180.0 / Math.PI));
    }

    /** Pose translation (1/16 block, hand frame) that moves the model up the screen by {@code up} hand units. */
    public static Vector3f upOnScreen(Matrix4fc frame, float up, boolean leftHand) {
        Quaternionf hand = frame.getUnnormalizedRotation(new Quaternionf());
        Vector3f t = new Vector3f(0, up, 0).rotate(hand.conjugate()).mul(16.0F);
        if (leftHand) t.x = -t.x;
        return t;
    }

    public static String describe(Matrix4fc frame) {
        Vector3f euler = frame.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesXYZ(new Vector3f()).mul((float) (180.0 / Math.PI));
        Vector3f scale = frame.getScale(new Vector3f());
        Vector3f origin = frame.getTranslation(new Vector3f());
        return String.format(Locale.ROOT, "rotationXYZ %.1f %.1f %.1f, scale %.3f %.3f %.3f, origin %.3f %.3f %.3f",
                euler.x, euler.y, euler.z, scale.x, scale.y, scale.z, origin.x, origin.y, origin.z);
    }
}
