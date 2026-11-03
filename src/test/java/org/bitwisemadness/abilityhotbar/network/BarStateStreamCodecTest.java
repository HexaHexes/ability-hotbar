package org.bitwisemadness.abilityhotbar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.junit.jupiter.api.Test;

/**
 * The bar-state network codec must round-trip exactly what {@link BarState}
 * carries - assignment order, empties, selection and schema version - since
 * the snapshot payload is the only path by which the client learns the bar.
 */
class BarStateStreamCodecTest {

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
    void multiBarStateRoundTripsBarsActiveBarAndSchema() {
        // Schema v2 snapshot: every bar, its own selection, and the active bar
        // must all survive the wire (ADR-011; both sides ship together).
        BarState original = BarState.empty(3, 3)
                .withAssignment(0, ResourceLocation.fromNamespaceAndPath("tensura", "fireball"))
                .withSelectedSlot(2)
                .withActiveBar(1)
                .withAssignment(2, ResourceLocation.fromNamespaceAndPath("iss", "magic_missile"))
                .withSelectedSlot(1);

        BarState decoded = roundTrip(original);

        assertEquals(3, decoded.bars().length);
        assertEquals(1, decoded.activeBar());
        assertEquals(original.schemaVersion(), decoded.schemaVersion());
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
