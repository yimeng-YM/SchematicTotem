"""Generate original, deterministic schematic fixtures using only Python's standard library."""
from pathlib import Path
import gzip
import struct


def utf(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def payload(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 2:
        return struct.pack(">h", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 4:
        return struct.pack(">q", value if value < 1 << 63 else value - (1 << 64))
    if kind == 7:
        return struct.pack(">i", len(value)) + bytes(value)
    if kind == 8:
        return utf(value)
    if kind == 9:
        element, values = value
        return bytes([element]) + struct.pack(">i", len(values)) + b"".join(payload(element, v) for v in values)
    if kind == 10:
        return b"".join(bytes([t]) + utf(key) + payload(t, v) for key, (t, v) in value.items()) + b"\0"
    if kind in (11, 12):
        element = 3 if kind == 11 else 4
        return struct.pack(">i", len(value)) + b"".join(payload(element, v) for v in value)
    raise ValueError(kind)


def write(path, root):
    path.write_bytes(gzip.compress(b"\x0a\x00\x00" + payload(10, root), mtime=0))


def state(name, **properties):
    result = {"Name": (8, "minecraft:" + name)}
    if properties:
        result["Properties"] = (10, {k: (8, v) for k, v in sorted(properties.items())})
    return result


root = Path(__file__).resolve().parents[1]
out = root / "src/test/resources/fixtures"
out.mkdir(parents=True, exist_ok=True)
sx, sy, sz = 7, 5, 6
palette = [state("air"), state("stone"), state("oak_planks"), state("glass"),
           state("oak_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"),
           state("water", level="0"), state("chest", facing="east", type="single", waterlogged="false")]
grid = [0] * (sx * sy * sz)


def put(x, y, z, index):
    grid[(y * sz + z) * sx + x] = index


for z in range(sz):
    for x in range(sx):
        put(x, 0, z, 1)
        if x in (0, sx - 1) or z in (0, sz - 1):
            for y in range(1, 4):
                put(x, y, z, 3 if y == 2 and x in (2, 4) else 2)
put(3, 1, 0, 0)
put(3, 2, 0, 0)
put(2, 1, 2, 4)
put(3, 1, 2, 6)
put(4, 1, 2, 5)
put(4, 1, 3, 5)
count = sum(v != 0 for v in grid)

bits = 3
words = [0] * ((len(grid) * bits + 63) // 64)
for index, value in enumerate(grid):
    bit = index * bits
    word, offset = divmod(bit, 64)
    words[word] |= value << offset
    if offset + bits > 64:
        words[word + 1] |= value >> (64 - offset)
words = [v & ((1 << 64) - 1) for v in words]
xyz = lambda x, y, z: {"x": (3, x), "y": (3, y), "z": (3, z)}
write(out / "original-house.litematic", {
    "Version": (3, 6), "MinecraftDataVersion": (3, 3463),
    "Metadata": (10, {"Name": (8, "Schematic Totem original fixture"), "TotalBlocks": (3, count)}),
    "Regions": (10, {"house": (10, {"Position": (10, xyz(0, 0, 0)), "Size": (10, xyz(sx, sy, sz)),
        "BlockStatePalette": (9, (10, palette)), "BlockStates": (12, words)})})})

sponge_palette = {}
for index, entry in enumerate(palette):
    name = entry["Name"][1]
    props = entry.get("Properties", (10, {}))[1]
    if props:
        name += "[" + ",".join(k + "=" + v[1] for k, v in props.items()) + "]"
    sponge_palette[name] = (3, index)
write(out / "original-house.schem", {"Schematic": (10, {
    "Version": (3, 3), "DataVersion": (3, 3463), "Width": (2, sx), "Height": (2, sy), "Length": (2, sz),
    "Blocks": (10, {"Palette": (10, sponge_palette), "Data": (7, grid)})})})

blocks = [{"pos": (9, (3, [x, y, z])), "state": (3, grid[(y * sz + z) * sx + x])}
          for y in range(sy) for z in range(sz) for x in range(sx)]
write(out / "original-house.nbt", {"DataVersion": (3, 3463), "size": (9, (3, [sx, sy, sz])),
    "palette": (9, (10, palette)), "blocks": (9, (10, blocks)), "entities": (9, (10, []))})
print(f"Generated three {sx}x{sy}x{sz} original fixtures, {count} non-air blocks each.")
