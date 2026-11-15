package dev.yimeng.schematictotem.compat;

/** Two texture coordinates kept without per-vertex allocation. */
public final class PackedUv {
    private PackedUv() { }
    public static long toLong(float x, float y) {
        return (Float.floatToRawIntBits(x) & 0xffffffffL) | ((long) Float.floatToRawIntBits(y) << 32);
    }
    public static float getX(long value) { return Float.intBitsToFloat((int) value); }
    public static float getY(long value) { return Float.intBitsToFloat((int) (value >>> 32)); }
    public static float u(net.minecraft.client.texture.Sprite sprite, float value) {
        return sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * value;
    }
    public static float v(net.minecraft.client.texture.Sprite sprite, float value) {
        return sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * value;
    }
}
