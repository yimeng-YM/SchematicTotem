package dev.yimeng.schematictotem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.yimeng.schematictotem.SchematicTotemClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/schematictotem.json. Plain fields so Gson can read hand-edited files; {@link #sanitize()} clamps everything.
 */
public final class TotemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Master switch. */
    public boolean enabled = true;
    /** Schematic shown on every replaced item, relative to the game directory (or absolute). Empty = vanilla model. */
    public String file = "";
    /** Custom item name -> schematic. A totem renamed in an anvil shows its own build. */
    public Map<String, String> named = new LinkedHashMap<>();
    /** Items whose model is replaced. */
    public List<String> items = new ArrayList<>(List.of("minecraft:totem_of_undying"));
    /** Also replace the item held by other players / mobs / armor stands (item frames and dropped items always are). */
    public boolean applyToOthers = true;

    /**
     * Quad budget of the most detailed level (GUI, first person, the totem pop animation). Coarser levels get 1/4,
     * 1/16, 1/64. The vanilla item path costs roughly 60 ns per quad per frame (less with Sodium), so 8000 is about
     * half a millisecond for a totem held in first person.
     */
    public int quadBudget = 8000;
    /**
     * Totems held by others, in item frames or on the ground use the finest detail level whose cells are still at least
     * this many pixels on screen; coarser levels take over with distance (GUI, first person and the pop animation
     * always use the finest level).
     */
    public float detailPixels = 3.0F;
    /** Skip faces that can only be seen from inside a sealed space (rooms without openings, hollow terrain...). */
    public boolean cullSealedInterior = true;
    /** Chests, beds, signs, banners, skulls, shulker boxes, decorated pots and conduits (only on the most detailed level). */
    public boolean renderBlockEntities = true;
    public int maxBlockEntities = 96;
    /** Light-emitting blocks (lanterns, glowstone, lava...) stay lit in the dark. */
    public boolean glowingBlocks = true;
    /** Biome used for grass / foliage / water colors. */
    public String tintBiome = "minecraft:plains";
    /** Editing a hand pose in the settings screen also changes the other hand (mirrored). */
    public boolean linkHands = true;

    /** How each schematic is turned and sized, keyed by the same path string as {@link #file}. */
    public Map<String, ModelOptions> models = new LinkedHashMap<>();
    /** How the miniature is held / shown in each display context (shared by all schematics). */
    public Poses poses = new Poses();

    /** Refuse files whose bounding box is larger than this many cells (memory guard). */
    public long maxCells = 256L * 1024 * 1024;

    /** Per-schematic appearance. */
    public static final class ModelOptions {
        /** Side of the build that faces the viewer: auto, south, west, north, east, up (lying flat, e.g. map art), down. */
        public String front = "auto";
        /** Extra turn in degrees: yaw around the vertical axis, pitch forward/back, roll sideways. */
        public float yaw = 0.0F;
        public float pitch = 0.0F;
        public float roll = 0.0F;
        /** Size multiplier in hands, frames and on the ground (the inventory icon always fills the slot). */
        public float size = 1.0F;
        /** "center" or "bottom" (stands on the bottom of the item's cube, e.g. a figure in the hand). */
        public String anchor = "center";
        /**
         * Detail of this schematic: its own quad budget for the finest level, 0 = use the global budget,
         * {@link #FULL_DETAIL} = exact 1:1, every block at its own size with no resampling at all.
         */
        public int budget = 0;

        public ModelOptions copy() {
            ModelOptions o = new ModelOptions();
            o.front = front;
            o.yaw = yaw;
            o.pitch = pitch;
            o.roll = roll;
            o.size = size;
            o.anchor = anchor;
            o.budget = budget;
            return o;
        }

        void sanitize() {
            if (!List.of("auto", "south", "west", "north", "east", "up", "down").contains(front)) front = "auto";
            yaw = wrapDegrees(yaw);
            pitch = wrapDegrees(pitch);
            roll = wrapDegrees(roll);
            size = clamp(size, 0.1F, 4.0F, 1.0F);
            if (!"bottom".equals(anchor)) anchor = "center";
            budget = budget == FULL_DETAIL ? FULL_DETAIL : budget <= 0 ? 0 : Math.max(256, Math.min(budget, 1_000_000));
        }
    }

    /** One display transform, in model JSON units: degrees, 1/16 block, scale. */
    public static final class Pose {
        public float[] rotation;
        public float[] translation;
        public float scale;

        public Pose() {
            this(0, 0, 0, 0, 0, 0, 1);
        }

        public Pose(float rx, float ry, float rz, float tx, float ty, float tz, float scale) {
            this.rotation = new float[]{rx, ry, rz};
            this.translation = new float[]{tx, ty, tz};
            this.scale = scale;
        }

        public Pose copy() {
            return new Pose(rotation[0], rotation[1], rotation[2], translation[0], translation[1], translation[2], scale);
        }

        void sanitize(Pose fallback) {
            if (rotation == null || rotation.length != 3) rotation = fallback.rotation.clone();
            if (translation == null || translation.length != 3) translation = fallback.translation.clone();
            for (int i = 0; i < 3; i++) {
                rotation[i] = wrapDegrees(rotation[i]);
                translation[i] = clamp(translation[i], -80.0F, 80.0F, 0.0F);
            }
            scale = clamp(scale, 0.05F, 4.0F, fallback.scale);
        }
    }

    /**
     * Display transforms. The miniature's front (see {@link ModelOptions#front}) points along +Z before these apply,
     * like the front of a block model. Defaults were tuned in game (2026-11-04) so that figures stand upright and face
     * the viewer everywhere: in third person the hand's frame has its forward axis along X, which vanilla mirrors for
     * the left hand, so the left pose turns the other way.
     */
    public static final class Poses {
        public Pose gui = Defaults.GUI.copy();
        public Pose firstPersonRight = Defaults.FIRST_PERSON_RIGHT.copy();
        public Pose firstPersonLeft = Defaults.FIRST_PERSON_LEFT.copy();
        public Pose thirdPersonRight = Defaults.THIRD_PERSON_RIGHT.copy();
        public Pose thirdPersonLeft = Defaults.THIRD_PERSON_LEFT.copy();
        public Pose ground = Defaults.GROUND.copy();
        public Pose fixed = Defaults.FIXED.copy();
        public Pose head = Defaults.HEAD.copy();
        public Pose onShelf = Defaults.ON_SHELF.copy();
        /** The big spinning totem in the middle of the screen when it saves you. */
        public Pose totemPop = Defaults.TOTEM_POP.copy();
        /**
         * First person drawn by mods like Punchy, which hide vanilla's hands, draw their own arms and then draw the held
         * item with the third-person context. Seen from the eyes, so it gets its own pose.
         */
        public Pose firstPersonModRight = Defaults.FIRST_PERSON_MOD_RIGHT.copy();
        public Pose firstPersonModLeft = Defaults.FIRST_PERSON_MOD_LEFT.copy();

        void sanitize() {
            if (gui == null) gui = Defaults.GUI.copy();
            if (firstPersonRight == null) firstPersonRight = Defaults.FIRST_PERSON_RIGHT.copy();
            if (firstPersonLeft == null) firstPersonLeft = Defaults.FIRST_PERSON_LEFT.copy();
            if (thirdPersonRight == null) thirdPersonRight = Defaults.THIRD_PERSON_RIGHT.copy();
            if (thirdPersonLeft == null) thirdPersonLeft = Defaults.THIRD_PERSON_LEFT.copy();
            if (ground == null) ground = Defaults.GROUND.copy();
            if (fixed == null) fixed = Defaults.FIXED.copy();
            if (head == null) head = Defaults.HEAD.copy();
            if (onShelf == null) onShelf = Defaults.ON_SHELF.copy();
            if (totemPop == null) totemPop = Defaults.TOTEM_POP.copy();
            if (firstPersonModRight == null) firstPersonModRight = Defaults.FIRST_PERSON_MOD_RIGHT.copy();
            if (firstPersonModLeft == null) firstPersonModLeft = Defaults.FIRST_PERSON_MOD_LEFT.copy();
            gui.sanitize(Defaults.GUI);
            firstPersonRight.sanitize(Defaults.FIRST_PERSON_RIGHT);
            firstPersonLeft.sanitize(Defaults.FIRST_PERSON_LEFT);
            thirdPersonRight.sanitize(Defaults.THIRD_PERSON_RIGHT);
            thirdPersonLeft.sanitize(Defaults.THIRD_PERSON_LEFT);
            ground.sanitize(Defaults.GROUND);
            fixed.sanitize(Defaults.FIXED);
            head.sanitize(Defaults.HEAD);
            onShelf.sanitize(Defaults.ON_SHELF);
            totemPop.sanitize(Defaults.TOTEM_POP);
            firstPersonModRight.sanitize(Defaults.FIRST_PERSON_MOD_RIGHT);
            firstPersonModLeft.sanitize(Defaults.FIRST_PERSON_MOD_LEFT);
        }
    }

    /** Default display transforms (see {@link Poses}). */
    public static final class Defaults {
        public static final Pose GUI = new Pose(15, -25, 0, 0, 0, 0, 1.0F);
        public static final Pose FIRST_PERSON_RIGHT = new Pose(0, -20, 0, -2, 6, 0, 0.32F);
        public static final Pose FIRST_PERSON_LEFT = new Pose(0, -20, 0, -2, 6, 0, 0.32F);
        public static final Pose THIRD_PERSON_RIGHT = new Pose(90, -90, 0, 0, 3, 1.5F, 0.5F);
        public static final Pose THIRD_PERSON_LEFT = new Pose(90, 90, 0, 0, 3, 1.5F, 0.5F);
        public static final Pose GROUND = new Pose(0, 0, 0, 0, 2, 0, 0.35F);
        public static final Pose FIXED = new Pose(0, 180, 0, 0, 0, 0, 0.9F);
        public static final Pose HEAD = new Pose(0, 180, 0, 0, 0, 0, 1.0F);
        public static final Pose ON_SHELF = new Pose(0, 180, 0, 0, 0, 0, 0.8F);
        public static final Pose TOTEM_POP = new Pose(0, 180, 0, 0, 0, 0, 1.0F);
        // From Punchy 3.0's measured hand frame (2026-11-04): nearly unrotated (roll -10, yaw 5) and mirror-symmetric, so like
        // vanilla first person both hands share one pose: upright, front 20 degrees towards the middle, standing on the palm.
        public static final Pose FIRST_PERSON_MOD_RIGHT = new Pose(3, -25, 10, -0.5F, 3, 0, 0.35F);
        public static final Pose FIRST_PERSON_MOD_LEFT = new Pose(3, -25, 10, -0.5F, 3, 0, 0.35F);

        private Defaults() {
        }
    }

    /** {@link ModelOptions#budget} value for "exact, 1:1, never scaled". */
    public static final int FULL_DETAIL = -1;

    /** Quad budget of the finest level for one schematic (its own setting, else the global one); FULL_DETAIL = exact. */
    public int budgetFor(String key) {
        ModelOptions o = models.get(key);
        if (o != null && o.budget == FULL_DETAIL) return FULL_DETAIL;
        return o != null && o.budget > 0 ? o.budget : quadBudget;
    }

    public ModelOptions options(String key) {
        ModelOptions o = models.get(key);
        return o != null ? o : new ModelOptions();
    }

    public ModelOptions optionsForEdit(String key) {
        return models.computeIfAbsent(key, k -> new ModelOptions());
    }

    public void sanitize() {
        if (file == null) file = "";
        if (named == null) named = new LinkedHashMap<>();
        named.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank());
        if (items == null || items.isEmpty()) items = new ArrayList<>(List.of("minecraft:totem_of_undying"));
        quadBudget = Math.max(256, Math.min(quadBudget, 1_000_000));
        detailPixels = clamp(detailPixels, 0.5F, 16.0F, 3.0F);
        maxBlockEntities = Math.max(0, Math.min(maxBlockEntities, 4096));
        if (tintBiome == null || tintBiome.isBlank()) tintBiome = "minecraft:plains";
        if (models == null) models = new LinkedHashMap<>();
        models.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        models.values().forEach(ModelOptions::sanitize);
        if (poses == null) poses = new Poses();
        poses.sanitize();
        maxCells = Math.max(1L << 16, maxCells);
    }

    static float wrapDegrees(float degrees) {
        if (!Float.isFinite(degrees)) return 0.0F;
        float d = degrees % 360.0F;
        if (d > 180.0F) d -= 360.0F;
        if (d <= -180.0F) d += 360.0F;
        return d;
    }

    static float clamp(float v, float min, float max, float fallback) {
        return Float.isFinite(v) ? Math.max(min, Math.min(v, max)) : fallback;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("schematictotem.json");
    }

    public static TotemConfig load() {
        Path path = path();
        TotemConfig config = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, TotemConfig.class);
            } catch (IOException | JsonParseException e) {
                SchematicTotemClient.LOGGER.warn("Could not read {}, using defaults", path, e);
            }
        }
        if (config == null) config = new TotemConfig();
        config.sanitize();
        config.save();
        return config;
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SchematicTotemClient.LOGGER.warn("Could not save {}", path, e);
        }
    }
}
