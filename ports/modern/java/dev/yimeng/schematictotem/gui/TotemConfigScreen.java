package dev.yimeng.schematictotem.gui;

import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.mesh.Miniature;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Settings with live previews: a 3D preview box (inventory icon, item frame, totem pop, dropped - drag to turn it),
 * and, in a world, a see-through background so the totem in your own hand (first or third person) updates while
 * you edit.
 */
public final class TotemConfigScreen extends Screen {
    private enum Tab {MODEL, POSE, GENERAL}

    private enum PreviewMode {
        GUI(ItemDisplayContext.GUI, 0.95F, false),
        FIXED(ItemDisplayContext.FIXED, 1.05F, true),
        POP(ItemDisplayContext.FIXED, 1.05F, true),
        GROUND(ItemDisplayContext.GROUND, 2.2F, false);

        final ItemDisplayContext context;
        final float scale;
        /** Frames and the pop animation are seen from the side their front faces; the preview looks the other way. */
        final boolean seenFromBehind;

        PreviewMode(ItemDisplayContext context, float scale, boolean seenFromBehind) {
            this.context = context;
            this.scale = scale;
            this.seenFromBehind = seenFromBehind;
        }
    }

    private static final List<String> FRONTS = List.of("auto", "south", "west", "north", "east", "up", "down");
    private static final List<String> BIOMES = List.of("minecraft:plains", "minecraft:meadow", "minecraft:cherry_grove", "minecraft:forest",
            "minecraft:jungle", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:desert",
            "minecraft:badlands", "minecraft:dark_forest", "minecraft:warm_ocean", "minecraft:lush_caves");

    // remembered between openings
    private static Tab tab = Tab.MODEL;
    private static PoseKind poseKind = PoseKind.FIRST_PERSON_RIGHT;
    private static PreviewMode previewMode = PreviewMode.GUI;

    private final @Nullable Screen parent;
    private final ItemStackRenderState previewState = new ItemStackRenderState();
    private final ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
    private float viewYaw, viewPitch, zoom = 1.0F;
    private boolean draggingPreview;
    private boolean pickedPoseInUse;
    /** Ticks until the miniatures are baked again after a detail change (0 = nothing pending). */
    private int rebakeCountdown, slotRebakeCountdown;
    private @Nullable CameraType perspectiveBefore;

    // layout
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentW, rowStep, widgetH, statusY;
    private int colX, colW, boxX, boxY, boxSize, hintY, bottomY;

    public TotemConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("schematictotem.screen.title"));
        this.parent = parent;
    }

    private TotemConfig config() {
        return TotemModels.INSTANCE.config();
    }

    private boolean inWorld() {
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }

    // ------------------------------------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (perspectiveBefore == null && minecraft != null) perspectiveBefore = minecraft.options.getCameraType();
        panelX = 6;
        panelY = 6;
        panelW = Mth.clamp((int) (width * 0.42F), 170, 240);
        panelH = height - 12;
        contentX = panelX + 6;
        contentW = panelW - 12;

        colX = panelX + panelW + 8;
        colW = Math.max(60, width - colX - 6);
        boxSize = Mth.clamp(Math.min(colW, height - 104), 60, 220);
        boxX = colX + (colW - boxSize) / 2;
        boxY = 8;
        hintY = boxY + boxSize + 26;
        bottomY = height - 26;

        int tabW = (contentW - 4) / 3;
        addTab(Tab.MODEL, contentX, tabW);
        addTab(Tab.POSE, contentX + tabW + 2, tabW);
        addTab(Tab.GENERAL, contentX + 2 * (tabW + 2), contentW - 2 * (tabW + 2));

        int top = panelY + 44, bottom = panelY + panelH - 4;
        int rows = switch (tab) {
            case MODEL -> 10; // choose + status (counts as one) + 8
            case POSE -> 10;
            case GENERAL -> 8;
        };
        rowStep = Mth.clamp((bottom - top) / rows, 17, 22);
        widgetH = rowStep - 2;
        switch (tab) {
            case MODEL -> initModel(top);
            case POSE -> initPose(top);
            case GENERAL -> initGeneral(top);
        }

        // right column: preview modes, then view + done at the bottom
        int modeW = (boxSize - 6) / 4;
        int i = 0;
        for (PreviewMode mode : PreviewMode.values()) {
            addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.preview." + mode.name().toLowerCase(Locale.ROOT)), b -> {
                previewMode = mode;
                resetView();
                rebuildWidgets();
            }).bounds(boxX + i * (modeW + 2), boxY + boxSize + 4, modeW, 18).build()).active = previewMode != mode;
            i++;
        }
        int buttonsW = Math.min(colW, 220), buttonsX = colX + (colW - buttonsW) / 2;
        if (inWorld()) {
            addRenderableWidget(CycleButton.<CameraType>builder(p -> Component.translatable("schematictotem.screen.perspective." + p.name().toLowerCase(Locale.ROOT)),
                            minecraft.options.getCameraType())
                    .withValues(CameraType.values())
                    .create(buttonsX, bottomY, buttonsW / 2 - 1, 20, Component.translatable("schematictotem.screen.perspective"),
                            (button, value) -> minecraft.options.setCameraType(value)));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(inWorld() ? buttonsX + buttonsW / 2 + 1 : buttonsX, bottomY, inWorld() ? buttonsW - buttonsW / 2 - 1 : buttonsW, 20).build());
    }

    private void addTab(Tab t, int x, int w) {
        addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.tab." + t.name().toLowerCase(Locale.ROOT)), b -> {
            tab = t;
            rebuildWidgets();
        }).bounds(x, panelY + 20, w, 20).build()).active = tab != t;
    }

    private void initModel(int y) {
        TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
        addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.choose"), b -> minecraft.setScreen(new FilePickerScreen(this)))
                .bounds(contentX, y, contentW, widgetH).build());
        y += rowStep;
        statusY = y;
        y += rowStep;
        if (slot == null) return;
        TotemConfig.ModelOptions o = config().optionsForEdit(slot.label);
        String auto = slot.autoFront();
        addRenderableWidget(CycleButton.<String>builder(f -> Component.translatable("schematictotem.front." + f, Component.translatable("schematictotem.front." + auto)), o.front)
                .withValues(FRONTS)
                .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen.front"), (b, v) -> {
                    o.front = v;
                    appearanceChanged();
                }));
        y += rowStep;
        y = slider(y, -180, 180, 1, o.yaw, v -> label("yaw", deg(v)), v -> o.yaw = (float) v);
        y = slider(y, -90, 90, 1, o.pitch, v -> label("pitch", deg(v)), v -> o.pitch = (float) v);
        y = slider(y, -180, 180, 1, o.roll, v -> label("roll", deg(v)), v -> o.roll = (float) v);
        y = slider(y, 0.2, 3.0, 0.05, o.size, v -> label("size", String.format(Locale.ROOT, "%.2f×", v)), v -> o.size = (float) v);
        addRenderableWidget(new ValueSlider(contentX, y, contentW, widgetH, 0, DETAIL_STEPS.length - 1, 1, detailIndex(o.budget),
                v -> detailLabel(DETAIL_STEPS[(int) v]), v -> {
            o.budget = DETAIL_STEPS[(int) v];
            slotRebakeCountdown = REBAKE_DELAY;
        }));
        y += rowStep;
        addRenderableWidget(CycleButton.<String>builder(a -> Component.translatable("schematictotem.anchor." + a), o.anchor)
                .withValues("center", "bottom")
                .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen.anchor"), (b, v) -> {
                    o.anchor = v;
                    appearanceChanged();
                }));
        y += rowStep;
        addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.reset_model"), b -> {
            boolean hadOwnDetail = config().options(slot.label).budget > 0;
            config().models.remove(slot.label);
            appearanceChanged();
            if (hadOwnDetail) slotRebakeCountdown = 1;
            rebuildWidgets();
        }).bounds(contentX, y, contentW, widgetH).build());
    }

    /** Detail choices: 0 = the global budget, a quad budget for this schematic's finest level, or exact 1:1 (last). */
    private static final int[] DETAIL_STEPS = {0, 500, 1000, 1500, 2000, 3000, 4000, 6000, 8000, 12000, 16000, 24000, 32000, 48000,
            TotemConfig.FULL_DETAIL};
    private static final int REBAKE_DELAY = 10; // ticks after the last change

    private static int detailIndex(int budget) {
        if (budget == TotemConfig.FULL_DETAIL) return DETAIL_STEPS.length - 1;
        if (budget <= 0) return 0;
        int best = 1;
        for (int i = 1; i < DETAIL_STEPS.length - 1; i++) {
            if (Math.abs(DETAIL_STEPS[i] - budget) < Math.abs(DETAIL_STEPS[best] - budget)) best = i;
        }
        return best;
    }

    private Component detailLabel(int budget) {
        if (budget == TotemConfig.FULL_DETAIL) return Component.translatable("schematictotem.screen.detail_full");
        return budget <= 0
                ? Component.translatable("schematictotem.screen.detail_auto", config().quadBudget)
                : Component.translatable("schematictotem.screen.detail", budget);
    }

    private void initPose(int y) {
        if (!pickedPoseInUse) {
            // open on the pose the held totem really uses (e.g. the left hand, or Punchy's first person)
            pickedPoseInUse = true;
            PoseKind recent = TotemModels.recentHandPose();
            if (recent != null) poseKind = recent;
        }
        addRenderableWidget(CycleButton.<PoseKind>builder(PoseKind::label, poseKind)
                .withValues(PoseKind.values())
                .displayOnlyValue() // the names are long ("first person, right hand (Punchy etc.)"); the tab says what they are
                .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen.context"), (b, v) -> {
                    poseKind = v;
                    showContextInWorld(v);
                    rebuildWidgets();
                }));
        y += rowStep;
        TotemConfig c = config();
        TotemConfig.Pose pose = poseKind.get(c.poses);
        PoseKind kind = poseKind;
        if (kind.otherHand() != null) {
            addRenderableWidget(CycleButton.onOffBuilder(c.linkHands)
                    .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen.link_hands"), (b, v) -> {
                        c.linkHands = v;
                        if (v) syncOtherHand(kind);
                    }));
        }
        y += rowStep;
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            int axis = i;
            y = slider(y, -180, 180, 1, pose.rotation[i], v -> label("rotation_" + axes[axis], deg(v)), v -> {
                pose.rotation[axis] = (float) v;
                syncOtherHand(kind);
            });
        }
        for (int i = 0; i < 3; i++) {
            int axis = i;
            y = slider(y, -24, 24, 0.25, pose.translation[i], v -> label("translation_" + axes[axis], String.format(Locale.ROOT, "%.2f", v)), v -> {
                pose.translation[axis] = (float) v;
                syncOtherHand(kind);
            });
        }
        y = slider(y, 0.05, 3.0, 0.01, pose.scale, v -> label("scale", String.format(Locale.ROOT, "%.2f", v)), v -> {
            pose.scale = (float) v;
            syncOtherHand(kind);
        });
        addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.reset_pose"), b -> {
            kind.reset(c.poses);
            if (c.linkHands && kind.otherHand() != null) kind.otherHand().reset(c.poses);
            appearanceChanged();
            rebuildWidgets();
        }).bounds(contentX, y, contentW, widgetH).build());
    }

    /** With "link hands" on, the other hand follows (mirrored where the hand frame needs it). */
    private void syncOtherHand(PoseKind kind) {
        if (config().linkHands && kind.otherHand() != null) {
            kind.copyToOtherHand(config().poses);
            appearanceChanged();
        }
    }

    private void initGeneral(int y) {
        TotemConfig c = config();
        y = toggle(y, "enabled", c.enabled, v -> {
            c.enabled = v;
            appearanceChanged();
        });
        y = toggle(y, "others", c.applyToOthers, v -> c.applyToOthers = v);
        y = slider(y, 1000, 40000, 500, c.quadBudget, v -> label("budget", String.valueOf((int) v)), v -> {
            c.quadBudget = (int) v;
            rebakeCountdown = REBAKE_DELAY;
        });
        y = slider(y, 1, 8, 0.5, c.detailPixels, v -> label("lod", String.format(Locale.ROOT, "%.1f", v)), v -> c.detailPixels = (float) v);
        y = toggle(y, "interior", c.cullSealedInterior, v -> {
            c.cullSealedInterior = v;
            rebakeCountdown = REBAKE_DELAY;
        });
        y = toggle(y, "blockentities", c.renderBlockEntities, v -> {
            c.renderBlockEntities = v;
            rebakeCountdown = REBAKE_DELAY;
        });
        y = toggle(y, "glowing", c.glowingBlocks, v -> {
            c.glowingBlocks = v;
            rebakeCountdown = REBAKE_DELAY;
        });
        String biome = BIOMES.contains(c.tintBiome) ? c.tintBiome : BIOMES.get(0);
        addRenderableWidget(CycleButton.<String>builder(id -> Component.translatable("biome." + id.replace(':', '.')), biome)
                .withValues(BIOMES)
                .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen.biome"), (b, v) -> c.tintBiome = v));
    }

    private int slider(int y, double min, double max, double step, double value, java.util.function.DoubleFunction<Component> label,
                       java.util.function.DoubleConsumer setter) {
        addRenderableWidget(new ValueSlider(contentX, y, contentW, widgetH, min, max, step, value, label, v -> {
            setter.accept(v);
            appearanceChanged();
        }));
        return y + rowStep;
    }

    private int toggle(int y, String key, boolean value, java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(value)
                .create(contentX, y, contentW, widgetH, Component.translatable("schematictotem.screen." + key), (b, v) -> setter.accept(v)));
        return y + rowStep;
    }

    private static Component label(String key, String value) {
        return Component.translatable("schematictotem.screen." + key, value);
    }

    private static String deg(double v) {
        return (int) Math.round(v) + "°";
    }

    private void appearanceChanged() {
        TotemModels.INSTANCE.appearanceChanged();
    }

    /** Selecting a hand pose switches to the view that shows it (the totem must be in that hand). */
    private void showContextInWorld(PoseKind kind) {
        if (!inWorld()) return;
        switch (kind) {
            case FIRST_PERSON_RIGHT, FIRST_PERSON_LEFT, FIRST_PERSON_MOD_RIGHT, FIRST_PERSON_MOD_LEFT ->
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            case THIRD_PERSON_RIGHT, THIRD_PERSON_LEFT -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            default -> {
            }
        }
        rebuildWidgets(); // the perspective button shows the new view
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        if (!inWorld()) {
            super.renderBackground(context, mouseX, mouseY, deltaTicks);
            return;
        }
        // no blur: the world (and the totem in your hand) stays visible as a live preview
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0101018);
        context.fill(colX - 2, boxY - 4, colX + colW + 2, hintY + 34, 0x80101018);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, title, panelX + panelW / 2, panelY + 6, 0xFFFFD866);
        if (tab == Tab.MODEL) drawModelStatus(context);
        drawPreview(context);

        Component hint = switch (tab) {
            case POSE -> Component.translatable(handHint(poseKind));
            case GENERAL -> Component.translatable("schematictotem.screen.general_hint");
            case MODEL -> Component.translatable("schematictotem.screen.preview_hint");
        };
        int y = hintY;
        if (tab == Tab.POSE && inWorld()) {
            // which pose the totem in hand is really drawn with right now (Punchy & co. use their own)
            PoseKind used = TotemModels.recentHandPose();
            Component status;
            int color;
            if (TotemModels.poseInUse(poseKind)) {
                status = Component.translatable("schematictotem.screen.pose_in_use");
                color = 0xFF7CE07C;
            } else if (used != null) {
                status = Component.translatable("schematictotem.screen.pose_other", used.label());
                color = 0xFFFFD866;
            } else {
                status = Component.translatable("schematictotem.screen.pose_unused");
                color = 0xFFB0B0B0;
            }
            drawLines(context, status, colX, y, colW, 2, color);
            y += 10 * Math.min(2, font.split(status, colW).size()) + 2;
        }
        drawLines(context, hint, colX, y, colW, 3, 0xFFC8C8C8);
    }

    private static String handHint(PoseKind kind) {
        return switch (kind) {
            case FIRST_PERSON_RIGHT, THIRD_PERSON_RIGHT, FIRST_PERSON_MOD_RIGHT -> "schematictotem.screen.hint_mainhand";
            case FIRST_PERSON_LEFT, THIRD_PERSON_LEFT, FIRST_PERSON_MOD_LEFT -> "schematictotem.screen.hint_offhand";
            default -> "schematictotem.screen.hint_preview";
        };
    }

    private void drawLines(GuiGraphics context, Component text, int x, int y, int w, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, w);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            context.drawString(font, lines.get(i), x, y + i * 10, color);
        }
    }

    private void drawModelStatus(GuiGraphics context) {
        TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
        int y = statusY + (rowStep - 18) / 2;
        if (slot == null) {
            drawLines(context, Component.translatable("schematictotem.screen.no_file"), contentX, y, contentW, 2, 0xFFC8C8C8);
            return;
        }
        String name = Path.of(slot.label).getFileName().toString();
        context.drawString(font, font.plainSubstrByWidth(name, contentW), contentX, y, 0xFF7FD4FF);
        String status;
        Miniature m = slot.miniature;
        if (slot.loading) status = Component.translatable("schematictotem.info.baking").getString().trim();
        else if (slot.error != null) status = slot.error;
        else if (m != null) status = Component.translatable("schematictotem.screen.status", m.stats.sizeX() + "×" + m.stats.sizeY() + "×" + m.stats.sizeZ(),
                m.levels[0].quadCount(), TotemModels.formatFactor(m.levels[0].factor)).getString();
        else status = Component.translatable("schematictotem.info.waiting").getString().trim();
        context.drawString(font, font.plainSubstrByWidth(status, contentW), contentX, y + 10,
                slot.error != null ? 0xFFFF6060 : 0xFFA8A8A8);
    }

    private void drawPreview(GuiGraphics context) {
        context.renderOutline(boxX - 1, boxY - 1, boxSize + 2, boxSize + 2, 0x60FFFFFF);
        if (minecraft == null) return;
        TotemModels.trackedEntity = null;
        if (previewMode == PreviewMode.POP) {
            // FIXED with no holder is how the game draws the floating totem
            minecraft.getItemModelResolver().updateForTopItem(previewState, totem, ItemDisplayContext.FIXED, minecraft.level, null, 0);
        } else {
            minecraft.getItemModelResolver().updateForTopItem(previewState, totem, previewMode.context, minecraft.level, minecraft.player, 0);
        }
        if (previewState.isEmpty()) return;
        Quaternionf view = new Quaternionf()
                .rotateX(viewPitch * Mth.DEG_TO_RAD)
                .rotateY((viewYaw + (previewMode.seenFromBehind ? 180.0F : 0.0F)) * Mth.DEG_TO_RAD);
        context.guiRenderState.submitPicturesInPictureState(MiniaturePreviewRenderer.State.of(previewState, view, boxX, boxY, boxX + boxSize, boxY + boxSize,
                boxSize * zoom * previewMode.scale));
    }

    private void resetView() {
        viewYaw = 0;
        viewPitch = 0;
        zoom = 1.0F;
    }

    private boolean inBox(double x, double y) {
        return x >= boxX && y >= boxY && x < boxX + boxSize && y < boxY + boxSize;
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (inBox(click.x(), click.y())) {
            if (doubled) resetView();
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingPreview) {
            viewYaw += (float) offsetX * 1.5F;
            viewPitch = Mth.clamp(viewPitch + (float) offsetY * 1.5F, -90.0F, 90.0F);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingPreview = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inBox(mouseX, mouseY)) {
            zoom = Mth.clamp(zoom * (verticalAmount > 0 ? 1.1F : 1 / 1.1F), 0.3F, 4.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ------------------------------------------------------------------------------------------------ closing

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // detail changes re-bake shortly after the slider stops, so the preview shows the result while editing
        if (rebakeCountdown > 0 && --rebakeCountdown == 0) {
            slotRebakeCountdown = 0;
            TotemModels.INSTANCE.applyConfig(config(), true);
        }
        if (slotRebakeCountdown > 0 && --slotRebakeCountdown == 0) {
            TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
            if (slot != null) TotemModels.INSTANCE.rebake(slot);
        }
    }

    @Override
    public void removed() {
        TotemConfig c = config();
        c.sanitize();
        c.save();
        if (rebakeCountdown > 0) {
            rebakeCountdown = 0;
            slotRebakeCountdown = 0;
            TotemModels.INSTANCE.applyConfig(c, true);
        } else if (slotRebakeCountdown > 0) {
            slotRebakeCountdown = 0;
            TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
            if (slot != null) TotemModels.INSTANCE.rebake(slot);
        }
        TotemModels.INSTANCE.appearanceChanged();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            if (perspectiveBefore != null) minecraft.options.setCameraType(perspectiveBefore);
            minecraft.setScreen(parent);
        }
    }
}
