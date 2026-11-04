package org.bitwisemadness.abilityhotbar.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ability-to-bar scope declarations (hotbar#90 AC1/AC2/AC6): loud setup-time
 * failures, the query every consumer filters with, and the distribution
 * contract (common code, no client class).
 */
class AbilityBarScopesTest {

    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press");
    private static final ResourceLocation OTHER_VERB =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "other");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");

    @BeforeEach
    @AfterEach
    void isolate() {
        AbilityBarScopes.reset();
        HotbarRegistry.reset();
    }

    @Test
    void registerStoresTheScopeAndAnswersQueries() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);

        assertEquals(CURIOS, AbilityBarScopes.scopeOf(PRESS).orElseThrow());
        assertTrue(AbilityBarScopes.isInScope(PRESS, CURIOS), "its own bar keeps it");
        assertFalse(AbilityBarScopes.isInScope(PRESS, HotbarRegistry.builtinBarId(0)),
                "every other bar is out of scope");
        assertFalse(AbilityBarScopes.isInScope(PRESS, null), "no bar context never matches a scope");
    }

    @Test
    void undeclaredAbilitiesPassEverywhere() {
        // AC5: no declaration = today's behaviour, on every bar.
        assertTrue(AbilityBarScopes.isInScope(OTHER_VERB, HotbarRegistry.builtinBarId(0)));
        assertTrue(AbilityBarScopes.isInScope(OTHER_VERB, null));
        assertTrue(AbilityBarScopes.isInScope(null, null));
        assertTrue(AbilityBarScopes.scopeOf(OTHER_VERB).isEmpty());
        assertTrue(AbilityBarScopes.scopeOf(null).isEmpty());
    }

    @Test
    void unknownBarIdFailsLoudly() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AbilityBarScopes.register(PRESS, CURIOS));
        assertTrue(e.getMessage().contains(CURIOS.toString()), "the id must be named");

        // built-ins are deliberately unreachable - scoping targets registered bars only
        IllegalArgumentException builtin = assertThrows(IllegalArgumentException.class,
                () -> AbilityBarScopes.register(PRESS, HotbarRegistry.builtinBarId(0)));
        assertTrue(builtin.getMessage().contains("registered"));
        assertTrue(AbilityBarScopes.scopeOf(PRESS).isEmpty());
    }

    @Test
    void duplicateAndContradictoryDeclarationsFailLoudly() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        AbilityBarScopes.register(PRESS, CURIOS);

        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> AbilityBarScopes.register(PRESS, CURIOS));
        assertTrue(duplicate.getMessage().contains("already scoped"));

        IllegalStateException contradictory = assertThrows(IllegalStateException.class,
                () -> AbilityBarScopes.register(PRESS, EXTRA));
        assertTrue(contradictory.getMessage().contains(CURIOS.toString()),
                "the existing scope must be named: " + contradictory.getMessage());
        assertEquals(CURIOS, AbilityBarScopes.scopeOf(PRESS).orElseThrow(),
                "a rejected declaration never shadows the first");
    }

    @Test
    void nullArgumentsFailLoudly() {
        assertThrows(IllegalArgumentException.class, () -> AbilityBarScopes.register(null, CURIOS));
        assertThrows(IllegalArgumentException.class, () -> AbilityBarScopes.register(PRESS, null));
    }

    @Test
    void resetClearsEveryDeclaration() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        AbilityBarScopes.register(PRESS, CURIOS);

        AbilityBarScopes.reset();

        assertTrue(AbilityBarScopes.scopeOf(PRESS).isEmpty());
        assertTrue(AbilityBarScopes.isInScope(PRESS, HotbarRegistry.builtinBarId(0)));
    }

    @Test
    void scopePackageNeverReferencesClientOnlyClasses() throws IOException {
        // AC6: declarations and queries are common code - callable at setup on
        // a dedicated server with no client class loaded.
        Path root = Path.of("src", "main", "java")
                .resolve(Path.of("org", "bitwisemadness", "abilityhotbar", "registry"));
        assertTrue(Files.isDirectory(root), "source root must exist: " + root);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(files.isEmpty(), "the registry package must exist");
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(text.contains("net.minecraft.client"),
                    file + " must load on a dedicated server");
            assertFalse(text.contains("abilityhotbar.client"),
                    file + " must load on a dedicated server");
        }
    }
}
