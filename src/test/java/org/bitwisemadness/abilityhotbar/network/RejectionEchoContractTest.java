package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The rejection re-send contract (core#19 AC5, ADR-003): a client→server
 * request is intent only, and <b>every</b> answer is a snapshot - accepted
 * requests echo the mutated state, rejected ones echo the current state, so
 * an optimistically-applied client view always converges on the server's
 * answer.
 *
 * <p>Pinned by scanning the source tree (the pattern {@link
 * org.bitwisemadness.abilityhotbar.content.ContentDistributionTest} uses for
 * distribution): the handlers are one-line branches over their named seams,
 * so the structural contract is what a future edit could silently drop - a
 * handler that persists on accept but forgets the reject-echo fails this gate
 * instead of diverging a client in game.</p>
 */
class RejectionEchoContractTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path NETWORK_PACKAGE =
            SOURCE_ROOT.resolve(Path.of("org", "bitwisemadness", "abilityhotbar", "network"));

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        assertTrue(Files.isDirectory(root),
                "expected a source directory (test working dir is the project dir): " + root);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void everyStateRequestHandlerEchoesOnRejection() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFilesUnder(NETWORK_PACKAGE)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("BarStateSync.accept")) {
                continue; // not a state-mutating request handler
            }
            if (!text.contains("BarStateSync.sendSnapshot")) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "handlers that persist on accept must also echo the current state on reject: "
                        + offenders);
    }

    @Test
    void theSnapshotPayloadAppliesOnTheClient() throws IOException {
        // the other half of convergence: the re-sent snapshot must land in the
        // client's view, or the echo has nothing to converge with.
        Path snapshot = NETWORK_PACKAGE.resolve("BarStateSnapshotPayload.java");
        String text = Files.readString(snapshot, StandardCharsets.UTF_8);
        assertTrue(text.contains("ClientBarState.set"),
                "the snapshot handler must apply to ClientBarState so echoes converge");
    }
}
