package org.bitwisemadness.abilityhotbar.compat;

import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.activation.ActivationContext;
import org.bitwisemadness.abilitycore.activation.EligibilityRule;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/**
 * The bar-scope activation guard on core's canonical eligibility chain
 * (hotbar#90 AC4).
 *
 * <p>Core's {@link EligibilityRule} contract hands the rule the
 * {@link ServerPlayer}, so the guard reads the <b>hotbar's own</b> attachment
 * for the server-authoritative active bar - no new public accessor on either
 * side, exactly as the issue requires. An out-of-scope activation answers
 * {@link ActivationOutcome#REFUSED_UNAVAILABLE}; an unscoped ability never
 * refuses.</p>
 *
 * <p>Common code. The caller registers it only when {@code ability_core} is
 * loaded (the {@code ModList} guard in the mod constructor, mirroring
 * {@link CoreSyncCompat}), so core's types never resolve on a core-less
 * runtime - and no client class is involved on any distribution.</p>
 */
public final class CoreScopeCompat {

    private CoreScopeCompat() {
    }

    /**
     * Adds the scope rule to {@link AbilityCore#ELIGIBILITY}. Call at mod init
     * behind the "core is loaded" guard; rules run in registration order and
     * a refusal short-circuits the pipeline.
     */
    public static void register() {
        AbilityCore.ELIGIBILITY.register(new EligibilityRule() {
            @Override
            public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
                    ActivationContext context) {
                BarState state = player.getData(BarStateAttachments.BAR_STATE);
                return refusalFor(request.abilityId(), state.activeBarId());
            }

            @Override
            public Set<ActivationSource> appliesTo() {
                // Every source: a scoped ability must be on its bar however
                // the activation arrives (direct slot, selected, native, menu).
                return Set.of();
            }
        });
    }

    /**
     * The guard's decision, pure so it is fixture-able: an unscoped ability
     * never refuses; a scoped one refuses with {@code REFUSED_UNAVAILABLE}
     * unless the active bar is its scope bar.
     *
     * @param abilityId the ability being activated
     * @param activeBar the player's server-authoritative active bar id
     * @return empty = permitted; present = terminal refusal
     */
    public static Optional<ActivationResult> refusalFor(ResourceLocation abilityId,
            ResourceLocation activeBar) {
        return AbilityBarScopes.scopeOf(abilityId)
                .filter(scope -> !scope.equals(activeBar))
                .map(scope -> ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
    }
}
