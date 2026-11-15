package dev.yimeng.schematictotem.test;

/** Accumulates render-thread CPU time per frame (see the test mixins). */
public final class FrameTimer {
    private static long frameStart, handStart, emitStart, drawStart;
    private static long frames, frameNanos, handNanos, emitNanos, drawNanos, drawBytes;

    private FrameTimer() {
    }

    public static void frameStart() {
        frameStart = System.nanoTime();
    }

    public static void frameEnd() {
        frameNanos += System.nanoTime() - frameStart;
        frames++;
    }

    public static void handStart() {
        handStart = System.nanoTime();
    }

    public static void handEnd() {
        handNanos += System.nanoTime() - handStart;
    }

    public static void emitStart() {
        emitStart = System.nanoTime();
    }

    public static void emitEnd() {
        emitNanos += System.nanoTime() - emitStart;
    }

    public static void drawStart(long bytes) {
        drawStart = System.nanoTime();
        drawBytes += bytes;
    }

    public static void drawEnd() {
        drawNanos += System.nanoTime() - drawStart;
    }

    public static synchronized void reset() {
        frames = frameNanos = handNanos = emitNanos = drawNanos = drawBytes = 0;
    }

    /** {frames, ms/frame, hand pass ms/frame, item vertex writing ms/frame, miniature upload+draw ms/frame, miniature MB/frame} */
    public static synchronized double[] read() {
        double f = Math.max(1, frames);
        return new double[]{frames, frameNanos / 1e6 / f, handNanos / 1e6 / f, emitNanos / 1e6 / f, drawNanos / 1e6 / f, drawBytes / 1e6 / f};
    }
}
