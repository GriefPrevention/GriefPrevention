package com.griefprevention.platform;

import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for entity types that were renamed across Minecraft versions.
 * <p>
 * Several entity type constants were renamed in 1.20.5. This enum provides version-safe
 * access to these types by looking up stable minecraft registry keys.
 *
 * @see EntityCompat for entity classes that may not exist on all versions
 */
public enum EntityTypeAlias
{
    ITEM("item"),
    END_CRYSTAL("end_crystal"),
    FIREWORK_ROCKET("firework_rocket"),
    TNT("tnt");
    // To support more types, add constants with their minecraft registry keys

    private final @NotNull EntityType entityType;

    EntityTypeAlias(@NotNull String... keys)
    {
        this.entityType = RegistryResolver.resolve(Registry.ENTITY_TYPE, keys);
    }

    /**
     * Gets the entity type.
     *
     * @return the entity type
     */
    public @NotNull EntityType type()
    {
        return entityType;
    }
}