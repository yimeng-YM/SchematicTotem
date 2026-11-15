package dev.yimeng.schematictotem.mesh;

import dev.yimeng.schematictotem.schematic.Volume;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/**
 * Guesses which side of a build is its front, for schematics nobody has turned by hand yet.
 * <ul>
 *   <li>Flat builds: map art lying on the ground faces up; a one-block-thin wall faces along its thin axis.</li>
 *   <li>Statues and most buildings are mirror-symmetric left to right, so the front lies on the axis across that
 *   mirror plane.</li>
 *   <li>Of the remaining sides, the one with the most detail (colour changes between neighbouring blocks seen from
 *   that side: faces, doors, windows) wins over plain backs.</li>
 * </ul>
 */
final class FrontGuess {
    private static final String[] SIDES = {"south", "north", "east", "west"};
    /** Sampling resolution: big builds are looked at through at most this many cells per axis. */
    private static final int MAX_SAMPLES = 96;

    private FrontGuess() {
    }

    static String guess(Volume volume, int[] b) {
        int dx = b[3] - b[0] + 1, dy = b[4] - b[1] + 1, dz = b[5] - b[2] + 1;
        int thin = Math.max(2, Math.max(dx, Math.max(dy, dz)) / 12);
        if (dy <= thin && dx > 3 * dy && dz > 3 * dy) return "up";
        int step = Math.max(1, (Math.max(dx, Math.max(dy, dz)) + MAX_SAMPLES - 1) / MAX_SAMPLES);
        int[] colors = new int[volume.paletteSize()];
        for (int i = 1; i < colors.length; i++) colors[i] = color(volume.state(i)) | 0x1000000; // never 0 for a block
        boolean alongZ = dz <= thin && dx > 3 * dz;
        boolean alongX = dx <= thin && dz > 3 * dx;
        if (!alongZ && !alongX) {
            double symX = symmetry(volume, b, step, true, colors); // mirror x: left/right is X, so the front is along Z
            double symZ = symmetry(volume, b, step, false, colors);
            if (symX > symZ + 0.05) alongZ = true;
            else if (symZ > symX + 0.05) alongX = true;
        }
        String best = "south";
        double bestScore = -1;
        for (String side : SIDES) {
            boolean zSide = side.equals("south") || side.equals("north");
            if (alongZ && !zSide || alongX && zSide) continue;
            double score = detail(volume, b, step, side, colors);
            if (score > bestScore + 1e-9) {
                bestScore = score;
                best = side;
            }
        }
        return best;
    }

    /** Share of sampled blocks whose mirror image across the build's centre plane holds the same colour. */
    private static double symmetry(Volume volume, int[] b, int step, boolean mirrorX, int[] colors) {
        long same = 0, total = 0;
        for (int y = b[1]; y <= b[4]; y += step) {
            for (int z = b[2]; z <= b[5]; z += step) {
                for (int x = b[0]; x <= b[3]; x += step) {
                    int idx = volume.get(x, y, z);
                    if (idx == 0) continue;
                    total++;
                    int mx = mirrorX ? b[0] + b[3] - x : x, mz = mirrorX ? z : b[2] + b[5] - z;
                    int other = volume.get(mx, y, mz);
                    if (other != 0 && colors[other] == colors[idx]) same++;
                }
            }
        }
        return total == 0 ? 0 : (double) same / total;
    }

    /** Colour changes between neighbouring pixels of the build seen from one side, per visible pixel. */
    private static double detail(Volume volume, int[] b, int step, String side, int[] colors) {
        boolean zSide = side.equals("south") || side.equals("north");
        int uMin = zSide ? b[0] : b[2], uMax = zSide ? b[3] : b[5];
        int dMin = zSide ? b[2] : b[0], dMax = zSide ? b[5] : b[3];
        boolean fromHigh = side.equals("south") || side.equals("east");
        int w = (uMax - uMin) / step + 1, h = (b[4] - b[1]) / step + 1;
        int[] image = new int[w * h];
        int visible = 0;
        for (int j = 0; j < h; j++) {
            int y = b[1] + j * step;
            for (int i = 0; i < w; i++) {
                int u = uMin + i * step;
                int pixel = 0;
                for (int k = 0; k <= dMax - dMin; k++) {
                    int d = fromHigh ? dMax - k : dMin + k;
                    int idx = zSide ? volume.get(u, y, d) : volume.get(d, y, u);
                    if (idx != 0) {
                        pixel = colors[idx];
                        break;
                    }
                }
                image[j * w + i] = pixel;
                if (pixel != 0) visible++;
            }
        }
        if (visible == 0) return 0;
        long changes = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int p = image[j * w + i];
                if (p == 0) continue;
                if (i + 1 < w && image[j * w + i + 1] != 0 && image[j * w + i + 1] != p) changes++;
                if (j + 1 < h && image[(j + 1) * w + i] != 0 && image[(j + 1) * w + i] != p) changes++;
            }
        }
        return (double) changes / visible;
    }

    private static int color(BlockState state) {
        try {
            MapColor color = state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            return color == null ? 0 : color.color;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
