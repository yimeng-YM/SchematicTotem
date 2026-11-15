package dev.yimeng.schematictotem.schematic;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block grid stored as 16x16x16 sections of palette indices; all-air sections are never allocated, so a floating
 * island in a mostly empty box costs memory only where there are blocks. Palette index 0 is always air.
 */
public final class Volume {
    private static final int SHIFT = 4, SIZE = 16, MASK = 15;

    public final int sizeX, sizeY, sizeZ;
    private final int sectionsX, sectionsY, sectionsZ;
    private final char[][] sections;
    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();

    private long nonAir;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public Volume(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.sectionsX = (sizeX + MASK) >> SHIFT;
        this.sectionsY = (sizeY + MASK) >> SHIFT;
        this.sectionsZ = (sizeZ + MASK) >> SHIFT;
        long count = (long) sectionsX * sectionsY * sectionsZ;
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Volume too large");
        this.sections = new char[(int) count][];
        this.paletteIndex.defaultReturnValue(-1);
        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        paletteIndex.put(air, 0);
    }

    /** Palette index for a state, adding it if needed. Air (and anything that renders as nothing) maps to 0. */
    public int indexOf(BlockState state) {
        if (state.isAir()) return 0;
        int index = paletteIndex.getInt(state);
        if (index >= 0) return index;
        index = palette.size();
        if (index > Character.MAX_VALUE) throw new IllegalStateException("More than 65535 different block states");
        palette.add(state);
        paletteIndex.put(state, index);
        return index;
    }

    public BlockState state(int index) {
        return palette.get(index);
    }

    public int paletteSize() {
        return palette.size();
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /** Palette index at a position; 0 (air) outside the volume. */
    public int get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return 0;
        char[] section = sections[((y >> SHIFT) * sectionsZ + (z >> SHIFT)) * sectionsX + (x >> SHIFT)];
        return section == null ? 0 : section[((y & MASK) << 8) | ((z & MASK) << 4) | (x & MASK)];
    }

    /** Sets a palette index. Writing air into an unallocated section is free. */
    public void set(int x, int y, int z, int index) {
        int s = ((y >> SHIFT) * sectionsZ + (z >> SHIFT)) * sectionsX + (x >> SHIFT);
        char[] section = sections[s];
        if (section == null) {
            if (index == 0) return;
            section = sections[s] = new char[SIZE * SIZE * SIZE];
        }
        int i = ((y & MASK) << 8) | ((z & MASK) << 4) | (x & MASK);
        int old = section[i];
        if (old == index) return;
        section[i] = (char) index;
        if (old == 0) {
            nonAir++;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        } else if (index == 0) {
            nonAir--; // bounds stay conservative
        }
    }

    public void set(int x, int y, int z, BlockState state) {
        set(x, y, z, indexOf(state));
    }

    /** True when any allocated (not all-air) section touches the box [x0,x1) x [y0,y1) x [z0,z1). */
    public boolean mayContainBlocks(int x0, int y0, int z0, int x1, int y1, int z1) {
        int sx0 = Math.max(0, x0) >> SHIFT, sx1 = (Math.min(sizeX, x1) - 1) >> SHIFT;
        int sy0 = Math.max(0, y0) >> SHIFT, sy1 = (Math.min(sizeY, y1) - 1) >> SHIFT;
        int sz0 = Math.max(0, z0) >> SHIFT, sz1 = (Math.min(sizeZ, z1) - 1) >> SHIFT;
        for (int sy = sy0; sy <= sy1; sy++) {
            for (int sz = sz0; sz <= sz1; sz++) {
                for (int sx = sx0; sx <= sx1; sx++) {
                    if (sections[(sy * sectionsZ + sz) * sectionsX + sx] != null) return true;
                }
            }
        }
        return false;
    }

    public interface SectionVisitor {
        /** data holds 16x16x16 palette indices, index ((y & 15) << 8) | ((z & 15) << 4) | (x & 15). */
        void visit(int baseX, int baseY, int baseZ, char[] data);
    }

    public void forEachSection(SectionVisitor visitor) {
        int i = 0;
        for (int sy = 0; sy < sectionsY; sy++) {
            for (int sz = 0; sz < sectionsZ; sz++) {
                for (int sx = 0; sx < sectionsX; sx++, i++) {
                    char[] section = sections[i];
                    if (section != null) visitor.visit(sx << SHIFT, sy << SHIFT, sz << SHIFT, section);
                }
            }
        }
    }

    public long nonAirCount() {
        return nonAir;
    }

    public boolean isEmpty() {
        return nonAir == 0;
    }

    /** Tight bounds of the non-air blocks: {minX, minY, minZ, maxX, maxY, maxZ} (inclusive). */
    public int[] bounds() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    public long allocatedBytes() {
        long bytes = 0;
        for (char[] section : sections) if (section != null) bytes += section.length * 2L;
        return bytes + sections.length * 8L;
    }
}
