package dev.yimeng.schematictotem.mesh;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.yimeng.schematictotem.schematic.SchematicReader;
import dev.yimeng.schematictotem.schematic.Volume;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads every schematic in the test folder (the player's real files), bakes it with stand-in block models and
 * checks the invariants of the result; also times the vanilla per-quad vertex path for the most detailed level.
 * Writes build/reports/schematictotem/pipeline.md.
 */
class SchematicPipelineTest {
    private static final MiniatureBaker.Settings SETTINGS = new MiniatureBaker.Settings(8000, true, true, 96, true, false);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static List<Path> files() throws IOException {
        String dir = System.getProperty("schematictotem.testDir");
        Assumptions.assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "no schematic folder: " + dir);
        try (Stream<Path> stream = Files.walk(Path.of(dir), 3)) {
            return stream.filter(Files::isRegularFile).filter(SchematicReader::isSupported).sorted().toList();
        }
    }

    @Test
    void readBakeAndMeasure() throws IOException {
        List<Path> files = files();
        Assumptions.assumeFalse(files.isEmpty(), "no schematic files");
        StubBlockModels models = new StubBlockModels();
        List<String> report = new ArrayList<>();
        report.add("| file | box | blocks (file / read) | palette | read ms | bake ms | quads L0..L3 (1:factor) | exact 1:1 quads | block entities | vanilla emit L0 | front guess |");
        report.add("|---|---|---|---|---|---|---|---|---|---|---|");
        List<String> failures = new ArrayList<>();

        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                long t0 = System.nanoTime();
                Volume volume = SchematicReader.read(file, 256L * 1024 * 1024);
                long readMs = (System.nanoTime() - t0) / 1_000_000L;
                long expected = expectedBlocks(file);
                if (expected >= 0 && expected != volume.nonAirCount()) {
                    failures.add(name + ": " + volume.nonAirCount() + " blocks read, metadata says " + expected);
                }
                if (volume.isEmpty()) {
                    report.add("| " + name + " | empty | | | " + readMs + " | | | | |");
                    continue;
                }

                Miniature m = MiniatureBaker.bake(volume, SETTINGS, name, models, models, () -> false);
                check(m, failures, name);
                // "完全还原": the finest level is 1:1 up to the safety limits, coarser levels still fit 1/4, 1/16, 1/64 of it
                long t1 = System.nanoTime();
                Miniature full = MiniatureBaker.bake(volume, new MiniatureBaker.Settings(8000, true, true, 96, true, true), name, models, models,
                        () -> false);
                long fullMs = (System.nanoTime() - t1) / 1_000_000L;
                Miniature.Level exact = full.levels[0];
                boolean capped = exact.factor != 1.0F;
                if (exact.quadCount() > MiniatureBaker.FULL_DETAIL_MAX_QUADS) failures.add(name + ": full detail over the quad limit");
                if (exact.quadCount() < m.levels[0].quadCount()) failures.add(name + ": full detail has fewer quads than budgeted");
                for (int i = 1; i < 4; i++) {
                    long cap = Math.max(64L, (long) exact.quadCount() >> (2 * i));
                    boolean minimal = full.levels[i].resolution() <= 1;
                    if (full.levels[i].quadCount() > cap && !minimal) failures.add(name + ": full detail level " + i + " over its share");
                }
                String fullText = String.format(Locale.ROOT, "%d%s, %d ms", exact.quadCount(),
                        capped ? String.format(Locale.ROOT, " (capped 1:%.2f)", exact.factor) : "", fullMs);

                StringBuilder levels = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    Miniature.Level l = m.levels[i];
                    if (i > 0) levels.append(" / ");
                    levels.append(l.quadCount()).append(String.format(Locale.ROOT, " (1:%.2f)", l.factor));
                }
                String emit = emitBenchmark(m.levels[0]);
                Miniature.Stats s = m.stats;
                report.add(String.format(Locale.ROOT, "| %s | %dx%dx%d | %s / %d | %d | %d | %d | %s | %s | %d | %s | %s |",
                        name, s.sizeX(), s.sizeY(), s.sizeZ(), expected < 0 ? "?" : String.valueOf(expected), s.blocks(), s.paletteSize(),
                        readMs, s.bakeMillis(), levels, fullText, m.levels[0].blockEntities.size(), emit, m.suggestedFront));
            } catch (IllegalArgumentException e) {
                // e.g. a layer of invisible light blocks: refused with a message, which is the expected outcome
                report.add("| " + name + " | refused: " + e.getMessage() + " | | | | | | | |");
            } catch (Exception e) {
                e.printStackTrace();
                failures.add(name + ": " + e);
                report.add("| " + name + " | ERROR " + e.getMessage() + " | | | | | | | |");
            }
        }

        report.add("");
        report.add(failures.isEmpty() ? "All checks passed." : "Failures:\n- " + String.join("\n- ", failures));
        String text = String.join("\n", report);
        System.out.println(text);
        Path out = Path.of(System.getProperty("schematictotem.reportDir", "build/reports/schematictotem"), "pipeline.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, text + "\n", StandardCharsets.UTF_8);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Litematica stores the number of non-air blocks in Metadata.TotalBlocks. */
    private static long expectedBlocks(Path file) throws IOException {
        if (!file.getFileName().toString().endsWith(".litematic")) return -1;
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        int regions = root.getCompoundOrEmpty("Regions").getKeys().size();
        if (regions != 1) return -1; // overlapping regions are merged, so the sum may differ
        return root.getCompoundOrEmpty("Metadata").getInt("TotalBlocks", -1);
    }

    private static void check(Miniature m, List<String> failures, String name) {
        for (int i = 0; i < 4; i++) {
            Miniature.Level l = m.levels[i];
            long budget = Math.max(64L, (long) SETTINGS.quadBudget() >> (2 * i));
            boolean minimal = l.gridX <= 1 && l.gridY <= 1 && l.gridZ <= 1;
            if (l.quadCount() > budget && !minimal) failures.add(name + ": level " + i + " has " + l.quadCount() + " quads > " + budget);
            if (i > 0 && l.factor < m.levels[i - 1].factor) failures.add(name + ": level " + i + " is finer than level " + (i - 1));
            if (i > 0 && l.quadCount() > m.levels[i - 1].quadCount()) failures.add(name + ": level " + i + " has more quads than level " + (i - 1));
            // a coarse grid can stick out by up to half a cell (+ rounding of its integer origin) on each side
            float slack = (0.5F * l.factor + 0.5F) / Math.max(m.stats.sizeX(), Math.max(m.stats.sizeY(), m.stats.sizeZ())) + 1e-4F;
            org.joml.Vector3f p = new org.joml.Vector3f();
            for (Mesh mesh : List.of(l.solid, l.translucent)) {
                for (int q = 0; q < mesh.size(); q++) {
                    for (int k = 0; k < 4; k++) {
                        mesh.position(q, k, p);
                        if (p.x() < -slack || p.y() < -slack || p.z() < -slack || p.x() > 1 + slack || p.y() > 1 + slack || p.z() > 1 + slack) {
                            failures.add(name + ": level " + i + " vertex outside the unit cube: " + p);
                            return;
                        }
                    }
                }
            }
        }
        assertTrue(m.levels[0].blockEntities.size() <= SETTINGS.maxBlockEntities());
        assertEquals(4, m.levels.length);
    }

    /** Mesh.emit into an entity-format buffer, as done every frame for a held totem. */
    private static String emitBenchmark(Miniature.Level level) {
        int count = level.quadCount();
        if (count == 0) return "-";
        int[] tints = new int[64];
        java.util.Arrays.fill(tints, -1);
        try (BufferAllocator allocator = new BufferAllocator(1 << 20)) {
            MatrixStack matrices = new MatrixStack();
            int frames = 0;
            long best = Long.MAX_VALUE;
            long end = System.nanoTime() + 1_500_000_000L;
            while (frames < 400 && System.nanoTime() < end) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frames));
                MatrixStack.Entry entry = matrices.peek();
                long t0 = System.nanoTime();
                BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                level.solid.emit(entry, builder, 0xF000F0, OverlayTexture.DEFAULT_UV, tints, 0);
                level.translucent.emit(entry, builder, 0xF000F0, OverlayTexture.DEFAULT_UV, tints, 0);
                try (BuiltBuffer built = builder.endNullable()) {
                    long dt = System.nanoTime() - t0;
                    if (frames > 20) best = Math.min(best, dt);
                }
                allocator.reset();
                matrices.pop();
                frames++;
            }
            if (best == Long.MAX_VALUE) return "-";
            return String.format(Locale.ROOT, "%.3f ms (%.0f ns/quad)", best / 1e6, (double) best / count);
        } catch (Throwable t) {
            return "n/a (" + t.getClass().getSimpleName() + ")";
        }
    }
}
