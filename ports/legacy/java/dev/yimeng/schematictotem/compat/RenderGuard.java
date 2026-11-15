package dev.yimeng.schematictotem.compat;

/** A block rendered as part of a miniature must retain its ordinary item model. */
public final class RenderGuard {
    private static final ThreadLocal<Integer> PARTS = ThreadLocal.withInitial(() -> 0);
    private RenderGuard() { }
    public static boolean partsActive() { return PARTS.get() > 0; }
    public static void beginParts() { PARTS.set(PARTS.get() + 1); }
    public static void endParts() { PARTS.set(PARTS.get() - 1); }
}
