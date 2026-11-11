package org.bitwisemadness.abilityhotbar.compat;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.sync.MasteryQueryAnsweredEvent;
import org.bitwisemadness.abilitycore.sync.MasteryQueryRequest;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
import org.jetbrains.annotations.Nullable;

/**
 * Core mastery for the ability menu's ROW display (REQ-CORE-12): the
 * client asks for the local player's snapshot when the menu opens, core
 * answers through {@link MasteryQueryAnsweredEvent}, and the rows read
 * level/XP/progress from here - the level badge in front of the name and
 * the bottom-line meter. Solicited only - core never pushes game data
 * (docs/sync.md), and this class keeps only the last answer so the rows
 * render instantly on reopen while a fresh exchange is in flight.
 *
 * <p>Loaded only behind a {@code ModList.isLoaded} guard at registration
 * ({@code AbilityHotbar}'s CoreSyncCompat block), so when core is absent
 * this class is never loaded and its core references never resolve - the
 * mod's optional-runtime invariant.</p>
 *
 * <p>Levels and XP come from the SERVER's answer only: the client never
 * runs the curve (a datapack {@code mastery/levels.json} lives server-side
 * and would diverge), the echoed request uuid fences out a reply for
 * another player, and an unanswered menu renders no badge and no bar -
 * a zero appears only when the server's snapshot says "never earned".</p>
 */
public final class CoreMasteryCompat {

    /** One exchange per menu open at most - resize re-inits coalesce. */
    static final long REQUEST_INTERVAL_MS = 1000L;

    private static long lastRequestAt;

    @Nullable
    private static String pendingTarget;

    private static boolean answered;

    @Nullable
    private static MasteryQueryResponse snapshot;

    private CoreMasteryCompat() {
    }

    /** Subscribes the snapshot receiver (call behind the isLoaded guard). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CoreMasteryCompat::onAnswered);
    }

    /** Whether a snapshot has arrived (the rows' badge/bar gate). */
    public static boolean answered() {
        return answered;
    }

    /**
     * Asks the server for {@code target}'s snapshot, throttled to one
     * exchange per {@link #REQUEST_INTERVAL_MS}. The echo the answer must
     * carry is recorded first, so a reply for anyone else is dropped.
     */
    public static void request(UUID target) {
        long now = System.currentTimeMillis();
        if (!shouldRequest(now)) {
            return;
        }
        markRequested(target, now);
        MasteryQueryRequest.sendToServer(target);
    }

    /** Throttle answer: false while a recent exchange is still in flight. */
    static boolean shouldRequest(long now) {
        return now - lastRequestAt >= REQUEST_INTERVAL_MS;
    }

    /** Records the pending echo + timestamp (the send's stateful half). */
    static void markRequested(UUID target, long now) {
        lastRequestAt = now;
        pendingTarget = target.toString();
    }

    /** Accepts an arrived answer echoing OUR request; anyone else is dropped. */
    static void accept(MasteryQueryResponse response) {
        if (pendingTarget == null || !pendingTarget.equals(response.targetPlayer())) {
            return;
        }
        snapshot = response;
        answered = true;
    }

    /** The row's own-target level for {@code id} (server truth; 0 = none stored). */
    public static int levelOf(ResourceLocation id) {
        MasteryQueryResponse.Entry entry = entry(id);
        return entry == null ? 0 : entry.level();
    }

    /** The row's own-target XP for {@code id} (server truth; 0 = none stored). */
    public static double xpOf(ResourceLocation id) {
        MasteryQueryResponse.Entry entry = entry(id);
        return entry == null ? 0.0 : entry.xp();
    }

    /** The row's own-target in-level fill for {@code id} (0 = none stored). */
    public static double progressOf(ResourceLocation id) {
        MasteryQueryResponse.Entry entry = entry(id);
        return entry == null ? 0.0 : entry.progress();
    }

    /**
     * The answered snapshot's current effect bonuses for {@code id} -
     * one per axis the ability's spec declares (empty before the answer
     * arrives, for undeclared abilities, and for non-participating
     * axes: the pane shows exactly what the server would apply).
     */
    public static List<MasteryQueryResponse.Bonus> bonusesOf(ResourceLocation id) {
        MasteryQueryResponse.Entry entry = entry(id);
        return entry == null ? List.of() : entry.bonuses();
    }

    /** Whether the ability participates in mastery (client-side declarations). */
    public static boolean declared(ResourceLocation id) {
        return MasteryDeclarations.has(id);
    }

    /** Honest XP text: whole pools lose the decimal, fractions keep it. */
    public static String formatXp(double xp) {
        if (Double.isFinite(xp) && Math.abs(xp - Math.rint(xp)) < 1.0e-9) {
            return Long.toString((long) Math.rint(xp));
        }
        return String.valueOf(xp);
    }

    /** The answered snapshot's ABILITY entry for one id, or null - never guessed. */
    @Nullable
    private static MasteryQueryResponse.Entry entry(ResourceLocation id) {
        if (!answered || snapshot == null) {
            return null;
        }
        String key = id.toString();
        for (MasteryQueryResponse.Entry candidate : snapshot.entries()) {
            if ("ABILITY".equals(candidate.kind()) && key.equals(candidate.id())) {
                return candidate;
            }
        }
        return null;
    }

    private static void onAnswered(MasteryQueryAnsweredEvent event) {
        accept(event.response());
    }

    /** Test seam: each test starts from a clean exchange (JUnit has no order). */
    static void reset() {
        lastRequestAt = 0L;
        pendingTarget = null;
        answered = false;
        snapshot = null;
    }
}
