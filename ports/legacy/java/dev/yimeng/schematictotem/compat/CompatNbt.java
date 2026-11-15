package dev.yimeng.schematictotem.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Typed defaults matching the optional-value NBT API in later game versions. */
public final class CompatNbt {
    private CompatNbt() { }
    public static NbtList list(NbtCompound nbt, String key) {
        return nbt.get(key) instanceof NbtList list ? list : new NbtList();
    }
    public static int integer(NbtCompound nbt, String key, int fallback) {
        return nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getInt(key) : fallback;
    }
    public static int integer(NbtList nbt, int key, int fallback) {
        return key >= 0 && key < nbt.size() && nbt.getHeldType() == NbtElement.INT_TYPE ? nbt.getInt(key) : fallback;
    }
    public static short shortValue(NbtCompound nbt, String key, short fallback) {
        return nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getShort(key) : fallback;
    }
}
