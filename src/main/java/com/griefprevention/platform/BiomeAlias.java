package com.griefprevention.platform;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Compatibility layer for biome key access across Minecraft versions.
 * <p>
 * In older versions, {@link Biome} is an enum without a {@code getKey()} method.
 * In newer versions, biomes are registry-based and implement {@code Keyed}.
 * This class provides version-safe access to biome keys.
 */
public final class BiomeAlias
{
    private static final MethodHandle GET_KEY;

    static
    {
        MethodHandle getKey = null;
        try
        {
            // Try to find getKey() method (exists on newer versions)
            getKey = MethodHandles.lookup().findVirtual(
                    Biome.class,
                    "getKey",
                    MethodType.methodType(NamespacedKey.class));
        }
        catch (NoSuchMethodException | IllegalAccessException ignored)
        {
            // Method doesn't exist on this version
        }
        GET_KEY = getKey;
    }

    private BiomeAlias() {}

    /**
     * Returns the namespaced key of a biome, compatible across versions.
     *
     * @param biome the biome
     * @return the namespaced key
     */
    public static @NotNull NamespacedKey keyOf(@NotNull Biome biome)
    {
        if (GET_KEY != null)
        {
            try
            {
                return (NamespacedKey) GET_KEY.invoke(biome);
            }
            catch (Throwable ignored)
            {
                // Fall through to enum-based approach
            }
        }

        // Fallback for older versions where Biome is an enum
        return NamespacedKey.minecraft(biome.name().toLowerCase());
    }
}