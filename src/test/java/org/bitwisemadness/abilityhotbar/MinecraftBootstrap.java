package org.bitwisemadness.abilityhotbar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Brings up just enough Minecraft for fixtures that touch real
 * {@code ItemStack}s. Vanilla's {@link Bootstrap#bootStrap()} is required
 * (the item registry refuses use before it), but NeoForge's patched
 * {@code FeatureFlags} reads FML's {@code LoadingModList} during that
 * bootstrap - and only a running game ever installs one - so this installs
 * an empty one first, then bootstraps. Idempotent: call it from a static
 * block or {@code @BeforeAll} before the first stack.
 */
public final class MinecraftBootstrap {

    private static boolean done;

    private MinecraftBootstrap() {
    }

    /** Runs the one-time setup; subsequent calls are no-ops. */
    public static synchronized void ensure() {
        if (done) {
            return;
        }
        SharedConstants.tryDetectVersion();
        installEmptyLoadingModList();
        Bootstrap.bootStrap();
        done = true;
    }

    /**
     * Installs a {@code LoadingModList} with no mod files: FeatureFlags'
     * loader iterates its files for feature-flag declarations, and an empty
     * list simply declares none. Reflective because FML's installation path
     * is game-startup machinery, not an API.
     */
    private static void installEmptyLoadingModList() {
        try {
            Class<?> type = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Field instance = type.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) != null) {
                return; // a real game (or an earlier run) already installed one
            }
            Method of = type.getMethod("of", List.class, List.class, List.class, List.class, Map.class);
            Object empty = of.invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
            if (instance.get(null) == null) {
                instance.set(null, empty);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "cannot prepare FML's LoadingModList for a headless bootstrap", e);
        }
    }
}
