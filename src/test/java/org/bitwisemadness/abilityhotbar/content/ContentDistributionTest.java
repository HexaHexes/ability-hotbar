package org.bitwisemadness.abilityhotbar.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The distribution contract (hotbar#83 AC 8): registration must be callable
 * on a dedicated server, so the content package may never reference a
 * client-only class; and the per-frame extraction - the point where a
 * provider actually runs - may only be invoked from client code. Both are
 * pinned by scanning the source tree, so a future edit that leaks a client
 * import into the common package fails the gate instead of a dedicated
 * server's classloader.
 */
class ContentDistributionTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path CONTENT_PACKAGE =
            SOURCE_ROOT.resolve(Path.of("org", "bitwisemadness", "abilityhotbar", "content"));

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        assertTrue(Files.isDirectory(root),
                "expected a source directory (test working dir is the project dir): " + root);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void contentPackageNeverReferencesClientOnlyClasses() throws IOException {
        List<Path> files = javaFilesUnder(CONTENT_PACKAGE);
        assertFalse(files.isEmpty(), "the content package must exist");
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(text.contains("net.minecraft.client"),
                    file + " must load on a dedicated server (no client imports)");
            assertFalse(text.contains("abilityhotbar.client"),
                    file + " must load on a dedicated server (no client imports)");
        }
    }

    @Test
    void perFrameProviderExtractionOnlyRunsInClientCode() throws IOException {
        for (Path file : javaFilesUnder(SOURCE_ROOT)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("ContentPlan.build(")) {
                continue;
            }
            String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
            assertTrue(relative.startsWith("org/bitwisemadness/abilityhotbar/client/"),
                    "ContentPlan.build (where a provider runs) must stay client-only, found in: "
                            + relative);
        }
    }
}
