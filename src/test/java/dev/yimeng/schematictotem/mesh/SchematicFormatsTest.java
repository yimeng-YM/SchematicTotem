package dev.yimeng.schematictotem.mesh;

import dev.yimeng.schematictotem.schematic.SchematicReader;
import dev.yimeng.schematictotem.schematic.Volume;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** All supported file formats must resolve to the same block states on every game version. */
class SchematicFormatsTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }
    private static Path fixture(String extension) throws Exception {
        return Path.of(Objects.requireNonNull(SchematicFormatsTest.class.getResource("/fixtures/original-house." + extension)).toURI());
    }
    @Test void formatsResolveToIdenticalBlocks() throws Exception {
        Volume expected = SchematicReader.read(fixture("litematic"), 1_000_000);
        assertEquals(7, expected.sizeX);
        assertEquals(5, expected.sizeY);
        assertEquals(6, expected.sizeZ);
        assertEquals(110, expected.nonAirCount());
        for (String format : new String[]{"schem", "nbt"}) {
            Volume actual = SchematicReader.read(fixture(format), 1_000_000);
            assertEquals(expected.nonAirCount(), actual.nonAirCount(), format);
            for (int y = 0; y < expected.sizeY; y++) for (int z = 0; z < expected.sizeZ; z++) for (int x = 0; x < expected.sizeX; x++) {
                assertEquals(expected.state(expected.get(x, y, z)), actual.state(actual.get(x, y, z)), format + " at " + x + "," + y + "," + z);
            }
        }
    }
    @Test void boundsLimitIsEnforcedInEveryFormat() throws Exception {
        for (String format : new String[]{"litematic", "schem", "nbt"}) {
            Path file = fixture(format);
            assertThrows(java.io.IOException.class, () -> SchematicReader.read(file, 100), format);
        }
    }
}
