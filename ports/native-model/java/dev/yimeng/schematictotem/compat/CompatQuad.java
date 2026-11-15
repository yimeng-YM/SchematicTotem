package dev.yimeng.schematictotem.compat;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import java.util.List;

/** Material properties normalized for the shared miniature geometry. */
public record CompatQuad(Vector3fc position0, Vector3fc position1, Vector3fc position2, Vector3fc position3,
                         long packedUV0, long packedUV1, long packedUV2, long packedUV3,
                         int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, int lightEmission) {
    public Vector3fc position(int index) {
        return switch(index) { case 0 -> position0; case 1 -> position1; case 2 -> position2; case 3 -> position3; default -> throw new IndexOutOfBoundsException(index); };
    }
    public long packedUV(int index) {
        return switch(index) { case 0 -> packedUV0; case 1 -> packedUV1; case 2 -> packedUV2; case 3 -> packedUV3; default -> throw new IndexOutOfBoundsException(index); };
    }
    public boolean isTinted() { return tintIndex >= 0; }
    public static List<CompatQuad> wrap(List<BakedQuad> quads) {
        return quads.stream().map(q -> new CompatQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.materialInfo().tintIndex(), q.direction(),
                q.materialInfo().sprite(), q.materialInfo().shadeDirectionOverride() != null, q.materialInfo().lightEmission())).toList();
    }
}
