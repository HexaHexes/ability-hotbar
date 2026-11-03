package org.bitwisemadness.abilityhotbar.registry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.AbilityHotbar;

/**
 * The hotbar registration API mods call at setup (ADR-012, hotbar#77).
 *
 * <p>A registration declares a hotbar's stable id, its display name and whether
 * players may assign abilities to it. Assignability gates two surfaces: the
 * assignment screen lists only assignable bars, and the server rejects
 * assignment requests naming a bar that does not exist or is not assignable.
 * Non-assignable bars (e.g. Pseudocore's curios bar) carry no attachment
 * state - their content stays with the registering mod.</p>
 *
 * <p>Built-in cyclable bars are <b>not</b> registered: they have positional ids
 * ({@code ability_hotbar:bar_N}) and are implicitly assignable, so
 * {@link #isAssignable(ResourceLocation)} answers for them by pattern.</p>
 *
 * <p>Registration is a code API for mod setup, not a datapack: hotbars are
 * structural and the requirement is "when a mod registers". The list preserves
 * registration order because the cycle set is "built-in window, then registered
 * bars in registration order". Writes are rare (mod setup) and reads frequent
 * (cycling, screens), so a copy-on-write list gives lock-free reads.</p>
 */
public final class HotbarRegistry {

    /** Path prefix of built-in bar ids; {@code bar_0}, {@code bar_1}, ... */
    private static final String BUILTIN_PREFIX = "bar_";

    private static final List<HotbarRegistration> REGISTRATIONS = new CopyOnWriteArrayList<>();

    /**
     * Guards the check-then-add in {@link #register}. A dedicated monitor, not
     * the list itself: synchronizing on a concurrent collection is both a
     * SpotBugs finding (JLM) and pointless, since CopyOnWriteArrayList has no
     * atomic put-if-absent of its own to protect.
     */
    private static final Object REGISTRATION_LOCK = new Object();

    private HotbarRegistry() {
    }

    /**
     * Declares a hotbar. Call during mod setup.
     *
     * @param id             stable identity; the {@code ability_hotbar:} namespace is reserved for built-ins
     * @param displayNameKey non-blank translation key for the bar's name
     * @param assignable     whether abilities can be assigned to this bar
     * @return the stored registration
     * @throws IllegalArgumentException on a reserved namespace, a blank name key or a malformed id
     * @throws IllegalStateException    when the id is already registered
     */
    public static HotbarRegistration register(ResourceLocation id, String displayNameKey, boolean assignable) {
        if (id == null) {
            throw new IllegalArgumentException("hotbar id must not be null");
        }
        if (id.getNamespace().equals(AbilityHotbar.MOD_ID)) {
            throw new IllegalArgumentException(
                    "namespace '" + AbilityHotbar.MOD_ID + "' is reserved for built-in bars: " + id);
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank for " + id);
        }
        HotbarRegistration registration = new HotbarRegistration(id, displayNameKey.trim(), assignable);
        // CopyOnWriteArrayList has no atomic put-if-absent; registration is a
        // setup-time action, so synchronized check-then-add suffices. The
        // duplicate check is by id so changed metadata still fails loudly.
        synchronized (REGISTRATION_LOCK) {
            if (get(id).isPresent()) {
                throw new IllegalStateException("hotbar already registered: " + id);
            }
            REGISTRATIONS.add(registration);
        }
        return registration;
    }

    /** @return the registration for the id, if a mod declared it */
    public static Optional<HotbarRegistration> get(ResourceLocation id) {
        for (HotbarRegistration registration : REGISTRATIONS) {
            if (registration.id().equals(id)) {
                return Optional.of(registration);
            }
        }
        return Optional.empty();
    }

    /** @return every registered hotbar, in registration order (the cycle-set tail) */
    public static List<HotbarRegistration> all() {
        return List.copyOf(REGISTRATIONS);
    }

    /** @return only assignable registrations, in registration order - the assignment screen's query */
    public static List<HotbarRegistration> assignableBars() {
        return REGISTRATIONS.stream().filter(HotbarRegistration::assignable).toList();
    }

    /**
     * The assignability answer for any bar id: built-ins are implicitly
     * assignable, registered bars answer with their flag, unknown ids are not
     * assignable (ADR-012 - the server re-validates every mutation with this).
     */
    public static boolean isAssignable(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        if (isBuiltin(id)) {
            return true;
        }
        return get(id).map(HotbarRegistration::assignable).orElse(false);
    }

    /** True for the positional built-in ids {@code ability_hotbar:bar_N}. */
    public static boolean isBuiltin(ResourceLocation id) {
        if (id == null || !id.getNamespace().equals(AbilityHotbar.MOD_ID)) {
            return false;
        }
        String path = id.getPath();
        if (!path.startsWith(BUILTIN_PREFIX) || path.length() == BUILTIN_PREFIX.length()) {
            return false;
        }
        for (int i = BUILTIN_PREFIX.length(); i < path.length(); i++) {
            if (!Character.isDigit(path.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The positional id of built-in bar {@code index} ({@code ability_hotbar:bar_N}). */
    public static ResourceLocation builtinBarId(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("built-in bar index must be >= 0, got " + index);
        }
        return ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, BUILTIN_PREFIX + index);
    }

    /**
     * The id given to a stored bar whose {@code bar_id} is missing or corrupt
     * (schema v3): preserved under an identity that can never collide with a
     * built-in ({@code bar_N}) or a registration (the namespace is reserved),
     * so the data is kept but never wrongly adopted (ADR-012 inert-by-identity).
     */
    public static ResourceLocation unidentifiedBarId(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("bar index must be >= 0, got " + index);
        }
        return ResourceLocation.fromNamespaceAndPath(AbilityHotbar.MOD_ID, "unidentified_" + index);
    }

    /**
     * Drops every registration. Test-only - registrations are permanent for a
     * game session (they are code, not data, so there is no reload path).
     */
    public static void reset() {
        REGISTRATIONS.clear();
    }
}
