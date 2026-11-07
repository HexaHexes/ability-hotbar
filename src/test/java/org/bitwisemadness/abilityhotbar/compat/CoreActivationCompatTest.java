package org.bitwisemadness.abilityhotbar.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.TargetContext;
import org.bitwisemadness.abilitycore.sync.ActivateAbilityPayload;
import org.junit.jupiter.api.Test;

/**
 * The activation payload's target hint (hotbar#94): an aimed, tagged
 * activation carries the entity in core's {@code TargetContext} wire
 * vocabulary, while a no-hint activation writes the absent-hint form - which
 * core's {@code toRequest} resolves to exactly the request this mod sent
 * before hints existed (AC2: byte-identical for untargeted activations).
 *
 * <p>The round-trip assertions go through core's own {@code toRequest}, so
 * this suite fails if the hotbar's wire strings drift from the vocabulary
 * core parses - the two repos cannot disagree silently.</p>
 */
class CoreActivationCompatTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final UUID AIMED = UUID.fromString("069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d");

    @Test
    void aimedActivationCarriesTheEntityWireForm() {
        var payload = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.DIRECT_SLOT, 3, 12345L, AIMED, null);

        assertEquals("entity", payload.targetKind());
        assertEquals("entity=" + AIMED, payload.targetParams());

        ActivationRequest request = ActivateAbilityPayload.toRequest(payload);
        assertNotNull(request, "core must parse the hotbar's wire form");
        assertEquals(new TargetContext.EntityHint(AIMED), request.target());
        assertEquals(3, request.slotContext());
        assertEquals(ActivationSource.DIRECT_SLOT, request.source());
    }

    @Test
    void noHintPayloadIsTheAbsentHintForm() {
        var payload = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.SELECTED_SLOT, -1, 0L, null, null);

        assertEquals("", payload.targetKind(), "no aim must write a blank kind");
        assertEquals("", payload.targetParams(), "no aim must write blank params");
    }

    @Test
    void noHintRequestIsByteIdenticalToThePreHintRequest() {
        // AC2: everything without a hint must produce the exact request the
        // mod sent before hotbar#94 - blank hint fields read as "no target
        // intent", i.e. the legacy four-arg fromWire.
        var payload = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.DIRECT_SLOT, 3, 12345L, null, null);
        var legacy = ActivateAbilityPayload.toRequest(payload);
        var preHint = ActivationRequest.fromWire(
                FIREBALL.toString(), "direct_slot", 3, 12345L);

        assertNotNull(legacy);
        assertEquals(preHint, legacy,
                "a hint-less activation must equal the pre-#94 request, field for field");
    }

    @Test
    void hintedPayloadSurvivesTheWireCodec() {
        var original = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.DIRECT_SLOT, 3, 12345L, AIMED, null);
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, original);
            assertEquals(original, ActivateAbilityPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void modeContextRidesTheWireAndTheRequest() {
        var payload = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.DIRECT_SLOT, 3, 12345L, null, "fire");
        assertEquals("fire", payload.mode(), "the slot's mode lands on the wire");

        var request = ActivateAbilityPayload.toRequest(payload);
        assertEquals("fire", request.modeContext(),
                "the server reads it as the advisory mode context");

        var none = CoreActivationCompat.payload(
                FIREBALL, ActivationSource.DIRECT_SLOT, 3, 12345L, null, null);
        assertEquals("", none.mode(), "null writes core's no-mode wire form");
        assertEquals(null, ActivateAbilityPayload.toRequest(none).modeContext(),
                "no mode stays no mode - byte-identical to pre-#29 activations");
    }
}
