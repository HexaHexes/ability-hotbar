package org.bitwisemadness.abilityhotbar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.junit.jupiter.api.Test;

/**
 * Proves the hotbar compiles and runs against the published
 * {@code org.bitwisemadness.abilitycore:ability_core} artifact - the classpath
 * entry this test needs comes only from the GitHub Packages dependency.
 *
 * <p>Also pins the wire contract the hotbar relies on for direct-slot and
 * selected-slot activation (REQ-HOT-10/11): an unrecognised source must
 * fail closed to {@code null} rather than defaulting to a policy-widening
 * value.</p>
 */
class CoreContractTest {

    @Test
    void directAndSelectedSourcesMapFromTheirWireNames() {
        assertEquals(ActivationSource.DIRECT_SLOT, ActivationSource.fromWire("direct"));
        assertEquals(ActivationSource.DIRECT_SLOT, ActivationSource.fromWire("direct_slot"));
        assertEquals(ActivationSource.SELECTED_SLOT, ActivationSource.fromWire("selected"));
        assertEquals(ActivationSource.SELECTED_SLOT, ActivationSource.fromWire("selected_slot"));
    }

    @Test
    void unknownSourceFailsClosedToNull() {
        assertNull(ActivationSource.fromWire("not_a_source"));
        assertNull(ActivationSource.fromWire(null));
    }

    @Test
    void wireNameRoundTrips() {
        for (ActivationSource source : ActivationSource.values()) {
            assertEquals(source, ActivationSource.fromWire(source.wireName()),
                    "wireName() must round-trip through fromWire() for " + source);
        }
    }
}
