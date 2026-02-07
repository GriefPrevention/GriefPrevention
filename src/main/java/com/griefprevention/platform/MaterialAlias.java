package com.griefprevention.platform;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for materials that were renamed across Minecraft versions.
 * <p>
 * Some material registry keys changed between versions. This enum provides version-safe
 * access to these materials by trying multiple registry keys in order.
 */
public enum MaterialAlias
{
    CHAIN("iron_chain", "chain"),
    SHORT_GRASS("short_grass", "grass");
    // To support more materials, add constants with their minecraft registry keys

    private final @NotNull Material material;

    MaterialAlias(@NotNull String... keys)
    {
        this.material = RegistryResolver.resolve(Registry.MATERIAL, keys);
    }

    /**
     * Gets the material.
     *
     * @return the material
     */
    public @NotNull Material material()
    {
        return material;
    }
}