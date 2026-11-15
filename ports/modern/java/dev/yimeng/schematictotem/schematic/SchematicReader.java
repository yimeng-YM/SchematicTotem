package dev.yimeng.schematictotem.schematic;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Litematica (.litematic), Sponge (.schem v1-3) and vanilla structure (.nbt) files into a {@link Volume}.
 * Block states written by older game versions go through the game's own DataFixer, like Litematica does.
 */
public final class SchematicReader {
    /** NBT size limit (uncompressed); a 256^3 litematic with a big palette stays well below this. */
    private static final long NBT_LIMIT = 1L << 30;
    private static final boolean DEBUG = Boolean.getBoolean("schematictotem.debugReader");

    private SchematicReader() {
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schem") || name.endsWith(".schematic") || name.endsWith(".nbt");
    }

    public static Volume read(Path path, long maxCells) throws IOException {
        CompoundTag root = readNbt(path);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (root.contains("Regions")) return readLitematic(root, maxCells);
        if (root.contains("Schematic") || root.contains("BlockData") || root.contains("Blocks") && root.contains("Width")) {
            return readSponge(root, maxCells);
        }
        if (root.contains("blocks") && root.contains("palette") || root.contains("palettes")) return readStructure(root, maxCells);
        if (name.endsWith(".schematic") && root.contains("Materials")) {
            throw new IOException("MCEdit .schematic files (pre-1.13) are not supported");
        }
        throw new IOException("Unknown schematic format");
    }

    private static CompoundTag readNbt(Path path) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(path))) {
            raw.mark(2);
            int b0 = raw.read(), b1 = raw.read();
            raw.reset();
            if (b0 == 0x1f && b1 == 0x8b) return NbtIo.readCompressed(raw, NbtAccounter.create(NBT_LIMIT));
            return NbtIo.read(new DataInputStream(raw), NbtAccounter.create(NBT_LIMIT));
        }
    }

    // ------------------------------------------------------------------------------------------------ litematic

    private static Volume readLitematic(CompoundTag root, long maxCells) throws IOException {
        int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.keySet().isEmpty()) throw new IOException("The litematic has no regions");

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regions.keySet()) {
            int[] box = regionBox(regions.getCompoundOrEmpty(key));
            if (box == null) continue;
            minX = Math.min(minX, box[0]);
            minY = Math.min(minY, box[1]);
            minZ = Math.min(minZ, box[2]);
            maxX = Math.max(maxX, box[3]);
            maxY = Math.max(maxY, box[4]);
            maxZ = Math.max(maxZ, box[5]);
        }
        if (minX > maxX) throw new IOException("The litematic has no blocks");
        Volume volume = newVolume(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, maxCells);
        StateFixer fixer = new StateFixer(dataVersion);

        for (String key : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(key);
            int[] box = regionBox(region);
            if (box == null) continue;
            int sx = box[3] - box[0] + 1, sy = box[4] - box[1] + 1, sz = box[5] - box[2] + 1;
            ListTag paletteNbt = region.getListOrEmpty("BlockStatePalette");
            int paletteSize = paletteNbt.size();
            if (paletteSize == 0) continue;
            int[] map = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) map[i] = volume.indexOf(fixer.fix(paletteNbt.getCompoundOrEmpty(i)));

            long[] data = region.getLongArray("BlockStates").orElse(new long[0]);
            int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
            long cells = (long) sx * sy * sz;
            if ((long) data.length * 64 < cells * bits) throw new IOException("Region '" + key + "' has truncated block data");
            long mask = (1L << bits) - 1;
            int ox = box[0] - minX, oy = box[1] - minY, oz = box[2] - minZ;
            long bit = 0;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++, bit += bits) {
                        int start = (int) (bit >>> 6);
                        int offset = (int) (bit & 63);
                        long value = data[start] >>> offset;
                        if (offset + bits > 64) value |= data[start + 1] << (64 - offset);
                        int v = (int) (value & mask);
                        if (v >= paletteSize) continue;
                        int index = map[v];
                        if (index != 0) volume.set(x + ox, y + oy, z + oz, index);
                    }
                }
            }
        }
        return volume;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of a region; sizes may be negative (Litematica keeps the drag direction). */
    private static int[] regionBox(CompoundTag region) {
        CompoundTag pos = region.getCompoundOrEmpty("Position");
        CompoundTag size = region.getCompoundOrEmpty("Size");
        int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
        int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
        if (sx == 0 || sy == 0 || sz == 0) return null;
        int ex = px + (sx > 0 ? sx - 1 : sx + 1);
        int ey = py + (sy > 0 ? sy - 1 : sy + 1);
        int ez = pz + (sz > 0 ? sz - 1 : sz + 1);
        return new int[]{Math.min(px, ex), Math.min(py, ey), Math.min(pz, ez), Math.max(px, ex), Math.max(py, ey), Math.max(pz, ez)};
    }

    // ------------------------------------------------------------------------------------------------ sponge

    private static Volume readSponge(CompoundTag root, long maxCells) throws IOException {
        CompoundTag schem = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
        int version = schem.getIntOr("Version", 1);
        int dataVersion = schem.getIntOr("DataVersion", version == 1 ? 1343 : 0);
        int width = schem.getShortOr("Width", (short) 0) & 0xFFFF;
        int height = schem.getShortOr("Height", (short) 0) & 0xFFFF;
        int length = schem.getShortOr("Length", (short) 0) & 0xFFFF;
        if (width == 0 || height == 0 || length == 0) throw new IOException("The schematic is empty");

        CompoundTag paletteNbt;
        byte[] data;
        if (version >= 3) {
            CompoundTag blocks = schem.getCompoundOrEmpty("Blocks");
            paletteNbt = blocks.getCompoundOrEmpty("Palette");
            data = blocks.getByteArray("Data").orElse(new byte[0]);
        } else {
            paletteNbt = schem.getCompoundOrEmpty("Palette");
            data = schem.getByteArray("BlockData").orElse(new byte[0]);
        }

        Volume volume = newVolume(width, height, length, maxCells);
        StateFixer fixer = new StateFixer(dataVersion);
        int maxId = 0;
        for (String key : paletteNbt.keySet()) maxId = Math.max(maxId, paletteNbt.getIntOr(key, 0));
        int[] map = new int[maxId + 1];
        for (String key : paletteNbt.keySet()) {
            int id = paletteNbt.getIntOr(key, -1);
            if (id >= 0) map[id] = volume.indexOf(fixer.fix(parseStateString(key)));
        }

        int pos = 0;
        long total = (long) width * height * length;
        for (long i = 0; i < total; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (pos >= data.length) throw new IOException("Truncated block data");
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 28) throw new IOException("Bad varint in block data");
            }
            if (value >= map.length || map[value] == 0) continue;
            int x = (int) (i % width);
            int z = (int) ((i / width) % length);
            int y = (int) (i / ((long) width * length));
            volume.set(x, y, z, map[value]);
        }
        return volume;
    }

    /** "minecraft:oak_stairs[facing=east,half=top]" -> {Name, Properties} so it can go through the DataFixer. */
    private static CompoundTag parseStateString(String text) {
        CompoundTag nbt = new CompoundTag();
        int bracket = text.indexOf('[');
        nbt.putString("Name", bracket < 0 ? text : text.substring(0, bracket));
        if (bracket >= 0 && text.endsWith("]")) {
            CompoundTag props = new CompoundTag();
            for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) props.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
            nbt.put("Properties", props);
        }
        return nbt;
    }

    // ------------------------------------------------------------------------------------------------ structure .nbt

    private static Volume readStructure(CompoundTag root, long maxCells) throws IOException {
        int dataVersion = root.getIntOr("DataVersion", 0);
        ListTag size = root.getListOrEmpty("size");
        if (size.size() < 3) throw new IOException("Structure has no size");
        int sx = size.getIntOr(0, 0), sy = size.getIntOr(1, 0), sz = size.getIntOr(2, 0);
        if (sx <= 0 || sy <= 0 || sz <= 0) throw new IOException("The structure is empty");
        ListTag palette = root.contains("palette") ? root.getListOrEmpty("palette") : root.getListOrEmpty("palettes").getListOrEmpty(0);

        Volume volume = newVolume(sx, sy, sz, maxCells);
        StateFixer fixer = new StateFixer(dataVersion);
        int[] map = new int[palette.size()];
        for (int i = 0; i < map.length; i++) map[i] = volume.indexOf(fixer.fix(palette.getCompoundOrEmpty(i)));

        ListTag blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            int state = block.getIntOr("state", -1);
            if (pos.size() < 3 || state < 0 || state >= map.length || map[state] == 0) continue;
            int x = pos.getIntOr(0, -1), y = pos.getIntOr(1, -1), z = pos.getIntOr(2, -1);
            if (volume.inside(x, y, z)) volume.set(x, y, z, map[state]);
        }
        return volume;
    }

    private static Volume newVolume(int sx, int sy, int sz, long maxCells) throws IOException {
        long cells = (long) sx * sy * sz;
        if (sx <= 0 || sy <= 0 || sz <= 0) throw new IOException("The schematic is empty");
        if (cells > maxCells) {
            throw new IOException("The schematic box is too large: " + sx + "x" + sy + "x" + sz + " (" + cells + " cells, limit " + maxCells + ")");
        }
        return new Volume(sx, sy, sz);
    }

    /** Block-state NBT -> BlockState, upgrading old data versions; unknown blocks become air. Results are cached per entry. */
    private static final class StateFixer {
        private final int dataVersion;
        private final int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        private final Map<CompoundTag, BlockState> cache = new Object2ObjectOpenHashMap<>();

        StateFixer(int dataVersion) {
            this.dataVersion = dataVersion;
        }

        BlockState fix(CompoundTag nbt) {
            BlockState cached = cache.get(nbt);
            if (cached != null) return cached;
            BlockState state;
            try {
                CompoundTag input = nbt;
                if (dataVersion > 0 && dataVersion < currentVersion) {
                    DataFixer fixer = DataFixers.getDataFixer();
                    Tag fixed = fixer.update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) nbt), dataVersion, currentVersion).getValue();
                    if (fixed instanceof CompoundTag compound) input = compound;
                }
                state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, input);
                if (state.is(Blocks.STRUCTURE_VOID)) state = Blocks.AIR.defaultBlockState();
            } catch (RuntimeException e) {
                state = Blocks.AIR.defaultBlockState();
                if (DEBUG) {
                    System.err.println("[SchematicReader] could not convert " + nbt + " (data version " + dataVersion + ")");
                    e.printStackTrace();
                }
            }
            cache.put(nbt.copy(), state);
            return state;
        }
    }
}
