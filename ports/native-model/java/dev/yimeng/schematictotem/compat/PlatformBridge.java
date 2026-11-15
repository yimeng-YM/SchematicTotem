package dev.yimeng.schematictotem.compat;

import dev.yimeng.schematictotem.SchematicTotemClient;
import java.nio.file.Path;
import java.util.Locale;

public final class PlatformBridge {
    private PlatformBridge() { }
    public static void openPath(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String executable = os.startsWith("windows") ? "explorer.exe" : os.startsWith("mac") ? "open" : "xdg-open";
        try { new ProcessBuilder(executable, path.toAbsolutePath().toString()).start(); }
        catch (Exception e) { SchematicTotemClient.LOGGER.warn("Could not open {}", path, e); }
    }
}
