package org.bitwisemadness.abilityhotbar.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.bitwisemadness.abilityhotbar.content.ContentPlan;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The owner write API for content-owned bars (hotbar#89): loud fail-closed
 * validation, on-demand growth of the bar entry, idempotent authoritative
 * re-seeding, and the distribution contract (common code).
 */
class SeededAssignmentsTest {

    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "other");
    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");
    private static final ResourceLocation EXTRA =
            ResourceLocation.fromNamespaceAndPath("otherpack", "extra");

    @BeforeEach
    @AfterEach
    void isolate() {
        HotbarRegistry.reset();
    }

    @Test
    void seedCreatesAndGrowsTheBarEntryOnDemand() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);

        BarState first = SeededAssignments.seed(BarState.empty(3), CURIOS, 0, "pseudocore_curios:press");

        Bar bar = SeededAssignments.storedBar(first, CURIOS).orElseThrow();
        assertEquals(1, bar.slotCount(), "the first seed creates the entry it needs");
        assertEquals(PRESS, bar.assignment(0).abilityId());

        BarState grown = SeededAssignments.seed(first, CURIOS, 5, "pseudocore_curios:press");
        Bar bigger = SeededAssignments.storedBar(grown, CURIOS).orElseThrow();
        assertEquals(6, bigger.slotCount(), "a later seed grows the entry");
        assertEquals(PRESS, bigger.assignment(5).abilityId());
        assertEquals(PRESS, bigger.assignment(0).abilityId(), "earlier seeds survive the growth");
        assertTrue(bigger.assignment(3).isEmpty(), "untouched cells stay empty");
    }

    @Test
    void structuralErrorsThrowBeforeAnythingIsWritten() {
        HotbarRegistry.register(EXTRA, "otherpack.bar.extra", true);
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState base = BarState.empty(3);
        BarState before = base;

        // unregistered bar
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base,
                        ResourceLocation.fromNamespaceAndPath("nobody", "bar"), 0,
                        "pseudocore_curios:press"));
        // assignable bars are player/menu-owned
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base, EXTRA, 0, "pseudocore_curios:press"));
        // out-of-range slots (both ends)
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base, CURIOS, -1, "pseudocore_curios:press"));
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base, CURIOS, ContentPlan.MAX_CELLS, "press"));
        // malformed id and null arguments
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base, CURIOS, 0, "not a valid id"));
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed((BarState) null, CURIOS, 0, "pseudocore_curios:press"));
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seed(base, null, 0, "pseudocore_curios:press"));

        // fail-closed: a rejected call mutates nothing
        assertEquals(before, base);
        assertTrue(SeededAssignments.storedBar(base, CURIOS).isEmpty());
    }

    @Test
    void nullClearsTheCellAndTheEntryStays() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);

        BarState seeded = SeededAssignments.seed(BarState.empty(3), CURIOS, 0, "pseudocore_curios:press");
        BarState cleared = SeededAssignments.seed(seeded, CURIOS, 0, null);

        Bar bar = SeededAssignments.storedBar(cleared, CURIOS).orElseThrow();
        assertTrue(bar.assignment(0).isEmpty(), "null clears the cell");
        assertEquals(1, bar.slotCount(), "the entry itself stays (it is stored state now)");
    }

    @Test
    void reseedIsAuthoritativeAndIdempotent() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);

        BarState seeded = SeededAssignments.seed(BarState.empty(3), CURIOS, 0, "pseudocore_curios:press");
        BarState changed = SeededAssignments.seed(seeded, CURIOS, 0, "pseudocore_curios:other");
        BarState reseeded = SeededAssignments.seed(changed, CURIOS, 0, "pseudocore_curios:press");
        BarState again = SeededAssignments.seed(reseeded, CURIOS, 0, "pseudocore_curios:press");

        assertEquals(PRESS,
                SeededAssignments.storedBar(reseeded, CURIOS).orElseThrow().assignment(0).abilityId(),
                "the latest re-seed wins over any prior state");
        assertArrayEquals(reseeded.bars()[reseeded.bars().length - 1].assignments(),
                again.bars()[again.bars().length - 1].assignments(),
                "re-seeding the same value changes nothing");
        assertEquals(reseeded.activeBarId(), again.activeBarId());
    }

    @Test
    void seedingTouchesNeitherOtherBarsNorTheActiveBar() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState base = BarState.empty(3, 3).withAssignment(0, PRESS);

        BarState seeded = SeededAssignments.seed(base, CURIOS, 2, "pseudocore_curios:press");

        assertEquals(base.activeBarId(), seeded.activeBarId(), "seeding never switches the bar");
        assertEquals(base.bars().length + 1, seeded.bars().length, "only the new entry appears");
        assertEquals(PRESS, seeded.bars()[0].assignment(0).abilityId(), "existing bars untouched");
        assertTrue(seeded.bars()[1].assignment(0).isEmpty());
        assertEquals(3, seeded.bars()[0].slotCount(), "existing bars keep their shape");
    }

    @Test
    void seedAllMatchesTheEquivalentSeedLoop() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState base = BarState.empty(3);

        BarState viaLoop = base;
        for (int slot = 0; slot < 4; slot++) {
            viaLoop = SeededAssignments.seed(viaLoop, CURIOS, slot, "pseudocore_curios:press");
        }
        BarState viaBatch = SeededAssignments.seedAll(base, CURIOS, 4,
                slot -> "pseudocore_curios:press");

        assertSameState(viaLoop, viaBatch, "the batch is the loop, end to end (hotbar#114)");
        assertSameState(viaBatch, SeededAssignments.seedAll(viaBatch, CURIOS, 4,
                        slot -> "pseudocore_curios:press"),
                "re-running the batch changes nothing");
    }

    /**
     * Field-wise state comparison: BarState/Bar carry arrays, so record
     * equality is reference equality - the existing fixtures compare pieces
     * for the same reason.
     */
    private static void assertSameState(BarState expected, BarState actual, String message) {
        assertEquals(expected.activeBarId(), actual.activeBarId(), message + " (active bar)");
        assertEquals(expected.schemaVersion(), actual.schemaVersion(), message + " (schema)");
        assertEquals(expected.bars().length, actual.bars().length, message + " (bar count)");
        for (int i = 0; i < expected.bars().length; i++) {
            assertEquals(expected.bars()[i].id(), actual.bars()[i].id(), message + " (id " + i + ")");
            assertEquals(expected.bars()[i].slotCount(), actual.bars()[i].slotCount(),
                    message + " (slots " + i + ")");
            assertEquals(expected.bars()[i].selectedSlot(), actual.bars()[i].selectedSlot(),
                    message + " (selection " + i + ")");
            assertArrayEquals(expected.bars()[i].assignments(), actual.bars()[i].assignments(),
                    message + " (assignments " + i + ")");
        }
    }

    @Test
    void seedAllAppliesNullAsClearPerSlot() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);

        BarState seeded = SeededAssignments.seedAll(BarState.empty(3), CURIOS, 3,
                slot -> "pseudocore_curios:press");
        BarState cleared = SeededAssignments.seedAll(seeded, CURIOS, 3, slot -> null);

        Bar bar = SeededAssignments.storedBar(cleared, CURIOS).orElseThrow();
        assertEquals(3, bar.slotCount(), "the entry stays (it is stored state now)");
        for (int slot = 0; slot < 3; slot++) {
            assertTrue(bar.assignment(slot).isEmpty(), "slot " + slot + " cleared");
        }
    }

    @Test
    void seedAllRejectsABadBatchBeforeWritingAnything() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState base = BarState.empty(3);

        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seedAll(base, CURIOS, ContentPlan.MAX_CELLS + 1,
                        slot -> "pseudocore_curios:press"),
                "slotCount past MAX_CELLS fails before the first write");
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seedAll(base, CURIOS, -1, slot -> "pseudocore_curios:press"));
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seedAll(base,
                        ResourceLocation.fromNamespaceAndPath("nobody", "bar"), 2,
                        slot -> "pseudocore_curios:press"),
                "an unregistered bar fails (the first seed validates it)");
        assertThrows(IllegalArgumentException.class,
                () -> SeededAssignments.seedAll(base, CURIOS, 2, null));

        assertEquals(base, base, "a rejected batch mutates nothing (pure - nothing was returned)");
        assertTrue(SeededAssignments.storedBar(base, CURIOS).isEmpty());
    }

    @Test
    void seedAllLogsEverySeedInDebug() {
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);

        try (LogCapture logs = LogCapture.forLogger(SeededAssignments.class,
                org.apache.logging.log4j.Level.DEBUG)) {
            SeededAssignments.seedAll(BarState.empty(3), CURIOS, 3,
                    slot -> "pseudocore_curios:press");

            List<String> lines = logs.messages();
            assertEquals(3, lines.size(), "one debug line per seeded cell: " + lines);
            assertTrue(lines.get(0).contains("pseudocore:curios") && lines.get(0).contains("slot 0")
                    && lines.get(0).contains("pseudocore_curios:press"),
                    "bar, slot and ability ride the line: " + lines.get(0));
        }
    }

    @Test
    void statePackageNeverReferencesClientOnlyClasses() throws IOException {
        // AC: the write API and its state are common code - callable on a
        // dedicated server with no client class loaded.
        Path root = Path.of("src", "main", "java")
                .resolve(Path.of("org", "bitwisemadness", "abilityhotbar", "state"));
        assertTrue(Files.isDirectory(root), "source root must exist: " + root);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(files.isEmpty(), "the state package must exist");
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(text.contains("net.minecraft.client"),
                    file + " must load on a dedicated server");
            assertFalse(text.contains("abilityhotbar.client"),
                    file + " must load on a dedicated server");
        }
    }
}
