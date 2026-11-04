package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.junit.jupiter.api.Test;

/**
 * The crosshair target hint's opt-in gate (hotbar#94): only a {@code targeted}
 * -tagged definition ever produces a hint, and only when something is actually
 * aimed at - untagged, unknown and empty-crosshair activations all resolve to
 * no hint, which is the byte-identical pre-#94 request downstream.
 *
 * <p>Also pins the distribution contract (AC4): the class reads
 * {@code Minecraft.hitResult}, so it must stay in the client package - a
 * source-tree scan fails the gate if the crosshair read ever migrates into
 * common code a dedicated server would load.</p>
 */
class TargetHintsTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path CLIENT_PACKAGE =
            SOURCE_ROOT.resolve(Path.of("org", "bitwisemadness", "abilityhotbar", "client"));

    private static AbilityDefinition definition(Set<String> tags) {
        return new AbilityDefinition("tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, null, tags, Map.of());
    }

    @Test
    void taggedAbilityAimedAtAnEntityCarriesTheHint() {
        UUID aimed = UUID.randomUUID();

        assertEquals(aimed,
                TargetHints.hintFor(definition(Set.of("targeted", "melee")), aimed),
                "a tagged ability aimed at an entity must attach the hint");
    }

    @Test
    void untaggedAbilityNeverCarriesAHint() {
        UUID aimed = UUID.randomUUID();

        assertNull(TargetHints.hintFor(definition(Set.of("melee")), aimed),
                "an untagged ability must never attach a hint even when aimed");
        assertNull(TargetHints.hintFor(definition(Set.of()), aimed),
                "no tags at all is untagged too");
    }

    @Test
    void taggedAbilityWithNothingAimedCarriesNoHint() {
        assertNull(TargetHints.hintFor(definition(Set.of("targeted")), null),
                "an empty crosshair must produce no hint");
    }

    @Test
    void unknownDefinitionFailsClosedToNoHint() {
        assertNull(TargetHints.hintFor(null, UUID.randomUUID()),
                "a definition that has not synced yet must not guess");
    }

    @Test
    void crosshairReadStaysInTheClientPackage() throws IOException {
        // AC4: Minecraft.hitResult may only be read from client code. Scan the
        // whole main tree - not just this class - so the read cannot migrate
        // into a package a dedicated server loads.
        assertTrue(Files.isDirectory(CLIENT_PACKAGE),
                "expected the client package (test working dir is the project dir)");
        boolean found = false;
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (!text.contains("hitResult")) {
                    continue;
                }
                found = true;
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                assertTrue(relative.startsWith("org/bitwisemadness/abilityhotbar/client/"),
                        "the crosshair read (hitResult) must stay client-only, found in: "
                                + relative);
            }
        }
        assertTrue(found, "the crosshair read must exist somewhere - did it get deleted?");
        assertFalse(Files.readString(
                        SOURCE_ROOT.resolve(Path.of("org", "bitwisemadness", "abilityhotbar",
                                "state", "ActivationResolver.java")),
                        StandardCharsets.UTF_8).contains("hitResult"),
                "sanity: the common resolver must not read the crosshair");
    }
}
