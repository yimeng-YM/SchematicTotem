package dev.yimeng.schematictotem.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.gui.TotemConfigScreen;
import dev.yimeng.schematictotem.mesh.Miniature;
import dev.yimeng.schematictotem.schematic.SchematicReader;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /totemmodel - client-side command to pick, inspect and tune the miniature. */
public final class TotemCommands {
    private static final List<String> FRONTS = List.of("auto", "south", "west", "north", "east", "up", "down");

    private static final SuggestionProvider<FabricClientCommandSource> FILES = (ctx, builder) -> {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String file : TotemModels.findFiles()) {
            if (file.toLowerCase(Locale.ROOT).contains(typed)) builder.suggest(file);
        }
        return builder.buildFuture();
    };

    private TotemCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("totemmodel")
                .executes(ctx -> info(ctx.getSource()))
                .then(literal("gui").executes(ctx -> openScreen()))
                .then(literal("load").then(argument("file", StringArgumentType.greedyString()).suggests(FILES)
                        .executes(ctx -> load(ctx.getSource(), StringArgumentType.getString(ctx, "file")))))
                .then(literal("list").executes(ctx -> list(ctx.getSource())))
                .then(literal("info").executes(ctx -> info(ctx.getSource())))
                .then(literal("clear").executes(ctx -> {
                    TotemModels.INSTANCE.clear();
                    ctx.getSource().sendFeedback(Text.translatable("schematictotem.cleared"));
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    TotemModels.INSTANCE.applyConfig(TotemConfig.load(), false);
                    TotemModels.INSTANCE.reloadAll();
                    ctx.getSource().sendFeedback(Text.translatable("schematictotem.reloading"));
                    return 1;
                }))
                .then(literal("on").executes(ctx -> set(ctx, c -> c.enabled = true, "enabled", "true")))
                .then(literal("off").executes(ctx -> set(ctx, c -> c.enabled = false, "enabled", "false")))
                // ---- the current schematic's appearance
                .then(literal("front").then(argument("side", StringArgumentType.word())
                        .suggests((ctx, b) -> suggest(b, FRONTS))
                        .executes(ctx -> front(ctx, StringArgumentType.getString(ctx, "side")))))
                .then(literal("turn").then(argument("yaw", FloatArgumentType.floatArg(-360, 360))
                        .executes(ctx -> turn(ctx, FloatArgumentType.getFloat(ctx, "yaw"), null, null))
                        .then(argument("pitch", FloatArgumentType.floatArg(-360, 360))
                                .executes(ctx -> turn(ctx, FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"), null))
                                .then(argument("roll", FloatArgumentType.floatArg(-360, 360))
                                        .executes(ctx -> turn(ctx, FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"),
                                                FloatArgumentType.getFloat(ctx, "roll")))))))
                .then(literal("size").then(argument("factor", FloatArgumentType.floatArg(0.1F, 4.0F))
                        .executes(ctx -> {
                            float size = FloatArgumentType.getFloat(ctx, "factor");
                            return model(ctx, o -> o.size = size, "size", String.valueOf(size));
                        })))
                .then(literal("detail")
                        .then(literal("auto").executes(ctx -> detail(ctx, 0)))
                        .then(literal("full").executes(ctx -> detail(ctx, TotemConfig.FULL_DETAIL)))
                        .then(argument("quads", IntegerArgumentType.integer(256, 1_000_000))
                                .executes(ctx -> detail(ctx, IntegerArgumentType.getInteger(ctx, "quads")))))
                .then(literal("anchor")
                        .then(literal("center").executes(ctx -> model(ctx, o -> o.anchor = "center", "anchor", "center")))
                        .then(literal("bottom").executes(ctx -> model(ctx, o -> o.anchor = "bottom", "anchor", "bottom"))))
                // ---- how it is held / shown (all schematics)
                .then(pose())
                // ---- detail and performance
                .then(literal("budget").then(argument("quads", IntegerArgumentType.integer(256, 1_000_000))
                        .executes(ctx -> {
                            int quads = IntegerArgumentType.getInteger(ctx, "quads");
                            return rebake(ctx, c -> c.quadBudget = quads, "quadBudget", String.valueOf(quads));
                        })))
                .then(literal("interior").then(argument("cull", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean cull = BoolArgumentType.getBool(ctx, "cull");
                            return rebake(ctx, c -> c.cullSealedInterior = cull, "cullSealedInterior", String.valueOf(cull));
                        })))
                .then(literal("blockentities").then(argument("show", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean show = BoolArgumentType.getBool(ctx, "show");
                            return rebake(ctx, c -> c.renderBlockEntities = show, "renderBlockEntities", String.valueOf(show));
                        })))
                .then(literal("biome").then(argument("biome", IdentifierArgumentType.identifier())
                        .suggests(TotemCommands::suggestBiomes)
                        .executes(ctx -> {
                            String biome = ctx.getArgument("biome", Identifier.class).toString();
                            return set(ctx, c -> c.tintBiome = biome, "tintBiome", biome);
                        })))
                .then(literal("others").then(argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            return set(ctx, c -> c.applyToOthers = enabled, "applyToOthers", String.valueOf(enabled));
                        })))
                // ---- several schematics by item name
                .then(literal("name").then(argument("name", StringArgumentType.string())
                        .then(argument("file", StringArgumentType.greedyString()).suggests(FILES)
                                .executes(ctx -> name(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "file"))))))
                .then(literal("unname").then(argument("name", StringArgumentType.string())
                        .suggests((ctx, b) -> suggest(b, TotemModels.INSTANCE.config().named.keySet().stream().map(TotemCommands::quoteIfNeeded).toList()))
                        .executes(ctx -> unname(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("folder").executes(ctx -> {
                    Path dir = TotemModels.gameDir().resolve("schematics");
                    try {
                        Files.createDirectories(dir);
                    } catch (Exception ignored) {
                    }
                    Util.getOperatingSystem().open(dir);
                    return 1;
                })));
    }

    /** /totemmodel pose <context> rotation|translation <x> <y> <z> | scale <s> | reset */
    private static LiteralArgumentBuilder<FabricClientCommandSource> pose() {
        LiteralArgumentBuilder<FabricClientCommandSource> pose = literal("pose");
        for (PoseKind kind : PoseKind.values()) {
            pose.then(literal(kind.id)
                    .then(literal("rotation").then(xyz((ctx, v) -> poseEdit(ctx, kind, p -> p.rotation = v, "rotation", v))))
                    .then(literal("translation").then(xyz((ctx, v) -> poseEdit(ctx, kind, p -> p.translation = v, "translation", v))))
                    .then(literal("scale").then(argument("s", FloatArgumentType.floatArg(0.05F, 4.0F))
                            .executes(ctx -> {
                                float s = FloatArgumentType.getFloat(ctx, "s");
                                return poseEdit(ctx, kind, p -> p.scale = s, "scale", new float[]{s});
                            })))
                    .then(literal("reset").executes(ctx -> {
                        kind.reset(TotemModels.INSTANCE.config().poses);
                        return saved(ctx, "pose." + kind.id, "reset");
                    })));
        }
        return pose;
    }

    private interface XyzAction {
        int run(CommandContext<FabricClientCommandSource> ctx, float[] value);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, Float> xyz(XyzAction action) {
        return argument("x", FloatArgumentType.floatArg(-360, 360))
                .then(argument("y", FloatArgumentType.floatArg(-360, 360))
                        .then(argument("z", FloatArgumentType.floatArg(-360, 360))
                                .executes(ctx -> action.run(ctx, new float[]{FloatArgumentType.getFloat(ctx, "x"),
                                        FloatArgumentType.getFloat(ctx, "y"), FloatArgumentType.getFloat(ctx, "z")}))));
    }

    private static int poseEdit(CommandContext<FabricClientCommandSource> ctx, PoseKind kind, Consumer<TotemConfig.Pose> change, String what,
                                float[] value) {
        change.accept(kind.get(TotemModels.INSTANCE.config().poses));
        return saved(ctx, "pose." + kind.id + "." + what, Arrays.toString(value));
    }

    private static int saved(CommandContext<FabricClientCommandSource> ctx, String key, String value) {
        TotemConfig config = TotemModels.INSTANCE.config();
        config.sanitize();
        config.save();
        TotemModels.INSTANCE.appearanceChanged();
        ctx.getSource().sendFeedback(Text.translatable("schematictotem.set", key, value));
        return 1;
    }

    private static int openScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        // runs after the chat screen that sent the command has closed
        client.send(() -> client.setScreen(new TotemConfigScreen(null)));
        return 1;
    }

    private static int front(CommandContext<FabricClientCommandSource> ctx, String side) {
        if (!FRONTS.contains(side)) {
            ctx.getSource().sendError(Text.translatable("schematictotem.front.unknown", side, String.join(", ", FRONTS)));
            return 0;
        }
        return model(ctx, o -> o.front = side, "front", side);
    }

    private static int turn(CommandContext<FabricClientCommandSource> ctx, float yaw, Float pitch, Float roll) {
        return model(ctx, o -> {
            o.yaw = yaw;
            if (pitch != null) o.pitch = pitch;
            if (roll != null) o.roll = roll;
        }, "turn", yaw + (pitch == null ? "" : " " + pitch) + (roll == null ? "" : " " + roll));
    }

    /**
     * The current schematic's own detail (quad budget of its finest level); 0 = the global budget, FULL_DETAIL = every
     * block 1:1 with no resampling. Re-bakes it.
     */
    private static int detail(CommandContext<FabricClientCommandSource> ctx, int quads) {
        String value = quads == 0 ? "auto" : quads == TotemConfig.FULL_DETAIL ? "full" : String.valueOf(quads);
        int result = model(ctx, o -> o.budget = quads, "detail", value);
        TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
        if (result > 0 && slot != null) TotemModels.INSTANCE.rebake(slot);
        return result;
    }

    /** Changes the current schematic's own appearance settings. */
    private static int model(CommandContext<FabricClientCommandSource> ctx, Consumer<TotemConfig.ModelOptions> change, String key, String value) {
        TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
        if (slot == null) {
            ctx.getSource().sendError(Text.translatable("schematictotem.info.none"));
            return 0;
        }
        change.accept(TotemModels.INSTANCE.config().optionsForEdit(slot.label));
        return saved(ctx, key, value);
    }

    private static int load(FabricClientCommandSource source, String file) {
        Path path = TotemModels.resolve(file);
        if (!SchematicReader.isSupported(path)) {
            source.sendError(Text.translatable("schematictotem.unsupported", file));
            return 0;
        }
        if (!TotemModels.isReadable(path)) {
            source.sendError(Text.translatable("schematictotem.not_found", file));
            return 0;
        }
        source.sendFeedback(Text.translatable("schematictotem.loading", TotemModels.displayPath(path)).formatted(Formatting.GRAY));
        TotemModels.INSTANCE.use(path);
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        List<String> files = TotemModels.findFiles();
        if (files.isEmpty()) {
            source.sendFeedback(Text.translatable("schematictotem.list.empty"));
            return 0;
        }
        source.sendFeedback(Text.translatable("schematictotem.list.header", files.size()).formatted(Formatting.GOLD));
        String current = TotemModels.INSTANCE.config().file;
        for (String file : files) {
            MutableText line = Text.literal(file.equals(current) ? " > " : " - ").append(Text.literal(file).styled(style -> style
                    .withColor(file.equals(current) ? Formatting.GREEN : Formatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand("/totemmodel load " + file))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("schematictotem.list.hover")))));
            source.sendFeedback(line);
        }
        return files.size();
    }

    private static int info(FabricClientCommandSource source) {
        TotemModels models = TotemModels.INSTANCE;
        TotemConfig config = models.config();
        if (!config.enabled) source.sendFeedback(Text.translatable("schematictotem.info.disabled").formatted(Formatting.YELLOW));
        TotemModels.Slot slot = models.defaultSlot();
        if (slot == null) {
            source.sendFeedback(Text.translatable("schematictotem.info.none"));
        } else {
            source.sendFeedback(describe(slot));
            TotemConfig.ModelOptions o = config.options(slot.label);
            String front = "auto".equals(o.front) ? "auto (" + slot.autoFront() + ")" : o.front;
            source.sendFeedback(Text.translatable("schematictotem.info.appearance", front, o.yaw, o.pitch, o.roll, o.size, o.anchor)
                    .formatted(Formatting.GRAY));
            Miniature m = slot.miniature;
            Text detail = o.budget == TotemConfig.FULL_DETAIL ? Text.translatable("schematictotem.info.detail_full")
                    : o.budget > 0 ? Text.literal(String.valueOf(o.budget))
                    : Text.translatable("schematictotem.info.detail_auto", config.quadBudget);
            source.sendFeedback(Text.translatable("schematictotem.info.detail", detail,
                    m == null ? "?" : TotemModels.formatFactor(m.levels[0].factor), m == null ? "?" : m.levels[0].quadCount()).formatted(Formatting.GRAY));
        }
        config.named.forEach((name, file) -> {
            TotemModels.Slot named = models.slots().stream().filter(s -> s.path.equals(TotemModels.resolve(file))).findFirst().orElse(null);
            MutableText line = Text.literal("\"" + name + "\" -> ").formatted(Formatting.GRAY);
            source.sendFeedback(named == null ? line.append(Text.literal(file)) : line.append(describe(named)));
        });
        source.sendFeedback(Text.translatable("schematictotem.info.settings", config.quadBudget, config.tintBiome,
                String.valueOf(config.applyToOthers)).formatted(Formatting.GRAY));
        source.sendFeedback(Text.translatable("schematictotem.info.gui").formatted(Formatting.DARK_GRAY));
        return 1;
    }

    private static MutableText describe(TotemModels.Slot slot) {
        MutableText text = Text.literal(slot.label).formatted(Formatting.AQUA);
        Miniature miniature = slot.miniature;
        if (slot.loading) return text.append(Text.translatable("schematictotem.info.baking").formatted(Formatting.GRAY));
        if (slot.error != null) return text.append(Text.literal(": " + slot.error).formatted(Formatting.RED));
        if (miniature == null) return text.append(Text.translatable("schematictotem.info.waiting").formatted(Formatting.GRAY));
        return text.append(Text.literal(": ").formatted(Formatting.WHITE)).append(TotemModels.describe(miniature).formatted(Formatting.WHITE));
    }

    private static int name(FabricClientCommandSource source, String name, String file) {
        Path path = TotemModels.resolve(file);
        if (!SchematicReader.isSupported(path) || !TotemModels.isReadable(path)) {
            source.sendError(Text.translatable("schematictotem.not_found", file));
            return 0;
        }
        TotemConfig config = TotemModels.INSTANCE.config();
        config.named.put(name, TotemModels.displayPath(path));
        config.save();
        TotemModels.INSTANCE.applyConfig(config, false);
        source.sendFeedback(Text.translatable("schematictotem.named", name, TotemModels.displayPath(path)));
        return 1;
    }

    private static int unname(FabricClientCommandSource source, String name) {
        TotemConfig config = TotemModels.INSTANCE.config();
        if (config.named.remove(name) == null) {
            source.sendError(Text.translatable("schematictotem.unnamed.missing", name));
            return 0;
        }
        config.save();
        TotemModels.INSTANCE.applyConfig(config, false);
        source.sendFeedback(Text.translatable("schematictotem.unnamed", name));
        return 1;
    }

    /** Changes a setting that only affects drawing. */
    private static int set(CommandContext<FabricClientCommandSource> ctx, Consumer<TotemConfig> change, String key, String value) {
        TotemConfig config = TotemModels.INSTANCE.config();
        change.accept(config);
        config.save();
        TotemModels.INSTANCE.applyConfig(config, false);
        ctx.getSource().sendFeedback(Text.translatable("schematictotem.set", key, value));
        return 1;
    }

    /** Changes a setting that needs the miniatures baked again. */
    private static int rebake(CommandContext<FabricClientCommandSource> ctx, Consumer<TotemConfig> change, String key, String value) {
        TotemConfig config = TotemModels.INSTANCE.config();
        change.accept(config);
        config.save();
        TotemModels.INSTANCE.applyConfig(config, true);
        ctx.getSource().sendFeedback(Text.translatable("schematictotem.set", key, value));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBiomes(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return builder.buildFuture();
        List<String> ids = client.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getIds().stream().map(Identifier::toString).sorted().toList();
        return suggest(builder, ids);
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, List<String> values) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static String quoteIfNeeded(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(name.charAt(i))) return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return name;
    }
}
