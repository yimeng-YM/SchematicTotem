package dev.yimeng.schematictotem.compat;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Normalized geometry used by the shared baker across packed and unpacked vanilla quad formats. */
public record CompatQuad(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc v3,
                         long packedUV0, long packedUV1, long packedUV2, long packedUV3,
                         int tintIndex, Direction face, Sprite sprite, boolean shade, int lightEmission) {
    public Vector3fc getPosition(int index) {
        return switch (index) { case 0 -> v0; case 1 -> v1; case 2 -> v2; case 3 -> v3; default -> throw new IndexOutOfBoundsException(index); };
    }
    public long getTexcoords(int index) {
        return switch (index) { case 0 -> packedUV0; case 1 -> packedUV1; case 2 -> packedUV2; case 3 -> packedUV3; default -> throw new IndexOutOfBoundsException(index); };
    }
    public boolean hasTint() { return tintIndex >= 0; }
    public static CompatQuad fromVanilla(BakedQuad quad) {
        int[] data = quad.getVertexData();
        int stride = data.length / 4;
        Vector3fc[] vertices = new Vector3fc[4];
        long[] uv = new long[4];
        for (int i = 0; i < 4; i++) {
            int p = i * stride;
            vertices[i] = new Vector3f(Float.intBitsToFloat(data[p]), Float.intBitsToFloat(data[p + 1]), Float.intBitsToFloat(data[p + 2]));
            uv[i] = PackedUv.toLong(Float.intBitsToFloat(data[p + 4]), Float.intBitsToFloat(data[p + 5]));
        }
        return new CompatQuad(vertices[0], vertices[1], vertices[2], vertices[3], uv[0], uv[1], uv[2], uv[3],
                quad.getColorIndex(), quad.getFace(), quad.getSprite(), quad.hasShade(), 0);
    }
}
