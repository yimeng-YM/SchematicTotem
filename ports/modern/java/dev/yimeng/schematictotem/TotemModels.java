package dev.yimeng.schematictotem;

import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.mesh.Miniature;
import dev.yimeng.schematictotem.mesh.MiniatureBaker;
import dev.yimeng.schematictotem.render.MiniatureRenderer;
import dev.yimeng.schematictotem.render.PoseComposer;
import dev.yimeng.schematictotem.schematic.SchematicReader;
import dev.yimeng.schematictotem.schematic.Volume;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Owns the loaded miniatures ("slots", one per schematic file) and decides, for every item the game is about to
 * draw, whether to swap in a miniature. Everything here runs on the render thread except the load/bake jobs, which
 * run one at a time on a background thread and hand their result back with {@code client.execute}.
 */
public final class TotemModels {
    public static final TotemModels INSTANCE = new TotemModels();
    /** Above this many quads in the finest level, choosing "exact" detail warns that holding it may cost frames. */
    private static final int FULL_DETAIL_HEAVY = 150_000;

    /** Entity whose item (item frame, dropped item...) is being prepared; set around ItemModelManager.updateForNonLivingEntity. */
    public static @Nullable Entity trackedEntity;
    /** > 0 while HeldItemRenderer draws the first-person hands (see HeldItemRendererMixin). */
    public static int handPassDepth;
    /** When each pose was last used for a totem of the local player (System.nanoTime), so the settings screen can tell. */
    private static final long[] poseUsedAt = new long[PoseKind.values().length];

    public static void markPoseUsed(PoseKind kind) {
        poseUsedAt[kind.ordinal()] = System.nanoTime();
    }

    /** Used for the local player's totem within the last half second. */
    public static boolean poseInUse(PoseKind kind) {
        long at = poseUsedAt[kind.ordinal()];
        return at != 0 && System.nanoTime() - at < 500_000_000L;
    }

    /**
     * The hand pose used for the local player's totem within the last second (the most recent one; the right hand's
     * when both hands hold a totem), or null.
     */
    public static @Nullable PoseKind recentHandPose() {
        PoseKind right = null, left = null;
        long rightAt = System.nanoTime() - 1_000_000_000L, leftAt = rightAt;
        for (PoseKind kind : PoseKind.values()) {
            if (kind.otherHand() == null) continue;
            long at = poseUsedAt[kind.ordinal()];
            if (at == 0) continue;
            if (kind.rightHand()) {
                if (at > rightAt) {
                    rightAt = at;
                    right = kind;
                }
            } else if (at > leftAt) {
                leftAt = at;
                left = kind;
            }
        }
        return right != null ? right : left;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SchematicTotem-Baker");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        return thread;
    });

    private TotemConfig config = new TotemConfig();
    private Set<Item> items = Set.of(Items.TOTEM_OF_UNDYING);
    private Map<String, String> namedLower = Map.of();
    /** Bumped on every change of poses / per-file options: slots recompute their display transforms lazily. */
    private int appearanceVersion;
    private final Map<Path, Slot> slots = new HashMap<>();
    private @Nullable Slot defaultSlot;

    /** One schematic file and its baked miniature. */
    public static final class Slot {
        public final Path path;
        public final String label;
        volatile int generation;
        public volatile @Nullable Miniature miniature;
        public volatile @Nullable String error;
        public volatile boolean loading;
        boolean announce;
        // display transforms for the current miniature + appearance settings (render thread)
        private PoseComposer.@Nullable Composed composed;
        private @Nullable Miniature composedFor;
        private int composedVersion = -1;
        private Object transformKey = new Object();

        Slot(Path path) {
            this.path = path;
            this.label = displayPath(path);
        }

        /** Side the build faces when the player has not chosen one (from the baked miniature), or south. */
        public String autoFront() {
            Miniature m = miniature;
            return m == null ? "south" : m.suggestedFront;
        }
    }

    private TotemModels() {
    }

    // ------------------------------------------------------------------------------------------------ config

    public TotemConfig config() {
        return config;
    }

    public void applyConfig(TotemConfig newConfig, boolean rebake) {
        newConfig.sanitize();
        this.config = newConfig;
        Set<Item> set = new HashSet<>();
        for (String id : newConfig.items) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) BuiltInRegistries.ITEM.getOptional(identifier).ifPresent(set::add);
        }
        if (set.isEmpty()) set.add(Items.TOTEM_OF_UNDYING);
        this.items = Set.copyOf(set);
        Map<String, String> named = new LinkedHashMap<>();
        newConfig.named.forEach((name, file) -> named.put(name.trim().toLowerCase(Locale.ROOT), file));
        this.namedLower = named;
        appearanceVersion++;

        Set<Path> wanted = new HashSet<>();
        defaultSlot = newConfig.file.isBlank() ? null : slot(resolve(newConfig.file));
        if (defaultSlot != null) wanted.add(defaultSlot.path);
        for (String file : named.values()) wanted.add(resolve(file));
        slots.values().removeIf(slot -> {
            if (wanted.contains(slot.path)) return false;
            slot.generation++; // cancels a running bake
            return true;
        });
        if (rebake) slots.values().forEach(slot -> {
            if (slot.miniature != null || slot.loading) request(slot, false);
        });
    }

    public void saveConfig() {
        config.save();
    }

    private Slot slot(Path path) {
        return slots.computeIfAbsent(path, Slot::new);
    }

    public @Nullable Slot defaultSlot() {
        return defaultSlot;
    }

    public Collection<Slot> slots() {
        return slots.values();
    }

    // ------------------------------------------------------------------------------------------------ rendering hook

    /** Called at the start of ItemModelManager.update; true means the miniature was put into the state. */
    public boolean tryRender(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, @Nullable ItemOwner held) {
        if (!config.enabled || !items.contains(stack.getItem())) return false;
        Minecraft client = Minecraft.getInstance();
        if (held != null && !config.applyToOthers) {
            LivingEntity owner = held.asLivingEntity();
            if (owner != null && owner != client.player) return false;
        }
        Slot slot = slotFor(stack);
        if (slot == null) return false;
        return renderSlot(state, stack, context, held, slot);
    }

    /** Before a server binds item components, render the loaded miniature without constructing an ItemStack. */
    public boolean renderPreview(ItemStackRenderState state, ItemDisplayContext context) {
        state.clear();
        ((dev.yimeng.schematictotem.mixin.ItemStackRenderStateAccessor) (Object) state).schematictotem$context(context);
        if (!config.enabled || defaultSlot == null) return false;
        return renderSlot(state, ItemStack.EMPTY, context, null, defaultSlot);
    }

    private boolean renderSlot(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, @Nullable ItemOwner held, Slot slot) {
        Minecraft client = Minecraft.getInstance();
        Miniature miniature = slot.miniature;
        Object token = client.getModelManager().specialBlockModelRenderer();
        if (miniature == null || miniature.resourceToken != token) {
            // Not baked yet, or baked against models a resource reload has replaced: keep the vanilla model meanwhile.
            if (!slot.loading && slot.error == null) request(slot, false);
            return false;
        }
        if (slot.composedFor != miniature || slot.composedVersion != appearanceVersion) {
            slot.composed = PoseComposer.compose(config.poses, config.options(slot.label), miniature.suggestedFront, miniature.outline);
            slot.composedFor = miniature;
            slot.composedVersion = appearanceVersion;
            slot.transformKey = new Object();
        }
        return MiniatureRenderer.render(state, stack, context, held, held == null ? trackedEntity : null, miniature, config, slot.composed,
                slot.transformKey);
    }

    /** Poses or per-file options were edited in place: recompute display transforms (no re-bake needed). */
    public void appearanceChanged() {
        appearanceVersion++;
    }

    /** Options of a slot's schematic (a fresh default object when none are stored yet). */
    public TotemConfig.ModelOptions options(Slot slot) {
        return config.options(slot.label);
    }

    private @Nullable Slot slotFor(ItemStack stack) {
        if (!namedLower.isEmpty()) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            if (name != null) {
                String file = namedLower.get(name.getString().trim().toLowerCase(Locale.ROOT));
                if (file != null) return slot(resolve(file));
            }
        }
        return defaultSlot;
    }

    // ------------------------------------------------------------------------------------------------ loading

    /** (Re)reads and bakes a slot on the worker thread. Results from superseded requests are dropped. */
    public void request(Slot slot, boolean announce) {
        Minecraft client = Minecraft.getInstance();
        int generation = ++slot.generation;
        slot.loading = true;
        slot.error = null;
        slot.announce |= announce;
        Object token = client.getModelManager().specialBlockModelRenderer();
        var models = client.getModelManager().getBlockModelShaper();
        TotemConfig c = config;
        int budget = c.budgetFor(slot.label);
        MiniatureBaker.Settings settings = new MiniatureBaker.Settings(budget == TotemConfig.FULL_DETAIL ? c.quadBudget : budget,
                c.cullSealedInterior, c.renderBlockEntities, c.maxBlockEntities, c.glowingBlocks, budget == TotemConfig.FULL_DETAIL);
        long maxCells = c.maxCells;
        worker.execute(() -> {
            if (slot.generation != generation) return;
            try {
                Volume volume = SchematicReader.read(slot.path, maxCells);
                if (slot.generation != generation) return;
                Miniature miniature = MiniatureBaker.bake(volume, settings, slot.path.getFileName().toString(), models, token,
                        () -> slot.generation != generation);
                client.execute(() -> finish(slot, generation, miniature, null));
            } catch (CancellationException ignored) {
            } catch (Throwable t) {
                SchematicTotemClient.LOGGER.warn("Could not load {}", slot.path, t);
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                client.execute(() -> finish(slot, generation, null, message));
            }
        });
    }

    private void finish(Slot slot, int generation, @Nullable Miniature miniature, @Nullable String error) {
        if (slot.generation != generation) return;
        slot.loading = false;
        boolean announce = slot.announce;
        slot.announce = false;
        if (miniature != null) {
            slot.miniature = miniature;
            slot.error = null;
            SchematicTotemClient.LOGGER.info("Baked {}: {}", slot.label, describe(miniature).getString());
            if (announce) message(Component.translatable("schematictotem.loaded", slot.label, describe(miniature)).withStyle(ChatFormatting.GREEN));
            if (config.budgetFor(slot.label) == TotemConfig.FULL_DETAIL) {
                Miniature.Level finest = miniature.levels[0];
                if (finest.factor > 1.001F) {
                    message(Component.translatable("schematictotem.full_capped", formatFactor(finest.factor)).withStyle(ChatFormatting.YELLOW));
                } else if (announce && finest.quadCount() > FULL_DETAIL_HEAVY) {
                    message(Component.translatable("schematictotem.full_heavy", finest.quadCount()).withStyle(ChatFormatting.YELLOW));
                }
            }
        } else {
            slot.miniature = null;
            slot.error = error;
            message(Component.translatable("schematictotem.failed", slot.label, error).withStyle(ChatFormatting.RED));
        }
    }

    /** "27x13x31, 4211 blocks, quads per level 9874 / 2890 (1:2) / ..., 3 block entities, 85 ms" */
    public static MutableComponent describe(Miniature m) {
        Miniature.Stats s = m.stats;
        StringBuilder levels = new StringBuilder();
        for (int i = 0; i < m.levels.length; i++) {
            Miniature.Level l = m.levels[i];
            if (i > 0) levels.append(" / ");
            levels.append(l.quadCount());
            if (l.factor > 1.001F) levels.append(" (1:").append(formatFactor(l.factor)).append(')');
        }
        return Component.translatable("schematictotem.stats", s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ(), s.blocks(), levels.toString(),
                m.levels[0].blockEntities.size(), s.bakeMillis());
    }

    public static String formatFactor(float factor) {
        return Math.abs(factor - Math.round(factor)) < 0.01F ? String.valueOf(Math.round(factor)) : String.format(Locale.ROOT, "%.2f", factor);
    }

    // ------------------------------------------------------------------------------------------------ user actions

    /** Uses a file for every replaced item and bakes it now. */
    public void use(Path path) {
        config.file = displayPath(path);
        config.save();
        applyConfig(config, false);
        if (defaultSlot != null) request(defaultSlot, true);
    }

    public void clear() {
        config.file = "";
        config.save();
        applyConfig(config, false);
    }

    /** Bakes one slot again with the current settings (e.g. after its detail changed). */
    public void rebake(Slot slot) {
        request(slot, false);
    }

    public void reloadAll() {
        for (Slot slot : slots.values()) request(slot, true);
    }

    /** Copies dropped schematic files into the schematics folder and uses the first one; returns the files left for the screen. */
    public List<Path> handleDroppedFiles(List<Path> paths) {
        List<Path> rest = new ArrayList<>();
        Path schematic = null;
        for (Path path : paths) {
            if (SchematicReader.isSupported(path) && Files.isRegularFile(path)) {
                if (schematic == null) schematic = path;
            } else {
                rest.add(path);
            }
        }
        if (schematic != null) importFile(schematic);
        return rest;
    }

    public void importFile(Path source) {
        message(Component.translatable("schematictotem.importing", source.getFileName().toString()).withStyle(ChatFormatting.GRAY));
        worker.execute(() -> {
            try {
                Path target = copyIntoSchematics(source);
                Minecraft.getInstance().execute(() -> use(target));
            } catch (IOException e) {
                SchematicTotemClient.LOGGER.warn("Could not import {}", source, e);
                Minecraft.getInstance().execute(() ->
                        message(Component.translatable("schematictotem.failed", source.getFileName().toString(), String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED)));
            }
        });
    }

    private static Path copyIntoSchematics(Path source) throws IOException {
        Path gameDir = gameDir();
        Path absolute = source.toAbsolutePath().normalize();
        if (absolute.startsWith(gameDir)) return absolute; // already inside the instance: use it where it is
        Path dir = gameDir.resolve("schematics");
        Files.createDirectories(dir);
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; ; n++) {
            Path target = dir.resolve(n == 1 ? name : base + " (" + n + ")" + ext);
            if (!Files.exists(target)) {
                Files.copy(absolute, target);
                return target;
            }
            if (Files.size(target) == Files.size(absolute) && Files.mismatch(target, absolute) == -1L) return target;
        }
    }

    // ------------------------------------------------------------------------------------------------ files

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    public static Path resolve(String file) {
        Path path = Path.of(file.trim());
        if (!path.isAbsolute()) path = gameDir().resolve(path);
        return path.toAbsolutePath().normalize();
    }

    /** Game-dir relative with forward slashes when possible, otherwise absolute. */
    public static String displayPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path gameDir = gameDir();
        return absolute.startsWith(gameDir) ? gameDir.relativize(absolute).toString().replace('\\', '/') : absolute.toString();
    }

    private static List<String> cachedFiles = List.of();
    private static long cachedFilesAt;

    /** Schematic files under the usual folders, game-dir relative, for command suggestions and /totemmodel list. */
    public static synchronized List<String> findFiles() {
        long now = System.currentTimeMillis();
        if (now - cachedFilesAt > 3000L) { // suggestions ask on every key press
            cachedFiles = scanFiles();
            cachedFilesAt = now;
        }
        return cachedFiles;
    }

    private static List<String> scanFiles() {
        Path gameDir = gameDir();
        List<String> found = new ArrayList<>();
        for (String dir : List.of("schematics", "config/worldedit/schematics", "structures")) {
            Path root = gameDir.resolve(dir);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root, 6)) {
                stream.filter(Files::isRegularFile).filter(SchematicReader::isSupported).limit(2000)
                        .forEach(p -> found.add(displayPath(p)));
            } catch (IOException | RuntimeException e) {
                SchematicTotemClient.LOGGER.debug("Could not list {}", root, e);
            }
        }
        found.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(found);
    }

    public static boolean isReadable(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (InputStream ignored = Files.newInputStream(path)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static void message(Component text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.displayClientMessage(Component.literal("[SchematicTotem] ").withStyle(ChatFormatting.GOLD).append(text), false);
        else SchematicTotemClient.LOGGER.info(text.getString());
    }
}
