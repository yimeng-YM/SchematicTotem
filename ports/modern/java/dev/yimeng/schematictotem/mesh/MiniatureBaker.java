package dev.yimeng.schematictotem.mesh;

import dev.yimeng.schematictotem.schematic.Volume;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Turns a {@link Volume} into a {@link Miniature}. Runs on a worker thread.
 * <ul>
 *   <li>Only faces that can be seen are kept: faces against opaque blocks, between leaves, and toward spaces that
 *   cannot be reached from outside the build (sealed rooms, hollow terrain) are dropped.</li>
 *   <li>Each detail level must fit a quad budget (full budget, then 1/4, 1/16, 1/64). When a level does not fit,
 *   the build is re-sampled into coarser cells of f blocks (f = 2^(k/6), so each step costs about a fifth of the
 *   quads instead of the three quarters a jump from 1 to 2 blocks would): every cell becomes one block, chosen by a
 *   vote weighted by how much of the cell each block fills and by whether it is on the surface, so roofs and grass
 *   stay on top.</li>
 *   <li>Quads are copied from the game's block models, translated and scaled into the item's unit cube, with
 *   shared corner vectors and biome tints resolved through a small tint table.</li>
 * </ul>
 */
public final class MiniatureBaker {
    /** No level is sampled finer than this many cells along its longest side; more detail cannot be seen in an item. */
    public static final int GRID_LIMIT = 256;
    /** Sampling factors f = 2^(step / STEPS_PER_OCTAVE). */
    private static final int STEPS_PER_OCTAVE = 6;
    /**
     * Safety limits of the 1:1 level: above them a full-detail schematic gets the finest factor that fits instead
     * (a million quads is ~90 MB of mesh and tens of ms per frame in hand; 100M cells is a 200 MB grid).
     */
    public static final long FULL_DETAIL_MAX_QUADS = 1_000_000L;
    public static final long FULL_DETAIL_MAX_CELLS = 100_000_000L;
    private static final Direction[] DIRS = Direction.values();
    private static final int[] DX = new int[6], DY = new int[6], DZ = new int[6];

    static {
        for (Direction d : DIRS) {
            DX[d.ordinal()] = d.getStepX();
            DY[d.ordinal()] = d.getStepY();
            DZ[d.ordinal()] = d.getStepZ();
        }
    }

    /** @param fullDetail the finest level is the schematic itself, 1:1 with no resampling, whatever its quad count */
    public record Settings(int quadBudget, boolean cullSealedInterior, boolean blockEntities, int maxBlockEntities, boolean glowingBlocks,
                           boolean fullDetail) {
    }

    private MiniatureBaker() {
    }

    public static float factorOf(int step) {
        return step == 0 ? 1.0F : (float) Math.pow(2.0, (double) step / STEPS_PER_OCTAVE);
    }

    public static Miniature bake(Volume volume, Settings settings, String name, BlockModelShaper models, Object resourceToken,
                                 BooleanSupplier cancelled) {
        long start = System.nanoTime();
        if (volume.isEmpty()) throw new IllegalArgumentException("The schematic contains no blocks");
        StateInfo[] info = analyze(volume, models);
        int[] b = volume.bounds();
        int dx = b[3] - b[0] + 1, dy = b[4] - b[1] + 1, dz = b[5] - b[2] + 1;
        int maxDim = Math.max(dx, Math.max(dy, dz));
        Space space = new Space(b, dx, dy, dz, maxDim);
        String front = FrontGuess.guess(volume, b);

        Map<Integer, Grid> grids = new HashMap<>();
        Level[] levels = new Level[4];
        TintTable tints = new TintTable();
        Map<Vector3f, Vector3f> interner = new HashMap<>();
        int step = settings.fullDetail() || maxDim <= GRID_LIMIT ? 0 : (int) Math.ceil(STEPS_PER_OCTAVE * log2((double) maxDim / GRID_LIMIT) - 1e-9);
        Level previous = null;
        Grid previousGrid = null;
        TextureAtlasSprite particle = null;
        long base = settings.quadBudget();
        for (int level = 0; level < levels.length; level++) {
            Grid grid;
            if (settings.fullDetail() && level == 0) {
                long cells = (long) dx * dy * dz;
                Grid exact = cells <= FULL_DETAIL_MAX_CELLS ? grid(volume, info, b, 0, settings.cullSealedInterior(), grids, cancelled) : null;
                if (exact != null && exact.quadCount <= FULL_DETAIL_MAX_QUADS) {
                    grid = exact;
                } else {
                    // too big to draw 1:1: the finest factor under the safety limits
                    int from = exact != null ? 0
                            : (int) Math.ceil(STEPS_PER_OCTAVE * log2(Math.cbrt((double) cells / FULL_DETAIL_MAX_CELLS)) - 1e-9);
                    grid = fit(volume, info, b, from, FULL_DETAIL_MAX_QUADS, settings.cullSealedInterior(), grids, cancelled);
                }
                base = Math.max(64L, grid.quadCount); // coarser levels for distance still shrink from here
            } else {
                long budget = Math.max(64L, base >> (2 * level));
                grid = fit(volume, info, b, step, budget, settings.cullSealedInterior(), grids, cancelled);
            }
            step = grid.step;
            if (previous != null && previousGrid == grid) {
                levels[level] = previous;
                continue;
            }
            grids.keySet().removeIf(s -> s < grid.step); // finer grids are never needed again
            levels[level] = previous = buildLevel(grid, info, space, settings, models, tints, interner, cancelled);
            previousGrid = grid;
            if (level == 0) particle = mostCommonParticle(grid, info, models);
        }
        if (levels[0].solid.isEmpty() && levels[0].translucent.isEmpty() && levels[0].blockEntities.isEmpty()) {
            throw new IllegalArgumentException("The schematic only contains blocks that are not drawn (light, barrier, structure void...)");
        }

        long bakeMillis = (System.nanoTime() - start) / 1_000_000L;
        Miniature.Stats stats = new Miniature.Stats(name, dx, dy, dz, volume.nonAirCount(), volume.paletteSize() - 1, bakeMillis,
                volume.allocatedBytes());
        Vector3fc[] outline = outline(levels[0], space.corners());
        return new Miniature(toPublic(levels), tints.keys(), space.corners(), outline, particle, resourceToken, stats, front);
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2.0);
    }

    // ------------------------------------------------------------------------------------------------ per-state info

    static final class StateInfo {
        final BlockState state;
        /** Hides neighbours' faces and stops the outside flood: opaque full cubes, and leaves (as with "fast" leaves). */
        final boolean opaque;
        final boolean model;
        final boolean translucent;
        final @Nullable FluidState fluid;
        final boolean fluidTranslucent;
        final TextureAtlasSprite @Nullable [] fluidSprites;
        /** Share of a cell the block fills (0 = never chosen when re-sampling). */
        final float weight;
        /** Quads per culled face, [6] = unculled. */
        final int[] quads = new int[7];
        final @Nullable BlockEntityKind blockEntity;
        final int luminance;

        StateInfo(BlockState state, boolean air, BlockModelShaper models, RandomSource random, List<BlockModelPart> parts) {
            this.state = state;
            if (air) {
                opaque = model = translucent = fluidTranslucent = false;
                fluid = null;
                fluidSprites = null;
                weight = 0.0F;
                blockEntity = null;
                luminance = 0;
                return;
            }
            opaque = state.getBlock() instanceof LeavesBlock || state.isSolidRender();
            blockEntity = BlockEntityKind.of(state);
            luminance = state.getLightEmission();

            FluidState fluidState = state.getFluidState();
            TextureAtlasSprite[] sprites = null;
            if (!fluidState.isEmpty()) {
                FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluidState.getType());
                if (handler != null) {
                    try {
                        sprites = handler.getFluidSprites(null, null, fluidState);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (sprites != null && (sprites.length < 2 || sprites[0] == null || sprites[1] == null)) sprites = null;
            }
            fluid = sprites == null ? null : fluidState;
            fluidSprites = sprites;
            fluidTranslucent = fluid != null && ItemBlockRenderTypes.getRenderLayer(fluidState) == ChunkSectionLayer.TRANSLUCENT;

            int total = 0;
            if (state.getRenderShape() == RenderShape.MODEL) {
                try {
                    parts.clear();
                    random.setSeed(42L);
                    models.getBlockModel(state).collectParts(random, parts);
                    for (BlockModelPart part : parts) {
                        for (Direction d : DIRS) quads[d.ordinal()] += part.getQuads(d).size();
                        quads[6] += part.getQuads(null).size();
                    }
                    for (int q : quads) total += q;
                } catch (RuntimeException e) {
                    Arrays.fill(quads, 0); // a broken (modded) model: draw nothing for this block
                    total = 0;
                }
            }
            model = total > 0;
            translucent = model && ItemBlockRenderTypes.getChunkRenderType(state) == ChunkSectionLayer.TRANSLUCENT;
            weight = weight(state, model, fluid != null);
        }

        private static float weight(BlockState state, boolean model, boolean fluid) {
            if (!model) return fluid ? 0.9F : 0.0F;
            double volume;
            try {
                volume = 0.0;
                for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                    volume += box.getXsize() * box.getYsize() * box.getZsize();
                }
            } catch (RuntimeException e) {
                volume = 0.5;
            }
            if (fluid) volume = Math.max(volume, 0.9);
            return (float) Mth.clamp(volume, 0.08, 1.0);
        }
    }

    private static StateInfo[] analyze(Volume volume, BlockModelShaper models) {
        StateInfo[] info = new StateInfo[volume.paletteSize()];
        RandomSource random = RandomSource.create(42L);
        List<BlockModelPart> parts = new ArrayList<>();
        for (int i = 0; i < info.length; i++) info[i] = new StateInfo(volume.state(i), i == 0, models, random, parts);
        return info;
    }

    // ------------------------------------------------------------------------------------------------ grids

    /** A dense grid of palette indices at one sampling factor, centred on the build's bounds. */
    static final class Grid {
        final int step;
        final float factor;
        final int nx, ny, nz;
        final int originX, originY, originZ;
        final char[] cells;
        final boolean tagsBound = tagsBound();
        @Nullable BitSet outside;
        long quadCount;

        Grid(int step, float factor, int nx, int ny, int nz, int originX, int originY, int originZ) {
            this.step = step;
            this.factor = factor;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.cells = new char[nx * ny * nz];
        }

        int index(int x, int y, int z) {
            return (y * nz + z) * nx + x;
        }

        int get(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) return 0;
            return cells[(y * nz + z) * nx + x];
        }

        boolean minimal() {
            return nx <= 1 && ny <= 1 && nz <= 1;
        }

        /** Marks every non-opaque cell connected to the outside of the box. */
        void flood(StateInfo[] info) {
            BitSet out = new BitSet(cells.length);
            IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    boolean edgeYZ = y == 0 || y == ny - 1 || z == 0 || z == nz - 1;
                    for (int x = 0; x < nx; x += edgeYZ ? 1 : Math.max(1, nx - 1)) {
                        seed(info, out, queue, index(x, y, z));
                    }
                }
            }
            int layer = nx * nz;
            while (!queue.isEmpty()) {
                int i = queue.dequeueInt();
                int x = i % nx;
                int t = i / nx;
                int z = t % nz;
                int y = t / nz;
                if (x > 0) seed(info, out, queue, i - 1);
                if (x < nx - 1) seed(info, out, queue, i + 1);
                if (z > 0) seed(info, out, queue, i - nx);
                if (z < nz - 1) seed(info, out, queue, i + nx);
                if (y > 0) seed(info, out, queue, i - layer);
                if (y < ny - 1) seed(info, out, queue, i + layer);
            }
            outside = out;
        }

        private void seed(StateInfo[] info, BitSet out, IntArrayFIFOQueue queue, int i) {
            if (!out.get(i) && !info[cells[i]].opaque) {
                out.set(i);
                queue.enqueue(i);
            }
        }

        boolean faceVisible(StateInfo[] info, StateInfo self, int x, int y, int z, int d) {
            int x2 = x + DX[d], y2 = y + DY[d], z2 = z + DZ[d];
            if (x2 < 0 || y2 < 0 || z2 < 0 || x2 >= nx || y2 >= ny || z2 >= nz) return true;
            int j = (y2 * nz + z2) * nx + x2;
            int other = cells[j];
            StateInfo n = info[other];
            if (n.opaque) return false;
            if (outside != null && !outside.get(j)) return false;
            return other == 0 || drawSide(self.state, n.state, DIRS[d], tagsBound);
        }

        boolean innerVisible(StateInfo self, int i) {
            return self.opaque || outside == null || outside.get(i);
        }

        boolean fluidFaceVisible(StateInfo[] info, StateInfo self, int x, int y, int z, int d) {
            int x2 = x + DX[d], y2 = y + DY[d], z2 = z + DZ[d];
            if (x2 < 0 || y2 < 0 || z2 < 0 || x2 >= nx || y2 >= ny || z2 >= nz) return true;
            int j = (y2 * nz + z2) * nx + x2;
            StateInfo n = info[cells[j]];
            if (n.fluid != null && n.fluid.getType().isSame(self.fluid.getType())) return false;
            if (n.opaque) return false;
            return outside == null || outside.get(j);
        }

        long countQuads(StateInfo[] info) {
            long total = 0;
            int i = 0;
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++, i++) {
                        int idx = cells[i];
                        if (idx == 0) continue;
                        StateInfo s = info[idx];
                        if (s.model) {
                            for (int d = 0; d < 6; d++) if (s.quads[d] > 0 && faceVisible(info, s, x, y, z, d)) total += s.quads[d];
                            if (s.quads[6] > 0 && innerVisible(s, i)) total += s.quads[6];
                        }
                        if (s.fluid != null) {
                            for (int d = 0; d < 6; d++) if (fluidFaceVisible(info, s, x, y, z, d)) total++;
                        }
                    }
                }
            }
            return total;
        }
    }

    /** The finest sampling step (not finer than start) whose quad count fits the budget. */
    private static Grid fit(Volume volume, StateInfo[] info, int[] b, int start, long budget, boolean cull, Map<Integer, Grid> cache,
                            BooleanSupplier cancelled) {
        int step = start;
        int tooFine = start - 1;
        Grid grid = grid(volume, info, b, step, cull, cache, cancelled);
        while (grid.quadCount > budget && !grid.minimal()) {
            tooFine = step;
            // quads shrink with the square of the factor: jump close to the answer, then search the gap
            int jump = (int) Math.ceil(STEPS_PER_OCTAVE * 0.5 * log2((double) grid.quadCount / budget));
            step += Math.max(1, jump);
            grid = grid(volume, info, b, step, cull, cache, cancelled);
        }
        while (step - tooFine > 1 && tooFine >= start) {
            int mid = (tooFine + step) >>> 1;
            Grid candidate = grid(volume, info, b, mid, cull, cache, cancelled);
            if (candidate.quadCount <= budget) {
                step = mid;
                grid = candidate;
            } else {
                tooFine = mid;
            }
        }
        return grid;
    }

    private static Grid grid(Volume volume, StateInfo[] info, int[] b, int step, boolean cull, Map<Integer, Grid> cache,
                             BooleanSupplier cancelled) {
        Grid cached = cache.get(step);
        if (cached != null) return cached;
        checkCancelled(cancelled);
        Grid grid = sample(volume, info, b, step, cancelled);
        if (cull) grid.flood(info);
        grid.quadCount = grid.countQuads(info);
        cache.put(step, grid);
        return grid;
    }

    private static Grid sample(Volume volume, StateInfo[] info, int[] b, int step, BooleanSupplier cancelled) {
        float f = factorOf(step);
        int dx = b[3] - b[0] + 1, dy = b[4] - b[1] + 1, dz = b[5] - b[2] + 1;
        int nx = cells(dx, f), ny = cells(dy, f), nz = cells(dz, f);
        Grid grid = new Grid(step, f, nx, ny, nz,
                b[0] - Math.round((nx * f - dx) * 0.5F),
                b[1] - Math.round((ny * f - dy) * 0.5F),
                b[2] - Math.round((nz * f - dz) * 0.5F));
        char[] cells = grid.cells;

        if (step == 0) {
            // one block per cell: copy the allocated sections only
            volume.forEachSection((baseX, baseY, baseZ, data) -> {
                for (int j = 0; j < data.length; j++) {
                    int idx = data[j];
                    if (idx == 0) continue;
                    int x = baseX + (j & 15) - grid.originX, y = baseY + (j >> 8) - grid.originY, z = baseZ + ((j >> 4) & 15) - grid.originZ;
                    if (x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz) cells[grid.index(x, y, z)] = (char) idx;
                }
            });
            return grid;
        }

        // cell c covers the blocks whose corner lies in [c*f, (c+1)*f)
        int[] xs = bounds(nx, f, grid.originX), ys = bounds(ny, f, grid.originY), zs = bounds(nz, f, grid.originZ);
        float[] score = new float[info.length];
        int[] touched = new int[info.length];
        float needed = 0.5F * f * f; // half of a one-block-thick wall through the cell
        int i = 0;
        for (int cy = 0; cy < ny; cy++) {
            checkCancelled(cancelled);
            for (int cz = 0; cz < nz; cz++) {
                for (int cx = 0; cx < nx; cx++, i++) {
                    int x0 = xs[cx], x1 = xs[cx + 1], y0 = ys[cy], y1 = ys[cy + 1], z0 = zs[cz], z1 = zs[cz + 1];
                    if (x0 >= x1 || y0 >= y1 || z0 >= z1 || !volume.mayContainBlocks(x0, y0, z0, x1, y1, z1)) continue;
                    int n = 0;
                    float filled = 0.0F;
                    for (int y = y0; y < y1; y++) {
                        for (int z = z0; z < z1; z++) {
                            for (int x = x0; x < x1; x++) {
                                int idx = volume.get(x, y, z);
                                if (idx == 0) continue;
                                StateInfo s = info[idx];
                                if (s.weight <= 0.0F) continue;
                                filled += s.weight;
                                if (score[idx] == 0.0F) touched[n++] = idx;
                                score[idx] += exposed(volume, info, x, y, z) ? s.weight * 4.0F : s.weight;
                            }
                        }
                    }
                    if (n == 0) continue;
                    int best = 0;
                    float bestScore = 0.0F;
                    for (int k = 0; k < n; k++) {
                        int idx = touched[k];
                        if (score[idx] > bestScore) {
                            bestScore = score[idx];
                            best = idx;
                        }
                        score[idx] = 0.0F;
                    }
                    if (filled >= needed) cells[i] = (char) best;
                }
            }
        }
        return grid;
    }

    private static int cells(int blocks, float f) {
        return Math.max(1, (int) Math.ceil(blocks / f - 1e-4));
    }

    /** First block of each cell along one axis (n + 1 entries, absolute coordinates). */
    private static int[] bounds(int n, float f, int origin) {
        int[] out = new int[n + 1];
        for (int c = 0; c <= n; c++) out[c] = origin + (int) Math.ceil(c * f - 1e-4);
        return out;
    }

    /**
     * Block.shouldDrawSide, which consults block tags for panes and walls. Tags are unbound on the title screen and
     * while switching servers; then fall back to the culling shapes plus "same see-through block" (glass next to glass).
     */
    static boolean drawSide(BlockState self, BlockState other, Direction side, boolean tagsBound) {
        if (tagsBound) {
            try {
                return Block.shouldRenderFace(self, other, side);
            } catch (RuntimeException ignored) {
            }
        }
        if (other.getFaceOcclusionShape(side.getOpposite()) == Shapes.block()) return false;
        return !(self.getBlock() == other.getBlock() && !self.canOcclude());
    }

    static boolean tagsBound() {
        try {
            Blocks.STONE.defaultBlockState().is(BlockTags.WALLS);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean exposed(Volume volume, StateInfo[] info, int x, int y, int z) {
        for (int d = 0; d < 6; d++) {
            if (!info[volume.get(x + DX[d], y + DY[d], z + DZ[d])].opaque) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ meshing

    /** Maps schematic block coordinates into the item's unit cube, centred; turning happens at display time. */
    private static final class Space {
        final int minX, minY, minZ;
        final float scale, offX, offY, offZ;
        final float sizeX, sizeY, sizeZ;

        Space(int[] b, int dx, int dy, int dz, int maxDim) {
            minX = b[0];
            minY = b[1];
            minZ = b[2];
            scale = 1.0F / maxDim;
            sizeX = dx * scale;
            sizeY = dy * scale;
            sizeZ = dz * scale;
            offX = (1.0F - sizeX) * 0.5F;
            offY = (1.0F - sizeY) * 0.5F;
            offZ = (1.0F - sizeZ) * 0.5F;
        }

        /** Block coordinates -> model space; writes into out. */
        void map(float x, float y, float z, float[] out) {
            out[0] = (x - minX) * scale + offX;
            out[1] = (y - minY) * scale + offY;
            out[2] = (z - minZ) * scale + offZ;
        }

        Vector3fc[] corners() {
            Vector3fc[] corners = new Vector3fc[8];
            for (int i = 0; i < 8; i++) {
                corners[i] = new Vector3f((i & 1) == 0 ? offX : offX + sizeX, (i & 2) == 0 ? offY : offY + sizeY,
                        (i & 4) == 0 ? offZ : offZ + sizeZ);
            }
            return corners;
        }
    }

    private static final class TintTable {
        private final Object2IntOpenHashMap<Miniature.TintKey> ids = new Object2IntOpenHashMap<>();
        private final List<Miniature.TintKey> keys = new ArrayList<>();

        TintTable() {
            ids.defaultReturnValue(-1);
        }

        int id(BlockState state, int tintIndex, boolean fluid) {
            Miniature.TintKey key = new Miniature.TintKey(state, fluid ? 0 : tintIndex, fluid);
            int id = ids.getInt(key);
            if (id < 0) {
                id = keys.size();
                keys.add(key);
                ids.put(key, id);
            }
            return id;
        }

        Miniature.TintKey[] keys() {
            return keys.toArray(new Miniature.TintKey[0]);
        }
    }

    /** Mutable level while it is being built. */
    private static final class Level {
        final float factor;
        final int gridX, gridY, gridZ;
        final List<BakedQuad> solid = new ArrayList<>();
        final List<BakedQuad> translucent = new ArrayList<>();
        final List<Miniature.BlockEntityPart> blockEntities = new ArrayList<>();

        Level(Grid grid) {
            factor = grid.factor;
            gridX = grid.nx;
            gridY = grid.ny;
            gridZ = grid.nz;
        }
    }

    @SuppressWarnings("deprecation") // MathHelper.hashCode is what the game itself seeds model variants with
    private static Level buildLevel(Grid grid, StateInfo[] info, Space space, Settings settings, BlockModelShaper models, TintTable tints,
                                    Map<Vector3f, Vector3f> interner, BooleanSupplier cancelled) {
        Level level = new Level(grid);
        float f = grid.factor;
        RandomSource random = RandomSource.create();
        List<BlockModelPart> parts = new ArrayList<>();
        boolean[] visible = new boolean[6];
        float[] p = new float[3];
        Vector3fc[] corners = new Vector3fc[4];
        boolean blockEntities = settings.blockEntities() && grid.step == 0;
        int i = 0;
        for (int y = 0; y < grid.ny; y++) {
            checkCancelled(cancelled);
            for (int z = 0; z < grid.nz; z++) {
                for (int x = 0; x < grid.nx; x++, i++) {
                    int idx = grid.cells[i];
                    if (idx == 0) continue;
                    StateInfo s = info[idx];
                    float bx = grid.originX + x * f, by = grid.originY + y * f, bz = grid.originZ + z * f;
                    int emission = settings.glowingBlocks() ? s.luminance : 0;

                    if (s.model) {
                        boolean any = false;
                        for (int d = 0; d < 6; d++) any |= visible[d] = s.quads[d] > 0 && grid.faceVisible(info, s, x, y, z, d);
                        boolean inner = s.quads[6] > 0 && grid.innerVisible(s, i);
                        if (any || inner) {
                            parts.clear();
                            random.setSeed(Mth.getSeed((int) bx, (int) by, (int) bz));
                            models.getBlockModel(s.state).collectParts(random, parts);
                            List<BakedQuad> out = s.translucent ? level.translucent : level.solid;
                            for (BlockModelPart part : parts) {
                                for (int d = 0; d < 6; d++) {
                                    if (!visible[d]) continue;
                                    for (BakedQuad quad : part.getQuads(DIRS[d])) {
                                        out.add(copy(quad, s, bx, by, bz, f, space, tints, interner, p, corners, emission));
                                    }
                                }
                                if (inner) {
                                    for (BakedQuad quad : part.getQuads(null)) {
                                        out.add(copy(quad, s, bx, by, bz, f, space, tints, interner, p, corners, emission));
                                    }
                                }
                            }
                        }
                    }

                    if (s.fluid != null) {
                        emitFluid(grid, info, s, x, y, z, bx, by, bz, f, space, tints, interner, p, level, settings);
                    }

                    if (blockEntities && s.blockEntity != null && level.blockEntities.size() < settings.maxBlockEntities()
                            && (grid.outside == null || grid.outside.get(i))) {
                        Quaternionf rotation = s.blockEntity.rotation(s.state);
                        if (rotation != null) {
                            space.map(bx + 0.5F, by + 0.5F, bz + 0.5F, p);
                            level.blockEntities.add(new Miniature.BlockEntityPart(s.state, p[0], p[1], p[2], space.scale, rotation));
                        }
                    }
                }
            }
        }
        return level;
    }

    private static BakedQuad copy(BakedQuad quad, StateInfo s, float bx, float by, float bz, float f, Space space, TintTable tints,
                                  Map<Vector3f, Vector3f> interner, float[] p, Vector3fc[] corners, int emission) {
        for (int k = 0; k < 4; k++) {
            Vector3fc v = quad.position(k);
            space.map(bx + v.x() * f, by + v.y() * f, bz + v.z() * f, p);
            corners[k] = intern(interner, p);
        }
        int tint = quad.isTinted() ? tints.id(s.state, quad.tintIndex(), false) : -1;
        return new BakedQuad(corners[0], corners[1], corners[2], corners[3],
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                tint, quad.direction(), quad.sprite(), quad.shade(), Math.max(quad.lightEmission(), emission));
    }

    private static Vector3f intern(Map<Vector3f, Vector3f> interner, float[] p) {
        Vector3f v = new Vector3f(p[0], p[1], p[2]);
        Vector3f existing = interner.putIfAbsent(v, v);
        return existing != null ? existing : v;
    }

    /** Fluid as a box: still texture on top and bottom, flowing texture on the sides, like the world renderer. */
    private static void emitFluid(Grid grid, StateInfo[] info, StateInfo s, int x, int y, int z, float bx, float by, float bz, float f,
                                  Space space, TintTable tints, Map<Vector3f, Vector3f> interner, float[] p, Level level, Settings settings) {
        StateInfo above = info[grid.get(x, y + 1, z)];
        float h = above.fluid != null && above.fluid.getType().isSame(s.fluid.getType()) ? 1.0F : Math.max(0.125F, s.fluid.getOwnHeight());
        TextureAtlasSprite still = s.fluidSprites[0], flowing = s.fluidSprites[1];
        int tint = tints.id(s.state, 0, true);
        int emission = settings.glowingBlocks() ? s.fluid.createLegacyBlock().getLightEmission() : 0;
        List<BakedQuad> out = s.fluidTranslucent ? level.translucent : level.solid;
        float x0 = bx, x1 = bx + f, y0 = by, y1 = by + h * f, z0 = bz, z1 = bz + f;
        float vTop = (1.0F - h) * 0.5F, vBottom = 0.5F;
        for (int d = 0; d < 6; d++) {
            if (!grid.fluidFaceVisible(info, s, x, y, z, d)) continue;
            Direction dir = DIRS[d];
            float[][] v = switch (dir) {
                case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
            };
            long[] uv = new long[4];
            TextureAtlasSprite sprite;
            if (dir.getAxis() == Direction.Axis.Y) {
                sprite = still;
                for (int k = 0; k < 4; k++) {
                    uv[k] = UVPair.pack(still.getU((v[k][0] - x0) / f), still.getV((v[k][2] - z0) / f));
                }
            } else {
                sprite = flowing;
                uv[0] = UVPair.pack(flowing.getU(0.0F), flowing.getV(vTop));
                uv[1] = UVPair.pack(flowing.getU(0.0F), flowing.getV(vBottom));
                uv[2] = UVPair.pack(flowing.getU(0.5F), flowing.getV(vBottom));
                uv[3] = UVPair.pack(flowing.getU(0.5F), flowing.getV(vTop));
            }
            Vector3fc[] c = new Vector3fc[4];
            for (int k = 0; k < 4; k++) {
                space.map(v[k][0], v[k][1], v[k][2], p);
                c[k] = intern(interner, p);
            }
            out.add(new BakedQuad(c[0], c[1], c[2], c[3], uv[0], uv[1], uv[2], uv[3], tint, dir, sprite, true, emission));
        }
    }

    // ------------------------------------------------------------------------------------------------ finishing

    private static Miniature.Level[] toPublic(Level[] levels) {
        Miniature.Level[] out = new Miniature.Level[levels.length];
        Map<Level, Miniature.Level> done = new HashMap<>();
        for (int i = 0; i < levels.length; i++) {
            out[i] = done.computeIfAbsent(levels[i], k -> new Miniature.Level(k.factor, k.gridX, k.gridY, k.gridZ,
                    k.solid.isEmpty() ? Mesh.EMPTY : new Mesh(k.solid),
                    k.translucent.isEmpty() ? Mesh.EMPTY : new Mesh(k.translucent),
                    List.copyOf(k.blockEntities)));
        }
        return out;
    }

    /** Distinct vertices of a level (at most ~20k, evenly sampled), plus block-entity boxes; the box corners if empty. */
    private static Vector3fc[] outline(Level level, Vector3fc[] corners) {
        java.util.Set<Vector3fc> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Vector3fc> points = new ArrayList<>();
        for (List<BakedQuad> quads : List.of(level.solid, level.translucent)) {
            for (BakedQuad quad : quads) {
                for (int k = 0; k < 4; k++) {
                    Vector3fc v = quad.position(k);
                    if (seen.add(v)) points.add(v);
                }
            }
        }
        for (Miniature.BlockEntityPart part : level.blockEntities) {
            float h = part.scale() * 0.5F;
            for (int i = 0; i < 8; i++) {
                points.add(new Vector3f(part.x() + ((i & 1) == 0 ? -h : h), part.y() + ((i & 2) == 0 ? -h : h), part.z() + ((i & 4) == 0 ? -h : h)));
            }
        }
        if (points.isEmpty()) return corners;
        int step = Math.max(1, points.size() / 20000);
        if (step == 1) return points.toArray(new Vector3fc[0]);
        List<Vector3fc> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) sampled.add(points.get(i));
        return sampled.toArray(new Vector3fc[0]);
    }

    private static @Nullable TextureAtlasSprite mostCommonParticle(Grid grid, StateInfo[] info, BlockModelShaper models) {
        int[] counts = new int[info.length];
        for (char c : grid.cells) counts[c]++;
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if ((info[i].model || info[i].fluid != null) && (best == 0 || counts[i] > counts[best])) best = i;
        }
        // item crack particles read this sprite, so never leave it empty
        BlockState state = best == 0 ? Blocks.STONE.defaultBlockState() : info[best].state;
        return models.getBlockModel(state).particleIcon();
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
    }
}
