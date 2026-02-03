package com.griefprevention.platform;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for materials that were renamed across Minecraft versions.
 * <p>
 * Some material constants were renamed in 1.20.5. This enum provides version-safe
 * access to these materials by trying both old and new field names.
 */
public enum MaterialAlias
{
    CHAIN("IRON_CHAIN", "CHAIN"),
    SHORT_GRASS("SHORT_GRASS", "GRASS");
    // To support more materials, add constants with their new and legacy field names

    private final @NotNull Material material;

    MaterialAlias(@NotNull String newName, @NotNull String legacyName)
    {
        Material resolved = FieldResolver.resolve(Material.class, newName, legacyName);
        if (resolved == null)
            throw new ExceptionInInitializerError("Failed to resolve Material: " + newName + " or " + legacyName);
        this.material = resolved;
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