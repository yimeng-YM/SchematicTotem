package dev.yimeng.schematictotem.mesh;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * One render layer of a detail level, flattened into plain arrays. Drawing it each frame is a tight loop: the
 * position matrix is read once, the six face normals are transformed once, and every vertex goes out through the
 * consumer's bulk vertex call - no per-quad objects, no per-quad normal or colour maths.
 */
public final class Mesh {
    public static final Mesh EMPTY = new Mesh(List.of());
    private static final Direction[] DIRS = Direction.values();

    private final int quads;
    private final float[] positions; // 12 per quad
    private final float[] uvs;       // 8 per quad
    private final byte[] faces;      // Direction ordinal
    private final short[] tints;     // tint table index, -1 = none
    private final byte[] emission;   // light emission 0..15
    /** Animated sprites used here (water, lava, sea lanterns...), for mods that only animate what is drawn. */
    public final Sprite[] animatedSprites;
    private int[] colors = new int[0];
    private int colorsVersion = Integer.MIN_VALUE;

    Mesh(List<BakedQuad> source) {
        quads = source.size();
        positions = new float[quads * 12];
        uvs = new float[quads * 8];
        faces = new byte[quads];
        tints = new short[quads];
        emission = new byte[quads];
        Set<Sprite> animated = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int q = 0; q < quads; q++) {
            BakedQuad quad = source.get(q);
            for (int k = 0; k < 4; k++) {
                Vector3fc p = quad.getPosition(k);
                positions[q * 12 + k * 3] = p.x();
                positions[q * 12 + k * 3 + 1] = p.y();
                positions[q * 12 + k * 3 + 2] = p.z();
                long uv = quad.getTexcoords(k);
                uvs[q * 8 + k * 2] = Vector2f.getX(uv);
                uvs[q * 8 + k * 2 + 1] = Vector2f.getY(uv);
            }
            faces[q] = (byte) quad.face().ordinal();
            tints[q] = (short) quad.tintIndex();
            emission[q] = (byte) quad.lightEmission();
            Sprite sprite = quad.sprite();
            if (sprite != null && sprite.getContents().isAnimated()) animated.add(sprite);
        }
        animatedSprites = animated.toArray(new Sprite[0]);
    }

    public int size() {
        return quads;
    }

    public boolean isEmpty() {
        return quads == 0;
    }

    /** Corner k of quad q, for tests and outline fitting. */
    public Vector3f position(int q, int k, Vector3f out) {
        int i = q * 12 + k * 3;
        return out.set(positions[i], positions[i + 1], positions[i + 2]);
    }

    /** Per-quad ARGB colours for the current tint table; rebuilt only when the table changes (render thread). */
    private int[] colors(int[] tintColors, int tintVersion) {
        if (colorsVersion != tintVersion || colors.length != quads) {
            int[] c = new int[quads];
            for (int q = 0; q < quads; q++) {
                int t = tints[q];
                c[q] = t >= 0 && t < tintColors.length ? tintColors[t] : 0xFFFFFFFF;
            }
            colors = c;
            colorsVersion = tintVersion;
        }
        return colors;
    }

    /** Writes every quad, transformed by the pose, into the consumer. */
    public void emit(MatrixStack.Entry pose, VertexConsumer consumer, int light, int overlay, int[] tintColors, int tintVersion) {
        if (quads == 0) return;
        int[] colors = colors(tintColors, tintVersion);
        Matrix4f m = pose.getPositionMatrix();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float[] normals = new float[18];
        Vector3f n = new Vector3f();
        for (Direction d : DIRS) {
            pose.transformNormal(d.getFloatVector(), n);
            normals[d.ordinal() * 3] = n.x;
            normals[d.ordinal() * 3 + 1] = n.y;
            normals[d.ordinal() * 3 + 2] = n.z;
        }
        float[] positions = this.positions, uvs = this.uvs;
        int p = 0, t = 0;
        for (int q = 0; q < quads; q++) {
            int color = colors[q];
            int f = faces[q] * 3;
            float nx = normals[f], ny = normals[f + 1], nz = normals[f + 2];
            int e = emission[q];
            int l = e == 0 ? light : LightmapTextureManager.applyEmission(light, e);
            for (int k = 0; k < 4; k++, p += 3, t += 2) {
                float x = positions[p], y = positions[p + 1], z = positions[p + 2];
                consumer.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                        color, uvs[t], uvs[t + 1], overlay, l, nx, ny, nz);
            }
        }
    }
}
