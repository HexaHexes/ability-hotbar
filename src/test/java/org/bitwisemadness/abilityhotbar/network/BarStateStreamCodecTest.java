package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.SeededAssignments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bar-state network codec must round-trip exactly what {@link BarState}
 * carries - assignment order, empties, selection and schema version - since
 * the snapshot payload is the only path by which the client learns the bar.
 */
class BarStateStreamCodecTest {

    private static final ResourceLocation CURIOS =
            ResourceLocation.fromNamespaceAndPath("pseudocore", "curios");

    /** The seeding fixture registers a bar; keep the static registry clean. */
    @BeforeEach
    @AfterEach
    void isolateRegistry() {
        HotbarRegistry.reset();
    }

    private static BarState roundTrip(BarState state) {
        ByteBuf buf = Unpooled.buffer();
        try {
            BarState.STREAM_CODEC.encode(buf, state);
            BarState decoded = BarState.STREAM_CODEC.decode(buf);
            assertFalse(buf.isReadable(), "codec must consume the whole buffer");
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    void roundTripsAssignmentsSelectionAndSchema() {
        BarState original = BarState.empty(4)
                .withAssignment(0, ResourceLocation.fromNamespaceAndPath("tensura", "fireball"))
                .withAssignment(3, ResourceLocation.fromNamespaceAndPath("iss", "magic_missile"))
                .withSelectedSlot(2);

        BarState decoded = roundTrip(original);
        assertEquals(original.assignments().length, decoded.assignments().length);
        assertEquals(original.selectedSlot(), decoded.selectedSlot());
        assertEquals(original.schemaVersion(), decoded.schemaVersion());
        for (int i = 0; i < original.assignments().length; i++) {
            assertEquals(original.assignment(i).abilityId(), decoded.assignment(i).abilityId(),
                    "slot " + i + " must round-trip");
        }
    }

    @Test
    void emptyStateRoundTrips() {
        BarState decoded = roundTrip(BarState.empty(2));
        assertEquals(2, decoded.assignments().length);
        assertTrue(decoded.assignment(0).isEmpty());
        assertTrue(decoded.assignment(1).isEmpty());
    }

    @Test
    void seededContentOwnedBarRoundTripsThroughTheSnapshot() {
        // #89 AC2: seeds ride the same wire as everything else - the client's
        // snapshot carries the seeded cell and the active content-owned bar.
        HotbarRegistry.register(CURIOS, "pseudocore.bar.curios", false);
        BarState seeded = SeededAssignments.seed(BarState.empty(3), CURIOS, 2,
                        "pseudocore_curios:press")
                .withActiveBar(CURIOS);

        BarState decoded = roundTrip(seeded);

        assertEquals(CURIOS, decoded.activeBarId());
        Bar stored = null;
        for (Bar bar : decoded.bars()) {
            if (bar.id().equals(CURIOS)) {
                stored = bar;
            }
        }        assertTrue(stored != null, "the content-owned bar itself crosses the wire");
        assertEquals(ResourceLocation.fromNamespaceAndPath("pseudocore_curios", "press"),
                stored.assignment(2).abilityId(), "the seeded cell survives the snapshot");
    }

    @Test
    void multiBarStateRoundTripsBarsActiveBarAndSchema() {
        // Schema v3 snapshot: every bar with its identity, its own selection,
        // and the active bar id must all survive the wire (ADR-011/012; both
        // sides ship together).
        BarState original = BarState.empty(3, 3)
                .withAssignment(0, ResourceLocation.fromNamespaceAndPath("tensura", "fireball"))
                .withSelectedSlot(2)
                .withActiveBar(HotbarRegistry.builtinBarId(1))
                .withAssignment(2, ResourceLocation.fromNamespaceAndPath("iss", "magic_missile"))
                .withSelectedSlot(1);

        BarState decoded = roundTrip(original);

        assertEquals(3, decoded.bars().length);
        assertEquals(HotbarRegistry.builtinBarId(1), decoded.activeBarId());
        assertEquals(original.schemaVersion(), decoded.schemaVersion());
        // Bar identities round-trip - the wire carries them, not just positions.
        assertEquals(HotbarRegistry.builtinBarId(0), decoded.bars()[0].id());
        assertEquals(HotbarRegistry.builtinBarId(1), decoded.bars()[1].id());
        assertEquals(HotbarRegistry.builtinBarId(2), decoded.bars()[2].id());
        // Active-bar view targets bar 1.
        assertEquals(1, decoded.selectedSlot());
        assertEquals(ResourceLocation.fromNamespaceAndPath("iss", "magic_missile"),
                decoded.assignment(2).abilityId());
        // Bar 0 kept its own slot and selection.
        assertEquals(ResourceLocation.fromNamespaceAndPath("tensura", "fireball"),
                decoded.bars()[0].assignment(0).abilityId());
        assertEquals(2, decoded.bars()[0].selectedSlot());
        // Bar 2 is untouched by the others.
        assertTrue(decoded.bars()[2].assignment(0).isEmpty());
        assertEquals(0, decoded.bars()[2].selectedSlot());
    }
}
