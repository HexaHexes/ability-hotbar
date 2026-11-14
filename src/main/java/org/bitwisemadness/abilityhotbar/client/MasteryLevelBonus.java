package org.bitwisemadness.abilityhotbar.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The row badge's extra mastery levels (owner's brief: the mastery level
 * shown for tensura abilities also counts Tensura's two mastery bars -
 * Learned adds 1, Mastered adds 2). A consumer whose display state lives
 * outside core's snapshot declares a source per id here, and the badge
 * adds the answer on top of core's own level.
 *
 * <p>Fail-soft like {@link LiveCosts}: an unknown id, a null player or
 * id, a throwing source, or a negative answer reads 0 - the badge stays
 * the plain core level, byte-identical for every id without a
 * declaration. Client display only; sources read state the consumer
 * already syncs or reads client-side (never a server round trip).</p>
 */
public final class MasteryLevelBonus {

    /** One ability's bonus answer (client display only). */
    @FunctionalInterface
    public interface BonusSource {

        /**
         * @param player the asking player (nullable)
         * @param id     the ability being drawn
         * @return levels to add to the shown mastery level
         */
        int bonusFor(@Nullable Player player, ResourceLocation id);
    }

    private static final Map<ResourceLocation, BonusSource> BONUSES = new ConcurrentHashMap<>();

    private MasteryLevelBonus() {
    }

    /**
     * Declares an ability's badge bonus (client display only).
     *
     * @param id     the ability whose badge adds levels
     * @param source the live answer (consumer-owned state)
     * @throws IllegalArgumentException on null arguments
     */
    public static void declare(ResourceLocation id, BonusSource source) {
        if (id == null || source == null) {
            throw new IllegalArgumentException("ability id and bonus source must not be null");
        }
        BONUSES.put(id, source);
    }

    /**
     * What the badge adds: the declared answer, else 0 - a broken source
     * costs one row's badge, never the screen.
     *
     * @param player the asking player (nullable)
     * @param id     the ability being drawn (nullable)
     * @return levels to add to the shown mastery level
     */
    public static int bonusFor(@Nullable Player player, @Nullable ResourceLocation id) {
        BonusSource source = id == null ? null : BONUSES.get(id);
        if (source == null) {
            return 0;
        }
        try {
            return Math.max(0, source.bonusFor(player, id));
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }

    /** Test isolation / reload paths. */
    public static boolean remove(ResourceLocation id) {
        return id != null && BONUSES.remove(id) != null;
    }
}
