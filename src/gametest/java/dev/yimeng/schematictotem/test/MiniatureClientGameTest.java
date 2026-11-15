package dev.yimeng.schematictotem.test;

import com.google.gson.GsonBuilder;
import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.config.PoseKind;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.gui.TotemConfigScreen;
import dev.yimeng.schematictotem.mesh.Miniature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Real-client check: loads a schematic, verifies the totem's render state carries the miniature in every display
 * context, takes screenshots (GUI, first/third person, item frame, dropped, the totem pop animation, the settings
 * screen) and compares the time per frame with the vanilla totem. With -Dschematictotem.test.sweep=true it also
 * sweeps candidate poses (screenshots named after the values) for tuning the defaults.
 * Writes schematictotem-test-report.json in the run dir.
 */
public final class MiniatureClientGameTest implements FabricClientGameTest {
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private ClientGameTestContext context;
    private TestServerContext server;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.context = context;
        String file = System.getProperty("schematictotem.test.file",
                "schematics/test.litematic");
        String front = System.getProperty("schematictotem.test.front", "");
        boolean sweep = Boolean.getBoolean("schematictotem.test.sweep");
        report.put("file", file);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            server = sp.getServer();
            for (String command : List.of("gamerule doDaylightCycle false", "time set noon", "gamerule doMobSpawning false",
                    "weather clear", "gamerule doWeatherCycle false")) {
                sp.getServer().runCommand(command);
            }
            context.runOnClient(client -> {
                client.options.getMaxFps().setValue(260);
                client.options.getEnableVsync().setValue(false);
                client.options.hudHidden = false;
            });

            // 1. load and bake
            context.runOnClient(client -> TotemModels.INSTANCE.use(Path.of(file)));
            context.waitFor(client -> {
                TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                return slot != null && !slot.loading;
            }, 20 * 60);
            Miniature miniature = context.computeOnClient(client -> TotemModels.INSTANCE.defaultSlot().miniature);
            if (miniature == null) {
                failures.add("bake failed: " + context.computeOnClient(client -> TotemModels.INSTANCE.defaultSlot().error));
                return;
            }
            report.put("bake", context.computeOnClient(client -> TotemModels.describe(miniature).getString()));
            report.put("guessedFront", miniature.suggestedFront);
            if (!front.isEmpty()) {
                context.runOnClient(client -> {
                    TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                    TotemModels.INSTANCE.config().optionsForEdit(slot.label).front = front;
                    TotemModels.INSTANCE.appearanceChanged();
                });
            }

            // 2. the render state carries the miniature in every context
            for (ItemDisplayContext displayContext : ItemDisplayContext.values()) {
                if (displayContext == ItemDisplayContext.NONE) continue;
                boolean[] result = context.computeOnClient(client -> {
                    KeyedItemRenderState state = new KeyedItemRenderState();
                    client.getItemModelManager().clearAndUpdate(state, new ItemStack(Items.TOTEM_OF_UNDYING), displayContext, client.world,
                            client.player, 0);
                    return new boolean[]{state.getModelKey() instanceof List<?> keys && keys.contains(miniature), state.isSideLit()};
                });
                if (!result[0]) failures.add("totem not replaced in " + displayContext);
                // the inventory uses vanilla's front-lit rig (its 3D rig leaves a model's front dark); only the GUI reads this flag
                if (result[0] && result[1] == (displayContext == ItemDisplayContext.GUI)) {
                    failures.add(displayContext + " should be " + (displayContext == ItemDisplayContext.GUI ? "front-lit" : "side-lit"));
                }
            }

            // 3. scene: player at the origin looking north; a wall with item frames 4 blocks ahead, a dropped totem
            sp.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:totem_of_undying");
            sp.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:totem_of_undying");
            sp.getServer().runCommand("fill -4 -60 -5 4 -55 -5 minecraft:white_concrete");
            sp.getServer().runCommand("summon minecraft:item_frame -1 -58 -4 {block_pos:[I;-1,-58,-4],Facing:3b,Item:{id:\"minecraft:totem_of_undying\",count:1},Fixed:1b}");
            sp.getServer().runCommand("summon minecraft:item_frame 1 -58 -4 {block_pos:[I;1,-58,-4],Facing:3b,Item:{id:\"minecraft:totem_of_undying\",count:1},Fixed:1b,ItemRotation:2b}");
            sp.getServer().runCommand("summon minecraft:item 0.5 -60 -2.5 {Item:{id:\"minecraft:totem_of_undying\",count:1},PickupDelay:32767,Age:-32768}");
            sp.getServer().runCommand("tp @p 0.5 -60 0.5 180 10");
            context.waitTicks(40);

            int frameCount = context.computeOnClient(client -> client.world.getEntitiesByClass(
                    net.minecraft.entity.decoration.ItemFrameEntity.class,
                    new net.minecraft.util.math.Box(-4, -61, -6, 4, -54, -2), entity -> entity.isAlive()).size());
            report.put("sceneItemFrames", frameCount);
            if (frameCount != 2) failures.add("expected two item frames in the scene, got " + frameCount);

            clearChat();
            shots("default");
            context.runOnClient(client -> client.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING)));
            context.waitTicks(8);
            context.takeScreenshot("schematictotem_pop_8");
            context.waitTicks(10);
            context.takeScreenshot("schematictotem_pop_18");
            context.waitTicks(40);

            PoseKind handPose = handPoses();
            if (Boolean.getBoolean("schematictotem.test.modSweep")) modSweep();
            if (FabricLoader.getInstance().isModLoaded("punchy")) modFit();

            // settings screen, every tab and preview mode
            context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
            context.waitTicks(5);
            context.setScreen(() -> new TotemConfigScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("schematictotem_screen_model");
            context.clickScreenButton("schematictotem.screen.tab.pose");
            context.waitTicks(3);
            context.takeScreenshot("schematictotem_screen_pose");
            // the pose tab opens on the pose the totem in hand really uses (the context button shows just its name)
            List<String> labels = context.computeOnClient(client -> Arrays.stream(PoseKind.values()).map(k -> k.label().getString()).toList());
            String poseButton = context.computeOnClient(client -> client.currentScreen.children().stream()
                    .filter(w -> w instanceof ClickableWidget)
                    .map(w -> ((ClickableWidget) w).getMessage().getString())
                    .filter(labels::contains)
                    .findFirst().orElse("?"));
            report.put("poseTabOpensOn", poseButton);
            String expectedLabel = context.computeOnClient(client -> handPose.label().getString());
            if (!poseButton.equals(expectedLabel)) failures.add("pose tab opened on '" + poseButton + "', expected " + expectedLabel);
            context.clickScreenButton("schematictotem.screen.preview.fixed");
            context.waitTicks(3);
            context.takeScreenshot("schematictotem_screen_pose_frame");
            context.clickScreenButton("schematictotem.screen.tab.general");
            context.clickScreenButton("schematictotem.screen.preview.pop");
            context.waitTicks(3);
            context.takeScreenshot("schematictotem_screen_general_pop");
            context.clickScreenButton("schematictotem.screen.tab.model");
            context.clickScreenButton("schematictotem.screen.preview.gui");
            context.setScreen(() -> null);
            context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));

            // per-schematic detail: a lower budget re-bakes this schematic coarser
            context.runOnClient(client -> {
                TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                TotemModels.INSTANCE.config().optionsForEdit(slot.label).budget = 1500;
                TotemModels.INSTANCE.rebake(slot);
            });
            context.waitFor(client -> !TotemModels.INSTANCE.defaultSlot().loading, 20 * 60);
            report.put("detail1500", context.computeOnClient(client -> TotemModels.describe(TotemModels.INSTANCE.defaultSlot().miniature).getString()));
            clearChat();
            context.waitTicks(5);
            context.takeScreenshot("schematictotem_detail_1500_first_person");
            context.runOnClient(client -> {
                TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                TotemModels.INSTANCE.config().optionsForEdit(slot.label).budget = 0;
                TotemModels.INSTANCE.rebake(slot);
            });
            context.waitFor(client -> !TotemModels.INSTANCE.defaultSlot().loading, 20 * 60);
            clearChat();

            if (sweep) sweep();

            // 4. render-thread CPU per frame: miniature vs vanilla totem, both hands, first person
            context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
            report.put("cpuMiniature", msPerFrame());
            context.runOnClient(client -> {
                TotemModels.INSTANCE.config().enabled = false;
                TotemModels.INSTANCE.appearanceChanged();
            });
            report.put("cpuVanilla", msPerFrame());
            context.runOnClient(client -> {
                TotemModels.INSTANCE.config().enabled = true;
                TotemModels.INSTANCE.appearanceChanged();
            });
            report.put("cpuMiniatureAgain", msPerFrame());
            String big = System.getProperty("schematictotem.test.big",
                    "schematics/large.litematic");
            if (Files.isRegularFile(Path.of(big))) {
                context.runOnClient(client -> TotemModels.INSTANCE.use(Path.of(big)));
                context.waitFor(client -> {
                    TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                    return slot != null && !slot.loading && slot.miniature != null;
                }, 20 * 60);
                report.put("bigBake", context.computeOnClient(client -> TotemModels.describe(TotemModels.INSTANCE.defaultSlot().miniature).getString()));
                clearChat();
                context.waitTicks(10);
                context.takeScreenshot("schematictotem_big_first_person");
                report.put("cpuBigMiniature", msPerFrame());

                // "完全还原": the finest level is the build itself, 1:1
                context.runOnClient(client -> {
                    TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                    TotemModels.INSTANCE.config().optionsForEdit(slot.label).budget = TotemConfig.FULL_DETAIL;
                    TotemModels.INSTANCE.rebake(slot);
                });
                context.waitFor(client -> {
                    TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                    return !slot.loading && slot.miniature != null;
                }, 20 * 120);
                Miniature full = context.computeOnClient(client -> TotemModels.INSTANCE.defaultSlot().miniature);
                report.put("bigFullDetail", context.computeOnClient(client -> TotemModels.describe(full).getString()));
                if (full.levels[0].factor != 1.0F) failures.add("full detail is 1:" + full.levels[0].factor);
                context.waitTicks(10);
                context.takeScreenshot("schematictotem_big_full_detail_first_person");
                context.setScreen(() -> new InventoryScreen(MinecraftClient.getInstance().player));
                context.waitTicks(5);
                context.takeScreenshot("schematictotem_big_full_detail_inventory");
                context.setScreen(() -> null);
                clearChat();
                report.put("cpuBigFullDetail", msPerFrame());
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            failures.add("crash: " + t);
            report.put("stacktrace", sw.toString());
        } finally {
            report.put("failures", failures);
            report.put("status", failures.isEmpty() ? "PASS" : "FAIL");
            try {
                Files.writeString(Path.of("schematictotem-test-report.json"),
                        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
            } catch (IOException ignored) {
            }
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
    }

    private void clearChat() {
        context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
    }

    /** First person, inventory, third person from the front and from behind. */
    private void shots(String tag) {
        clearChat();
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        context.waitTicks(4);
        context.takeScreenshot("schematictotem_" + tag + "_first_person");
        context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        context.waitTicks(4);
        context.takeScreenshot("schematictotem_" + tag + "_third_front");
        context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        context.waitTicks(4);
        context.takeScreenshot("schematictotem_" + tag + "_third_back");
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        context.setScreen(() -> new InventoryScreen(MinecraftClient.getInstance().player));
        context.waitTicks(4);
        context.takeScreenshot("schematictotem_" + tag + "_inventory");
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /** Candidate poses for tuning: each screenshot is named after the values tried. */
    private void sweep() {
        clearChat();
        // reference: a vanilla block in the main hand next to the miniature in the offhand
        runServer("item replace entity @p weapon.mainhand with minecraft:grass_block");
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        context.waitTicks(5);
        context.takeScreenshot("sweep_reference_block_vs_offhand");
        runServer("item replace entity @p weapon.mainhand with minecraft:totem_of_undying");

        float[][] firstPerson = {{0.32F, 6, 0}, {0.32F, 6, -2}, {0.32F, 6, -4}, {0.36F, 5, -3}};
        for (float[] v : firstPerson) {
            for (PoseKind kind : new PoseKind[]{PoseKind.FIRST_PERSON_RIGHT, PoseKind.FIRST_PERSON_LEFT}) {
                context.runOnClient(client -> {
                    TotemConfig.Pose pose = kind.get(TotemModels.INSTANCE.config().poses);
                    pose.scale = v[0];
                    pose.translation = new float[]{v[2], v[1], 0};
                    TotemModels.INSTANCE.appearanceChanged();
                });
            }
            context.waitTicks(4);
            context.takeScreenshot(String.format(Locale.ROOT, "sweep_fp_s%.2f_ty%.0f_tx%.0f", v[0], v[1], v[2]));
        }
        context.runOnClient(client -> {
            TotemModels.INSTANCE.config().poses = new TotemConfig.Poses();
            TotemModels.INSTANCE.appearanceChanged();
        });
        float[][] thirdPerson = {{0.5F, 3, 1.5F}, {0.6F, 3, 1.5F}, {0.6F, 1, 3}};
        for (float[] v : thirdPerson) {
            for (PoseKind kind : new PoseKind[]{PoseKind.THIRD_PERSON_RIGHT, PoseKind.THIRD_PERSON_LEFT}) {
                context.runOnClient(client -> {
                    TotemConfig.Pose pose = kind.get(TotemModels.INSTANCE.config().poses);
                    pose.scale = v[0];
                    pose.translation = new float[]{0, v[1], v[2]};
                    TotemModels.INSTANCE.appearanceChanged();
                });
            }
            context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(4);
            context.takeScreenshot(String.format(Locale.ROOT, "sweep_tp_s%.2f_ty%.0f_tz%.1f", v[0], v[1], v[2]));
        }
        context.runOnClient(client -> {
            TotemModels.INSTANCE.config().poses = new TotemConfig.Poses();
            TotemModels.INSTANCE.appearanceChanged();
            client.options.setPerspective(Perspective.FIRST_PERSON);
        });
    }

    /**
     * Which pose each hand is drawn with (vanilla first person, or the mod pose when a mod like Punchy draws first
     * person with third-person contexts), and that changing that pose - and only that one - moves the totem on screen.
     * Returns the right hand's first-person pose.
     */
    private PoseKind handPoses() throws IOException {
        boolean punchy = FabricLoader.getInstance().isModLoaded("punchy");
        report.put("punchy", punchy);
        PoseKind right = punchy ? PoseKind.FIRST_PERSON_MOD_RIGHT : PoseKind.FIRST_PERSON_RIGHT;
        PoseKind left = punchy ? PoseKind.FIRST_PERSON_MOD_LEFT : PoseKind.FIRST_PERSON_LEFT;
        PoseKind unused = punchy ? PoseKind.FIRST_PERSON_RIGHT : PoseKind.FIRST_PERSON_MOD_RIGHT;
        clearChat();

        context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        context.waitTicks(25);
        List<String> usedThird = posesInUse();
        report.put("posesUsedInThirdPerson", usedThird);
        if (!usedThird.contains(PoseKind.THIRD_PERSON_RIGHT.id) || !usedThird.contains(PoseKind.THIRD_PERSON_LEFT.id)
                || usedThird.contains(right.id)) {
            failures.add("third person should use the third-person poses, used: " + usedThird);
        }

        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        context.waitTicks(25);
        List<String> used = posesInUse();
        report.put("posesUsedInFirstPerson", used);
        if (!used.contains(right.id) || !used.contains(left.id)) failures.add("first person should use " + right.id + " and " + left.id + ", used: " + used);
        if (used.contains(unused.id)) failures.add(unused.id + " should not be used in first person");
        PoseKind recent = context.computeOnClient(client -> TotemModels.recentHandPose());
        report.put("recentHandPose", recent == null ? "none" : recent.id);
        if (recent != right) failures.add("recentHandPose should be " + right.id + ", was " + recent);

        // halving a pose's scale must halve the size the miniature is drawn at in that hand, and only there
        context.takeScreenshot("schematictotem_hands_base");
        float[] base = drawnScales();
        float[] halfRight = withPose(right, pose -> pose.scale *= 0.5F, "schematictotem_hands_right_half");
        float[] halfLeft = withPose(left, pose -> pose.scale *= 0.5F, "schematictotem_hands_left_half");
        float[] halfUnused = withPose(unused, pose -> pose.scale *= 0.5F, "schematictotem_hands_unused_half");
        report.put("drawnScale", String.format(Locale.ROOT,
                "right %.4f / left %.4f; right pose halved %.4f / %.4f; left halved %.4f / %.4f; %s halved %.4f / %.4f",
                base[0], base[1], halfRight[0], halfRight[1], halfLeft[0], halfLeft[1], unused.id, halfUnused[0], halfUnused[1]));
        if (!near(halfRight[0], base[0] * 0.5F) || !near(halfRight[1], base[1])) failures.add("halving " + right.id + " did not halve the right hand only");
        if (!near(halfLeft[1], base[1] * 0.5F) || !near(halfLeft[0], base[0])) failures.add("halving " + left.id + " did not halve the left hand only");
        if (!near(halfUnused[0], base[0]) || !near(halfUnused[1], base[1])) failures.add("halving " + unused.id + " changed a hand");
        return right;
    }

    private List<String> posesInUse() {
        return context.computeOnClient(client -> Arrays.stream(PoseKind.values()).filter(TotemModels::poseInUse).map(k -> k.id).toList());
    }

    /** Scale of the matrices the miniature was last drawn with in the hand pass: [right hand, left hand]. */
    private float[] drawnScales() {
        context.waitTicks(3);
        return context.computeOnClient(client -> new float[]{HandFrameProbe.drawnScale(true), HandFrameProbe.drawnScale(false)});
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 0.02F * Math.max(Math.abs(a), Math.abs(b)) + 1e-5F;
    }

    /** Screenshot and drawn scales with one pose changed, then the pose restored. */
    private float[] withPose(PoseKind kind, java.util.function.Consumer<TotemConfig.Pose> change, String name) {
        TotemConfig.Pose saved = context.computeOnClient(client -> kind.get(TotemModels.INSTANCE.config().poses).copy());
        context.runOnClient(client -> {
            change.accept(kind.get(TotemModels.INSTANCE.config().poses));
            TotemModels.INSTANCE.appearanceChanged();
        });
        float[] scales = drawnScales();
        context.takeScreenshot(name);
        context.runOnClient(client -> {
            TotemConfig.Pose pose = kind.get(TotemModels.INSTANCE.config().poses);
            pose.rotation = saved.rotation;
            pose.translation = saved.translation;
            pose.scale = saved.scale;
            TotemModels.INSTANCE.appearanceChanged();
        });
        context.waitTicks(3);
        return scales;
    }

    /**
     * Measures the frame Punchy draws each hand's totem in and screenshots poses computed from it: the model upright,
     * its front turned towards the camera (and by yaw towards the middle of the screen), at a few sizes and heights.
     */
    private void modFit() {
        clearChat();
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        HandFrameProbe.clear();
        context.waitTicks(10);
        org.joml.Matrix4f right = HandFrameProbe.frame(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        org.joml.Matrix4f left = HandFrameProbe.frame(ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        if (right == null || left == null) {
            failures.add("no hand frame measured (right " + right + ", left " + left + ")");
            return;
        }
        report.put("punchyRightFrame", HandFrameProbe.describe(right));
        report.put("punchyLeftFrame", HandFrameProbe.describe(left));
        float[][] candidates = {{-20, 0.40F, 0.5F}, {-20, 0.30F, 0.5F}, {-20, 0.50F, 0.5F}, {-20, 0.40F, 0.0F}, {-20, 0.40F, 0.8F},
                {0, 0.40F, 0.5F}, {-35, 0.40F, 0.5F}};
        Map<String, String> computed = new LinkedHashMap<>();
        for (float[] c : candidates) {
            org.joml.Vector3f rotR = HandFrameProbe.facingCamera(right, c[0], false), rotL = HandFrameProbe.facingCamera(left, -c[0], true);
            org.joml.Vector3f upR = HandFrameProbe.upOnScreen(right, c[2] * c[1], false), upL = HandFrameProbe.upOnScreen(left, c[2] * c[1], true);
            String name = String.format(Locale.ROOT, "modfit_yaw%.0f_s%.2f_up%.1f", c[0], c[1], c[2]);
            computed.put(name, String.format(Locale.ROOT, "right [%.1f, %.1f, %.1f] t [%.2f, %.2f, %.2f]; left [%.1f, %.1f, %.1f] t [%.2f, %.2f, %.2f]",
                    rotR.x, rotR.y, rotR.z, upR.x, upR.y, upR.z, rotL.x, rotL.y, rotL.z, upL.x, upL.y, upL.z));
            context.runOnClient(client -> {
                TotemConfig.Poses poses = TotemModels.INSTANCE.config().poses;
                set(PoseKind.FIRST_PERSON_MOD_RIGHT.get(poses), rotR, upR, c[1]);
                set(PoseKind.FIRST_PERSON_MOD_LEFT.get(poses), rotL, upL, c[1]);
                TotemModels.INSTANCE.appearanceChanged();
            });
            context.waitTicks(4);
            context.takeScreenshot(name);
        }
        report.put("punchyComputedPoses", computed);
        context.runOnClient(client -> {
            TotemConfig.Poses poses = TotemModels.INSTANCE.config().poses;
            PoseKind.FIRST_PERSON_MOD_RIGHT.reset(poses);
            PoseKind.FIRST_PERSON_MOD_LEFT.reset(poses);
            TotemModels.INSTANCE.appearanceChanged();
        });
    }

    private static void set(TotemConfig.Pose pose, org.joml.Vector3f rotation, org.joml.Vector3f translation, float scale) {
        pose.rotation = new float[]{rotation.x, rotation.y, rotation.z};
        pose.translation = new float[]{translation.x, translation.y, translation.z};
        pose.scale = scale;
    }

    /** Candidate poses for first person drawn by a mod (Punchy): screenshots named after the values, both hands at once. */
    private void modSweep() {
        clearChat();
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        String spec = System.getProperty("schematictotem.test.modPoses", "90,90,0,0,3,1.5,0.5");
        for (String candidate : spec.split(";")) {
            String[] v = candidate.split(",");
            float[] f = new float[7];
            for (int i = 0; i < 7; i++) f[i] = Float.parseFloat(v[i].trim());
            context.runOnClient(client -> {
                TotemConfig.Poses poses = TotemModels.INSTANCE.config().poses;
                TotemConfig.Pose pose = PoseKind.FIRST_PERSON_MOD_RIGHT.get(poses);
                pose.rotation = new float[]{f[0], f[1], f[2]};
                pose.translation = new float[]{f[3], f[4], f[5]};
                pose.scale = f[6];
                PoseKind.FIRST_PERSON_MOD_RIGHT.copyToOtherHand(poses);
                TotemModels.INSTANCE.appearanceChanged();
            });
            context.waitTicks(4);
            context.takeScreenshot("modsweep_" + candidate.trim().replace(',', '_'));
        }
        context.runOnClient(client -> {
            TotemConfig.Poses poses = TotemModels.INSTANCE.config().poses;
            PoseKind.FIRST_PERSON_MOD_RIGHT.reset(poses);
            PoseKind.FIRST_PERSON_MOD_LEFT.reset(poses);
            TotemModels.INSTANCE.appearanceChanged();
        });
    }

    private void runServer(String command) {
        server.runCommand(command);
        context.waitTicks(2);
    }

    private void setPose(PoseKind kind, float[] rotation) {
        context.runOnClient(client -> {
            kind.get(TotemModels.INSTANCE.config().poses).rotation = rotation.clone();
            TotemModels.INSTANCE.appearanceChanged();
        });
    }

    /** Render-thread CPU per frame (whole frame, and the first-person hand pass) over 200 ticks. */
    private String msPerFrame() {
        context.waitTicks(20);
        FrameTimer.reset();
        context.waitTicks(200);
        double[] r = FrameTimer.read();
        return String.format(Locale.ROOT, "%.0f frames, %.3f ms/frame, hand pass %.3f, item vertex writing %.3f, miniature upload+draw %.3f ms (%.2f MB)/frame",
                r[0], r[1], r[2], r[3], r[4], r[5]);
    }
}
