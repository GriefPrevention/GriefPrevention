package com.griefprevention.platform;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for resolving registry entries by minecraft key with fallback support.
 * <p>
 * Used by alias enums to handle renamed constants across Minecraft versions.
 */
final class RegistryResolver
{
    private RegistryResolver() {}

    /**
     * Resolves a registry entry by trying each key in order and returning the first match.
     *
     * @param registry the registry to look up entries in
     * @param keys the minecraft keys to try, in order of preference
     * @param <T> the registry entry type
     * @return the resolved value
     * @throws ExceptionInInitializerError if no key matches
     */
    static <T extends Keyed> @NotNull T resolve(@NotNull Registry<T> registry, @NotNull String... keys)
    {
        for (String key : keys)
        {
            T value = registry.get(NamespacedKey.minecraft(key));
            if (value != null) return value;
        }
        throw new ExceptionInInitializerError("Failed to resolve registry entry for keys: " + String.join(", ", keys));
    }

    /**
     * Resolves a registry entry by type class, obtaining the registry via {@link Bukkit#getRegistry(Class)}.
     * <p>
     * Useful when a {@link Registry} constant is not available on the minimum supported version.
     *
     * @param type the registry entry class
     * @param keys the minecraft keys to try, in order of preference
     * @param <T> the registry entry type
     * @return the resolved value
     * @throws ExceptionInInitializerError if the registry is unavailable or no key matches
     */
    static <T extends Keyed> @NotNull T resolve(@NotNull Class<T> type, @NotNull String... keys)
    {
        Registry<T> registry = Bukkit.getRegistry(type);
        if (registry == null)
            throw new ExceptionInInitializerError(type.getSimpleName() + " registry is not available");
        return resolve(registry, keys);
    }
}